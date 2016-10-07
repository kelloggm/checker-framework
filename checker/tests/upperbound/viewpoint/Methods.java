import org.checkerframework.checker.upperbound.qual.LessThanLength;

class Methods {
    interface MyFakeList<T> {
        T get(@LessThanLength("this") int i);
    }

    // A flow expression of #1 means the first parameter
    void test(MyFakeList<String> list, @LessThanLength("#1") int index, int notIndex) {
        // TODO: This gives an error, because the annotation on index has not been view point
        // adapted.
        //:: error: (argument.type.incompatible)
        list.get(index);
        @SuppressWarnings("assignment.type.incompatible")
        @LessThanLength("list") int otherIndex = 0;
        list.get(otherIndex);

        //:: error: (argument.type.incompatible)
        list.get(notIndex);
    }

    void callTest(MyFakeList<String> otherList) {
        @SuppressWarnings("assignment.type.incompatible")
        @LessThanLength("otherList") int index = 0;
        test(otherList, index, 0); // ok

        @SuppressWarnings("assignment.type.incompatible")
        @LessThanLength("list") int notIndex = 0;
        //:: error: (argument.type.incompatible)
        test(otherList, notIndex, 0);
    }
}
