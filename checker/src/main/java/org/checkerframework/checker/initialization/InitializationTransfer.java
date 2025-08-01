package org.checkerframework.checker.initialization;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import java.util.ArrayList;
import java.util.List;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import org.checkerframework.dataflow.analysis.RegularTransferResult;
import org.checkerframework.dataflow.analysis.TransferInput;
import org.checkerframework.dataflow.analysis.TransferResult;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.FieldAccessNode;
import org.checkerframework.dataflow.cfg.node.MethodInvocationNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ThisNode;
import org.checkerframework.dataflow.expression.FieldAccess;
import org.checkerframework.dataflow.expression.JavaExpression;
import org.checkerframework.framework.flow.CFAbstractAnalysis;
import org.checkerframework.framework.flow.CFAbstractTransfer;
import org.checkerframework.framework.flow.CFAbstractValue;
import org.checkerframework.framework.type.AnnotatedTypeMirror;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedDeclaredType;
import org.checkerframework.javacutil.TreePathUtil;
import org.checkerframework.javacutil.TreeUtils;

/**
 * A transfer function that extends {@link CFAbstractTransfer} and tracks {@link
 * InitializationStore}s. In addition to the features of {@link CFAbstractTransfer}, this transfer
 * function also tracks which fields of the current class ('self' receiver) have been initialized.
 *
 * <p>More precisely, the following refinements are performed:
 *
 * <ol>
 *   <li>After the call to a constructor ({@code this()} call), all non-null fields of the current
 *       class can safely be considered initialized.
 *   <li>After a method call with a postcondition that ensures a field to be non-null, that field
 *       can safely be considered initialized (this is done in {@link
 *       InitializationStore#insertValue(JavaExpression, CFAbstractValue)}).
 *   <li>All non-null fields with an initializer can be considered initialized (this is done in
 *       {@link InitializationStore#insertValue(JavaExpression, CFAbstractValue)}).
 *   <li>After the call to a super constructor ("super()" call), all non-null fields of the super
 *       class can safely be considered initialized.
 * </ol>
 *
 * @see InitializationStore
 * @param <V> the type of the abstract value
 * @param <T> the type of the transfer function
 * @param <S> the type of the store
 */
