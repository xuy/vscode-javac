package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import io.typefox.lsapi.MessageParams;
import io.typefox.lsapi.MessageParamsImpl;
import io.typefox.lsapi.SymbolInformation;
import io.typefox.lsapi.WorkspaceSymbolParams;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.javacs.Main.JSON;

class Workspace {

    private Path root;

    private Map<JavacConfig, SymbolIndex> indexCache = new ConcurrentHashMap<>();

    private Map<Path, Optional<JavacConfig>> configCache = new ConcurrentHashMap<>();

    private Map<JavacConfig, JavacHolder> compilerCache = new ConcurrentHashMap<>();

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
        if (Files.exists(dir.resolve(".jls-config"))) {
            JavaConfigJson json = readJavaConfigJson(dir.resolve(".jls-config"));
            Collection<Path> classPath = json.classPath.stream().map(dir::resolve).collect(Collectors.toList());
            Collection<Path> sourcePath = json.sources.stream().map(dir::resolve).collect(Collectors.toList());
            Path outputDirectory = dir.resolve(json.outputDirectory);
            JavacConfig config = new JavacConfig(sourcePath, classPath, outputDirectory);

            return Optional.of(config);
        } else {
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

    private JavaFileObject findFile(JavacHolder compiler, Path path) {
        return compiler.fileManager.getRegularFile(path.toFile());
    }

    public List<SymbolInformation> getSymbols(WorkspaceSymbolParams params) {
        return indexCache.values()
                .stream()
                .flatMap(symbolIndex -> symbolIndex.search(params.getQuery()))
                .limit(100)
                .collect(Collectors.toList());
    }

    public URI getURI(String uri) {
        return this.root.toUri().resolve(uri);
    }

    public synchronized JCTree.JCCompilationUnit getTree(Path path, URI uri) {
        JavacHolder compiler = findCompiler(path);
        JavaFileObject file = findFile(compiler, path);
        SymbolIndex index = findIndex(path);

        JCTree.JCCompilationUnit tree = index.get(uri);
        if (tree == null) {
            DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
            compiler.onError(errors);
            tree = compiler.parse(file);
            compiler.compile(tree);
            index.update(tree, compiler.context);
        }
        return tree;
    }

    public JavaFileObject getFile(Path path) {
        return findCompiler(path).fileManager.getRegularFile(path.toFile());
    }

}
