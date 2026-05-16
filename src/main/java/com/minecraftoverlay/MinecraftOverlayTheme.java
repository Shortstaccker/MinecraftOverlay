package com.minecraftoverlay;

public enum MinecraftOverlayTheme {
    CUSTOM("Custom", 195.0F, 0.75F, 1.0F, 220.0F, 0.12F, 0.18F),
    SPOTIFY("Spotify", 141.0F, 0.72F, 0.86F, 150.0F, 0.22F, 0.12F),
    DISCORD("Discord", 235.0F, 0.66F, 1.0F, 229.0F, 0.24F, 0.16F),
    LUNAR("Lunar", 205.0F, 0.88F, 1.0F, 218.0F, 0.20F, 0.13F),
    MIDNIGHT("Midnight", 193.0F, 0.38F, 0.92F, 225.0F, 0.28F, 0.10F);

    private final String label;
    private final float hue;
    private final float saturation;
    private final float brightness;
    private final float tabHue;
    private final float tabSaturation;
    private final float tabBrightness;

    MinecraftOverlayTheme(String label, float hue, float saturation, float brightness, float tabHue, float tabSaturation, float tabBrightness) {
        this.label = label;
        this.hue = hue;
        this.saturation = saturation;
        this.brightness = brightness;
        this.tabHue = tabHue;
        this.tabSaturation = tabSaturation;
        this.tabBrightness = tabBrightness;
    }

    public String label() {
        return label;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    public void apply(MinecraftOverlayConfig config) {
        if (isCustom()) return;
        config.hue = hue;
        config.saturation = saturation;
        config.brightness = brightness;
        config.tabHue = tabHue;
        config.tabSaturation = tabSaturation;
        config.tabBrightness = tabBrightness;
    }

    public static MinecraftOverlayTheme fromConfig(String value) {
        if (value == null || value.isBlank()) return CUSTOM;
        for (MinecraftOverlayTheme theme : values()) {
            if (theme.name().equalsIgnoreCase(value) || theme.label.equalsIgnoreCase(value)) return theme;
        }
        return CUSTOM;
    }
}
