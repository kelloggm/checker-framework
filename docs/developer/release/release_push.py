#!/usr/bin/env python3
# encoding: utf-8
"""
release_push.py

Created by Jonathan Burke on 2013-12-30.

Copyright (c) 2013-2016 University of Washington. All rights reserved.
"""

# See README-release-process.html for more information

import os
from os.path import expanduser

from release_vars import AFU_LIVE_RELEASES_DIR
from release_vars import ANNO_FILE_UTILITIES
from release_vars import CF_VERSION
from release_vars import CHECKER_FRAMEWORK
from release_vars import CHECKER_LIVE_RELEASES_DIR
from release_vars import CHECKER_LIVE_API_DIR
from release_vars import CHECKLINK
from release_vars import DEV_SITE_DIR
from release_vars import DEV_SITE_URL
from release_vars import INTERM_ANNO_REPO
from release_vars import INTERM_CHECKER_REPO
from release_vars import LIVE_SITE_DIR
from release_vars import LIVE_SITE_URL
from release_vars import RELEASE_BUILD_COMPLETED_FLAG_FILE
from release_vars import SANITY_DIR
from release_vars import SCRIPTS_DIR
from release_vars import TMP_DIR

from release_vars import execute

from release_utils import continue_or_exit
from release_utils import current_distribution_by_website
from release_utils import delete_if_exists
from release_utils import delete_path
from release_utils import delete_path_if_exists
from release_utils import ensure_group_access
from release_utils import get_announcement_email
from release_utils import print_step
from release_utils import prompt_to_continue
from release_utils import prompt_yes_no
from release_utils import push_changes_prompt_if_fail
from release_utils import has_command_line_option
from release_utils import read_first_line
from release_utils import set_umask
from release_utils import subprocess
from release_utils import version_number_to_array
from sanity_checks import javac_sanity_check, maven_sanity_check

import sys


def check_release_version(previous_release, new_release):
    """Ensure that the given new release version is greater than the given
    previous one."""
    if version_number_to_array(previous_release) >= version_number_to_array(
        new_release
    ):
        raise Exception(
            "Previous release version ("
            + previous_release
            + ") should be less than "
            + "the new release version ("
            + new_release
            + ")"
        )


def copy_release_dir(path_to_dev_releases, path_to_live_releases, release_version):
    """Copy a release directory with the given release version from the dev
    site to the live site. For example,
    /cse/www2/types/dev/checker-framework/releases/2.0.0 ->
    /cse/www2/types/checker-framework/releases/2.0.0"""
    source_location = os.path.join(path_to_dev_releases, release_version)
    dest_location = os.path.join(path_to_live_releases, release_version)

    if os.path.exists(dest_location):
        delete_path(dest_location)

    if os.path.exists(dest_location):
        raise Exception("Destination location exists: " + dest_location)

    # The / at the end of the source location is necessary so that
    # rsync copies the files in the source directory to the destination directory
    # rather than a subdirectory of the destination directory.
    cmd = "rsync --no-group --omit-dir-times --recursive --links --quiet %s/ %s" % (
        source_location,
        dest_location,
    )
    execute(cmd)

    return dest_location


def promote_release(path_to_releases, release_version):
    """Copy a release directory to the top level. For example,
    /cse/www2/types/checker-framework/releases/2.0.0/* ->
    /cse/www2/types/checker-framework/*"""
    from_dir = os.path.join(path_to_releases, release_version)
    to_dir = os.path.join(path_to_releases, "..")
    # Trailing slash is crucial.
    cmd = "rsync -aJ --no-group --omit-dir-times %s/ %s" % (from_dir, to_dir)
    execute(cmd)


def copy_htaccess():
    "Copy the .htaccess file from the dev site to the live site."
    LIVE_HTACCESS = os.path.join(LIVE_SITE_DIR, ".htaccess")
    execute(
        "rsync --times %s %s" % (os.path.join(DEV_SITE_DIR, ".htaccess"), LIVE_HTACCESS)
    )
    ensure_group_access(LIVE_HTACCESS)


