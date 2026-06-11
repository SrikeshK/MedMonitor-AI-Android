"""
TC-01 | Splash Screen & Onboarding
====================================
STABILIZED: Optional UI validations do not fail the suite.
"""

import datetime
from base_driver import create_driver, quit_driver
from report_generator import reporter

SUITE = "TC-01 | Splash & Onboarding"

def test_splash_and_onboarding_flow():
    driver = None
    h = None
    start_time = datetime.datetime.now()
    result = {
        "suite": SUITE,
        "test_name": "Splash and Onboarding Flow",
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

        # Rule 1: Splash screen never completes is a critical failure
        h.wait_for_splash()

        h.handle_permissions()
        h.handle_onboarding()

        # Check if we at least moved away from splash
        curr_act = h.get_current_activity()
        if "SplashActivity" in curr_act:
             raise Exception("CRITICAL: Splash screen never completes")

        h.log_step("Transition from Splash Completed")

    except Exception as e:
        result["status"] = "FAIL"
        result["root_cause"] = str(e)
        if h:
            fail_info = h.capture_failure("Critical Failure", "Onboarding", str(e))
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
    test_splash_and_onboarding_flow()
