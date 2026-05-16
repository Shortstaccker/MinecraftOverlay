package com.minecraftoverlay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class MinecraftOverlayConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("minecraftoverlay.json");

    public boolean browserVisible = false;
    public boolean screenshotsVisible = false;
    public boolean friendsVisible = false;
    public boolean notesVisible = false;
    public boolean calculatorVisible = false;
    public boolean timeVisible = false;
    public boolean spotifyVisible = false;
    public boolean appearanceVisible = false;
    public boolean browserPinned = false;
    public boolean screenshotsPinned = false;
    public boolean friendsPinned = false;
    public boolean notesPinned = false;
    public boolean calculatorPinned = false;
    public boolean timePinned = false;
    public boolean spotifyPinned = false;
    public boolean appearancePinned = false;
    public String overlayTheme = "Custom";
    public String browserUrl = "https://www.google.com";
    public int activeBrowserTab = 0;
    public BrowserTab[] browserTabs = new BrowserTab[]{new BrowserTab("https://www.google.com", false)};
    public float hue = 195.0F;
    public float saturation = 0.75F;
    public float brightness = 1.0F;
    public float tabHue = 220.0F;
    public float tabSaturation = 0.12F;
    public float tabBrightness = 0.18F;
    public float tabOpacity = 0.90F;
    public float pinnedTabOpacity = 0.65F;
    public boolean taskbarVertical = false;
    public double browserPixelScale = 0.75D;
    public boolean compatibilityMode = false;
    public float outputVolume = 1.0F;
    public String outputDevice = "";
    public int openOverlayKey = 340;
    public int openOverlayKeySecond = 258;
    public int spotifyPreviousKey = 296;
    public int spotifyPreviousKeySecond = 0;
    public int spotifyPlayPauseKey = 297;
    public int spotifyPlayPauseKeySecond = 0;
    public int spotifyNextKey = 298;
    public int spotifyNextKeySecond = 0;
    public int timePauseKey = 299;
    public int timePauseKeySecond = 0;
    public int timeResetKey = 300;
    public int timeResetKeySecond = 0;
    public String timerSound = "pling";
    public boolean discordRpcEnabled = false;
    public String discordApplicationId = "";
    public String spotifyClientId = "";
    public String spotifyAccessToken = "";
    public String spotifyRefreshToken = "";
    public long spotifyTokenExpiresAtMillis = 0L;
    public boolean spotifySearchBarEnabled = true;
    public String calculatorExpression = "";
    public String calculatorResult = "0";
    public String notesText = "";
    public int activeNoteIndex = 0;
    public Note[] notes = new Note[]{new Note("Note 1", "", 1, false, false, 1.0F)};
    public String[] essentialMessageHistory = new String[0];
    public WindowState browserWindow = new WindowState(0, 0, 396, 184, false);
    public WindowState screenshotsWindow = new WindowState(0, 0, 396, 174, false);
    public WindowState friendsWindow = new WindowState(0, 0, 396, 184, false);
    public WindowState notesWindow = new WindowState(0, 0, 396, 180, false);
    public WindowState calculatorWindow = new WindowState(0, 0, 320, 252, false);
    public WindowState timeWindow = new WindowState(0, 0, 320, 180, false);
    public WindowState spotifyWindow = new WindowState(0, 0, 360, 220, false);
    public WindowState appearanceWindow = new WindowState(0, 0, 396, 280, false);

    public static MinecraftOverlayConfig load() {
        if (!Files.isRegularFile(CONFIG_PATH)) {
            return new MinecraftOverlayConfig();
        }

        try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
            MinecraftOverlayConfig config = GSON.fromJson(reader, MinecraftOverlayConfig.class);
            return config != null ? config : new MinecraftOverlayConfig();
        } catch (IOException | RuntimeException exception) {
            return new MinecraftOverlayConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }

    public static class WindowState {
        public int x;
        public int y;
        public int width;
        public int height;
        public boolean positioned;

        public WindowState(int x, int y, int width, int height, boolean positioned) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.positioned = positioned;
        }
    }

    public static class BrowserTab {
        public String url;
        public boolean pinned;

        public BrowserTab(String url, boolean pinned) {
            this.url = url;
            this.pinned = pinned;
        }
    }

    public static class Note {
        public String title;
        public String text;
        public int textSize;
        public boolean bold;
        public boolean italic;
        public float opacity = 1.0F;
        public WindowState window;

        public Note(String title, String text, int textSize, boolean bold, boolean italic) {
            this(title, text, textSize, bold, italic, 1.0F);
        }

        public Note(String title, String text, int textSize, boolean bold, boolean italic, float opacity) {
            this.title = title;
            this.text = text;
            this.textSize = textSize;
            this.bold = bold;
            this.italic = italic;
            this.opacity = opacity;
        }
    }
}
