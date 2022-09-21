// Test case to ensure that @SameLen({}) is equivalent to @SameLenUnknown.
// Sometimes @SameLen({}) is inferred by WPI.

import org.checkerframework.checker.index.qual.SameLen;
import org.checkerframework.checker.index.qual.SameLenUnknown;

public class SameLenUnknownVsEmpty {
  public void testAssignabilityInBothDirections(int @SameLenUnknown[] a, int @SameLen({}) [] b) {
    int @SameLen({}) [] b1 = a;
    int @SameLenUnknown [] a1 = b;
  }

  int[] myField;

  public void testThatBothAreTreatedAsTop(int @SameLen("this.myField") [] c) {
    int @SameLen({}) [] b1 = c;
    int @SameLenUnknown [] a1 = c;
  }
}
