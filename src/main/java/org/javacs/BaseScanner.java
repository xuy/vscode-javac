package org.javacs;

import com.sun.source.util.TreePath;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeScanner;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;

public class BaseScanner extends TreeScanner {
    protected final Context context;
    protected JCTree.JCCompilationUnit compilationUnit;

    public BaseScanner(Context context) {
        this.context = context;
    }

    @Override
    public void visitTopLevel(JCTree.JCCompilationUnit tree) {
        this.compilationUnit = tree;
        super.visitTopLevel(tree);
    }

    @Override
    public void scan(JCTree node) {
        if (node != null) {
            node.accept(this);
        }
    }

    @Override
    public void scan(List<? extends JCTree> nodes) {
        if (nodes != null) {
            for (JCTree node : nodes) {
                scan(node);
            }
        }
    }

    @Override
    public void visitErroneous(JCTree.JCErroneous tree) {
        scan(tree.errs);
    }
}
