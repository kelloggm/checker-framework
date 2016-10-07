import org.checkerframework.checker.upperbound.qual.LessThanLength;

class Fields {
    Object[] fieldArray;

    void testFields(int index) {
        if (index < fieldArray.length) {
            Object o1 = this.fieldArray[index];
            Object o2 = fieldArray[index];
        }
        int index2 = 0;
        if (index2 < this.fieldArray.length) {
            Object o1 = this.fieldArray[index2];
            Object o2 = fieldArray[index2];
            // TODO: this is a false positive.  Explicitly written annotations need to be view
            // point adapted, too.
            //:: error: (assignment.type.incompatible)
            @LessThanLength("fieldArray") int i = index2;
        }
    }

    class Inner {
        Object innerFieldArray[];

        @SuppressWarnings("assignment.type.incompatible")
        @LessThanLength("innerFieldArray") int index = 0;

        @SuppressWarnings("assignment.type.incompatible")
        @LessThanLength("this.innerFieldArray") int index2 = 0;
    }

    void useInner(Inner myInner) {
        //:: error: (assignment.type.incompatible)
        @LessThanLength("innerFieldArray") int i = myInner.index;
        //:: error: (assignment.type.incompatible)
        @LessThanLength("innerFieldArray") int i2 = myInner.index2;
        @LessThanLength("myInner.innerFieldArray") int i3 = myInner.index;
        @LessThanLength("myInner.innerFieldArray") int i4 = myInner.index2;
        Object o = myInner.innerFieldArray[myInner.index];
        Object o2 = myInner.innerFieldArray[myInner.index2];
    }

    int field;

    Object getElementAtField(Object[] array) {
        if (field < array.length) {
            // field is @LessThanLength("array")
            @LessThanLength("array") int test = field;
            return array[field];
        }
        return null;
    }
}
