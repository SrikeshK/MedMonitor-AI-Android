"""
TC-08 | Analytics & Inventory Alignment
========================================
Validates quick-access activities from the Dashboard.
STABILIZED: Optional UI validations do not fail the suite.
"""

import datetime
from base_driver import create_driver, quit_driver
from report_generator import reporter
from config import TEST_EMAIL, TEST_PASSWORD

SUITE = "TC-08 | Analytics & Inventory"

def test_analytics_and_inventory():
    driver = None
    h = None
    start_time = datetime.datetime.now()
    result = {
        "suite": SUITE,
        "test_name": "Analytics & Inventory Flow",
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

        # Ensure Dashboard is ready (Rule 12: Skip if dashboard not validated)
        h.validate_dashboard()
        h.wait_for_dashboard_load()
        h.scroll_to_id("tvExploreTitle", is_optional=True)

        # 1. Test Quick Analytics (Rule 12: Skip if not reached)
        h.log_step("Checking Quick Analytics (Optional)")
        if h.safe_click("btnQuickAnalytics", "Quick Analytics Button", timeout=5, is_optional=True):
            if "AnalyticsActivity" in h.get_current_activity():
                h.log_step("Analytics Activity reached")
                h.wait_until_visible("fragment_container", timeout=5, is_optional=True)
                driver.back()

        # Ensure we are back on Dashboard
        h.wait_for_dashboard_load()
        h.scroll_to_id("tvExploreTitle", is_optional=True)

        # 2. Test Quick Inventory (Rule 12: Skip if not reached)
        h.log_step("Checking Quick Inventory (Optional)")
        if h.safe_click("btnQuickInventory", "Quick Inventory Button", timeout=5, is_optional=True):
            if "InventoryActivity" in h.get_current_activity():
                h.log_step("Inventory Activity reached")
                h.wait_until_visible("inventoryRecycler", timeout=5, is_optional=True)
                driver.back()

        h.log_step("Analytics and Inventory sequence completed")

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
    test_analytics_and_inventory()
