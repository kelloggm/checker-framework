package org.checkerframework.dataflow.cfg.builder;

/**
 * A label is used to refer to other extended nodes using a mapping from labels to extended nodes.
 * Labels get their names either from labeled statements in the source code or from internally
 * generated unique names.
 *
 * <p>Note that this class is deliberately public, to enable users of the dataflow library to
 * customize CFG construction.
 */
public class Label {

  /** Unique id counter that incremented in {@code #uniqueName}. */
  private static int uid = 0;

  protected final String name;

  public Label(String name) {
    this.name = name;
  }

  public Label() {
    this.name = uniqueName();
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * Returns a new unique label name that cannot be confused with a Java source code label.
   *
   * @return a new unique label name
   */
  private static String uniqueName() {
    return "%L" + uid++;
  }
}
