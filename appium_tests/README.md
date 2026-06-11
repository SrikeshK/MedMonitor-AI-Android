# 🏥 MedMonitor End-to-End Appium Test Suite

This folder contains a complete, robust, and automated Appium E2E testing framework for the MedMonitor Android application. It includes automated tests covering all key user journeys (Patient & Caregiver) and compiles comprehensive test results into a custom-formatted Excel dashboard report with pass/fail visualization charts and interactive screenshot references.

---

## 📂 Project Structure

```text
appium_tests/
├── config.py                 # Android caps, credentials, timeouts, server settings
├── base_driver.py            # Appium driver management, element waiting, and utilities
├── report_generator.py       # Custom Excel report compilation using openpyxl
├── run_all_tests.py          # Master test runner script coordinating execution
├── requirements.txt          # Python package dependencies
├── README.md                 # Setup and execution guide (this file)
├── reports/                  # Directory where the Excel spreadsheet reports are generated
├── screenshots/              # Folder housing PNG screenshots taken during test runs
└── tests/                    # E2E test module files
    ├── test_01_splash_onboarding.py
    ├── test_02_authentication.py
    ├── test_03_dashboard.py
    ├── test_04_medicine_management.py
    ├── test_05_dose_confirmation.py
    ├── test_06_caregiver_flow.py
    ├── test_07_settings_profile.py
    └── test_08_analytics_inventory.py
```

---

## 🛠 Prerequisites & Setup

To run this test suite, your environment needs Python, Android SDK/ADB, Appium server, and a running Android device/emulator.

### 1. Install System Requirements
- **Python**: Install Python 3.8 or higher.
- **Android Studio & SDK**: Install Android Studio. Ensure you have the Android SDK platform tools added to your system environment variables.
- **Node.js**: Install Node.js (for running Appium server).

### 2. Install Appium Server & UIAutomator2 Driver
Install Appium server globally and set up the Android driver using `npm`:
```bash
# Install Appium 2.x
npm install -g appium

# Install the UiAutomator2 driver for Android
appium driver install uiautomator2
```

### 3. Install Python Dependencies
Install the required packages in your Python environment:
```bash
pip install -r requirements.txt
```

---

## ⚙️ Configuration

Open `config.py` to adapt the settings to match your test device or environment:
- **`DESIRED_CAPS`**: Set `platformVersion` (Android OS version) and `deviceName` (from running `adb devices`).
- **`TEST_EMAIL` & `TEST_PASSWORD`**: Configure credentials for a valid test account in your Firebase database.
- **`APPIUM_SERVER_URL`**: Update this if your Appium server is running on a different port or host.

---

## 🚀 Execution

Follow these steps **in order** to execute the full E2E test suite:

### Step 1 — Launch Android Emulator or Connect Device
- Open **Android Studio → Device Manager** and click ▶ to start your AVD emulator, **OR**
- Connect a physical Android device via USB with **USB Debugging** enabled.
- Verify the device is recognised by running:
  ```bash
  adb devices
  ```
  You should see your device listed (e.g., `emulator-5554  device`).

### Step 2 — Start the Appium Server
Open a **new terminal window** and run:
```bash
appium
```
Leave this terminal running in the background. You should see `Appium REST http interface listener started` in the output.

### Step 3 — Navigate to the Test Folder
Open a **second terminal window** and navigate into the `appium_tests` directory:
```bash
cd appium_tests
```

### Step 4 — Execute the Master Test Runner
From inside the `appium_tests` directory, run:
```bash
python run_all_tests.py
```
This will execute all 8 test suites (TC-01 through TC-08) sequentially. You can watch the live interactions on your device/emulator screen as each test case runs.

### Step 5 — View the Generated Report
Once execution is complete, open the auto-generated Excel report:
```
appium_tests/reports/MedMonitor_E2E_Test_Report.xlsx
```
Open it using **Microsoft Excel**, **Google Sheets**, or **LibreOffice Calc** to review the full pass/fail dashboard and per-suite results.

---

## 📊 Automated Excel Analysis Report

Upon completion, a spreadsheet named `MedMonitor_E2E_Test_Report.xlsx` will be generated inside the `reports/` folder.

### Key Report Features:
- **📊 Summary Dashboard**:
  - Color-coded KPI boxes summarizing **Total Tests**, **Passed**, **Failed**, **Skipped**, **Pass Rate**, and **Total Duration**.
  - Interactive **Test Results Distribution Pie Chart** visualizing compliance statistics.
  - Granular breakdown table of test cases per suite.
- **📋 Detailed Test Logs**:
  - Individual tabs for each suite (`TC-01` through `TC-08`).
  - Precise step-by-step description of what was executed.
  - Hyperlinks to screenshots taken at the exact failure (or success verification) steps.
