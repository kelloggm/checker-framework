package org.checkerframework.javacutil;

import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.api.JavacScope;
import com.sun.tools.javac.code.Kinds;
import com.sun.tools.javac.code.Kinds.KindSelector;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.DeferredAttr;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.comp.Resolve;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Log.DiscardDiagnosticHandler;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

/** A utility class to find symbols corresponding to string references (identifiers). */
// This class reflectively accesses jdk.compiler/com.sun.tools.javac.comp.
// This is why --add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED is required when
// running the Checker Framework.  If this class is re-written, then that --add-opens should be
// removed.
public class Resolver {

  /** Instance of {@link Resolve} for name resolution. */
  private final Resolve resolve;

  /** Instance of {@link Names} for access to the compiler's name table. */
  private final Names names;

  /** Instance of {@link Trees}. */
  private final Trees trees;

  /** Instance of {@link Log} for error logs. */
  private final Log log;

  /** {@code Resolve#findMethod} method. */
  private static final Method FIND_METHOD;

  /** {@code Resolve#findVar} method. */
  private static final Method FIND_VAR;

  /** {@code Resolve#findIdent} method. */
  private static final Method FIND_IDENT;

  /** {@code Resolve#findIdentInType} method. */
  private static final Method FIND_IDENT_IN_TYPE;

  /** {@code Resolve#findIdentInPackage} method. */
  private static final Method FIND_IDENT_IN_PACKAGE;

  /** {@code Resolve#findType} method. */
  private static final Method FIND_TYPE;

  /** {@code com.sun.tools.javac.comp.Resolve$AccessError} class. */
  private static final Class<?> ACCESSERROR;

  /** {@code com.sun.tools.javac.comp.Resolve$AccessError#access} method. */
  // Note that currently access(...) is defined in InvalidSymbolError, a superclass of AccessError
  private static final Method ACCESSERROR_ACCESS;

  /**
   * Method for new Log.DiscardDiagnosticHandler. Before JDK 25, DiscardDiagnosticHandler was a
   * static inner class of Log and an instance of log was passed as the first argument. Starting
   * with JDK 25, DiscardDiagnosticHandler is an inner class of log.
   */
  private static final Constructor<DiscardDiagnosticHandler> NEW_DIAGNOSTIC_HANDLER;

  /** The latest source version supported by this compiler. */
  private static final int sourceVersionNumber =
      Integer.parseInt(SourceVersion.latest().toString().substring("RELEASE_".length()));

  /** True if we are running on at least Java 13. */
  private static final boolean atLeastJava13 = sourceVersionNumber >= 13;

  /** True if we are running on at least Java 23. */
  private static final boolean atLeastJava23 = sourceVersionNumber >= 23;

  static {
    try {
      FIND_METHOD =
          Resolve.class.getDeclaredMethod(
              "findMethod",
              Env.class,
              Type.class,
              Name.class,
              List.class,
              List.class,
              boolean.class,
              boolean.class);
      FIND_METHOD.setAccessible(true);

      if (atLeastJava23) {
        FIND_VAR =
            Resolve.class.getDeclaredMethod(
                "findVar", DiagnosticPosition.class, Env.class, Name.class);
      } else {
        FIND_VAR = Resolve.class.getDeclaredMethod("findVar", Env.class, Name.class);
      }
      FIND_VAR.setAccessible(true);

      if (atLeastJava13) {
        FIND_IDENT =
            Resolve.class.getDeclaredMethod(
                "findIdent", DiagnosticPosition.class, Env.class, Name.class, KindSelector.class);
      } else {
        FIND_IDENT =
            Resolve.class.getDeclaredMethod("findIdent", Env.class, Name.class, KindSelector.class);
      }
      FIND_IDENT.setAccessible(true);

      if (atLeastJava13) {
        FIND_IDENT_IN_TYPE =
            Resolve.class.getDeclaredMethod(
                "findIdentInType",
                DiagnosticPosition.class,
                Env.class,
                Type.class,
                Name.class,
                KindSelector.class);
      } else {
        FIND_IDENT_IN_TYPE =
            Resolve.class.getDeclaredMethod(
                "findIdentInType", Env.class, Type.class, Name.class, KindSelector.class);
      }
      FIND_IDENT_IN_TYPE.setAccessible(true);

      if (atLeastJava13) {
        FIND_IDENT_IN_PACKAGE =
            Resolve.class.getDeclaredMethod(
                "findIdentInPackage",
                DiagnosticPosition.class,
                Env.class,
                TypeSymbol.class,
                Name.class,
                KindSelector.class);
      } else {
        FIND_IDENT_IN_PACKAGE =
            Resolve.class.getDeclaredMethod(
                "findIdentInPackage", Env.class, TypeSymbol.class, Name.class, KindSelector.class);
      }
      FIND_IDENT_IN_PACKAGE.setAccessible(true);

      FIND_TYPE = Resolve.class.getDeclaredMethod("findType", Env.class, Name.class);
      FIND_TYPE.setAccessible(true);

      // Pre JDK 25:
      //   new Log.DiscardDiagnosticHandler(log)
      // JDK 25:
      //   log.new DiscardDiagnosticHandler()
      // But both of those are reflectively accessed the same way.
      NEW_DIAGNOSTIC_HANDLER = Log.DiscardDiagnosticHandler.class.getConstructor(Log.class);
      NEW_DIAGNOSTIC_HANDLER.setAccessible(true);
    } catch (Exception e) {
      Error err =
          new AssertionError("Compiler 'Resolve' class doesn't contain required 'find*' method");
      err.initCause(e);
      throw err;
    }

    try {
      ACCESSERROR = Class.forName("com.sun.tools.javac.comp.Resolve$AccessError");
      ACCESSERROR_ACCESS = ACCESSERROR.getMethod("access", Name.class, TypeSymbol.class);
      ACCESSERROR_ACCESS.setAccessible(true);
    } catch (ClassNotFoundException e) {
      throw new BugInCF("Compiler 'Resolve$AccessError' class could not be retrieved.", e);
    } catch (NoSuchMethodException e) {
      throw new BugInCF(
          "Compiler 'Resolve$AccessError' class doesn't contain required 'access' method", e);
    }
  }

