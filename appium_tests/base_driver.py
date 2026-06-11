"""
Base Driver Setup for MedMonitor Appium Tests
==============================================
"""

import os
import time
from appium import webdriver
from appium.options.android import UiAutomator2Options
from config import (
    APPIUM_SERVER_URL, DESIRED_CAPS,
    IMPLICIT_WAIT, SCREENSHOTS_DIR
)
from automation_helpers import AutomationHelper

def create_driver():
    """Initialize and return Appium WebDriver with attached helper."""
    os.makedirs(SCREENSHOTS_DIR, exist_ok=True)

    options = UiAutomator2Options()
    for key, val in DESIRED_CAPS.items():
        clean_key = key.replace("appium:", "") if key.startswith("appium:") else key
        options.set_capability(clean_key, val)

    try:
        driver = webdriver.Remote(APPIUM_SERVER_URL, options=options)
        driver.implicitly_wait(IMPLICIT_WAIT)
        driver.helper = AutomationHelper(driver)
        return driver
    except Exception as e:
        raise RuntimeError(f"Appium session failed: {str(e)}")

def quit_driver(driver):
    if driver:
        driver.quit()
