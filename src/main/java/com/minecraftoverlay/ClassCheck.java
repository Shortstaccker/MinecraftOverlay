package com.minecraftoverlay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger("ClassCheck");

    public static void run() {
        check("net.digitalingot.fcef.CefApp");
        check("net.dimaskama.mcef.api.MCEFApi");
        check("org.cef.CefApp");
        
        LOGGER.info("Context ClassLoader: {}", Thread.currentThread().getContextClassLoader());
        LOGGER.info("Mod ClassLoader: {}", ClassCheck.class.getClassLoader());
    }

    private static void check(String name) {
        try {
            Class.forName(name, false, ClassCheck.class.getClassLoader());
            LOGGER.info("Class found (mod loader): {}", name);
        } catch (Throwable e) {
            LOGGER.info("Class NOT found (mod loader): {}", name);
        }
        
        try {
            Class.forName(name, false, Thread.currentThread().getContextClassLoader());
            LOGGER.info("Class found (context loader): {}", name);
        } catch (Throwable e) {
            LOGGER.info("Class NOT found (context loader): {}", name);
        }
    }
}
