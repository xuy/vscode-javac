package org.javacs;

import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.MessageParamsImpl;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.WorkspaceSymbolParams;

import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.javacs.Main.JSON;

class Workspace {

    private static final Logger LOG = Logger.getLogger("main");
    private Path root;
    private Map<Path, String> sourceByPath = new HashMap<>();

    private Map<JavacConfig, SymbolIndex> indexCache = new HashMap<>();

    // TODO invalidate cache when VSCode notifies us config file has changed
    private Map<Path, Optional<JavacConfig>> configCache = new HashMap<>();

    private Map<JavacConfig, JavacHolder> compilerCache = new HashMap<>();

    /**
     * Instead of looking for javaconfig.json and creating a JavacHolder, just use this.
     * For testing.
     */
    private final JavacHolder testJavac;

    private final JavaLanguageServer javaLanguageServer;

    private static Map<Path, Workspace> workspaces = new HashMap<>();

    public static synchronized Workspace getInstance(Path path,
                                                     JavaLanguageServer javaLanguageServer) {
        Workspace ret = workspaces.get(path);
        if (ret == null) {
            ret = new Workspace(path, javaLanguageServer);
        }
        return ret;
    }

    private Workspace(Path root, JavaLanguageServer javaLanguageServer) {
        this.root = root;
        this.testJavac = null;
        this.javaLanguageServer = javaLanguageServer;
        workspaces.put(root, this);
    }

    Workspace(Path root, JavaLanguageServer javaLanguageServer, JavacHolder testJavac) {
        this.root = root;
        this.testJavac = testJavac;
        this.javaLanguageServer = javaLanguageServer;
        workspaces.put(root, this);
    }

    private Optional<Path> getFilePath(URI uri) {
        if (!uri.getScheme().equals("file"))
            return Optional.empty();
        else
            return Optional.of(Paths.get(uri));
    }

    /**
     * Look for a configuration in a parent directory of uri
     */
    public JavacHolder findCompiler(Path path) {
        if (testJavac != null) {
            return testJavac;
        }

        Path dir = path.getParent();
        Optional<JavacConfig> config = findConfig(dir);

        // If config source path doesn't contain source file, then source file has no config
        if (config.isPresent() && !config.get().sourcePath.stream().anyMatch(path::startsWith))
            throw new NoJavaConfigException(path.getFileName() + " is not on the source path");

        Optional<JavacHolder> maybeHolder = config.map(c -> compilerCache.computeIfAbsent(c, this::newJavac));

        return maybeHolder.orElseThrow(() -> new NoJavaConfigException(path));
    }

    private JavacHolder newJavac(JavacConfig c) {
        return new JavacHolder(c.classPath,
                c.sourcePath,
                c.outputDirectory);
    }


    public SymbolIndex findIndex(Path path) {
        Path dir = path.getParent();
        Optional<JavacConfig> config = findConfig(dir);
        Optional<SymbolIndex> index = config.map(c -> indexCache.computeIfAbsent(c, this::newIndex));

        return index.orElseThrow(() -> new NoJavaConfigException(path));
    }

    private SymbolIndex newIndex(JavacConfig c) {
        return new SymbolIndex(c.classPath,
                c.sourcePath,
                c.outputDirectory,
                root,
                javaLanguageServer::publishDiagnostics);
    }


    public Optional<JavacConfig> findConfig(Path dir) {
        return configCache.computeIfAbsent(dir, this::doFindConfig);
    }

    private Optional<JavacConfig> doFindConfig(Path dir) {
        while (true) {
            Optional<JavacConfig> found = readIfConfig(dir);

            if (found.isPresent())
                return found;
            else if (root.startsWith(dir))
                return Optional.empty();
            else
                dir = dir.getParent();
        }
    }

    /**
     * If directory contains a config file, for example javaconfig.json or an eclipse project file, read it.
     */
    public Optional<JavacConfig> readIfConfig(Path dir) {
        if (Files.exists(dir.resolve("javaconfig.json"))) {
            JavaConfigJson json = readJavaConfigJson(dir.resolve("javaconfig.json"));
            Set<Path> classPath = json.classPathFile.map(classPathFile -> {
                Path classPathFilePath = dir.resolve(classPathFile);
                return readClassPathFile(classPathFilePath);
            }).orElse(Collections.emptySet());
            Set<Path> sourcePath = json.sourcePath.stream().map(dir::resolve).collect(Collectors.toSet());
            Path outputDirectory = dir.resolve(json.outputDirectory);
            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        } else if (Files.exists(dir.resolve("pom.xml"))) {
            return Optional.ofNullable(MavenJavacConfig.get(root).getConfig(dir));
        }
        // TODO add more file types
        else {
            return Optional.empty();
        }
    }

    private JavaConfigJson readJavaConfigJson(Path configFile) {
        try {
            return JSON.readValue(configFile.toFile(), JavaConfigJson.class);
        } catch (IOException e) {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage("Error reading " + configFile);
            message.setType(MessageParams.TYPE_ERROR);

            throw new ShowMessageException(message, e);
        }
    }

    private Set<Path> readClassPathFile(Path classPathFilePath) {
        try {
            InputStream in = Files.newInputStream(classPathFilePath);
            String text = new BufferedReader(new InputStreamReader(in))
                    .lines()
                    .collect(Collectors.joining());
            Path dir = classPathFilePath.getParent();

            return Arrays.stream(text.split(File.pathSeparator))
                    .map(dir::resolve)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            MessageParamsImpl message = new MessageParamsImpl();

            message.setMessage("Error reading " + classPathFilePath);
            message.setType(MessageParams.TYPE_ERROR);

            throw new ShowMessageException(message, e);
        }
    }

    public void setFile(Path path, String text) {
        sourceByPath.put(path, text);
        invalidateCache(path);
    }

    public void clearFile(Path path) {
        sourceByPath.remove(path);
        invalidateCache(path);
    }

    public String getFile(Path path) {
        return sourceByPath.get(path);
    }

    public JavaFileObject findFile(JavacHolder compiler, Path path) {
        if (sourceByPath.containsKey(path))
            return new StringFileObject(sourceByPath.get(path), path);
        else
            return compiler.fileManager.getRegularFile(path.toFile());
    }

    private void invalidateCache(Path sourceFile) {
        Path dir = sourceFile.getParent();
        Optional<JavacConfig> config = findConfig(dir);
        if (config.isPresent()) {
            SymbolIndex index = indexCache.get(config.get());
            if (index != null) {
                index.clear(sourceFile.toUri());
            }
        }
    }

    public List<SymbolInformation> getSymbols(WorkspaceSymbolParams params) {
        return indexCache.values()
                .stream()
                .flatMap(symbolIndex -> symbolIndex.search(params.getQuery()))
                .limit(100)
                .collect(Collectors.toList());
    }

    public URI getURI(String uri) {
        URI full = URI.create(uri);
        if (!full.getScheme().equals("file")) {
            return null;
        }
        return this.root.toUri().resolve(full.getPath().substring(1));
    }

}
