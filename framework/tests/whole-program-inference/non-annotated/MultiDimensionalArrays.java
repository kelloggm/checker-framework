// This test ensures that annotations on different component types of multidimensional arrays
// are printed correctly.

import testlib.wholeprograminference.qual.Sibling1;
import testlib.wholeprograminference.qual.Sibling2;

class MultiDimensionalArrays {

    // two dimensional arrays

    void requiresS1S2(@Sibling1 int @Sibling2 [] x) {}

    int[] twoDimArray;

    void testField() {
        // :: error: argument.type.incompatible
        requiresS1S2(twoDimArray);
    }

    void useField(@Sibling1 int @Sibling2 [] x) {
        twoDimArray = x;
    }

    void testParam(int[] x) {
        // :: error: argument.type.incompatible
        requiresS1S2(x);
    }

    void useParam(@Sibling1 int @Sibling2 [] x) {
        testParam(x);
    }

    // three dimensional arrays

    void requiresS1S2S1(@Sibling1 int @Sibling2 [] @Sibling1 [] x) {}

    int[][] threeDimArray;

    void testField2() {
        // :: error: argument.type.incompatible
        requiresS1S2S1(threeDimArray);
    }

    void useField2(@Sibling1 int @Sibling2 [] @Sibling1 [] x) {
        threeDimArray = x;
    }

    void testParam2(int[][] x) {
        // :: error: argument.type.incompatible
        requiresS1S2S1(x);
    }

    void useParam2(@Sibling1 int @Sibling2 [] @Sibling1 [] x) {
        testParam2(x);
    }

    // three dimensional array with annotations only on two inner types

    void requiresS1S2N(@Sibling1 int @Sibling2 [][] x) {}

    int[][] threeDimArray2;

    void testField3() {
        // :: error: argument.type.incompatible
        requiresS1S2N(threeDimArray2);
    }

    void useField3(@Sibling1 int @Sibling2 [][] x) {
        threeDimArray2 = x;
    }

    void testParam3(int[][] x) {
        // :: error: argument.type.incompatible
        requiresS1S2N(x);
    }

    void useParam3(@Sibling1 int @Sibling2 [][] x) {
        testParam3(x);
    }

    // three dimensional array with annotations only on two array types, not innermost type

    void requiresS2S1(int @Sibling2 [] @Sibling1 [] x) {}

    int[][] threeDimArray3;

    void testField4() {
        // :: error: argument.type.incompatible
        requiresS2S1(threeDimArray3);
    }

    void useField4(int @Sibling2 [] @Sibling1 [] x) {
        threeDimArray3 = x;
    }

    void testParam4(int[][] x) {
        // :: error: argument.type.incompatible
        requiresS2S1(x);
    }

    void useParam4(int @Sibling2 [] @Sibling1 [] x) {
        testParam4(x);
    }
}