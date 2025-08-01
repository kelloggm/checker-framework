package org.checkerframework.framework.type;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable;
import org.checkerframework.javacutil.TypesUtils;

/** TypeVariableSubstitutor replaces type variables from a declaration with arguments to its use. */
public class TypeVariableSubstitutor {

  /** Create a TypeVariableSubstitutor. */
  public TypeVariableSubstitutor() {}

  /**
   * Given a mapping from type variable to its type argument, replace each instance of a type
   * variable with a copy of type argument.
   *
   * @see #substituteTypeVariable(AnnotatedTypeMirror,
   *     org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable)
   * @param typeVarToTypeArgument a mapping from type variable to its type argument
   * @param type the type to substitute
   * @return a copy of type with its type variables substituted
   */
  public AnnotatedTypeMirror substitute(
      Map<TypeVariable, AnnotatedTypeMirror> typeVarToTypeArgument, AnnotatedTypeMirror type) {
    return new Visitor(typeVarToTypeArgument, true).visit(type);
  }

  /**
   * Given a mapping from type variable to its type argument, replace each instance of a type
   * variable with the given type argument.
   *
   * @see #substituteTypeVariable(AnnotatedTypeMirror,
   *     org.checkerframework.framework.type.AnnotatedTypeMirror.AnnotatedTypeVariable)
   * @param typeVarToTypeArgument a mapping from type variable to its type argument
   * @param type the type to substitute
   * @return a copy of type with its type variables substituted
   */
  public AnnotatedTypeMirror substituteWithoutCopyingTypeArguments(
      Map<TypeVariable, AnnotatedTypeMirror> typeVarToTypeArgument, AnnotatedTypeMirror type) {
    return new Visitor(typeVarToTypeArgument, false).visit(type);
  }

  /**
   * Given the types of a type parameter declaration, the argument to that type parameter
   * declaration, and a given use of that declaration, return a substitute for the use with the
   * correct annotations.
   *
   * <p>To determine what primary annotations are correct for the substitute the following rules are
   * used: If the type variable use has a primary annotation then apply that primary annotation to
   * the substitute. Otherwise, use the annotations of the argument.
   *
   * @param argument the argument to declaration (this will be a value in typeParamToArg)
   * @param use the use that is being replaced
   * @return a deep copy of argument with the appropriate annotations applied
   */
  protected AnnotatedTypeMirror substituteTypeVariable(
      AnnotatedTypeMirror argument, AnnotatedTypeVariable use) {
    AnnotatedTypeMirror substitute = argument.deepCopy(true);
    substitute.addAnnotations(argument.getPrimaryAnnotationsField());

    if (!use.getPrimaryAnnotationsField().isEmpty()) {
      substitute.replaceAnnotations(use.getPrimaryAnnotations());
    }

    return substitute;
  }

  /**
   * Visitor that makes the substitution. This is an inner class so that its methods cannot be
   * called by clients of {@link TypeVariableSubstitutor}.
   */
  protected class Visitor extends AnnotatedTypeCopier {

    /**
     * A mapping from {@link TypeParameterElement} to the {@link AnnotatedTypeMirror} that should
     * replace its uses.
     */
    private final Map<TypeParameterElement, AnnotatedTypeMirror> elementToArgMap;

    /**
     * A list of type variables that should be replaced by the type mirror at the same index in
     * {@code typeMirrors}
     */
    private final List<TypeVariable> typeVars;

    /**
     * A list of TypeMirrors that should replace the type variable at the same index in {@code
     * typeVars}
     */
    private final List<TypeMirror> typeMirrors;

    /** True if a copy of type argument should be substituted. */
    private final boolean copyArgument;

    /**
     * Creates the Visitor.
     *
     * @param typeParamToArg mapping from TypeVariable to the AnnotatedTypeMirror that will replace
     *     it
     * @param copyArgument true if a copy of type argument should be substituted
     */
    public Visitor(Map<TypeVariable, AnnotatedTypeMirror> typeParamToArg, boolean copyArgument) {
      int size = typeParamToArg.size();
      elementToArgMap = new HashMap<>(size);
      typeVars = new ArrayList<>(size);
      typeMirrors = new ArrayList<>(size);

      for (Map.Entry<TypeVariable, AnnotatedTypeMirror> paramToArg : typeParamToArg.entrySet()) {
        elementToArgMap.put(
            (TypeParameterElement) paramToArg.getKey().asElement(), paramToArg.getValue());
        typeVars.add(paramToArg.getKey());
        typeMirrors.add(paramToArg.getValue().getUnderlyingType());
      }
      this.copyArgument = copyArgument;
    }

    @Override
    protected <T extends AnnotatedTypeMirror> T makeCopy(T original) {
      if (original.getKind() == TypeKind.TYPEVAR) {
        return super.makeCopy(original);
      }
      TypeMirror s =
          TypesUtils.substitute(
              original.getUnderlyingType(),
              typeVars,
              typeMirrors,
              original.atypeFactory.processingEnv);

      @SuppressWarnings("unchecked")
      T copy =
          (T) AnnotatedTypeMirror.createType(s, original.atypeFactory, original.isDeclaration());
      maybeCopyPrimaryAnnotations(original, copy);

      return copy;
    }

    @Override
    public AnnotatedTypeMirror visitTypeVariable(
        AnnotatedTypeVariable original,
        IdentityHashMap<AnnotatedTypeMirror, AnnotatedTypeMirror> originalToCopy) {

      if (visitingExecutableTypeParam) {
        // AnnotatedExecutableType differs from AnnotatedDeclaredType in that its list of
        // type parameters cannot be adapted in place since the
        // AnnotatedExecutable.typeVarTypes field is of type AnnotatedTypeVariable and not
        // AnnotatedTypeMirror.  When substituting, all component types that contain a use
        // of the executable's type parameters will be substituted.  The executable's type
        // parameters will have their bounds substituted but the top-level
        // AnnotatedTypeVariable's will remain
        visitingExecutableTypeParam = false;
        return super.visitTypeVariable(original, originalToCopy);

      } else {
        Element typeVarElem = original.getUnderlyingType().asElement();
        if (elementToArgMap.containsKey(typeVarElem)) {
          AnnotatedTypeMirror argument = elementToArgMap.get(typeVarElem);
          if (copyArgument) {
            return substituteTypeVariable(argument, original);
          } else {
            return argument;
          }
        }
      }

      return super.visitTypeVariable(original, originalToCopy);
    }
  }
}
