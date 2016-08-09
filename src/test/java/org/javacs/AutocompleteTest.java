package org.javacs;

import io.typefox.lsapi.*;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

public class AutocompleteTest extends Fixtures {
    private static final Logger LOG = Logger.getLogger("main");

    @Test
    public void staticMember() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 33);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    @Ignore
    public void staticReference() throws IOException {
        String file = "/org/javacs/example/AutocompleteStaticReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 2, 37);

        assertThat(suggestions, hasItems("methodStatic"));
        assertThat(suggestions, not(hasItems( "method", "new")));
    }

    @Test
    public void member() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 13);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "fieldStaticPrivate", "methodStaticPrivate", "class")));
        assertThat(suggestions, hasItems("field", "method", "fieldPrivate", "methodPrivate", "getClass"));
    }

    @Test
    public void throwsSignature() throws IOException {
        String file = "/org/javacs/example/AutocompleteMember.java";

        // Static method
        Set<String> suggestions = items(file, 4, 13).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("method() throws Exception"));
    }

    @Test
    public void initBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 7, 9);

        assertThat(suggestions, hasItems("field", "fieldStatic", "method", "methodStatic"));
        
        // this.f
        suggestions = insertText(file, 8, 14);

        assertThat(suggestions, hasItems("field", "method"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic")));
        
        // AutocompleteMembers.f
        suggestions = insertText(file, 9, 29);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));

        // TODO
//        // this::m
//        suggestions = insertText(file, 10, 15);
//
//        assertThat(suggestions, hasItems("method"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "methodStatic")));
//
//        // AutocompleteMembers::m
//        suggestions = insertText(file, 11, 30);
//
//        assertThat(suggestions, hasItems("methodStatic"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void method() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 21, 9);

        assertThat(suggestions, hasItems("field", "fieldStatic", "method", "methodStatic", "argument"));
        
        // this.f
        suggestions = insertText(file, 22, 14);

        assertThat(suggestions, hasItems("field", "method"));
        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "argument")));
        
        // AutocompleteMembers.f
        suggestions = insertText(file, 23, 29);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method", "argument")));

        // TODO
//        // this::m
//        suggestions = insertText(file, 10, 15);
//
//        assertThat(suggestions, hasItems("method"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "methodStatic")));
//
//        // AutocompleteMembers::m
//        suggestions = insertText(file, 11, 30);
//
//        assertThat(suggestions, hasItems("methodStatic"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void staticInitBlock() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 15, 9);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));
        
        // AutocompleteMembers.f
        suggestions = insertText(file, 16, 29);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method")));

        // TODO
//        // AutocompleteMembers::m
//        suggestions = insertText(file, 17, 30);
//
//        assertThat(suggestions, hasItems("methodStatic"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }

    @Test
    public void staticMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteMembers.java";

        // f
        Set<String> suggestions = insertText(file, 29, 9);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "argument"));
        assertThat(suggestions, not(hasItems("field", "method")));
        
        // AutocompleteMembers.f
        suggestions = insertText(file, 30, 29);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic"));
        assertThat(suggestions, not(hasItems("field", "method", "argument")));

        // TODO
