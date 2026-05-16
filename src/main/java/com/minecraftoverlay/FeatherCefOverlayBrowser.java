package com.minecraftoverlay;

import net.digitalingot.fcef.CefApp;
import net.digitalingot.fcef.CefBrowser;
import net.digitalingot.fcef.CefClient;
import net.digitalingot.fcef.CefFrame;
import net.digitalingot.fcef.CefKeyEventType;
import net.digitalingot.fcef.CefMouseButtonType;
import net.digitalingot.fcef.CefRect;
import net.digitalingot.fcef.CefReturnValue;
import net.digitalingot.fcef.CefSettings;
import net.digitalingot.fcef.handler.CefLifeSpanHandler;
import net.digitalingot.fcef.handler.CefLoadHandler;
import net.digitalingot.fcef.handler.CefRenderHandler;
import net.digitalingot.fcef.handler.CefRequestHandler;
import net.digitalingot.fcef.handler.CefResourceRequestHandler;
import net.digitalingot.fcef.network.CefRequest;
import net.digitalingot.fcef.renderer.SharedTexture;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.lwjgl.glfw.GLFW;

import java.awt.event.KeyEvent;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Predicate;


final class FeatherCefOverlayBrowser implements OverlayBrowser {
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/103.0.0.0 Safari/537.36";
    private static CefApp sharedApp;
    private static boolean processPrepared;

    private final CefClient client;
    private final Consumer<String> urlChanged;
    private final Predicate<String> requestBlocker;
    private final Runnable blockedRequestCallback;
    private final Object frameLock = new Object();
    private CefBrowser browser;
    private NativeImageBackedTexture texture;
    private SharedTexture sharedTexture;
    private boolean sharedTextureLocked;
    private byte[] pendingFrame;
    private int pendingWidth;
    private int pendingHeight;
    private long pendingSharedHandle;
    private long openedSharedHandle;
    private boolean pendingSharedFrame;
    private boolean pendingSharedNewTexture;
    private long lastInvalidateNanos;
    private int renderWidth = 1;
    private int renderHeight = 1;
    private GlTexture acceleratedTexture;
    private boolean acceleratedTextureReady;
    private boolean loggedFirstPaint;
    private boolean loggedAcceleratedPaint;
    private boolean loggedAcceleratedTexture;
    private boolean loggedAcceleratedFailure;

    static synchronized void prepareProcess() {
        if (processPrepared) return;
        processPrepared = true;
        System.setProperty("jcef.offscreen_rendering_enabled", "true");
        System.setProperty("mcef.forced", "true");
    }

    FeatherCefOverlayBrowser(String url, Consumer<String> urlChanged, Predicate<String> requestBlocker, Runnable blockedRequestCallback) throws IOException {
        this.urlChanged = urlChanged;
        this.requestBlocker = requestBlocker;
        this.blockedRequestCallback = blockedRequestCallback;
        this.client = getApp().createClient();
        installHandlers();
        this.browser = client.createBrowser(normalizeUrl(url));
        this.browser.setVisibility(true);
        this.browser.invalidate();
    }

    @Override
    public void resize(int width, int height) {
        int clampedWidth = Math.max(1, width);
        int clampedHeight = Math.max(1, height);
        if (renderWidth != clampedWidth || renderHeight != clampedHeight) {
            renderWidth = clampedWidth;
            renderHeight = clampedHeight;
            // Do NOT close the accelerated texture here. Closing it immediately
            // invalidates openedSharedHandle before CEF delivers the new frame's
            // shared handle, causing open() to fail and the browser to show
            // "waiting for first frame" indefinitely after every resize.
            // ensureAcceleratedTexture() will lazily recreate the GL texture
            // when the next frame arrives with the updated dimensions.
            // Also reset the failure flag so the retry path is not suppressed.
            loggedAcceleratedFailure = false;
        }
        if (browser != null) {
            browser.resize(clampedWidth, clampedHeight);
            browser.invalidate();
        }
    }

    @Override
    public void setFocus(boolean focused) {
        if (browser != null) browser.setFocus(focused);
    }

    @Override
    public void onMouseClicked(Click event, boolean isDoubleClick) {
        if (browser == null) return;
        int button = toCefMouseButton(event.button());
        int clickCount = isDoubleClick ? 2 : 1;
        browser.sendMouseClickEvent((int) event.x(), (int) event.y(), event.modifiers(), button, false, clickCount);
        browser.invalidate();
    }

