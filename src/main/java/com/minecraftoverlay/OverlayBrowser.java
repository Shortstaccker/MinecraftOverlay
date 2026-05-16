package com.minecraftoverlay;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;

interface OverlayBrowser extends AutoCloseable {
    void resize(int width, int height);

    void setFocus(boolean focused);

    void onMouseClicked(Click event, boolean isDoubleClick);

    void onMouseReleased(Click event);

    void onMouseScrolled(int x, int y, double scrollY);

    void onMouseMoved(int x, int y);

    void onKeyPressed(KeyInput event);

    void onKeyReleased(KeyInput event);

    void onCharTyped(CharInput event);

    AbstractTexture getTexture();

    default GpuTextureView getTextureView() {
        return null;
    }

    default boolean isTextureVerticallyFlipped() {
        return false;
    }

    default String getUrl() {
        return "";
    }

    void reload();

    void goBack();

    void goForward();

    void loadUrl(String url);

    void executeJavaScript(String script, String url, int line);

    void sendNativeKeyEvent(int awtEventId, int glfwKey, int modifiers);

    Object getNativeBrowser();

    @Override
    void close();
}