//        // AutocompleteMembers::m
//        suggestions = insertText(file, 17, 30);
//
//        assertThat(suggestions, hasItems("methodStatic"));
//        assertThat(suggestions, not(hasItems("field", "fieldStatic", "method")));
    }
    
    @Test
    public void order() throws IOException {
        String file = "/org/javacs/example/AutocompleteOrder.java";

        // this.
        Set<String> suggestions = items(file, 4, 26).stream().map(i -> i.getSortText()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("0/getMethod()", "1/getInheritedMethod()", "2/getClass()"));
        
        // identifier
        suggestions = items(file, 6, 9).stream().map(i -> i.getSortText()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("0/localVariable", "0/parameter", "1/test(String parameter)", "2/AutocompleteOrder"));
    }

    @Test
    public void otherMethod() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // new AutocompleteMember().
        Set<String> suggestions = insertText(file, 4, 33);

        assertThat(suggestions, not(hasItems("fieldStatic", "methodStatic", "class")));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldPrivate", "methodPrivate")));
        assertThat(suggestions, hasItems("field", "method", "getClass"));
    }

    @Test
    public void otherStatic() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // new AutocompleteMember().
        Set<String> suggestions = insertText(file, 6, 27);

        assertThat(suggestions, hasItems("fieldStatic", "methodStatic", "class"));
        assertThat(suggestions, not(hasItems("fieldStaticPrivate", "methodStaticPrivate")));
        assertThat(suggestions, not(hasItems("fieldPrivate", "methodPrivate")));
        assertThat(suggestions, not(hasItems("field", "method", "getClass")));
    }

    @Test
    public void otherClass() throws IOException {
        String file = "/org/javacs/example/AutocompleteOther.java";

        // Name of class
        Set<String> suggestions = insertText(file, 5, 20);

        // String is in root scope, List is in import java.util.*
        assertThat(suggestions, hasItems("AutocompleteOther", "AutocompleteMember", "String", "List"));
    }

    @Test
    public void fromClasspath() throws IOException {
        String file = "/org/javacs/example/AutocompleteFromClasspath.java";

        // Static method
        Set<String> suggestions = items(file, 8, 17).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("add(E)", "add(int, E)"));
    }

    @Test
    public void betweenLines() throws IOException {
        String file = "/org/javacs/example/AutocompleteBetweenLines.java";

        // Static method
        Set<String> suggestions = insertText(file, 8, 17);

        assertThat(suggestions, hasItems("add"));
    }

    @Test
    @Ignore
    public void reference() throws IOException {
        String file = "/org/javacs/example/AutocompleteReference.java";

        // Static method
        Set<String> suggestions = insertText(file, 2, 14);

        assertThat(suggestions, not(hasItems("methodStatic")));
        assertThat(suggestions, hasItems("method", "getClass"));
    }

    @Test
    public void docstring() throws IOException {
        String file = "/org/javacs/example/AutocompleteDocstring.java";

        Set<String> docstrings = documentation(file, 7, 14);

        assertThat(docstrings, hasItems("A method", "A field"));

        docstrings = documentation(file, 11, 31);

        assertThat(docstrings, hasItems("A fieldStatic", "A methodStatic"));
    }

    @Test
    public void classes() throws IOException {
        String file = "/org/javacs/example/AutocompleteClasses.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 9);

        assertThat(suggestions, hasItems("String", "SomeInnerClass"));
    }

    @Test
    public void editMethodName() throws IOException {
        String file = "/org/javacs/example/AutocompleteEditMethodName.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 20);

        assertThat(suggestions, hasItems("getClass"));
    }

    @Test
    public void restParams() throws IOException {
        String file = "/org/javacs/example/AutocompleteRest.java";

        // Static method
        Set<String> suggestions = items(file, 4, 17).stream().map(i -> i.getLabel()).collect(Collectors.toSet());

        assertThat(suggestions, hasItems("restMethod(String... params)"));
    }

    @Test
    public void constructor() throws IOException {
        String file = "/org/javacs/example/AutocompleteConstructor.java";

        // Static method
        Set<String> suggestions = insertText(file, 4, 16);

        assertThat(suggestions, hasItems("AutocompleteConstructor", "String"));
    }

    @Test
    public void importPackage() throws IOException {
        String file = "/org/javacs/example/AutocompletePackage.java";

        // Static method
        Set<String> suggestions = insertText(file, 2, 11);

        assertThat(suggestions, hasItems("javacs"));
    }

    @Test
    public void importClass() throws IOException {
        String file = "/org/javacs/example/AutocompletePackage.java";

        // Static method
        Set<String> suggestions = insertText(file, 3, 26);

        assertThat(suggestions, hasItems("AutocompletePackage"));
    }

    @Test
    public void outerClass() throws IOException {
        String file = "/org/javacs/example/AutocompleteOuter.java";

        // Initializer of static inner class
        Set<String> suggestions = insertText(file, 11, 13);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, not(hasItems("method", "field")));

        // Initializer of inner class
        suggestions = insertText(file, 17, 13);

        assertThat(suggestions, hasItems("methodStatic", "fieldStatic"));
        assertThat(suggestions, hasItems("method", "field"));
    }

    @Test
    public void innerDeclaration() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        Set<String> suggestions = insertText(file, 4, 28);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, hasItem("InnerEnum"));

        suggestions = insertText(file, 5, 9);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerNew() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        Set<String> suggestions = insertText(file, 9, 32);

        assertThat("suggests qualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests qualified inner enum declaration", suggestions, not(hasItem("InnerEnum")));

        suggestions = insertText(file, 10, 13);

        assertThat("suggests unqualified inner class declaration", suggestions, hasItem("InnerClass"));
        assertThat("suggests unqualified inner enum declaration", suggestions, hasItem("InnerEnum"));
    }

    @Test
    public void innerEnum() throws IOException {
        String file = "/org/javacs/example/AutocompleteInners.java";

        Set<String> suggestions = insertText(file, 14, 39);

        assertThat("suggests enum constants", suggestions, hasItems("Foo", "Bar"));
    }

    private Set<String> insertText(String file, int row, int column) throws IOException {
        List<? extends CompletionItem> items = items(file, row, column);

        return items
                .stream()
                .map(CompletionItem::getInsertText)
                .collect(Collectors.toSet());
    }

    private Set<String> documentation(String file, int row, int column) throws IOException {
        List<? extends CompletionItem> items = items(file, row, column);

        return items
                .stream()
                .flatMap(i -> {
                    if (i.getDocumentation() != null)
                        return Stream.of(i.getDocumentation().trim());
                    else
                        return Stream.empty();
                })
                .collect(Collectors.toSet());
    }

    private List<? extends CompletionItem> items(String file, int row, int column) {
        TextDocumentPositionParamsImpl position = new TextDocumentPositionParamsImpl();

        position.setPosition(new PositionImpl());
        position.getPosition().setLine(row);
        position.getPosition().setCharacter(column);
        position.setTextDocument(new TextDocumentIdentifierImpl());
        position.getTextDocument().setUri(uri(file).toString());

        JavaLanguageServer server = getJavaLanguageServer();

        return server.autocomplete(position).getItems();
    }

    private URI uri(String file) {
        try {
            return AutocompleteTest.class.getResource(file).toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