    @Override
    public void onMouseReleased(Click event) {
        if (browser == null) return;
        browser.sendMouseClickEvent((int) event.x(), (int) event.y(), event.modifiers(), toCefMouseButton(event.button()), true, 1);
        browser.invalidate();
    }

    @Override
    public void onMouseScrolled(int x, int y, double scrollY) {
        if (browser == null) return;
        browser.sendMouseWheelEvent(x, y, 0, (int) Math.round(scrollY * 80.0D), 0);
        browser.invalidate();
    }

    @Override
    public void onMouseMoved(int x, int y) {
        if (browser != null) browser.sendMouseMoveEvent(x, y, 0, false);
    }

    @Override
    public void onKeyPressed(KeyInput event) {
        if (browser == null) return;
        int windowsKeyCode = toWindowsKeyCode(event.key());
        browser.sendKeyEvent(CefKeyEventType.KEYEVENT_RAWKEYDOWN, toCefModifiers(event.modifiers()), windowsKeyCode, event.scancode(), false, '\0', '\0');
        browser.invalidate();
    }

    @Override
    public void onKeyReleased(KeyInput event) {
        if (browser == null) return;
        int windowsKeyCode = toWindowsKeyCode(event.key());
        browser.sendKeyEvent(CefKeyEventType.KEYEVENT_KEYUP, toCefModifiers(event.modifiers()), windowsKeyCode, event.scancode(), false, '\0', '\0');
    }

