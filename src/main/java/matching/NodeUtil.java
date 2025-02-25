package matching;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.Tree;
import org.antlr.v4.runtime.tree.Trees;
import research.diffsearch.tree.TreeUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Utility methods to handle parse tree nodes.
 * Some of them are specific to the grammar and/or language.
 */
public class NodeUtil {

    private Parser queryParser;
    private Parser changeParser;

    public NodeUtil(Parser queryParser, Parser changeParser) {
        this.queryParser = queryParser;
        this.changeParser = changeParser;
    }

    // TODO: get this from the grammar or some other place
    private static Set<String> placeholderNames = new HashSet<>(Arrays.asList("LT", "ID", "binOP", "OP", "unOP", "EXPR"));

    public enum Kind {
        UNNAMED_PLACEHOLDER, NAMED_PLACEHOLDER, NORMAL, WILDCARD, EMPTY
    }

    public Kind getKind(Tree t) {
        String text = TreeUtils.getCompleteNodeText(t, Arrays.asList(changeParser.getRuleNames()));
        if (text.equals("<...>")) {
            return Kind.WILDCARD;
        } else if (t.getChildCount() == 0 && text.equals("_")) {
            return Kind.EMPTY;
        } else if (t.getChildCount() == 0) {
            if (placeholderNames.contains(text))
                return Kind.UNNAMED_PLACEHOLDER;
            for (String n : placeholderNames) {
                if (text.startsWith(n) && text.contains("<") && text.endsWith(">")) {
                    return Kind.NAMED_PLACEHOLDER;
                }
            }
        }
        return Kind.NORMAL;
    }

    public String namedPlaceholderToString(Tree t) {
        return Trees.getNodeText(t, queryParser);
    }

    public String querySubtreeToString(Tree t) {
        return Trees.toStringTree(t, queryParser);
    }

    public String changeSubtreeToString(Tree t) {
        return Trees.toStringTree(t, changeParser);
    }

    public boolean isMatchingNormalNode(Tree k, Tree v) {
        String kText = Trees.getNodeText(k, queryParser);
        String vText = Trees.getNodeText(v, changeParser);
        return kText.equals(vText);
    }

    public boolean isMatchingPlaceholder(Tree k, Tree v) {
        // Note: the following checks are brittle w.r.t. changes of the grammar and may be incomplete
        String kText = Trees.getNodeText(k, queryParser);
        String vText = Trees.getNodeText(v, changeParser);
        String vParentText = Trees.getNodeText(v.getParent(), changeParser);

        if (kText.equals("LT") || kText.matches("LT<[0-9]+>")) {
            return v.getParent() != null && (vParentText.equals("literal") || vParentText.equals("atom"));
        } else if (kText.equals("ID") || kText.matches("ID<[0-9]+>")) {
            return v.getChildCount() == 0;
        } else if (kText.equals("binOP") || kText.matches("binOP<[0-9]+>")) {
            // return vText.equals("binary_operators");
            return v.getParent() != null && (vParentText.equals("binary_operators")
                                             || vParentText.equals("binOperator")
                                             || vParentText.equals("bin_op"));
        } else if (kText.equals("OP") || kText.matches("OP<[0-9]+>")) {
            return vParentText.equals("binary_operators")
                   || vParentText.equals("assign_operators")
                   || vParentText.equals("assignmentOperator")
                   || vParentText.equals("expr_stmt");
        } else if (kText.equals("unOP") || kText.matches("unOP<[0-9]+>")) {
            return vParentText.equals("unary_prefix_operators")
                   || vParentText.equals("unary_postfix_operators");
        } else if (kText.equals("EXPR") || kText.matches("EXPR<[0-9]+>")) {
            return vText.equals("expression") || vText.equals("expr")
                   || vParentText.equals("expression")//v.getChildCount() == 0;
                   || vParentText.equals("methodCall")
                   || vParentText.equals("singleExpression");
        }
        throw new IllegalArgumentException("Unexpected node label " + kText);
    }

    public boolean isMatchingEmpty(Tree k, Tree v) {
        // Note: the following check is brittle w.r.t. changes of the grammar
        return getKind(k) == Kind.EMPTY &&
               v.getChildCount() == 0 &&
               Trees.getNodeText(v, changeParser).equals("multipleStatements");
    }

    public Tree extractOldSubtree(Tree t) {
        Tree leftQuerySnippet = t.getChild(0);
        return leftQuerySnippet.getChild(0);
    }

    public Tree extractNewSubtree(Tree t) {
        // Managing the case in which there is a NEWLINE child

        int n = 2;

        while (t.getChild(n).toStringTree().equals("\n")
               || Trees.getNodeText(t.getChild(n), changeParser).equals("\n")) {
            n++;
        }
        Tree leftQuerySnippet = t.getChild(n);


        return leftQuerySnippet.getChild(0);
    }

}
