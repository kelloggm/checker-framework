// Based on an incorrect inference I observed when running WPI.

public class NullTernary extends NullTernaryAbstract<ArrayWrapper> {

  @SuppressWarnings("all")
  String[][] rows;

  @Override
  public ArrayWrapper getRow(int i) {
    // :: warning: return
    return (i < 0 || i >= rows.length) ? null : ArrayWrapper.of(rows[i], i);
  }
}
