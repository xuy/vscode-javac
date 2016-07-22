package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import io.typefox.lsapi.*;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.*;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

interface ShutdownHandler {
    void shutdown(JavaLanguageServer server);
}
