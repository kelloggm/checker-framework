package org.checkerframework.framework.test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import javax.annotation.processing.AbstractProcessor;
import org.checkerframework.checker.initialization.qual.UnderInitialization;
import org.junit.Assert;

/**
 * A specialized variant of {@link CheckerFrameworkPerDirectoryTest} for testing the Whole Program
 * Inference feature of the Checker Framework, which is tested by running pairs of these tests: a
 * "generation test" (of class {@link AinferGeneratePerDirectoryTest}) to do inference using the
 * {@code -Ainfer} option, and a "validation test" (of class {@link AinferValidatePerDirectoryTest})
 * to check that files typecheck after those inferences are taken into account. This common
 * superclass of those two classes should never be used directly.
 */
public abstract class CheckerFrameworkWPIPerDirectoryTest extends CheckerFrameworkPerDirectoryTest {

  /**
   * Creates a new checker test. Use this constructor when creating a generation test.
   *
   * <p>{@link TestConfigurationBuilder#getDefaultConfigurationBuilder(String, File, String,
   * Iterable, Iterable, List, boolean)} adds additional checker options.
   *
   * @param testFiles the files containing test code, which will be type-checked
   * @param checker the class for the checker to use
   * @param testDir the path to the directory of test inputs
   * @param checkerOptions options to pass to the compiler when running tests
   */
  protected CheckerFrameworkWPIPerDirectoryTest(
      List<File> testFiles,
      Class<? extends AbstractProcessor> checker,
      String testDir,
      String... checkerOptions) {
    super(testFiles, checker, testDir, checkerOptions);

    String skipComment;
    if (this.checkerOptions.contains("-Ainfer=ajava")) {
      skipComment = "@infer-ajava-skip-test";
    } else if (this.checkerOptions.contains("-Ainfer=jaifs")) {
      skipComment = "@infer-jaifs-skip-test";
    } else if (this.checkerOptions.contains("-Ainfer=stubs")) {
      skipComment = "@infer-stubs-skip-test";
    } else {
      skipComment = null;
    }
    if (skipComment != null) {
      List<File> removeFiles = new ArrayList<>();
      for (File testFile : testFiles) {
        if (hasSkipComment(testFile, skipComment)) {
          removeFiles.add(testFile);
        }
      }
      this.testFiles.removeAll(removeFiles);
    }
  }

  /**
   * Do not typecheck any file ending with the given String. A subclass of
   * CheckerFrameworkWPIPerDirectoryTest uses this routine to avoid typechecking files in the
   * all-systems test suite that are problematic for one typechecker. For example, this routine is
   * useful when running the all-systems tests using WPI, because some all-systems tests have
   * expected errors that become warnings during a WPI run (because of {@code -Awarns}) and so must
   * be excluded.
   *
   * <p>This code takes advantage of the mutability of the {@link #testFiles} field.
   *
   * @param endswith a string that the absolute path of the target file that should not be
   *     typechecked ends with. Usually, this takes the form "all-systems/ProblematicFile.java".
   */
  protected void doNotTypecheck(
      @UnderInitialization(CheckerFrameworkPerDirectoryTest.class) CheckerFrameworkWPIPerDirectoryTest this,
      String endswith) {
    int removeIndex = -1;
    for (int i = 0; i < testFiles.size(); i++) {
      File f = testFiles.get(i);
      if (f.getAbsolutePath().endsWith(endswith)) {
        if (removeIndex != -1) {
          Assert.fail(
              "When attempting to exclude a file, found more than one match in the"
                  + " test suite. Check the test code and use a more-specific removal"
                  + " key. Attempting to exclude: "
                  + endswith);
        }
        removeIndex = i;
      }
    }
    // This test code can run for every subdirectory of the all-systems tests, so there is no
    // guarantee that the file to be excluded will be found.
    if (removeIndex != -1) {
      testFiles.remove(removeIndex);
    }
  }

  /**
   * Returns true if {@code file} contains {@code skipComment}.
   *
   * @param file a java test file
   * @param skipComment a comment that indicates that a test should be skipped
   * @return true if {@code file} contains {@code skipComment}
   */
  public static boolean hasSkipComment(File file, String skipComment) {
    try (Scanner in = new Scanner(file, StandardCharsets.UTF_8)) {
      while (in.hasNext()) {
        String nextLine = in.nextLine();
        if (nextLine.contains(skipComment)) {
          return true;
        }
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    return false;
  }
}
