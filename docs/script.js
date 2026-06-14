/* Premium micro-interactions for MEDMONITOR AI Landing Page */

document.addEventListener("DOMContentLoaded", () => {
    // 1. Console Welcome Message
    console.log(
        "%c MEDMONITOR AI %c Version 1.0 %c Active Build: PASSED ",
        "background: #6366f1; color: #ffffff; font-family: sans-serif; font-size: 14px; font-weight: bold; border-radius: 4px 0 0 4px; padding: 4px 8px;",
        "background: #1f2937; color: #d1d5db; font-family: sans-serif; font-size: 14px; padding: 4px 8px;",
        "background: #10b981; color: #ffffff; font-family: sans-serif; font-size: 14px; font-weight: bold; border-radius: 0 4px 4px 0; padding: 4px 8px;"
    );
    console.log("Developed by Srikesh K (Information Technology)");

    // 2. Entrance Animation for Cards and Hero
    const fadeElements = [
        document.querySelector(".hero-section"),
        document.querySelector(".status-section"),
        document.querySelector(".portal-section"),
        document.querySelector(".main-footer")
    ];

    // Initialize animation properties
    fadeElements.forEach((el, index) => {
        if (el) {
            el.style.opacity = "0";
            el.style.transform = "translateY(25px)";
            el.style.transition = "opacity 0.8s cubic-bezier(0.16, 1, 0.3, 1), transform 0.8s cubic-bezier(0.16, 1, 0.3, 1)";
            
            // Staggered trigger
            setTimeout(() => {
                el.style.opacity = "1";
                el.style.transform = "translateY(0)";
            }, 150 * (index + 1));
        }
    });

    // 3. Hover Sound or Haptic Trigger Simulation (Visual Indicator)
    const portalButtons = document.querySelectorAll(".portal-btn");
    portalButtons.forEach(btn => {
        btn.addEventListener("mouseenter", () => {
            // Add custom scale or glow effect class if needed
            btn.classList.add("btn-hover-active");
        });
        
        btn.addEventListener("mouseleave", () => {
            btn.classList.remove("btn-hover-active");
        });

        // Simulating subtle click animation
        btn.addEventListener("mousedown", () => {
            btn.style.transform = "scale(0.98) translateY(-2px)";
        });

        btn.addEventListener("mouseup", () => {
            btn.style.transform = "translateY(-5px)";
        });
    });
});
