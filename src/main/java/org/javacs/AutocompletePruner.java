package org.javacs;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

import javax.tools.JavaFileObject;

/**
 * Removes all statements after the cursor
 */
// TODO if we ever implement incremental parsing, this will need to make a partial copy of the AST rather than modify it
public class AutocompletePruner extends CursorScanner {
    public AutocompletePruner(JavaFileObject file, long cursor, Context context) {
        super(file, cursor, context);
    }

    @Override
    public void visitBlock(JCTree.JCBlock tree) {
        List<JCTree.JCStatement> stats = tree.stats;

        int countStatements = 0;

        // Scan up to statement containing cursor
        while (countStatements < stats.size()) {
            JCTree.JCStatement s = stats.get(countStatements);
            if (containsCursor(s))
                break;
            else
                s.accept(this);

            countStatements++;
        }

        // Advance over statement containing cursor
        countStatements++;

        // Remove all statements after statement containing cursor
        tree.stats = stats.take(countStatements);
    }
}
