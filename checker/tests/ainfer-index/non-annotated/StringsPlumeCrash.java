// Based on a NumberFormatException in StringsPlume.

public class StringsPlumeCrash {

  // Both of these methods got the non-sensical annotation
  // @org.checkerframework.common.value.qual.IntRange(from = -2147483648, to = 9223372036854775807)
  // from the Value Checker.

  // From StringsPlume
  public static int count(String s, int ch) {
    int result = 0;
    int pos = s.indexOf(ch);
    while (pos > -1) {
      result++;
      pos = s.indexOf(ch, pos + 1);
    }
    return result;
  }

  // From ArraysPlume
  public static int indexOfEq(Object[] a, Object elt) {
    for (int i = 0; i < a.length; i++) {
      if (elt == a[i]) {
        return i;
      }
    }
    return -1;
  }
}
