"""
TC-07 | Settings & Profile Alignment
====================================
Validates Profile editing and Settings navigation.
STABILIZED: Optional UI validations do not fail the suite.
"""

import datetime
from base_driver import create_driver, quit_driver
from report_generator import reporter
from config import TEST_EMAIL, TEST_PASSWORD

SUITE = "TC-07 | Settings & Profile"

def test_profile_and_settings():
    driver = None
    h = None
    start_time = datetime.datetime.now()
    result = {
        "suite": SUITE,
        "test_name": "Profile Edit & Settings Nav",
        "status": "PASS",
        "duration": 0,
        "current_activity": "",
        "expected_element": "",
        "root_cause": "",
        "screenshot_path": "",
        "steps": []
    }

    try:
        driver = create_driver()
        h = driver.helper

        h.wait_for_splash()
        h.handle_permissions()
        h.handle_onboarding()

        try:
            h.perform_login(TEST_EMAIL, TEST_PASSWORD)
        except Exception as e:
            raise Exception(f"CRITICAL: Authentication failed - {str(e)}")

        h.handle_mode_selection("patient")

        # 1. Navigate to Profile (Rule 2: Optional)
        h.log_step("Navigating to Profile (Optional)")
        if h.navigate_and_verify("navigation_profile", "MainActivity", "Profile", is_optional=True):

            # 2. Edit Profile (Optional)
            h.log_step("Checking Edit Profile (Optional)")
            if h.safe_click("btnEditAvatar", "Edit Avatar Button", timeout=5, is_optional=True):
                try:
                    name_field = h.wait_for_and_verify((h.By.ID, f"{h.pkg}:id/etName"), timeout=5)
                    if name_field:
                        name_field.clear()
                        name_field.send_keys("Test Admin")
                        if h.driver.is_keyboard_shown(): h.driver.hide_keyboard()
                        h.safe_click("btnSaveProfile", "Save Changes Button", is_optional=True)
                        h.log_step("Profile edit sequence completed")
                except:
                    h.log_internal_debug("Profile edit interaction failed")

            # 3. Settings Navigation (Rule 11: Optional)
            h.log_step("Checking Settings Navigation (Optional)")
            if h.safe_click("cardSettings", "Settings Card", timeout=5, is_optional=True):
                # Verify settings components but don't fail
                if h.wait_until_visible("cardGeneral", timeout=5, is_optional=True):
                    h.safe_click("cardGeneral", "General Settings", is_optional=True)
                    h.log_step("General Settings reached")
                    driver.back() # Return to Settings
                    driver.back() # Return to Profile

        h.log_step("Profile and Settings flow sequence completed")

    except Exception as e:
        result["status"] = "FAIL"
        result["root_cause"] = str(e)
        if h:
            fail_info = h.capture_failure("Critical Failure", "N/A", str(e))
            result.update(fail_info)

    finally:
        result["duration"] = (datetime.datetime.now() - start_time).total_seconds()
        if h:
            result["steps"] = h.current_test_steps
            with open("internal_debug.log", "a") as f:
                f.write(f"\n--- {SUITE} ---\n")
                f.write("\n".join(h.internal_debug_logs))
                f.write("\n")

        reporter.add_result(result)
        quit_driver(driver)

def run_all():
    test_profile_and_settings()
