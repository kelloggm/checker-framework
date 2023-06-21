// test for the behavior described by https://bugs.openjdk.org/browse/JDK-8215881

import java.net.*;

public class BindFail {
  public void test() throws Exception {
    // :: error: required.method.not.called
    Socket s = new Socket();
    SocketAddress addr = new InetSocketAddress("127.0.0.1", 6010);
    try {
      s.bind(addr);
    } catch(Exception e) {
      // socket might still be open on this path
      return;
    }
    s.close();
  }
}
