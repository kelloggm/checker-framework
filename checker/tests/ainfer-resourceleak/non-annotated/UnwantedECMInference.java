// This test ensures that @EnsuresCalledMethods annotations are not inferred by the normal
// WPI postcondition annotation inference algorithm (i.e., that it is disabled). With the
// usual WPI postcondition annotation inference algorithm, this test case would produce a
// spurious (but technically correct) error.

public class UnwantedECMInference {

  class Foo {
    Object field;

    void doStuff() {
      field.toString();
    }
  }

  class Bar extends Foo {
    void doStuff() {
      // not toString(), so an @EnsuresCalledMethods annotation on either this
      // method or on the super class about field is an error!
      field.hashCode();
    }
  }
}