  public Resolver(ProcessingEnvironment env) {
    Context context = ((JavacProcessingEnvironment) env).getContext();
    this.resolve = Resolve.instance(context);
    this.names = Names.instance(context);
    this.trees = Trees.instance(env);
    this.log = Log.instance(context);
  }

  /**
   * Determine the environment for the given path.
   *
   * @param path the tree path to the local scope
   * @return the corresponding attribution environment
   */
  public Env<AttrContext> getEnvForPath(TreePath path) {
    TreePath iter = path;
    JavacScope scope = null;
    while (scope == null && iter != null) {
      try {
        scope = (JavacScope) trees.getScope(iter);
      } catch (NullPointerException t) {
        // This statement fixes https://github.com/typetools/checker-framework/issues/1059 .
        // It work around the crash by skipping through the TreePath until something doesn't
        // crash. This probably returns the class scope, so users might not get the
        // variables they expect. But that is better than crashing.
        iter = iter.getParentPath();
      }
    }
    if (scope != null) {
      return scope.getEnv();
    } else {
      throw new BugInCF("Could not determine any possible scope for path: " + path.getLeaf());
    }
  }

  /**
   * Finds the package with name {@code name}.
   *
   * @param name the name of the package
   * @param path the tree path to the local scope
   * @return the {@code PackageSymbol} for the package if it is found, {@code null} otherwise
   */
  public @Nullable PackageSymbol findPackage(String name, TreePath path) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();

