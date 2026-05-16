package com.minecraftoverlay;

import net.dimaskama.mcef.api.MCEFBrowser;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.AbstractTexture;
import org.lwjgl.glfw.GLFW;

import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Method;

final class McefOverlayBrowser implements OverlayBrowser {
    private final MCEFBrowser browser;
    private final BorrowedGpuTexture texture = new BorrowedGpuTexture();

    McefOverlayBrowser(MCEFBrowser browser) {
        this.browser = browser;
    }

    @Override
    public void resize(int width, int height) {
        browser.resize(width, height);
    }

    @Override
    public void setFocus(boolean focused) {
        browser.setFocus(focused);
    }

    @Override
    public void onMouseClicked(Click event, boolean isDoubleClick) {
        browser.onMouseClicked(event, isDoubleClick);
    }

    @Override
    public void onMouseReleased(Click event) {
        browser.onMouseReleased(event);
    }

    @Override
    public void onMouseScrolled(int x, int y, double scrollY) {
        browser.onMouseScrolled(x, y, scrollY);
    }

    @Override
    public void onMouseMoved(int x, int y) {
        browser.onMouseMoved(x, y);
    }

    @Override
    public void onKeyPressed(KeyInput event) {
        browser.onKeyPressed(event);
    }

    @Override
    public void onKeyReleased(KeyInput event) {
        browser.onKeyReleased(event);
    }

    @Override
    public void onCharTyped(CharInput event) {
        browser.onCharTyped(event);
    }

    @Override
    public AbstractTexture getTexture() {
        GpuTexture gpuTexture = browser.getTexture();
        GpuTextureView textureView = browser.getTextureView();
        if (gpuTexture == null || textureView == null) {
            return null;
        }
        texture.update(gpuTexture, textureView);
        return texture;
    }

    @Override
    public GpuTextureView getTextureView() {
        return browser.getTextureView();
    }

    @Override
    public String getUrl() {
        try {
            Object cefBrowser = getNativeBrowser();
            Object url = findMethod(cefBrowser.getClass(), "getURL").invoke(cefBrowser);
            return url instanceof String text ? text : "";
        } catch (Throwable ignored) {
            try {
                Object cefBrowser = getNativeBrowser();
                Object url = findMethod(cefBrowser.getClass(), "getUrl").invoke(cefBrowser);
                return url instanceof String text ? text : "";
            } catch (Throwable ignoredAgain) {
                return "";
            }
        }
    }

    @Override
    public void reload() {
        invokeNative("reload");
    }

    @Override
    public void goBack() {
        invokeNative("goBack");
    }

    @Override
    public void goForward() {
        invokeNative("goForward");
    }

    @Override
    public void loadUrl(String url) {
        invokeNative("loadURL", new Class<?>[]{String.class}, url);
    }

    @Override
    public void executeJavaScript(String script, String url, int line) {
        invokeNative("executeJavaScript", new Class<?>[]{String.class, String.class, int.class}, script, url, line);
    }

    @Override
    public void sendNativeKeyEvent(int awtEventId, int glfwKey, int modifiers) {
        try {
            Object cefBrowser = getNativeBrowser();
            Component component = (Component) findMethod(cefBrowser.getClass(), "getUIComponent").invoke(cefBrowser);
            KeyEvent awtEvent = new KeyEvent(
                    component,
                    awtEventId,
                    System.currentTimeMillis(),
                    toAwtInputModifiers(modifiers),
                    awtEventId == KeyEvent.KEY_TYPED ? KeyEvent.VK_UNDEFINED : toAwtKeyCode(glfwKey),
                    awtEventId == KeyEvent.KEY_TYPED ? toAwtKeyChar(glfwKey) : KeyEvent.CHAR_UNDEFINED
            );
            findMethod(cefBrowser.getClass(), "sendKeyEvent", KeyEvent.class).invoke(cefBrowser, awtEvent);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("MCEF native key path failed", exception);
        }
    }

    @Override
    public Object getNativeBrowser() {
        try {
            return browser.getClass().getMethod("getCefBrowser").invoke(browser);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not access MCEF native browser", exception);
        }
    }

    @Override
    public void close() {
        browser.close();
    }

    private static final class BorrowedGpuTexture extends AbstractTexture {
        private void update(GpuTexture texture, GpuTextureView view) {
            this.glTexture = texture;
            this.glTextureView = view;
        }

        @Override
        public void close() {
            this.glTexture = null;
            this.glTextureView = null;
        }
    }

    private void invokeNative(String methodName) {
        invokeNative(methodName, new Class<?>[0]);
    }

    private void invokeNative(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Object cefBrowser = getNativeBrowser();
            findMethod(cefBrowser.getClass(), methodName, parameterTypes).invoke(cefBrowser, args);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("MCEF browser method failed: " + methodName, exception);
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static int toAwtInputModifiers(int modifiers) {
        int awtModifiers = 0;
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) awtModifiers |= InputEvent.SHIFT_DOWN_MASK;
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) awtModifiers |= InputEvent.CTRL_DOWN_MASK;
        if ((modifiers & GLFW.GLFW_MOD_ALT) != 0) awtModifiers |= InputEvent.ALT_DOWN_MASK;
        if ((modifiers & GLFW.GLFW_MOD_SUPER) != 0) awtModifiers |= InputEvent.META_DOWN_MASK;
        return awtModifiers;
    }

    private static int toAwtKeyCode(int glfwKey) {
        return glfwKey == GLFW.GLFW_KEY_BACKSPACE ? KeyEvent.VK_BACK_SPACE : KeyEvent.VK_ENTER;
    }

    private static char toAwtKeyChar(int glfwKey) {
        return glfwKey == GLFW.GLFW_KEY_BACKSPACE ? '\b' : '\n';
    }
}
