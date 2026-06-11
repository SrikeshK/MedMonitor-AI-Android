"""
TC-03 | Dashboard & E2E Validation
====================================
Detailed validation of Dashboard components and navigation persistence.
STABILIZED: Optional UI validations do not fail the suite.
"""

import datetime
from base_driver import create_driver, quit_driver
from report_generator import reporter
from config import TEST_EMAIL, TEST_PASSWORD

SUITE = "TC-03 | Dashboard & E2E"

def test_dashboard_and_navigation():
    driver = None
    start_time = datetime.datetime.now()
    h = None
    result = {
        "suite": SUITE,
        "test_name": "Dashboard Validation & Navigation",
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
        h.handle_onboarding() # Optional flow handled inside

        # Rule 1: Login failure is a critical failure
        try:
            h.perform_login(TEST_EMAIL, TEST_PASSWORD)
        except Exception as e:
            raise Exception(f"CRITICAL: Authentication failed - {str(e)}")

        h.handle_mode_selection("patient")

        # 1. Dashboard Component Validation (Rule 6: Retry and continue)
        h.validate_dashboard()

        # 2. Wait for dynamic content and scroll to Explore (Rule 7: Optional scroll)
        h.wait_for_dashboard_load()
        h.scroll_to_id("tvExploreTitle", is_optional=True)

        # 3. Test Bottom Navigation (Rule 2: Optional validations)
        # Navigation to Medicines
        h.navigate_and_verify("navigation_medicines", "MainActivity", "Medicines", is_optional=True)

        # Navigation back to Home
        h.navigate_and_verify("navigation_dashboard", "MainActivity", "Home", is_optional=True)

        # 4. Test Quick Actions (Rule 12: Skip if not validated)
        h.log_step("Checking Quick Analytics (Optional)")
        if h.safe_click("btnQuickAnalytics", "Quick Analytics", timeout=5, is_optional=True):
            if "AnalyticsActivity" in h.get_current_activity():
                h.log_step("Analytics Activity reached")
                driver.back()

        h.log_step("Checking Quick Inventory (Optional)")
        if h.safe_click("btnQuickInventory", "Quick Inventory", timeout=5, is_optional=True):
            if "InventoryActivity" in h.get_current_activity():
                h.log_step("Inventory Activity reached")
                driver.back()

    except Exception as e:
        # Rule 15: Only critical failures should produce FAIL
        result["status"] = "FAIL"
        result["root_cause"] = str(e)
        if h:
            fail_info = h.capture_failure("Critical Failure", "N/A", str(e))
            result.update(fail_info)

    finally:
        result["duration"] = (datetime.datetime.now() - start_time).total_seconds()
        if h:
            result["steps"] = h.current_test_steps
            # Write internal logs to file for debugging
            with open("internal_debug.log", "a") as f:
                f.write(f"\n--- {SUITE} ---\n")
                f.write("\n".join(h.internal_debug_logs))
                f.write("\n")

        reporter.add_result(result)
        quit_driver(driver)

def run_all():
    test_dashboard_and_navigation()
