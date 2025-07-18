package org.checkerframework.checker.initialization;

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import org.checkerframework.common.basetype.BaseTypeChecker;

/**
 * Tracks whether a value is initialized (all its fields are set), and checks that values are
 * initialized before being used. Implements the freedom-before-commitment scheme for
 * initialization, augmented by type frames.
 *
 * @checker_framework.manual #initialization-checker Initialization Checker
 */
public abstract class InitializationChecker extends BaseTypeChecker {

  /** Create a new InitializationChecker. */
  protected InitializationChecker() {}

  @Override
  public NavigableSet<String> getSuppressWarningsPrefixes() {
    NavigableSet<String> result = super.getSuppressWarningsPrefixes();
    // "fbc" is for backward compatibility only.
    // Notes:
    //   * "fbc" suppresses *all* warnings, not just those related to initialization.  See
    //     https://checkerframework.org/manual/#initialization-checking-suppressing-warnings .
    //   * "initialization" is not a checkername/prefix.
    result.add("fbc");
    return result;
  }

  /** Returns a list of all fields of the given class. */
  public static List<VariableTree> getAllFields(ClassTree clazz) {
    List<VariableTree> fields = new ArrayList<>();
    for (Tree t : clazz.getMembers()) {
      if (t instanceof VariableTree) {
        VariableTree vt = (VariableTree) t;
        fields.add(vt);
      }
    }
    return fields;
  }

  @Override
  protected boolean messageKeyMatches(
      String messageKey, String messageKeyInSuppressWarningsString) {
    // Also support the shorter keys used by typetools
    return super.messageKeyMatches(messageKey, messageKeyInSuppressWarningsString)
        || super.messageKeyMatches(
            messageKey.replace(".invalid", ""), messageKeyInSuppressWarningsString);
  }
}
