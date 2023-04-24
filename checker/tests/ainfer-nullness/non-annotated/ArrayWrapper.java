// Part of the test in NullTernary.java

public class ArrayWrapper {

  private ArrayWrapper() {}

  static ArrayWrapper of(String[] a, int b) {
    return new ArrayWrapper();
  }
}
