package com.minecraftoverlay;

import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.dimaskama.mcef.api.MCEFApi;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gl.GpuSampler;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.KeyEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public class MinecraftOverlayScreen extends Screen {
    private static final int TOOLBAR_HEIGHT = 0;
    private static final int SIDE_RAIL_WIDTH = 58;
    private static final int STATUS_HEIGHT = 14;
    private static final int HEADER_HEIGHT = 14;
    private static final int RESIZE_HANDLE = 8;
    private static final int MIN_BROWSER_WIDTH = 120;
    private static final int MIN_BROWSER_HEIGHT = 80;
    private static final int MIN_SCREENSHOTS_WIDTH = 120;
    private static final int MIN_SCREENSHOTS_HEIGHT = 80;
    private static final int MIN_FRIENDS_WIDTH = 120;
    private static final int MIN_FRIENDS_HEIGHT = 80;
    private static final int MIN_NOTES_WIDTH = 120;
    private static final int MIN_NOTES_HEIGHT = 80;
    private static final int MIN_CALCULATOR_WIDTH = 180;
    private static final int MIN_CALCULATOR_HEIGHT = 150;
    private static final int MIN_TIME_WIDTH = 180;
    private static final int MIN_TIME_HEIGHT = 135;
    private static final int MIN_SPOTIFY_WIDTH = 180;
    private static final int MIN_SPOTIFY_HEIGHT = 115;
    private static final int MIN_APPEARANCE_WIDTH = 260;
    private static final int MIN_APPEARANCE_HEIGHT = 220;
    private static final double DEFAULT_BROWSER_PIXEL_SCALE = 0.75D;
    private static final boolean RENDER_BROWSER_TEXTURE = true;
    private static Method drawTexturedQuadMethod;
    private static final Identifier SAMPLER_SOURCE = Identifier.of(MinecraftOverlay.MOD_ID, "browser_sampler");
    private static boolean samplerSourceRegistered;
    private static final Tab[] WINDOW_ORDER = {
            Tab.BROWSER,
            Tab.SCREENSHOTS,
            Tab.FRIENDS,
            Tab.NOTES,
            Tab.CALCULATOR,
            Tab.TIME,
            Tab.SPOTIFY,
            Tab.APPEARANCE
    };
    private static final List<BrowserTabState> SHARED_BROWSER_TABS = new ArrayList<>();
    private static int sharedActiveBrowserTab;
    private static boolean loadedSharedBrowserTabs;
    private static int nextBrowserTextureId;
    private static final List<NoteState> SHARED_NOTES = new ArrayList<>();
    private static boolean loadedSharedNotes;
    private static int sharedActiveNoteIndex;
    private static boolean loadedSharedHudConfig;
    private static boolean sharedBrowserPinned;
    private static boolean sharedScreenshotsPinned;
    private static boolean sharedFriendsPinned;
    private static boolean sharedNotesPinned;
    private static boolean sharedCalculatorPinned;
    private static boolean sharedTimePinned;
    private static boolean sharedSpotifyPinned;
    private static boolean sharedAppearancePinned;
    private static float sharedHue = 195.0F;
    private static float sharedSaturation = 0.75F;
    private static float sharedBrightness = 1.0F;
    private static float sharedTabHue = 220.0F;
    private static float sharedTabSaturation = 0.12F;
    private static float sharedTabBrightness = 0.18F;
    private static float sharedTabOpacity = 0.90F;
    private static float sharedPinnedTabOpacity = 0.65F;
    private static MinecraftOverlayConfig.WindowState sharedBrowserWindow = new MinecraftOverlayConfig.WindowState(16,
            74, 520, 320, true);
    private static MinecraftOverlayConfig.WindowState sharedScreenshotsWindow = new MinecraftOverlayConfig.WindowState(
            548, 74, 396, 220, true);
    private static MinecraftOverlayConfig.WindowState sharedFriendsWindow = new MinecraftOverlayConfig.WindowState(548,
            306, 396, 184, true);
    private static MinecraftOverlayConfig.WindowState sharedNotesWindow = new MinecraftOverlayConfig.WindowState(548,
            502, 396, 180, true);
    private static MinecraftOverlayConfig.WindowState sharedCalculatorWindow = new MinecraftOverlayConfig.WindowState(
            548, 502, 320, 252, true);
    private static MinecraftOverlayConfig.WindowState sharedTimeWindow = new MinecraftOverlayConfig.WindowState(548,
            502, 320, 180, true);
    private static MinecraftOverlayConfig.WindowState sharedSpotifyWindow = new MinecraftOverlayConfig.WindowState(548,
            502, 340, 178, true);
    private static MinecraftOverlayConfig.WindowState sharedAppearanceWindow = new MinecraftOverlayConfig.WindowState(
            548, 502, 396, 220, true);
    private static float sharedNotesOpacity = 1.0F;
    private static boolean sharedNotesBold = false;
    private static boolean sharedNotesItalic = false;
    private static String sharedNotesText = "";

    private final Screen parent;
    private static MinecraftOverlayConfig config;

    public static MinecraftOverlayConfig getConfig() {
        if (config == null)
            config = MinecraftOverlayConfig.load();
        return config;
    }

    private Tab focusedTab = Tab.BROWSER;
    private boolean browserVisible = true;
    private boolean screenshotsVisible = true;
    private boolean friendsVisible = true;
    private boolean notesVisible = true;
    private boolean calculatorVisible = true;
    private boolean timeVisible = true;
    private boolean spotifyVisible = true;
    private boolean appearanceVisible = true;
    private boolean browserPinned;
    private boolean screenshotsPinned;
    private boolean friendsPinned;
    private boolean notesPinned;
    private boolean calculatorPinned;
    private boolean timePinned;
    private boolean spotifyPinned;
    private boolean appearancePinned;
    private boolean useImageForTabs;

    private final OverlayWindow browserWindow = new OverlayWindow(16, 74, 520, 320, MIN_BROWSER_WIDTH,
            MIN_BROWSER_HEIGHT);
    private final OverlayWindow screenshotsWindow = new OverlayWindow(548, 74, 396, 220, MIN_SCREENSHOTS_WIDTH,
            MIN_SCREENSHOTS_HEIGHT);
    private final OverlayWindow friendsWindow = new OverlayWindow(548, 306, 396, 184, MIN_FRIENDS_WIDTH,
            MIN_FRIENDS_HEIGHT);
    private final OverlayWindow calculatorWindow = new OverlayWindow(548, 502, 320, 252, MIN_CALCULATOR_WIDTH,
            MIN_CALCULATOR_HEIGHT);
    private final OverlayWindow timeWindow = new OverlayWindow(548, 502, 320, 180, MIN_TIME_WIDTH, MIN_TIME_HEIGHT);
    private final OverlayWindow spotifyWindow = new OverlayWindow(548, 502, 360, 220, MIN_SPOTIFY_WIDTH,
            MIN_SPOTIFY_HEIGHT);
    private final OverlayWindow appearanceWindow = new OverlayWindow(548, 502, 396, 220, MIN_APPEARANCE_WIDTH,
            MIN_APPEARANCE_HEIGHT);

    private TextFieldWidget urlField;
    private TextFieldWidget friendField;
    private TextFieldWidget messageField;
    private TextFieldWidget calculatorField;
    private TextFieldWidget spotifySearchField;
    private int spotifySearchVersion = -1;

    private static MCEFApi.Initialization browserInitialization;
    private final List<BrowserTabState> browserTabs = SHARED_BROWSER_TABS;
    private static boolean browserRequested;
    private static boolean browserFailed;
    private static boolean browserCreating;
    private static boolean browserNeedsRestartAfterMcefReset;
    private static boolean forceMcefBrowserBackend;
    private static boolean mcefNativeResetAttempted;
    private boolean browserKeyboardFocused;
    private boolean browserNativeKeyWarningLogged;
    private static String browserError = "";
    private int blockedAdRequests;
    private boolean browserBackspaceHeld;
    private int browserBackspaceRepeatTicks;
    private int browserBackspaceModifiers;
    private int awaitingKeybind = -1;
    private int pendingComboModifier;

    private List<Path> screenshots = List.of();
    private final Map<Path, Identifier> screenshotTextures = new HashMap<>();
    private Path selectedScreenshot;
    private BufferedImage editedScreenshot;
    private Identifier editedScreenshotTexture;
    private boolean screenshotDirty;
    private DragTarget dragTarget = DragTarget.NONE;
    private ColorDragTarget colorDragTarget = ColorDragTarget.NONE;
    private boolean resizingLeft;
    private boolean resizingRight;
    private boolean resizingTop;
    private boolean resizingBottom;
    private boolean compatibilityMode;
    private String status = "";
    private float hue = 195.0F;
    private float saturation = 0.75F;
    private float brightness = 1.0F;
    private float tabHue = 220.0F;
    private float tabSaturation = 0.12F;
    private float tabBrightness = 0.18F;
    private float tabOpacity = 0.90F;
    private float pinnedTabOpacity = 0.65F;
    private boolean taskbarVertical;
    private double browserPixelScale = DEFAULT_BROWSER_PIXEL_SCALE;
    private ColorMode colorMode = ColorMode.ACCENT;
    private int openOverlayKey = GLFW.GLFW_KEY_O;
    private int openOverlayKeySecond;
    private String notesText = "";
    private final List<NoteState> notes = SHARED_NOTES;
    private int activeNoteIndex = sharedActiveNoteIndex;
    private int draggedNoteIndex = -1;
    private final List<String> essentialMessageHistory = new ArrayList<>();
    private String calculatorExpression = "";
    private String calculatorResult = "0";
    private final long openedAtNanos = System.nanoTime();

    public MinecraftOverlayScreen(Screen parent) {
        this(parent, Tab.BROWSER);
    }

    public MinecraftOverlayScreen(Screen parent, Tab requestedTab) {
        super(Text.literal("MinecraftOverlay"));
        this.parent = parent;
        this.config = getConfig();
        loadSettings(requestedTab);
    }

    private void loadSettings(Tab requestedTab) {
        browserVisible = config.browserVisible || requestedTab == Tab.BROWSER;
        screenshotsVisible = config.screenshotsVisible || requestedTab == Tab.SCREENSHOTS;
        friendsVisible = config.friendsVisible || requestedTab == Tab.FRIENDS;
        notesVisible = config.notesVisible || requestedTab == Tab.NOTES;
        calculatorVisible = config.calculatorVisible || requestedTab == Tab.CALCULATOR;
        timeVisible = config.timeVisible || requestedTab == Tab.TIME;
        spotifyVisible = config.spotifyVisible || requestedTab == Tab.SPOTIFY;
        appearanceVisible = false;
        browserPinned = config.browserPinned;
        screenshotsPinned = config.screenshotsPinned;
        friendsPinned = config.friendsPinned;
        notesPinned = config.notesPinned;
        calculatorPinned = config.calculatorPinned;
        timePinned = config.timePinned;
        spotifyPinned = config.spotifyPinned;
        appearancePinned = false;
        hue = clampHue(config.hue);
        saturation = clamp01(config.saturation);
        brightness = clamp01(config.brightness);
        tabHue = clampHue(config.tabHue);
        tabSaturation = clamp01(config.tabSaturation);
        tabBrightness = clamp01(config.tabBrightness);
        tabOpacity = clampOpacity(config.tabOpacity);
        pinnedTabOpacity = clampOpacity(config.pinnedTabOpacity);
        taskbarVertical = config.taskbarVertical;
        browserPixelScale = clampBrowserPixelScale(config.browserPixelScale);
        compatibilityMode = config.compatibilityMode;
        openOverlayKey = config.openOverlayKey;
        openOverlayKeySecond = config.openOverlayKeySecond;
        MinecraftOverlaySpotifyControls.configureSpotifyApi(config.spotifyClientId, config.spotifyAccessToken,
                config.spotifyRefreshToken, config.spotifyTokenExpiresAtMillis);
        calculatorExpression = config.calculatorExpression == null ? "" : config.calculatorExpression;
        calculatorResult = config.calculatorResult == null || config.calculatorResult.isBlank() ? "0"
                : config.calculatorResult;
        loadNotesFromConfig();
        essentialMessageHistory.clear();
        if (config.essentialMessageHistory != null) {
            for (String entry : config.essentialMessageHistory) {
                if (entry != null && !entry.isBlank())
                    essentialMessageHistory.add(entry);
            }
        }
        applyWindowState(browserWindow, config.browserWindow);
        applyWindowState(screenshotsWindow, config.screenshotsWindow);
        applyWindowState(friendsWindow, config.friendsWindow);
        applyWindowState(calculatorWindow, config.calculatorWindow);
        applyWindowState(timeWindow, config.timeWindow);
        applyWindowState(spotifyWindow, config.spotifyWindow);
        applyWindowState(appearanceWindow, config.appearanceWindow);
        focusedTab = requestedTab;
        if (browserVisible && !browserFailed && browserInitialization == null) {
            browserRequested = browserRequested || !MinecraftOverlay.isThirdPartyClientDetected();
        }
        updateSharedHudState();
    }

    private void saveSettings() {
        ensureConfigWindowStates();
        config.browserVisible = browserVisible;
        config.screenshotsVisible = screenshotsVisible;
        config.friendsVisible = friendsVisible;
        config.notesVisible = notesVisible;
        config.calculatorVisible = calculatorVisible;
        config.timeVisible = timeVisible;
        config.spotifyVisible = spotifyVisible;
        config.appearanceVisible = false;
        config.browserPinned = browserPinned;
        config.screenshotsPinned = screenshotsPinned;
        config.friendsPinned = friendsPinned;
        config.notesPinned = notesPinned;
        config.calculatorPinned = calculatorPinned;
        config.timePinned = timePinned;
        config.spotifyPinned = spotifyPinned;
        config.appearancePinned = false;
        saveBrowserTabsToConfig();
        config.hue = hue;
        config.saturation = saturation;
        config.brightness = brightness;
        config.tabHue = tabHue;
        config.tabSaturation = tabSaturation;
        config.tabBrightness = tabBrightness;
        config.tabOpacity = tabOpacity;
        config.pinnedTabOpacity = pinnedTabOpacity;
        config.taskbarVertical = taskbarVertical;
        config.browserPixelScale = browserPixelScale;
        config.compatibilityMode = compatibilityMode;
        config.openOverlayKey = openOverlayKey;
        config.openOverlayKeySecond = openOverlayKeySecond;
        config.calculatorExpression = calculatorExpression;
        config.calculatorResult = calculatorResult;
        config.activeNoteIndex = Math.max(0, Math.min(activeNoteIndex, notes.size() - 1));
        sharedActiveNoteIndex = config.activeNoteIndex;
        config.notes = notes.stream()
                .map(note -> {
                    MinecraftOverlayConfig.Note cn = new MinecraftOverlayConfig.Note(note.title, note.text,
                            note.textSize, note.bold, note.italic, note.opacity);
                    cn.window = copyWindowState(note.window);
                    return cn;
                })
                .toArray(MinecraftOverlayConfig.Note[]::new);
        config.notesText = getActiveNote().text;
        config.essentialMessageHistory = essentialMessageHistory.toArray(String[]::new);
        copyWindowState(browserWindow, config.browserWindow);
        copyWindowState(screenshotsWindow, config.screenshotsWindow);
        copyWindowState(friendsWindow, config.friendsWindow);
        copyWindowState(calculatorWindow, config.calculatorWindow);
        copyWindowState(timeWindow, config.timeWindow);
        copyWindowState(spotifyWindow, config.spotifyWindow);
        copyWindowState(appearanceWindow, config.appearanceWindow);
        updateSharedHudState();
        config.save();
    }

    private void updateSharedHudState() {
        sharedBrowserPinned = browserPinned;
        sharedScreenshotsPinned = screenshotsPinned;
        sharedFriendsPinned = friendsPinned;
        sharedNotesPinned = notesPinned;
        sharedCalculatorPinned = calculatorPinned;
        sharedTimePinned = timePinned;
        sharedSpotifyPinned = spotifyPinned;
        sharedAppearancePinned = false;
        sharedHue = hue;
        sharedSaturation = saturation;
        sharedBrightness = brightness;
        sharedTabHue = tabHue;
        sharedTabSaturation = tabSaturation;
        sharedTabBrightness = tabBrightness;
        sharedTabOpacity = tabOpacity;
        sharedPinnedTabOpacity = pinnedTabOpacity;
        sharedBrowserWindow = copyWindowState(browserWindow);
        sharedScreenshotsWindow = copyWindowState(screenshotsWindow);
        sharedFriendsWindow = copyWindowState(friendsWindow);
        sharedCalculatorWindow = copyWindowState(calculatorWindow);
        sharedTimeWindow = copyWindowState(timeWindow);
        sharedSpotifyWindow = copyWindowState(spotifyWindow);
        sharedAppearanceWindow = copyWindowState(appearanceWindow);
        if (notes != null && !notes.isEmpty()) {
            NoteState note = getActiveNote();
            if (note != null) {
                sharedNotesOpacity = note.opacity;
                sharedNotesBold = note.bold;
                sharedNotesItalic = note.italic;
                sharedNotesText = note.text;
            }
        }
        loadedSharedHudConfig = true;
    }

    private void saveBrowserTabsToConfig() {
        config.activeBrowserTab = Math.max(0, Math.min(sharedActiveBrowserTab, browserTabs.size() - 1));
        config.browserTabs = browserTabs.stream()
                .map(tab -> new MinecraftOverlayConfig.BrowserTab(tab.url, tab.pinned))
                .toArray(MinecraftOverlayConfig.BrowserTab[]::new);
    }

    private void loadNotesFromConfig() {
        if (loadedSharedNotes) {
            activeNoteIndex = sharedActiveNoteIndex;
            notesText = getActiveNote().text;
            return;
        }
        notes.clear();
        if (config.notes != null) {
            for (MinecraftOverlayConfig.Note note : config.notes) {
                if (note != null) {
                    notes.add(new NoteState(
                            note.title == null || note.title.isBlank() ? "Note " + (notes.size() + 1) : note.title,
                            note.text == null ? "" : note.text,
                            Math.max(1, Math.min(3, note.textSize)),
                            note.bold,
                            note.italic,
                            Math.max(0.1F, Math.min(1.0F, note.opacity)),
                            note.window != null ? note.window.x : 548,
                            note.window != null ? note.window.y : 502,
                            note.window != null ? note.window.width : 396,
                            note.window != null ? note.window.height : 180));
                    if (note.window != null)
                        notes.get(notes.size() - 1).window.positioned = note.window.positioned;
                }
            }
        }
        if (notes.isEmpty()) {
            String migrated = config.notesText == null ? "" : config.notesText;
            notes.add(new NoteState("Note 1", migrated, 1, false, false, 1.0F, 548, 502, 396, 180));
        }
        activeNoteIndex = Math.max(0, Math.min(config.activeNoteIndex, notes.size() - 1));
        sharedActiveNoteIndex = activeNoteIndex;
        notesText = getActiveNote().text;
        loadedSharedNotes = true;
    }

    private NoteState getActiveNote() {
        if (notes.isEmpty()) {
            notes.add(new NoteState("Note 1", "", 1, false, false, 1.0F, 548, 502, 396, 180));
        }
        activeNoteIndex = Math.max(0, Math.min(activeNoteIndex, notes.size() - 1));
        return notes.get(activeNoteIndex);
    }

    private void ensureConfigWindowStates() {
        if (config.browserWindow == null)
            config.browserWindow = new MinecraftOverlayConfig.WindowState(0, 0, 520, 320, false);
        if (config.screenshotsWindow == null)
            config.screenshotsWindow = new MinecraftOverlayConfig.WindowState(0, 0, 396, 220, false);
        if (config.friendsWindow == null)
            config.friendsWindow = new MinecraftOverlayConfig.WindowState(0, 0, 396, 184, false);
        if (config.notesWindow == null)
            config.notesWindow = new MinecraftOverlayConfig.WindowState(0, 0, 360, 180, false);
        if (config.calculatorWindow == null)
            config.calculatorWindow = new MinecraftOverlayConfig.WindowState(0, 0, 320, 252, false);
        if (config.timeWindow == null)
            config.timeWindow = new MinecraftOverlayConfig.WindowState(0, 0, 320, 180, false);
        if (config.spotifyWindow == null)
            config.spotifyWindow = new MinecraftOverlayConfig.WindowState(0, 0, 360, 220, false);
        if (config.appearanceWindow == null)
            config.appearanceWindow = new MinecraftOverlayConfig.WindowState(0, 0, 396, 280, false);
    }

    private static void applyWindowState(OverlayWindow window, MinecraftOverlayConfig.WindowState state) {
        if (state == null)
            return;
        window.x = state.x;
        window.y = state.y;
        window.width = state.width;
        window.height = state.height;
        window.positioned = state.positioned;
    }

    private static void copyWindowState(OverlayWindow window, MinecraftOverlayConfig.WindowState state) {
        state.x = window.x;
        state.y = window.y;
        state.width = window.width;
        state.height = window.height;
        state.positioned = window.positioned;
    }

    private static MinecraftOverlayConfig.WindowState copyWindowState(OverlayWindow window) {
        return new MinecraftOverlayConfig.WindowState(window.x, window.y, window.width, window.height,
                window.positioned);
    }

    @Override
    protected void init() {
        this.config = MinecraftOverlayConfig.load();
        loadSettings(focusedTab);
        initializeWindowPositions();
        rebuild();
        if (browserVisible && browserRequested)
            startBrowserInitialization();
    }

    private void initializeWindowPositions() {
        int rightX = Math.max(16, width - 412);
        if (!browserWindow.positioned) {
            browserWindow.x = (taskbarVertical ? SIDE_RAIL_WIDTH : 0) + 16;
            browserWindow.y = 16;
            browserWindow.width = Math.max(browserWindow.minWidth, Math.min(560, width - SIDE_RAIL_WIDTH - 448));
            browserWindow.height = Math.max(browserWindow.minHeight, height - TOOLBAR_HEIGHT - STATUS_HEIGHT - 28);
            browserWindow.positioned = true;
        }
        if (!screenshotsWindow.positioned) {
            screenshotsWindow.x = rightX;
            screenshotsWindow.y = 16;
            screenshotsWindow.positioned = true;
        }
        if (!friendsWindow.positioned) {
            friendsWindow.x = rightX;
            friendsWindow.y = 250;
            friendsWindow.positioned = true;
        }
        for (NoteState note : notes) {
            if (!note.window.positioned) {
                note.window.x = rightX;
                note.window.y = Math.min(Math.max(16, height - TOOLBAR_HEIGHT - STATUS_HEIGHT - 210), 460);
                note.window.positioned = true;
            }
        }
        if (!appearanceWindow.positioned) {
            appearanceWindow.x = rightX;
            appearanceWindow.y = TOOLBAR_HEIGHT + 445;
            appearanceWindow.positioned = true;
        }
        if (!calculatorWindow.positioned) {
            calculatorWindow.x = rightX;
            calculatorWindow.y = Math.max(16,
                    Math.min(height - TOOLBAR_HEIGHT - STATUS_HEIGHT - calculatorWindow.height - 18, 250));
            calculatorWindow.positioned = true;
        }
        if (!timeWindow.positioned) {
            timeWindow.x = rightX;
            timeWindow.y = Math.max(16,
                    Math.min(height - TOOLBAR_HEIGHT - STATUS_HEIGHT - timeWindow.height - 18, 360));
            timeWindow.positioned = true;
        }
        if (!spotifyWindow.positioned) {
            spotifyWindow.x = rightX;
            spotifyWindow.y = Math.max(16,
                    Math.min(height - TOOLBAR_HEIGHT - STATUS_HEIGHT - spotifyWindow.height - 18, 520));
            spotifyWindow.positioned = true;
        }
        clampWindows();
    }

    private void clampWindows() {
        browserWindow.clamp(width, height);
        screenshotsWindow.clamp(width, height);
        friendsWindow.clamp(width, height);
        calculatorWindow.clamp(width, height);
        timeWindow.clamp(width, height);
        spotifyWindow.clamp(width, height);
        appearanceWindow.clamp(width, height);
        for (NoteState note : notes)
            note.window.clamp(width, height);
    }

    private void rebuild() {
        clearChildren();
        initWindowWidgetsInFocusOrder();
        addToolbarButtons();
    }

    private void addToolbarButtons() {
        if (taskbarVertical) {
            int x = 10;
            int y = 18;
            addToolbarButton("◎", "Browser", x, y, browserVisible, () -> toggleTab(Tab.BROWSER));
            y += 32;
            addToolbarButton("▣", "Screenshots", x, y, screenshotsVisible, () -> toggleTab(Tab.SCREENSHOTS));
            y += 32;
            addToolbarButton("✎", "Notes", x, y, notesVisible, () -> toggleTab(Tab.NOTES));
            y += 32;
            addToolbarButton("☊", "Spotify", x, y, spotifyVisible, () -> toggleTab(Tab.SPOTIFY));
            y += 32;
            addToolbarButton("▦", "Calculator", x, y, calculatorVisible, () -> toggleTab(Tab.CALCULATOR));
            y += 32;
            addToolbarButton("◷", "Timers", x, y, timeVisible, () -> toggleTab(Tab.TIME));
            y += 32;
            addToolbarButton("☻", "Friends", x, y, friendsVisible, () -> toggleTab(Tab.FRIENDS));
            addToolbarButton("⚙", "Settings", x, Math.max(y + 32, height - 72), appearanceVisible,
                    () -> client.setScreen(new MinecraftOverlaySettingsScreen(this)));
            addToolbarButton("⌃", "Close", x, Math.max(y + 64, height - 38), false, this::close);
            return;
        }

        int x = 12;
        int y = height - 30;
        int buttonX = x;
        addToolbarButton("◎", "Browser", buttonX, y, browserVisible, () -> toggleTab(Tab.BROWSER));
        buttonX += 32;
        addToolbarButton("▣", "Screenshots", buttonX, y, screenshotsVisible, () -> toggleTab(Tab.SCREENSHOTS));
        buttonX += 32;
        addToolbarButton("✎", "Notes", buttonX, y, notesVisible, () -> toggleTab(Tab.NOTES));
        buttonX += 32;
        addToolbarButton("☊", "Spotify", buttonX, y, spotifyVisible, () -> toggleTab(Tab.SPOTIFY));
        buttonX += 32;
        addToolbarButton("▦", "Calculator", buttonX, y, calculatorVisible, () -> toggleTab(Tab.CALCULATOR));
        buttonX += 32;
        addToolbarButton("◷", "Timers", buttonX, y, timeVisible, () -> toggleTab(Tab.TIME));
        buttonX += 32;
        addToolbarButton("☻", "Friends", buttonX, y, friendsVisible, () -> toggleTab(Tab.FRIENDS));
        buttonX += 32;
        addToolbarButton("⚙", "Settings", buttonX, y, appearanceVisible,
                () -> client.setScreen(new MinecraftOverlaySettingsScreen(this)));
        buttonX += 32;
        addToolbarButton("⌃", "Close", Math.max(buttonX, width - 36), y, false, this::close);
    }

    private void initWindowWidgetsInFocusOrder() {
        for (Tab tab : WINDOW_ORDER) {
            if (tab != focusedTab)
                initWindowWidgets(tab);
        }
        initWindowWidgets(focusedTab);
    }

    private void initWindowWidgets(Tab tab) {
        if (!isTabVisible(tab))
            return;
        switch (tab) {
            case BROWSER -> initBrowser();
            case SCREENSHOTS -> initScreenshots();
            case FRIENDS -> initFriends();
            case NOTES -> initNotes();
            case CALCULATOR -> initCalculator();
            case TIME -> initTime();
            case SPOTIFY -> initSpotify();
            case APPEARANCE -> initAppearance();
        }
    }

    private void toggleTab(Tab tab) {
        if (tab == Tab.BROWSER) {
            browserVisible = !browserVisible;
        } else if (tab == Tab.SCREENSHOTS) {
            screenshotsVisible = !screenshotsVisible;
        } else if (tab == Tab.FRIENDS) {
            friendsVisible = !friendsVisible;
        } else if (tab == Tab.NOTES) {
            notesVisible = !notesVisible;
        } else if (tab == Tab.CALCULATOR) {
            calculatorVisible = !calculatorVisible;
        } else if (tab == Tab.TIME) {
            timeVisible = !timeVisible;
        } else if (tab == Tab.SPOTIFY) {
            spotifyVisible = !spotifyVisible;
        } else {
            appearanceVisible = !appearanceVisible;
        }
        focusedTab = tab;
        saveSettings();
        rebuild();
    }

    private void togglePinnedWindow(Tab tab) {
        if (tab == Tab.BROWSER) {
            browserPinned = !browserPinned;
            if (browserPinned)
                dockPinnedWindowToTop(browserWindow);
            status = browserPinned ? "Browser pinned to HUD." : "Browser unpinned from HUD.";
        } else if (tab == Tab.SCREENSHOTS) {
            screenshotsPinned = !screenshotsPinned;
            if (screenshotsPinned)
                dockPinnedWindowToTop(screenshotsWindow);
            status = screenshotsPinned ? "Screenshots pinned to HUD." : "Screenshots unpinned from HUD.";
        } else if (tab == Tab.FRIENDS) {
            friendsPinned = !friendsPinned;
            if (friendsPinned)
                dockPinnedWindowToTop(friendsWindow);
            status = friendsPinned ? "Friends pinned to HUD." : "Friends unpinned from HUD.";
        } else if (tab == Tab.NOTES) {
            notesPinned = !notesPinned;
            if (notesPinned)
                dockPinnedWindowToTop(getActiveNote().window);
            status = notesPinned ? "Notes pinned to HUD." : "Notes unpinned from HUD.";
        } else if (tab == Tab.CALCULATOR) {
            calculatorPinned = !calculatorPinned;
            if (calculatorPinned)
                dockPinnedWindowToTop(calculatorWindow);
            status = calculatorPinned ? "Calculator pinned to HUD." : "Calculator unpinned from HUD.";
        } else if (tab == Tab.TIME) {
            timePinned = !timePinned;
            if (timePinned)
                dockPinnedWindowToTop(timeWindow);
            status = timePinned ? "Clock pinned to HUD." : "Clock unpinned from HUD.";
        } else if (tab == Tab.SPOTIFY) {
            spotifyPinned = !spotifyPinned;
            if (spotifyPinned)
                dockPinnedWindowToTop(spotifyWindow);
            status = spotifyPinned ? "Spotify controls pinned to HUD." : "Spotify controls unpinned from HUD.";
        } else {
            appearancePinned = !appearancePinned;
            if (appearancePinned)
                dockPinnedWindowToTop(appearanceWindow);
            status = appearancePinned ? "Settings pinned to HUD." : "Settings unpinned from HUD.";
        }
        saveSettings();
        rebuild();
    }

    private void dockPinnedWindowToTop(OverlayWindow window) {
        window.y = 0;
        window.positioned = true;
        window.clamp(width, height);
    }

    private void initBrowser() {
        int navX = browserWindow.x + 5;
        int navY = browserWindow.y + HEADER_HEIGHT + 4;
        int controlHeight = 16;
        int openWidth = 42;
        int historyWidth = 22;
        int reloadWidth = 22;
        int pinWidth = 38;
        int newWidth = 22;
        int closeWidth = 22;
        int urlX = navX + historyWidth * 2 + 10;
        int urlWidth = Math.max(40, browserWindow.width - openWidth - reloadWidth - pinWidth - newWidth - closeWidth
                - historyWidth * 2 - 55);
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> navigateBrowserHistory(false))
                .dimensions(navX, navY, historyWidth, controlHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> navigateBrowserHistory(true))
                .dimensions(navX + historyWidth + 4, navY, historyWidth, controlHeight).build());
        urlField = new TextFieldWidget(textRenderer, urlX, navY, urlWidth, controlHeight, Text.literal("URL"));
        urlField.setMaxLength(2048);
        urlField.setText(getActiveBrowserTab().url);
        addDrawableChild(urlField);
        int buttonX = urlX + urlWidth + 5;
        addDrawableChild(ButtonWidget.builder(Text.literal("Go"), button -> openUrl())
                .dimensions(buttonX, navY, openWidth, controlHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("R"), button -> reloadActiveBrowserTab())
                .dimensions(buttonX + openWidth + 5, navY, reloadWidth, controlHeight).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal(getActiveBrowserTab().pinned ? "Unpin" : "Pin"),
                        button -> togglePinnedBrowserTab())
                .dimensions(buttonX + openWidth + reloadWidth + 10, navY, pinWidth, controlHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> addBrowserTab())
                .dimensions(buttonX + openWidth + reloadWidth + pinWidth + 15, navY, newWidth, controlHeight).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("x"), button -> closeActiveBrowserTab()).dimensions(
                buttonX + openWidth + reloadWidth + pinWidth + newWidth + 20, navY, closeWidth, controlHeight).build());
    }

    private void initScreenshots() {
        screenshots = loadScreenshots();
        int topY = screenshotsWindow.y + HEADER_HEIGHT + 5;
        if (selectedScreenshot == null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Folder"), button -> openScreenshotsFolder())
                    .dimensions(screenshotsWindow.x + screenshotsWindow.width - 62, topY, 56, 16).build());
            return;
        }
        int x = screenshotsWindow.x + 8;
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> closeScreenshotEditor())
                .dimensions(x, topY, 44, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveEditedScreenshot())
                .dimensions(x + 50, topY, 44, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset"), button -> reloadSelectedScreenshot())
                .dimensions(x + 100, topY, 48, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Rot"), button -> rotateEditedScreenshot())
                .dimensions(x + 154, topY, 36, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("B-"), button -> adjustEditedScreenshotBrightness(0.86F))
                .dimensions(x + 196, topY, 30, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("B+"), button -> adjustEditedScreenshotBrightness(1.16F))
                .dimensions(x + 232, topY, 30, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Gray"), button -> grayscaleEditedScreenshot())
                .dimensions(x + 268, topY, 42, 16).build());
    }

    private void initFriends() {
        int contentX = friendsWindow.x + 6;
        int contentY = friendsWindow.y + HEADER_HEIGHT + 5;
        int contentWidth = friendsWindow.width - 12;
        addDrawableChild(ButtonWidget
                .builder(Text.literal("Social"),
                        button -> sendEssentialCommand("essentialfriends", "Opened Essential social menu."))
                .dimensions(contentX, contentY, 62, 16).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal("Inbox"),
                        button -> sendEssentialCommand("emsg", "Opened Essential messages command."))
                .dimensions(contentX + 67, contentY, 52, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Invite"), button -> inviteFriend())
                .dimensions(contentX + 124, contentY, 52, 16).build());
        friendField = new TextFieldWidget(textRenderer, contentX, contentY + 27,
                Math.min(126, Math.max(76, contentWidth / 3)), 16, Text.literal("Friend"));
        friendField.setPlaceholder(Text.literal("Friend"));
        addDrawableChild(friendField);
        int messageX = friendField.getX() + friendField.getWidth() + 5;
        int sendWidth = 52;
        messageField = new TextFieldWidget(textRenderer, messageX, contentY + 27,
                Math.max(60, friendsWindow.x + friendsWindow.width - 6 - sendWidth - 5 - messageX), 16,
                Text.literal("Message"));
        messageField.setPlaceholder(Text.literal("Message"));
        addDrawableChild(messageField);
        addDrawableChild(ButtonWidget.builder(Text.literal("Send"), button -> sendFriendMessage())
                .dimensions(friendsWindow.x + friendsWindow.width - 6 - sendWidth, contentY + 27, sendWidth, 16)
                .build());
    }

    private void initCalculator() {
        int x = calculatorWindow.x + 8;
        int y = calculatorWindow.y + HEADER_HEIGHT + 8;
        int width = calculatorWindow.width - 16;
        calculatorField = new TextFieldWidget(textRenderer, x, y, width, 18, Text.literal("Expression"));
        calculatorField.setMaxLength(120);
        calculatorField.setText(calculatorExpression);
        calculatorField.setChangedListener(text -> calculatorExpression = text);
        addDrawableChild(calculatorField);

        String[][] buttons = {
                { "7", "8", "9", "/" },
                { "4", "5", "6", "*" },
                { "1", "2", "3", "-" },
                { "0", ".", "(", ")" },
                { "C", "Back", "=", "+" }
        };
        int gap = 4;
        int buttonWidth = Math.max(36, (width - gap * 3) / 4);
        int buttonHeight = 22;
        int buttonY = y + 48;
        for (int row = 0; row < buttons.length; row++) {
            for (int col = 0; col < buttons[row].length; col++) {
                String token = buttons[row][col];
                int buttonX = x + col * (buttonWidth + gap);
                addDrawableChild(ButtonWidget.builder(Text.literal(token), button -> pressCalculatorButton(token))
                        .dimensions(buttonX, buttonY + row * (buttonHeight + gap), buttonWidth, buttonHeight).build());
            }
        }
    }

    private void initSpotify() {
        if (MinecraftOverlaySpotifyControls.getTrackTitle().isBlank())
            MinecraftOverlaySpotifyControls.refresh();
        int x = spotifyWindow.x + 8;
        int y = spotifyWindow.y + HEADER_HEIGHT + 8;
        boolean searchEnabled = config.spotifySearchBarEnabled;
        if (searchEnabled) {
            int searchButtonWidth = 54;
            spotifySearchField = new TextFieldWidget(textRenderer, x, y,
                    Math.max(40, spotifyWindow.width - 24 - searchButtonWidth), 18, Text.literal("Search songs"));
            spotifySearchField.setMaxLength(128);
            spotifySearchField.setText(MinecraftOverlaySpotifyControls.getSearchQuery());
            addDrawableChild(spotifySearchField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Search"), button -> searchSpotify())
                    .dimensions(x + spotifyWindow.width - 16 - searchButtonWidth, y, searchButtonWidth, 18).build());
        } else {
            spotifySearchField = null;
        }

        int albumSize = 48;
        int albumY = searchEnabled ? y + 26 : y;
        int controlsY = spotifyWindow.y + spotifyWindow.height - 50;
        int buttonSize = 20;
        int gap = 4;
        int buttonCount = 5;
        int controlsWidth = buttonCount * buttonSize + (buttonCount - 1) * gap;
        int controlsX = spotifyWindow.x + Math.max(8, (spotifyWindow.width - controlsWidth) / 2);

        // Shuffle, Prev, Play/Pause, Next, Repeat
        addDrawableChild(ButtonWidget.builder(Text.literal("🔀"), button -> {
            status = "Use Spotify to change shuffle.";
        }).dimensions(controlsX, controlsY, buttonSize, buttonSize).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal("⏮"), button -> status = MinecraftOverlaySpotifyControls.previousTrack())
                .dimensions(controlsX + buttonSize + gap, controlsY, buttonSize, buttonSize).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal(MinecraftOverlaySpotifyControls.isPlaying() ? "⏸" : "▶"),
                        button -> status = MinecraftOverlaySpotifyControls.playPause())
                .dimensions(controlsX + (buttonSize + gap) * 2, controlsY, buttonSize, buttonSize).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("⏭"), button -> status = MinecraftOverlaySpotifyControls.nextTrack())
                        .dimensions(controlsX + (buttonSize + gap) * 3, controlsY, buttonSize, buttonSize).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("🔁"), button -> {
            status = "Use Spotify to change repeat.";
        }).dimensions(controlsX + (buttonSize + gap) * 4, controlsY, buttonSize, buttonSize).build());

        if (searchEnabled) {
            int resultsY = albumY + albumSize + 28;
            int resultRows = Math.min(MinecraftOverlaySpotifyControls.getSearchResults().size(),
                    Math.max(0, (controlsY - resultsY - 18) / 19));
            for (int i = 0; i < resultRows; i++) {
                MinecraftOverlaySpotifyControls.SpotifySearchResult result = MinecraftOverlaySpotifyControls
                        .getSearchResults().get(i);
                int index = i;
                String label = textRenderer.trimToWidth(result.title() + " - " + result.artist(),
                        spotifyWindow.width - 20);
                addDrawableChild(ButtonWidget.builder(Text.literal(label),
                        button -> status = MinecraftOverlaySpotifyControls.playSearchResult(index))
                        .dimensions(x, resultsY + i * 19, spotifyWindow.width - 16, 17).build());
            }
        }
        spotifySearchVersion = MinecraftOverlaySpotifyControls.getSearchVersion();
    }

    private void searchSpotify() {
        if (spotifySearchField == null)
            return;
        status = MinecraftOverlaySpotifyControls.search(spotifySearchField.getText());
        setFocused(null);
    }

    private void initTime() {
        int x = timeWindow.x + 8;
        int y = timeWindow.y + HEADER_HEIGHT + 90;
        int buttonWidth = Math.max(56, (timeWindow.width - 28) / 4);
        addDrawableChild(ButtonWidget.builder(Text.literal("Timer"), button -> {
            MinecraftOverlayTimeTools.setActiveTool(MinecraftOverlayTimeTools.Tool.TIMER);
            rebuild();
        }).dimensions(x, y, buttonWidth, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Watch"), button -> {
            MinecraftOverlayTimeTools.setActiveTool(MinecraftOverlayTimeTools.Tool.STOPWATCH);
            rebuild();
        }).dimensions(x + buttonWidth + 4, y, buttonWidth, 18).build());
        addDrawableChild(
                ButtonWidget
                        .builder(
                                Text.literal(
                                        MinecraftOverlayTimeTools.activeTool() == MinecraftOverlayTimeTools.Tool.TIMER
                                                && MinecraftOverlayTimeTools.isTimerRunning()
                                                || MinecraftOverlayTimeTools
                                                        .activeTool() == MinecraftOverlayTimeTools.Tool.STOPWATCH
                                                        && MinecraftOverlayTimeTools.isStopwatchRunning() ? "Pause"
                                                                : "Start"),
                                button -> status = MinecraftOverlayTimeTools.toggleActive())
                        .dimensions(x + (buttonWidth + 4) * 2, y, buttonWidth, 18).build());
        addDrawableChild(
                ButtonWidget.builder(Text.literal("Reset"), button -> status = MinecraftOverlayTimeTools.resetActive())
                        .dimensions(x + (buttonWidth + 4) * 3, y, buttonWidth, 18).build());
        int timerY = y + 28;
        addDrawableChild(ButtonWidget
                .builder(Text.literal("-1m"), button -> status = MinecraftOverlayTimeTools.adjustTimerMinutes(-1))
                .dimensions(x, timerY, 46, 18).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal("+1m"), button -> status = MinecraftOverlayTimeTools.adjustTimerMinutes(1))
                .dimensions(x + 52, timerY, 46, 18).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal("+5m"), button -> status = MinecraftOverlayTimeTools.adjustTimerMinutes(5))
                .dimensions(x + 104, timerY, 46, 18).build());
    }

    private void pressCalculatorButton(String token) {
        if ("C".equals(token)) {
            calculatorExpression = "";
            calculatorResult = "0";
        } else if ("Back".equals(token)) {
            if (!calculatorExpression.isEmpty())
                calculatorExpression = calculatorExpression.substring(0, calculatorExpression.length() - 1);
        } else if ("=".equals(token)) {
            evaluateCalculator();
        } else {
            calculatorExpression += token;
        }
        if (calculatorField != null)
            calculatorField.setText(calculatorExpression);
        saveSettings();
        rebuild();
    }

    private void evaluateCalculator() {
        try {
            double value = new CalculatorParser(calculatorExpression).parse();
            calculatorResult = formatCalculatorResult(value);
            status = "Calculator: " + calculatorResult;
        } catch (IllegalArgumentException exception) {
            calculatorResult = "Error";
            status = exception.getMessage();
        }
    }

    private static String formatCalculatorResult(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            return "Error";
        if (Math.abs(value - Math.rint(value)) < 0.000000001D)
            return String.valueOf((long) Math.rint(value));
        return String.format(Locale.ROOT, "%.8f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private void initNotes() {
        for (int i = 0; i < notes.size(); i++) {
            NoteState note = notes.get(i);
            int contentX = note.window.x + 6;
            int contentY = note.window.y + HEADER_HEIGHT + 8;
            int noteIndex = i;

            if (i == activeNoteIndex) {
                TextFieldWidget titleField = new TextFieldWidget(textRenderer, contentX, contentY,
                        note.window.width - 100, 18, Text.literal("Title"));
                titleField.setText(note.title);
                titleField.setChangedListener(text -> note.title = text);
                addDrawableChild(titleField);

                addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> addNote())
                        .dimensions(note.window.x + note.window.width - 86, contentY, 24, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> removeNote())
                        .dimensions(note.window.x + note.window.width - 58, contentY, 24, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Save"), button -> saveSettings())
                        .dimensions(note.window.x + note.window.width - 30, contentY, 24, 18).build());

                int toolsY = contentY + 30;
                addDrawableChild(ButtonWidget.builder(Text.literal(note.bold ? "B*" : "B"), button -> toggleNoteBold())
                        .dimensions(contentX, toolsY, 24, 18).build());
                addDrawableChild(
                        ButtonWidget.builder(Text.literal(note.italic ? "I*" : "I"), button -> toggleNoteItalic())
                                .dimensions(contentX + 28, toolsY, 24, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("A-"), button -> adjustNoteTextSize(-1))
                        .dimensions(contentX + 56, toolsY, 30, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("A+"), button -> adjustNoteTextSize(1))
                        .dimensions(contentX + 90, toolsY, 30, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Stamp"), button -> insertNoteStamp())
                        .dimensions(contentX + 126, toolsY, 48, 18).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), button -> clearNoteText())
                        .dimensions(contentX + 178, toolsY, 48, 18).build());
                addDrawableChild(
                        ButtonWidget
                                .builder(Text.literal("O: " + Math.round(note.opacity * 100.0F) + "%"),
                                        button -> adjustNoteOpacity(0.1F))
                                .dimensions(contentX + 230, toolsY, 58, 18).build());
            }

            int editorY = contentY + (i == activeNoteIndex ? 54 : 4);
            int editorH = note.window.height - HEADER_HEIGHT - (i == activeNoteIndex ? 68 : 18);
            NotesEditorWidget editor = new NotesEditorWidget(contentX, editorY, Math.max(80, note.window.width - 12),
                    Math.max(20, editorH), Text.literal("Notes"), note.text, note.textSize, note.bold, note.italic,
                    text -> updateNotesText(note, text));
            addDrawableChild(editor);
        }
    }

    private void initAppearance() {
        int x = appearanceWindow.x + 8;
        int y = appearanceWindow.y + HEADER_HEIGHT + 5;
        addDrawableChild(
                ButtonWidget.builder(Text.literal(colorMode == ColorMode.ACCENT ? "Accent *" : "Accent"), button -> {
                    colorMode = ColorMode.ACCENT;
                    rebuild();
                }).dimensions(x, y, 72, 16).build());
        addDrawableChild(ButtonWidget
                .builder(Text.literal(colorMode == ColorMode.TAB_BACKGROUND ? "Tab BG *" : "Tab BG"), button -> {
                    colorMode = ColorMode.TAB_BACKGROUND;
                    rebuild();
                }).dimensions(x + 78, y, 72, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(useImageForTabs ? "Tabs: Image" : "Tabs: Color"), button -> {
            useImageForTabs = !useImageForTabs;
            saveSettings();
            rebuild();
        }).dimensions(x + 156, y, 78, 16).build());
        int scaleY = appearanceWindow.y + appearanceWindow.height - 23;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> adjustBrowserPixelScale(-0.05D))
                .dimensions(x, scaleY, 22, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> adjustBrowserPixelScale(0.05D))
                .dimensions(x + 28, scaleY, 22, 16).build());

        int opacityY = appearanceWindow.y + appearanceWindow.height - 43;
        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> {
            tabOpacity = clampOpacity(tabOpacity - 0.05F);
            config.tabOpacity = tabOpacity;
            updateSharedHudState();
            rebuild();
        }).dimensions(x, opacityY, 22, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tab Opac"), button -> {
        }).dimensions(x + 24, opacityY, 52, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
            tabOpacity = clampOpacity(tabOpacity + 0.05F);
            config.tabOpacity = tabOpacity;
            updateSharedHudState();
            rebuild();
        }).dimensions(x + 78, opacityY, 22, 16).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("-"), button -> {
            pinnedTabOpacity = clampOpacity(pinnedTabOpacity - 0.05F);
            config.pinnedTabOpacity = pinnedTabOpacity;
            updateSharedHudState();
            rebuild();
        }).dimensions(x + 104, opacityY, 22, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Pin Opac"), button -> {
        }).dimensions(x + 128, opacityY, 52, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+"), button -> {
            pinnedTabOpacity = clampOpacity(pinnedTabOpacity + 0.05F);
            config.pinnedTabOpacity = pinnedTabOpacity;
            updateSharedHudState();
            rebuild();
        }).dimensions(x + 182, opacityY, 22, 16).build());
        int keyY = appearanceWindow.y + HEADER_HEIGHT + 128;
        int buttonX = appearanceWindow.x + appearanceWindow.width - 94;
        for (int i = 0; i < 6; i++) {
            final int index = i;
            addDrawableChild(
                    ButtonWidget
                            .builder(Text.literal(keybindButtonText(index, getKeybind(index), getSecondKeybind(index))),
                                    button -> beginKeybindCapture(index))
                            .dimensions(buttonX, keyY + i * 19, 86, 16).build());
        }
    }

    private void adjustBrowserPixelScale(double delta) {
        browserPixelScale = clampBrowserPixelScale(browserPixelScale + delta);
        resizeBrowser();
        saveSettings();
        status = "Browser pixel scale: " + Math.round(browserPixelScale * 100.0D) + "%";
    }

    private void beginKeybindCapture(int index) {
        awaitingKeybind = index;
        pendingComboModifier = 0;
        setBrowserKeyboardFocused(getActiveBrowserTab(), false);
        status = "Press one key or a modifier then another key for " + keybindLabel(index) + ".";
        rebuild();
    }

    private String keybindButtonText(int index, int key, int secondKey) {
        if (awaitingKeybind == index)
            return pendingComboModifier == 0 ? "Press..." : keyName(pendingComboModifier) + " + ...";
        return keyComboName(key, secondKey);
    }

    private String keybindLabel(int index) {
        return switch (index) {
            case 1 -> "Prev";
            case 2 -> "Play";
            case 3 -> "Next";
            case 4 -> "Time pause";
            case 5 -> "Time reset";
            default -> "Overlay";
        };
    }

    private int getKeybind(int index) {
        return switch (index) {
            case 1 -> config.spotifyPreviousKey;
            case 2 -> config.spotifyPlayPauseKey;
            case 3 -> config.spotifyNextKey;
            case 4 -> config.timePauseKey;
            case 5 -> config.timeResetKey;
            default -> openOverlayKey;
        };
    }

    private int getSecondKeybind(int index) {
        return switch (index) {
            case 1 -> config.spotifyPreviousKeySecond;
            case 2 -> config.spotifyPlayPauseKeySecond;
            case 3 -> config.spotifyNextKeySecond;
            case 4 -> config.timePauseKeySecond;
            case 5 -> config.timeResetKeySecond;
            default -> openOverlayKeySecond;
        };
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
        if (pendingComboModifier != 0)
            setKeybind(awaitingKeybind, pendingComboModifier, key);
        else
            setKeybind(awaitingKeybind, key, 0);
    }

    private void setKeybind(int index, int key, int secondKey) {
        if (index == 0) {
            openOverlayKey = key;
            openOverlayKeySecond = secondKey;
            config.openOverlayKey = key;
            config.openOverlayKeySecond = secondKey;
        } else if (index == 1) {
            config.spotifyPreviousKey = key;
            config.spotifyPreviousKeySecond = secondKey;
        } else if (index == 2) {
            config.spotifyPlayPauseKey = key;
            config.spotifyPlayPauseKeySecond = secondKey;
        } else if (index == 3) {
            config.spotifyNextKey = key;
            config.spotifyNextKeySecond = secondKey;
        } else if (index == 4) {
            config.timePauseKey = key;
            config.timePauseKeySecond = secondKey;
        } else if (index == 5) {
            config.timeResetKey = key;
            config.timeResetKeySecond = secondKey;
        }
        awaitingKeybind = -1;
        pendingComboModifier = 0;
        saveSettings();
        status = keybindLabel(index) + " key set to " + keyComboName(key, secondKey) + ".";
        rebuild();
    }

    private static String keyName(int key) {
        if (key <= 0)
            return "Unbound";
        String name = GLFW.glfwGetKeyName(key, 0);
        if (name != null && !name.isBlank())
            return name.toUpperCase(Locale.ROOT);
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_DELETE -> "Delete";
            case GLFW.GLFW_KEY_INSERT -> "Insert";
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
        if (key <= 0)
            return "Unbound";
        return secondKey <= 0 ? keyName(key) : keyName(key) + " + " + keyName(secondKey);
    }

    private static boolean isModifierKey(int key) {
        return key == GLFW.GLFW_KEY_LEFT_SHIFT || key == GLFW.GLFW_KEY_RIGHT_SHIFT
                || key == GLFW.GLFW_KEY_LEFT_CONTROL || key == GLFW.GLFW_KEY_RIGHT_CONTROL
                || key == GLFW.GLFW_KEY_LEFT_ALT || key == GLFW.GLFW_KEY_RIGHT_ALT
                || key == GLFW.GLFW_KEY_LEFT_SUPER || key == GLFW.GLFW_KEY_RIGHT_SUPER;
    }

    private void openUrl() {
        String url = normalizeBrowserUrl();
        if (url == null) {
            status = "Enter a URL first.";
            return;
        }
        urlField.setText(url);
        BrowserTabState tab = getActiveBrowserTab();
        tab.url = url;
        browserRequested = true;
        if (tab.browser != null) {
            loadBrowserUrl(tab);
        } else {
            browserFailed = false;
            browserError = "";
            startBrowserInitialization();
        }
        saveSettings();
    }

    private void addBrowserTab() {
        addBrowserTab("https://www.google.com");
    }

    private void addBrowserTab(String url) {
        browserTabs.add(new BrowserTabState(url, false));
        sharedActiveBrowserTab = browserTabs.size() - 1;
        browserRequested = true;
        browserFailed = false;
        browserError = "";
        saveSettings();
        rebuild();
        startBrowserInitialization();
    }

    private void closeActiveBrowserTab() {
        BrowserTabState active = getActiveBrowserTab();
        if (active.pinned) {
            status = "Unpin this tab before closing it.";
            return;
        }
        if (browserTabs.size() <= 1) {
            closeBrowser(active);
            browserRequested = false;
            saveSettings();
            rebuild();
            return;
        }
        closeBrowser(active);
        browserTabs.remove(sharedActiveBrowserTab);
        sharedActiveBrowserTab = Math.max(0, Math.min(sharedActiveBrowserTab, browserTabs.size() - 1));
        if (urlField != null)
            urlField.setText(getActiveBrowserTab().url);
        browserFailed = false;
        browserError = "";
        saveSettings();
        rebuild();
    }

    private void reloadActiveBrowserTab() {
        BrowserTabState tab = getActiveBrowserTab();
        if (tab.browser == null) {
            openUrl();
            return;
        }
        try {
            tab.browser.reload();
            status = "Reloading " + tab.url;
        } catch (Throwable exception) {
            browserFailed = true;
            browserError = exception.toString();
            status = "Could not reload browser: " + browserError;
            MinecraftOverlay.LOGGER.warn("Failed to reload embedded browser", exception);
        }
    }

    private boolean handleWindowHeaderClick(double mouseX, double mouseY) {
        if (appearanceVisible && handleHeaderIcons(appearanceWindow, mouseX, mouseY, Tab.APPEARANCE))
            return true;
        if (notesVisible) {
            for (NoteState note : notes) {
                if (handleHeaderIcons(note.window, mouseX, mouseY, Tab.NOTES))
                    return true;
            }
        }
        if (friendsVisible && handleHeaderIcons(friendsWindow, mouseX, mouseY, Tab.FRIENDS))
            return true;
        if (spotifyVisible && handleHeaderIcons(spotifyWindow, mouseX, mouseY, Tab.SPOTIFY))
            return true;
        if (timeVisible && handleHeaderIcons(timeWindow, mouseX, mouseY, Tab.TIME))
            return true;
        if (calculatorVisible && handleHeaderIcons(calculatorWindow, mouseX, mouseY, Tab.CALCULATOR))
            return true;
        if (screenshotsVisible && handleHeaderIcons(screenshotsWindow, mouseX, mouseY, Tab.SCREENSHOTS))
            return true;
        if (browserVisible && handleHeaderIcons(browserWindow, mouseX, mouseY, Tab.BROWSER))
            return true;
        return false;
    }

    private boolean handleHeaderIcons(OverlayWindow window, double mouseX, double mouseY, Tab tab) {
        if (mouseX >= window.x + window.width - 18 && mouseX <= window.x + window.width - 4 && mouseY >= window.y + 4
                && mouseY <= window.y + 16) {
            toggleTab(tab);
            return true;
        }
        if (mouseX >= window.x + window.width - 34 && mouseX <= window.x + window.width - 20 && mouseY >= window.y + 4
                && mouseY <= window.y + 16) {
            togglePinnedWindow(tab);
            return true;
        }
        return false;
    }

    private void navigateBrowserHistory(boolean forward) {
        BrowserTabState tab = getActiveBrowserTab();
        if (tab.browser == null) {
            status = "Browser is not running.";
            return;
        }
        try {
            if (forward)
                tab.browser.goForward();
            else
                tab.browser.goBack();
            status = forward ? "Forward." : "Back.";
        } catch (Throwable exception) {
            status = forward ? "Could not go forward." : "Could not go back.";
            MinecraftOverlay.LOGGER.warn("Failed to navigate browser history", exception);
        }
    }

    private void togglePinnedBrowserTab() {
        BrowserTabState tab = getActiveBrowserTab();
        tab.pinned = !tab.pinned;
        status = tab.pinned ? "Pinned tab." : "Unpinned tab.";
        saveSettings();
        rebuild();
    }

    private String normalizeBrowserUrl() {
        String currentUrl = getActiveBrowserTab().url;
        String url = urlField == null ? currentUrl.trim() : urlField.getText().trim();
        if (url.isEmpty())
            return null;
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            url = "https://" + url;
        return url;
    }

    private void startBrowserInitialization() {
        String clientName = MinecraftOverlay.getDetectedClientName();
        if ((clientName.equals("Lunar") || clientName.equals("Feather")) && !browserRequested) {
            status = "Click Go or + to start browser on " + clientName + ".";
            return;
        }
        BrowserTabState activeTab = getActiveBrowserTab();
        if (shouldUseFeatherBrowserBackend(activeTab)) {
            if (!browserCreating && !browserFailed) {
                if (activeTab.browser == null) {
                    createEmbeddedBrowser(activeTab);
                } else {
                    for (BrowserTabState tab : browserTabs) {
                        if (tab.browser == null) {
                            createEmbeddedBrowser(tab);
                            break;
                        }
                    }
                }
            }
            if (!browserFailed && getActiveBrowserTab().browser == null) {
                status = "Starting Feather browser engine...";
            }
            return;
        }
        if (browserInitialization == null && !shouldUseFeatherBrowserBackend(activeTab)) {
            try {
                if (!isCommonsCompressCompatible()) {
                    browserFailed = true;
                    browserError = "commons-compress is too old (missing TarArchiveInputStream.getNextEntry). "
                            + "Update/remove the client/mod that bundles an old commons-compress.";
                    status = "Embedded browser failed: " + browserError;
                    return;
                }
                prepareMcefRuntime();
                browserInitialization = MCEFApi.initialize();
            } catch (Throwable t) {
                browserFailed = true;
                if (resetMcefInstallAfterStartupFailure(t)) {
                    browserNeedsRestartAfterMcefReset = true;
                    browserError = "MCEF native files need a clean reinstall. Close the client fully, then start it again.";
                } else {
                    browserError = "MCEFApi.initialize() crashed: " + t.toString();
                }
                status = "Embedded browser failed: " + browserError;
                MinecraftOverlay.LOGGER.error("MCEF Modern crashed during initialize()", t);
                return;
            }
            if (browserInitialization == null) {
                browserFailed = true;
                browserError = "MCEFApi.initialize() returned null";
                status = "Embedded browser failed: " + browserError;
                MinecraftOverlay.LOGGER.error("MCEF Modern failed to initialize: MCEFApi.initialize() returned null.");
                return;
            }
            browserInitialization.getFuture().whenComplete((api, throwable) -> {
                if (throwable != null) {
                    browserFailed = true;
                    Throwable cause = throwable;
                    while ((cause instanceof java.util.concurrent.CompletionException
                            || cause instanceof RuntimeException) && cause.getCause() != null) {
                        cause = cause.getCause();
                    }
                    if (resetMcefInstallAfterStartupFailure(cause)) {
                        browserNeedsRestartAfterMcefReset = true;
                        browserError = "MCEF native files need a clean reinstall. Close the client fully, then start it again.";
                    } else {
                        browserError = cause.toString();
                    }
                    status = "Embedded browser failed: " + browserError;
                    MinecraftOverlay.LOGGER.error("MCEF Modern failed to initialize (unwrapped: {})", browserError,
                            throwable);
                } else {
                    MinecraftOverlay.LOGGER.info("MCEF Modern initialized successfully.");
                }
            });
        }
        if (browserInitialization.getFuture().isCompletedExceptionally()) {
            browserFailed = true;
            status = getBrowserStatus();
            return;
        }
        if (browserInitialization.getFuture().isDone() && !browserCreating && !browserFailed) {
            for (BrowserTabState tab : browserTabs) {
                if (tab.browser == null)
                    createEmbeddedBrowser(tab);
            }
            return;
        }
        status = getBrowserStatus();
    }

    private static boolean isCommonsCompressCompatible() {
        try {
            Class<?> tis = Class.forName("org.apache.commons.compress.archivers.tar.TarArchiveInputStream");
            tis.getMethod("getNextTarEntry");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean shouldUseFeatherBrowserBackend() {
        return !forceMcefBrowserBackend && shouldUseFeatherBrowserBackendStatic();
    }

    private boolean shouldUseFeatherBrowserBackend(BrowserTabState tab) {
        return tab != null && !tab.forceMcefBackend && shouldUseFeatherBrowserBackend();
    }

    private static boolean shouldUseFeatherBrowserBackendStatic() {
        return "Feather".equals(MinecraftOverlay.getDetectedClientName())
                && isClassAvailable("net.digitalingot.fcef.CefApp");
    }

    private static boolean isClassAvailable(String className) {
        try {
            Class.forName(className, false, MinecraftOverlayScreen.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
        }
        try {
            ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
            return contextLoader != null && Class.forName(className, false, contextLoader) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void prepareMcefRuntime() {
        clearLegacyOverlayProperty("mcef.dir");
        clearLegacyOverlayProperty("jcef.cache_path");
        clearLegacyOverlayProperty("jcef.app.dir");
        clearLegacyOverlayProperty("jcef.log.file");
        clearLegacyOverlayProperty("jcef.log.severity");
        clearLegacyOverlayProperty("mcef.isolated");
        clearLegacyOverlayProperty("mcef.skip_lock_check");
        clearLegacyOverlayProperty("mcef.no_sandbox");
        clearLegacyOverlayProperty("mcef.trace");

        Path cachePath = readMcefPathField("CACHE_PATH");
        Path installPath = readMcefPathField("JCEF_PATH");
        cleanupChromiumLockFiles(cachePath);
        MinecraftOverlay.LOGGER.info(
                "Starting MCEF Modern with managed paths. client={}, cache={}, install={}",
                MinecraftOverlay.getDetectedClientName().isEmpty() ? "vanilla/fabric"
                        : MinecraftOverlay.getDetectedClientName(),
                cachePath,
                installPath);
    }

    private static void clearLegacyOverlayProperty(String key) {
        String value = System.getProperty(key);
        if (value == null)
            return;
        String lowerValue = value.toLowerCase(Locale.ROOT).replace('\\', '/');
        boolean oldOverlayPath = lowerValue.contains("mcef_overlay_isolated")
                || lowerValue.contains("minecraftoverlay");
        boolean oldOverlayFlag = "mcef.isolated".equals(key) || "mcef.skip_lock_check".equals(key);
        if (oldOverlayPath || oldOverlayFlag) {
            System.clearProperty(key);
        }
    }

    private static boolean resetMcefInstallAfterStartupFailure(Throwable throwable) {
        if (mcefNativeResetAttempted || !isMcefStartupFailure(throwable))
            return false;
        mcefNativeResetAttempted = true;
        boolean deletedAny = false;
        deletedAny |= deleteManagedMcefPath(readMcefPathField("JCEF_PATH"));
        deletedAny |= deleteManagedMcefPath(readMcefPathField("CACHE_PATH"));
        if (deletedAny) {
            MinecraftOverlay.LOGGER.warn("Reset MCEF native files after startup failure. Client restart is required.",
                    throwable);
        } else {
            MinecraftOverlay.LOGGER.warn(
                    "MCEF startup failed and native files could not be reset while the client is running.", throwable);
        }
        return true;
    }

    private static boolean isMcefStartupFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            String text = (current.getClass().getName() + " " + current.getMessage()).toLowerCase(Locale.ROOT);
            if (text.contains("unsatisfiedlinkerror")
                    && (text.contains("libcef") || text.contains("jcef") || text.contains("mcef-modern"))) {
                return true;
            }
            if (text.contains("could not create installation directory")
                    || text.contains("failed to initialize mcef modern")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static boolean deleteManagedMcefPath(Path path) {
        if (!isManagedMcefPath(path))
            return false;
        try (Stream<Path> stream = Files.walk(path)) {
            List<Path> paths = stream.sorted(Comparator.reverseOrder()).toList();
            boolean deleted = false;
            for (Path entry : paths) {
                Files.deleteIfExists(entry);
                deleted = true;
            }
            return deleted;
        } catch (IOException exception) {
            MinecraftOverlay.LOGGER.warn("Could not reset MCEF path {}", path, exception);
            return false;
        }
    }

    private static boolean isManagedMcefPath(Path path) {
        if (path == null)
            return false;
        Path normalized = path.toAbsolutePath().normalize();
        String lower = normalized.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        return lower.contains("/config/mcef-modern/") && (lower.endsWith("/jcef") || lower.endsWith("/cache"));
    }

    private static Path readMcefPathField(String fieldName) {
        try {
            Class<?> mcefModern = Class.forName("net.dimaskama.mcef.impl.MCEFModern");
            Field field = mcefModern.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Path path)
                return path;
            if (value instanceof java.io.File file)
                return file.toPath();
            if (value instanceof String text && !text.isBlank())
                return Path.of(text);
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.debug("Could not read MCEF Modern path field {}", fieldName, exception);
        }
        return null;
    }

    private static void cleanupChromiumLockFiles(Path cachePath) {
        if (cachePath == null)
            return;
        try {
            Files.createDirectories(cachePath);
            Files.deleteIfExists(cachePath.resolve("SingletonLock"));
            Files.deleteIfExists(cachePath.resolve("SingletonCookie"));
            Files.deleteIfExists(cachePath.resolve("SingletonSocket"));
            Files.deleteIfExists(cachePath.resolve("lockfile"));
        } catch (IOException exception) {
            MinecraftOverlay.LOGGER.warn("Could not clean stale MCEF cache lock files in {}", cachePath, exception);
        }
    }

    private void createEmbeddedBrowser(BrowserTabState tab) {
        if (browserCreating || tab.browser != null || browserFailed)
            return;
        browserCreating = true;
        try {
            String detectedClient = MinecraftOverlay.getDetectedClientName();
            boolean featherBackend = shouldUseFeatherBrowserBackend(tab);
            boolean transparent = false;
            MinecraftOverlay.LOGGER.info(
                    "Attempting to create {} browser for {} (client: {}, transparent: {})",
                    featherBackend ? "Feather FCEF" : "MCEF",
                    tab.url,
                    detectedClient.isEmpty() ? "vanilla/fabric" : detectedClient,
                    transparent);
            if (featherBackend) {
                tab.browser = new FeatherCefOverlayBrowser(tab.url, newUrl -> updateBrowserTabUrl(tab, newUrl),
                        null, null);
            } else {
                tab.browser = new McefOverlayBrowser(MCEFApi.getInstance().createBrowser(tab.url, transparent));
            }
            if (tab.browser == null) {
                throw new RuntimeException("Browser backend returned null");
            }
            if (!featherBackend) {
                installAdBlocker(tab);
                installDisplayHandler(tab);
                installLifeSpanHandler(tab);
            }
            if (tab == getActiveBrowserTab()) {
                resizeBrowser();
                tab.browser.setFocus(true);
            } else {
                tab.browser.resize(getBrowserRenderWidth(), getBrowserRenderHeight());
            }
            tab.pendingNavigationTicks = 0; // Directly created with target URL
            status = "Embedded browser started.";
            MinecraftOverlay.LOGGER.info("Successfully created embedded browser for {}", tab.url);
        } catch (Throwable exception) {
            browserFailed = true;
            browserError = exception.toString();
            status = "Could not start embedded browser: " + browserError;
            MinecraftOverlay.LOGGER.error("Failed to create embedded browser for {}", tab.url, exception);
        } finally {
            browserCreating = false;
        }
    }

    private String getBrowserStatus() {
        if (browserNeedsRestartAfterMcefReset) {
            return "Embedded browser failed: MCEF native files need a clean reinstall. Close the client fully, then start it again.";
        }
        if (browserFailed) {
            String err = browserError.isEmpty() ? "Embedded browser failed."
                    : "Embedded browser failed: " + browserError;
            if (MinecraftOverlay.isThirdPartyClientDetected() && !shouldUseFeatherBrowserBackend()) {
                err += "\n(MCEF Modern compatibility mode is active. Restart the client after replacing the mod jar.)";
            }
            return err;
        }
        if (shouldUseFeatherBrowserBackend(getActiveBrowserTab()))
            return browserRequested ? "Starting Feather browser engine..." : "Enter a URL and press Go.";
        if (browserInitialization == null)
            return browserRequested ? "Waiting for MCEF Modern..." : "Enter a URL and press Go.";
        float percentage = browserInitialization.getPercentage();
        String stageName = browserInitialization.getStage() != null ? browserInitialization.getStage().name()
                : "INITIALIZING";
        String suffix = percentage >= 0.0F ? " " + Math.round(percentage) + "%" : "";

        String result = "MCEF: " + stageName + suffix;
        if (percentage < 0) {
            result += " (Starting native engine...)";
        }
        return result;
    }

    private List<Path> loadScreenshots() {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
        if (!Files.isDirectory(dir))
            return List.of();
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.filter(MinecraftOverlayScreen::isImage)
                    .sorted(Comparator.comparing(MinecraftOverlayScreen::lastModified).reversed()).limit(48).toList();
        } catch (IOException exception) {
            MinecraftOverlay.LOGGER.warn("Failed to list screenshots", exception);
            return List.of();
        }
    }

    private void openScreenshotsFolder() {
        Path dir = MinecraftClient.getInstance().runDirectory.toPath().resolve("screenshots");
        try {
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toFile());
            status = "Opened screenshots folder.";
        } catch (IOException exception) {
            status = "Could not open screenshots folder.";
            MinecraftOverlay.LOGGER.warn("Failed to open screenshots folder", exception);
        }
    }

    private boolean selectScreenshotAt(double mouseX, double mouseY) {
        if (!screenshotsVisible || selectedScreenshot != null || screenshots.isEmpty())
            return false;
        int columns = getScreenshotColumns();
        int tileWidth = getScreenshotTileWidth(columns);
        int tileHeight = getScreenshotTileHeight();
        int startX = screenshotsWindow.x + 8;
        int startY = screenshotsWindow.y + HEADER_HEIGHT + 34;
        int visible = getVisibleScreenshotCount(columns, tileHeight);
        for (int i = 0; i < Math.min(visible, screenshots.size()); i++) {
            int col = i % columns;
            int row = i / columns;
            int tileX = startX + col * (tileWidth + 8);
            int tileY = startY + row * (tileHeight + 8);
            if (mouseX >= tileX && mouseX <= tileX + tileWidth && mouseY >= tileY && mouseY <= tileY + tileHeight) {
                openScreenshotEditor(screenshots.get(i));
                return true;
            }
        }
        return false;
    }

    private void openScreenshotEditor(Path screenshot) {
        selectedScreenshot = screenshot;
        screenshotDirty = false;
        reloadSelectedScreenshot();
        rebuild();
    }

    private void closeScreenshotEditor() {
        selectedScreenshot = null;
        editedScreenshot = null;
        screenshotDirty = false;
        destroyEditedScreenshotTexture();
        rebuild();
    }

    private void reloadSelectedScreenshot() {
        if (selectedScreenshot == null)
            return;
        try {
            editedScreenshot = ImageIO.read(selectedScreenshot.toFile());
            screenshotDirty = false;
            destroyEditedScreenshotTexture();
            status = "Loaded " + selectedScreenshot.getFileName();
        } catch (IOException exception) {
            status = "Could not load screenshot.";
            MinecraftOverlay.LOGGER.warn("Failed to load screenshot {}", selectedScreenshot, exception);
        }
    }

    private void saveEditedScreenshot() {
        if (selectedScreenshot == null || editedScreenshot == null)
            return;
        try {
            String format = screenshotFormat(selectedScreenshot);
            BufferedImage image = "jpg".equals(format) || "jpeg".equals(format) ? copyAsRgb(editedScreenshot)
                    : editedScreenshot;
            ImageIO.write(image, format, selectedScreenshot.toFile());
            screenshotDirty = false;
            Identifier cached = screenshotTextures.remove(selectedScreenshot);
            if (cached != null)
                client.getTextureManager().destroyTexture(cached);
            screenshots = loadScreenshots();
            status = "Saved " + selectedScreenshot.getFileName();
        } catch (IOException exception) {
            status = "Could not save screenshot.";
            MinecraftOverlay.LOGGER.warn("Failed to save screenshot {}", selectedScreenshot, exception);
        }
    }

    private void rotateEditedScreenshot() {
        if (editedScreenshot == null)
            return;
        BufferedImage rotated = new BufferedImage(editedScreenshot.getHeight(), editedScreenshot.getWidth(),
                BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = rotated.createGraphics();
        try {
            graphics.translate(rotated.getWidth(), 0);
            graphics.rotate(Math.PI / 2.0D);
            graphics.drawImage(editedScreenshot, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        editedScreenshot = rotated;
        screenshotDirty = true;
        destroyEditedScreenshotTexture();
    }

    private void adjustEditedScreenshotBrightness(float factor) {
        if (editedScreenshot == null)
            return;
        BufferedImage adjusted = new BufferedImage(editedScreenshot.getWidth(), editedScreenshot.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < editedScreenshot.getHeight(); y++) {
            for (int x = 0; x < editedScreenshot.getWidth(); x++) {
                int argb = editedScreenshot.getRGB(x, y);
                int alpha = argb & 0xFF000000;
                int red = clampColor(Math.round(((argb >> 16) & 0xFF) * factor));
                int green = clampColor(Math.round(((argb >> 8) & 0xFF) * factor));
                int blue = clampColor(Math.round((argb & 0xFF) * factor));
                adjusted.setRGB(x, y, alpha | (red << 16) | (green << 8) | blue);
            }
        }
        editedScreenshot = adjusted;
        screenshotDirty = true;
        destroyEditedScreenshotTexture();
    }

    private void grayscaleEditedScreenshot() {
        if (editedScreenshot == null)
            return;
        BufferedImage adjusted = new BufferedImage(editedScreenshot.getWidth(), editedScreenshot.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < editedScreenshot.getHeight(); y++) {
            for (int x = 0; x < editedScreenshot.getWidth(); x++) {
                int argb = editedScreenshot.getRGB(x, y);
                int gray = Math
                        .round(((argb >> 16) & 0xFF) * 0.299F + ((argb >> 8) & 0xFF) * 0.587F + (argb & 0xFF) * 0.114F);
                adjusted.setRGB(x, y, (argb & 0xFF000000) | (gray << 16) | (gray << 8) | gray);
            }
        }
        editedScreenshot = adjusted;
        screenshotDirty = true;
        destroyEditedScreenshotTexture();
    }

    private Identifier getEditedScreenshotTexture() {
        if (editedScreenshotTexture != null)
            return editedScreenshotTexture;
        if (editedScreenshot == null || selectedScreenshot == null)
            return null;
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(editedScreenshot, "png", output);
            NativeImage image = NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
            editedScreenshotTexture = Identifier.of(MinecraftOverlay.MOD_ID, "screenshot_edit/"
                    + Integer.toHexString(selectedScreenshot.toAbsolutePath().toString().hashCode()));
            client.getTextureManager().registerTexture(editedScreenshotTexture,
                    new NativeImageBackedTexture(() -> "Edited Screenshot", image));
            return editedScreenshotTexture;
        } catch (IOException exception) {
            MinecraftOverlay.LOGGER.warn("Failed to create screenshot editor texture", exception);
            return null;
        }
    }

    private void destroyEditedScreenshotTexture() {
        if (editedScreenshotTexture != null) {
            client.getTextureManager().destroyTexture(editedScreenshotTexture);
            editedScreenshotTexture = null;
        }
    }

    private static BufferedImage copyAsRgb(BufferedImage source) {
        BufferedImage rgb = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return rgb;
    }

    private static String screenshotFormat(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".jpg") || name.endsWith(".jpeg"))
            return "jpg";
        return "png";
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private void sendFriendMessage() {
        if (client.player == null || client.player.networkHandler == null) {
            status = "Join a world or server first.";
            return;
        }
        String friend = friendField.getText().trim();
        String message = messageField.getText().trim();
        if (friend.isEmpty() || message.isEmpty()) {
            status = "Enter a friend and message.";
            return;
        }
        boolean essentialDetected = MinecraftOverlay.isEssentialDetected();
        String command = essentialDetected ? "emsg " : "msg ";
        client.player.networkHandler.sendChatCommand(command + friend + " " + message);
        addEssentialHistory(friend + ": " + message);
        messageField.setText("");
        status = essentialDetected ? "Sent with /emsg." : "Essential not detected. Sent with /msg.";
        saveSettings();
    }

    private void addEssentialHistory(String entry) {
        essentialMessageHistory.add(0, entry);
        while (essentialMessageHistory.size() > 6)
            essentialMessageHistory.remove(essentialMessageHistory.size() - 1);
    }

    private void sendEssentialCommand(String command, String successStatus) {
        if (!MinecraftOverlay.isEssentialDetected()) {
            status = "Essential mod was not detected.";
            return;
        }
        if (client.player == null || client.player.networkHandler == null) {
            status = "Join a world or server first.";
            return;
        }
        client.player.networkHandler.sendChatCommand(command);
        status = successStatus;
    }

    private void inviteFriend() {
        if (friendField == null || friendField.getText().trim().isEmpty()) {
            status = "Enter a friend username first.";
            return;
        }
        sendEssentialCommand("einvite " + friendField.getText().trim(), "Sent Essential invite.");
    }

    private void clearNoteText() {
        getActiveNote().text = "";
        notesText = "";
        rebuild();
        saveSettings();
        status = "Notes cleared.";
    }

    private void insertNoteStamp() {
        NoteState note = getActiveNote();
        note.text = (note.text.isBlank() ? "" : "\n") + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMM d, h:mm a", Locale.ROOT)) + "\n" + note.text;
        notesText = note.text;
        rebuild();
        saveSettings();
        status = "Timestamp added.";
    }

    private void updateNotesText(NoteState note, String text) {
        note.text = text;
        if (note == getActiveNote())
            notesText = text;
        updateSharedHudState();
    }

    private void addNote() {
        int number = notes.size() + 1;
        NoteState last = getActiveNote();
        notes.add(new NoteState("Note " + number, "", 1, false, false, 1.0F, last.window.x + 20, last.window.y + 20,
                last.window.width, last.window.height));
        activeNoteIndex = notes.size() - 1;
        saveSettings();
        rebuild();
    }

    private void removeNote() {
        if (notes.size() <= 1) {
            clearNoteText();
            return;
        }
        notes.remove(activeNoteIndex);
        activeNoteIndex = Math.max(0, Math.min(activeNoteIndex, notes.size() - 1));
        notesText = getActiveNote().text;
        saveSettings();
        rebuild();
    }

    private void toggleNoteBold() {
        getActiveNote().bold = !getActiveNote().bold;
        saveSettings();
        rebuild();
    }

    private void toggleNoteItalic() {
        getActiveNote().italic = !getActiveNote().italic;
        saveSettings();
        rebuild();
    }

    private void adjustNoteTextSize(int delta) {
        NoteState note = getActiveNote();
        note.textSize = Math.max(1, Math.min(3, note.textSize + delta));
        saveSettings();
        rebuild();
    }

    private void adjustNoteOpacity(float delta) {
        NoteState note = getActiveNote();
        note.opacity += delta;
        if (note.opacity > 1.05F)
            note.opacity = 0.1F;
        note.opacity = Math.max(0.1F, Math.min(1.0F, note.opacity));
        saveSettings();
        rebuild();
    }

    @Override
    public void tick() {
        super.tick();
        if (shouldUseFeatherBrowserBackend())
            FeatherCefOverlayBrowser.pump();
        if (browserVisible && !browserFailed && browserInitialization == null && browserRequested) {
            startBrowserInitialization();
        }
        if (browserRequested && browserInitialization != null) {
            if (browserInitialization.getFuture().isCompletedExceptionally()) {
                browserFailed = true;
                status = getBrowserStatus();
            } else if (browserInitialization.getFuture().isDone() && !browserCreating && !browserFailed) {
                // Background initialization for non-visible tabs can be laggy on some clients.
                // We'll only initialize the active tab if it's not ready, and background tabs
                // only if visible.
                BrowserTabState activeTab = getActiveBrowserTab();
                if (activeTab.browser == null && browserVisible) {
                    createEmbeddedBrowser(activeTab);
                } else if (browserVisible) {
                    for (BrowserTabState tab : browserTabs) {
                        if (tab.browser == null) {
                            createEmbeddedBrowser(tab);
                            break; // Create one per tick
                        }
                    }
                }
            } else {
                status = getBrowserStatus();
            }
        }

        for (BrowserTabState tab : browserTabs) {
            if (tab.browser != null) {
                long now = System.nanoTime();
                boolean activeBrowser = tab == getActiveBrowserTab();
                boolean visibleBrowser = activeBrowser && browserVisible;
                boolean pinnedBrowser = activeBrowser && browserPinned;
                long urlPollInterval = visibleBrowser ? 250_000_000L : 1_000_000_000L;
                if (now - tab.lastUrlPollNanos > urlPollInterval) {
                    tab.lastUrlPollNanos = now;
                    pollBrowserTabUrl(tab);
                }
                if (visibleBrowser || pinnedBrowser || now - tab.lastFrameCheckNanos > 1_000_000_000L) {
                    tab.lastFrameCheckNanos = now;
                    AbstractTexture tex = getBrowserTexture(tab);
                    if (tex == null) {
                        if (tab.waitingForFrameSinceNanos == 0L) {
                            tab.waitingForFrameSinceNanos = now;
                        } else if (now - tab.waitingForFrameSinceNanos > 2_000_000_000L
                                && (now - tab.waitingForFrameSinceNanos) % 1_000_000_000L < 50_000_000L) {
                            if (activeBrowser)
                                resizeBrowser();
                            else
                                tab.browser.resize(getBrowserRenderWidth(), getBrowserRenderHeight());
                        }
                    } else {
                        tab.waitingForFrameSinceNanos = 0L;
                    }
                }
            }
            if (tab.browser != null && tab.pendingNavigationTicks > 0) {
                tab.pendingNavigationTicks--;
                if (tab.pendingNavigationTicks == 0) {
                    loadBrowserUrl(tab);
                }
            }
        }

        BrowserTabState activeTab = getActiveBrowserTab();
        repeatBrowserBackspace(activeTab);
        int currentSpotifySearchVersion = MinecraftOverlaySpotifyControls.getSearchVersion();
        if (spotifyVisible && spotifySearchVersion != currentSpotifySearchVersion
                && (spotifySearchField == null || !spotifySearchField.isFocused())) {
            spotifySearchVersion = currentSpotifySearchVersion;
            rebuild();
        }
    }

    private void repeatBrowserBackspace(BrowserTabState tab) {
        if (!browserBackspaceHeld || !shouldSendKeyToBrowser(tab))
            return;
        browserBackspaceRepeatTicks++;
        if (browserBackspaceRepeatTicks < 4 || browserBackspaceRepeatTicks % 2 != 0)
            return;
        sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_PRESSED, GLFW.GLFW_KEY_BACKSPACE, browserBackspaceModifiers);
        runBrowserBackspaceFallback(tab);
        tab.browser.setFocus(true);
    }

    private void loadBrowserUrl(BrowserTabState tab) {
        try {
            blockedAdRequests = 0;
            tab.browser.loadUrl(tab.url);
            status = "Loading " + tab.url;
            MinecraftOverlay.LOGGER.info("Navigated embedded browser to {}", tab.url);
        } catch (Throwable exception) {
            browserFailed = true;
            browserError = exception.toString();
            status = "Could not navigate browser: " + browserError;
            MinecraftOverlay.LOGGER.warn("Failed to navigate embedded browser", exception);
            closeBrowser(tab);
        }
    }

    private void updateBrowserTabUrl(BrowserTabState tab, String newUrl) {
        if (newUrl == null || newUrl.isBlank() || "about:blank".equals(newUrl))
            return;
        client.execute(() -> {
            tab.url = newUrl;
            if (tab == getActiveBrowserTab() && urlField != null) {
                urlField.setText(newUrl);
            }
        });
    }

    private void pollBrowserTabUrl(BrowserTabState tab) {
        try {
            String currentUrl = tab.browser.getUrl();
            if (currentUrl != null && !currentUrl.isBlank() && !"about:blank".equals(currentUrl)
                    && !currentUrl.equals(tab.url)) {
                updateBrowserTabUrl(tab, currentUrl);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float animation = getOverlayAnimationProgress();
        renderOverlayBackground(context, animation);
        context.getMatrices().pushMatrix();
        context.getMatrices().translate(0.0F, Math.round((1.0F - animation) * 10.0F));
        if (taskbarVertical) {
            context.fill(0, 0, SIDE_RAIL_WIDTH, height,
                    withAlpha(scaleRgb(getTabBackgroundColor(), 0.42F), 238));
            context.fill(6, 8, SIDE_RAIL_WIDTH - 6, height - 8,
                    withAlpha(scaleRgb(getTabBackgroundColor(), 0.62F), 224));
            context.fill(SIDE_RAIL_WIDTH - 1, 14, SIDE_RAIL_WIDTH, height - 14,
                    withAlpha(getAccentColor(), 110));
        } else {
            context.fill(0, height - 40, width, height,
                    withAlpha(scaleRgb(getTabBackgroundColor(), 0.62F), 236));
            context.fill(0, height - 40, width, height - 39,
                    withAlpha(getAccentColor(), 90));
        }
        context.fill(0, height - STATUS_HEIGHT, width, height,
                withAlpha(scaleRgb(getTabBackgroundColor(), 0.72F), 238));

        if (!browserVisible && browserRequested && browserInitialization != null
                && !browserInitialization.getFuture().isDone()) {
            // Keep status updated while initializing even if browser window is hidden
            status = getBrowserStatus();
        }
        renderWindowsInFocusOrder(context, mouseX, mouseY, delta);

        if (!status.isEmpty())
            context.drawTextWithShadow(textRenderer, status, taskbarVertical ? SIDE_RAIL_WIDTH + 12 : 12,
                    taskbarVertical ? height - 12 : height - 56, 0xFFBBBBBB);
        renderCompatibilityStatus(context);
        renderOverlayWidgets(context, mouseX, mouseY, delta);

        if (dragTarget != DragTarget.NONE || colorDragTarget != ColorDragTarget.NONE) {
            updateSharedHudState();
        }
        context.getMatrices().popMatrix();
    }

    private void renderCompatibilityStatus(DrawContext context) {
        String detectedClient = MinecraftOverlay.getDetectedClientName();
        if (!compatibilityMode && detectedClient.isEmpty())
            return;
        String text = compatibilityMode
                ? (detectedClient.isEmpty() ? "Compatibility mode" : detectedClient + " compatibility")
                : detectedClient + " detected";
        int textWidth = textRenderer.getWidth(text);
        context.drawTextWithShadow(textRenderer, text, Math.max(12, width - textWidth - 12), height - 43,
                compatibilityMode ? getAccentColor() : 0xFFBBBBBB);
    }

    private void renderWindowsInFocusOrder(DrawContext context, int mouseX, int mouseY, float delta) {
        for (Tab tab : WINDOW_ORDER) {
            if (tab != focusedTab)
                renderWindow(tab, context, mouseX, mouseY, delta);
        }
        renderWindow(focusedTab, context, mouseX, mouseY, delta);
    }

    private void renderWindow(Tab tab, DrawContext context, int mouseX, int mouseY, float delta) {
        if (!isTabVisible(tab))
            return;
        switch (tab) {
            case BROWSER -> renderBrowser(context);
            case SCREENSHOTS -> renderScreenshots(context);
            case FRIENDS -> renderFriends(context);
            case NOTES -> renderNotes(context);
            case CALCULATOR -> renderCalculator(context);
            case TIME -> renderTime(context);
            case SPOTIFY -> renderSpotify(context);
            case APPEARANCE -> renderAppearance(context, mouseX, mouseY, delta);
        }
    }

    private boolean isTabVisible(Tab tab) {
        return switch (tab) {
            case BROWSER -> browserVisible;
            case SCREENSHOTS -> screenshotsVisible;
            case FRIENDS -> friendsVisible;
            case NOTES -> notesVisible;
            case CALCULATOR -> calculatorVisible;
            case TIME -> timeVisible;
            case SPOTIFY -> spotifyVisible;
            case APPEARANCE -> appearanceVisible;
        };
    }

    private void renderOverlayBackground(DrawContext context, float animation) {
        int alpha = Math.round(222.0F * animation);
        context.fill(0, 0, width, height, withAlpha(0xFF0B0C10, alpha));
    }

    private float getOverlayAnimationProgress() {
        float raw = Math.min(1.0F, (System.nanoTime() - openedAtNanos) / 180_000_000.0F);
        return 1.0F - (float) Math.pow(1.0F - raw, 3.0D);
    }

    private void renderOverlayWidgets(DrawContext context, int mouseX, int mouseY, float delta) {
        var children = children();
        for (var child : children) {
            if (child instanceof ClickableWidget widget)
                widget.render(context, mouseX, mouseY, delta);
        }
    }

    private void addToolbarButton(String icon, String tooltip, int x, int y, boolean selected, Runnable action) {
        int buttonWidth = 28;
        int buttonHeight = 21;
        addDrawableChild(new OverlayToolbarButton(x, y, buttonWidth, buttonHeight, Text.literal(icon), tooltip, action,
                selected, taskbarVertical, this::getAccentColor, this::getTabBackgroundColor));
    }

    private void renderBrowser(DrawContext context) {
        renderWindowChrome(context, browserWindow, "Browser");
        renderBrowserTabs(context);
        BrowserTabState tab = getActiveBrowserTab();
        beginWindowClip(context, browserWindow);
        if (tab.browser == null) {
            context.drawWrappedTextWithShadow(textRenderer,
                    Text.literal(browserRequested ? getBrowserStatus()
                            : "Enter a URL and press Go to start the embedded browser."),
                    browserWindow.x + 8, browserWindow.y + HEADER_HEIGHT + 66, browserWindow.width - 16, 0xFFFFFFFF);
            if (browserInitialization != null && browserInitialization.getStage() != null) {
                String stage = "MCEF: " + browserInitialization.getStage().name() + " "
                        + Math.round(browserInitialization.getPercentage()) + "%";
                context.drawTextWithShadow(textRenderer, stage, browserWindow.x + 8,
                        browserWindow.y + HEADER_HEIGHT + 48, getAccentColor());
            }
            endWindowClip(context);
            return;
        }
        try {
            if (!RENDER_BROWSER_TEXTURE) {
                context.drawWrappedTextWithShadow(
                        textRenderer,
                        Text.literal("Browser is running, but frame rendering is disabled for crash testing."),
                        browserWindow.x + 8,
                        browserWindow.y + HEADER_HEIGHT + 66,
                        browserWindow.width - 16,
                        0xFFFFFFFF);
                return;
            }
            AbstractTexture texture = getBrowserTexture(tab);
            GpuTextureView textureView = tab.browser == null ? null : tab.browser.getTextureView();
            if (texture == null) {
                if (textureView == null) {
                    maybeFallbackFromFeatherBrowser(tab);
                    context.drawTextWithShadow(textRenderer,
                            tab.pendingNavigationTicks > 0 ? "Preparing embedded browser..."
                                    : "Browser started. Waiting for first frame...",
                            browserWindow.x + 8, browserWindow.y + HEADER_HEIGHT + 66, 0xFFFFFFFF);
                    return;
                }
            }
            tab.waitingForFrameSinceNanos = 0L;
            tab.loggedFeatherFrameWait = false;
            if (texture != null) {
                drawBrowserTexture(context, tab, texture);
            } else {
                drawBrowserTextureView(context, textureView, getBrowserX(), getBrowserY(), getBrowserWidth(),
                        getBrowserHeight());
            }
            if (blockedAdRequests > 0) {
                context.drawTextWithShadow(textRenderer, "Blocked " + blockedAdRequests + " ad requests",
                        getBrowserX() + 8, getBrowserY() + 8, 0xFFE6E6E6);
            }
        } catch (Throwable exception) {
            browserFailed = true;
            browserError = exception.toString();
            status = "Browser render failed: " + browserError;
            MinecraftOverlay.LOGGER.warn("Embedded browser render failed", exception);
            closeBrowser(tab);
        } finally {
            endWindowClip(context);
        }
    }

    private void renderBrowserTabs(DrawContext context) {
        int x = browserWindow.x + 5;
        int y = browserWindow.y + HEADER_HEIGHT + 23;
        int availableWidth = Math.max(80, browserWindow.width - 10);
        int tabWidth = Math.max(52, Math.min(108, availableWidth / Math.max(1, browserTabs.size())));
        for (int i = 0; i < browserTabs.size(); i++) {
            int tabX = x + i * tabWidth;
            if (tabX >= x + availableWidth)
                break;
            int right = Math.min(tabX + tabWidth - 2, x + availableWidth);
            boolean active = i == sharedActiveBrowserTab;
            context.fill(tabX, y, right, y + 14,
                    active ? scaleRgb(getTabBackgroundColor(), 1.25F) : getTabBackgroundColor());
            context.fill(tabX, y, right, y + 2, active ? getAccentColor() : scaleRgb(getTabBackgroundColor(), 1.55F));
            BrowserTabState tab = browserTabs.get(i);
            String label = textRenderer.trimToWidth((tab.pinned ? "* " : "") + tab.title(),
                    Math.max(20, right - tabX - 10));
            context.drawTextWithShadow(textRenderer, label, tabX + 5, y + 4, active ? 0xFFFFFFFF : 0xFFBBBBBB);
        }
    }

    private void drawBrowserTexture(DrawContext context, BrowserTabState tab, AbstractTexture texture) {
        if (texture == null)
            return;
        drawTextureQuad(context, tab, texture, getBrowserX(), getBrowserY(), getBrowserWidth(), getBrowserHeight());
    }

    private static void drawTextureQuad(DrawContext context, BrowserTabState tab, AbstractTexture texture, int x, int y,
            int width, int height) {
        if (texture == null || tab == null || width <= 0 || height <= 0)
            return;
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            if (tab.registeredTexture != texture) {
                client.getTextureManager().registerTexture(tab.textureIdentifier, texture);
                tab.registeredTexture = texture;
            }
            boolean flipY = tab.browser != null && tab.browser.isTextureVerticallyFlipped();
            context.drawTexturedQuad(tab.textureIdentifier, x, y, x + width, y + height, 0.0F, 1.0F,
                    flipY ? 1.0F : 0.0F, flipY ? 0.0F : 1.0F);
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to draw embedded browser texture", exception);
            context.fill(x, y, x + width, y + height, 0xFF15171C);
        }
    }

    private static void drawBrowserTextureView(DrawContext context, GpuTextureView view, int x, int y, int width,
            int height) {
        if (view == null || width <= 0 || height <= 0)
            return;
        MinecraftClient client = MinecraftClient.getInstance();
        ensureSamplerSourceTexture(client);
        AbstractTexture samplerSource = client.getTextureManager().getTexture(SAMPLER_SOURCE);
        GpuSampler sampler = samplerSource == null ? null : samplerSource.getSampler();
        if (sampler == null) {
            context.fill(x, y, x + width, y + height, 0xFF15171C);
            return;
        }
        try {
            Method method = getDrawTexturedQuadMethod();
            method.invoke(
                    context,
                    RenderPipelines.GUI_OPAQUE_TEX_BG,
                    view,
                    sampler,
                    x,
                    y,
                    x + width,
                    y + height,
                    0.0F,
                    0.0F,
                    1.0F,
                    1.0F,
                    -1);
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to draw embedded browser texture view", exception);
            context.fill(x, y, x + width, y + height, 0xFF15171C);
        }
    }

    private static Method getDrawTexturedQuadMethod() throws NoSuchMethodException {
        if (drawTexturedQuadMethod != null)
            return drawTexturedQuadMethod;
        Method found = null;
        for (Method method : DrawContext.class.getDeclaredMethods()) {
            if (!"drawTexturedQuad".equals(method.getName()))
                continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 13)
                continue;
            if (params[0] != RenderPipeline.class)
                continue;
            if (params[1] != GpuTextureView.class)
                continue;
            if (params[2] != GpuSampler.class)
                continue;
            found = method;
            break;
        }
        if (found == null) {
            throw new NoSuchMethodException("DrawContext.drawTexturedQuad(RenderPipeline,GpuTextureView,GpuSampler,...)");
        }
        found.setAccessible(true);
        drawTexturedQuadMethod = found;
        return found;
    }

    private static void ensureSamplerSourceTexture(MinecraftClient client) {
        if (samplerSourceRegistered || client == null)
            return;
        try {
            byte[] pixel = Base64.getDecoder().decode(
                    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAFgwJ/lL4t9QAAAABJRU5ErkJggg==");
            NativeImage image = NativeImage.read(new ByteArrayInputStream(pixel));
            client.getTextureManager().registerTexture(SAMPLER_SOURCE,
                    new NativeImageBackedTexture(() -> "Browser Sampler", image));
            samplerSourceRegistered = true;
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to create browser sampler texture", exception);
        }
    }

    private void maybeFallbackFromFeatherBrowser(BrowserTabState tab) {
        if (!(tab.browser instanceof FeatherCefOverlayBrowser) || tab.forceMcefBackend)
            return;
        long now = System.nanoTime();
        if (tab.waitingForFrameSinceNanos == 0L) {
            tab.waitingForFrameSinceNanos = now;
            return;
        }
        if (now - tab.waitingForFrameSinceNanos < 6_000_000_000L)
            return;
        if (!tab.loggedFeatherFrameWait) {
            tab.loggedFeatherFrameWait = true;
            MinecraftOverlay.LOGGER.warn("Feather browser is running but has not exposed a renderable frame yet for {}",
                    tab.url);
        }
        MinecraftOverlay.LOGGER.warn("Switching embedded browser from Feather FCEF to MCEF Modern for this session.");
        forceMcefBrowserBackend = true;
        for (BrowserTabState browserTab : browserTabs) {
            browserTab.forceMcefBackend = true;
            if (browserTab.browser instanceof FeatherCefOverlayBrowser) {
                closeBrowser(browserTab);
            }
        }
        browserFailed = false;
        browserError = "";
        browserCreating = false;
        status = "Feather browser did not provide a texture. Switching to MCEF Modern...";
        startBrowserInitialization();
    }

    private void beginWindowClip(DrawContext context, OverlayWindow window) {
        context.enableScissor(window.x + 2, window.y + HEADER_HEIGHT, window.x + window.width - 2,
                window.y + window.height - 2);
    }

    private void endWindowClip(DrawContext context) {
        context.disableScissor();
    }

    private void renderScreenshots(DrawContext context) {
        renderWindowChrome(context, screenshotsWindow, "Screenshots");
        beginWindowClip(context, screenshotsWindow);
        if (selectedScreenshot != null) {
            renderScreenshotEditor(context);
            endWindowClip(context);
            return;
        }
        context.drawTextWithShadow(textRenderer, "Recent Screenshots", screenshotsWindow.x + 8,
                screenshotsWindow.y + HEADER_HEIGHT + 10, 0xFFFFFFFF);
        if (screenshots.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No screenshots found.", screenshotsWindow.x + 8,
                    screenshotsWindow.y + HEADER_HEIGHT + 42, 0xFFCCCCCC);
            endWindowClip(context);
            return;
        }
        int columns = getScreenshotColumns();
        int tileWidth = getScreenshotTileWidth(columns);
        int tileHeight = getScreenshotTileHeight();
        int visible = getVisibleScreenshotCount(columns, tileHeight);
        int startX = screenshotsWindow.x + 8;
        int startY = screenshotsWindow.y + HEADER_HEIGHT + 34;
        for (int i = 0; i < Math.min(visible, screenshots.size()); i++) {
            Path screenshot = screenshots.get(i);
            int col = i % columns;
            int row = i / columns;
            int tileX = startX + col * (tileWidth + 8);
            int tileY = startY + row * (tileHeight + 8);
            int previewX = tileX;
            int previewY = tileY;
            int previewWidth = tileWidth;
            int previewHeight = Math.max(40, tileHeight - 34);
            context.fill(tileX - 1, tileY - 1, tileX + tileWidth + 1, tileY + tileHeight + 1,
                    withAlpha(scaleRgb(getTabBackgroundColor(), 1.3F), 170));
            Identifier texture = getScreenshotTexture(screenshot);
            if (texture != null) {
                context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF222222);
                context.drawTextWithShadow(textRenderer, "Screenshot", previewX + 8, previewY + previewHeight / 2 - 4, 0xFFAAAAAA);
            } else {
                context.fill(previewX, previewY, previewX + previewWidth, previewY + previewHeight, 0xFF222222);
                context.drawTextWithShadow(textRenderer, "No preview", previewX + 8, previewY + previewHeight / 2 - 4,
                        0xFFAAAAAA);
            }
            String name = textRenderer.trimToWidth(screenshot.getFileName().toString(), Math.max(40, tileWidth - 4));
            context.drawTextWithShadow(textRenderer, name, tileX + 2, tileY + previewHeight + 3, 0xFFDDDDDD);
        }
        endWindowClip(context);
    }

    private void renderScreenshotEditor(DrawContext context) {
        int top = screenshotsWindow.y + HEADER_HEIGHT + 28;
        int left = screenshotsWindow.x + 8;
        int areaWidth = Math.max(40, screenshotsWindow.width - 16);
        int areaHeight = Math.max(40, screenshotsWindow.height - HEADER_HEIGHT - 36);
        Identifier texture = getEditedScreenshotTexture();
        if (texture == null || editedScreenshot == null) {
            context.drawTextWithShadow(textRenderer, "Could not load screenshot.", left, top + 20, 0xFFFFAAAA);
            return;
        }
        int imageWidth = editedScreenshot.getWidth();
        int imageHeight = editedScreenshot.getHeight();
        double scale = Math.min(areaWidth / (double) Math.max(1, imageWidth),
                areaHeight / (double) Math.max(1, imageHeight));
        int drawWidth = Math.max(1, (int) Math.round(imageWidth * scale));
        int drawHeight = Math.max(1, (int) Math.round(imageHeight * scale));
        int drawX = left + (areaWidth - drawWidth) / 2;
        int drawY = top + (areaHeight - drawHeight) / 2;
        context.fill(left, top, left + areaWidth, top + areaHeight, 0x70000000);
        context.fill(drawX, drawY, drawX + drawWidth, drawY + drawHeight, 0xFF222222);
        context.drawTextWithShadow(textRenderer, "Image", drawX + drawWidth / 2 - 16, drawY + drawHeight / 2 - 4, 0xFFAAAAAA);
        String name = selectedScreenshot.getFileName().toString() + (screenshotDirty ? " *" : "");
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(name, areaWidth - 4), left + 2, top + 2,
                0xFFFFFFFF);
    }

    private void renderFriends(DrawContext context) {
        renderWindowChrome(context, friendsWindow, "Essential Friends");
        beginWindowClip(context, friendsWindow);
        int y = friendsWindow.y + HEADER_HEIGHT + 54;
        context.drawTextWithShadow(textRenderer,
                MinecraftOverlay.isEssentialDetected() ? "Essential messages" : "Essential not detected. Using /msg.",
                friendsWindow.x + 8, y, 0xFFCCCCCC);
        y += 16;
        if (essentialMessageHistory.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No sent messages yet.", friendsWindow.x + 8, y, 0xFF888888);
            endWindowClip(context);
            return;
        }
        for (int i = 0; i < Math.min(4, essentialMessageHistory.size()); i++) {
            String entry = textRenderer.trimToWidth(essentialMessageHistory.get(i),
                    Math.max(40, friendsWindow.width - 16));
            context.drawTextWithShadow(textRenderer, entry, friendsWindow.x + 8, y + i * 13, 0xFFDDDDDD);
        }
        endWindowClip(context);
    }

    private void renderNotes(DrawContext context) {
        for (int i = 0; i < notes.size(); i++) {
            if (i == activeNoteIndex)
                continue;
            NoteState note = notes.get(i);
            renderWindowChrome(context, note.window, note.title, note.opacity);
        }
        if (activeNoteIndex >= 0 && activeNoteIndex < notes.size()) {
            NoteState note = notes.get(activeNoteIndex);
            renderWindowChrome(context, note.window, note.title, note.opacity);
        }
    }

    private void renderCalculator(DrawContext context) {
        renderWindowChrome(context, calculatorWindow, "Calculator");
        beginWindowClip(context, calculatorWindow);
        int x = calculatorWindow.x + 8;
        int y = calculatorWindow.y + HEADER_HEIGHT + 32;
        int width = calculatorWindow.width - 16;
        context.fill(x, y, x + width, y + 16, withAlpha(scaleRgb(getTabBackgroundColor(), 1.18F), 210));
        context.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(calculatorResult, width - 8), x + 4, y + 5,
                "Error".equals(calculatorResult) ? 0xFFFFAAAA : 0xFFFFFFFF);
        endWindowClip(context);
    }

    private void renderTime(DrawContext context) {
        renderWindowChrome(context, timeWindow, "Clock");
        beginWindowClip(context, timeWindow);
        int x = timeWindow.x + 10;
        int y = timeWindow.y + HEADER_HEIGHT + 10;
        int width = timeWindow.width - 20;
        context.drawCenteredTextWithShadow(textRenderer, MinecraftOverlayTimeTools.clockText(),
                timeWindow.x + timeWindow.width / 2, y, 0xFFFFFFFF);
        int rowY = y + 24;
        int activeColor = getAccentColor();
        context.drawTextWithShadow(textRenderer, "Timer", x, rowY,
                MinecraftOverlayTimeTools.activeTool() == MinecraftOverlayTimeTools.Tool.TIMER ? activeColor
                        : 0xFFBBBBBB);
        context.drawTextWithShadow(textRenderer, MinecraftOverlayTimeTools.timerText(),
                x + width - textRenderer.getWidth(MinecraftOverlayTimeTools.timerText()), rowY, 0xFFFFFFFF);
        rowY += 16;
        context.drawTextWithShadow(textRenderer, "Stopwatch", x, rowY,
                MinecraftOverlayTimeTools.activeTool() == MinecraftOverlayTimeTools.Tool.STOPWATCH ? activeColor
                        : 0xFFBBBBBB);
        context.drawTextWithShadow(textRenderer, MinecraftOverlayTimeTools.stopwatchText(),
                x + width - textRenderer.getWidth(MinecraftOverlayTimeTools.stopwatchText()), rowY, 0xFFFFFFFF);
        endWindowClip(context);
    }

    private static final Map<String, SpotifyCoverTexture> SPOTIFY_COVER_CACHE = new HashMap<>();
    private static final Set<String> SPOTIFY_BAD_COVERS = new HashSet<>();

    private void renderSpotify(DrawContext context) {
        renderWindowChrome(context, spotifyWindow, "Spotify");
        beginWindowClip(context, spotifyWindow);
        int x = spotifyWindow.x + 8;
        int y = spotifyWindow.y + HEADER_HEIGHT + 8;
        boolean searchEnabled = config.spotifySearchBarEnabled;
        int albumSize = 48;
        int albumY = searchEnabled ? y + 26 : y;

        String coverArtBase64 = MinecraftOverlaySpotifyControls.getCoverArtBase64();
        SpotifyCoverTexture coverTexture = getSpotifyCoverTexture(client, coverArtBase64);

        if (coverTexture != null) {
            context.fill(x, albumY, x + albumSize, albumY + albumSize, 0xFF222222);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, coverTexture.identifier(), x, albumY, 0.0F, 0.0F,
                    albumSize, albumSize, coverTexture.width(), coverTexture.height(), coverTexture.width(),
                    coverTexture.height());
        } else {
            context.fill(x, albumY, x + albumSize, albumY + albumSize, 0x40000000);
            context.drawCenteredTextWithShadow(textRenderer, "\u266B", x + albumSize / 2, albumY + albumSize / 2 - 4,
                    0xFFFFFFFF);
        }

        // Track info
        int textX = x + albumSize + 8;
        int maxWidth = spotifyWindow.width - (textX - spotifyWindow.x) - 8;
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(MinecraftOverlaySpotifyControls.getTrackTitle().isBlank() ? "Not playing"
                        : MinecraftOverlaySpotifyControls.getTrackTitle(), maxWidth),
                textX, albumY + 2, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer,
                textRenderer.trimToWidth(MinecraftOverlaySpotifyControls.getTrackArtist(), maxWidth), textX, albumY + 14,
                0xFFAAAAAA);

        // Progress bar
        int barY = albumY + albumSize + 12;
        int barWidth = spotifyWindow.width - 16;
        int barHeight = 2;
        context.fill(x, barY, x + barWidth, barY + barHeight, 0x40FFFFFF);
        int progressWidth = (int) (barWidth * MinecraftOverlaySpotifyControls.getProgressRatio());
        context.fill(x, barY, x + progressWidth, barY + barHeight, 0xFFFFFFFF);
        context.fill(x + progressWidth - 2, barY - 1, x + progressWidth + 2, barY + 3, 0xFFFFFFFF);

        // Time labels
        String timeSummary = MinecraftOverlaySpotifyControls.getTimeSummary();
        String current = "0:00";
        String total = "0:00";
        if (timeSummary.contains(" / ")) {
            String[] parts = timeSummary.split(" / ");
            current = parts[0];
            total = parts[1];
        }
        context.drawTextWithShadow(textRenderer, current, x, barY + 6, 0xFFAAAAAA);
        context.drawTextWithShadow(textRenderer, total, x + barWidth - textRenderer.getWidth(total), barY + 6,
                0xFFAAAAAA);
        if (searchEnabled) {
            int statusY = MinecraftOverlaySpotifyControls.getSearchResults().isEmpty()
                    ? barY + 20
                    : spotifyWindow.y + spotifyWindow.height - 64;
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(MinecraftOverlaySpotifyControls.getSearchStatus(), barWidth), x, statusY,
                    0xFFAAAAAA);
        }

        endWindowClip(context);
    }

    private static SpotifyCoverTexture getSpotifyCoverTexture(MinecraftClient client, String coverArtBase64) {
        if (client == null || coverArtBase64 == null || coverArtBase64.isBlank())
            return null;
        if (SPOTIFY_BAD_COVERS.contains(coverArtBase64))
            return null;
        SpotifyCoverTexture coverTexture = SPOTIFY_COVER_CACHE.get(coverArtBase64);
        if (coverTexture != null)
            return coverTexture;
        try {
            byte[] bytes = Base64.getDecoder().decode(coverArtBase64);
            NativeImage image = readSpotifyCoverImage(bytes);
            Identifier coverId = Identifier.of(MinecraftOverlay.MOD_ID,
                    "spotify_cover/" + Integer.toHexString(coverArtBase64.hashCode()));
            coverTexture = new SpotifyCoverTexture(coverId, image.getWidth(), image.getHeight());
            client.getTextureManager().registerTexture(coverId,
                    new NativeImageBackedTexture(() -> "Spotify Cover Art", image));
            SPOTIFY_COVER_CACHE.put(coverArtBase64, coverTexture);
            if (SPOTIFY_COVER_CACHE.size() > 5) {
                for (String key : new ArrayList<>(SPOTIFY_COVER_CACHE.keySet())) {
                    if (key.equals(coverArtBase64))
                        continue;
                    SpotifyCoverTexture oldTexture = SPOTIFY_COVER_CACHE.remove(key);
                    if (oldTexture != null)
                        client.getTextureManager().destroyTexture(oldTexture.identifier());
                    break;
                }
            }
            return coverTexture;
        } catch (Exception exception) {
            SPOTIFY_BAD_COVERS.add(coverArtBase64);
            MinecraftOverlay.LOGGER.warn(
                    "Failed to decode Spotify cover art once; this cover will be skipped until the track changes.",
                    exception);
            return null;
        }
    }

    private static NativeImage readSpotifyCoverImage(byte[] bytes) throws IOException {
        try {
            return NativeImage.read(new ByteArrayInputStream(bytes));
        } catch (IOException pngException) {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(bytes));
            if (bufferedImage == null)
                throw pngException;
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", output);
            return NativeImage.read(new ByteArrayInputStream(output.toByteArray()));
        }
    }

    private void exportDebugInfo() {
        try {
            Path debugFile = MinecraftClient.getInstance().runDirectory.toPath().resolve("overlay_debug.txt");
            StringBuilder sb = new StringBuilder();
            sb.append("Minecraft Overlay Debug Info\n");
            sb.append("============================\n");
            sb.append("Client: ").append(MinecraftOverlay.getDetectedClientName()).append("\n");
            sb.append("OS: ").append(System.getProperty("os.name")).append(" (")
                    .append(System.getProperty("os.version")).append(")\n");
            sb.append("Java: ").append(System.getProperty("java.version")).append("\n");
            sb.append("User Dir: ").append(System.getProperty("user.dir")).append("\n");
            sb.append("Library Path: ").append(System.getProperty("java.library.path")).append("\n\n");

            sb.append("System Properties:\n");
            System.getProperties().forEach((k, v) -> {
                String key = String.valueOf(k);
                if (key.contains("jcef") || key.contains("mcef") || key.contains("lunar") || key.contains("feather")
                        || key.contains("java.library.path")) {
                    sb.append(k).append("=").append(v).append("\n");
                }
            });

            Files.writeString(debugFile, sb.toString());
            status = "Debug info exported to overlay_debug.txt";
        } catch (IOException e) {
            status = "Failed to export debug info: " + e.getMessage();
        }
    }

    private void renderAppearance(DrawContext context, int mouseX, int mouseY, float delta) {
        renderWindowChrome(context, appearanceWindow, "Settings");
        int x = appearanceWindow.x + 8;
        int y = appearanceWindow.y + HEADER_HEIGHT + 28;
        int pickerWidth = appearanceWindow.width - 16;
        int pickerHeight = getSettingsPickerHeight();

        // Add Debug Info button at the top of settings
        int debugY = appearanceWindow.y + HEADER_HEIGHT + 4;
        boolean hoveringDebug = mouseX >= x && mouseX <= x + pickerWidth && mouseY >= debugY && mouseY <= debugY + 20;
        context.fill(x, debugY, x + pickerWidth, debugY + 20, hoveringDebug ? 0x60FFFFFF : 0x30FFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, "Export Browser Debug Info", x + pickerWidth / 2, debugY + 6,
                0xFFFFFFFF);

        if (appearanceVisible && hoveringDebug
                && GLFW.glfwGetMouseButton(MinecraftClient.getInstance().getWindow().getHandle(),
                        GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS) {
            exportDebugInfo();
        }

        int sliderY = y + pickerHeight + 16;
        drawColorGradient(context, x, y, pickerWidth, pickerHeight);
        drawHueSlider(context, x, sliderY, pickerWidth);
        int previewY = sliderY + 20;
        int color = getEditedColor();
        context.fill(x, previewY, x + 42, previewY + 18, color);
        context.drawTextWithShadow(textRenderer, colorMode == ColorMode.ACCENT ? "Accent color" : "Tab background",
                x + 50, previewY + 5, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, String.format("#%06X", color & 0xFFFFFF), x + pickerWidth - 64,
                previewY + 5, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, "Browser resolution " + Math.round(browserPixelScale * 100.0D) + "%",
                x + 58, appearanceWindow.y + appearanceWindow.height - 19, 0xFFDDDDDD);
        int keyY = appearanceWindow.y + HEADER_HEIGHT + 130;
        context.drawTextWithShadow(textRenderer, "Keybinds", x, keyY - 14, 0xFFFFFFFF);
        for (int i = 0; i < 6; i++) {
            int rowY = keyY + i * 19;
            context.drawTextWithShadow(textRenderer, keybindLabel(i), x, rowY + 4,
                    awaitingKeybind == i ? getAccentColor() : 0xFFDDDDDD);
            context.drawTextWithShadow(textRenderer,
                    textRenderer.trimToWidth(keyComboName(getKeybind(i), getSecondKeybind(i)),
                            Math.max(60, appearanceWindow.width - 108)),
                    x + 90, rowY + 4, 0xFFAAAAAA);
        }
        int markerX = x + Math.round(getEditedSaturation() * pickerWidth);
        int markerY = y + Math.round((1.0F - getEditedBrightness()) * pickerHeight);
        context.fill(markerX - 3, markerY - 3, markerX + 4, markerY + 4, 0xFFFFFFFF);
        context.fill(markerX - 2, markerY - 2, markerX + 3, markerY + 3, 0xFF000000);
        int hueMarkerX = x + Math.round((getEditedHue() / 360.0F) * pickerWidth);
        context.fill(hueMarkerX - 2, sliderY - 3, hueMarkerX + 3, sliderY + 11, 0xFFFFFFFF);
    }

    private int getSettingsPickerHeight() {
        return Math.max(42, Math.min(72, appearanceWindow.height - HEADER_HEIGHT - 188));
    }

    private void drawColorGradient(DrawContext context, int x, int y, int width, int height) {
        int cell = 4;
        for (int row = 0; row < height; row += cell) {
            float b = 1.0F - (float) row / Math.max(1, height);
            for (int col = 0; col < width; col += cell) {
                float s = (float) col / Math.max(1, width);
                context.fill(x + col, y + row, x + Math.min(width, col + cell), y + Math.min(height, row + cell),
                        hsbToRgb(getEditedHue(), s, b));
            }
        }
        int hueColor = hsbToRgb(getEditedHue(), 1.0F, 1.0F);
        context.fill(x, y, x + width, y + 1, 0xFFFFFFFF);
        context.fill(x, y + height - 1, x + width, y + height, 0xFF000000);
        context.fill(x, y, x + 1, y + height, 0xFFFFFFFF);
        context.fill(x + width - 1, y, x + width, y + height, hueColor);
    }

    private void drawHueSlider(DrawContext context, int x, int y, int width) {
        int cell = 3;
        for (int col = 0; col < width; col += cell) {
            float sliderHue = ((float) col / Math.max(1, width)) * 360.0F;
            context.fill(x + col, y, x + Math.min(width, col + cell), y + 8, hsbToRgb(sliderHue, 1.0F, 1.0F));
        }
    }

    private void renderWindowChrome(DrawContext context, OverlayWindow window, String title) {
        renderWindowChrome(context, window, title, sharedTabOpacity);
    }

    private void renderWindowChrome(DrawContext context, OverlayWindow window, String title, float opacity) {
        int alpha = Math.round(222 * opacity);
        context.fill(window.x, window.y, window.x + window.width, window.y + window.height,
                withAlpha(scaleRgb(getTabBackgroundColor(), 0.38F), alpha));
        context.fill(window.x, window.y, window.x + window.width, window.y + HEADER_HEIGHT,
                withAlpha(scaleRgb(getTabBackgroundColor(), 0.52F), Math.round(246 * opacity)));
        context.fill(window.x, window.y, window.x + window.width, window.y + 2,
                withAlpha(getAccentColor(), Math.round(255 * opacity)));
        context.fill(window.x, window.y, window.x + window.width, window.y + 1,
                withAlpha(scaleRgb(getAccentColor(), 1.42F), Math.round(230 * opacity)));
        context.drawTextWithShadow(textRenderer, title, window.x + 6, window.y + 6, 0xFFFFFFFF);

        boolean pinned = isWindowPinned(title);
        int iconX = window.x + window.width - 16;
        int pinX = iconX - 16;
        int iconY = window.y + 5;
        context.drawTextWithShadow(textRenderer, "✖", iconX, iconY, 0xFFFFFFFF);
        context.drawTextWithShadow(textRenderer, pinned ? "📌" : "📍", pinX - 4, iconY,
                pinned ? getAccentColor() : 0xFFFFFFFF);

        context.fill(window.x, window.y + window.height - 1, window.x + window.width, window.y + window.height,
                withAlpha(getAccentColor(), 135));
        context.fill(window.x, window.y, window.x + 1, window.y + window.height,
                withAlpha(getAccentColor(), 110));
        context.fill(window.x + window.width - 1, window.y, window.x + window.width, window.y + window.height,
                withAlpha(getAccentColor(), 70));
        int handleLeft = window.x + window.width - RESIZE_HANDLE;
        int handleTop = window.y + window.height - RESIZE_HANDLE;
        context.fill(handleLeft, handleTop + 6, handleLeft + RESIZE_HANDLE, handleTop + RESIZE_HANDLE,
                withAlpha(getAccentColor(), 210));
        context.fill(handleLeft + 4, handleTop + 3, handleLeft + RESIZE_HANDLE, handleTop + 4, 0xFF888888);
        context.fill(handleLeft + 6, handleTop + 1, handleLeft + RESIZE_HANDLE, handleTop + 2, 0xFF888888);
        context.fill(window.x, window.y, window.x + RESIZE_HANDLE, window.y + 2, withAlpha(getAccentColor(), 170));
        context.fill(window.x + window.width - RESIZE_HANDLE, window.y, window.x + window.width, window.y + 2,
                withAlpha(getAccentColor(), 170));
        context.fill(window.x, window.y + window.height - 2, window.x + RESIZE_HANDLE, window.y + window.height,
                withAlpha(getAccentColor(), 170));
    }

    private boolean isWindowPinned(String title) {
        if (notesPinned) {
            for (NoteState note : notes) {
                if (note.title.equals(title))
                    return true;
            }
        }
        return switch (title) {
            case "Browser" -> browserPinned;
            case "Screenshots" -> screenshotsPinned;
            case "Friends" -> friendsPinned;
            case "Notes" -> notesPinned;
            case "Calculator" -> calculatorPinned;
            case "Clock" -> timePinned;
            case "Spotify" -> spotifyPinned;
            case "Appearance", "Settings" -> appearancePinned;
            default -> false;
        };
    }

    private Identifier getScreenshotTexture(Path screenshot) {
        Identifier existing = screenshotTextures.get(screenshot);
        if (existing != null)
            return existing;
        try (InputStream stream = Files.newInputStream(screenshot)) {
            NativeImage image = NativeImage.read(stream);
            NativeImageBackedTexture texture = new NativeImageBackedTexture(
                    () -> String.valueOf(screenshot.getFileName()), image);
            Identifier id = Identifier.of(MinecraftOverlay.MOD_ID,
                    "screenshot/" + Integer.toHexString(screenshot.toAbsolutePath().toString().hashCode()));
            client.getTextureManager().registerTexture(id, texture);
            screenshotTextures.put(screenshot, id);
            return id;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private int getScreenshotColumns() {
        return Math.max(1, Math.min(4, (screenshotsWindow.width - 16) / 118));
    }

    private int getScreenshotTileWidth(int columns) {
        return Math.max(72, (screenshotsWindow.width - 16 - (columns - 1) * 8) / Math.max(1, columns));
    }

    private int getScreenshotTileHeight() {
        return Math.max(84, Math.min(112, (screenshotsWindow.height - HEADER_HEIGHT - 48) / 2));
    }

    private int getVisibleScreenshotCount(int columns, int tileHeight) {
        int rows = Math.max(1, (screenshotsWindow.height - HEADER_HEIGHT - 44) / Math.max(1, tileHeight + 8));
        return Math.max(1, columns * rows);
    }

    private int getAccentColor() {
        return hsbToRgb(hue, saturation, brightness);
    }

    private int getTabBackgroundColor() {
        return hsbToRgb(tabHue, tabSaturation, tabBrightness);
    }

    private int getEditedColor() {
        return hsbToRgb(getEditedHue(), getEditedSaturation(), getEditedBrightness());
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

    private static int hsbToRgb(float hue, float saturation, float brightness) {
        return 0xFF000000 | (Color.HSBtoRGB(hue / 360.0F, saturation, brightness) & 0xFFFFFF);
    }

    private static int scaleRgb(int color, float scale) {
        int alpha = color & 0xFF000000;
        int red = Math.min(255, Math.max(0, Math.round(((color >> 16) & 0xFF) * scale)));
        int green = Math.min(255, Math.max(0, Math.round(((color >> 8) & 0xFF) * scale)));
        int blue = Math.min(255, Math.max(0, Math.round((color & 0xFF) * scale)));
        return alpha | (red << 16) | (green << 8) | blue;
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0xFFFFFF);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        saveSettings();
        closeScreenshotTextures();
        client.setScreen(parent);
    }

    @Override
    public void removed() {
        saveSettings();
        closeScreenshotTextures();
    }

    private void closeBrowser(BrowserTabState tab) {
        if (tab.browser != null) {
            try {
                tab.browser.close();
            } catch (Throwable exception) {
                MinecraftOverlay.LOGGER.warn("Failed to close embedded browser", exception);
            }
            tab.browser = null;
        }
        closeBrowserTextureView(tab);
        tab.pendingNavigationTicks = 0;
        tab.waitingForFrameSinceNanos = 0L;
        tab.loggedFeatherFrameWait = false;
    }

    private AbstractTexture getBrowserTexture(BrowserTabState tab) {
        if (tab.browser == null)
            return null;
        return tab.browser.getTexture();
    }

    private void closeBrowserTextureView(BrowserTabState tab) {
        if (tab.registeredTexture == null)
            return;
        try {
            client.getTextureManager().destroyTexture(tab.textureIdentifier);
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to destroy browser texture", exception);
        }
        tab.registeredTexture = null;
    }

    private void closeScreenshotTextures() {
        for (Identifier id : screenshotTextures.values())
            client.getTextureManager().destroyTexture(id);
        screenshotTextures.clear();
        destroyEditedScreenshotTexture();
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        clampWindows();
        saveSettings();
        rebuild();
        resizeBrowser();
    }

    private int getBrowserX() {
        return browserWindow.x + 5;
    }

    private int getBrowserY() {
        return browserWindow.y + HEADER_HEIGHT + 42;
    }

    private int getBrowserWidth() {
        return Math.max(1, browserWindow.width - 10);
    }

    private int getBrowserHeight() {
        return Math.max(1, browserWindow.height - HEADER_HEIGHT - 47);
    }

    private boolean isInBrowserBounds(double x, double y) {
        return x >= getBrowserX() && y >= getBrowserY() && x < getBrowserX() + getBrowserWidth()
                && y < getBrowserY() + getBrowserHeight();
    }

    private int browserMouseX(double x) {
        return (int) ((x - getBrowserX()) * getBrowserInputScale());
    }

    private int browserMouseY(double y) {
        return (int) ((y - getBrowserY()) * getBrowserInputScale());
    }

    private double getBrowserInputScale() {
        return client.getWindow().getScaleFactor() * browserPixelScale;
    }

    private void resizeBrowser() {
        BrowserTabState tab = getActiveBrowserTab();
        if (tab.browser != null) {
            tab.browser.resize(getBrowserRenderWidth(), getBrowserRenderHeight());
        }
    }

    private int getBrowserRenderWidth() {
        return Math.max(1,
                (int) Math.round(getBrowserWidth() * client.getWindow().getScaleFactor() * browserPixelScale));
    }

    private int getBrowserRenderHeight() {
        return Math.max(1,
                (int) Math.round(getBrowserHeight() * client.getWindow().getScaleFactor() * browserPixelScale));
    }

    @Override
    public boolean mouseClicked(Click event, boolean isDoubleClick) {
        if (handleWindowHeaderClick(event.x(), event.y()))
            return true;
        if (selectBrowserTab(event.x(), event.y())) {
            setBrowserKeyboardFocused(getActiveBrowserTab(), false);
            return true;
        }
        if (selectScreenshotAt(event.x(), event.y())) {
            setBrowserKeyboardFocused(getActiveBrowserTab(), false);
            return true;
        }
        colorDragTarget = getColorDragTarget(event.x(), event.y());
        if (colorDragTarget != ColorDragTarget.NONE) {
            focusedTab = Tab.APPEARANCE;
            setBrowserKeyboardFocused(getActiveBrowserTab(), false);
            updateColorFromMouse(event.x(), event.y());
            return true;
        }
        dragTarget = getDragTarget(event.x(), event.y());
        if (dragTarget != DragTarget.NONE) {
            Tab previousFocus = focusedTab;
            focusedTab = dragTarget.getTab();
            if (previousFocus != focusedTab)
                rebuild();
            setBrowserKeyboardFocused(getActiveBrowserTab(), false);
            return true;
        }
        boolean handled = super.mouseClicked(event, isDoubleClick);
        BrowserTabState tab = getActiveBrowserTab();
        if (handled || !browserVisible || tab.browser == null || !isInBrowserBounds(event.x(), event.y())) {
            setBrowserKeyboardFocused(tab, false);
            return handled;
        }
        focusedTab = Tab.BROWSER;
        setFocused(null);
        tab.browser.onMouseClicked(new Click(browserMouseX(event.x()), browserMouseY(event.y()), event.buttonInfo()),
                isDoubleClick);
        setBrowserKeyboardFocused(tab, true);
        return true;
    }

    @Override
    public boolean mouseReleased(Click event) {
        boolean saveAfterRelease = dragTarget != DragTarget.NONE || colorDragTarget != ColorDragTarget.NONE;
        dragTarget = DragTarget.NONE;
        colorDragTarget = ColorDragTarget.NONE;
        if (saveAfterRelease)
            saveSettings();
        boolean handled = super.mouseReleased(event);
        BrowserTabState tab = getActiveBrowserTab();
        if (handled || !browserVisible || tab.browser == null || !isInBrowserBounds(event.x(), event.y()))
            return handled;
        tab.browser.onMouseReleased(new Click(browserMouseX(event.x()), browserMouseY(event.y()), event.buttonInfo()));
        if (browserKeyboardFocused)
            tab.browser.setFocus(true);
        return true;
    }

    @Override
    public boolean mouseDragged(Click event, double dragX, double dragY) {
        if (colorDragTarget != ColorDragTarget.NONE) {
            updateColorFromMouse(event.x(), event.y());
            return true;
        }
        if (dragTarget != DragTarget.NONE) {
            OverlayWindow window = getDraggedWindow();
            boolean resizing = !dragTarget.isMove();
            if (dragTarget.isMove()) {
                window.x += (int) dragX;
                window.y += (int) dragY;
            } else {
                if (resizingLeft) {
                    window.x += (int) dragX;
                    window.width -= (int) dragX;
                }
                if (resizingRight) {
                    window.width += (int) dragX;
                }
                if (resizingTop) {
                    window.y += (int) dragY;
                    window.height -= (int) dragY;
                }
                if (resizingBottom) {
                    window.height += (int) dragY;
                }
            }
            window.clamp(width, height);
            rebuild();
            if (window == browserWindow && resizing)
                resizeBrowser();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        BrowserTabState tab = getActiveBrowserTab();
        if (browserVisible && tab.browser != null && isInBrowserBounds(mouseX, mouseY))
            tab.browser.onMouseMoved(browserMouseX(mouseX), browserMouseY(mouseY));
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        boolean handled = super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        BrowserTabState tab = getActiveBrowserTab();
        if (handled || !browserVisible || tab.browser == null || !isInBrowserBounds(mouseX, mouseY))
            return handled;
        focusedTab = Tab.BROWSER;
        tab.browser.onMouseScrolled(browserMouseX(mouseX), browserMouseY(mouseY), scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput event) {
        if (awaitingKeybind >= 0) {
            captureKeybind(event.key());
            return true;
        }
        if (browserVisible && urlField != null && urlField.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            openUrl();
            setFocused(null);
            return true;
        }
        if (messageField != null && messageField.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            sendFriendMessage();
            return true;
        }
        if (calculatorField != null && calculatorField.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            calculatorExpression = calculatorField.getText();
            evaluateCalculator();
            saveSettings();
            rebuild();
            return true;
        }
        if (spotifySearchField != null && spotifySearchField.isFocused()
                && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            searchSpotify();
            return true;
        }
        BrowserTabState tab = getActiveBrowserTab();
        if (shouldSendKeyToBrowser(tab)) {
            sendBrowserKeyPressed(tab, event);
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                browserBackspaceHeld = true;
                browserBackspaceRepeatTicks = 0;
                browserBackspaceModifiers = event.modifiers();
            }
            tab.browser.setFocus(true);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyInput event) {
        BrowserTabState tab = getActiveBrowserTab();
        if (shouldSendKeyToBrowser(tab)) {
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                tab.browser.setFocus(true);
                return true;
            }
            sendBrowserKeyReleased(tab, event);
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                browserBackspaceHeld = false;
                browserBackspaceRepeatTicks = 0;
            }
            tab.browser.setFocus(true);
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharInput event) {
        BrowserTabState tab = getActiveBrowserTab();
        if (shouldSendKeyToBrowser(tab)) {
            tab.browser.onCharTyped(event);
            tab.browser.setFocus(true);
            return true;
        }
        return super.charTyped(event);
    }

    private boolean shouldSendKeyToBrowser(BrowserTabState tab) {
        return browserVisible && browserKeyboardFocused && focusedTab == Tab.BROWSER && tab.browser != null
                && !isOverlayTextFieldFocused();
    }

    private boolean isOverlayTextFieldFocused() {
        return (urlField != null && urlField.isFocused())
                || (friendField != null && friendField.isFocused())
                || (messageField != null && messageField.isFocused())
                || (calculatorField != null && calculatorField.isFocused())
                || (getFocused() instanceof NotesEditorWidget);
    }

    private void setBrowserKeyboardFocused(BrowserTabState tab, boolean focused) {
        browserKeyboardFocused = focused;
        if (!focused) {
            browserBackspaceHeld = false;
            browserBackspaceRepeatTicks = 0;
        }
        if (tab.browser != null) {
            tab.browser.setFocus(focused);
        }
    }

    private void sendBrowserKeyPressed(BrowserTabState tab, KeyInput event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_PRESSED, event.key(), event.modifiers());
            sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_TYPED, event.key(), event.modifiers());
            sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_RELEASED, event.key(), event.modifiers());
            runBrowserEnterSubmitFallback(tab);
            return;
        }
        if (isBrowserNativeSpecialKey(event.key())) {
            sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_PRESSED, event.key(), event.modifiers());
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_TYPED, event.key(), event.modifiers());
                runBrowserBackspaceFallback(tab);
            }
        } else {
            tab.browser.onKeyPressed(event);
        }
    }

    private void sendBrowserKeyReleased(BrowserTabState tab, KeyInput event) {
        if (isBrowserNativeSpecialKey(event.key())) {
            sendBrowserNativeKeyEvent(tab, KeyEvent.KEY_RELEASED, event.key(), event.modifiers());
        } else {
            tab.browser.onKeyReleased(event);
        }
    }

    private boolean isBrowserNativeSpecialKey(int key) {
        return key == GLFW.GLFW_KEY_BACKSPACE;
    }

    private void sendBrowserNativeKeyEvent(BrowserTabState tab, int eventId, int glfwKey, int modifiers) {
        try {
            tab.browser.sendNativeKeyEvent(eventId, glfwKey, modifiers);
        } catch (Throwable exception) {
            if (!browserNativeKeyWarningLogged) {
                browserNativeKeyWarningLogged = true;
                MinecraftOverlay.LOGGER.warn("Native browser key event path failed; using browser wrapper fallback",
                        exception);
            }
            if (eventId == KeyEvent.KEY_RELEASED) {
                tab.browser.onKeyReleased(new KeyInput(glfwKey, 0, modifiers));
            } else if (eventId == KeyEvent.KEY_TYPED) {
                tab.browser.onCharTyped(new CharInput(toAwtKeyChar(glfwKey), modifiers));
            } else {
                tab.browser.onKeyPressed(new KeyInput(glfwKey, 0, modifiers));
            }
        }
    }

    private void runBrowserBackspaceFallback(BrowserTabState tab) {
        try {
            String script = """
                    (function(){
                      const e = document.activeElement;
                      if (!e || e === document.body) return;
                      if (e.isContentEditable) {
                        document.execCommand('delete', false, null);
                        return;
                      }
                      if (typeof e.value !== 'string') return;
                      const start = e.selectionStart == null ? e.value.length : e.selectionStart;
                      const end = e.selectionEnd == null ? start : e.selectionEnd;
                      if (start !== end) {
                        e.value = e.value.slice(0, start) + e.value.slice(end);
                        e.selectionStart = e.selectionEnd = start;
                      } else if (start > 0) {
                        e.value = e.value.slice(0, start - 1) + e.value.slice(start);
                        e.selectionStart = e.selectionEnd = start - 1;
                      }
                      e.dispatchEvent(new InputEvent('input', {bubbles:true, inputType:'deleteContentBackward'}));
                    })();
                    """;
            tab.browser.executeJavaScript(script, tab.url, 0);
        } catch (Throwable exception) {
            if (!browserNativeKeyWarningLogged) {
                browserNativeKeyWarningLogged = true;
                MinecraftOverlay.LOGGER.warn("Browser backspace fallback failed", exception);
            }
        }
    }

    private void runBrowserEnterSubmitFallback(BrowserTabState tab) {
        try {
            String script = """
                    (function(){
                      const e = document.activeElement;
                      if (!e || e === document.body) return;
                      const keydown = new KeyboardEvent('keydown', {key:'Enter', code:'Enter', bubbles:true, cancelable:true});
                      const canceled = !e.dispatchEvent(keydown);
                      e.dispatchEvent(new KeyboardEvent('keyup', {key:'Enter', code:'Enter', bubbles:true, cancelable:true}));
                      if (canceled) return;
                      const form = e.form;
                      if (form) {
                        if (typeof form.requestSubmit === 'function') form.requestSubmit();
                        else form.submit();
                        return;
                      }
                      const root = e.closest && e.closest('form,[role="search"],[role="textbox"],[contenteditable="true"]');
                      const button = root && root.querySelector && root.querySelector('button[type="submit"],button[aria-label*="Send" i],button[title*="Send" i],button[aria-label*="Search" i],button[title*="Search" i]');
                      if (button) button.click();
                    })();
                    """;
            tab.browser.executeJavaScript(script, tab.url, 0);
        } catch (Throwable exception) {
            if (!browserNativeKeyWarningLogged) {
                browserNativeKeyWarningLogged = true;
                MinecraftOverlay.LOGGER.warn("Browser enter submit fallback failed", exception);
            }
        }
    }

    private static char toAwtKeyChar(int glfwKey) {
        return glfwKey == GLFW.GLFW_KEY_BACKSPACE ? '\b' : '\n';
    }

    private OverlayWindow getDraggedWindow() {
        if (dragTarget == DragTarget.BROWSER_MOVE || dragTarget == DragTarget.BROWSER_RESIZE)
            return browserWindow;
        if (dragTarget == DragTarget.SCREENSHOTS_MOVE || dragTarget == DragTarget.SCREENSHOTS_RESIZE)
            return screenshotsWindow;
        if (dragTarget == DragTarget.FRIENDS_MOVE || dragTarget == DragTarget.FRIENDS_RESIZE)
            return friendsWindow;
        if (dragTarget == DragTarget.CALCULATOR_MOVE || dragTarget == DragTarget.CALCULATOR_RESIZE)
            return calculatorWindow;
        if (dragTarget == DragTarget.TIME_MOVE || dragTarget == DragTarget.TIME_RESIZE)
            return timeWindow;
        if (dragTarget == DragTarget.SPOTIFY_MOVE || dragTarget == DragTarget.SPOTIFY_RESIZE)
            return spotifyWindow;
        if (dragTarget == DragTarget.APPEARANCE_MOVE || dragTarget == DragTarget.APPEARANCE_RESIZE)
            return appearanceWindow;
        if (dragTarget == DragTarget.NOTES_MOVE || dragTarget == DragTarget.NOTES_RESIZE) {
            return (draggedNoteIndex >= 0 && draggedNoteIndex < notes.size()) ? notes.get(draggedNoteIndex).window
                    : browserWindow;
        }
        return friendsWindow;
    }

    private DragTarget getDragTarget(double mouseX, double mouseY) {
        draggedNoteIndex = -1;
        DragTarget target = getDragTargetForTab(focusedTab, mouseX, mouseY);
        if (target != DragTarget.NONE)
            return target;

        if (focusedTab != Tab.APPEARANCE && appearanceVisible && (target = getWindowDragTarget(appearanceWindow, mouseX,
                mouseY, DragTarget.APPEARANCE_MOVE, DragTarget.APPEARANCE_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.NOTES && notesVisible) {
            for (int i = notes.size() - 1; i >= 0; i--) {
                NoteState note = notes.get(i);
                if ((target = getWindowDragTarget(note.window, mouseX, mouseY, DragTarget.NOTES_MOVE,
                        DragTarget.NOTES_RESIZE)) != DragTarget.NONE) {
                    draggedNoteIndex = i;
                    if (activeNoteIndex != i) {
                        activeNoteIndex = i;
                        rebuild();
                    }
                    return target;
                }
            }
        }
        if (focusedTab != Tab.FRIENDS && friendsVisible && (target = getWindowDragTarget(friendsWindow, mouseX, mouseY,
                DragTarget.FRIENDS_MOVE, DragTarget.FRIENDS_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.SPOTIFY && spotifyVisible && (target = getWindowDragTarget(spotifyWindow, mouseX, mouseY,
                DragTarget.SPOTIFY_MOVE, DragTarget.SPOTIFY_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.TIME && timeVisible && (target = getWindowDragTarget(timeWindow, mouseX, mouseY,
                DragTarget.TIME_MOVE, DragTarget.TIME_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.CALCULATOR && calculatorVisible && (target = getWindowDragTarget(calculatorWindow, mouseX,
                mouseY, DragTarget.CALCULATOR_MOVE, DragTarget.CALCULATOR_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.SCREENSHOTS && screenshotsVisible && (target = getWindowDragTarget(screenshotsWindow,
                mouseX, mouseY, DragTarget.SCREENSHOTS_MOVE, DragTarget.SCREENSHOTS_RESIZE)) != DragTarget.NONE)
            return target;
        if (focusedTab != Tab.BROWSER && browserVisible && (target = getWindowDragTarget(browserWindow, mouseX, mouseY,
                DragTarget.BROWSER_MOVE, DragTarget.BROWSER_RESIZE)) != DragTarget.NONE)
            return target;
        return DragTarget.NONE;
    }

    private DragTarget getDragTargetForTab(Tab tab, double mouseX, double mouseY) {
        DragTarget target = DragTarget.NONE;
        switch (tab) {
            case APPEARANCE:
                if (appearanceVisible)
                    target = getWindowDragTarget(appearanceWindow, mouseX, mouseY, DragTarget.APPEARANCE_MOVE,
                            DragTarget.APPEARANCE_RESIZE);
                break;
            case NOTES:
                if (notesVisible) {
                    for (int i = notes.size() - 1; i >= 0; i--) {
                        NoteState note = notes.get(i);
                        if ((target = getWindowDragTarget(note.window, mouseX, mouseY, DragTarget.NOTES_MOVE,
                                DragTarget.NOTES_RESIZE)) != DragTarget.NONE) {
                            draggedNoteIndex = i;
                            if (activeNoteIndex != i) {
                                activeNoteIndex = i;
                                rebuild();
                            }
                            return target;
                        }
                    }
                }
                break;
            case FRIENDS:
                if (friendsVisible)
                    target = getWindowDragTarget(friendsWindow, mouseX, mouseY, DragTarget.FRIENDS_MOVE,
                            DragTarget.FRIENDS_RESIZE);
                break;
            case SPOTIFY:
                if (spotifyVisible)
                    target = getWindowDragTarget(spotifyWindow, mouseX, mouseY, DragTarget.SPOTIFY_MOVE,
                            DragTarget.SPOTIFY_RESIZE);
                break;
            case TIME:
                if (timeVisible)
                    target = getWindowDragTarget(timeWindow, mouseX, mouseY, DragTarget.TIME_MOVE,
                            DragTarget.TIME_RESIZE);
                break;
            case CALCULATOR:
                if (calculatorVisible)
                    target = getWindowDragTarget(calculatorWindow, mouseX, mouseY, DragTarget.CALCULATOR_MOVE,
                            DragTarget.CALCULATOR_RESIZE);
                break;
            case SCREENSHOTS:
                if (screenshotsVisible)
                    target = getWindowDragTarget(screenshotsWindow, mouseX, mouseY, DragTarget.SCREENSHOTS_MOVE,
                            DragTarget.SCREENSHOTS_RESIZE);
                break;
            case BROWSER:
                if (browserVisible)
                    target = getWindowDragTarget(browserWindow, mouseX, mouseY, DragTarget.BROWSER_MOVE,
                            DragTarget.BROWSER_RESIZE);
                break;
        }
        return target;
    }

    private DragTarget getWindowDragTarget(OverlayWindow window, double mouseX, double mouseY, DragTarget moveTarget,
            DragTarget resizeTarget) {
        resizingLeft = resizingRight = resizingTop = resizingBottom = false;
        if (window.isInResizeHandle(mouseX, mouseY)) {
            resizingLeft = mouseX <= window.x + RESIZE_HANDLE;
            resizingRight = mouseX >= window.x + window.width - RESIZE_HANDLE;
            resizingTop = mouseY <= window.y + RESIZE_HANDLE;
            resizingBottom = mouseY >= window.y + window.height - RESIZE_HANDLE;
            return resizeTarget;
        }
        return window.isInHeader(mouseX, mouseY) ? moveTarget : DragTarget.NONE;
    }

    private ColorDragTarget getColorDragTarget(double mouseX, double mouseY) {
        if (!appearanceVisible)
            return ColorDragTarget.NONE;
        int x = appearanceWindow.x + 12;
        int y = appearanceWindow.y + HEADER_HEIGHT + 28;
        int pickerWidth = appearanceWindow.width - 16;
        int pickerHeight = getSettingsPickerHeight();
        int sliderY = y + pickerHeight + 16;
        if (mouseX >= x && mouseX <= x + pickerWidth && mouseY >= y && mouseY <= y + pickerHeight)
            return ColorDragTarget.GRADIENT;
        if (mouseX >= x && mouseX <= x + pickerWidth && mouseY >= sliderY - 4 && mouseY <= sliderY + 14)
            return ColorDragTarget.HUE;
        return ColorDragTarget.NONE;
    }

    private void updateColorFromMouse(double mouseX, double mouseY) {
        config.overlayTheme = MinecraftOverlayTheme.CUSTOM.name();
        int x = appearanceWindow.x + 12;
        int y = appearanceWindow.y + HEADER_HEIGHT + 28;
        int pickerWidth = appearanceWindow.width - 16;
        int pickerHeight = getSettingsPickerHeight();
        int sliderY = y + pickerHeight + 16;
        if (colorDragTarget == ColorDragTarget.GRADIENT) {
            if (colorMode == ColorMode.ACCENT) {
                saturation = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth)));
                brightness = clamp01(1.0F - (float) ((mouseY - y) / Math.max(1, pickerHeight)));
            } else {
                tabSaturation = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth)));
                tabBrightness = clamp01(1.0F - (float) ((mouseY - y) / Math.max(1, pickerHeight)));
            }
        } else if (colorDragTarget == ColorDragTarget.HUE) {
            if (colorMode == ColorMode.ACCENT) {
                hue = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth))) * 360.0F;
            } else {
                tabHue = clamp01((float) ((mouseX - x) / Math.max(1, pickerWidth))) * 360.0F;
            }
        }
        updateSharedHudState();
    }

    private static boolean isImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static float clampOpacity(float value) {
        return Math.max(0.1F, Math.min(1.0F, value));
    }

    private static float clampHue(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value))
            return 195.0F;
        float clamped = value % 360.0F;
        return clamped < 0.0F ? clamped + 360.0F : clamped;
    }

    private static double clampBrowserPixelScale(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value))
            return DEFAULT_BROWSER_PIXEL_SCALE;
        return Math.max(0.1D, Math.min(1.0D, value));
    }

    public static void renderPinnedHud(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof MinecraftOverlayScreen
                || client.currentScreen instanceof MinecraftOverlaySettingsScreen)
            return;
        ensureSharedHudConfig();
        if (sharedBrowserPinned)
            renderPinnedBrowser(context, client);
        if (sharedScreenshotsPinned)
            renderPinnedScreenshots(context, client);
        if (sharedFriendsPinned)
            renderPinnedTextWindow(context, client, sharedFriendsWindow, "Essential Friends",
                    "Open the overlay to use Essential friends controls.", 1.0F, false, false);
        if (sharedCalculatorPinned)
            renderPinnedTextWindow(context, client, sharedCalculatorWindow, "Calculator", getConfig().calculatorResult,
                    1.0F, false, false);
        if (sharedTimePinned)
            renderPinnedTime(context, client);
        if (sharedSpotifyPinned)
            renderPinnedSpotify(context, client);
        if (sharedNotesPinned) {
            for (NoteState note : SHARED_NOTES) {
                renderPinnedTextWindow(
                        context, client, new MinecraftOverlayConfig.WindowState(note.window.x, note.window.y,
                                note.window.width, note.window.height, true),
                        note.title, note.text, note.opacity, note.bold, note.italic);
            }
        }
    }

    public static void tickBackgroundBrowsers() {
        if (shouldUseFeatherBrowserBackendStatic())
            FeatherCefOverlayBrowser.pump();
        if (!sharedBrowserPinned || SHARED_BROWSER_TABS.isEmpty())
            return;
        int activeIndex = Math.max(0, Math.min(sharedActiveBrowserTab, SHARED_BROWSER_TABS.size() - 1));
        BrowserTabState tab = SHARED_BROWSER_TABS.get(activeIndex);
        if (tab.browser != null) {
            try {
                tab.browser.getTexture();
            } catch (Throwable exception) {
                MinecraftOverlay.LOGGER.debug("Background browser tick failed", exception);
            }
        }
    }

    public static void updateSharedHudStateFromConfig() {
        loadedSharedHudConfig = false;
        ensureSharedHudConfig();
    }

    private static void ensureSharedHudConfig() {
        if (loadedSharedHudConfig)
            return;
        MinecraftOverlayConfig config = getConfig();
        sharedBrowserPinned = config.browserPinned;
        sharedScreenshotsPinned = config.screenshotsPinned;
        sharedFriendsPinned = config.friendsPinned;
        sharedNotesPinned = config.notesPinned;
        sharedCalculatorPinned = config.calculatorPinned;
        sharedTimePinned = config.timePinned;
        sharedSpotifyPinned = config.spotifyPinned;
        sharedAppearancePinned = config.appearancePinned;
        sharedHue = clampHue(config.hue);
        sharedSaturation = clamp01(config.saturation);
        sharedBrightness = clamp01(config.brightness);
        sharedTabHue = clampHue(config.tabHue);
        sharedTabSaturation = clamp01(config.tabSaturation);
        sharedTabBrightness = clamp01(config.tabBrightness);
        sharedTabOpacity = clampOpacity(config.tabOpacity);
        sharedPinnedTabOpacity = clampOpacity(config.pinnedTabOpacity);
        if (config.browserWindow != null)
            sharedBrowserWindow = config.browserWindow;
        if (config.screenshotsWindow != null)
            sharedScreenshotsWindow = config.screenshotsWindow;
        if (config.friendsWindow != null)
            sharedFriendsWindow = config.friendsWindow;
        if (config.notesWindow != null)
            sharedNotesWindow = config.notesWindow;
        if (config.calculatorWindow != null)
            sharedCalculatorWindow = config.calculatorWindow;
        if (config.timeWindow != null)
            sharedTimeWindow = config.timeWindow;
        if (config.spotifyWindow != null)
            sharedSpotifyWindow = config.spotifyWindow;
        if (config.appearanceWindow != null)
            sharedAppearanceWindow = config.appearanceWindow;
        if (!loadedSharedNotes) {
            SHARED_NOTES.clear();
            if (config.notes != null) {
                for (MinecraftOverlayConfig.Note note : config.notes) {
                    if (note != null) {
                        SHARED_NOTES.add(new NoteState(
                                note.title == null || note.title.isBlank() ? "Note " + (SHARED_NOTES.size() + 1)
                                        : note.title,
                                note.text == null ? "" : note.text,
                                Math.max(1, Math.min(3, note.textSize)),
                                note.bold,
                                note.italic,
                                Math.max(0.1F, Math.min(1.0F, note.opacity)),
                                note.window != null ? note.window.x : 548,
                                note.window != null ? note.window.y : 502,
                                note.window != null ? note.window.width : 396,
                                note.window != null ? note.window.height : 180));
                        if (note.window != null)
                            SHARED_NOTES.get(SHARED_NOTES.size() - 1).window.positioned = note.window.positioned;
                    }
                }
            }
            if (SHARED_NOTES.isEmpty()) {
                String migrated = config.notesText == null ? "" : config.notesText;
                SHARED_NOTES.add(new NoteState("Note 1", migrated, 1, false, false, 1.0F, 548, 502, 396, 180));
            }
            sharedActiveNoteIndex = Math.max(0, Math.min(config.activeNoteIndex, SHARED_NOTES.size() - 1));
            loadedSharedNotes = true;
        }
        if (!loadedSharedBrowserTabs || SHARED_BROWSER_TABS.isEmpty()) {
            SHARED_BROWSER_TABS.clear();
            if (config.browserTabs != null) {
                for (MinecraftOverlayConfig.BrowserTab tab : config.browserTabs) {
                    if (tab != null && tab.url != null && !tab.url.isBlank()) {
                        SHARED_BROWSER_TABS.add(new BrowserTabState(tab.url, tab.pinned));
                    }
                }
            }
            if (SHARED_BROWSER_TABS.isEmpty())
                SHARED_BROWSER_TABS.add(new BrowserTabState(config.browserUrl, false));
            sharedActiveBrowserTab = Math.max(0, Math.min(config.activeBrowserTab, SHARED_BROWSER_TABS.size() - 1));
            loadedSharedBrowserTabs = true;
        }
        loadedSharedHudConfig = true;
    }

    private static void renderPinnedBrowser(DrawContext context, MinecraftClient client) {
        if (SHARED_BROWSER_TABS.isEmpty())
            return;
        int index = Math.max(0, Math.min(SHARED_BROWSER_TABS.size() - 1, sharedActiveBrowserTab));
        BrowserTabState tab = SHARED_BROWSER_TABS.get(index);
        if (tab == null || tab.browser == null)
            return;
        AbstractTexture texture = tab.browser.getTexture();
        GpuTextureView textureView = tab.browser.getTextureView();
        if (texture == null && textureView == null)
            return;
        int x = sharedBrowserWindow.x;
        int y = sharedBrowserWindow.y;
        int width = Math.max(1, sharedBrowserWindow.width);
        int height = Math.max(1, sharedBrowserWindow.height);
        int normalContentWidth = Math.max(1, sharedBrowserWindow.width - 10);
        int normalContentHeight = Math.max(1, sharedBrowserWindow.height - HEADER_HEIGHT - 47);
        double contentAspect = normalContentWidth / (double) normalContentHeight;
        int drawWidth = width;
        int drawHeight = Math.max(1, (int) Math.round(drawWidth / contentAspect));
        if (drawHeight > height) {
            drawHeight = height;
            drawWidth = Math.max(1, (int) Math.round(drawHeight * contentAspect));
        }
        int drawX = x + Math.max(0, (width - drawWidth) / 2);
        int drawY = y;
        if (texture != null) {
            drawTextureQuad(context, tab, texture, drawX, drawY, drawWidth, drawHeight);
        } else {
            drawBrowserTextureView(context, textureView, drawX, drawY, drawWidth, drawHeight);
        }
    }

    private static void renderPinnedScreenshots(DrawContext context, MinecraftClient client) {
        MinecraftOverlayConfig.WindowState window = sharedScreenshotsWindow;
        renderPinnedWindowChrome(context, client, window, "Screenshots", 1.0F);
        Path dir = client.runDirectory.toPath().resolve("screenshots");
        int y = window.y + HEADER_HEIGHT + 9;
        if (!Files.isDirectory(dir)) {
            context.drawTextWithShadow(client.textRenderer, "No screenshots found.", window.x + 8, y, 0xFFCCCCCC);
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(MinecraftOverlayScreen::isImage)
                    .sorted(Comparator.comparing(MinecraftOverlayScreen::lastModified).reversed()).limit(4).toList();
            if (files.isEmpty()) {
                context.drawTextWithShadow(client.textRenderer, "No screenshots found.", window.x + 8, y, 0xFFCCCCCC);
                return;
            }
            for (Path file : files) {
                String name = client.textRenderer.trimToWidth(file.getFileName().toString(),
                        Math.max(40, window.width - 16));
                context.drawTextWithShadow(client.textRenderer, name, window.x + 8, y, 0xFFDDDDDD);
                y += 14;
            }
        } catch (IOException exception) {
            context.drawTextWithShadow(client.textRenderer, "Could not load screenshots.", window.x + 8, y, 0xFFFFAAAA);
        }
    }

    private static void renderPinnedSpotify(DrawContext context, MinecraftClient client) {
        MinecraftOverlayConfig.WindowState window = sharedSpotifyWindow;
        renderPinnedWindowChrome(context, client, window, "Spotify", 1.0F);
        int x = window.x + 8;
        int y = window.y + HEADER_HEIGHT + 8;
        int albumSize = Math.max(32, Math.min(56, Math.min(window.width / 4, window.height - HEADER_HEIGHT - 28)));
        SpotifyCoverTexture coverTexture = getSpotifyCoverTexture(client,
                MinecraftOverlaySpotifyControls.getCoverArtBase64());
        if (coverTexture != null) {
            context.fill(x, y, x + albumSize, y + albumSize, 0xFF222222);
            context.drawTexture(RenderPipelines.GUI_TEXTURED, coverTexture.identifier(), x, y, 0.0F, 0.0F,
                    albumSize, albumSize, coverTexture.width(), coverTexture.height(), coverTexture.width(),
                    coverTexture.height());
        } else {
            context.fill(x, y, x + albumSize, y + albumSize, 0x40000000);
            context.drawCenteredTextWithShadow(client.textRenderer, "\u266B", x + albumSize / 2, y + albumSize / 2 - 4,
                    0xFFFFFFFF);
        }

        int textX = x + albumSize + 8;
        int maxWidth = Math.max(40, window.x + window.width - textX - 8);
        String title = MinecraftOverlaySpotifyControls.getTrackTitle().isBlank() ? "Not playing"
                : MinecraftOverlaySpotifyControls.getTrackTitle();
        context.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(title, maxWidth), textX, y + 3,
                0xFFFFFFFF);
        context.drawTextWithShadow(client.textRenderer,
                client.textRenderer.trimToWidth(MinecraftOverlaySpotifyControls.getTrackArtist(), maxWidth), textX,
                y + 16, 0xFFAAAAAA);

        if (window.height >= HEADER_HEIGHT + albumSize + 36) {
            int barY = y + albumSize + 10;
            int barWidth = Math.max(32, window.width - 16);
            int progressWidth = (int) (barWidth * MinecraftOverlaySpotifyControls.getProgressRatio());
            context.fill(x, barY, x + barWidth, barY + 2, 0x40FFFFFF);
            context.fill(x, barY, x + progressWidth, barY + 2, getSharedAccentColor());
        }
    }

    private static void renderPinnedTime(DrawContext context, MinecraftClient client) {
        MinecraftOverlayConfig.WindowState window = sharedTimeWindow;
        renderPinnedWindowChrome(context, client, window, "Clock", 1.0F);
        int x = window.x + 8;
        int y = window.y + HEADER_HEIGHT + 10;
        int width = Math.max(40, window.width - 16);
        context.drawCenteredTextWithShadow(client.textRenderer, MinecraftOverlayTimeTools.clockText(),
                window.x + window.width / 2, y, 0xFFFFFFFF);
        y += 22;
        String timer = "Timer " + MinecraftOverlayTimeTools.timerText();
        String stopwatch = "Stopwatch " + MinecraftOverlayTimeTools.stopwatchText();
        context.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(timer, width), x, y,
                MinecraftOverlayTimeTools.activeTool() == MinecraftOverlayTimeTools.Tool.TIMER ? getSharedAccentColor()
                        : 0xFFDDDDDD);
        context.drawTextWithShadow(client.textRenderer, client.textRenderer.trimToWidth(stopwatch, width), x, y + 14,
                MinecraftOverlayTimeTools.activeTool() == MinecraftOverlayTimeTools.Tool.STOPWATCH
                        ? getSharedAccentColor()
                        : 0xFFDDDDDD);
    }

    private static void renderPinnedTextWindow(DrawContext context, MinecraftClient client,
            MinecraftOverlayConfig.WindowState window, String title, String text, float opacity, boolean bold,
            boolean italic) {
        renderPinnedWindowChrome(context, client, window, title, opacity);
        net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY.withBold(bold).withItalic(italic);
        context.drawWrappedTextWithShadow(client.textRenderer, Text.literal(text).setStyle(style), window.x + 8,
                window.y + HEADER_HEIGHT + 9, Math.max(40, window.width - 16), 0xFFDDDDDD);
    }

    private static void renderPinnedWindowChrome(DrawContext context, MinecraftClient client,
            MinecraftOverlayConfig.WindowState window, String title, float opacity) {
        float effectiveOpacity = opacity * sharedPinnedTabOpacity;
        context.fill(window.x, window.y, window.x + window.width, window.y + window.height,
                withAlpha(scaleRgb(getSharedTabBackgroundColor(), 0.72F), Math.round(204 * effectiveOpacity)));
        context.fill(window.x, window.y, window.x + window.width, window.y + HEADER_HEIGHT,
                withAlpha(getSharedTabBackgroundColor(), Math.round(221 * effectiveOpacity)));
        context.fill(window.x, window.y, window.x + window.width, window.y + 2,
                withAlpha(getSharedAccentColor(), Math.round(255 * effectiveOpacity)));
        context.drawTextWithShadow(client.textRenderer, title, window.x + 6, window.y + 4, 0xFFFFFFFF);
    }

    private static int getSharedAccentColor() {
        return hsbToRgb(sharedHue, sharedSaturation, sharedBrightness);
    }

    private static int getSharedTabBackgroundColor() {
        return hsbToRgb(sharedTabHue, sharedTabSaturation, sharedTabBrightness);
    }

    private BrowserTabState getActiveBrowserTab() {
        if (browserTabs.isEmpty()) {
            browserTabs.add(new BrowserTabState(config.browserUrl, false));
            sharedActiveBrowserTab = 0;
        }
        sharedActiveBrowserTab = Math.max(0, Math.min(sharedActiveBrowserTab, browserTabs.size() - 1));
        return browserTabs.get(sharedActiveBrowserTab);
    }

    private boolean selectBrowserTab(double mouseX, double mouseY) {
        if (!browserVisible || browserTabs.isEmpty())
            return false;
        int x = browserWindow.x + 5;
        int y = browserWindow.y + HEADER_HEIGHT + 23;
        int availableWidth = Math.max(80, browserWindow.width - 10);
        if (mouseX < x || mouseX > x + availableWidth || mouseY < y || mouseY > y + 14)
            return false;
        int tabWidth = Math.max(52, Math.min(108, availableWidth / Math.max(1, browserTabs.size())));
        int index = Math.max(0, Math.min(browserTabs.size() - 1, (int) ((mouseX - x) / tabWidth)));
        sharedActiveBrowserTab = index;
        if (urlField != null)
            urlField.setText(getActiveBrowserTab().url);
        focusedTab = Tab.BROWSER;
        saveSettings();
        rebuild();
        resizeBrowser();
        return true;
    }

    private void installAdBlocker(BrowserTabState tab) {
        if (tab.adBlockInstalled || tab.browser == null)
            return;
        try {
            Object cefBrowser = tab.browser.getNativeBrowser();
            if (cefBrowser == null || !cefBrowser.getClass().getName().startsWith("org.cef."))
                return;
            Object cefClient = cefBrowser.getClass().getMethod("getClient").invoke(cefBrowser);
            ClassLoader loader = cefClient.getClass().getClassLoader();
            Class<?> requestHandlerType = Class.forName("org.cef.handler.CefRequestHandler", true, loader);
            Class<?> resourceHandlerType = Class.forName("org.cef.handler.CefResourceRequestHandler", true, loader);
            Object resourceHandler = Proxy.newProxyInstance(loader, new Class<?>[] { resourceHandlerType },
                    this::handleResourceRequest);
            Object requestHandler = Proxy.newProxyInstance(loader, new Class<?>[] { requestHandlerType },
                    (proxy, method, args) -> {
                        if ("getResourceRequestHandler".equals(method.getName()))
                            return resourceHandler;
                        return defaultProxyValue(method);
                    });
            Method addRequestHandler = cefClient.getClass().getMethod("addRequestHandler", requestHandlerType);
            addRequestHandler.invoke(cefClient, requestHandler);
            tab.adBlockInstalled = true;
            status = "Ad blocker enabled.";
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Could not install browser ad blocker", exception);
            status = "Browser started. Ad blocker unavailable: " + exception.getClass().getSimpleName();
        }
    }

    private void installDisplayHandler(BrowserTabState tab) {
        if (tab.displayHandlerInstalled || tab.browser == null)
            return;
        try {
            Object cefBrowser = tab.browser.getNativeBrowser();
            if (cefBrowser == null || !cefBrowser.getClass().getName().startsWith("org.cef."))
                return;
            Object cefClient = cefBrowser.getClass().getMethod("getClient").invoke(cefBrowser);
            ClassLoader loader = cefClient.getClass().getClassLoader();
            Class<?> displayHandlerType = Class.forName("org.cef.handler.CefDisplayHandler", true, loader);
            Object displayHandler = Proxy.newProxyInstance(loader, new Class<?>[] { displayHandlerType },
                    (proxy, method, args) -> {
                        if ("onAddressChange".equals(method.getName()) && args.length >= 3
                                && args[2] instanceof String newUrl) {
                            if (!newUrl.isEmpty() && !newUrl.equals("about:blank")) {
                                updateBrowserTabUrl(tab, newUrl);
                            }
                        }
                        return defaultProxyValue(method);
                    });
            Method addDisplayHandler = cefClient.getClass().getMethod("addDisplayHandler", displayHandlerType);
            addDisplayHandler.invoke(cefClient, displayHandler);
            tab.displayHandlerInstalled = true;
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Could not install browser display handler", exception);
        }
    }

    private void installLifeSpanHandler(BrowserTabState tab) {
        if (tab.lifeSpanHandlerInstalled || tab.browser == null)
            return;
        try {
            Object cefBrowser = tab.browser.getNativeBrowser();
            if (cefBrowser == null || !cefBrowser.getClass().getName().startsWith("org.cef."))
                return;
            Object cefClient = cefBrowser.getClass().getMethod("getClient").invoke(cefBrowser);
            ClassLoader loader = cefClient.getClass().getClassLoader();
            Class<?> lifeSpanHandlerType = Class.forName("org.cef.handler.CefLifeSpanHandler", true, loader);
            Object lifeSpanHandler = Proxy.newProxyInstance(loader, new Class<?>[] { lifeSpanHandlerType },
                    (proxy, method, args) -> {
                        if ("onBeforePopup".equals(method.getName()) && args.length >= 3
                                && args[2] instanceof String targetUrl) {
                            if (targetUrl != null && !targetUrl.isEmpty() && !targetUrl.equals("about:blank")) {
                                client.execute(() -> addBrowserTab(targetUrl));
                                return true;
                            }
                        }
                        return defaultProxyValue(method);
                    });
            Method addLifeSpanHandler = cefClient.getClass().getMethod("addLifeSpanHandler", lifeSpanHandlerType);
            addLifeSpanHandler.invoke(cefClient, lifeSpanHandler);
            tab.lifeSpanHandlerInstalled = true;
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Could not install browser life span handler", exception);
        }
    }

    private Object handleResourceRequest(Object proxy, Method method, Object[] args) {
        if ("onBeforeResourceLoad".equals(method.getName()) && args != null) {
            for (Object arg : args) {
                String url = getCefRequestUrl(arg);
                if (url != null && shouldBlockRequest(url)) {
                    blockedAdRequests++;
                    return blockRequestValue(method);
                }
            }
        }
        return defaultProxyValue(method);
    }

    private static Object blockRequestValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == Boolean.TYPE || type == Boolean.class)
            return true;
        if (type.isEnum()) {
            for (Object constant : type.getEnumConstants()) {
                String name = ((Enum<?>) constant).name().toUpperCase(Locale.ROOT);
                if (name.contains("CANCEL") || name.contains("BLOCK"))
                    return constant;
            }
        }
        return defaultProxyValue(method);
    }

    private static Object defaultProxyValue(Method method) {
        Class<?> type = method.getReturnType();
        if (type == Boolean.TYPE)
            return false;
        if (type == Integer.TYPE)
            return 0;
        if (type == Long.TYPE)
            return 0L;
        if (type == Float.TYPE)
            return 0.0F;
        if (type == Double.TYPE)
            return 0.0D;
        return null;
    }

    private static String getCefRequestUrl(Object value) {
        if (value == null || !value.getClass().getName().startsWith("org.cef.network.CefRequest"))
            return null;
        try {
            return (String) value.getClass().getMethod("getURL").invoke(value);
        } catch (Throwable exception) {
            return null;
        }
    }

    private static boolean shouldBlockRequest(String url) {
        String lower = url.toLowerCase(Locale.ROOT);
        String host = "";
        try {
            URI uri = URI.create(lower);
            host = uri.getHost() == null ? "" : uri.getHost();
        } catch (IllegalArgumentException ignored) {
            host = lower;
        }
        if (isYoutubeRequiredHost(host))
            return false;
        String[] blockedHosts = {
                "doubleclick.net", "googlesyndication.com", "googleadservices.com", "adservice.google.com",
                "adsystem.com", "amazon-adsystem.com", "adnxs.com", "taboola.com", "outbrain.com",
                "scorecardresearch.com", "quantserve.com", "pubmatic.com", "rubiconproject.com",
                "criteo.com", "casalemedia.com", "openx.net", "yieldmo.com", "adform.net", "adroll.com",
                "adsrvr.org", "moatads.com", "serving-sys.com", "2mdn.net", "google-analytics.com",
                "googletagmanager.com", "googletagservices.com", "securepubads.g.doubleclick.net",
                "analytics.twitter.com", "facebook.net", "connect.facebook.net",
                "hotjar.com", "mouseflow.com", "adobedtm.com", "segment.io", "mixpanel.com"
        };
        for (String blockedHost : blockedHosts) {
            if (host.equals(blockedHost) || host.endsWith("." + blockedHost))
                return true;
        }
        String[] blockedPaths = {
                "/pagead/", "/pagead2/", "/ads?", "/ads/", "/adserver", "/adservice", "/prebid",
                "/bidder", "/banners/", "/sponsor", "/tracking/", "/track?", "/analytics.js",
                "/gtag/js", "/collect?", "/beacon/", "/pixel?", "/adunit", "/adclick", "/advert"
        };
        for (String marker : blockedPaths) {
            if (lower.contains(marker))
                return true;
        }
        return false;
    }

    private static boolean isYoutubeRequiredHost(String host) {
        if (host == null || host.isBlank())
            return false;
        String[] allowedHosts = {
                "youtube.com", "youtube-nocookie.com", "ytimg.com", "googlevideo.com", "ggpht.com",
                "googleusercontent.com", "gstatic.com", "google.com", "accounts.google.com"
        };
        for (String allowedHost : allowedHosts) {
            if (host.equals(allowedHost) || host.endsWith("." + allowedHost))
                return true;
        }
        return false;
    }

    public enum Tab {
        BROWSER,
        SCREENSHOTS,
        FRIENDS,
        NOTES,
        CALCULATOR,
        TIME,
        SPOTIFY,
        APPEARANCE
    }

    private enum ColorDragTarget {
        NONE,
        GRADIENT,
        HUE
    }

    private enum ColorMode {
        ACCENT,
        TAB_BACKGROUND
    }

    private enum DragTarget {
        NONE,
        BROWSER_MOVE,
        BROWSER_RESIZE,
        SCREENSHOTS_MOVE,
        SCREENSHOTS_RESIZE,
        FRIENDS_MOVE,
        FRIENDS_RESIZE,
        NOTES_MOVE,
        NOTES_RESIZE,
        CALCULATOR_MOVE,
        CALCULATOR_RESIZE,
        TIME_MOVE,
        TIME_RESIZE,
        SPOTIFY_MOVE,
        SPOTIFY_RESIZE,
        APPEARANCE_MOVE,
        APPEARANCE_RESIZE;

        private boolean isMove() {
            return this == BROWSER_MOVE || this == SCREENSHOTS_MOVE || this == FRIENDS_MOVE || this == NOTES_MOVE
                    || this == CALCULATOR_MOVE || this == TIME_MOVE || this == SPOTIFY_MOVE || this == APPEARANCE_MOVE;
        }

        private Tab getTab() {
            if (this == BROWSER_MOVE || this == BROWSER_RESIZE)
                return Tab.BROWSER;
            if (this == SCREENSHOTS_MOVE || this == SCREENSHOTS_RESIZE)
                return Tab.SCREENSHOTS;
            if (this == FRIENDS_MOVE || this == FRIENDS_RESIZE)
                return Tab.FRIENDS;
            if (this == NOTES_MOVE || this == NOTES_RESIZE)
                return Tab.NOTES;
            if (this == CALCULATOR_MOVE || this == CALCULATOR_RESIZE)
                return Tab.CALCULATOR;
            if (this == TIME_MOVE || this == TIME_RESIZE)
                return Tab.TIME;
            if (this == SPOTIFY_MOVE || this == SPOTIFY_RESIZE)
                return Tab.SPOTIFY;
            return Tab.APPEARANCE;
        }
    }

    private static class OverlayToolbarButton extends ClickableWidget {
        private final Runnable action;
        private final String tooltip;
        private final boolean selected;
        private final boolean vertical;
        private final IntSupplier accentColor;
        private final IntSupplier tabColor;

        private OverlayToolbarButton(int x, int y, int width, int height, Text message, String tooltip, Runnable action,
                boolean selected, boolean vertical, IntSupplier accentColor, IntSupplier tabColor) {
            super(x, y, width, height, message);
            this.action = action;
            this.tooltip = tooltip;
            this.selected = selected;
            this.vertical = vertical;
            this.accentColor = accentColor;
            this.tabColor = tabColor;
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            int base = tabColor.getAsInt();
            int fill = selected ? withAlpha(scaleRgb(accentColor.getAsInt(), 0.34F), 238)
                    : withAlpha(scaleRgb(base, hovered ? 1.18F : 0.82F), 232);
            int border = selected ? accentColor.getAsInt()
                    : withAlpha(scaleRgb(0xFFFFFF, hovered ? 0.70F : 0.36F), hovered ? 110 : 60);
            context.fill(getX(), getY(), getX() + width, getY() + height, fill);
            context.fill(getX(), getY(), getX() + width, getY() + 1, border);
            context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, selected ? accentColor.getAsInt() : 0xA0000000);
            context.fill(getX(), getY(), getX() + 1, getY() + height, border);
            context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
            int textColor = active ? 0xFFFFFFFF : 0xFF777777;
            context.drawCenteredTextWithShadow(MinecraftClient.getInstance().textRenderer, getMessage(),
                    getX() + width / 2, getY() + 7, textColor);
            if (hovered && tooltip != null && !tooltip.isBlank()) {
                int tooltipWidth = MinecraftClient.getInstance().textRenderer.getWidth(tooltip) + 10;
                int tooltipX = vertical
                        ? Math.max(4, Math.min(getX() + width + 8,
                                MinecraftClient.getInstance().getWindow().getScaledWidth() - tooltipWidth - 4))
                        : Math.max(4, Math.min(getX() + width / 2 - tooltipWidth / 2,
                                MinecraftClient.getInstance().getWindow().getScaledWidth() - tooltipWidth - 4));
                int tooltipY = vertical ? getY() + 8 : getY() - 24;
                context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 18, 0xF0101116);
                context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, accentColor.getAsInt());
                context.drawTextWithShadow(MinecraftClient.getInstance().textRenderer, tooltip, tooltipX + 5,
                        tooltipY + 6, 0xFFFFFFFF);
            }
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            if (active && visible)
                action.run();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private static class NotesEditorWidget extends ClickableWidget {
        private final Consumer<String> onChanged;
        private String text;
        private int cursor;
        private final int textSize;
        private final boolean bold;
        private final boolean italic;

        private NotesEditorWidget(int x, int y, int width, int height, Text message, String text, int textSize,
                boolean bold, boolean italic, Consumer<String> onChanged) {
            super(x, y, width, height, message);
            this.text = text == null ? "" : text;
            this.cursor = this.text.length();
            this.textSize = Math.max(1, Math.min(3, textSize));
            this.bold = bold;
            this.italic = italic;
            this.onChanged = onChanged;
        }

        private String getText() {
            return text;
        }

        private void setText(String value) {
            text = value == null ? "" : value;
            cursor = Math.min(cursor, text.length());
            onChanged.accept(text);
        }

        private void insertText(String value) {
            if (value == null || value.isEmpty())
                return;
            text = text.substring(0, cursor) + value + text.substring(cursor);
            cursor += value.length();
            onChanged.accept(text);
        }

        @Override
        protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
            MinecraftClient client = MinecraftClient.getInstance();
            int border = isFocused() ? 0xFFE6E6E6 : 0xFF4A4D55;
            context.fill(getX(), getY(), getX() + width, getY() + height, 0xD8121419);
            context.fill(getX(), getY(), getX() + width, getY() + 1, border);
            context.fill(getX(), getY() + height - 1, getX() + width, getY() + height, border);
            context.fill(getX(), getY(), getX() + 1, getY() + height, border);
            context.fill(getX() + width - 1, getY(), getX() + width, getY() + height, border);
            int color = bold ? 0xFFFFFFFF : 0xFFE8E8E8;
            String prefix = italic ? "/ " : "";
            if (text.isBlank()) {
                context.drawTextWithShadow(client.textRenderer, "Write notes here. Enter adds a line.", getX() + 6,
                        getY() + 7, 0xFF777777);
                return;
            }
            int y = getY() + 7;
            int lineGap = switch (textSize) {
                case 2 -> 14;
                case 3 -> 18;
                default -> 10;
            };
            for (String line : text.split("\n", -1)) {
                net.minecraft.text.Style style = net.minecraft.text.Style.EMPTY.withBold(bold).withItalic(italic);
                Text content = Text.literal(line).setStyle(style);
                if (textSize == 1) {
                    context.drawWrappedTextWithShadow(client.textRenderer, content, getX() + 6, y,
                            Math.max(20, width - 12), color);
                    y += Math.max(lineGap, 10);
                } else {
                    context.getMatrices().pushMatrix();
                    float scale = textSize == 2 ? 1.25F : 1.5F;
                    context.getMatrices().translate(getX() + 6, y);
                    context.getMatrices().scale(scale, scale);
                    context.drawTextWithShadow(client.textRenderer, content, 0, 0, color);
                    context.getMatrices().popMatrix();
                    y += lineGap;
                }
                if (y > getY() + height - 10)
                    break;
            }
            if (isFocused() && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                context.fill(getX() + 6, getY() + height - 10, getX() + 46, getY() + height - 9, 0xFFDDDDDD);
            }
        }

        @Override
        public void onClick(Click click, boolean doubleClick) {
            setFocused(true);
            cursor = text.length();
        }

        @Override
        public boolean keyPressed(KeyInput event) {
            if (!isFocused())
                return false;
            if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (cursor > 0) {
                    text = text.substring(0, cursor - 1) + text.substring(cursor);
                    cursor--;
                    onChanged.accept(text);
                }
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
                insertText("\n");
                return true;
            }
            if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
                setFocused(false);
                return true;
            }
            return false;
        }

        @Override
        public boolean charTyped(CharInput event) {
            if (!isFocused())
                return false;
            if (event.codepoint() >= 32 || event.codepoint() == '\t') {
                insertText(Character.toString(event.codepoint()));
                return true;
            }
            return false;
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }

    private static class OverlayWindow {
        private int x;
        private int y;
        private int width;
        private int height;
        private final int minWidth;
        private final int minHeight;
        private boolean positioned;

        private OverlayWindow(int x, int y, int width, int height, int minWidth, int minHeight) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.minWidth = minWidth;
            this.minHeight = minHeight;
        }

        private DragTarget getDragTarget(double mouseX, double mouseY, DragTarget moveTarget, DragTarget resizeTarget) {
            if (isInResizeHandle(mouseX, mouseY))
                return resizeTarget;
            if (isInHeader(mouseX, mouseY))
                return moveTarget;
            return DragTarget.NONE;
        }

        private boolean isInHeader(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + HEADER_HEIGHT;
        }

        private boolean isInResizeHandle(double mouseX, double mouseY) {
            boolean left = mouseX >= x && mouseX <= x + RESIZE_HANDLE;
            boolean right = mouseX >= x + width - RESIZE_HANDLE && mouseX <= x + width;
            boolean top = mouseY >= y && mouseY <= y + RESIZE_HANDLE;
            boolean bottom = mouseY >= y + height - RESIZE_HANDLE && mouseY <= y + height;
            return (left || right) && (top || bottom);
        }

        private void clamp(int screenWidth, int screenHeight) {
            width = Math.max(minWidth, Math.min(width, Math.max(minWidth, screenWidth - 20)));
            height = Math.max(minHeight, Math.min(height, Math.max(minHeight, screenHeight - 20)));
            x = Math.max(0, Math.min(x, screenWidth - width));
            y = Math.max(0, Math.min(y, screenHeight - height));
        }
    }

    private static class BrowserTabState {
        private String url;
        private boolean pinned;
        private OverlayBrowser browser;
        private final Identifier textureIdentifier = Identifier.of(MinecraftOverlay.MOD_ID,
                "browser/" + (nextBrowserTextureId++));
        private AbstractTexture registeredTexture;
        private boolean adBlockInstalled;
        private boolean displayHandlerInstalled;
        private boolean lifeSpanHandlerInstalled;
        private int pendingNavigationTicks;
        private boolean forceMcefBackend;
        private long waitingForFrameSinceNanos;
        private long lastUrlPollNanos;
        private long lastFrameCheckNanos;
        private boolean loggedFeatherFrameWait;

        private BrowserTabState(String url, boolean pinned) {
            this.url = url == null || url.isBlank() ? "https://www.google.com" : url;
            this.pinned = pinned;
        }

        private String title() {
            try {
                URI uri = URI.create(url);
                String host = uri.getHost();
                if (host != null && !host.isBlank())
                    return host.startsWith("www.") ? host.substring(4) : host;
            } catch (IllegalArgumentException ignored) {
            }
            return "New tab";
        }
    }

    private record SpotifyCoverTexture(Identifier identifier, int width, int height) {
    }

    private static class CalculatorParser {
        private final String expression;
        private int position;

        private CalculatorParser(String expression) {
            this.expression = expression == null ? "" : expression;
        }

        private double parse() {
            double value = parseExpression();
            skipWhitespace();
            if (position != expression.length())
                throw new IllegalArgumentException("Unexpected calculator input.");
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+'))
                    value += parseTerm();
                else if (match('-'))
                    value -= parseTerm();
                else
                    return value;
            }
        }

        private double parseTerm() {
            double value = parsePower();
            while (true) {
                skipWhitespace();
                if (match('*'))
                    value *= parsePower();
                else if (match('/'))
                    value /= parsePower();
                else
                    return value;
            }
        }

        private double parsePower() {
            double value = parseUnary();
            skipWhitespace();
            if (match('^'))
                value = Math.pow(value, parsePower());
            return value;
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+'))
                return parseUnary();
            if (match('-'))
                return -parseUnary();
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                double value = parseExpression();
                if (!match(')'))
                    throw new IllegalArgumentException("Missing closing parenthesis.");
                return value;
            }

            int start = position;
            while (position < expression.length()) {
                char c = expression.charAt(position);
                if ((c >= '0' && c <= '9') || c == '.')
                    position++;
                else
                    break;
            }
            if (start == position)
                throw new IllegalArgumentException("Enter a calculator expression.");
            try {
                return Double.parseDouble(expression.substring(start, position));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("Invalid number.");
            }
        }

        private boolean match(char expected) {
            skipWhitespace();
            if (position >= expression.length() || expression.charAt(position) != expected)
                return false;
            position++;
            return true;
        }

        private void skipWhitespace() {
            while (position < expression.length() && Character.isWhitespace(expression.charAt(position)))
                position++;
        }
    }

    private static class NoteState {
        private String title;
        private String text;
        private int textSize;
        private boolean bold;
        private boolean italic;
        private float opacity;
        private final OverlayWindow window;

        private NoteState(String title, String text, int textSize, boolean bold, boolean italic, float opacity, int x,
                int y, int w, int h) {
            this.title = title;
            this.text = text;
            this.textSize = textSize;
            this.bold = bold;
            this.italic = italic;
            this.opacity = opacity;
            this.window = new OverlayWindow(x, y, w, h, MIN_NOTES_WIDTH, MIN_NOTES_HEIGHT);
        }
    }
}
