package com.minecraftoverlay;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.registry.entry.RegistryEntry;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class MinecraftOverlayTimeTools {
    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm:ss a", Locale.ROOT);
    private static final long DEFAULT_TIMER_MILLIS = TimeUnit.MINUTES.toMillis(5);

    private static Tool activeTool = Tool.STOPWATCH;
    private static long timerDurationMillis = DEFAULT_TIMER_MILLIS;
    private static long timerRemainingMillis = DEFAULT_TIMER_MILLIS;
    private static long timerEndMillis;
    private static boolean timerRunning;
    private static boolean timerSoundPlayed;
    private static long stopwatchAccumulatedMillis;
    private static long stopwatchStartedMillis;
    private static boolean stopwatchRunning;

    private MinecraftOverlayTimeTools() {
    }

    public static String clockText() {
        return LocalTime.now().format(CLOCK_FORMAT);
    }

    public static Tool activeTool() {
        return activeTool;
    }

    public static void setActiveTool(Tool tool) {
        activeTool = tool == null ? Tool.STOPWATCH : tool;
    }

    public static String toggleActive() {
        return activeTool == Tool.TIMER ? toggleTimer() : toggleStopwatch();
    }

    public static String resetActive() {
        return activeTool == Tool.TIMER ? resetTimer() : resetStopwatch();
    }

    public static String toggleTimer() {
        setActiveTool(Tool.TIMER);
        if (timerRunning) {
            timerRemainingMillis = getTimerRemainingMillis();
            timerRunning = false;
            return "Timer paused.";
        }
        if (timerRemainingMillis <= 0L) timerRemainingMillis = timerDurationMillis;
        timerEndMillis = System.currentTimeMillis() + timerRemainingMillis;
        timerRunning = true;
        timerSoundPlayed = false;
        return "Timer started.";
    }

    public static String resetTimer() {
        setActiveTool(Tool.TIMER);
        timerRunning = false;
        timerRemainingMillis = timerDurationMillis;
        timerSoundPlayed = false;
        return "Timer reset.";
    }

    public static String adjustTimerMinutes(int deltaMinutes) {
        setActiveTool(Tool.TIMER);
        long delta = TimeUnit.MINUTES.toMillis(deltaMinutes);
        timerDurationMillis = Math.max(TimeUnit.SECONDS.toMillis(10), Math.min(TimeUnit.HOURS.toMillis(24), timerDurationMillis + delta));
        if (!timerRunning) {
            timerRemainingMillis = timerDurationMillis;
        } else {
            long remaining = Math.max(0L, getTimerRemainingMillis() + delta);
            timerEndMillis = System.currentTimeMillis() + remaining;
            timerRemainingMillis = remaining;
        }
        return "Timer set to " + formatDuration(timerDurationMillis) + ".";
    }

    public static String toggleStopwatch() {
        setActiveTool(Tool.STOPWATCH);
        if (stopwatchRunning) {
            stopwatchAccumulatedMillis = getStopwatchMillis();
            stopwatchRunning = false;
            return "Stopwatch paused.";
        }
        stopwatchStartedMillis = System.currentTimeMillis();
        stopwatchRunning = true;
        return "Stopwatch started.";
    }

    public static String resetStopwatch() {
        setActiveTool(Tool.STOPWATCH);
        stopwatchRunning = false;
        stopwatchAccumulatedMillis = 0L;
        return "Stopwatch reset.";
    }

    public static boolean isTimerRunning() {
        updateTimerIfDone();
        return timerRunning;
    }

    public static boolean isStopwatchRunning() {
        return stopwatchRunning;
    }

    public static String timerText() {
        return formatDuration(getTimerRemainingMillis());
    }

    public static String stopwatchText() {
        return formatDuration(getStopwatchMillis());
    }

    private static long getTimerRemainingMillis() {
        if (!timerRunning) return Math.max(0L, timerRemainingMillis);
        long remaining = Math.max(0L, timerEndMillis - System.currentTimeMillis());
        if (remaining == 0L) {
            timerRunning = false;
            timerRemainingMillis = 0L;
            playTimerSoundOnce();
        }
        return remaining;
    }

    private static long getStopwatchMillis() {
        return stopwatchRunning ? stopwatchAccumulatedMillis + Math.max(0L, System.currentTimeMillis() - stopwatchStartedMillis) : stopwatchAccumulatedMillis;
    }

    private static void updateTimerIfDone() {
        getTimerRemainingMillis();
    }

    public static String nextTimerSound(String currentId) {
        TimerSound[] sounds = TimerSound.values();
        TimerSound current = timerSound(currentId);
        int next = (current.ordinal() + 1) % sounds.length;
        return sounds[next].id;
    }

    public static String timerSoundLabel(String id) {
        return timerSound(id).label;
    }

    public static void previewTimerSound(String id) {
        playTimerSound(timerSound(id));
    }

    private static void playTimerSoundOnce() {
        if (timerSoundPlayed)
            return;
        timerSoundPlayed = true;
        MinecraftOverlayConfig config = MinecraftOverlayScreen.getConfig();
        playTimerSound(timerSound(config.timerSound));
    }

    private static void playTimerSound(TimerSound sound) {
        if (sound == TimerSound.NONE)
            return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getSoundManager() == null)
            return;
        client.getSoundManager().play(PositionedSoundInstance.master(sound.soundEvent, 1.0F));
    }

    private static TimerSound timerSound(String id) {
        if (id != null) {
            for (TimerSound sound : TimerSound.values()) {
                if (sound.id.equalsIgnoreCase(id))
                    return sound;
            }
        }
        return TimerSound.PLING;
    }

    private static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, Math.round(millis / 1000.0D));
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public enum Tool {
        TIMER,
        STOPWATCH
    }

    private enum TimerSound {
        NONE("none", "None", null),
        PLING("pling", "Pling", SoundEvents.BLOCK_NOTE_BLOCK_PLING),
        BELL("bell", "Bell", RegistryEntry.of(SoundEvents.BLOCK_BELL_USE)),
        LEVEL_UP("level_up", "Level Up", RegistryEntry.of(SoundEvents.ENTITY_PLAYER_LEVELUP)),
        ANVIL("anvil", "Anvil", RegistryEntry.of(SoundEvents.BLOCK_ANVIL_LAND)),
        BUTTON("button", "Button", SoundEvents.UI_BUTTON_CLICK);

        private final String id;
        private final String label;
        private final RegistryEntry<SoundEvent> soundEvent;

        TimerSound(String id, String label, RegistryEntry<SoundEvent> soundEvent) {
            this.id = id;
            this.label = label;
            this.soundEvent = soundEvent;
        }
    }
}
