package tests.wpirunners;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.FrameworkPerDirectoryTest;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runners.Parameterized.Parameters;
import testlib.wholeprograminference.WholeProgramInferenceTestChecker;

/**
 * Tests whole-program inference with the aid of .jaif files.
 *
 * <p>IMPORTANT: The errors captured in the tests located in tests/whole-program-inference/ are not
 * relevant. The meaning of this test class is to test if the generated .jaif files are similar to
 * the expected ones. The errors on .java files must be ignored.
 */
public class WholeProgramInferenceJaifsTest extends FrameworkPerDirectoryTest {

    /** @param testFiles the files containing test code, which will be type-checked */
    public WholeProgramInferenceJaifsTest(List<File> testFiles) {
        super(
                testFiles,
                WholeProgramInferenceTestChecker.class,
                "value",
                "-Anomsgtext",
                "-Ainfer=jaifs");
    }

    @Before
    public void before() throws Exception {
        Assume.assumeTrue("running_wpi_tests".equals(System.getProperty("wpiTestStatus")));
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"whole-program-inference/non-annotated"};
    }
}