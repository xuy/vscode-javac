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
                return null;
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
            }

            @Override
            public void didChange(DidChangeTextDocumentParams params) {
            }

            @Override
            public void didClose(DidCloseTextDocumentParams params) {
            }

            @Override
            public void didSave(DidSaveTextDocumentParams params) {
            }

            @Override
            public void onPublishDiagnostics(Consumer<PublishDiagnosticsParams> callback) {
                publishDiagnostics = callback;
            }
        };
    }

    private String patch(String sourceText, TextDocumentContentChangeEvent change) {
        try {
            Range range = change.getRange();
            BufferedReader reader = new BufferedReader(new StringReader(sourceText));
            StringWriter writer = new StringWriter();

            // Skip unchanged lines
            int line = 0;

            while (line < range.getStart().getLine()) {
                writer.write(reader.readLine() + '\n');
                line++;
            }

            // Skip unchanged chars
            for (int character = 0; character < range.getStart().getCharacter(); character++)
                writer.write(reader.read());

            // Write replacement text
            writer.write(change.getText());

            // Skip replaced text
            reader.skip(change.getRangeLength());

            // Write remaining text
            while (true) {
                int next = reader.read();

                if (next == -1)
                    return writer.toString();
                else
                    writer.write(next);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Path> getFilePath(URI uri) {
        if (uri == null || !uri.getScheme().equals("file"))
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
        URI uri = workspace.getURI(params.getTextDocument().getUri());
        int line = params.getPosition().getLine();
        int character = params.getPosition().getCharacter();
        List<Location> result = new ArrayList<>();

        findSymbol(uri, line, character).ifPresent(symbol -> {
            getFilePath(uri).map(workspace::findIndex).ifPresent(index -> {
                index.findSymbol(symbol).ifPresent(info -> {
                    result.add(info.getLocation());
                });
                index.references(symbol).forEach(result::add);
            });
        });

        return result;
    }

    private List<? extends SymbolInformation> findDocumentSymbols(DocumentSymbolParams params) {
        URI uri = workspace.getURI(params.getTextDocument().getUri());

        return getFilePath(uri).map(path -> {
            SymbolIndex index = workspace.findIndex(path);
            List<? extends SymbolInformation> found = index.allInFile(uri).collect(Collectors.toList());

            return found;
        }).orElse(Collections.emptyList());
    }

    private Optional<Symbol> findSymbol(URI uri, int line, int character) {
        return getFilePath(uri).flatMap(path -> {
            JavacHolder compiler = workspace.findCompiler(path);
            SymbolIndex index = workspace.findIndex(path);
            JavaFileObject file = workspace.findFile(compiler, path);
            long cursor = findOffset(file, line, character);
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file, cursor, compiler.context);

            JCTree.JCCompilationUnit tree = index.get(uri);
            if (tree == null) {
                DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
                compiler.onError(errors);
                tree = compiler.parse(file);
                compiler.compile(tree);
                index.update(tree, compiler.context);
            }

            tree.accept(visitor);
            return visitor.found;
        });
    }

    public List<? extends Location> gotoDefinition(TextDocumentPositionParams position) {
        URI uri = workspace.getURI(position.getTextDocument().getUri());
        int line = position.getPosition().getLine();
        int character = position.getPosition().getCharacter();
        List<Location> result = new ArrayList<>();

        findSymbol(uri, line, character).ifPresent(symbol -> {
            getFilePath(uri).map(workspace::findIndex).ifPresent(index -> {
                index.findSymbol(symbol).ifPresent(info -> {
                    result.add(info.getLocation());
                });
            });
        });

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
        HoverImpl result = new HoverImpl();

        URI uri = workspace.getURI(position.getTextDocument().getUri());
        Optional<Path> maybePath = getFilePath(uri);

        if (maybePath.isPresent()) {
            Path path = maybePath.get();
            JavacHolder compiler = workspace.findCompiler(path);
            JavaFileObject file = workspace.findFile(compiler, path);
            SymbolIndex index = workspace.findIndex(path);
            long cursor = findOffset(file, position.getPosition().getLine(), position.getPosition().getCharacter());
            SymbolUnderCursorVisitor visitor = new SymbolUnderCursorVisitor(file, cursor, compiler.context);

            JCTree.JCCompilationUnit tree = index.get(uri);
            if (tree == null) {
                DiagnosticCollector<JavaFileObject> errors = new DiagnosticCollector<>();
                compiler.onError(errors);
                tree = compiler.parse(file);
                compiler.compile(tree);
                index.update(tree, compiler.context);
            }

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
        
        return result;
    }

    private MarkedStringImpl markedString(String value) {
        MarkedStringImpl result = new MarkedStringImpl();

        result.setLanguage("java");
        result.setValue(value);

        return result;
    }

}
