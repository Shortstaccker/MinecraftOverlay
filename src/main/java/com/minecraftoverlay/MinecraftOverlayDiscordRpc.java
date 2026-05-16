package com.minecraftoverlay;

import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinecraftOverlayDiscordRpc {
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MinecraftOverlay Discord RPC");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final AtomicBoolean updateQueued = new AtomicBoolean();
    private static final long STARTED_AT = Instant.now().getEpochSecond();
    private static RandomAccessFile pipe;
    private static String connectedClientId = "";
    private static long lastUpdateNanos;
    private static String lastStatus = "Discord RPC disabled.";
    private static String lastActivityPayload = "";

    private MinecraftOverlayDiscordRpc() {
    }

    public static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        if (now - lastUpdateNanos < TimeUnit.SECONDS.toNanos(15)) return;
        lastUpdateNanos = now;
        refresh(client);
    }

    public static void refresh(MinecraftClient client) {
        MinecraftOverlayConfig config = MinecraftOverlayScreen.getConfig();
        String clientId = config.discordApplicationId == null ? "" : config.discordApplicationId.trim();
        if (!config.discordRpcEnabled || clientId.isEmpty()) {
            queueDisconnect(clientId.isEmpty() ? "Discord RPC needs an application ID." : "Discord RPC disabled.");
            return;
        }
        if (!updateQueued.compareAndSet(false, true)) return;
        EXECUTOR.execute(() -> {
            try {
                updateActivity(client, clientId);
            } catch (Throwable exception) {
                lastStatus = "Discord RPC failed: " + exception.getClass().getSimpleName();
                MinecraftOverlay.LOGGER.warn("Failed to update Discord RPC", exception);
                closePipe();
            } finally {
                updateQueued.set(false);
            }
        });
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    private static void queueDisconnect(String status) {
        lastStatus = status;
        if (pipe == null) return;
        EXECUTOR.execute(MinecraftOverlayDiscordRpc::closePipe);
    }

    private static void updateActivity(MinecraftClient client, String clientId) throws IOException {
        if (pipe == null || !clientId.equals(connectedClientId)) {
            closePipe();
            pipe = openDiscordPipe();
            connectedClientId = clientId;
            sendFrame(0, handshake(clientId));
        }
        String activityPayload = activity(client);
        if (!activityPayload.equals(lastActivityPayload)) {
            sendFrame(1, activityPayload);
            lastActivityPayload = activityPayload;
        }
        lastStatus = "Discord RPC active as application " + clientId + ".";
    }

    private static RandomAccessFile openDiscordPipe() throws IOException {
        IOException lastException = null;
        for (int i = 0; i < 10; i++) {
            try {
                return new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
            } catch (IOException exception) {
                lastException = exception;
            }
        }
        throw lastException == null ? new IOException("Discord IPC pipe not found") : lastException;
    }

    private static String handshake(String clientId) {
        JsonObject payload = new JsonObject();
        payload.addProperty("v", 1);
        payload.addProperty("client_id", clientId);
        return payload.toString();
    }

    private static String activity(MinecraftClient client) {
        JsonObject timestamps = new JsonObject();
        timestamps.addProperty("start", STARTED_AT);

        JsonObject activity = new JsonObject();
        activity.addProperty("name", "MinecraftOverlay");
        activity.addProperty("type", 0);
        activity.addProperty("details", "Using MinecraftOverlay");
        activity.addProperty("state", getClientState(client));
        activity.add("timestamps", timestamps);
        activity.addProperty("instance", false);

        JsonObject args = new JsonObject();
        args.addProperty("pid", ProcessHandle.current().pid());
        args.add("activity", activity);

        JsonObject payload = new JsonObject();
        payload.addProperty("cmd", "SET_ACTIVITY");
        payload.add("args", args);
        payload.addProperty("nonce", UUID.randomUUID().toString());
        return payload.toString();
    }

    private static String getClientState(MinecraftClient client) {
        if (client == null) return "Starting Minecraft";
        if (client.getCurrentServerEntry() != null && client.getCurrentServerEntry().address != null) {
            return "Playing " + sanitizeServer(client.getCurrentServerEntry().address);
        }
        if (client.world != null) return client.isInSingleplayer() ? "In singleplayer" : "In multiplayer";
        return "In menus";
    }

    private static String sanitizeServer(String address) {
        String trimmed = address.trim();
        if (trimmed.length() > 64) trimmed = trimmed.substring(0, 64);
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static void sendFrame(int opcode, String payload) throws IOException {
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = ByteBuffer.allocate(8 + payloadBytes.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(opcode);
        buffer.putInt(payloadBytes.length);
        buffer.put(payloadBytes);
        pipe.write(buffer.array());
    }

    private static void closePipe() {
        connectedClientId = "";
        lastActivityPayload = "";
        if (pipe == null) return;
        try {
            pipe.close();
        } catch (IOException ignored) {
        } finally {
            pipe = null;
        }
    }
}
