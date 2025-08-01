package org.checkerframework.dataflow.util;

import com.sun.source.tree.MethodTree;
import java.util.EnumSet;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.ExecutableElement;
import org.checkerframework.dataflow.qual.Deterministic;
import org.checkerframework.dataflow.qual.Pure;
import org.checkerframework.dataflow.qual.SideEffectFree;
import org.checkerframework.javacutil.AnnotationProvider;
import org.checkerframework.javacutil.BugInCF;
import org.checkerframework.javacutil.ElementUtils;
import org.checkerframework.javacutil.TreeUtils;

/**
 * A utility class for working with the {@link SideEffectFree}, {@link Deterministic}, and {@link
 * Pure} annotations.
 *
 * @see SideEffectFree
 * @see Deterministic
 * @see Pure
 */
public class PurityUtils {

  /** Do not instantiate. */
  private PurityUtils() {
    throw new Error("Do not instantiate PurityUtils.");
  }

  /** Represents a method that is both deterministic and side-effect free. */
  private static final EnumSet<Pure.Kind> detAndSeFree =
      EnumSet.of(Pure.Kind.DETERMINISTIC, Pure.Kind.SIDE_EFFECT_FREE);

  /**
   * Does the method {@code methodTree} have any purity annotation?
   *
   * @param provider how to get annotations
   * @param methodTree a method to test
   * @return true if the method has any purity annotations
   */
  public static boolean hasPurityAnnotation(AnnotationProvider provider, MethodTree methodTree) {
    return !getPurityKinds(provider, methodTree).isEmpty();
  }

  /**
   * Does the method {@code methodElement} have any purity annotation?
   *
   * @param provider how to get annotations
   * @param methodElement a method to test
   * @return true if the method has any purity annotations
   */
  public static boolean hasPurityAnnotation(
      AnnotationProvider provider, ExecutableElement methodElement) {
    return !getPurityKinds(provider, methodElement).isEmpty();
  }

  /**
   * Is the method {@code methodTree} deterministic?
   *
   * @param provider how to get annotations
   * @param methodTree a method to test
   * @return true if the method is deterministic
   */
  public static boolean isDeterministic(AnnotationProvider provider, MethodTree methodTree) {
    ExecutableElement methodElement = TreeUtils.elementFromDeclaration(methodTree);
    if (methodElement == null) {
      throw new BugInCF("Could not find element for tree: " + methodTree);
    }
    return isDeterministic(provider, methodElement);
  }

  /**
   * Is the method {@code methodElement} deterministic?
   *
   * @param provider how to get annotations
   * @param methodElement a method to test
   * @return true if the method is deterministic
   */
  public static boolean isDeterministic(
      AnnotationProvider provider, ExecutableElement methodElement) {
    EnumSet<Pure.Kind> kinds = getPurityKinds(provider, methodElement);
    return kinds.contains(Pure.Kind.DETERMINISTIC);
  }

  /**
   * Is the method {@code methodTree} side-effect-free?
   *
   * <p>This method does not use, and has different semantics than, {@link
   * AnnotationProvider#isSideEffectFree}. This method is concerned only with standard purity
   * annotations.
   *
   * @param provider how to get annotations
   * @param methodTree a method to test
   * @return true if the method is side-effect-free
   * @deprecated use {@link AnnotationProvider#isSideEffectFree}
   */
  @Deprecated // 2022-09-27
  public static boolean isSideEffectFree(AnnotationProvider provider, MethodTree methodTree) {
    ExecutableElement methodElement = TreeUtils.elementFromDeclaration(methodTree);
    if (methodElement == null) {
      throw new BugInCF("Could not find element for tree: " + methodTree);
    }
    return isSideEffectFree(provider, methodElement);
  }

  /**
   * Is the method {@code methodElement} side-effect-free?
   *
   * <p>This method does not use, and has different semantics than, {@link
   * AnnotationProvider#isSideEffectFree}. This method is concerned only with standard purity
   * annotations.
   *
   * @param provider how to get annotations
   * @param methodElement a method to test
   * @return true if the method is side-effect-free
   */
  public static boolean isSideEffectFree(
      AnnotationProvider provider, ExecutableElement methodElement) {
    EnumSet<Pure.Kind> kinds = getPurityKinds(provider, methodElement);
    return kinds.contains(Pure.Kind.SIDE_EFFECT_FREE);
  }

  /**
   * Returns the purity annotations on the method {@code methodTree}.
   *
   * @param provider how to get annotations. Its {@link AnnotationProvider#isSideEffectFree} and
   *     {@link AnnotationProvider#isDeterministic} methods are not used.
   * @param methodTree a method to test
   * @return the types of purity of the method {@code methodTree}
   */
  public static EnumSet<Pure.Kind> getPurityKinds(
      AnnotationProvider provider, MethodTree methodTree) {
    ExecutableElement methodElement = TreeUtils.elementFromDeclaration(methodTree);
    if (methodElement == null) {
      throw new BugInCF("Could not find element for tree: " + methodTree);
    }
    return getPurityKinds(provider, methodElement);
  }

  /**
   * Returns the purity annotations on the method {@code methodElement}.
   *
   * @param provider how to get annotations. Its {@link AnnotationProvider#isSideEffectFree} and
   *     {@link AnnotationProvider#isDeterministic} methods are not used.
   * @param methodElement a method to test
   * @return the types of purity of the method {@code methodElement}
   */
  public static EnumSet<Pure.Kind> getPurityKinds(
      AnnotationProvider provider, ExecutableElement methodElement) {
    // Special case for record accessors
    if (ElementUtils.isRecordAccessor(methodElement)
        && ElementUtils.isAutoGeneratedRecordMember(methodElement)) {
      return detAndSeFree;
    }

    AnnotationMirror pureAnnotation = provider.getDeclAnnotation(methodElement, Pure.class);
    AnnotationMirror sefAnnotation =
        provider.getDeclAnnotation(methodElement, SideEffectFree.class);
    AnnotationMirror detAnnotation = provider.getDeclAnnotation(methodElement, Deterministic.class);

    if (pureAnnotation != null) {
      return detAndSeFree;
    }
    EnumSet<Pure.Kind> result = EnumSet.noneOf(Pure.Kind.class);
    if (sefAnnotation != null) {
      result.add(Pure.Kind.SIDE_EFFECT_FREE);
    }
    if (detAnnotation != null) {
      result.add(Pure.Kind.DETERMINISTIC);
    }
    return result;
  }
}
