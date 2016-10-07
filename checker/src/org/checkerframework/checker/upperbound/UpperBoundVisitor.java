package org.checkerframework.checker.upperbound;

import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.util.TreePath;
import javax.lang.model.element.AnnotationMirror;
import org.checkerframework.checker.upperbound.qual.LessThanLength;
import org.checkerframework.common.basetype.BaseTypeChecker;
import org.checkerframework.common.basetype.BaseTypeVisitor;
import org.checkerframework.dataflow.analysis.FlowExpressions;
import org.checkerframework.dataflow.analysis.FlowExpressions.Receiver;
import org.checkerframework.framework.source.Result;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.util.FlowExpressionParseUtil;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionContext;
import org.checkerframework.framework.util.FlowExpressionParseUtil.FlowExpressionParseException;
import org.checkerframework.javacutil.InternalUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 *  Warns about array accesses that could be too high.
 */
public class UpperBoundVisitor extends BaseTypeVisitor<UpperBoundAnnotatedTypeFactory> {

    private static final String UPPER_BOUND = "array.access.unsafe.high";

    public UpperBoundVisitor(BaseTypeChecker checker) {
        super(checker);
    }

    /**
     *  When the visitor reachs an array access, it needs to check a couple of things.
     *  First, it checks if the index has been assigned a reasonable UpperBound type:
     *  only an index with type LessThanLength(arr) is safe to access arr.
     *  If that fails, it checks if the access is still safe. To do
     *  so, it checks if the MinLen checker knows the minimum
     *  length of arr by querying the MinLenATF.
     *  If the MinLen of the array is known, the visitor can check if
     *  the index is less than the MinLen, using the Value Checker. If so
     *  then the access is still safe. Otherwise, report a potential unsafe
     *  access.
     */
    @Override
    public Void visitArrayAccess(ArrayAccessTree tree, Void type) {
        ExpressionTree indexTree = tree.getIndex();
        ExpressionTree arrTree = tree.getExpression();

        Receiver arrName = FlowExpressions.internalReprOf(checker.getAnnotationProvider(), arrTree);
        AnnotatedTypeMirror indexType = atypeFactory.getAnnotatedType(indexTree);

        // Need to be able to check these as part of the conditional below.
        // We need the max because we want to know whether the index is
        // less than the minimum length of the array. If it could be any
        // of several values, we want the highest one.
        Integer valMax = atypeFactory.valMaxFromExpressionTree(indexTree);
        Integer minLen = atypeFactory.minLenFromExpressionTree(arrTree);

        // Is indexType LTL of a set containing arrName?
        if (indexType.hasAnnotation(LessThanLength.class)
                && (hasValue(indexType, indexTree, arrName))) {

            // If so, this is safe - get out of here.
            return super.visitArrayAccess(tree, type);
        } else if (valMax != null && minLen != null && valMax < minLen) {
            return super.visitArrayAccess(tree, type);
        } else {
            // Unsafe, since neither the Upper bound or MinLen checks succeeded.
            checker.report(Result.warning(UPPER_BOUND, indexType.toString(), arrName), indexTree);
            return super.visitArrayAccess(tree, type);
        }
    }

    private boolean hasValue(
            AnnotatedTypeMirror indexType, ExpressionTree indexTree, Receiver arrName) {
        TreePath path = atypeFactory.getPath(indexTree);
        ClassTree classTree = TreeUtils.enclosingClass(path);
        assert classTree != null;
        Receiver r = new FlowExpressions.ThisReference(InternalUtils.typeOf(classTree));
        FlowExpressionContext contxt = new FlowExpressionContext(r, null, checker.getContext());
        for (AnnotationMirror anno : indexType.getAnnotations()) {
            String[] expressions = UpperBoundUtils.getValue(anno);
            if (expressions == null) {
                continue;
            }
            for (String expression : expressions) {
                try {
                    Receiver parsedExpression =
                            FlowExpressionParseUtil.parse(expression, contxt, path, true);
                    if (parsedExpression.equals(arrName)) {
                        return true;
                    }
                } catch (FlowExpressionParseException e) {
                    // An annotation on an index might not parse if the expression references
                    // something out of scope.  No need to report the error.
                    // checker.report(e.getResult(), indexTree);
                }
            }
        }
        return false;
    }
}
