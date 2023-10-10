import org.checkerframework.checker.mustcall.qual.*;
import org.checkerframework.checker.nullness.qual.*;

class AssignNullInExceptionBlock {

  static class Foo {}

  static Foo makeFoo1() {
    throw new UnsupportedOperationException();
  }

  static Foo makeFoo3() throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  static Foo makeFoo() {
    return new Foo();
  }

  Foo fooField;

  void test1() {
    try {
      fooField = makeFoo();
    } catch (Exception e) {
      Foo f = null; // The RHS here can be anything, but must exist. Both literals and
      // identifiers lead to the bad behavior. However, f must be a local
      // variable: it cannot be a field or other externally-visible name.
      fooField = f;
    }
  }

  //  void test2() {
  //    try {
  //      fooField = makeFoo1();
  //    } catch (Exception e) {
  //      Foo f = null;
  //      fooField = f;
  //    }
  //  }
  //
  //  void test3() {
  //    Foo f = null;
  //    try {
  //      fooField = makeFoo1();
  //    } catch (Exception e) {
  //      fooField = f;
  //    }
  //  }
  //

  static Foo makeFoo2() throws UnsupportedOperationException {
    return new Foo();
  }

  Foo fooField2;

  void test4() {
    try {
      fooField2 = makeFoo2();
    } catch (Exception e) {
      Foo f2 = null;
      fooField2 = f2;
    }
  }
  //
  //  void test5() {
  //    try {
  //      fooField = makeFoo3();
  //    } catch (Exception e) {
  //      Foo f = null;
  //      fooField = f;
  //    }
  //  }
  //
  //  void test6() {
  //    Foo f = null;
  //    try {
  //      fooField = makeFoo3();
  //    } catch (Exception e) {
  //      fooField = f;
  //    }
  //  }
}
