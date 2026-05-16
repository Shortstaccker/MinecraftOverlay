package com.minecraftoverlay;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.KeyBinding.Category;
import net.minecraft.client.util.InputUtil;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Pattern;

public class MinecraftOverlay implements ModInitializer, ClientModInitializer {
    public static final String MOD_ID = "minecraftoverlay";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static KeyBinding openOverlayKey;
    private static boolean clientInitialized;
    private static float lastAppliedOutputVolume = -1.0F;
    private boolean overlayComboPressed;
    private final boolean[] customKeyPressed = new boolean[6];

    @Override
    public void onInitialize() {
        LOGGER.info("MinecraftOverlay common initializer loaded.");
    }

    @Override
    public void onInitializeClient() {
        if (clientInitialized) return;
        clientInitialized = true;
        ClassCheck.run();
        configureFeatherCefEarly();

        openOverlayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.minecraftoverlay.open_overlay", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_O, Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        HudElementRegistry.addLast(Identifier.of(MOD_ID, "pinned_hud"), (context, tickCounter) -> {
            MinecraftOverlayScreen.tickBackgroundBrowsers();
            MinecraftOverlayScreen.renderPinnedHud(context);
        });
        LOGGER.info("MinecraftOverlay initialized.");
    }

    private void configureFeatherCefEarly() {
        if (!"Feather".equals(getDetectedClientName()) || !isClassPresent("net.digitalingot.fcef.CefApp")) return;
        try {
            Class<?> browserClass = Class.forName("com.minecraftoverlay.FeatherCefOverlayBrowser", true, MinecraftOverlay.class.getClassLoader());
            browserClass.getDeclaredMethod("prepareProcess").invoke(null);
        } catch (Throwable exception) {
            LOGGER.debug("Could not prepare Feather CEF before startup", exception);
        }
    }

    private void onClientTick(MinecraftClient client) {
        applyOutputVolume(client);
        MinecraftOverlayScreen.tickBackgroundBrowsers();
        MinecraftOverlaySpotifyControls.tick(client);
        MinecraftOverlayDiscordRpc.tick(client);
        while (openOverlayKey.wasPressed()) openOverlay(client, MinecraftOverlayScreen.Tab.BROWSER);
        pollCustomKeybinds(client);

        boolean comboDown = (InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT) || InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT)) 
                          && InputUtil.isKeyPressed(client.getWindow(), GLFW.GLFW_KEY_TAB);
        if (comboDown && !overlayComboPressed) openOverlay(client, MinecraftOverlayScreen.Tab.BROWSER);
        overlayComboPressed = comboDown;
    }

    public static void openOverlay(MinecraftClient client, MinecraftOverlayScreen.Tab tab) {
        if (client.currentScreen instanceof MinecraftOverlayScreen) return;
        client.setScreen(new MinecraftOverlayScreen(client.currentScreen, tab));
    }

    private void pollCustomKeybinds(MinecraftClient client) {
        if (client.getWindow() == null) return;
        MinecraftOverlayConfig config = MinecraftOverlayScreen.getConfig();
        pollCustomKey(client, 0, config.openOverlayKey, config.openOverlayKeySecond, MinecraftOverlayScreen.Tab.BROWSER);
        pollCustomActionKey(client, 1, config.spotifyPreviousKey, config.spotifyPreviousKeySecond, MinecraftOverlaySpotifyControls::previousTrack);
        pollCustomActionKey(client, 2, config.spotifyPlayPauseKey, config.spotifyPlayPauseKeySecond, MinecraftOverlaySpotifyControls::playPause);
        pollCustomActionKey(client, 3, config.spotifyNextKey, config.spotifyNextKeySecond, MinecraftOverlaySpotifyControls::nextTrack);
        pollCustomActionKey(client, 4, config.timePauseKey, config.timePauseKeySecond, MinecraftOverlayTimeTools::toggleActive);
        pollCustomActionKey(client, 5, config.timeResetKey, config.timeResetKeySecond, MinecraftOverlayTimeTools::resetActive);
    }

    private void pollCustomKey(MinecraftClient client, int index, int key, int secondKey, MinecraftOverlayScreen.Tab tab) {
        if (key <= 0) return;
        boolean pressed = InputUtil.isKeyPressed(client.getWindow(), key) && (secondKey <= 0 || InputUtil.isKeyPressed(client.getWindow(), secondKey));
        if (pressed && !customKeyPressed[index]) {
            if (tab == MinecraftOverlayScreen.Tab.APPEARANCE) openSettings(client);
            else openOverlay(client, tab);
        }
        customKeyPressed[index] = pressed;
    }

    private void pollCustomActionKey(MinecraftClient client, int index, int key, int secondKey, Runnable action) {
        if (key <= 0) return;
        boolean pressed = InputUtil.isKeyPressed(client.getWindow(), key) && (secondKey <= 0 || InputUtil.isKeyPressed(client.getWindow(), secondKey));
        if (pressed && !customKeyPressed[index]) action.run();
        customKeyPressed[index] = pressed;
    }

    private static void applyOutputVolume(MinecraftClient client) {
        if (client == null || client.options == null || client.getSoundManager() == null) return;
        float volume = Math.max(0.0F, Math.min(1.0F, MinecraftOverlayScreen.getConfig().outputVolume));
        if (Math.abs(volume - lastAppliedOutputVolume) < 0.001F) return;
        client.options.getSoundVolumeOption(SoundCategory.MASTER).setValue((double) volume);
        client.getSoundManager().setVolume(SoundCategory.MASTER, volume);
        lastAppliedOutputVolume = volume;
    }

    private void openSettings(MinecraftClient client) {
        client.setScreen(new MinecraftOverlaySettingsScreen(client.currentScreen));
    }

    public static String getDetectedClientName() {
        if (isModLoadedAny("feather", "featherclient", "feather-client", "feather_client") || hasLoadedModMatching("feather")) return "Feather";
        if (isModLoadedAny("lunar", "lunarclient", "lunar-client", "lunar_client") || hasLoadedModMatching("lunar")) return "Lunar";

        try {
            String probe = getEnvironmentProbe();
            if (probe.contains("feather")) return "Feather";
            if (probe.contains("lunar") || probe.contains(".lunarclient") || System.getProperty("lunar.webosr.url") != null) return "Lunar";

            for (StackTraceElement element : Thread.currentThread().getStackTrace()) {
                String className = element.getClassName().toLowerCase(Locale.ROOT);
                if (className.contains("lunarclient") || className.contains("feather")) return className.contains("lunar") ? "Lunar" : "Feather";
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static boolean isThirdPartyClientDetected() {
        return !getDetectedClientName().isEmpty();
    }

    public static boolean isEssentialDetected() {
        if (isModLoadedAny("essential", "essential-loader", "essential_loader", "essential-container", "essential_container", "essential_partner_mod")) return true;
        if (hasLoadedModMatching("essential")) return true;
        return isClassPresent("gg.essential.Essential")
                || isClassPresent("gg.essential.api.EssentialAPI")
                || isClassPresent("gg.essential.loader.stage1.EssentialLoader")
                || isClassPresent("gg.essential.loader.stage2.EssentialLoader")
                || getEnvironmentProbe().contains("essential");
    }

    public static Path getDetectedClientProfileDir() {
        Path profileFromIchor = profileFromModPathList(System.getProperty("ichor.fabric.localModPath", ""));
        if (profileFromIchor != null) return profileFromIchor;

        Path profileFromFabricMods = profileFromModPathList(System.getProperty("fabric.mod.path", ""));
        if (profileFromFabricMods != null) return profileFromFabricMods;

        String clientName = getDetectedClientName();
        Path gameDir = getFabricGameDir();
        Path userDir = safePath(System.getProperty("user.dir", ""));

        if ("Feather".equals(clientName)) {
            Path featherProfile = firstExistingDirectory(
                    pathContains(gameDir, "feather") ? gameDir : null,
                    pathContains(userDir, "feather") ? userDir : null,
                    resolveEnvPath("APPDATA", ".minecraft" + File.separator + "feather"),
                    resolveEnvPath("APPDATA", "Feather Launcher"),
                    resolveEnvPath("APPDATA", ".feather"),
                    resolveEnvPath("APPDATA", "Feather Client"),
                    resolveEnvPath("LOCALAPPDATA", ".feather"),
                    resolveEnvPath("LOCALAPPDATA", "Feather Client"),
                    resolveEnvPath("USERPROFILE", "Feather Launcher")
            );
            if (featherProfile != null) return featherProfile;
        }

        if ("Lunar".equals(clientName)) {
            Path lunarProfile = firstExistingDirectory(
                    pathContains(gameDir, "lunar") ? gameDir : null,
                    pathContains(userDir, "lunar") ? userDir : null,
                    Path.of(System.getProperty("user.home", ""), ".lunarclient", "profiles", "lunar", "1.21")
            );
            if (lunarProfile != null) return lunarProfile;
        }

        if (!clientName.isEmpty() && gameDir != null) return gameDir;
        return null;
    }

    public static boolean isModLoadedAny(String... modIds) {
        FabricLoader loader = FabricLoader.getInstance();
        for (String modId : modIds) {
            if (loader.isModLoaded(modId)) return true;
        }
        return false;
    }

    private static boolean hasLoadedModMatching(String needle) {
        try {
            String lowerNeedle = needle.toLowerCase(Locale.ROOT);
            return FabricLoader.getInstance().getAllMods().stream().anyMatch(mod -> {
                String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
                String name = mod.getMetadata().getName().toLowerCase(Locale.ROOT);
                return id.contains(lowerNeedle) || name.contains(lowerNeedle);
            });
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, MinecraftOverlay.class.getClassLoader());
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

    private static String getEnvironmentProbe() {
        StringBuilder probe = new StringBuilder();
        appendSystemProperty(probe, "sun.java.command");
        appendSystemProperty(probe, "java.class.path");
        appendSystemProperty(probe, "user.dir");
        appendSystemProperty(probe, "minecraft.launcher.brand");
        appendSystemProperty(probe, "java.vm.vendor");
        appendSystemProperty(probe, "ichor.fabric.localModPath");
        appendSystemProperty(probe, "fabric.mod.path");
        appendSystemProperty(probe, "fabric.addMods");
        appendSystemProperty(probe, "lunar.webosr.url");
        appendEnv(probe, "FEATHER_HOME");
        appendEnv(probe, "FEATHER_DIR");
        appendEnv(probe, "LUNAR_HOME");
        return probe.toString().toLowerCase(Locale.ROOT);
    }

    private static void appendSystemProperty(StringBuilder builder, String key) {
        String value = System.getProperty(key, "");
        if (!value.isBlank()) builder.append(' ').append(value);
    }

    private static void appendEnv(StringBuilder builder, String key) {
        String value = System.getenv(key);
        if (value != null && !value.isBlank()) builder.append(' ').append(value);
    }

    private static Path profileFromModPathList(String value) {
        if (value == null || value.isBlank()) return null;
        for (String entry : value.split(Pattern.quote(File.pathSeparator))) {
            Path profile = profileFromModPath(safePath(entry));
            if (profile != null) return profile;
        }
        return null;
    }

    private static Path profileFromModPath(Path path) {
        if (path == null) return null;
        Path current = path;
        if (looksLikeJar(current) || Files.isRegularFile(current)) current = current.getParent();

        while (current != null) {
            String name = pathName(current);
            if ("mods".equals(name)) return current.getParent();
            if (name.startsWith("fabric-") && current.getParent() != null && "mods".equals(pathName(current.getParent()))) {
                Path profile = current.getParent().getParent();
                if (profile != null) return profile;
            }
            current = current.getParent();
        }

        return path.getParent();
    }

    private static boolean looksLikeJar(Path path) {
        Path fileName = path.getFileName();
        return fileName != null && fileName.toString().toLowerCase(Locale.ROOT).endsWith(".jar");
    }

    private static String pathName(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
    }

    private static Path getFabricGameDir() {
        try {
            return FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Path safePath(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path resolveEnvPath(String envName, String child) {
        String value = System.getenv(envName);
        if (value == null || value.isBlank()) return null;
        try {
            return Path.of(value, child).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Path firstExistingDirectory(Path... candidates) {
        for (Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) return candidate;
        }
        return null;
    }

    private static boolean pathContains(Path path, String needle) {
        return path != null && path.toString().toLowerCase(Locale.ROOT).contains(needle);
    }
}