    @Override
    public void onCharTyped(CharInput event) {
        if (browser == null || !event.isValidChar()) return;
        String text = event.asString();
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            browser.sendKeyEvent(CefKeyEventType.KEYEVENT_CHAR, toCefModifiers(event.modifiers()), character, character, false, character, character);
        }
        browser.invalidate();
    }

    @Override
    public net.minecraft.client.texture.AbstractTexture getTexture() {
        pump();
        if (browser != null) {
            long now = System.nanoTime();
            if (now - lastInvalidateNanos > 250_000_000L) {
                lastInvalidateNanos = now;
                browser.invalidate();
            }
        }
        uploadPendingFrame();
        return texture;
    }

    @Override
    public String getUrl() {
        return currentUrl();
    }

    @Override
    public boolean isTextureVerticallyFlipped() {
        return true;
    }

    @Override
    public void reload() {
        if (browser != null) browser.reload();
    }

    @Override
    public void goBack() {
        executeJavaScript("history.back()", currentUrl(), 0);
    }

    @Override
    public void goForward() {
        executeJavaScript("history.forward()", currentUrl(), 0);
    }

    @Override
    public void loadUrl(String url) {
        if (browser != null) browser.loadUrl(normalizeUrl(url));
    }

    @Override
    public void executeJavaScript(String script, String url, int line) {
        if (browser != null) browser.executeJavaScript(script, url, line);
    }

    @Override
    public void sendNativeKeyEvent(int awtEventId, int glfwKey, int modifiers) {
        if (browser == null) return;
        int type = awtEventId == KeyEvent.KEY_RELEASED ? CefKeyEventType.KEYEVENT_KEYUP
                : awtEventId == KeyEvent.KEY_TYPED ? CefKeyEventType.KEYEVENT_CHAR
                : CefKeyEventType.KEYEVENT_RAWKEYDOWN;
        char character = awtEventId == KeyEvent.KEY_TYPED ? toKeyChar(glfwKey) : '\0';
        int windowsKeyCode = awtEventId == KeyEvent.KEY_TYPED ? character : toWindowsKeyCode(glfwKey);
        browser.sendKeyEvent(type, toCefModifiers(modifiers), windowsKeyCode, 0, false, character, character);
    }

    @Override
    public Object getNativeBrowser() {
        return browser;
    }

    @Override
    public void close() {
        if (browser != null) {
            try {
                browser.closeBrowser();
            } catch (Throwable exception) {
                MinecraftOverlay.LOGGER.warn("Failed to close Feather browser", exception);
            }
            browser = null;
        }
        try {
            client.close();
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to close Feather browser client", exception);
        }
        closeTexture();
        closeAcceleratedTexture();
    }

    private void installHandlers() {
        client.setLifeSpanHandler(new CefLifeSpanHandler() {
            @Override
            public void onAfterCreated(CefBrowser createdBrowser) {
                browser = createdBrowser;
                browser.setVisibility(true);
                browser.invalidate();
                notifyUrl(createdBrowser.getUrl());
            }
        });
        client.setLoadHandler(new CefLoadHandler() {
            @Override
            public void onLoadEnd(CefBrowser loadedBrowser, CefFrame frame, int statusCode) {
                loadedBrowser.setVisibility(true);
                loadedBrowser.invalidate();
                notifyUrl(loadedBrowser.getUrl());
            }

            @Override
            public void onLoadError(CefBrowser loadedBrowser, CefFrame frame, int errorCode, String errorText, String failedUrl) {
                MinecraftOverlay.LOGGER.warn("Feather browser load error: {} (code {}) for URL: {}", errorText, errorCode, failedUrl);
                if (failedUrl != null && !failedUrl.isBlank()) notifyUrl(failedUrl);
            }
        });
        client.setRequestHandler(new CefRequestHandler() {
            private final CefResourceRequestHandler resourceHandler = new CefResourceRequestHandler() {
                @Override
                public int onBeforeResourceLoad(CefBrowser requestBrowser, CefFrame frame, CefRequest request, net.digitalingot.fcef.CefCallback callback) {
                    String requestUrl = request == null ? null : request.getURL();
                    if (requestUrl != null && requestBlocker != null && requestBlocker.test(requestUrl)) {
                        if (blockedRequestCallback != null) blockedRequestCallback.run();
                        return CefReturnValue.RV_CANCEL;
                    }
                    return CefReturnValue.RV_CONTINUE;
                }
            };

            @Override
            public CefResourceRequestHandler getResourceRequestHandler(CefBrowser requestBrowser, CefFrame frame, CefRequest request, boolean isNavigation, boolean isDownload, String requestInitiator, net.digitalingot.fcef.misc.BoolRef disableDefaultHandling) {
                return resourceHandler;
            }

            @Override
            public void onDocumentAvailableInMainFrame(CefBrowser documentBrowser) {
                documentBrowser.setVisibility(true);
                documentBrowser.invalidate();
                notifyUrl(documentBrowser.getUrl());
            }
        });
        client.setRenderHandler(new CefRenderHandler() {
            @Override
            public void onPaint(CefBrowser paintBrowser, boolean popup, CefRect dirtyRect, ByteBuffer buffer, int width, int height) {
                if (popup || width <= 0 || height <= 0 || buffer == null) return;
                if (!loggedFirstPaint) {
                    loggedFirstPaint = true;
                    MinecraftOverlay.LOGGER.info("Feather browser delivered first software frame: {}x{}", width, height);
                }
                ByteBuffer copy = buffer.duplicate();
                copy.position(0);
                int length = Math.min(copy.remaining(), width * height * 4);
                byte[] frame = new byte[length];
                copy.get(frame);
                synchronized (frameLock) {
                    pendingFrame = frame;
                    pendingWidth = width;
                    pendingHeight = height;
                }
            }

            @Override
            public void onAcceleratedPaint(CefBrowser paintBrowser, boolean popup, long sharedHandle) {
                queueAcceleratedFrame(popup, sharedHandle, false);
            }

            @Override
            public void onAcceleratedPaint2(CefBrowser paintBrowser, boolean popup, long sharedHandle, boolean newTexture) {
                queueAcceleratedFrame(popup, sharedHandle, newTexture);
            }
        });
    }

    private void queueAcceleratedFrame(boolean popup, long sharedHandle, boolean newTexture) {
        if (popup || sharedHandle == 0L) return;
        if (!loggedAcceleratedPaint) {
            loggedAcceleratedPaint = true;
            MinecraftOverlay.LOGGER.info("Feather browser delivered accelerated frames. Using shared texture rendering.");
        }
        synchronized (frameLock) {
            pendingSharedHandle = sharedHandle;
            pendingSharedFrame = true;
            pendingSharedNewTexture |= newTexture;
        }
    }

    private void notifyUrl(String newUrl) {
        if (newUrl != null && !newUrl.isBlank() && !"about:blank".equals(newUrl) && !newUrl.startsWith("ERR_") && urlChanged != null) {
            urlChanged.accept(newUrl);
        }
    }

    private String currentUrl() {
        try {
            return browser == null ? "" : browser.getUrl();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null)
            return "";
        String trimmed = url.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("https://youtube.com") || lower.equals("http://youtube.com")
                || lower.equals("https://www.youtube.com") || lower.equals("http://www.youtube.com")) {
            return "https://m.youtube.com/";
        }
        if (lower.startsWith("https://youtube.com/"))
            return "https://m.youtube.com/" + trimmed.substring("https://youtube.com/".length());
        if (lower.startsWith("http://youtube.com/"))
            return "https://m.youtube.com/" + trimmed.substring("http://youtube.com/".length());
        if (lower.startsWith("https://www.youtube.com/"))
            return "https://m.youtube.com/" + trimmed.substring("https://www.youtube.com/".length());
        if (lower.startsWith("http://www.youtube.com/"))
            return "https://m.youtube.com/" + trimmed.substring("http://www.youtube.com/".length());
        return trimmed;
    }

    private void uploadPendingFrame() {
        if (uploadPendingSharedFrame())
            return;
        byte[] frame;
        int width;
        int height;
        synchronized (frameLock) {
            if (pendingFrame == null) return;
            frame = pendingFrame;
            width = pendingWidth;
            height = pendingHeight;
            pendingFrame = null;
        }
        if (frame.length < width * height * 4) return;
        if (texture == null || texture.getImage() == null || texture.getImage().getWidth() != width || texture.getImage().getHeight() != height) {
            closeTexture();
            texture = new NativeImageBackedTexture("minecraftoverlay:browser", width, height, false);
        }
        NativeImage image = texture.getImage();
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int blue = frame[index++] & 0xFF;
                int green = frame[index++] & 0xFF;
                int red = frame[index++] & 0xFF;
                int alpha = frame[index++] & 0xFF;
                image.setColorArgb(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        texture.upload();
    }

    private boolean uploadPendingSharedFrame() {
        long sharedHandle;
        boolean newTexture;
        synchronized (frameLock) {
            if (!pendingSharedFrame)
                return false;
            sharedHandle = pendingSharedHandle;
            newTexture = pendingSharedNewTexture;
            pendingSharedFrame = false;
            pendingSharedNewTexture = false;
        }
        if (sharedHandle == 0L)
            return false;
        int width = Math.max(1, renderWidth);
        int height = Math.max(1, renderHeight);
        try {
            boolean needsTexture = texture == null
                    || texture.getImage() == null
                    || texture.getImage().getWidth() != width
                    || texture.getImage().getHeight() != height
                    || sharedTexture == null
                    || newTexture
                    || openedSharedHandle != sharedHandle;
            if (needsTexture) {
                closeSharedTexture();
                if (texture == null || texture.getImage() == null || texture.getImage().getWidth() != width
                        || texture.getImage().getHeight() != height) {
                    closeTexture();
                    texture = new NativeImageBackedTexture("minecraftoverlay:feather_browser", width, height, false);
                    texture.upload();
                }
                if (!(texture.getGlTexture() instanceof GlTexture glTexture)) {
                    if (!loggedAcceleratedFailure) {
                        loggedAcceleratedFailure = true;
                        MinecraftOverlay.LOGGER.warn("Feather accelerated browser texture is not an OpenGL texture.");
                    }
                    return false;
                }
                sharedTexture = new SharedTexture(glTexture.getGlId());
                if (!sharedTexture.open(sharedHandle)) {
                    closeSharedTexture();
                    openedSharedHandle = 0L;
                    if (!loggedAcceleratedFailure) {
                        loggedAcceleratedFailure = true;
                        MinecraftOverlay.LOGGER.warn("Could not open Feather accelerated browser shared texture.");
                    }
                    return false;
                }
                openedSharedHandle = sharedHandle;
                acceleratedTextureReady = false;
            }
            if (!sharedTextureLocked) {
                if (!sharedTexture.lock()) {
                    if (!loggedAcceleratedFailure) {
                        loggedAcceleratedFailure = true;
                        MinecraftOverlay.LOGGER.warn("Could not lock Feather accelerated browser shared texture.");
                    }
                    return false;
                }
                sharedTextureLocked = true;
            }
            acceleratedTextureReady = true;
            if (!loggedAcceleratedTexture) {
                loggedAcceleratedTexture = true;
                MinecraftOverlay.LOGGER.info("Feather accelerated browser texture is ready: {}x{}", width, height);
            }
            return true;
        } catch (Throwable exception) {
            if (!loggedAcceleratedFailure) {
                loggedAcceleratedFailure = true;
                MinecraftOverlay.LOGGER.warn("Failed to upload Feather accelerated browser texture", exception);
            }
            closeAcceleratedTexture();
            return false;
        }
    }


    private void closeTexture() {
        if (texture != null) {
            texture.close();
            texture = null;
        }
    }

    private void closeAcceleratedTexture() {
        closeSharedTexture();
        // acceleratedTexture field removed - using sharedTexture only
        openedSharedHandle = 0L;
        acceleratedTextureReady = false;
    }

    private void closeSharedTexture() {
        if (sharedTexture != null) {
            try {
                if (sharedTextureLocked)
                    sharedTexture.unlock();
            } catch (Throwable exception) {
                MinecraftOverlay.LOGGER.debug("Failed to unlock Feather shared texture", exception);
            }
            sharedTextureLocked = false;
            try {
                sharedTexture.close();
            } catch (Throwable exception) {
                MinecraftOverlay.LOGGER.debug("Failed to close Feather shared texture", exception);
            }
            sharedTexture = null;
        }
    }

    private static synchronized CefApp getApp() throws IOException {
        if (sharedApp != null) return sharedApp;
        prepareProcess();
        Path configDir = Path.of(System.getenv("APPDATA") != null ? System.getenv("APPDATA") : System.getProperty("user.home"), ".minecraft");
        Path cachePath = configDir.resolve("minecraftoverlay").resolve("feather_cache");
        
        try {
            Files.createDirectories(cachePath);
            Files.deleteIfExists(cachePath.resolve("SingletonLock"));
        } catch (IOException ignored) {}

        CefSettings settings = CefSettings.builder()
                .commandLineArgsDisabled(false)
                .rootCachePath(cachePath.getParent())
                .cachePath(cachePath)
                .userDataPath(cachePath.getParent().resolve("feather_user"))
                .persistSessionCookies(true)
                .persistUserPreferences(true)
                .userAgent(USER_AGENT)
                .userAgentProduct("Chrome/103.0.0.0")
                .hardwareAcceleration(true)
                .logSeverity(CefSettings.CefLogSeverity.WARNING)
                .build();
        sharedApp = CefApp.getInstance(settings);
        return sharedApp;
    }

    static void pump() {
        CefApp app = sharedApp;
        if (app == null) return;
        try {
            app.doWork();
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.debug("Feather browser pump failed", exception);
        }
    }

    private static int toCefMouseButton(int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) return CefMouseButtonType.MBT_RIGHT;
        if (button == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) return CefMouseButtonType.MBT_MIDDLE;
        return CefMouseButtonType.MBT_LEFT;
    }

    private static int toCefModifiers(int modifiers) {
        int cefModifiers = 0;
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) cefModifiers |= 1 << 1;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) cefModifiers |= 1 << 2;
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) cefModifiers |= 1 << 3;
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) cefModifiers |= 1 << 7;
        return cefModifiers;
    }

    private static int toWindowsKeyCode(int glfwKey) {
        if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) return glfwKey;
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) return glfwKey;
        if (glfwKey >= GLFW.GLFW_KEY_F1 && glfwKey <= GLFW.GLFW_KEY_F24) return 0x70 + (glfwKey - GLFW.GLFW_KEY_F1);
        return switch (glfwKey) {
            case GLFW.GLFW_KEY_BACKSPACE -> KeyEvent.VK_BACK_SPACE;
            case GLFW.GLFW_KEY_TAB -> KeyEvent.VK_TAB;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> KeyEvent.VK_ENTER;
            case GLFW.GLFW_KEY_ESCAPE -> KeyEvent.VK_ESCAPE;
            case GLFW.GLFW_KEY_SPACE -> KeyEvent.VK_SPACE;
            case GLFW.GLFW_KEY_LEFT -> KeyEvent.VK_LEFT;
            case GLFW.GLFW_KEY_UP -> KeyEvent.VK_UP;
            case GLFW.GLFW_KEY_RIGHT -> KeyEvent.VK_RIGHT;
            case GLFW.GLFW_KEY_DOWN -> KeyEvent.VK_DOWN;
            case GLFW.GLFW_KEY_DELETE -> KeyEvent.VK_DELETE;
            case GLFW.GLFW_KEY_INSERT -> KeyEvent.VK_INSERT;
            case GLFW.GLFW_KEY_HOME -> KeyEvent.VK_HOME;
            case GLFW.GLFW_KEY_END -> KeyEvent.VK_END;
            case GLFW.GLFW_KEY_PAGE_UP -> KeyEvent.VK_PAGE_UP;
            case GLFW.GLFW_KEY_PAGE_DOWN -> KeyEvent.VK_PAGE_DOWN;
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> KeyEvent.VK_SHIFT;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> KeyEvent.VK_CONTROL;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> KeyEvent.VK_ALT;
            default -> glfwKey;
        };
    }

    private static char toKeyChar(int glfwKey) {
        return glfwKey == GLFW.GLFW_KEY_BACKSPACE ? '\b' : '\n';
    }
}
