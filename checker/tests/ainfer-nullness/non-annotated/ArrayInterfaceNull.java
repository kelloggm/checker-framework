

public class ArrayInterfaceNull {

  public interface MyInterface {
    String [] getArray();
  }

  // Inference will infer something about this array param from the two call sites below.
  public static void testMethod(final String [] arr) { }

  public static void test1() {
    // :: warning: argument
    testMethod(null);
  }

  public static void test2(MyInterface myInterface) {

    // testMethod(myInterface.getArray());
  }
}
