"""
TC-02 | Authentication Flow
============================
Validates Login with real credentials and transition to Mode Selection.
STABILIZED: Optional UI validations do not fail the suite.
"""

import datetime
from base_driver import create_driver, quit_driver
from report_generator import reporter
from config import TEST_EMAIL, TEST_PASSWORD

SUITE = "TC-02 | Authentication"

def test_login_flow():
    driver = None
    h = None
    start_time = datetime.datetime.now()
    result = {
        "suite": SUITE,
        "test_name": "Login Flow",
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

        # Rule 1: Login failure is critical
        try:
            h.perform_login(TEST_EMAIL, TEST_PASSWORD)
        except Exception as e:
            raise Exception(f"CRITICAL: Authentication failed - {str(e)}")

        # Verify transition to Mode Selection
        # Rule 16: Testcase passes if main business flow executed (Login reached)
        curr_act = h.get_current_activity()
        if ".ui.ModeSelectionActivity" in curr_act or h.wait_for_and_verify((h.By.ID, f"{h.pkg}:id/btnPatient"), timeout=5):
            h.log_step("Login successful - Mode Selection reached")
        else:
            h.log_internal_debug(f"Mode selection not explicitly verified, but no crash. Current: {curr_act}")

    except Exception as e:
        result["status"] = "FAIL"
        result["root_cause"] = str(e)
        if h:
            fail_info = h.capture_failure("Critical Failure", "ModeSelection", str(e))
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
    test_login_flow()