def copy_releases_to_live_site(cf_version):
    """Copy the new releases of the AFU and the Checker
    Framework from the dev site to the live site."""
    CHECKER_INTERM_RELEASES_DIR = os.path.join(DEV_SITE_DIR, "releases")
    copy_release_dir(CHECKER_INTERM_RELEASES_DIR, CHECKER_LIVE_RELEASES_DIR, cf_version)
    delete_path_if_exists(CHECKER_LIVE_API_DIR)
    promote_release(CHECKER_LIVE_RELEASES_DIR, cf_version)
    AFU_INTERM_RELEASES_DIR = os.path.join(
        DEV_SITE_DIR, "annotation-file-utilities", "releases"
    )
    copy_release_dir(AFU_INTERM_RELEASES_DIR, AFU_LIVE_RELEASES_DIR, cf_version)
    promote_release(AFU_LIVE_RELEASES_DIR, cf_version)


def ensure_group_access_to_releases():
    """Gives group access to all files and directories in the \"releases\"
    subdirectories on the live web site for the AFU and the
    Checker Framework."""
    ensure_group_access(AFU_LIVE_RELEASES_DIR)
    ensure_group_access(CHECKER_LIVE_RELEASES_DIR)


def stage_maven_artifacts_in_maven_central(new_cf_version):
    """Stages the Checker Framework artifacts on Maven Central. After the
    artifacts are staged, the user can then close them, which makes them
    available for testing purposes but does not yet release them on Maven
    Central. This is a reversible step, since artifacts that have not been
    released can be dropped, which for our purposes is equivalent to never
    having staged them."""
    gnupgPassphrase = read_first_line(
        "/projects/swlab1/checker-framework/hosting-info/release-private.password"
    )
    execute(
        "./gradlew publish -Prelease=true --no-parallel -Psigning.gnupg.keyName=checker-framework-dev@googlegroups.com -Psigning.gnupg.passphrase=%s"
        % gnupgPassphrase,
        working_dir=CHECKER_FRAMEWORK,
    )


def is_file_empty(filename):
    "Returns true if the given file has size 0."
    return os.path.getsize(filename) == 0


def run_link_checker(site, output, additional_param=""):
    """Runs the link checker on the given web site and saves the output to the
    given file. Additional parameters (if given) are passed directly to the
    link checker script."""
    delete_if_exists(output)
    check_links_script = os.path.join(SCRIPTS_DIR, "checkLinks.sh")
    if additional_param == "":
        cmd = ["sh", check_links_script, site]
    else:
        cmd = ["sh", check_links_script, additional_param, site]
    env = {"CHECKLINK": CHECKLINK}

    out_file = open(output, "w+")

    print(
        (
            "Executing: "
            + " ".join("%s=%r" % (key2, val2) for (key2, val2) in list(env.items()))
            + " "
            + " ".join(cmd)
        )
    )
    process = subprocess.Popen(cmd, env=env, stdout=out_file, stderr=out_file)
    process.communicate()
    process.wait()
    out_file.close()

    if process.returncode != 0:
        msg = "Non-zero return code (%s; see output in %s) while executing %s" % (
            process.returncode,
            output,
            cmd,
        )
        print(msg + "\n")
        if not prompt_yes_no("Continue despite link checker results?", True):
            raise Exception(msg)

    return output


