"""
MedMonitor Appium Test Configuration
=====================================
"""

import os

# Paths
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.dirname(BASE_DIR)
REPORTS_DIR = os.path.join(BASE_DIR, "reports")
SCREENSHOTS_DIR = os.path.join(BASE_DIR, "screenshots")

# APK Detection
APK_PATHS = [
    os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "debug", "app-debug.apk"),
    os.path.join(PROJECT_ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk"),
]

def get_apk_path():
    for path in APK_PATHS:
        if os.path.exists(path):
            return path
    return None

APPIUM_SERVER_URL = "http://127.0.0.1:4723"

DESIRED_CAPS = {
    "platformName": "Android",
    "appium:automationName": "UiAutomator2",
    "appium:deviceName": "Android Emulator",
    "appium:platformVersion": "13",
    "appium:appPackage": "com.medmonitor",
    "appium:appActivity": ".ui.SplashActivity",
    "appium:noReset": False,
    "appium:fullReset": False,
    "appium:autoGrantPermissions": True,
    "appium:newCommandTimeout": 120,
}

found_apk = get_apk_path()
if found_apk:
    DESIRED_CAPS["appium:app"] = found_apk

# Test Account Credentials
TEST_EMAIL = "admin@gmail.com"
TEST_PASSWORD = "admin123"

# Test Medicine Data
MEDICINE_TEST_DATA = {
    "name": "Amoxicillin AI Test",
    "dose": "1",
    "quantity": "30",
}

# Timeouts
IMPLICIT_WAIT = 5
EXPLICIT_WAIT = 15
SPLASH_WAIT = 5

# Reports
EXCEL_REPORT_NAME = "MedMonitor_Automation_Report.xlsx"
