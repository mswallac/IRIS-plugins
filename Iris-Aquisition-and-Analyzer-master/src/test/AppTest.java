

import org.junit.Test;
import static org.junit.Assert.*;

public class AppTest {
    @Test public void testAppHasAGreeting() {
        application.Main classUnderTest = new application.Main();
        assertNotNull("app should have a greeting",
            application.Main.getGreeting());
    }
}