def check_all_links(
    afu_website,
    checker_website,
    suffix,
    test_mode,
    cf_version_of_broken_link_to_suppress="",
):
    """Checks all links on the given web sites for the AFU
    and the Checker Framework. The suffix parameter should be \"dev\" for the
    dev web site and \"live\" for the live web site. test_mode indicates
    whether this script is being run in release or in test mode. The
    cf_version_of_broken_link_to_suppress parameter should be set to the
    new Checker Framework version and should only be passed when checking links
    for the dev web site (to prevent reporting of a broken link to the
    not-yet-live zip file for the new release)."""
    afuCheck = run_link_checker(afu_website, TMP_DIR + "/afu." + suffix + ".check")
    additional_param = ""
    if cf_version_of_broken_link_to_suppress != "":
        additional_param = (
            "--suppress-broken 404:https://checkerframework.org/checker-framework-"
            + cf_version_of_broken_link_to_suppress
            + ".zip"
        )
    checkerCheck = run_link_checker(
        checker_website,
        TMP_DIR + "/checker-framework." + suffix + ".check",
        additional_param,
    )

    is_afuCheck_empty = is_file_empty(afuCheck)
    is_checkerCheck_empty = is_file_empty(checkerCheck)

    errors_reported = not (is_afuCheck_empty and is_checkerCheck_empty)
    if errors_reported:
        print("Link checker results can be found at:\n")
    if not is_afuCheck_empty:
        print("\t" + afuCheck + "\n")
    if not is_checkerCheck_empty:
        print("\t" + checkerCheck + "\n")
    if errors_reported:
        if not prompt_yes_no("Continue despite link checker results?", True):
            release_option = ""
            if not test_mode:
                release_option = " release"
            raise Exception(
                "The link checker reported errors.  Please fix them by committing changes to the mainline\n"
                + "repository and pushing them to GitHub, then updating the development and live sites by\n"
                + "running\n"
                + "  python3 release_build.py all\n"
                + "  python3 release_push"
                + release_option
                + "\n"
            )


def push_interm_to_release_repos():
    """Push the release to the GitHub repositories for
    the AFU and the Checker Framework. This is an
    irreversible step."""
    push_changes_prompt_if_fail(INTERM_ANNO_REPO)
    push_changes_prompt_if_fail(INTERM_CHECKER_REPO)


def validate_args(argv):
    """Validate the command-line arguments to ensure that they meet the
    criteria issued in print_usage."""
    if len(argv) > 3:
        print_usage()
        raise Exception("Invalid arguments. " + ",".join(argv))
    for i in range(1, len(argv)):
        if argv[i] != "release":
            print_usage()
            raise Exception("Invalid arguments. " + ",".join(argv))


def print_usage():
    """Print instructions on how to use this script, and in particular how to
    set test or release mode."""
    print(
        (
            "Usage: python3 release_build.py [release]\n"
            + 'If the "release" argument is '
            + "NOT specified then the script will execute all steps that checking and prompting "
            + "steps but will NOT actually perform a release.  This is for testing the script."
        )
    )


