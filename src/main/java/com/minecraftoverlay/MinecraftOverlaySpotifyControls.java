package com.minecraftoverlay;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.MinecraftClient;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MinecraftOverlaySpotifyControls {
    private static final HttpClient COVER_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();
    private static final HttpClient SPOTIFY_HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private static final String SPOTIFY_REDIRECT_URI = "http://127.0.0.1:8743/spotify/callback";
    private static final String SPOTIFY_SCOPES = "user-read-private user-read-playback-state user-modify-playback-state";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MinecraftOverlay Spotify Controls");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final ExecutorService COVER_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MinecraftOverlay Spotify Cover Art");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final ExecutorService SEARCH_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "MinecraftOverlay Spotify Search");
            thread.setDaemon(true);
            return thread;
        }
    });
    private static final AtomicBoolean commandQueued = new AtomicBoolean();
    private static final AtomicBoolean coverLookupQueued = new AtomicBoolean();
    private static final AtomicBoolean searchQueued = new AtomicBoolean();
    private static final Object pendingLock = new Object();
    private static long lastRefreshNanos;
    private static long lastCoverRefreshNanos;
    private static volatile long lastCoverLookupAttemptNanos;
    private static volatile String pendingAction = "";
    private static volatile String pendingQueuedStatus = "";
    private static volatile String lastStatus = "Spotify controls idle.";
    private static volatile String playbackStatus = "Unknown";
    private static volatile String trackTitle = "";
    private static volatile String trackArtist = "";
    private static volatile String coverArtBase64 = "";
    private static volatile String coverTrackKey = "";
    private static volatile String coverLookupKey = "";
    private static volatile float volume = 1.0F;
    private static volatile double positionSeconds;
    private static volatile double durationSeconds;
    private static volatile String searchQuery = "";
    private static volatile String searchStatus = "Search for a song.";
    private static volatile List<SpotifySearchResult> searchResults = List.of();
    private static volatile int searchVersion;
    private static volatile String spotifyClientId = "";
    private static volatile String spotifyAccessToken = "";
    private static volatile String spotifyRefreshToken = "";
    private static volatile long spotifyTokenExpiresAtMillis;
    private static volatile String spotifyAuthStatus = "Spotify API not connected.";
    private static volatile String oauthVerifier = "";
    private static volatile String oauthState = "";
    private static HttpServer oauthServer;

    private MinecraftOverlaySpotifyControls() {
    }

    public static void tick(MinecraftClient client) {
        long now = System.nanoTime();
        if (now - lastRefreshNanos < TimeUnit.MILLISECONDS.toNanos(2_500)) return;
        lastRefreshNanos = now;
        refresh(false);
    }

    public static void refresh() {
        refresh(true);
    }

    private static void refresh(boolean forceCover) {
        long now = System.nanoTime();
        if (commandQueued.get()) return;
        boolean includeCover = forceCover || coverArtBase64.isBlank() || now - lastCoverRefreshNanos > TimeUnit.SECONDS.toNanos(45);
        if (includeCover) lastCoverRefreshNanos = now;
        queueCommand(includeCover ? "status:cover" : "status", forceCover ? "Refreshing Spotify." : "");
    }

    public static String playPause() {
        return queueCommand("playpause", "Spotify play/pause requested.");
    }

    public static String nextTrack() {
        lastCoverRefreshNanos = 0L;
        return queueCommand("next", "Spotify next requested.");
    }

    public static String previousTrack() {
        lastCoverRefreshNanos = 0L;
        return queueCommand("previous", "Spotify previous requested.");
    }

    public static String stop() {
        return queueCommand("pause", "Spotify pause requested.");
    }

    public static String setVolume(float value) {
        return queueCommand("volume:" + value, "Spotify volume set to " + Math.round(value * 100) + "%.");
    }

    public static void configureSpotifyApi(String clientId, String accessToken, String refreshToken, long expiresAtMillis) {
        spotifyClientId = clientId == null ? "" : clientId.trim();
        spotifyAccessToken = accessToken == null ? "" : accessToken.trim();
        spotifyRefreshToken = refreshToken == null ? "" : refreshToken.trim();
        spotifyTokenExpiresAtMillis = expiresAtMillis;
        spotifyAuthStatus = isApiConnected() ? "Spotify API connected." : "Spotify API not connected.";
    }

    public static String startAuthorization(String clientId) {
        spotifyClientId = clientId == null ? "" : clientId.trim();
        if (spotifyClientId.isBlank()) {
            spotifyAuthStatus = "Paste a Spotify Client ID first.";
            bumpSearchUi();
            return spotifyAuthStatus;
        }
        try {
            stopOauthServer();
            oauthVerifier = createCodeVerifier();
            oauthState = createCodeVerifier().substring(0, 24);
            String challenge = createCodeChallenge(oauthVerifier);
            oauthServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 8743), 0);
            oauthServer.createContext("/spotify/callback", MinecraftOverlaySpotifyControls::handleOauthCallback);
            oauthServer.setExecutor(SEARCH_EXECUTOR);
            oauthServer.start();
            String authorizeUrl = "https://accounts.spotify.com/authorize?response_type=code"
                    + "&client_id=" + urlEncode(spotifyClientId)
                    + "&scope=" + urlEncode(SPOTIFY_SCOPES)
                    + "&redirect_uri=" + urlEncode(SPOTIFY_REDIRECT_URI)
                    + "&code_challenge_method=S256"
                    + "&code_challenge=" + urlEncode(challenge)
                    + "&state=" + urlEncode(oauthState)
                    + "&show_dialog=true";
            openExternalUri(authorizeUrl);
            spotifyAuthStatus = "Approve Spotify in your browser.";
        } catch (Throwable exception) {
            spotifyAuthStatus = "Could not start Spotify login.";
            MinecraftOverlay.LOGGER.warn("Failed to start Spotify authorization", exception);
            stopOauthServer();
        }
        bumpSearchUi();
        return spotifyAuthStatus;
    }

    public static String openDeveloperDashboard() {
        try {
            openExternalUri("https://developer.spotify.com/dashboard/create");
            return "Opened Spotify app creation page.";
        } catch (IOException exception) {
            MinecraftOverlay.LOGGER.warn("Failed to open Spotify app creation page", exception);
            return "Could not open Spotify app page.";
        }
    }

    public static String search(String query) {
        String trimmedQuery = query == null ? "" : query.trim();
        searchQuery = trimmedQuery;
        if (trimmedQuery.isBlank()) {
            searchResults = List.of();
            searchStatus = "Type a song or artist first.";
            searchVersion++;
            return searchStatus;
        }
        if (!searchQueued.compareAndSet(false, true)) {
            searchStatus = "Spotify search already running.";
            return searchStatus;
        }
        searchStatus = "Searching Spotify...";
        searchVersion++;
        SEARCH_EXECUTOR.execute(() -> searchAsync(trimmedQuery));
        return searchStatus;
    }

    public static String playSearchResult(int index) {
        List<SpotifySearchResult> results = searchResults;
        if (index < 0 || index >= results.size()) {
            searchStatus = "Search result is no longer available.";
            bumpSearchUi();
            return searchStatus;
        }
        SpotifySearchResult result = results.get(index);
        if (isApiConnected()) {
            searchStatus = "Starting: " + result.title();
            bumpSearchUi();
            SEARCH_EXECUTOR.execute(() -> playSpotifyTrackAsync(result));
            return searchStatus;
        }
        String query = resultQuery(result);
        if (query.isBlank()) {
            searchStatus = "Search result is missing a title.";
            bumpSearchUi();
            return searchStatus;
        }
        searchStatus = "Connect Spotify in settings to play from the overlay.";
        bumpSearchUi();
        return searchStatus;
    }

    public static String getSearchQuery() {
        return searchQuery;
    }

    public static String getSearchStatus() {
        return searchStatus;
    }

    public static List<SpotifySearchResult> getSearchResults() {
        return searchResults;
    }

    public static int getSearchVersion() {
        return searchVersion;
    }

    public static String getClientId() {
        return spotifyClientId;
    }

    public static boolean isApiConnected() {
        return !spotifyClientId.isBlank() && !spotifyRefreshToken.isBlank();
    }

    public static String getLastStatus() {
        return lastStatus;
    }

    public static String getPlaybackStatus() {
        return playbackStatus;
    }

    public static String getTrackTitle() {
        return trackTitle;
    }

    public static String getTrackArtist() {
        return trackArtist;
    }

    public static String getCoverArtBase64() {
        return coverArtBase64;
    }

    public static float getVolume() {
        return volume;
    }

    public static boolean isPlaying() {
        return "Playing".equalsIgnoreCase(playbackStatus);
    }

    public static double getProgressRatio() {
        if (durationSeconds <= 0.0D) return 0.0D;
        return Math.max(0.0D, Math.min(1.0D, positionSeconds / durationSeconds));
    }

    public static String getTimeSummary() {
        if (durationSeconds <= 0.0D) return "--:-- / --:--";
        return formatTime(positionSeconds) + " / " + formatTime(durationSeconds);
    }

    public static String getTrackSummary() {
        if (trackTitle.isBlank()) return lastStatus;
        String artist = trackArtist.isBlank() ? "" : " - " + trackArtist;
        return playbackStatus + ": " + trackTitle + artist;
    }

    private static String queueCommand(String action, String queuedStatus) {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            lastStatus = "Spotify controls need Windows media sessions.";
            return lastStatus;
        }
        boolean statusAction = action.startsWith("status");
        if (!commandQueued.compareAndSet(false, true)) {
            if (!statusAction) {
                synchronized (pendingLock) {
                    pendingAction = action;
                    pendingQueuedStatus = queuedStatus;
                }
                if (queuedStatus != null && !queuedStatus.isBlank()) lastStatus = queuedStatus;
            }
            return lastStatus;
        }
        if (queuedStatus != null && !queuedStatus.isBlank()) lastStatus = queuedStatus;
        EXECUTOR.execute(() -> runQueuedCommands(action));
        return lastStatus;
    }

    private static void runQueuedCommands(String initialAction) {
        try {
            String currentAction = initialAction;
            while (currentAction != null && !currentAction.isBlank()) {
                updateFromBridge(currentAction);
                synchronized (pendingLock) {
                    currentAction = pendingAction;
                    if (pendingQueuedStatus != null && !pendingQueuedStatus.isBlank()) lastStatus = pendingQueuedStatus;
                    pendingAction = "";
                    pendingQueuedStatus = "";
                }
            }
        } catch (Throwable exception) {
            lastStatus = "Spotify command failed: " + exception.getClass().getSimpleName();
            MinecraftOverlay.LOGGER.warn("Failed to control Spotify", exception);
        } finally {
            commandQueued.set(false);
            String nextAction = "";
            String nextStatus = "";
            synchronized (pendingLock) {
                if (!pendingAction.isBlank() && commandQueued.compareAndSet(false, true)) {
                    nextAction = pendingAction;
                    nextStatus = pendingQueuedStatus;
                    pendingAction = "";
                    pendingQueuedStatus = "";
                }
            }
            if (!nextAction.isBlank()) {
                if (nextStatus != null && !nextStatus.isBlank()) lastStatus = nextStatus;
                String action = nextAction;
                EXECUTOR.execute(() -> runQueuedCommands(action));
            }
        }
    }

    private static void updateFromBridge(String action) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encodePowerShell(buildBridgeScript(action))
        ).redirectErrorStream(true).start();

        StringBuilder scriptOutput = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                scriptOutput.append(line).append('\n');
            }
        }
        boolean finished = process.waitFor(10, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            lastStatus = "Spotify command timed out.";
            return;
        }
        String output = scriptOutput.toString();
        String result = "";
        for (String line : output.split("\n")) {
            if (line.trim().startsWith("SPOTIFY|")) {
                result = line.trim();
                break;
            }
        }
        if (process.exitValue() != 0 && result.isBlank()) {
            lastStatus = "Spotify command failed.";
            return;
        }
        applyBridgeResult(result);
    }

    private static void applyBridgeResult(String result) {
        if (result == null || result.isBlank()) {
            lastStatus = "Spotify did not return a media session.";
            return;
        }
        String[] parts = result.split("\\|", -1);
        if (parts.length < 5 || !"SPOTIFY".equals(parts[0])) {
            lastStatus = "Spotify returned an unreadable response.";
            return;
        }
        if ("NO_SESSION".equals(parts[1])) {
            playbackStatus = "Not running";
            trackTitle = "";
            trackArtist = "";
            coverArtBase64 = "";
            positionSeconds = 0.0D;
            durationSeconds = 0.0D;
            lastStatus = "Spotify is not playing.";
            return;
        }
        if ("ERROR".equals(parts[1])) {
            lastStatus = parts.length > 2 && !parts[2].isBlank() ? parts[2] : "Spotify command failed.";
            return;
        }
        playbackStatus = parts[1].isBlank() ? "Unknown" : parts[1];
        String newTrackTitle = parts[2];
        String newTrackArtist = parts[3];
        String newCoverTrackKey = coverKey(newTrackTitle, newTrackArtist);
        if (!newCoverTrackKey.equals(coverTrackKey)) {
            coverTrackKey = newCoverTrackKey;
            coverLookupKey = "";
            lastCoverLookupAttemptNanos = 0L;
            coverArtBase64 = "";
        }
        trackTitle = newTrackTitle;
        trackArtist = newTrackArtist;
        String actionResult = parts[4];
        positionSeconds = parts.length > 5 ? parseSeconds(parts[5]) : 0.0D;
        durationSeconds = parts.length > 6 ? parseSeconds(parts[6]) : 0.0D;
        if (parts.length > 7 && !parts[7].isBlank()) {
            coverArtBase64 = parts[7];
            coverLookupKey = coverTrackKey;
        } else if (coverArtBase64.isBlank()) {
            fetchCoverArtFallback(newTrackTitle, newTrackArtist);
        }
        volume = parts.length > 8 ? (float) parseSeconds(parts[8]) : 1.0F;

        String artist = trackArtist.isBlank() ? "" : " - " + trackArtist;
        lastStatus = ("OK".equals(actionResult) ? playbackStatus : actionResult) + ": " + (trackTitle.isBlank() ? "Spotify" : trackTitle + artist);
    }

    private static void fetchCoverArtFallback(String title, String artist) {
        String key = coverKey(title, artist);
        if (key.isBlank()) return;
        long now = System.nanoTime();
        if (key.equals(coverLookupKey) && now - lastCoverLookupAttemptNanos < TimeUnit.SECONDS.toNanos(30)) return;
        if (!coverLookupQueued.compareAndSet(false, true)) return;
        coverLookupKey = key;
        lastCoverLookupAttemptNanos = now;
        COVER_EXECUTOR.execute(() -> fetchCoverArtFallbackAsync(title, artist, key));
    }

    private static void searchAsync(String query) {
        try {
            if (isApiConnected() && ensureSpotifyAccessToken()) {
                searchSpotifyApiAsync(query);
                return;
            }
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
            HttpRequest searchRequest = HttpRequest.newBuilder(URI.create("https://api.deezer.com/search?limit=5&q=" + encodedQuery))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "MinecraftOverlay/1.0")
                    .GET()
                    .build();
            HttpResponse<String> searchResponse = COVER_HTTP_CLIENT.send(searchRequest,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            JsonArray results = null;
            if (searchResponse.statusCode() / 100 == 2 && searchResponse.body() != null
                    && !searchResponse.body().isBlank()) {
                JsonObject root = JsonParser.parseString(searchResponse.body()).getAsJsonObject();
                results = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : null;
            }
            if (results == null || results.isEmpty()) {
                searchItunesPreviewAsync(query);
                return;
            }
            List<SpotifySearchResult> parsedResults = new ArrayList<>();
            for (JsonElement element : results) {
                if (!element.isJsonObject())
                    continue;
                JsonObject track = element.getAsJsonObject();
                String title = getJsonString(track, "title_short");
                if (title.isBlank())
                    title = getJsonString(track, "title");
                JsonObject artistObject = track.has("artist") && track.get("artist").isJsonObject()
                        ? track.getAsJsonObject("artist")
                        : null;
                JsonObject albumObject = track.has("album") && track.get("album").isJsonObject()
                        ? track.getAsJsonObject("album")
                        : null;
                String artist = artistObject == null ? "" : getJsonString(artistObject, "name");
                String album = albumObject == null ? "" : getJsonString(albumObject, "title");
                if (!title.isBlank())
                    parsedResults.add(new SpotifySearchResult(title, artist, album, ""));
            }
            searchResults = List.copyOf(parsedResults);
            searchStatus = parsedResults.isEmpty() ? "No songs found." : "Connect Spotify to play results directly.";
            bumpSearchUi();
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to search Spotify tracks", exception);
            try {
                searchItunesPreviewAsync(query);
            } catch (Throwable fallbackException) {
                searchResults = List.of();
                searchStatus = "Spotify search failed.";
                bumpSearchUi();
                MinecraftOverlay.LOGGER.warn("Fallback music search failed", fallbackException);
            }
        } finally {
            searchQueued.set(false);
        }
    }

    private static void searchItunesPreviewAsync(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest searchRequest = HttpRequest.newBuilder(
                URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=5&term=" + encodedQuery))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> response = COVER_HTTP_CLIENT.send(searchRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
            searchResults = List.of();
            searchStatus = "Music search failed.";
            bumpSearchUi();
            return;
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray results = root.has("results") && root.get("results").isJsonArray() ? root.getAsJsonArray("results") : null;
        if (results == null || results.isEmpty()) {
            searchResults = List.of();
            searchStatus = "No songs found.";
            bumpSearchUi();
            return;
        }
        List<SpotifySearchResult> parsedResults = new ArrayList<>();
        for (JsonElement element : results) {
            if (!element.isJsonObject())
                continue;
            JsonObject track = element.getAsJsonObject();
            String title = getJsonString(track, "trackName");
            String artist = getJsonString(track, "artistName");
            String album = getJsonString(track, "collectionName");
            if (!title.isBlank())
                parsedResults.add(new SpotifySearchResult(title, artist, album, ""));
        }
        searchResults = List.copyOf(parsedResults);
        searchStatus = parsedResults.isEmpty() ? "No songs found." : "Connect Spotify to play results directly.";
        bumpSearchUi();
    }

    private static void searchSpotifyApiAsync(String query) throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest searchRequest = HttpRequest.newBuilder(
                URI.create("https://api.spotify.com/v1/search?type=track&limit=5&q=" + encodedQuery))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bearer " + spotifyAccessToken)
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> response = SPOTIFY_HTTP_CLIENT.send(searchRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
            searchResults = List.of();
            if (response.statusCode() == 403) {
                searchResults = List.of();
                searchStatus = spotifyApiError("Spotify blocked search", response);
                MinecraftOverlay.LOGGER.warn("Spotify search blocked: HTTP {} {}", response.statusCode(),
                        abbreviate(response.body(), 300));
                bumpSearchUi();
                return;
            }
            searchStatus = switch (response.statusCode()) {
                case 401 -> "Spotify login expired. Connect again.";
                case 429 -> "Spotify rate limited search. Try again soon.";
                default -> spotifyApiError("Spotify search failed", response);
            };
            MinecraftOverlay.LOGGER.warn("Spotify search failed: HTTP {} {}", response.statusCode(),
                    abbreviate(response.body(), 300));
            bumpSearchUi();
            return;
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject tracks = root.has("tracks") && root.get("tracks").isJsonObject() ? root.getAsJsonObject("tracks") : null;
        JsonArray items = tracks != null && tracks.has("items") && tracks.get("items").isJsonArray()
                ? tracks.getAsJsonArray("items")
                : null;
        if (items == null || items.isEmpty()) {
            searchResults = List.of();
            searchStatus = "No songs found.";
            bumpSearchUi();
            return;
        }
        List<SpotifySearchResult> parsedResults = new ArrayList<>();
        for (JsonElement element : items) {
            if (!element.isJsonObject())
                continue;
            JsonObject track = element.getAsJsonObject();
            String title = getJsonString(track, "name");
            String uri = getJsonString(track, "uri");
            String album = "";
            JsonObject albumObject = track.has("album") && track.get("album").isJsonObject()
                    ? track.getAsJsonObject("album")
                    : null;
            if (albumObject != null)
                album = getJsonString(albumObject, "name");
            String artist = "";
            JsonArray artists = track.has("artists") && track.get("artists").isJsonArray()
                    ? track.getAsJsonArray("artists")
                    : null;
            if (artists != null && !artists.isEmpty() && artists.get(0).isJsonObject())
                artist = getJsonString(artists.get(0).getAsJsonObject(), "name");
            if (!title.isBlank() && !uri.isBlank())
                parsedResults.add(new SpotifySearchResult(title, artist, album, uri));
        }
        searchResults = List.copyOf(parsedResults);
        searchStatus = parsedResults.isEmpty() ? "No songs found." : "Pick a result to play.";
        bumpSearchUi();
    }

    private static String getJsonString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? "" : element.getAsString().trim();
    }

    private static boolean getJsonBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static void playSpotifyTrackAsync(SpotifySearchResult result) {
        try {
            if (!ensureSpotifyAccessToken()) {
                searchStatus = "Connect Spotify in settings to play from the overlay.";
                bumpSearchUi();
                return;
            }
            if (result.uri().isBlank()) {
                searchStatus = "Finding Spotify track: " + result.title();
                bumpSearchUi();
            }
            String uri = result.uri().isBlank() ? findSpotifyTrackUri(result) : result.uri();
            if (uri.isBlank()) {
                if (searchStatus == null || searchStatus.isBlank() || searchStatus.startsWith("Starting:")
                        || searchStatus.startsWith("Finding Spotify track:")) {
                    searchStatus = "No Spotify track found for: " + result.title();
                }
                bumpSearchUi();
                return;
            }
            String deviceId = findSpotifyPlaybackDeviceId();
            HttpResponse<String> response = playSpotifyUri(uri, deviceId);
            if (response.statusCode() == 204 || response.statusCode() == 202) {
                searchStatus = "Playing: " + result.title();
                lastCoverRefreshNanos = System.nanoTime();
                refresh(true);
            } else if (response.statusCode() == 403) {
                searchStatus = "Spotify Premium is required for direct playback.";
            } else if (response.statusCode() == 404) {
                searchStatus = deviceId.isBlank()
                        ? "Open Spotify once so it appears as a playback device."
                        : "Spotify device is unavailable. Start Spotify playback once.";
            } else {
                searchStatus = "Spotify play failed: HTTP " + response.statusCode();
            }
        } catch (Throwable exception) {
            searchStatus = "Spotify play failed.";
            MinecraftOverlay.LOGGER.warn("Failed to play Spotify track", exception);
        } finally {
            bumpSearchUi();
        }
    }

    private static String resultQuery(SpotifySearchResult result) {
        return (result.title() + " " + result.artist()).trim();
    }

    private static String findSpotifyTrackUri(SpotifySearchResult result) throws IOException, InterruptedException {
        String title = result.title() == null ? "" : result.title().trim();
        String artist = result.artist() == null ? "" : result.artist().trim();
        String album = result.album() == null ? "" : result.album().trim();
        if (title.isBlank())
            return "";
        if (!ensureSpotifyAccessToken())
            return "";

        List<String> queries = new ArrayList<>();
        if (!artist.isBlank())
            queries.add(title + " " + artist);
        if (!artist.isBlank() && !album.isBlank())
            queries.add(title + " " + artist + " " + album);
        if (!artist.isBlank())
            queries.add("track:" + title + " artist:" + artist);
        queries.add(title);

        String firstPlayableUri = "";
        for (String query : queries) {
            String uri = searchSpotifyTrackUri(query, result, true);
            if (!uri.isBlank())
                return uri;
            if (firstPlayableUri.isBlank())
                firstPlayableUri = searchSpotifyTrackUri(query, result, false);
        }
        return firstPlayableUri;
    }

    private static String searchSpotifyTrackUri(String query, SpotifySearchResult result, boolean requireTitleMatch)
            throws IOException, InterruptedException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20");
        HttpRequest searchRequest = HttpRequest.newBuilder(
                URI.create("https://api.spotify.com/v1/search?q=" + encodedQuery + "&type=track&limit=10"))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bearer " + spotifyAccessToken)
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> response = SPOTIFY_HTTP_CLIENT.send(searchRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank()) {
            searchStatus = switch (response.statusCode()) {
                case 401 -> "Spotify login expired. Connect again.";
                case 403 -> spotifyApiError("Spotify blocked lookup", response);
                case 429 -> "Spotify rate limited lookup. Try again soon.";
                default -> spotifyApiError("Spotify lookup failed", response);
            };
            MinecraftOverlay.LOGGER.warn("Spotify lookup failed: HTTP {} {}", response.statusCode(),
                    abbreviate(response.body(), 300));
            return "";
        }
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonObject tracks = root.has("tracks") && root.get("tracks").isJsonObject() ? root.getAsJsonObject("tracks") : null;
        JsonArray items = tracks != null && tracks.has("items") && tracks.get("items").isJsonArray()
                ? tracks.getAsJsonArray("items")
                : null;
        if (items == null || items.isEmpty() || !items.get(0).isJsonObject())
            return "";
        String fallbackUri = "";
        String wantedTitle = normalizeTrackText(result.title());
        String wantedArtist = normalizeTrackText(result.artist());
        for (JsonElement element : items) {
            if (!element.isJsonObject())
                continue;
            JsonObject track = element.getAsJsonObject();
            String uri = getJsonString(track, "uri");
            if (uri.isBlank())
                continue;
            if (fallbackUri.isBlank())
                fallbackUri = uri;
            if (!requireTitleMatch)
                return uri;
            String foundTitle = normalizeTrackText(getJsonString(track, "name"));
            if (!wantedTitle.isBlank() && !foundTitle.equals(wantedTitle))
                continue;
            if (wantedArtist.isBlank())
                return uri;
            JsonArray artists = track.has("artists") && track.get("artists").isJsonArray()
                    ? track.getAsJsonArray("artists")
                    : null;
            if (artists == null || artists.isEmpty())
                return uri;
            for (JsonElement artistElement : artists) {
                if (artistElement.isJsonObject()
                        && normalizeTrackText(getJsonString(artistElement.getAsJsonObject(), "name")).equals(wantedArtist)) {
                    return uri;
                }
            }
        }
        return requireTitleMatch ? "" : fallbackUri;
    }

    private static String normalizeTrackText(String value) {
        if (value == null)
            return "";
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("\\([^)]*\\)", "")
                .replaceAll("\\[[^]]*\\]", "")
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private static HttpResponse<String> playSpotifyUri(String uri, String deviceId) throws IOException, InterruptedException {
        String body = "{\"uris\":[\"" + escapeJson(uri) + "\"]}";
        String endpoint = "https://api.spotify.com/v1/me/player/play";
        if (deviceId != null && !deviceId.isBlank())
            endpoint += "?device_id=" + urlEncode(deviceId);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bearer " + spotifyAccessToken)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return SPOTIFY_HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String findSpotifyPlaybackDeviceId() throws IOException, InterruptedException {
        if (!ensureSpotifyAccessToken())
            return "";
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.spotify.com/v1/me/player/devices"))
                .timeout(Duration.ofSeconds(6))
                .header("Authorization", "Bearer " + spotifyAccessToken)
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> response = SPOTIFY_HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank())
            return "";
        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray devices = root.has("devices") && root.get("devices").isJsonArray()
                ? root.getAsJsonArray("devices")
                : null;
        if (devices == null || devices.isEmpty())
            return "";
        String firstPlayable = "";
        for (JsonElement element : devices) {
            if (!element.isJsonObject())
                continue;
            JsonObject device = element.getAsJsonObject();
            if (getJsonBoolean(device, "is_restricted"))
                continue;
            String id = getJsonString(device, "id");
            if (id.isBlank())
                continue;
            if (getJsonBoolean(device, "is_active"))
                return id;
            if (firstPlayable.isBlank())
                firstPlayable = id;
        }
        return firstPlayable;
    }

    private static boolean ensureSpotifyAccessToken() {
        if (spotifyClientId.isBlank() || spotifyRefreshToken.isBlank())
            return false;
        if (!spotifyAccessToken.isBlank() && System.currentTimeMillis() < spotifyTokenExpiresAtMillis - 30_000L)
            return true;
        try {
            String body = "grant_type=refresh_token"
                    + "&refresh_token=" + urlEncode(spotifyRefreshToken)
                    + "&client_id=" + urlEncode(spotifyClientId);
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = SPOTIFY_HTTP_CLIENT.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank())
                return false;
            applyTokenResponse(response.body(), false);
            return !spotifyAccessToken.isBlank();
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.warn("Failed to refresh Spotify token", exception);
            return false;
        }
    }

    private static void handleOauthCallback(HttpExchange exchange) throws IOException {
        String responseText = "Spotify login failed. You can close this tab.";
        try {
            Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
            String error = params.getOrDefault("error", "");
            String state = params.getOrDefault("state", "");
            String code = params.getOrDefault("code", "");
            if (!error.isBlank()) {
                spotifyAuthStatus = "Spotify login rejected.";
            } else if (code.isBlank() || !state.equals(oauthState)) {
                spotifyAuthStatus = "Spotify login returned invalid data.";
            } else if (exchangeCodeForToken(code)) {
                spotifyAuthStatus = "Spotify API connected.";
                searchStatus = "Spotify connected. Search and pick a song.";
                responseText = "Spotify connected. You can close this tab.";
            } else {
                spotifyAuthStatus = "Spotify login failed.";
            }
        } catch (Throwable exception) {
            spotifyAuthStatus = "Spotify login failed.";
            MinecraftOverlay.LOGGER.warn("Failed to finish Spotify authorization", exception);
        } finally {
            byte[] bytes = responseText.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
            stopOauthServer();
            bumpSearchUi();
        }
    }

    private static boolean exchangeCodeForToken(String code) throws IOException, InterruptedException {
        String body = "grant_type=authorization_code"
                + "&code=" + urlEncode(code)
                + "&redirect_uri=" + urlEncode(SPOTIFY_REDIRECT_URI)
                + "&client_id=" + urlEncode(spotifyClientId)
                + "&code_verifier=" + urlEncode(oauthVerifier);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = SPOTIFY_HTTP_CLIENT.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2 || response.body() == null || response.body().isBlank())
            return false;
        applyTokenResponse(response.body(), true);
        return !spotifyAccessToken.isBlank() && !spotifyRefreshToken.isBlank();
    }

    private static void applyTokenResponse(String body, boolean requireRefreshToken) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        spotifyAccessToken = getJsonString(json, "access_token");
        String newRefreshToken = getJsonString(json, "refresh_token");
        if (!newRefreshToken.isBlank())
            spotifyRefreshToken = newRefreshToken;
        if (requireRefreshToken && spotifyRefreshToken.isBlank())
            spotifyAccessToken = "";
        long expiresIn = (long) parseSeconds(getJsonString(json, "expires_in"));
        spotifyTokenExpiresAtMillis = System.currentTimeMillis() + Math.max(60L, expiresIn) * 1000L;
        MinecraftOverlayConfig config = MinecraftOverlayScreen.getConfig();
        config.spotifyClientId = spotifyClientId;
        config.spotifyAccessToken = spotifyAccessToken;
        config.spotifyRefreshToken = spotifyRefreshToken;
        config.spotifyTokenExpiresAtMillis = spotifyTokenExpiresAtMillis;
        config.save();
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> params = new HashMap<>();
        if (rawQuery == null || rawQuery.isBlank())
            return params;
        for (String pair : rawQuery.split("&")) {
            int equals = pair.indexOf('=');
            String key = equals >= 0 ? pair.substring(0, equals) : pair;
            String value = equals >= 0 ? pair.substring(equals + 1) : "";
            params.put(urlDecode(key), urlDecode(value));
        }
        return params;
    }

    private static String createCodeVerifier() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String createCodeChallenge(String verifier) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private static void stopOauthServer() {
        HttpServer server = oauthServer;
        oauthServer = null;
        if (server != null)
            server.stop(0);
    }

    private static void openExternalUri(String uri) throws IOException {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(uri));
            return;
        }
        new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", "Start-Process '" + uri.replace("'", "''") + "'").start();
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String urlDecode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String spotifyApiError(String prefix, HttpResponse<String> response) {
        String message = spotifyErrorMessage(response == null ? "" : response.body());
        if (message.isBlank())
            return prefix + ": HTTP " + (response == null ? "?" : response.statusCode());
        return prefix + ": " + message;
    }

    private static String spotifyErrorMessage(String body) {
        if (body == null || body.isBlank())
            return "";
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            JsonObject error = root.has("error") && root.get("error").isJsonObject()
                    ? root.getAsJsonObject("error")
                    : null;
            if (error != null) {
                String message = getJsonString(error, "message");
                if (!message.isBlank())
                    return abbreviate(message, 70);
            }
            String errorDescription = getJsonString(root, "error_description");
            if (!errorDescription.isBlank())
                return abbreviate(errorDescription, 70);
            String plainError = getJsonString(root, "error");
            if (!plainError.isBlank())
                return abbreviate(plainError, 70);
        } catch (Throwable ignored) {
        }
        return abbreviate(body.replace('\n', ' ').replace('\r', ' ').trim(), 70);
    }

    private static String abbreviate(String value, int maxLength) {
        if (value == null)
            return "";
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength)
            return trimmed;
        return trimmed.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private static void bumpSearchUi() {
        searchVersion++;
    }

    private static void fetchCoverArtFallbackAsync(String title, String artist, String key) {
        try {
            String query = URLEncoder.encode((title + " " + artist).trim(), StandardCharsets.UTF_8);
            String artworkUrl = findDeezerCoverUrl(query);
            if (artworkUrl == null || artworkUrl.isBlank()) artworkUrl = findItunesCoverUrl(query);
            if (artworkUrl == null || artworkUrl.isBlank()) return;
            HttpRequest imageRequest = HttpRequest.newBuilder(URI.create(artworkUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "MinecraftOverlay/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> imageResponse = COVER_HTTP_CLIENT.send(imageRequest, HttpResponse.BodyHandlers.ofByteArray());
            byte[] bytes = imageResponse.body();
            if (imageResponse.statusCode() / 100 == 2 && bytes != null && bytes.length > 0 && bytes.length < 5_242_880 && key.equals(coverTrackKey)) {
                coverArtBase64 = Base64.getEncoder().encodeToString(bytes);
                coverLookupKey = key;
            }
        } catch (Throwable exception) {
            MinecraftOverlay.LOGGER.debug("Could not fetch fallback Spotify cover art", exception);
        } finally {
            coverLookupQueued.set(false);
        }
    }

    private static String findDeezerCoverUrl(String encodedQuery) throws IOException, InterruptedException {
        HttpRequest searchRequest = HttpRequest.newBuilder(URI.create("https://api.deezer.com/search?limit=1&q=" + encodedQuery))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> searchResponse = COVER_HTTP_CLIENT.send(searchRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (searchResponse.statusCode() / 100 != 2 || searchResponse.body() == null || searchResponse.body().isBlank()) return "";
        JsonObject root = JsonParser.parseString(searchResponse.body()).getAsJsonObject();
        JsonArray results = root.has("data") && root.get("data").isJsonArray() ? root.getAsJsonArray("data") : null;
        if (results == null || results.isEmpty() || !results.get(0).isJsonObject()) return "";
        JsonObject first = results.get(0).getAsJsonObject();
        JsonObject album = first.has("album") && first.get("album").isJsonObject() ? first.getAsJsonObject("album") : null;
        if (album == null) return "";
        JsonElement artworkElement = album.has("cover_xl") ? album.get("cover_xl") : (album.has("cover_big") ? album.get("cover_big") : album.get("cover"));
        return artworkElement == null || artworkElement.isJsonNull() ? "" : artworkElement.getAsString();
    }

    private static String findItunesCoverUrl(String encodedQuery) throws IOException, InterruptedException {
        HttpRequest searchRequest = HttpRequest.newBuilder(URI.create("https://itunes.apple.com/search?media=music&entity=song&limit=1&term=" + encodedQuery))
                .timeout(Duration.ofSeconds(4))
                .header("User-Agent", "MinecraftOverlay/1.0")
                .GET()
                .build();
        HttpResponse<String> searchResponse = COVER_HTTP_CLIENT.send(searchRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (searchResponse.statusCode() / 100 != 2 || searchResponse.body() == null || searchResponse.body().isBlank()) return "";
        JsonObject root = JsonParser.parseString(searchResponse.body()).getAsJsonObject();
        JsonArray results = root.has("results") && root.get("results").isJsonArray() ? root.getAsJsonArray("results") : null;
        if (results == null || results.isEmpty() || !results.get(0).isJsonObject()) return "";
        JsonObject first = results.get(0).getAsJsonObject();
        JsonElement artworkElement = first.get("artworkUrl100");
        if (artworkElement == null || artworkElement.isJsonNull()) return "";
        return artworkElement.getAsString().replace("100x100bb", "600x600bb");
    }

    private static String coverKey(String title, String artist) {
        return ((title == null ? "" : title.trim()) + "|" + (artist == null ? "" : artist.trim())).toLowerCase(Locale.ROOT);
    }

    private static double parseSeconds(String text) {
        if (text == null || text.isBlank()) return 0.0D;
        try {
            return Math.max(0.0D, Double.parseDouble(text));
        } catch (NumberFormatException ignored) {
            return 0.0D;
        }
    }

    private static String formatTime(double seconds) {
        int wholeSeconds = Math.max(0, (int) Math.round(seconds));
        int minutes = wholeSeconds / 60;
        int remainingSeconds = wholeSeconds % 60;
        return String.format(Locale.ROOT, "%d:%02d", minutes, remainingSeconds);
    }

    private static String encodePowerShell(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    private static String buildBridgeScript(String action) {
        boolean includeThumbnail = action.endsWith(":cover");
        String commandAction = action;
        if (commandAction.endsWith(":cover")) commandAction = commandAction.substring(0, commandAction.length() - ":cover".length());
        String safeAction = commandAction;
        if (!commandAction.startsWith("volume:")) {
            safeAction = switch (commandAction) {
                case "playpause", "next", "previous", "pause", "status" -> commandAction;
                default -> "status";
            };
        }
        return """
                $ProgressPreference = 'SilentlyContinue'
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Runtime.WindowsRuntime
                [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
                [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties, Windows.Media.Control, ContentType = WindowsRuntime] | Out-Null
                [Windows.Storage.Streams.DataReader, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
                [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime] | Out-Null
                $asTaskGeneric = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.IsGenericMethod -and $_.GetParameters().Count -eq 1 })[0]
                function Await-WinRt($op, [Type]$type) {
                  $task = $asTaskGeneric.MakeGenericMethod($type).Invoke($null, @($op))
                  $task.Wait()
                  return $task.Result
                }
                function Clean($value) {
                  if ($null -eq $value) { return '' }
                  return ([string]$value).Replace('|', ' ').Replace("`r", ' ').Replace("`n", ' ').Trim()
                }
                try {
                  $manager = Await-WinRt ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
                  $sessions = @($manager.GetSessions())
                  $session = $sessions | Where-Object { $_.SourceAppUserModelId -match 'Spotify' } | Select-Object -First 1
                  if ($null -eq $session) {
                    Write-Output 'SPOTIFY|NO_SESSION|||'
                    exit 0
                  }
                  $action = '__ACTION__'
                  $includeThumbnail = __INCLUDE_THUMBNAIL__
                  $actionResult = 'OK'
                  if ($action -eq 'playpause') {
                    $ok = [bool](Await-WinRt ($session.TryTogglePlayPauseAsync()) ([bool]))
                    if ($ok) { $actionResult = 'Toggled' } else { $actionResult = 'Play/pause blocked' }
                  } elseif ($action -eq 'next') {
                    $ok = [bool](Await-WinRt ($session.TrySkipNextAsync()) ([bool]))
                    if ($ok) { $actionResult = 'Next' } else { $actionResult = 'Next blocked' }
                  } elseif ($action -eq 'previous') {
                    $ok = [bool](Await-WinRt ($session.TrySkipPreviousAsync()) ([bool]))
                    if ($ok) { $actionResult = 'Previous' } else { $actionResult = 'Previous blocked' }
                  } elseif ($action -eq 'pause') {
                    $ok = [bool](Await-WinRt ($session.TryPauseAsync()) ([bool]))
                    if ($ok) { $actionResult = 'Paused' } else { $actionResult = 'Pause blocked' }
                  } elseif ($action -match '^volume:(.+)') {
                    $vol = [float]$Matches[1]
                    $app = New-Object -ComObject Shell.Application
                    # Note: Media sessions don't support volume directly. This is a generic way to control system volume, 
                    # but for Spotify specifically, we'd need more complex logic. 
                    # For now, we return the session status and skip actual volume logic if not easily available.
                  }
                  
                  $props = Await-WinRt ($session.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                  $timeline = $session.GetTimelineProperties()
                  $status = Clean $session.GetPlaybackInfo().PlaybackStatus
                  $title = Clean $props.Title
                  $artist = Clean $props.Artist
                  $position = 0
                  $duration = 0
                  if ($null -ne $timeline) {
                    $position = [Math]::Max(0, [Math]::Round($timeline.Position.TotalSeconds))
                    $duration = [Math]::Max(0, [Math]::Round($timeline.EndTime.TotalSeconds))
                  }
                  
                  $thumbnailBase64 = ''
                  if ($includeThumbnail) {
                    try {
                      if ($null -ne $props.Thumbnail) {
                        $stream = Await-WinRt ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                        if ($stream.Size -gt 0 -and $stream.Size -lt 5242880) {
                          $reader = [Windows.Storage.Streams.DataReader]::new($stream)
                          [void](Await-WinRt ($reader.LoadAsync([uint32]$stream.Size)) ([uint32]))
                          $bytes = New-Object byte[] ([int]$stream.Size)
                          $reader.ReadBytes($bytes)
                          $thumbnailBase64 = [Convert]::ToBase64String($bytes)
                        }
                      }
                    } catch {
                      $thumbnailBase64 = ''
                    }
                  }
                  
                  Write-Output ('SPOTIFY|' + $status + '|' + $title + '|' + $artist + '|' + $actionResult + '|' + $position + '|' + $duration + '|' + $thumbnailBase64 + '|1.0')
                } catch {
                  Write-Output ('SPOTIFY|ERROR|' + (Clean $_.Exception.Message) + '||')
                  exit 1
                }
                """.replace("__ACTION__", safeAction)
                .replace("__INCLUDE_THUMBNAIL__", includeThumbnail ? "$true" : "$false");
    }

    public record SpotifySearchResult(String title, String artist, String album, String uri) {
    }
}
