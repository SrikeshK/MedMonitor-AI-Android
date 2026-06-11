"""
Master Test Runner for MedMonitor Appium E2E Test Suite
=========================================================
Runs all test modules sequentially, compiles outcomes,
and triggers the rich Excel report generation.
"""

import sys
import os
import time
import datetime
from colorama import init, Fore, Style

# Add appium_tests folder to python paths to ensure correct imports
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "tests"))

# Import all tests
import test_01_splash_onboarding
import test_02_authentication
import test_03_dashboard
import test_04_medicine_management
import test_05_dose_confirmation
import test_06_caregiver_flow
import test_07_settings_profile
import test_08_analytics_inventory

from report_generator import reporter


def run_test_suite():
    init(autoreset=True)

    print(Fore.CYAN + Style.BRIGHT + "\n" + "="*80)
    print(Fore.CYAN + Style.BRIGHT + "  [+]  MEDMONITOR ANDROID MOBILE APP - E2E APPIUM TEST RUNNER  [+]")
    print(Fore.CYAN + Style.BRIGHT + "="*80 + "\n")

    start_time = datetime.datetime.now()

    # 1. Splash & Onboarding
    try:
        test_01_splash_onboarding.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-01: {e}")

    # 2. Authentication
    try:
        test_02_authentication.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-02: {e}")

    # 3. Dashboard
    try:
        test_03_dashboard.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-03: {e}")

    # 4. Medicine Management
    try:
        test_04_medicine_management.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-04: {e}")

    # 5. Dose Confirmation
    try:
        test_05_dose_confirmation.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-05: {e}")

    # 6. Caregiver Flow
    try:
        test_06_caregiver_flow.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-06: {e}")

    # 7. Settings & Profile
    try:
        test_07_settings_profile.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-07: {e}")

    # 8. Analytics & Inventory
    try:
        test_08_analytics_inventory.run_all()
    except Exception as e:
        print(Fore.RED + f"Error running TC-08: {e}")

    end_time = datetime.datetime.now()
    duration = (end_time - start_time).total_seconds()

    print(Fore.CYAN + "\n" + "="*80)
    print(Fore.CYAN + f"  Test Suite run completed in {duration:.1f} seconds")
    print(Fore.CYAN + "="*80)

    # Compile the final Excel report
    print(Fore.YELLOW + "Compiling results and generating Excel report...")
    try:
        report_path = reporter.generate()
        print(Fore.GREEN + Style.BRIGHT + f"\nSUCCESS: Excel Report generated successfully!")
        print(Fore.GREEN + f"Path: {report_path}\n")
    except Exception as e:
        print(Fore.RED + f"Failed to generate Excel report: {e}\n")


if __name__ == "__main__":
    run_test_suite()
