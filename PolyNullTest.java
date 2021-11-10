import org.checkerframework.checker.mustcall.qual.*;

class PolyNullTest {

    static @MustCall({}) Object bot;
    static @MustCall({"close"}) Object bot2;
    
    static @PolyMustCall Object test(@PolyMustCall Object obj) {
	return null;
    }

    static @PolyMustCall Object test2(@PolyMustCall Object obj) {
	return bot;
    }

    static @PolyMustCall Object test3(@PolyMustCall Object obj) {
	return bot2;
    }
}
