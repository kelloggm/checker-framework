// This is a simple test that it is not possible to construct spuriously-MCA
// objects.

import java.io.*;
import java.net.Socket;
import org.checkerframework.checker.calledmethods.qual.*;
import org.checkerframework.checker.mustcall.qual.*;

public class MustCallAliasImplWrong3 {
  static @MustCallAlias Closeable example(@MustCallAlias Closeable p) throws IOException {
    p.close();
    return new Socket("localhost", 5000);
  }

  // should have an error for this constructor
  @MustCallAlias MustCallAliasImplWrong3(@MustCallAlias InputStream i) throws IOException {
    i.close();
  }
}