def main(argv):
    """The release_push script is mainly responsible for copying the artifacts
    (for the AFU and the Checker Framework) from the
    development web site to Maven Central and to
    the live site. It also performs link checking on the live site, pushes
    the release to GitHub repositories, and guides the user to
    perform manual steps such as sending the
    release announcement e-mail."""
    # MANUAL Indicates a manual step
    # AUTO Indicates the step is fully automated.

    set_umask()

    validate_args(argv)
    test_mode = not has_command_line_option(argv, "release")

    m2_settings = expanduser("~") + "/.m2/settings.xml"
    if not os.path.exists(m2_settings):
        raise Exception("File does not exist: " + m2_settings)

    if test_mode:
        msg = (
            "You have chosen test_mode.\n"
            + "This means that this script will execute all build steps that "
            + "do not have side effects.  That is, this is a test run of the script.  All checks and user prompts "
            + "will be shown but no steps will be executed that will cause the release to be deployed or partially "
            + "deployed.\n"
            + 'If you meant to do an actual release, re-run this script with one argument, "release".'
        )
    else:
        msg = "You have chosen release_mode.  Please follow the prompts to run a full Checker Framework release."

    continue_or_exit(msg + "\n")
    if test_mode:
        print("Continuing in test mode.")
    else:
        print("Continuing in release mode.")

    if not os.path.exists(RELEASE_BUILD_COMPLETED_FLAG_FILE):
        continue_or_exit(
            "It appears that release_build.py has not been run since the last push to "
            + "the AFU or Checker Framework repositories.  Please ensure it has "
            + "been run."
        )

    # The release script checks that the new release version is greater than the previous release version.

    print_step("Push Step 1: Checking release versions")  # SEMIAUTO
    dev_afu_website = os.path.join(DEV_SITE_URL, "annotation-file-utilities")
    live_afu_website = os.path.join(LIVE_SITE_URL, "annotation-file-utilities")

    dev_checker_website = DEV_SITE_URL
    live_checker_website = LIVE_SITE_URL
    current_cf_version = current_distribution_by_website(live_checker_website)
    new_cf_version = CF_VERSION
    check_release_version(current_cf_version, new_cf_version)

    print(
        "Checker Framework and AFU:  current-version=%s    new-version=%s"
        % (current_cf_version, new_cf_version)
    )

    # Runs the link the checker on all websites at:
    # https://checkerframework.org/dev/
    # The output of the link checker is written to files in the /scratch/$USER/cf-release directory
    # whose locations will be output at the command prompt if the link checker reported errors.

    # In rare instances (such as when a link is correct but the link checker is
    # unable to check it), you may add a suppression to the checklink-args.txt file.
    # In extremely rare instances (such as when a website happens to be down at the
    # time you ran the link checker), you may ignore an error.

    print_step("Push Step 2: Check links on development site")  # SEMIAUTO

    if prompt_yes_no("Run link checker on DEV site?", True):
        check_all_links(
            dev_afu_website, dev_checker_website, "dev", test_mode, new_cf_version
        )

    # Runs sanity tests on the development release. Later, we will run a smaller set of sanity
    # tests on the live release to ensure no errors occurred when promoting the release.

    print_step("Push Step 3: Run development sanity tests")  # SEMIAUTO
    if prompt_yes_no("Perform this step?", True):
        print_step("3a: Run javac sanity test on development release.")
        if prompt_yes_no("Run javac sanity test on development release?", True):
            javac_sanity_check(dev_checker_website, new_cf_version)

        print_step("3b: Run Maven sanity test on development release.")
        if prompt_yes_no("Run Maven sanity test on development repo?", True):
            maven_sanity_check("maven-dev", "", new_cf_version)

    # Runs all tests on the development release.

    print_step("Push Step 4: Run all tests (takes a long time)")
    if prompt_yes_no("Perform this step?", True):
        ant_cmd = "./gradlew allTests"
        execute(ant_cmd, True, False, CHECKER_FRAMEWORK)

        ant_cmd = "./gradlew test"
        execute(ant_cmd, True, False, ANNO_FILE_UTILITIES)

    # The Central Repository is a repository of build artifacts for build programs like Maven and Ivy.
    # This step stages (but doesn't release) the Checker Framework's Maven artifacts in the Sonatypes
    # Central Repository.

    # Once staging is complete, there are manual steps to log into Sonatype Central and "close" the
    # staging repository. Closing allows us to test the artifacts.

    # This step deploys the artifacts to the Central Repository and prompts the user to close the
    # artifacts. Later, you will be prompted to release the staged artifacts after we push the
    # release to our GitHub repositories.

    # For more information on deploying to the Central Repository see:
    # https://docs.sonatype.org/display/Repository/Sonatype+OSS+Maven+Repository+Usage+Guide

    print_step("Push Step 5: Stage Maven artifacts in Central")  # SEMIAUTO

    print_step("Step 5a: Stage the artifacts at Maven Central.")
    if (not test_mode) or prompt_yes_no(
        "Stage Maven artifacts in Maven Central?", not test_mode
    ):
        stage_maven_artifacts_in_maven_central(new_cf_version)

        print_step("Step 5b: Close staged artifacts at Maven Central.")
        continue_or_exit(
            "Maven artifacts have been staged!  Please 'close' (but don't release) the artifacts.\n"
            + " * Browse to https://central.sonatype.com/publishing/deployments\n"
            + " * Log in using your Sonatype credentials\n"
            + ' * In the search box at upper right, type "checker"\n'
            + " * In the top pane, click on org.checkerframework-XXXX\n"
            + ' * Click "close" at the top\n'
            + " * For the close message, enter:  Checker Framework release "
            + new_cf_version
            + "\n"
            + " * Click the Refresh button near the top of the page until the bottom pane has:\n"
            + '   "Activity   Last operation completed successfully".\n'
            + " * Copy the URL of the closed artifacts (in the bottom pane) for use in the next step\n"
            "(You can also see the instructions at: http://central.sonatype.org/pages/releasing-the-deployment.html)\n"
        )

        print_step("Step 5c: Run Maven sanity test on Maven Central artifacts.")
        if prompt_yes_no("Run Maven sanity test on Maven Central artifacts?", True):
            repo_url = input("Please enter the repo URL of the closed artifacts:\n")

            maven_sanity_check("maven-staging", repo_url, new_cf_version)

    # This step copies the development release directories to the live release directories.
    # It then adds the appropriate permissions to the release. Symlinks need to be updated to point
    # to the live website rather than the development website. A straight copy of the directory
    # will NOT update the symlinks.

    print_step(
        "Push Step 6. Copy dev current release website to live website"
    )  # SEMIAUTO
    if not test_mode:
        if prompt_yes_no("Copy release to the live website?"):
            print("Copying to live site")
            copy_releases_to_live_site(new_cf_version)
            copy_htaccess()
            ensure_group_access_to_releases()
    else:
        print("Test mode: Skipping copy to live site!")

    # This step downloads the checker-framework-X.Y.Z.zip file of the newly live release and ensures we
    # can run the Nullness Checker. If this step fails, you should backout the release.

    print_step("Push Step 7: Run javac sanity tests on the live release.")  # SEMIAUTO
    if not test_mode:
        if prompt_yes_no("Run javac sanity test on live release?", True):
            javac_sanity_check(live_checker_website, new_cf_version)
            SANITY_TEST_CHECKER_FRAMEWORK_DIR = SANITY_DIR + "/test-checker-framework"
            if not os.path.isdir(SANITY_TEST_CHECKER_FRAMEWORK_DIR):
                execute("mkdir -p " + SANITY_TEST_CHECKER_FRAMEWORK_DIR)
            sanity_test_script = os.path.join(SCRIPTS_DIR, "test-checker-framework.sh")
            execute(
                "sh " + sanity_test_script + " " + new_cf_version,
                True,
                False,
                SANITY_TEST_CHECKER_FRAMEWORK_DIR,
            )
    else:
        print("Test mode: Skipping javac sanity tests on the live release.")

    # Runs the link the checker on all websites at:
    # https://checkerframework.org/
    # The output of the link checker is written to files in the /scratch/$USER/cf-release directory whose locations
    # will be output at the command prompt. Review the link checker output.

    # The set of broken links that is displayed by this check will differ from those in push
    # step 2 because the Checker Framework manual and website uses a mix of absolute and
    # relative links. Therefore, some links from the development site actually point to the
    # live site (the previous release). After step 5, these links point to the current
    # release and may be broken.

    print_step("Push Step 8. Check live site links")  # SEMIAUTO
    if not test_mode:
        if prompt_yes_no("Run link checker on LIVE site?", True):
            check_all_links(live_afu_website, live_checker_website, "live", test_mode)
    else:
        print("Test mode: Skipping checking of live site links.")

    # This step pushes the changes committed to the interm repositories to the GitHub
    # repositories. This is the first irreversible change. After this point, you can no longer
    # backout changes and should do another release in case of critical errors.

    print_step("Push Step 9. Push changes to repositories")  # SEMIAUTO
    # This step could be performed without asking for user input but I think we should err on the side of caution.
    if not test_mode:
        if prompt_yes_no(
            "Push the release to GitHub repositories?  This is irreversible.", True
        ):
            push_interm_to_release_repos()
            print("Pushed to repos")
    else:
        print("Test mode: Skipping push to GitHub!")

    # This is a manual step that releases the staged Maven artifacts to the actual Central Repository.
    # This is also an irreversible step. Once you have released these artifacts they will be forever
    # available to the Java community through the Central Repository. Follow the prompts. The Maven
    # artifacts (such as checker-qual.jar) are still needed, but the Maven plug-in is no longer maintained.

    ## TODO: Update the instructions below once I've tried the new location.
    print_step(
        "Push Step 10. Release staged artifacts in Central Repository."
    )  # MANUAL
    if test_mode:
        msg = (
            "Test Mode: You are in test_mode.  Please 'DROP' the artifacts. "
            + "To drop, log into https://central.sonatype.com/publishing/deployments using your "
            + "Sonatype credentials and follow the 'DROP' instructions at: "
            + "http://central.sonatype.org/pages/releasing-the-deployment.html"
        )
    else:
        msg = (
            "Please 'release' the artifacts.\n"
            + "First log into https://central.sonatype.com/publishing/deployments using your Sonatype credentials. Go to Staging Repositories and "
            + "locate the org.checkerframework repository and click on it.\n"
            + "If you have a permissions problem, try logging out and back in.\n"
            + "Finally, click on the Release button at the top of the page. In the dialog box that pops up, "
            + 'leave the "Automatically drop" box checked. For the description, write '
            + "Checker Framework release "
            + new_cf_version
            + "\n\n"
        )

    print(msg)
    prompt_to_continue()

    if test_mode:
        print("Test complete")
    else:
        # A prompt describes the email you should send to all relevant mailing lists.
        # Please fill out the email and announce the release.

        print_step(
            "Push Step 11. Post the Checker Framework and Annotation File Utilities releases on GitHub."
        )  # MANUAL

        msg = (
            "\n"
            + "Download the following files to your local machine."
            + "\n"
            + "  https://checkerframework.org/checker-framework-"
            + new_cf_version
            + ".zip\n"
            + "  https://checkerframework.org/annotation-file-utilities/annotation-tools-"
            + new_cf_version
            + ".zip\n"
            + "\n"
            + "To post the Checker Framework release on GitHub:\n"
            + "\n"
            + "* Browse to https://github.com/typetools/checker-framework/releases/new?tag=checker-framework-"
            + new_cf_version
            + "\n"
            + "* For the release title, enter: Checker Framework "
            + new_cf_version
            + "\n"
            + "* For the description, insert the latest Checker Framework changelog entry (available at https://checkerframework.org/CHANGELOG.md). Please include the first line with the release version and date.\n"
            + '* Find the link below "Attach binaries by dropping them here or selecting them." Click on "selecting them" and upload checker-framework-'
            + new_cf_version
            + ".zip from your machine.\n"
            + '* Click on the green "Publish release" button.\n'
            + "\n"
            + "To post the Annotation File Utilities release on GitHub:\n"
            + "\n"
            + "* Browse to https://github.com/typetools/annotation-tools/releases/new?tag="
            + new_cf_version
            + "\n"
            + "* For the release title, enter: Annotation File Utilities "
            + new_cf_version
            + "\n"
            + "* For the description, insert the latest Annotation File Utilities changelog entry (available at https://checkerframework.org/annotation-file-utilities/changelog.html). Please include the first line with the release version and date. For bullet points, use the * Markdown character.\n"
            + '* Find the link below "Attach binaries by dropping them here or selecting them." Click on "selecting them" and upload annotation-tools-'
            + new_cf_version
            + ".zip from your machine.\n"
            + '* Click on the green "Publish release" button.\n'
        )

        continue_or_exit(msg)

        print_step("Push Step 12. Announce the release.")  # MANUAL
        continue_or_exit(
            "Please announce the release using the email structure below.\n"
            + get_announcement_email(new_cf_version)
        )

        print_step("Push Step 13. Prep for next Checker Framework release.")  # MANUAL
        continue_or_exit(
            "Change the patch level (last number) of the Checker Framework version\nin build.gradle:  increment it and add -SNAPSHOT\n"
        )

        print_step(
            "Push Step 14. Update the Checker Framework Gradle plugin."
        )  # MANUAL
        print("You might have to wait for Maven Central to propagate changes.\n")
        continue_or_exit(
            "Please update the Checker Framework Gradle plugin:\n"
            + "https://github.com/kelloggm/checkerframework-gradle-plugin/blob/master/RELEASE.md#updating-the-checker-framework-version\n"
        )
        continue_or_exit(
            "Make a pull request to the Checker Framework that\n"
            + "updates the version number of the Checker Framework\n"
            + "Gradle Plugin in docs/examples/lombok and docs/examples/errorprone .\n"
            + "The pull request's tests will fail; you will merge it in a day."
        )

    delete_if_exists(RELEASE_BUILD_COMPLETED_FLAG_FILE)

    print("Done with release_push.py.\n")


if __name__ == "__main__":
    sys.exit(main(sys.argv))
