package org.javacs;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Collects all "java" directories in the given workspace
 */
class CatchEmAllJavacConfig {

    private static final Logger LOG = Logger.getLogger("catchemall");

    /**
     * Mapping (workspace root -> config)
     */
    private static Map<Path, CatchEmAllJavacConfig> configs = new HashMap<>();

    private JavacConfig javacConfig;

    /**
     * @param path workspace root
     * @return configuration for a given workspace root (computes if absent)
     */
    public static synchronized CatchEmAllJavacConfig get(Path path) {
        CatchEmAllJavacConfig config = configs.get(path);
        if (config == null) {
            LOG.info("Building configuration based on all java files for " + path);
            config = new CatchEmAllJavacConfig(path);
            configs.put(path, config);
        }
        return config;
    }

    /**
     * @return javac configuration for a given workspace
     */
    JavacConfig getConfig() {
        return javacConfig;
    }

    /**
     * Constructs new object for a given workspace root
     * @param path workspace root
     */
    private CatchEmAllJavacConfig(Path path) {

        // reading all the java directories in workspace
        Set<Path> directories = getSourceDirs(path);
        // if we found no directories, let's try to add root one
        if (directories.isEmpty()) {
            directories.add(path);
        }
        try {
            javacConfig = new JavacConfig(directories,
                    Collections.emptySet(),
                    Files.createTempDirectory(path.getFileName().toString()));
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Cannot create configuration", ex);
        }
    }

    /**
     * @param path workspace root
     * @return all "java" directories found
     */
    private Set<Path> getSourceDirs(Path path) {
        Set<Path> dirs = new HashSet<>();
        try {

            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().equals("java")) {
                        dirs.add(dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.warning("Unable to collect java directories: " + e);
        }
        return dirs;
    }

}
