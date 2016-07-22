package org.javacs;

import io.typefox.lsapi.InitializeParamsImpl;
import org.junit.BeforeClass;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;

public class Fixtures {
    static {
        try {
            LoggingFormat.startLogging();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() { }

    static JavaLanguageServer getJavaLanguageServer() {
        Set<Path> classPath = Collections.emptySet();
        Set<Path> sourcePath = Collections.singleton(Paths.get("src/test/resources").toAbsolutePath());
        Path outputDirectory = Paths.get("out").toAbsolutePath();
        JavacHolder javac = new JavacHolder(classPath, sourcePath, outputDirectory);
        JavaLanguageServer server = new JavaLanguageServer();
        Path workspaceRoot = Paths.get(".").toAbsolutePath().normalize();
        server.setWorkspace(new Workspace(workspaceRoot, server, javac));

        InitializeParamsImpl init = new InitializeParamsImpl();
        init.setRootPath(workspaceRoot.toString());

        server.initialize(init);
        return server;
    }
}
