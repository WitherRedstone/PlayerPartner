package com.chinaex123.player_partner.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public final class PartnerSkinLookup {

    /** 正版 ID 的合法格式。 */
    private static final Pattern VALID_NAME = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    /** 新的按名字查 UUID 的接口，未知 ID 返回 404。 */
    private static final String LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    /** 旧接口，仅在新接口异常时兜底。 */
    private static final String LEGACY_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";
    /** 会话服务器，用来拿皮肤属性。 */
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "player_partner-skin-lookup");
        thread.setDaemon(true);
        return thread;
    });
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ConcurrentHashMap<String, CompletableFuture<Result>> CACHE = new ConcurrentHashMap<>();

    /** 查询状态。 */
    public enum Status {
        /** 查询成功。 */
        OK,
        /** 这个正版 ID 不存在。 */
        NOT_FOUND,
        /** ID 格式不合法。 */
        INVALID_NAME,
        /** 网络异常。 */
        NETWORK_ERROR
    }

    /** 查询结果。 */
    public record Result(Status status, @Nullable GameProfile profile) {
        public static Result notFound() {
            return new Result(Status.NOT_FOUND, null);
        }

        public static Result invalidName() {
            return new Result(Status.INVALID_NAME, null);
        }

        public static Result networkError() {
            return new Result(Status.NETWORK_ERROR, null);
        }
    }

    public static CompletableFuture<Result> lookup(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!VALID_NAME.matcher(trimmed).matches()) {
            return CompletableFuture.completedFuture(Result.invalidName());
        }
        return CACHE.computeIfAbsent(trimmed.toLowerCase(Locale.ROOT),
                key -> CompletableFuture.supplyAsync(() -> fetch(trimmed), EXECUTOR));
    }

    private static Result fetch(String name) {
        try {
            Optional<JsonObject> lookup = lookupUuid(name);
            if (lookup.isEmpty()) {
                return Result.notFound();
            }

            JsonObject lookupJson = lookup.get();
            String undashedId = lookupJson.get("id").getAsString();
            String canonicalName = lookupJson.has("name") ? lookupJson.get("name").getAsString() : name;
            UUID uuid = parseUuid(undashedId);

            Optional<JsonObject> session = fetchSession(undashedId);
            if (session.isEmpty()) {
                return Result.networkError();
            }

            GameProfile profile = new GameProfile(uuid, canonicalName);
            JsonArray properties = session.get().getAsJsonArray("properties");
            if (properties != null) {
                for (JsonElement element : properties) {
                    JsonObject property = element.getAsJsonObject();
                    if (!"textures".equals(property.get("name").getAsString())) {
                        continue;
                    }
                    String value = property.get("value").getAsString();
                    String signature = property.has("signature") ? property.get("signature").getAsString() : null;
                    profile.getProperties().put("textures", new Property("textures", value, signature));
                }
            }

            if (profile.getProperties().get("textures").isEmpty()) {
                return Result.notFound();
            }
            return new Result(Status.OK, profile);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Result.networkError();
        } catch (Exception exception) {
            return Result.networkError();
        }
    }

    private static Optional<JsonObject> lookupUuid(String name) throws Exception {
        HttpResponse<String> response = get(LOOKUP_URL + name);
        if (response.statusCode() == 200) {
            return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
        }
        if (response.statusCode() == 404 || response.statusCode() == 204) {
            return Optional.empty();
        }

        // 新接口异常时，退回旧接口再试一次。
        HttpResponse<String> legacy = get(LEGACY_LOOKUP_URL + name);
        if (legacy.statusCode() == 200) {
            return Optional.of(JsonParser.parseString(legacy.body()).getAsJsonObject());
        }
        if (legacy.statusCode() == 404 || legacy.statusCode() == 204) {
            return Optional.empty();
        }
        throw new IllegalStateException("查询正版 ID 失败：" + response.statusCode());
    }

    private static Optional<JsonObject> fetchSession(String undashedId) throws Exception {
        HttpResponse<String> response = get(SESSION_URL + undashedId + "?unsigned=false");
        if (response.statusCode() != 200) {
            return Optional.empty();
        }
        return Optional.of(JsonParser.parseString(response.body()).getAsJsonObject());
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static UUID parseUuid(String undashed) {
        long most = Long.parseUnsignedLong(undashed.substring(0, 16), 16);
        long least = Long.parseUnsignedLong(undashed.substring(16, 32), 16);
        return new UUID(most, least);
    }
}
