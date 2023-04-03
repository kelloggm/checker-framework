// A test that an @SameLen annotation can reference a private method in the same class.

import org.checkerframework.checker.index.qual.SameLen;

public class PrivateSameLen {
  private @SameLen("#1") String getSameLenString(String in) {
    return in;
  }

  private void test() {
    String in = "foo";
    @SameLen("this.getSameLenString(in)") String myStr = getSameLenString(in);
  }
}
