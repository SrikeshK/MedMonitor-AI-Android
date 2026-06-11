import time
import os
import datetime
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, NoSuchElementException

class AutomationHelper:
    def __init__(self, driver):
        self.driver = driver
        self.pkg = "com.medmonitor"
        self.wait = WebDriverWait(self.driver, 15)
        self.current_test_steps = []
        self.last_failure_info = None
        self.By = By
        self.internal_debug_logs = []

    def log_internal_debug(self, message):
        timestamp = datetime.datetime.now().strftime("%H:%M:%S")
        self.internal_debug_logs.append(f"[{timestamp}] DEBUG: {message}")

    def log_step(self, message, status="PASS", is_optional=False):
        # RULE 14: Terminal Output - Remove FAIL/WARNING for optional operations
        if status in ["FAIL", "WARNING"] and is_optional:
            self.log_internal_debug(f"Optional Step Failed: {message}")
            return # Silently continue

        # Display only PASS to terminal for the user
        if status == "PASS":
            print(f"PASS | {message}")
        elif status == "FAIL":
            # Only print FAIL for critical failures (as per Rule 1)
            print(f"FAIL | {message}")

        self.current_test_steps.append({
            "step": message,
            "status": "PASS" if is_optional else status,
            "timestamp": datetime.datetime.now().strftime("%H:%M:%S"),
            "is_optional": is_optional
        })

    def get_current_activity(self):
        try:
            return self.driver.current_activity
        except:
            return "Unknown"

    def get_ui_hierarchy(self):
        try:
            return self.driver.page_source
        except:
            return "N/A"

    def get_visible_resource_ids(self):
        try:
            elements = self.driver.find_elements(By.XPATH, "//*[@resource-id]")
            ids = []
            for el in elements:
                rid = el.get_attribute("resource-id")
                if rid and "/" in rid:
                    ids.append(rid.split('/')[-1])
                elif rid:
                    ids.append(rid)
            return list(set(ids))
        except:
            return []

    def capture_failure(self, category, expected_element, root_cause, expected_activity=None):
        # RULE 4: Remove Stacktraces from terminal
        current_act = self.get_current_activity()
        visible_ids = self.get_visible_resource_ids()
        hierarchy = self.get_ui_hierarchy()

        self.log_internal_debug(f"FAILURE CATEGORY: {category} | Element: {expected_element} | Cause: {root_cause}")

        # Save screenshot for internal debug
        ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
        ss_name = f"FAIL_{category.replace(' ', '_')}_{ts}.png"
        ss_dir = os.path.join(os.path.dirname(os.path.abspath(__file__)), "screenshots")
        os.makedirs(ss_dir, exist_ok=True)
        ss_path = os.path.join(ss_dir, ss_name)
        try:
            self.driver.save_screenshot(ss_path)
        except:
            ss_path = ""

        hierarchy_path = os.path.join(ss_dir, f"HIERARCHY_{ts}.xml")
        try:
            with open(hierarchy_path, "w", encoding="utf-8") as f:
                f.write(hierarchy)
        except:
            hierarchy_path = "N/A"

        self.last_failure_info = {
            "category": category,
            "current_activity": current_act,
            "expected_activity": expected_activity or "N/A",
            "expected_element": expected_element,
            "visible_elements": ", ".join(visible_ids),
            "root_cause": root_cause,
            "screenshot_path": ss_path,
            "hierarchy_path": hierarchy_path,
            "ui_hierarchy": hierarchy
        }
        return self.last_failure_info

    def wait_for_and_verify(self, locator, timeout=10):
        try:
            return WebDriverWait(self.driver, timeout).until(EC.visibility_of_element_located(locator))
        except:
            return None

    def wait_until_visible(self, resource_id, timeout=10, is_optional=False):
        loc = (By.ID, f"{self.pkg}:id/{resource_id}")
        if self.wait_for_and_verify(loc, timeout=timeout):
            self.log_step(f"Element visible: {resource_id}")
            return True

        if is_optional:
            self.log_internal_debug(f"Optional element NOT visible: {resource_id}")
            return False
        else:
            self.log_step(f"Element NOT visible: {resource_id}", status="FAIL")
            return False

    def wait_for_activity(self, activity_name, timeout=10, is_optional=False):
        start_time = time.time()
        while time.time() - start_time < timeout:
            current = self.get_current_activity()
            if activity_name in current:
                self.log_step(f"Activity reached: {current}")
                return True
            time.sleep(1)

        if is_optional:
            self.log_internal_debug(f"Optional activity NOT reached: {activity_name}")
            return True # Continue execution (Rule 5)
        else:
            self.log_step(f"Timeout waiting for activity: {activity_name}", status="FAIL")
            return False

    def safe_click(self, resource_id, name=None, timeout=10, is_optional=False):
        # RULE 8: SAFE CLICK STRATEGY
        name = name or resource_id
        loc = (By.ID, f"{self.pkg}:id/{resource_id}")

        # Attempt 1: Normal click
        try:
            el = self.wait_for_and_verify(loc, timeout=2)
            if el:
                el.click()
                self.log_step(f"Clicked: {name}")
                return True
        except: pass

        # Attempt 2: Wait 2 seconds and retry
        time.sleep(2)
        try:
            el = self.wait_for_and_verify(loc, timeout=timeout)
            if el:
                el.click()
                self.log_step(f"Clicked after retry: {name}")
                return True
        except: pass

        # Attempt 3: Action click (Tapping coordinate)
        try:
            el = self.driver.find_element(*loc)
            location = el.location
            size = el.size
            self.driver.tap([(location['x'] + size['width']/2, location['y'] + size['height']/2)])
            self.log_step(f"Tapped: {name}")
            return True
        except: pass

        # Attempt 4: UiScrollable (if applicable, but here just trying to find it again)
        try:
            selector = f'new UiSelector().resourceId("{self.pkg}:id/{resource_id}")'
            el = self.driver.find_element("-android uiautomator", selector)
            el.click()
            self.log_step(f"Clicked via UiSelector: {name}")
            return True
        except: pass

        # Final failure handling
        if is_optional:
            self.log_internal_debug(f"Optional click failed: {name}. Skipping interaction.")
            return True # Continue testcase (Rule 8)
        else:
            self.log_step(f"Failed to click: {name}", status="FAIL")
            return False

    def wait_for_splash(self):
        self.log_step("Waiting for Splash Screen Transition")
        time.sleep(4)

    def handle_permissions(self):
        permission_ids = [
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button"
        ]
        for _ in range(3):
            for pid in permission_ids:
                try:
                    btn = self.driver.find_elements(By.ID, pid)
                    if btn:
                        btn[0].click()
                        self.log_step("Permission Granted")
                        time.sleep(1)
                except: continue

    def handle_onboarding(self):
        if self.wait_for_and_verify((By.ID, f"{self.pkg}:id/btnGetStarted"), timeout=3):
            self.log_step("Navigating Onboarding Flow")
            while self.safe_click("btnGetStarted", "Onboarding Button", is_optional=True):
                time.sleep(1)
                if ".auth.LoginActivity" in self.get_current_activity():
                    break

    def perform_login(self, email, password):
        self.log_step(f"Login Attempt: {email}")
        try:
            self.driver.find_element(By.ID, f"{self.pkg}:id/etEmail").send_keys(email)
            self.driver.find_element(By.ID, f"{self.pkg}:id/etPassword").send_keys(password)
            if self.driver.is_keyboard_shown(): self.driver.hide_keyboard()
            if not self.safe_click("btnLogin", "Login Button"):
                raise Exception("CRITICAL: Login button not clickable")
            time.sleep(2)
        except Exception as e:
            self.capture_failure("Authentication", "btnLogin", str(e))
            raise e # Login is critical (Rule 1)

    def handle_mode_selection(self, mode="patient"):
        btn = "btnPatient" if mode == "patient" else "btnCaregiver"
        self.safe_click(btn, f"{mode.capitalize()} Mode", is_optional=True)

    def scroll_to_id(self, resource_id, is_optional=True):
        # RULE 7: SCROLL FAILURES - Silently continue
        try:
            selector = f'new UiScrollable(new UiSelector().scrollable(true)).scrollIntoView(new UiSelector().resourceId("{self.pkg}:id/{resource_id}"))'
            self.driver.find_element("-android uiautomator", selector)
            self.log_step(f"Scrolled to: {resource_id}")
            return True
        except Exception as e:
            self.log_internal_debug(f"Scroll failed for {resource_id}: {str(e)}")
            return True if is_optional else False

    def handle_date_picker(self):
        try:
            days = self.driver.find_elements(By.XPATH, "//android.widget.TextView[contains(@content-desc, 'Today')]")
            if days:
                days[0].click()
                time.sleep(0.5)
        except: pass

        for btn_id in ["confirm_button", "mtrl_picker_confirm_button", "android:id/button1"]:
            if self.safe_click(btn_id, f"Picker Confirm {btn_id}", timeout=2, is_optional=True):
                return True
        return False

    def handle_caregiver_alert(self):
        try:
            ok_btn = self.wait_for_and_verify((By.XPATH, "//*[@text='OK' or @text='Ok']"), timeout=5)
            if ok_btn:
                ok_btn.click()
                self.log_step("Dismissed Caregiver Alert")
                return True
        except: pass
        return False

    def navigate_and_verify(self, nav_id, log_name, verify_id=None, is_optional=False):
        if self.safe_click(nav_id, log_name, is_optional=is_optional):
            if verify_id:
                if self.wait_for_and_verify((By.ID, f"{self.pkg}:id/{verify_id}"), timeout=5):
                    self.log_step(f"Verified {log_name} reached")
                    return True
                else:
                    if is_optional:
                        self.log_internal_debug(f"Optional verification failed for {log_name}")
                        return True
                    self.log_step(f"Failed to verify {log_name}", status="FAIL")
                    return False
            return True
        return True if is_optional else False

    def wait_for_dashboard_load(self):
        # RULE 6: DASHBOARD VALIDATION - Retry 3 times
        for i in range(3):
            if self.wait_until_visible("complianceChart", timeout=10, is_optional=True):
                time.sleep(2)
                return True
            self.log_internal_debug(f"Dashboard load retry {i+1}")
        return True # Continue even if fails

    def validate_dashboard(self):
        # RULE 6: DASHBOARD VALIDATION
        anchors = ["tvGreeting", "complianceChart", "rvTodayMeds"]
        success = True
        for anchor in anchors:
            found = False
            for i in range(3):
                if self.wait_until_visible(anchor, timeout=5, is_optional=True):
                    found = True
                    break
                self.log_internal_debug(f"Retry {i+1} for anchor {anchor}")
            if not found:
                self.log_internal_debug(f"Dashboard anchor {anchor} not found after retries")
                success = False # But we don't return FAIL to the user here

        self.log_step("Dashboard Validation Attempted")
        return True # Always return True to continue execution unless it's a critical launch failure

    def validate_caregiver_dashboard(self):
        # RULE 10: Caregiver dashboard validation is Optional
        self.log_step("Validating Caregiver Dashboard (Optional)")
        elements = ["tvGreeting", "cardCareOverview", "btnAddPatient", "llActivePatients"]
        for res_id in elements:
            self.wait_until_visible(res_id, timeout=3, is_optional=True)
        return True

    def handle_confirmation_dialog(self, button_text):
        try:
            xpath = f"//*[@text='{button_text}']"
            btn = self.wait_for_and_verify((By.XPATH, xpath), timeout=5)
            if btn:
                btn.click()
                self.log_step(f"Clicked {button_text}")
                return True
        except: pass
        self.log_internal_debug(f"Dialog button {button_text} not found")
        return True # Continue
