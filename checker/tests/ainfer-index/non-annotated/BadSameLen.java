// A test case for inference of an unparseable @SameLen annotation, based on a real case
// in plume-lib/require-javadoc.

public class BadSameLen {
  private boolean isTrivialGetterOrSetter(Object md) {
    String propertyName = propertyName(md);
    return propertyName != null && hasCorrectSignature(md, propertyName);
  }

  private String propertyName(Object md) {
    return null;
  }

  private boolean hasCorrectSignature(Object md, String propertyName) {
    return false;
  }
}
