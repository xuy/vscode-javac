package org.javacs;

import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.tree.JCTree;
import io.typefox.lsapi.*;
import io.typefox.lsapi.services.LanguageServer;
import io.typefox.lsapi.services.TextDocumentService;
import io.typefox.lsapi.services.WindowService;
import io.typefox.lsapi.services.WorkspaceService;
import io.typefox.lsapi.PublishDiagnosticsParamsImpl;

import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

class JavaLanguageServer implements LanguageServer {
    private static final Logger LOG = Logger.getLogger("main");
    private Consumer<PublishDiagnosticsParams> publishDiagnostics = p -> {};
    private Consumer<MessageParams> showMessage = m -> {};

    private Workspace workspace;

    private ShutdownHandler shutdownHandler;

    public JavaLanguageServer() {
    }

    /**
     * Initializes language server with given workspace (for testing)
     * @param workspace workspace to set
     */
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    /**
     * Sets shutdown handler
     * @param shutdownHandler handler to set
     */
    public void setShutdownHandler(ShutdownHandler shutdownHandler) {
        this.shutdownHandler = shutdownHandler;
    }

    public void onError(String message, Throwable error) {
        if (error instanceof ShowMessageException)
            showMessage.accept(((ShowMessageException) error).message);
        else if (error instanceof NoJavaConfigException) {
            // Swallow error
            // If you want to show a message for no-java-config, 
            // you have to specifically catch the error lower down and re-throw it
            LOG.warning(error.getMessage());
        }
        else {
            LOG.log(Level.SEVERE, message, error);
            
            MessageParamsImpl m = new MessageParamsImpl();

            m.setMessage(message);
            m.setType(MessageParams.TYPE_ERROR);

            showMessage.accept(m);
        }
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {

        workspace = Workspace.getInstance(Paths.get(params.getRootPath()).toAbsolutePath().normalize(), this);

        InitializeResultImpl result = new InitializeResultImpl();

        ServerCapabilitiesImpl c = new ServerCapabilitiesImpl();

        c.setTextDocumentSync(ServerCapabilities.SYNC_INCREMENTAL);
        c.setDefinitionProvider(true);
        c.setCompletionProvider(new CompletionOptionsImpl());
        c.setHoverProvider(true);
        c.setWorkspaceSymbolProvider(true);
        c.setReferencesProvider(true);
        c.setDocumentSymbolProvider(true);

        result.setCapabilities(c);

        return CompletableFuture.completedFuture(result);
    }

    @Override
    public void shutdown() {
        if (shutdownHandler != null) {
            shutdownHandler.shutdown(this);
        }
    }

    @Override
    public void exit() {

    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return new TextDocumentService() {
            @Override
            public CompletableFuture<CompletionList> completion(TextDocumentPositionParams position) {
                System.out.println("Auto complete is called here " + position.toString());
                return CompletableFuture.completedFuture(autocomplete(position));
            }

            @Override
            public CompletableFuture<CompletionItem> resolveCompletionItem(CompletionItem unresolved) {
                return null;
            }

            @Override
            public CompletableFuture<Hover> hover(TextDocumentPositionParams position) {
                return CompletableFuture.completedFuture(doHover(position));
            }

            @Override
            public CompletableFuture<SignatureHelp> signatureHelp(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends Location>> definition(TextDocumentPositionParams position) {
                return CompletableFuture.completedFuture(gotoDefinition(position));
            }

            @Override
            public CompletableFuture<List<? extends Location>> references(ReferenceParams params) {
                return CompletableFuture.completedFuture(findReferences(params));
            }

            @Override
            public CompletableFuture<DocumentHighlight> documentHighlight(TextDocumentPositionParams position) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends SymbolInformation>> documentSymbol(DocumentSymbolParams params) {
                return CompletableFuture.completedFuture(findDocumentSymbols(params));
            }

            @Override
            public CompletableFuture<List<? extends Command>> codeAction(CodeActionParams params) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends CodeLens>> codeLens(CodeLensParams params) {
                return null;
            }

            @Override
            public CompletableFuture<CodeLens> resolveCodeLens(CodeLens unresolved) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> rangeFormatting(DocumentRangeFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<List<? extends TextEdit>> onTypeFormatting(DocumentOnTypeFormattingParams params) {
                return null;
            }

            @Override
            public CompletableFuture<WorkspaceEdit> rename(RenameParams params) {
                return null;
            }

            @Override
            public void didOpen(DidOpenTextDocumentParams params) {
                /*
                try {
                    TextDocumentItem document = params.getTextDocument();
                    URI uri = URI.create(document.getUri());
                    Optional<Path> path = getFilePath(uri);

                    if (path.isPresent()) {
                        String text = document.getText();

                        workspace.setFile(path.get(), text);
                        doLint(path.get());
                    }
                } catch (NoJavaConfigException e) {
                    throw ShowMessageException.warning(e.getMessage(), e);
                }
                */
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
                /*
                VersionedTextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Optional<Path> path = getFilePath(uri);

                if (path.isPresent()) {
                    for (TextDocumentContentChangeEvent change : params.getContentChanges()) {
                        if (change.getRange() == null)
                            workspace.setFile(path.get(), change.getText());
                        else {
                            String existingText = workspace.getFile(path.get());
                            String newText = patch(existingText, change);

                            workspace.setFile(path.get(), newText);
                        }
                    }
                }
                */
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {
                /*
                TextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Optional<Path> path = getFilePath(uri);

                if (path.isPresent()) {
                    // Remove from source cache
                    workspace.clearFile(path.get());
                }
                */
            }

            @Override
            public void didSave(DidSaveTextDocumentParams params) {
                /*
                TextDocumentIdentifier document = params.getTextDocument();
                URI uri = URI.create(document.getUri());
                Optional<Path> path = getFilePath(uri);

                // TODO re-lint dependencies as well as changed files
                if (path.isPresent())
                    doLint(path.get());
                */
            }

            @Override
            public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
                publishDiagnostics = callback;
            }
        };
    }

    private Optional<Path> getFilePath(URI uri) {
        if (!uri.getScheme().equals("file"))
            return Optional.empty();
        else
            return Optional.of(Paths.get(uri));
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return new WorkspaceService() {
            @Override
            public CompletableFuture<List<? extends SymbolInformation>> symbol(WorkspaceSymbolParams params) {
                return CompletableFuture.completedFuture(workspace.getSymbols(params));
            }

            @Override
            public void didChangeConfiguraton(DidChangeConfigurationParams params) {
                
            }

            @Override
            public void didChangeWatchedFiles(DidChangeWatchedFilesParams params) {
                /*
                for (FileEvent event : params.getChanges()) {
                    String eventUri = event.getUri();
                    if (eventUri.endsWith(".java")) {
                        if (event.getType() == FileEvent.TYPE_DELETED) {
                            URI uri = URI.create(event.getUri());

                            getFilePath(uri).ifPresent(path -> {
                                JavacHolder compiler = workspace.findCompiler(path);
                                JavaFileObject file = workspace.findFile(compiler, path);
                                SymbolIndex index = workspace.findIndex(path);

                                compiler.clear(file);
                                index.clear(file.toUri());
                            });
                        }
                    }
                    else if (eventUri.endsWith("javaconfig.json") || eventUri.endsWith("pom.xml")) {
                        // TODO invalidate caches when javaconfig.json or pom.xml changes
                    }
                }
                */
            }
        };
    }

    @Override
    public WindowService getWindowService() {
        return new WindowService() {
            @Override
            public void onShowMessage(Consumer<MessageParams> callback) {
                showMessage = callback;
            }

            @Override
            public void onShowMessageRequest(Consumer<ShowMessageRequestParams> callback) {

            }

            @Override
            public void onLogMessage(Consumer<MessageParams> callback) {

            }
        };
    }
    
    void publishDiagnostics(Collection<Path> paths, DiagnosticCollector<JavaFileObject> errors) {
        Map<URI, PublishDiagnosticsParamsImpl> files = new HashMap<>();
        
        paths.forEach(p -> files.put(p.toUri(), newPublishDiagnostics(p.toUri())));
        
        errors.getDiagnostics().forEach(error -> {
            if (error.getStartPosition() != javax.tools.Diagnostic.NOPOS) {
                URI uri = error.getSource().toUri();
                PublishDiagnosticsParamsImpl publish = files.computeIfAbsent(uri, this::newPublishDiagnostics);

                RangeImpl range = position(error);
                DiagnosticImpl diagnostic = new DiagnosticImpl();
                int severity = severity(error.getKind());

                diagnostic.setSeverity(severity);
                diagnostic.setRange(range);
                diagnostic.setCode(error.getCode());
                diagnostic.setMessage(error.getMessage(null));

                publish.getDiagnostics().add(diagnostic);
            }
        });

        files.values().forEach(publishDiagnostics);
    }

    private int severity(javax.tools.Diagnostic.Kind kind) {
        switch (kind) {
            case ERROR:
                return Diagnostic.SEVERITY_ERROR;
            case WARNING:
            case MANDATORY_WARNING:
                return Diagnostic.SEVERITY_WARNING;
            case NOTE:
            case OTHER:
            default:
                return Diagnostic.SEVERITY_INFO;
        }
    }

    private PublishDiagnosticsParamsImpl newPublishDiagnostics(URI newUri) {
        PublishDiagnosticsParamsImpl p = new PublishDiagnosticsParamsImpl();

        p.setDiagnostics(new ArrayList<>());
        p.setUri(newUri.toString());

        return p;
    }

    private RangeImpl position(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        // Compute start position
        PositionImpl start = new PositionImpl();

        start.setLine((int) (error.getLineNumber() - 1));
        start.setCharacter((int) (error.getColumnNumber() - 1));

        // Compute end position
        PositionImpl end = endPosition(error);

        // Combine into Range
        RangeImpl range = new RangeImpl();

        range.setStart(start);
        range.setEnd(end);

        return range;
    }

    private PositionImpl endPosition(javax.tools.Diagnostic<? extends JavaFileObject> error) {
        try (Reader reader = error.getSource().openReader(true)) {
            long startOffset = error.getStartPosition();
            long endOffset = error.getEndPosition();

            reader.skip(startOffset);

            int line = (int) error.getLineNumber() - 1;
            int column = (int) error.getColumnNumber() - 1;

            for (long i = startOffset; i < endOffset; i++) {
                int next = reader.read();

                if (next == '\n') {
                    line++;
                    column = 0;
                }
                else
                    column++;
            }

            PositionImpl end = new PositionImpl();

            end.setLine(line);
            end.setCharacter(column);

            return end;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private List<? extends Location> findReferences(ReferenceParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());
        int line = params.getPosition().getLine();
        int character = params.getPosition().getCharacter();
        List<Location> result = new ArrayList<>();

        try {
            findSymbol(uri, line, character).ifPresent(symbol -> {
                getFilePath(uri).map(workspace::findIndex).ifPresent(index -> {
                    index.findSymbol(symbol).ifPresent(info -> {
                        result.add(info.getLocation());
                    });
                    index.references(symbol).forEach(result::add);
                });
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "An error occurred while looking for references " +
                            uri + ' ' + line + ':' + character,
                    e);
        }


        return result;
    }

    private List<? extends SymbolInformation> findDocumentSymbols(DocumentSymbolParams params) {
        URI uri = URI.create(params.getTextDocument().getUri());

        return getFilePath(uri).map(path -> {
            SymbolIndex index = workspace.findIndex(path);
            List<? extends SymbolInformation> found = index.allInFile(uri).collect(Collectors.toList());

            return found;
        }).orElse(Collections.emptyList());
    }

    private Optional<Symbol> findSymbol(URI uri, int line, int character) {
        return getFilePath(uri).flatMap(path -> {
            JCTree.JCCompilationUnit tree = workspace.getTree(path, uri);
            JavaFileObject file = workspace.getFile(path);
            long cursor = findOffset(file, line, character);
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file,
                    cursor,
                    workspace.findCompiler(path).context);
            tree.accept(visitor);
            return visitor.found;
        });
    }

    public List<? extends Location> gotoDefinition(TextDocumentPositionParams position) {
        URI uri = URI.create(position.getTextDocument().getUri());
        int line = position.getPosition().getLine();
        int character = position.getPosition().getCharacter();
        List<Location> result = new ArrayList<>();

        try {
            findSymbol(uri, line, character).ifPresent(symbol -> {
                getFilePath(uri).map(workspace::findIndex).ifPresent(index -> {
                    index.findSymbol(symbol).ifPresent(info -> {
                        result.add(info.getLocation());
                    });
                });
            });
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "An error occurred while looking for definition " + uri + ' ' + line + ':' + character ,
                    e);
        }

        return result;
    }

    public static RangeImpl findPosition(JavaFileObject file, long startOffset, long endOffset) {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            // Find the start position
            while (offset < startOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            PositionImpl start = createPosition(line, character);

            // Find the end position
            while (offset < endOffset) {
                int next = in.read();

                if (next < 0)
                    break;
                else {
                    offset++;
                    character++;

                    if (next == '\n') {
                        line++;
                        character = 0;
                    }
                }
            }

            PositionImpl end = createPosition(line, character);

            // Combine into range
            RangeImpl range = new RangeImpl();

            range.setStart(start);
            range.setEnd(end);

            return range;
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }

    private static PositionImpl createPosition(int line, int character) {
        PositionImpl p = new PositionImpl();

        p.setLine(line);
        p.setCharacter(character);

        return p;
    }

    public static long findOffset(JavaFileObject file, int targetLine, int targetCharacter) {
        try (Reader in = file.openReader(true)) {
            long offset = 0;
            int line = 0;
            int character = 0;

            while (line < targetLine) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;

                    if (next == '\n')
                        line++;
                }
            }

            while (character < targetCharacter) {
                int next = in.read();

                if (next < 0)
                    return offset;
                else {
                    offset++;
                    character++;
                }
            }

            return offset;
        } catch (IOException e) {
            throw ShowMessageException.error(e.getMessage(), e);
        }
    }

    public HoverImpl doHover(TextDocumentPositionParams position) {
//        LOG.info("Hover " + position.getTextDocument().getUri() + ' ' +
//                position.getPosition().getLine() + ':' + position.getPosition().getCharacter());

        HoverImpl result = new HoverImpl();
        URI uri = workspace.getURI(position.getTextDocument().getUri());

        try {
            Optional<Path> maybePath = getFilePath(uri);
            if (maybePath.isPresent()) {
                JCTree.JCCompilationUnit tree = workspace.getTree(maybePath.get(), uri);
                JavaFileObject file = workspace.getFile(maybePath.get());
                long cursor = findOffset(file, position.getPosition().getLine(), position.getPosition().getCharacter());
                SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file,
                        cursor,
                        workspace.findCompiler(maybePath.get()).context);
                tree.accept(visitor);

                if (visitor.found.isPresent()) {
                    Symbol symbol = visitor.found.get();
                    List<MarkedStringImpl> contents = new ArrayList<>();

                    String text = tree.docComments.getCommentText(visitor.foundTree);
                    if (text != null) {
                        contents.add(markedString(text));
                    } else {

                        switch (symbol.getKind()) {
                            case PACKAGE:
                                contents.add(markedString("package " + symbol.getQualifiedName()));

                                break;
                            case ENUM:
                                contents.add(markedString("enum " + symbol.getQualifiedName()));

                                break;
                            case CLASS:
                                contents.add(markedString("class " + symbol.getQualifiedName()));

                                break;
                            case ANNOTATION_TYPE:
                                contents.add(markedString("@interface " + symbol.getQualifiedName()));

                                break;
                            case INTERFACE:
                                contents.add(markedString("interface " + symbol.getQualifiedName()));

                                break;
                            case METHOD:
                            case CONSTRUCTOR:
                            case STATIC_INIT:
                            case INSTANCE_INIT:
                                Symbol.MethodSymbol method = (Symbol.MethodSymbol) symbol;
                                String signature = AutocompleteVisitor.methodSignature(method);
                                String returnType = ShortTypePrinter.print(method.getReturnType());

                                contents.add(markedString(returnType + " " + signature));

                                break;
                            case PARAMETER:
                            case LOCAL_VARIABLE:
                            case EXCEPTION_PARAMETER:
                            case ENUM_CONSTANT:
                            case FIELD:
                                contents.add(markedString(ShortTypePrinter.print(symbol.type)));

                                break;
                            case TYPE_PARAMETER:
                            case OTHER:
                            case RESOURCE_VARIABLE:
                                break;
                        }
                    }
                    result.setContents(contents);
                }
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING,
                    "An error occurred while looking for hover " +
                            uri + ' ' + position.getPosition().getLine() + ':' + position.getPosition().getCharacter(),
                    e);
        }

        return result;
    }

    private MarkedStringImpl markedString(String value) {
        MarkedStringImpl result = new MarkedStringImpl();

        result.setLanguage("java");
        result.setValue(value);

        return result;
    }

    public CompletionList autocomplete(TextDocumentPositionParams position) {
        CompletionListImpl result = new CompletionListImpl();

        result.setIncomplete(false);
        result.setItems(new ArrayList<>());

        Optional<Path> maybePath = getFilePath(URI.create(position.getTextDocument().getUri()));

        if (maybePath.isPresent()) {
            Path path = maybePath.get();
            DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();

            JavacHolder compiler = workspace.findCompiler(path);
            JavaFileObject file = workspace.findFile(compiler, path);
            long cursor = findOffset(file, position.getPosition().getLine(), position.getPosition().getCharacter());
            JavaFileObject withSemi = withSemicolonAfterCursor(file, path, cursor);
            AutocompleteVisitor autocompleter = new AutocompleteVisitor(withSemi, cursor, compiler.context);

            compiler.onError(errors);

            JCTree.JCCompilationUnit ast = compiler.parse(withSemi);

            // Remove all statements after the cursor
            // There are often parse errors after the cursor, which can generate unrecoverable type errors
            ast.accept(new AutocompletePruner(withSemi, cursor, compiler.context));

            compiler.compile(ast);

            ast.accept(autocompleter);

            result.getItems().addAll(autocompleter.suggestions);
        }

        return result;
    }

    /**
     * Insert ';' after the users cursor so we recover from parse errors in a helpful way when doing autocomplete.
     */
    private JavaFileObject withSemicolonAfterCursor(JavaFileObject file, Path path, long cursor) {
        try (Reader reader = file.openReader(true)) {
            StringBuilder acc = new StringBuilder();

            for (int i = 0; i < cursor; i++) {
                int next = reader.read();

                if (next == -1)
                    throw new RuntimeException("End of file " + file + " before cursor " + cursor);

                acc.append((char) next);
            }

            acc.append(";");

            for (int next = reader.read(); next > 0; next = reader.read()) {
                acc.append((char) next);
            }

            return new StringFileObject(acc.toString(), path);
        } catch (IOException e) {
            throw ShowMessageException.error("Error reading " + file, e);
        }
    }

}