public class InitializationTransfer<
        V extends CFAbstractValue<V>,
        T extends InitializationTransfer<V, T, S>,
        S extends InitializationStore<V, S>>
    extends CFAbstractTransfer<V, S, T> {

  protected final InitializationAnnotatedTypeFactory<?, ?, ?, ?> atypeFactory;

  public InitializationTransfer(CFAbstractAnalysis<V, S, T> analysis) {
    super(analysis);
    this.atypeFactory = (InitializationAnnotatedTypeFactory<?, ?, ?, ?>) analysis.getTypeFactory();
  }

  @Override
  protected boolean isNotFullyInitializedReceiver(MethodTree methodTree) {
    if (super.isNotFullyInitializedReceiver(methodTree)) {
      return true;
    }
    AnnotatedDeclaredType receiverType =
        analysis.getTypeFactory().getAnnotatedType(methodTree).getReceiverType();
    if (receiverType != null) {
      return atypeFactory.isUnknownInitialization(receiverType)
          || atypeFactory.isUnderInitialization(receiverType);
    } else {
      // There is no receiver e.g. in static methods.
      return false;
    }
  }

  /**
   * Returns the fields that can safely be considered initialized after the method call {@code
   * node}.
   *
   * @param node a method call
   * @return the fields that are initialized after the method call
   */
  protected List<VariableElement> initializedFieldsAfterCall(MethodInvocationNode node) {
    List<VariableElement> result = new ArrayList<>();
    MethodInvocationTree tree = node.getTree();
    ExecutableElement method = TreeUtils.elementFromUse(tree);
    boolean isConstructor = method.getSimpleName().contentEquals("<init>");
    Node receiver = node.getTarget().getReceiver();
    String methodString = tree.getMethodSelect().toString();

    // Case 1: After a call to the constructor of the same class, all
    // invariant fields are guaranteed to be initialized.
    if (isConstructor && receiver instanceof ThisNode && methodString.equals("this")) {
      ClassTree clazz = TreePathUtil.enclosingClass(analysis.getTypeFactory().getPath(tree));
      TypeElement clazzElem = TreeUtils.elementFromDeclaration(clazz);
      markInvariantFieldsAsInitialized(result, clazzElem);
    }

    // Case 4: After a call to the constructor of the super class, all
    // invariant fields of any super class are guaranteed to be initialized.
    if (isConstructor && receiver instanceof ThisNode && methodString.equals("super")) {
      ClassTree clazz = TreePathUtil.enclosingClass(analysis.getTypeFactory().getPath(tree));
      TypeElement clazzElem = TreeUtils.elementFromDeclaration(clazz);
      TypeMirror superClass = clazzElem.getSuperclass();

      while (superClass != null && superClass.getKind() != TypeKind.NONE) {
        clazzElem = (TypeElement) analysis.getTypes().asElement(superClass);
        superClass = clazzElem.getSuperclass();
        markInvariantFieldsAsInitialized(result, clazzElem);
      }
    }

    return result;
  }

  /**
   * Adds all the fields of the class {@code clazzElem} that have the 'invariant annotation' to the
   * set of initialized fields {@code result}.
   */
  protected void markInvariantFieldsAsInitialized(
      List<VariableElement> result, TypeElement clazzElem) {
    List<VariableElement> fields = ElementFilter.fieldsIn(clazzElem.getEnclosedElements());
    for (VariableElement field : fields) {
      if (((Symbol) field).type.tsym.completer != Symbol.Completer.NULL_COMPLETER
          || ((Symbol) field).type.getKind() == TypeKind.ERROR) {
        // If the type is not completed yet, we might run into trouble. Skip the field.
        // TODO: is there a nicer solution?
        // This was raised by Issue #244.
        continue;
      }
      AnnotatedTypeMirror fieldType = atypeFactory.getAnnotatedType(field);
      if (atypeFactory.hasFieldInvariantAnnotation(fieldType, field)) {
        result.add(field);
      }
    }
  }

  @Override
  public TransferResult<V, S> visitAssignment(AssignmentNode n, TransferInput<V, S> in) {
    TransferResult<V, S> result = super.visitAssignment(n, in);
    JavaExpression lhs = JavaExpression.fromNode(n.getTarget());

    // If this is an assignment to a field of 'this', then mark the field as initialized.
    if (!lhs.containsUnknown() && lhs instanceof FieldAccess) {
      FieldAccess fa = (FieldAccess) lhs;
      // Only a ternary expression may cause a conditional transfer result, e.g.
      //      condExpr#num0 = (obj instanceof List)
      // In such cases, the LHS is never a FieldAccess, so we can assert that result
      // is a regular transfer result. This is important because otherwise the
      // addInitializedField would be called on a temporary, merged store.
      assert result instanceof RegularTransferResult;
      result.getRegularStore().addInitializedField(fa);
    }
    return result;
  }

  /**
   * If an invariant field is initialized and has the invariant annotation, then it has at least the
   * invariant annotation. Note that only fields of the 'this' receiver are tracked for
   * initialization.
   */
  @Override
  public TransferResult<V, S> visitFieldAccess(FieldAccessNode n, TransferInput<V, S> p) {
    TransferResult<V, S> result = super.visitFieldAccess(n, p);
    assert !result.containsTwoStores();
    S store = result.getRegularStore();
    if (store.isFieldInitialized(n.getElement()) && n.getReceiver() instanceof ThisNode) {
      AnnotatedTypeMirror fieldAnno = analysis.getTypeFactory().getAnnotatedType(n.getElement());
      // Only if the field has the type system's invariant annotation,
      // such as @NonNull.
      if (fieldAnno.hasPrimaryAnnotation(atypeFactory.getFieldInvariantAnnotation())) {
        AnnotationMirror inv = atypeFactory.getFieldInvariantAnnotation();
        V oldResultValue = result.getResultValue();
        V refinedResultValue =
            analysis.createSingleAnnotationValue(inv, oldResultValue.getUnderlyingType());
        V newResultValue = refinedResultValue.mostSpecific(oldResultValue, null);
        return recreateTransferResult(newResultValue, result);
      }
    }
    return result;
  }

  @Override
  public TransferResult<V, S> visitMethodInvocation(
      MethodInvocationNode n, TransferInput<V, S> in) {
    TransferResult<V, S> result = super.visitMethodInvocation(n, in);
    List<VariableElement> newlyInitializedFields = initializedFieldsAfterCall(n);
    if (!newlyInitializedFields.isEmpty()) {
      for (VariableElement f : newlyInitializedFields) {
        result.getThenStore().addInitializedField(f);
        result.getElseStore().addInitializedField(f);
      }
    }
    return result;
  }
}
