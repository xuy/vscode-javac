package org.javacs;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.building.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.DefaultRemoteRepositoryManager;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads Maven pom.xml project descriptors and transforms them into <code>JavacConfig</code>
 * TODO (alexsaveliev): Does not downloads dependencies, returns empty classpath for now
 */
class MavenJavacConfig {

    private static final Logger LOG = Logger.getLogger("maven");

    /**
     * Model builder factory used to produce Maven projects
     */
    private static DefaultModelBuilderFactory modelBuilderFactory = new DefaultModelBuilderFactory();

    /**
     * Mapping (workspace root -> config)
     */
    private static Map<Path, MavenJavacConfig> configs = new HashMap<>();

    /**
     * Maven repository system
     */
    private static RepositorySystem repositorySystem;

    /**
     * Maven repository system session
     */
    private static RepositorySystemSession repositorySystemSession;

    static {
        // initializing repository system and session
        initRepositorySystem();
    }

    /**
     * Mapping (directory -> config) for each directory containing pom.xml
     */
    private Map<Path, JavacConfig> javacConfigMap = new HashMap<>();

    /**
     * @param path workspace root
     * @return configuration for a given workspace root (computes if absent)
     */
    public static synchronized MavenJavacConfig get(Path path) {
        MavenJavacConfig config = configs.get(path);
        if (config == null) {
            LOG.info("Building configuration based on Maven descriptors for " + path);
            config = new MavenJavacConfig(path);
            configs.put(path, config);
        }
        return config;
    }

    /**
     * @param path directory inside workspace root
     * @return javac configuration for a given directory (if present)
     */
    JavacConfig getConfig(Path path) {
        return javacConfigMap.get(path);
    }

    /**
     * Initializes Maven repository system and session
     */
    private static void initRepositorySystem() {
        repositorySystem = newRepositorySystem();
        repositorySystemSession = newRepositorySystemSession(repositorySystem);
    }