    try {
      Env<AttrContext> env = getEnvForPath(path);
      final Element res;
      if (atLeastJava13) {
        res = resolve(FIND_IDENT, null, env, names.fromString(name), Kinds.KindSelector.PCK);
      } else {
        res = resolve(FIND_IDENT, env, names.fromString(name), Kinds.KindSelector.PCK);
      }

      // findIdent will return a PackageSymbol even for a symbol that is not a package,
      // such as a.b.c.MyClass.myStaticField. "exists()" must be called on it to ensure
      // that it exists.
      if (res.getKind() == ElementKind.PACKAGE) {
        PackageSymbol ps = (PackageSymbol) res;
        return ps.exists() ? ps : null;
      } else {
        return null;
      }
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Finds the field with name {@code name} in {@code type} or a superclass or superinterface of
   * {@code type}.
   *
   * <p>The method adheres to all the rules of Java's scoping (while also considering the imports)
   * for name resolution.
   *
   * @param name the name of the field
   * @param type the type of the receiver (i.e., the type in which to look for the field)
   * @param path the tree path to the local scope
   * @return the element for the field, {@code null} otherwise
   */
  public @Nullable VariableElement findField(String name, TypeMirror type, TreePath path) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();
    try {
      Env<AttrContext> env = getEnvForPath(path);
      final Element res;
      if (atLeastJava13) {
        res =
            resolve(
                FIND_IDENT_IN_TYPE,
                null,
                env,
                type,
                names.fromString(name),
                Kinds.KindSelector.VAR);
      } else {
        res =
            resolve(FIND_IDENT_IN_TYPE, env, type, names.fromString(name), Kinds.KindSelector.VAR);
      }

      if (res.getKind().isField()) {
        return (VariableElement) res;
      } else if (res.getKind() == ElementKind.OTHER && ACCESSERROR.isInstance(res)) {
        // Return the inaccessible field that was found
        return (VariableElement) invokeNoException(res, ACCESSERROR_ACCESS, null, null);
      } else {
        // Most likely didn't find the field and the Element is a SymbolNotFoundError
        return null;
      }
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Finds the local variable (including formal parameters) with name {@code name} in the given
   * scope.
   *
   * @param name the name of the local variable
   * @param path the tree path to the local scope
   * @return the element for the local variable, {@code null} otherwise
   */
  public @Nullable VariableElement findLocalVariableOrParameter(String name, TreePath path) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();
    try {
      Env<AttrContext> env = getEnvForPath(path);
      // Either a VariableElement or a SymbolNotFoundError.
      Element res;
      if (atLeastJava23) {
        res = resolve(FIND_VAR, null, env, names.fromString(name));
      } else {
        res = resolve(FIND_VAR, env, names.fromString(name));
      }
      // Every kind in the documentation of Element.getKind() is explicitly tested, possibly
      // in the "default:" case.
      switch (res.getKind()) {
        case EXCEPTION_PARAMETER:
        case LOCAL_VARIABLE:
        case PARAMETER:
        case RESOURCE_VARIABLE:
          return (VariableElement) res;
        case ENUM_CONSTANT:
        case FIELD:
          return null;
        default:
          if (ElementUtils.isBindingVariable(res)) {
            return (VariableElement) res;
          }
          if (res instanceof VariableElement) {
            throw new BugInCF("unhandled variable ElementKind " + res.getKind());
          }
          // The Element might be a SymbolNotFoundError.
          return null;
      }
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Finds the class literal with name {@code name}.
   *
   * <p>The method adheres to all the rules of Java's scoping (while also considering the imports)
   * for name resolution.
   *
   * @param name the name of the class
   * @param path the tree path to the local scope
   * @return the element for the class
   */
  public Element findClass(String name, TreePath path) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();
    try {
      Env<AttrContext> env = getEnvForPath(path);
      return resolve(FIND_TYPE, env, names.fromString(name));
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Finds the class with name {@code name} in a given package.
   *
   * @param name the name of the class
   * @param pck the PackageSymbol for the package
   * @param path the tree path to the local scope
   * @return the {@code ClassSymbol} for the class if it is found, {@code null} otherwise
   */
  public @Nullable ClassSymbol findClassInPackage(String name, PackageSymbol pck, TreePath path) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();
    try {
      Env<AttrContext> env = getEnvForPath(path);
      final Element res;
      if (atLeastJava13) {
        res =
            resolve(
                FIND_IDENT_IN_PACKAGE,
                null,
                env,
                pck,
                names.fromString(name),
                Kinds.KindSelector.TYP);
      } else {
        res =
            resolve(
                FIND_IDENT_IN_PACKAGE, env, pck, names.fromString(name), Kinds.KindSelector.TYP);
      }

      if (ElementUtils.isTypeElement(res)) {
        return (ClassSymbol) res;
      } else {
        return null;
      }
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Finds the method element for a given name and list of expected parameter types.
   *
   * <p>The method adheres to all the rules of Java's scoping (while also considering the imports)
   * for name resolution.
   *
   * <p>(This method takes into account autoboxing.)
   *
   * <p>This method is a wrapper around {@code com.sun.tools.javac.comp.Resolve.findMethod}.
   *
   * @param methodName name of the method to find
   * @param receiverType type of the receiver of the method
   * @param path tree path
   * @param argumentTypes types of arguments passed to the method call
   * @return the method element (if found)
   */
  public @Nullable ExecutableElement findMethod(
      String methodName,
      TypeMirror receiverType,
      TreePath path,
      java.util.List<TypeMirror> argumentTypes) {
    Log.DiagnosticHandler discardDiagnosticHandler = newDiagnosticHandler();
    try {
      Env<AttrContext> env = getEnvForPath(path);

      Type site = (Type) receiverType;
      Name name = names.fromString(methodName);
      List<Type> argtypes = List.nil();
      for (TypeMirror a : argumentTypes) {
        argtypes = argtypes.append((Type) a);
      }
      List<Type> typeargtypes = List.nil();
      boolean allowBoxing = true;
      boolean useVarargs = false;

      try {
        // For some reason we have to set our own method context, which is rather ugly.
        // TODO: find a nicer way to do this.
        Object methodContext = buildMethodContext();
        Object oldContext = getField(resolve, "currentResolutionContext");
        setField(resolve, "currentResolutionContext", methodContext);
        Element resolveResult =
            resolve(FIND_METHOD, env, site, name, argtypes, typeargtypes, allowBoxing, useVarargs);
        setField(resolve, "currentResolutionContext", oldContext);
        ExecutableElement methodResult;
        if (resolveResult.getKind() == ElementKind.METHOD
            || resolveResult.getKind() == ElementKind.CONSTRUCTOR) {
          methodResult = (ExecutableElement) resolveResult;
        } else if (resolveResult.getKind() == ElementKind.OTHER
            && ACCESSERROR.isInstance(resolveResult)) {
          // Return the inaccessible method that was found.
          methodResult =
              (ExecutableElement) invokeNoException(resolveResult, ACCESSERROR_ACCESS, null, null);
        } else {
          methodResult = null;
        }
        return methodResult;
      } catch (Throwable t) {
        Error err =
            new AssertionError(
                String.format(
                    "Unexpected reflection error in findMethod(%s, %s, ..., %s)",
                    methodName,
                    receiverType,
                    // path
                    argumentTypes));
        err.initCause(t);
        throw err;
      }
    } finally {
      log.popDiagnosticHandler(discardDiagnosticHandler);
    }
  }

  /**
   * Build an instance of {@code Resolve$MethodResolutionContext}.
   *
   * @return a MethodResolutionContext
   * @throws ClassNotFoundException if there is trouble constructing the instance
   * @throws InstantiationException if there is trouble constructing the instance
   * @throws IllegalAccessException if there is trouble constructing the instance
   * @throws InvocationTargetException if there is trouble constructing the instance
   * @throws NoSuchFieldException if there is trouble constructing the instance
   */
  protected Object buildMethodContext()
      throws ClassNotFoundException,
          InstantiationException,
          IllegalAccessException,
          InvocationTargetException,
          NoSuchFieldException {
    // Class is not accessible, instantiate reflectively.
    Class<?> methCtxClss =
        Class.forName("com.sun.tools.javac.comp.Resolve$MethodResolutionContext");
    Constructor<?> constructor = methCtxClss.getDeclaredConstructors()[0];
    constructor.setAccessible(true);
    Object methodContext = constructor.newInstance(resolve);
    // we need to also initialize the fields attrMode and step
    setField(methodContext, "attrMode", DeferredAttr.AttrMode.CHECK);
    @SuppressWarnings("rawtypes")
    List<?> phases = (List) getField(resolve, "methodResolutionSteps");
    assert phases != null : "@AssumeAssertion(nullness): assumption";
    setField(methodContext, "step", phases.get(1));
    return methodContext;
  }

  /**
   * Reflectively set a field.
   *
   * @param receiver the receiver in which to set the field
   * @param fieldName name of field to set
   * @param value new value for field
   * @throws NoSuchFieldException if the field does not exist in the receiver
   * @throws IllegalAccessException if the field is not accessible
   */
  @SuppressWarnings({
    "nullness:argument",
    "interning:argument"
  }) // assume that the fields all accept null and uninterned values
  private void setField(Object receiver, String fieldName, @Nullable Object value)
      throws NoSuchFieldException, IllegalAccessException {
    Field f = receiver.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    f.set(receiver, value);
  }

  /** Reflectively get the value of a field. */
  private @Nullable Object getField(Object receiver, String fieldName)
      throws NoSuchFieldException, IllegalAccessException {
    Field f = receiver.getClass().getDeclaredField(fieldName);
    f.setAccessible(true);
    return f.get(receiver);
  }

  /**
   * Creates a new {@code DiscardDiagnosticHandler} with the current {@code log}.
   *
   * @return a new {@code DiscardDiagnosticHandler} with the current {@code log}
   */
  private Log.DiscardDiagnosticHandler newDiagnosticHandler() {
    try {
      return NEW_DIAGNOSTIC_HANDLER.newInstance(log);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new BugInCF(e);
    }
  }

  /**
   * Invoke the given method on the {@code resolve} field.
   *
   * @param method the method to called
   * @param args the arguments to the call
   * @return the result of invoking the method on {@code resolve} (as the receiver) and the
   *     arguments
   */
  private Symbol resolve(Method method, @Nullable Object... args) {
    return invokeNoException(resolve, method, args);
  }

  /**
   * Invoke a method reflectively. This is like {@code Method.invoke()}, but it throws no checked
   * exceptions.
   *
   * @param receiver the receiver
   * @param method the method to called
   * @param args the arguments to the call
   * @return the result of invoking the method on the receiver and arguments
   */
  private Symbol invokeNoException(Object receiver, Method method, @Nullable Object... args) {
    try {
      @SuppressWarnings("nullness") // assume arguments are OK
      @NonNull Symbol res = (Symbol) method.invoke(receiver, args);
      return res;
    } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
      throw new BugInCF(
          e,
          "Unexpected reflection error in invokeNoException(%s, %s, %s)",
          receiver,
          method,
          Arrays.toString(args));
    }
  }
}
