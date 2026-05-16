package com.minecraftoverlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MinecraftOverlaySettingsScreen extends Screen {
    private static final double DEFAULT_BROWSER_PIXEL_SCALE = 0.75D;
    private static final int SETTINGS_CONTENT_HEIGHT = 700;
    private static final int KEYBIND_COUNT = 6;

    private final Screen parent;
    private final MinecraftOverlayConfig config;
    private float hue;
    private float saturation;
    private float brightness;
    private float tabHue;
    private float tabSaturation;
    private float tabBrightness;
    private MinecraftOverlayTheme overlayTheme;
    private double browserPixelScale;
    private float tabOpacity;
    private float pinnedTabOpacity;
    private boolean taskbarVertical;
    private float outputVolume;
    private String outputDevice;
    private String timerSound;
    private String spotifyClientId;
    private boolean spotifySearchBarEnabled;
    private boolean compatibilityMode;
    private boolean discordRpcEnabled;
    private String discordApplicationId;
    private ColorMode colorMode = ColorMode.ACCENT;
    private DragTarget dragTarget = DragTarget.NONE;
    private ButtonWidget doneButton;
    private int awaitingKeybind = -1;
    private int pendingComboModifier;
    private boolean outputPickerOpen;
    private boolean themePickerOpen;
    private int scrollOffset;
    private TextFieldWidget discordClientIdField;
    private TextFieldWidget spotifyClientIdField;
    private String status = "";

    public MinecraftOverlaySettingsScreen(Screen parent) {
        super(Text.literal("MinecraftOverlay Settings"));
        this.parent = parent;
        this.config = MinecraftOverlayScreen.getConfig();
        refreshFields();
    }

    private void refreshFields() {
        this.hue = clampHue(config.hue);
        this.saturation = clamp01(config.saturation);
        this.brightness = clamp01(config.brightness);
        this.tabHue = clampHue(config.tabHue);
        this.tabSaturation = clamp01(config.tabSaturation);
        this.tabBrightness = clamp01(config.tabBrightness);
        this.overlayTheme = MinecraftOverlayTheme.fromConfig(config.overlayTheme);
        this.browserPixelScale = clampBrowserPixelScale(config.browserPixelScale);
        this.tabOpacity = clampOpacity(config.tabOpacity);
        this.pinnedTabOpacity = clampOpacity(config.pinnedTabOpacity);
        this.taskbarVertical = config.taskbarVertical;
        this.outputVolume = clamp01(config.outputVolume);
        this.outputDevice = config.outputDevice == null ? "" : config.outputDevice;
        this.timerSound = config.timerSound == null ? "pling" : config.timerSound;
        this.spotifyClientId = config.spotifyClientId == null ? "" : config.spotifyClientId;
        this.spotifySearchBarEnabled = config.spotifySearchBarEnabled;
        this.compatibilityMode = config.compatibilityMode;
        this.discordRpcEnabled = config.discordRpcEnabled;
        this.discordApplicationId = config.discordApplicationId == null ? "" : config.discordApplicationId;

        MinecraftClient minecraftClient = MinecraftClient.getInstance();
        if (minecraftClient != null && minecraftClient.options != null) {
            String selectedDevice = minecraftClient.options.getSoundDevice().getValue();
            if (selectedDevice != null) this.outputDevice = selectedDevice;
        }
    }

    @Override
    protected void init() {
        refreshFields();
        clampScroll();
        rebuild();
    }

    private void rebuild() {
        clearChildren();
        clampScroll();
        int panelX = getPanelX();
        int panelY = getPanelY() - scrollOffset;
        int panelWidth = getPanelWidth();

        addDrawableChild(ButtonWidget.builder(Text.literal(colorMode == ColorMode.ACCENT ? "Accent *" : "Accent"), button -> {
            colorMode = ColorMode.ACCENT;
            rebuild();
        }).dimensions(panelX + 16, panelY + 34, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(colorMode == ColorMode.TAB_BACKGROUND ? "Tab BG *" : "Tab BG"), button -> {
            colorMode = ColorMode.TAB_BACKGROUND;
            rebuild();
        }).dimensions(panelX + 110, panelY + 34, 88, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Theme: " + overlayTheme.label()), button -> toggleThemePicker()).dimensions(panelX + 204, panelY + 34, 132, 20).build());
        if (themePickerOpen) addThemePicker(panelX + 204, panelY + 56);

        int scaleY = panelY + 258;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustBrowserPixelScale(-0.05D)).dimensions(panelX + 16, scaleY, 24, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustBrowserPixelScale(0.05D)).dimensions(panelX + 46, scaleY, 24, 20).build());

        int opacityY = panelY + 306;
        addDrawableChild(ButtonWidget.builder(Text.literal("Window -"), button -> adjustTabOpacity(-0.05F)).dimensions(panelX + 16, opacityY, 74, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Window +"), button -> adjustTabOpacity(0.05F)).dimensions(panelX + 96, opacityY, 74, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pinned -"), button -> adjustPinnedTabOpacity(-0.05F)).dimensions(panelX + 186, opacityY, 74, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pinned +"), button -> adjustPinnedTabOpacity(0.05F)).dimensions(panelX + 266, opacityY, 74, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(taskbarVertical ? "Taskbar: Vertical" : "Taskbar: Bottom"), button -> toggleTaskbarOrientation()).dimensions(panelX + 346, opacityY, 126, 20).build());

        int audioY = panelY + 366;
        addDrawableChild(ButtonWidget.builder(Text.literal("Vol -"), button -> adjustOutputVolume(-0.05F)).dimensions(panelX + 16, audioY, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Vol +"), button -> adjustOutputVolume(0.05F)).dimensions(panelX + 74, audioY, 52, 20).build());
        addDrawableChild(ButtonWidget.builder(buttonText("Speaker " + Math.round(outputVolume * 100.0F) + "%: " + outputDeviceLabel(outputDevice), 230), button -> toggleOutputPicker()).dimensions(panelX + 132, audioY, 230, 20).build());
        int clientY = audioY + 34;
        addDrawableChild(ButtonWidget.builder(Text.literal(compatibilityMode ? "Client: On" : "Client: Off"), button -> toggleCompatibilityMode()).dimensions(panelX + 16, clientY, 104, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export Debug"), button -> exportDebugInfo()).dimensions(panelX + 126, clientY, 102, 20).build());
        if (outputPickerOpen) addOutputDevicePicker(panelX + 132, audioY + 22);

        int timerSoundY = panelY + 456;
        addDrawableChild(ButtonWidget.builder(buttonText("Timer sound: " + MinecraftOverlayTimeTools.timerSoundLabel(timerSound), 190), button -> cycleTimerSound()).dimensions(panelX + 16, timerSoundY, 190, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Test"), button -> previewTimerSound()).dimensions(panelX + 212, timerSoundY, 52, 20).build());

        int spotifyY = panelY + 506;
        spotifyClientIdField = new TextFieldWidget(textRenderer, panelX + 16, spotifyY + 22, 250, 18, Text.literal("Spotify Client ID"));
        spotifyClientIdField.setMaxLength(96);
        spotifyClientIdField.setText(spotifyClientId);
        spotifyClientIdField.setPlaceholder(Text.literal("Spotify Client ID"));
        spotifyClientIdField.setChangedListener(text -> spotifyClientId = text.trim());
        addDrawableChild(spotifyClientIdField);
        addDrawableChild(ButtonWidget.builder(Text.literal(MinecraftOverlaySpotifyControls.isApiConnected() ? "Reconnect" : "Connect"), button -> connectSpotify()).dimensions(panelX + 272, spotifyY + 22, 86, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Create App"), button -> openSpotifyDashboard()).dimensions(panelX + 364, spotifyY + 22, 96, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(spotifySearchBarEnabled ? "Search Bar: On" : "Search Bar: Off"), button -> toggleSpotifySearchBar()).dimensions(panelX + 16, spotifyY + 44, 132, 18).build());

        int discordY = panelY + 614;
        discordClientIdField = new TextFieldWidget(textRenderer, panelX + 16, discordY + 22, 250, 18, Text.literal("Discord Application ID"));
        discordClientIdField.setMaxLength(32);
        discordClientIdField.setText(discordApplicationId);
        discordClientIdField.setPlaceholder(Text.literal("Discord application ID"));
        discordClientIdField.setChangedListener(text -> discordApplicationId = text.trim());
        addDrawableChild(discordClientIdField);
        addDrawableChild(ButtonWidget.builder(Text.literal(discordRpcEnabled ? "RPC: On" : "RPC: Off"), button -> toggleDiscordRpc()).dimensions(panelX + 272, discordY + 22, 86, 18).build());

        int keyX = panelX + panelWidth - 142;
        int keyY = panelY + 74;
        for (int i = 0; i < KEYBIND_COUNT; i++) {
            final int index = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(keybindButtonText(index)), button -> beginKeybindCapture(index)).dimensions(keyX, keyY + i * 28, 118, 20).build());
        }
        doneButton = ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(width / 2 - 50, height - 26, 100, 20).build();
    }

    private Text buttonText(String text, int width) {
        return Text.literal(textRenderer.trimToWidth(text, Math.max(16, width - 10)));
    }

    @Override
    public void close() {
        save();
        if (client != null) client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        clampScroll();
        context.fill(0, 0, width, height, 0xDE0B0C10);
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFFFF);
        int panelX = getPanelX();
        int panelY = getPanelY();
        int contentY = panelY - scrollOffset;
        int panelWidth = getPanelWidth();
        int panelHeight = getVisiblePanelHeight();

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, withAlpha(getTabBackgroundColor(), 229));
        context.fill(panelX, panelY, panelX + panelWidth, panelY + 2, getAccentColor());

        context.enableScissor(panelX, panelY, panelX + panelWidth, panelY + panelHeight);
        context.drawTextWithShadow(textRenderer, "Colors", panelX + 16, contentY + 16, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Keybinds", panelX + panelWidth - 198, contentY + 48, 0xFFFFFFFF);

        int pickerX = panelX + 16;
        int pickerY = contentY + 66;
        int pickerWidth = Math.min(280, panelWidth / 2 - 28);
        int pickerHeight = getPickerHeight();
        drawColorGradient(context, pickerX, pickerY, pickerWidth, pickerHeight);
        int sliderY = pickerY + pickerHeight + 14;
        drawHueSlider(context, pickerX, sliderY, pickerWidth);
        int markerX = pickerX + Math.round(getEditedSaturation() * pickerWidth);
        int markerY = pickerY + Math.round((1.0F - getEditedBrightness()) * pickerHeight);
        context.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
        context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFF000000);
        int hueMarkerX = pickerX + Math.round((getEditedHue() / 360.0F) * pickerWidth);
        context.fill(hueMarkerX - 2, sliderY - 3, hueMarkerX + 3, sliderY + 12, 0xFFFFFFFF);

        int previewY = sliderY + 22;
        int color = getEditedColor();
        context.fill(pickerX, previewY, pickerX + 48, previewY + 18, color);
        context.drawTextWithShadow(textRenderer, colorMode == ColorMode.ACCENT ? "Accent color" : "Tab background", pickerX + 58, previewY + 5, 0xFFDDDDDD);
        context.drawTextWithShadow(textRenderer, String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF), pickerX + pickerWidth - 66, previewY + 5, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Browser resolution " + Math.round(browserPixelScale * 100.0D) + "%", pickerX, contentY + 238, 0xFFDDDDDD);
        context.drawTextWithShadow(textRenderer, "Window opacity " + Math.round(tabOpacity * 100.0F) + "%", pickerX, contentY + 284, 0xFFDDDDDD);
        context.drawTextWithShadow(textRenderer, "Pinned opacity " + Math.round(pinnedTabOpacity * 100.0F) + "%", pickerX + 186, contentY + 284, 0xFFDDDDDD);
        context.drawTextWithShadow(textRenderer, "Audio output", pickerX, contentY + 344, 0xFFFFFFFF);
        String detectedClient = MinecraftOverlay.getDetectedClientName();
        String compatText = detectedClient.isEmpty() ? "Client compatibility: Vanilla/Fabric" : "Client compatibility: " + detectedClient + " detected";
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(compatText, panelWidth - 32), pickerX, contentY + 420, compatibilityMode ? getAccentColor() : 0xFFBBBBBB);
        context.drawTextWithShadow(textRenderer, "Timer alert", pickerX, contentY + 442, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Spotify account", pickerX, contentY + 506, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth("Redirect URI: http://127.0.0.1:8743/spotify/callback", panelWidth - 32), pickerX, contentY + 530, 0xFFBBBBBB);
        context.drawTextWithShadow(textRenderer, "Discord RPC", pickerX, contentY + 614, 0xFFFFFFFF);

        int labelX = panelX + panelWidth - 212;
        int kY = contentY + 78;
        for (int i = 0; i < KEYBIND_COUNT; i++) {
            context.drawTextWithShadow(textRenderer, keybindLabel(i), labelX, kY + i * 28, awaitingKeybind == i ? getAccentColor() : 0xFFDDDDDD);
        }

        super.render(context, mouseX, mouseY, delta);
        context.disableScissor();

        renderScrollBar(context, panelX, panelY, panelWidth, panelHeight);
        if (!status.isEmpty()) context.drawCenteredTextWithShadow(textRenderer, status, width / 2, height - 52, 0xFFBBBBBB);
        if (doneButton != null) doneButton.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click event, boolean isDoubleClick) {
        if (doneButton != null && doneButton.mouseClicked(event, isDoubleClick)) return true;
        dragTarget = getDragTarget(event.x(), event.y());
        if (dragTarget != DragTarget.NONE) {
            updateColorFromMouse(event.x(), event.y());
            return true;
        }
        return super.mouseClicked(event, isDoubleClick);
    }

    @Override
    public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (dragTarget != DragTarget.NONE) {
            updateColorFromMouse(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(Click event) {
        if (dragTarget != DragTarget.NONE) {
            dragTarget = DragTarget.NONE;
            save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) Math.round(scrollY * 24.0D)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (awaitingKeybind >= 0) {
            captureKeybind(event.key());
            return true;
        }
        return super.keyPressed(event);
    }

    private void adjustBrowserPixelScale(double delta) {
        browserPixelScale = clampBrowserPixelScale(browserPixelScale + delta);
        config.browserPixelScale = browserPixelScale;
        status = "Browser resolution: " + Math.round(browserPixelScale * 100.0D) + "%";
        save();
        rebuild();
    }

    private void adjustTabOpacity(float delta) {
        tabOpacity = clampOpacity(tabOpacity + delta);
        config.tabOpacity = tabOpacity;
        status = "Window opacity: " + Math.round(tabOpacity * 100.0F) + "%.";
        save();
        rebuild();
    }

    private void adjustPinnedTabOpacity(float delta) {
        pinnedTabOpacity = clampOpacity(pinnedTabOpacity + delta);
        config.pinnedTabOpacity = pinnedTabOpacity;
        status = "Pinned opacity: " + Math.round(pinnedTabOpacity * 100.0F) + "%.";
        save();
        rebuild();
    }

    private void toggleTaskbarOrientation() {
        taskbarVertical = !taskbarVertical;
        config.taskbarVertical = taskbarVertical;
        status = taskbarVertical ? "Taskbar moved to the left." : "Taskbar moved to the bottom.";
        save();
        rebuild();
    }

    private void adjustOutputVolume(float delta) {
        outputVolume = clamp01(outputVolume + delta);
        config.outputVolume = outputVolume;
        applyMinecraftOutputSettings();
        status = "Minecraft output volume: " + Math.round(outputVolume * 100.0F) + "%.";
        save();
        rebuild();
    }

    private void toggleOutputPicker() {
        outputPickerOpen = !outputPickerOpen;
        rebuild();
    }

    private void cycleTimerSound() {
        timerSound = MinecraftOverlayTimeTools.nextTimerSound(timerSound);
        config.timerSound = timerSound;
        status = "Timer sound: " + MinecraftOverlayTimeTools.timerSoundLabel(timerSound) + ".";
        MinecraftOverlayTimeTools.previewTimerSound(timerSound);
        save();
        rebuild();
    }

    private void previewTimerSound() {
        MinecraftOverlayTimeTools.previewTimerSound(timerSound);
        status = "Previewed timer sound.";
    }

    private void connectSpotify() {
        spotifyClientId = spotifyClientIdField == null ? spotifyClientId : spotifyClientIdField.getText().trim();
        config.spotifyClientId = spotifyClientId;
        config.save();
        status = MinecraftOverlaySpotifyControls.startAuthorization(spotifyClientId);
        rebuild();
    }

    private void openSpotifyDashboard() {
        status = MinecraftOverlaySpotifyControls.openDeveloperDashboard();
    }

    private void toggleSpotifySearchBar() {
        spotifySearchBarEnabled = !spotifySearchBarEnabled;
        config.spotifySearchBarEnabled = spotifySearchBarEnabled;
        status = spotifySearchBarEnabled ? "Spotify search bar enabled." : "Spotify search bar disabled.";
        save();
        rebuild();
    }

    private void toggleThemePicker() {
        themePickerOpen = !themePickerOpen;
        outputPickerOpen = false;
        rebuild();
    }

    private void selectTheme(MinecraftOverlayTheme theme) {
        overlayTheme = theme;
        config.overlayTheme = theme.name();
        if (!theme.isCustom()) {
            theme.apply(config);
            refreshFields();
        }
        status = "Theme: " + theme.label() + ".";
        themePickerOpen = false;
        save();
        rebuild();
    }

    private void toggleDiscordRpc() {
        discordRpcEnabled = !discordRpcEnabled;
        config.discordRpcEnabled = discordRpcEnabled;
        status = discordRpcEnabled ? "Discord RPC enabled." : "Discord RPC disabled.";
        save();
        MinecraftOverlayDiscordRpc.refresh(client);
        rebuild();
    }

    private void selectOutputDevice(String device) {
        outputDevice = device;
        config.outputDevice = device;
        outputPickerOpen = false;
        applyMinecraftOutputSettings();
        status = outputDevice.isBlank() ? "Speaker output: Minecraft default." : "Speaker output: " + outputDeviceLabel(outputDevice);
        save();
        rebuild();
    }

    private void exportDebugInfo() {
        MinecraftOverlay.LOGGER.info("=== MinecraftOverlay Browser Debug Info ===");
        MinecraftOverlay.LOGGER.info("Client Name: {}", MinecraftOverlay.getDetectedClientName());
        MinecraftOverlay.LOGGER.info("Third Party Client: {}", MinecraftOverlay.isThirdPartyClientDetected());
        MinecraftOverlay.LOGGER.info("Essential Detected: {}", MinecraftOverlay.isEssentialDetected());
        MinecraftOverlay.LOGGER.info("Compatibility Mode: {}", compatibilityMode);
        MinecraftOverlay.LOGGER.info("OS: {} ({})", System.getProperty("os.name"), System.getProperty("os.arch"));
        MinecraftOverlay.LOGGER.info("Java: {} ({})", System.getProperty("java.version"), System.getProperty("java.vendor"));
        MinecraftOverlay.LOGGER.info("Library Path: {}", System.getProperty("java.library.path"));
        
        try {
            Class.forName("net.digitalingot.fcef.CefApp", false, MinecraftOverlayScreen.class.getClassLoader());
            MinecraftOverlay.LOGGER.info("Feather CEF: Available (Mod Loader)");
        } catch (Throwable e) {
            MinecraftOverlay.LOGGER.info("Feather CEF: NOT Available (Mod Loader)");
        }
        
        try {
            Class.forName("net.digitalingot.fcef.CefApp", false, Thread.currentThread().getContextClassLoader());
            MinecraftOverlay.LOGGER.info("Feather CEF: Available (Context Loader)");
        } catch (Throwable e) {
            MinecraftOverlay.LOGGER.info("Feather CEF: NOT Available (Context Loader)");
        }
        
        try {
            Class.forName("net.dimaskama.mcef.api.MCEFApi", false, MinecraftOverlayScreen.class.getClassLoader());
            MinecraftOverlay.LOGGER.info("MCEF Modern: Available");
        } catch (Throwable e) {
            MinecraftOverlay.LOGGER.info("MCEF Modern: NOT Available");
        }
        
        MinecraftOverlay.LOGGER.info("=== End of Debug Info ===");
        status = "Debug info exported to log.";
        rebuild();
    }

    private void toggleCompatibilityMode() {
        compatibilityMode = !compatibilityMode;
        config.compatibilityMode = compatibilityMode;
        status = compatibilityMode ? "Compatibility mode enabled." : "Compatibility mode disabled.";
        save();
        rebuild();
    }

    private void beginKeybindCapture(int index) {
        awaitingKeybind = index;
        pendingComboModifier = 0;
        status = "Press one key or a modifier then another key for " + keybindLabel(index) + ".";
        rebuild();
    }

    private void captureKeybind(int key) {
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            setKeybind(awaitingKeybind, -1, 0);
            return;
        }
        if (pendingComboModifier == 0 && isModifierKey(key)) {
            pendingComboModifier = key;
            status = "Now press the second key for " + keyName(key) + " + ...";
            return;
        }
        if (pendingComboModifier != 0) setKeybind(awaitingKeybind, pendingComboModifier, key);
        else setKeybind(awaitingKeybind, key, 0);
    }

    private void setKeybind(int index, int key, int secondKey) {
        switch (index) {
            case 1 -> {
                config.spotifyPreviousKey = key;
                config.spotifyPreviousKeySecond = secondKey;
            }
            case 2 -> {
                config.spotifyPlayPauseKey = key;
                config.spotifyPlayPauseKeySecond = secondKey;
            }
            case 3 -> {
                config.spotifyNextKey = key;
                config.spotifyNextKeySecond = secondKey;
            }
            case 4 -> {
                config.timePauseKey = key;
                config.timePauseKeySecond = secondKey;
            }
            case 5 -> {
                config.timeResetKey = key;
                config.timeResetKeySecond = secondKey;
            }
            default -> {
                config.openOverlayKey = key;
                config.openOverlayKeySecond = secondKey;
            }
        }
        awaitingKeybind = -1;
        pendingComboModifier = 0;
        status = keybindLabel(index) + " key set to " + keyComboName(key, secondKey) + ".";
        save();
        rebuild();
    }

    private int getKeybind(int index) {
        return switch (index) {
            case 1 -> config.spotifyPreviousKey;
            case 2 -> config.spotifyPlayPauseKey;
            case 3 -> config.spotifyNextKey;
            case 4 -> config.timePauseKey;
            case 5 -> config.timeResetKey;
            default -> config.openOverlayKey;
        };
    }

    private int getSecondKeybind(int index) {
        return switch (index) {
            case 1 -> config.spotifyPreviousKeySecond;
            case 2 -> config.spotifyPlayPauseKeySecond;
            case 3 -> config.spotifyNextKeySecond;
            case 4 -> config.timePauseKeySecond;
            case 5 -> config.timeResetKeySecond;
            default -> config.openOverlayKeySecond;
        };
    }

    private String keybindButtonText(int index) {
        if (awaitingKeybind == index) return pendingComboModifier == 0 ? "Press..." : keyName(pendingComboModifier) + " + ...";
        return keyComboName(getKeybind(index), getSecondKeybind(index));
    }

    private static String keybindLabel(int index) {
        return switch (index) {
            case 1 -> "Spotify prev";
            case 2 -> "Spotify play";
            case 3 -> "Spotify next";
            case 4 -> "Time pause";
            case 5 -> "Time reset";
            default -> "Overlay";
        };
    }

    private void save() {
        config.hue = hue;
        config.saturation = saturation;
        config.brightness = brightness;
        config.tabHue = tabHue;
        config.tabSaturation = tabSaturation;
        config.tabBrightness = tabBrightness;
        config.overlayTheme = overlayTheme == null ? MinecraftOverlayTheme.CUSTOM.name() : overlayTheme.name();
        config.browserPixelScale = browserPixelScale;
        config.tabOpacity = tabOpacity;
        config.pinnedTabOpacity = pinnedTabOpacity;
        config.taskbarVertical = taskbarVertical;
        config.compatibilityMode = compatibilityMode;
        config.outputVolume = outputVolume;
        config.outputDevice = outputDevice;
        config.timerSound = timerSound;
        config.spotifyClientId = spotifyClientIdField == null ? spotifyClientId : spotifyClientIdField.getText().trim();
        config.spotifySearchBarEnabled = spotifySearchBarEnabled;
        config.discordRpcEnabled = discordRpcEnabled;
        config.discordApplicationId = discordClientIdField == null ? discordApplicationId : discordClientIdField.getText().trim();
        config.appearanceVisible = false;
        config.appearancePinned = false;
        applyMinecraftOutputSettings();
        MinecraftOverlayScreen.updateSharedHudStateFromConfig();
        MinecraftOverlayDiscordRpc.refresh(client);
        config.save();
    }

    private void applyMinecraftOutputSettings() {
        MinecraftClient minecraftClient = client == null ? MinecraftClient.getInstance() : client;
        if (minecraftClient == null || minecraftClient.options == null || minecraftClient.getSoundManager() == null) return;

        float clampedVolume = clamp01(outputVolume);
        minecraftClient.options.getSoundVolumeOption(SoundCategory.MASTER).setValue((double) clampedVolume);
        minecraftClient.getSoundManager().setVolume(SoundCategory.MASTER, clampedVolume);
        if (outputDevice != null) {
            minecraftClient.options.getSoundDevice().setValue(outputDevice);
        }
        minecraftClient.options.write();
    }

    private DragTarget getDragTarget(double mouseX, double mouseY) {
        int x = getPanelX() + 16;
        int y = getPanelY() - scrollOffset + 66;
        int pickerWidth = Math.min(280, getPanelWidth() / 2 - 28);
        int pickerHeight = getPickerHeight();
        int sliderY = y + pickerHeight + 14;
        if (mouseX >= x && mouseX <= x + pickerWidth && mouseY >= y && mouseY <= y + pickerHeight) return DragTarget.GRADIENT;
        if (mouseX >= x && mouseX <= x + pickerWidth && mouseY >= sliderY - 4 && mouseY <= sliderY + 14) return DragTarget.HUE;
        return DragTarget.NONE;
    }

    private void updateColorFromMouse(double mouseX, double mouseY) {
        overlayTheme = MinecraftOverlayTheme.CUSTOM;
        int x = getPanelX() + 16;
        int y = getPanelY() - scrollOffset + 66;
        int pickerWidth = Math.min(280, getPanelWidth() / 2 - 28);
        int pickerHeight = getPickerHeight();
        int sliderY = y + pickerHeight + 14;
        if (dragTarget == DragTarget.GRADIENT) {
            if (colorMode == ColorMode.ACCENT) {
                saturation = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth)));
                brightness = clamp01(1.0F - (float) ((mouseY - y) / Math.max(1, pickerHeight)));
            } else {
                tabSaturation = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth)));
                tabBrightness = clamp01(1.0F - (float) ((mouseY - y) / Math.max(1, pickerHeight)));
            }
        } else if (dragTarget == DragTarget.HUE) {
            if (colorMode == ColorMode.ACCENT) hue = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth))) * 360.0F;
            else tabHue = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth))) * 360.0F;
        }
    }

    private void drawColorGradient(DrawContext context, int x, int y, int width, int height) {
        int cell = 4;
        for (int row = 0; row < height; row += cell) {
            float b = 1.0F - (float) row / Math.max(1, height);
            for (int col = 0; col < width; col += cell) {
                float s = (float) col / Math.max(1, width);
                context.fill(x + col, y + row, x + Math.min(width, col + cell), y + Math.min(height, row + cell), hsbToRgb(getEditedHue(), s, b));
            }
        }
    }

    private void drawHueSlider(DrawContext context, int x, int y, int width) {
        int cell = 3;
        for (int col = 0; col < width; col += cell) {
            float sliderHue = ((float) col / Math.max(1, width)) * 360.0F;
            context.fill(x + col, y, x + Math.min(width, col + cell), y + 9, hsbToRgb(sliderHue, 1.0F, 1.0F));
        }
    }

    private int getPanelWidth() {
        return Math.min(640, Math.max(360, width - 44));
    }

    private int getPanelX() {
        return width / 2 - getPanelWidth() / 2;
    }

    private int getPanelY() {
        return 38;
    }

    private int getVisiblePanelHeight() {
        return Math.max(132, Math.min(460, height - getPanelY() - 38));
    }

    private int getPickerHeight() {
        return height < 520 ? 80 : 104;
    }

    private int getMaxScroll() {
        return Math.max(0, SETTINGS_CONTENT_HEIGHT - getVisiblePanelHeight());
    }

    private void clampScroll() {
        scrollOffset = Math.max(0, Math.min(scrollOffset, getMaxScroll()));
    }

    private void renderScrollBar(DrawContext context, int panelX, int panelY, int panelWidth, int panelHeight) {
        int maxScroll = getMaxScroll();
        if (maxScroll <= 0) return;
        int trackX = panelX + panelWidth - 5;
        int trackY = panelY + 8;
        int trackHeight = Math.max(24, panelHeight - 16);
        int thumbHeight = Math.max(20, trackHeight * panelHeight / SETTINGS_CONTENT_HEIGHT);
        int thumbY = trackY + (trackHeight - thumbHeight) * scrollOffset / maxScroll;
        context.fill(trackX, trackY, trackX + 2, trackY + trackHeight, 0x40000000);
        context.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, withAlpha(getAccentColor(), 190));
    }

    private float getEditedHue() {
        return colorMode == ColorMode.ACCENT ? hue : tabHue;
    }

    private float getEditedSaturation() {
        return colorMode == ColorMode.ACCENT ? saturation : tabSaturation;
    }

    private float getEditedBrightness() {
        return colorMode == ColorMode.ACCENT ? brightness : tabBrightness;
    }

    private int getEditedColor() {
        return hsbToRgb(getEditedHue(), getEditedSaturation(), getEditedBrightness());
    }

    private int getAccentColor() {
        return hsbToRgb(hue, saturation, brightness);
    }

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        return 0xFF000000 | (Color.HSBtoRGB(clampHue(hue) / 360.0F, clamp01(saturation), clamp01(brightness)) & 0xFFFFFF);
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clampHue(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 195.0F;
        float clamped = value % 360.0F;
        return clamped < 0.0F ? clamped + 360.0F : clamped;
    }

    private static double clampBrowserPixelScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return DEFAULT_BROWSER_PIXEL_SCALE;
        return Math.max(0.35D, Math.min(1.0D, value));
    }

    private static float clampOpacity(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return 0.9F;
        return Math.max(0.1F, Math.min(1.0F, value));
    }

    private int getTabBackgroundColor() {
        return hsbToRgb(tabHue, tabSaturation, tabBrightness);
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    private static String keyName(int key) {
        if (key <= 0) return "Unbound";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank()) return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_SHIFT -> "LShift";
            case GLFW.GLFW_KEY_RIGHT_SHIFT -> "RShift";
            case GLFW.GLFW_KEY_LEFT_CONTROL -> "LCtrl";
            case GLFW.GLFW_KEY_RIGHT_CONTROL -> "RCtrl";
            case GLFW.GLFW_KEY_LEFT_ALT -> "LAlt";
            case GLFW.GLFW_KEY_RIGHT_ALT -> "RAlt";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> "Key " + key;
        };
    }

    private static String keyComboName(int key, int secondKey) {
        if (key <= 0) return "Unbound";
        return secondKey <= 0 ? keyName(key) : keyName(key) + " + " + keyName(secondKey);
    }

    private static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private String[] listOutputDevices() {
        List<String> devices = new ArrayList<>();
        devices.add("");
        MinecraftClient minecraftClient = client == null ? MinecraftClient.getInstance() : client;
        if (minecraftClient != null && minecraftClient.getSoundManager() != null) {
            for (String device : minecraftClient.getSoundManager().getSoundDevices()) {
                if (!devices.contains(device)) devices.add(device);
            }
        }
        return devices.toArray(String[]::new);
    }

    private void addOutputDevicePicker(int x, int y) {
        String[] devices = listOutputDevices();
        int count = Math.min(5, devices.length);
        int pickerWidth = Math.min(340, getPanelWidth() - (x - getPanelX()) - 16);
        for (int i = 0; i < count; i++) {
            String device = devices[i];
            int rowY = y + i * 18;
            addDrawableChild(ButtonWidget.builder(Text.literal(outputDeviceLabel(device)), button -> selectOutputDevice(device)).dimensions(x, rowY, pickerWidth, 18).build());
        }
    }

    private static String outputDeviceLabel(String device) {
        if (device == null || device.isBlank()) return "Default";
        String prefix = "OpenAL Soft on ";
        return device.startsWith(prefix) ? device.substring(prefix.length()) : device;
    }

    private void addThemePicker(int x, int y) {
        int pickerWidth = 132;
        MinecraftOverlayTheme[] themes = MinecraftOverlayTheme.values();
        for (int i = 0; i < themes.length; i++) {
            MinecraftOverlayTheme theme = themes[i];
            int rowY = y + i * 18;
            addDrawableChild(ButtonWidget.builder(Text.literal(theme.label()), button -> selectTheme(theme)).dimensions(x, rowY, pickerWidth, 18).build());
        }
    }

    private enum DragTarget {
        NONE,
        GRADIENT,
        HUE
    }

    private enum ColorMode {
        ACCENT,
        TAB_BACKGROUND
    }
}