    /**
     * Initializes repository system
     * @return repository system
     */
    private static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);

        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
            @Override
            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
                LOG.log(Level.SEVERE, "Failed co create service " + type + " using implementation " + impl, exception);
            }
        });

        return locator.getService(RepositorySystem.class);
    }

    /**
     * Initializes repository system session
     *
     * @param system repository system to use
     * @return repository system session
     */
    private static RepositorySystemSession newRepositorySystemSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo =
                new LocalRepository(org.apache.maven.repository.RepositorySystem.defaultUserLocalRepository);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    /**
     * Constructs new object for a given workspace root
     * @param path workspace root
     */
    private MavenJavacConfig(Path path) {
        // reading all the pom.xml in workspace
        Collection<Path> descriptors = getDescriptors(path);
        Map<String, MavenProject> idToProjectMap = new HashMap<>();
        Map<Path, MavenProject> pathToProjectMap = new HashMap<>();
        // makings maps of group/artifactid -> maven project and pom.xml -> maven project.
        // first one will be used to find sub-project dependencies,
        // the second one to associate pom.xml's directory with the javac config built
        for (Path descriptor : descriptors) {
            try {
                MavenProject project = getMavenProject(descriptor);
                idToProjectMap.put(project.getGroupId() + '/' + project.getArtifactId(), project);
                pathToProjectMap.put(descriptor, project);
            } catch (ModelBuildingException e) {
                LOG.log(Level.WARNING, "Cannot parse Maven project descriptor " + path, e);
            }
        }
        for (Map.Entry<Path, MavenProject> entry : pathToProjectMap.entrySet()) {
            MavenProject project = entry.getValue();
            javacConfigMap.put(entry.getKey().getParent(), new JavacConfig(collectSourcePath(project, idToProjectMap),
                    Collections.emptySet(),
                    Paths.get(project.getBuild().getOutputDirectory())));
        }
    }

    /**
     * Collects source path from project and its local dependencies (local dependency is when Maven project A refers to
     * Maven project B from the same workspace)
     * @param project project to collect dependencies for
     * @param idToProjectMap map of group/artifactid -> maven project
     * @return list of source path from project and its local dependencies
     */
    private static Set<Path> collectSourcePath(MavenProject project, Map<String, MavenProject> idToProjectMap) {
        Set<Path> ret = new HashSet<>();
        collectSourcePath(project, idToProjectMap, ret, new HashSet<>());
        return ret;
    }

    /**
     * Collects source path from project and its local dependencies (local dependency is when Maven project A refers to
     * Maven project B from the same workspace)
     * @param project project to collect dependencies for
     * @param idToProjectMap map of group/artifactid -> maven project
     * @param ret target set
     * @param visited tracks visited projects to avoid loops
     */
    private static void collectSourcePath(MavenProject project,
                                          Map<String, MavenProject> idToProjectMap,
                                          Set<Path> ret,
                                          Set<String> visited) {
        String id = project.getGroupId() + '/' + project.getArtifactId();
        if (!visited.add(id)) {
            return;
        }
        // extract project's source roots
        collectSourceRoots(project, ret);
        for (Dependency dependency : project.getDependencies()) {
            id = dependency.getGroupId() + '/' + dependency.getArtifactId();
            MavenProject dep = idToProjectMap.get(id);
            if (dep != null) {
                // extract project's local dependency source roots
                collectSourcePath(dep, idToProjectMap, ret, visited);
            }
        }
    }

    /**
     * Collects project's source roots
     * @param project Maven project
     * @param ret target set to fill
     */
    private static void collectSourceRoots(MavenProject project, Set<Path> ret) {
        Path root = project.getModel().getPomFile().getParentFile().toPath();
        for (String sourceRoot : project.getCompileSourceRoots()) {
            File f = concat(root, sourceRoot).toFile();
            if (f.isDirectory()) {
                ret.add(f.toPath());
            }
        }
        for (String sourceRoot : project.getTestCompileSourceRoots()) {
            File f = concat(root, sourceRoot).toFile();
            if (f.isDirectory()) {
                ret.add(f.toPath());
            }
        }

        String sourceRoot = project.getBuild().getSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/main";
        }
        File f = concat(root, sourceRoot).toFile();
        if (f.isDirectory()) {
            ret.add(f.toPath());
        }

        sourceRoot = project.getBuild().getTestSourceDirectory();
        if (sourceRoot == null) {
            sourceRoot = "src/test";
        }
        f = concat(root, sourceRoot).toFile();
        if (f.isDirectory()) {
            ret.add(f.toPath());
        }
    }

    /**
     * Concats (if needed) parent and child paths
     * @param parent parent path
     * @param child child path
     * @return concatenated path if child is relative or child if it's absolute
     */
    private static Path concat(Path parent, String child) {
        Path c = Paths.get(child);
        if (c.isAbsolute()) {
            return c;
        } else {
            return parent.resolve(c);
        }
    }

    /**
     * @param path workspace root
     * @return all pom.xml found
     */
    private Collection<Path> getDescriptors(Path path) {
        Collection<Path> descriptors = new LinkedList<>();
        try {

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.getFileName().toString();
                    if (name.equals("pom.xml")) {
                        descriptors.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warning("Unable to collect Maven project descriptors: " + e);
        }
        return descriptors;
    }

    /**
     * Parses Maven project
     * @param descriptor pom.xml path
     * @return Maven project object
     * @throws ModelBuildingException
     */
    private MavenProject getMavenProject(Path descriptor) throws ModelBuildingException {
        ModelBuildingRequest request = new DefaultModelBuildingRequest();
        request.setSystemProperties(System.getProperties());
        request.setPomFile(descriptor.toFile());
        request.setModelResolver(new MavenModelResolver(new DefaultRemoteRepositoryManager(),
                repositorySystem,
                repositorySystemSession));
        ModelBuildingResult result = modelBuilderFactory.newInstance().build(request);
        return new MavenProject(result.getEffectiveModel());
    }


}
