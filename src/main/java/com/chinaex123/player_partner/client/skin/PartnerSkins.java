package com.chinaex123.player_partner.client.skin;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.entity.PlayerPartnerEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class PartnerSkins {

    /** 合法正版名字：3 到 16 位字母、数字或下划线。 */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private static final ResourceLocation DEFAULT_TEXTURE = PlayerPartner.id("textures/entity/default_skin.png");
    private static final PlayerSkin DEFAULT_SKIN = new PlayerSkin(
            DEFAULT_TEXTURE, null, null, null, PlayerSkin.Model.WIDE, false);

    /** 新版查询接口，正常时优先使用。 */
    private static final String LOOKUP_URL = "https://api.minecraftservices.com/minecraft/profile/lookup/name/";
    /** 旧版查询接口，仅在新版接口被限流或不可用时作为备用。 */
    private static final String LEGACY_LOOKUP_URL = "https://api.mojang.com/users/profiles/minecraft/";
    /** 获取玩家皮肤属性的接口。 */
    private static final String SESSION_URL = "https://sessionserver.mojang.com/session/minecraft/profile/";
    private static final String USER_AGENT = "PlayerPartner/1.0 (Minecraft NeoForge mod)";

    /** 网络故障时最多尝试的次数。 */
    private static final int MAX_ATTEMPTS = 3;
    /** 两次重试之间的间隔（毫秒）。 */
    private static final long RETRY_DELAY_MS = 30_000L;

    /** 按玩家名（小写）缓存的查询结果。 */
    private static final Map<String, Lookup> LOOKUPS = new ConcurrentHashMap<>();
    /** 每个生物上次处理过的名字，用来判断命名牌是否被改过。 */
    private static final Map<PlayerPartnerEntity, EntityState> ENTITIES = new WeakHashMap<>();

    /** 单线程后台执行器，避免同时发出大量 HTTP 请求。 */
    private static final ExecutorService FETCH_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "player_partner-skin-fetch");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile HttpClient httpClient;

    private PartnerSkins() {
    }

    public static PlayerSkin getSkin(PlayerPartnerEntity entity) {
        String name = entity.getSkinName();

        EntityState state = ENTITIES.computeIfAbsent(entity, key -> new EntityState());
        if (!name.equals(state.name)) {
            // 名字变了，允许重新提示一次。
            state.name = name;
            state.announced = false;
        }

        Lookup lookup = getOrStart(name);

        // 默认作者皮肤不提示，只有玩家自己写错 ID 时才提示。
        if (!state.announced && !name.equalsIgnoreCase(PlayerPartnerEntity.DEFAULT_SKIN_NAME)) {
            String messageKey = switch (lookup.status()) {
                case NOT_FOUND, INVALID_NAME -> "message.player_partner.skin_not_found";
                case NETWORK_ERROR -> "message.player_partner.skin_network_error";
                default -> null;
            };
            if (messageKey != null && announce(entity, name, messageKey)) {
                state.announced = true;
            }
        }

        return lookup.skin() != null ? lookup.skin() : DEFAULT_SKIN;
    }

    private static Lookup getOrStart(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        Lookup existing = LOOKUPS.get(key);
        long now = System.currentTimeMillis();

        if (existing != null) {
            boolean canRetry = existing.status() == Status.NETWORK_ERROR
                    && now >= existing.retryAt()
                    && existing.attempt() < MAX_ATTEMPTS;
            if (!canRetry) {
                return existing;
            }
            PlayerPartner.LOGGER.debug("正在重试玩家“{}”的皮肤查询（第 {} 次）", name, existing.attempt() + 1);
        }

        int attempt = existing == null ? 1 : existing.attempt() + 1;
        Lookup pending = new Lookup(Status.PENDING, null, 0L, attempt);
        LOOKUPS.put(key, pending);
        startLookup(name, key, attempt);
        return LOOKUPS.getOrDefault(key, pending);
    }

    private static void startLookup(String name, String key, int attempt) {
        if (!VALID_NAME.matcher(name).matches()) {
            LOOKUPS.put(key, new Lookup(Status.INVALID_NAME, null, 0L, attempt));
            return;
        }

        FETCH_EXECUTOR.execute(() -> {
            try {
                GameProfile profile = lookupProfile(name);
                if (profile == null) {
                    // 查无此人：名字写错了。
                    LOOKUPS.put(key, new Lookup(Status.NOT_FOUND, null, 0L, attempt));
                    return;
                }

                Minecraft minecraft = Minecraft.getInstance();
                minecraft.execute(() -> {
                    try {
                        minecraft.getSkinManager().getOrLoad(profile).whenComplete((skin, error) -> {
                            if (error != null || skin == null) {
                                PlayerPartner.LOGGER.warn("无法加载玩家“{}”的皮肤", name, error);
                                LOOKUPS.put(key, networkError(attempt));
                            } else {
                                LOOKUPS.put(key, new Lookup(Status.OK, skin, 0L, attempt));
                            }
                        });
                    } catch (Throwable throwable) {
                        PlayerPartner.LOGGER.warn("无法加载玩家“{}”的皮肤", name, throwable);
                        LOOKUPS.put(key, networkError(attempt));
                    }
                });
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                PlayerPartner.LOGGER.warn("无法解析玩家“{}”的资料", name, exception);
                LOOKUPS.put(key, networkError(attempt));
            }
        });
    }

    /** 记录一次网络故障，并设置下次重试的时间。 */
    private static Lookup networkError(int attempt) {
        return new Lookup(Status.NETWORK_ERROR, null, System.currentTimeMillis() + RETRY_DELAY_MS, attempt);
    }

    @Nullable
    private static GameProfile lookupProfile(String name) throws IOException, InterruptedException {
        JsonObject profileJson;

        HttpResponse<String> response = send(LOOKUP_URL + name);
        int status = response.statusCode();
        if (status == 204 || status == 404) {
            return null;
        }
        if (status == 200) {
            profileJson = JsonParser.parseString(response.body()).getAsJsonObject();
        } else {
            // 新版接口可能被限流或临时不可用，这时再用旧接口试一次。
            profileJson = lookupLegacyProfile(name);
        }

        if (profileJson == null || !profileJson.has("id")) {
            return null;
        }

        UUID uuid = parseUndashedUuid(profileJson.get("id").getAsString());
        String canonicalName = profileJson.has("name") ? profileJson.get("name").getAsString() : name;

        GameProfile profile = new GameProfile(uuid, canonicalName);
        JsonObject textures = fetchTextures(uuid);
        if (textures != null && textures.has("value")) {
            String value = textures.get("value").getAsString();
            String signature = textures.has("signature") ? textures.get("signature").getAsString() : null;
            profile.getProperties().put("textures", new Property("textures", value, signature));
        }
        return profile;
    }

    @Nullable
    private static JsonObject lookupLegacyProfile(String name) throws IOException, InterruptedException {
        HttpResponse<String> response = send(LEGACY_LOOKUP_URL + name);
        int status = response.statusCode();
        if (status == 204 || status == 404 || status == 400) {
            return null;
        }
        if (status != 200) {
            throw new IOException("解析“" + name + "”时服务器返回了意外状态码 " + status);
        }
        return JsonParser.parseString(response.body()).getAsJsonObject();
    }

    @Nullable
    private static JsonObject fetchTextures(UUID uuid) throws IOException, InterruptedException {
        String undashed = uuid.toString().replace("-", "");
        HttpResponse<String> response = send(SESSION_URL + undashed);
        if (response.statusCode() == 204 || response.statusCode() == 404) {
            return null;
        }
        if (response.statusCode() != 200) {
            throw new IOException("获取玩家 " + undashed + " 的资料时服务器返回了意外状态码 " + response.statusCode());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray properties = json.getAsJsonArray("properties");
        if (properties == null) {
            return null;
        }
        for (JsonElement element : properties) {
            JsonObject property = element.getAsJsonObject();
            if (property.has("name") && "textures".equals(property.get("name").getAsString())) {
                return property;
            }
        }
        return null;
    }

    private static HttpResponse<String> send(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .build();
        return client().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static HttpClient client() {
        HttpClient client = httpClient;
        if (client == null) {
            synchronized (PartnerSkins.class) {
                client = httpClient;
                if (client == null) {
                    client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.NORMAL)
                            .build();
                    httpClient = client;
                }
            }
        }
        return client;
    }

    private static UUID parseUndashedUuid(String id) {
        String hex = id.replace("-", "");
        if (hex.length() != 32) {
            throw new IllegalArgumentException("玩家 ID 格式错误：" + id);
        }
        return new UUID(
                Long.parseUnsignedLong(hex.substring(0, 16), 16),
                Long.parseUnsignedLong(hex.substring(16, 32), 16));
    }

    private static boolean announce(PlayerPartnerEntity entity, String name, String messageKey) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || entity.distanceToSqr(minecraft.player) > 64.0D * 64.0D) {
            return false;
        }

        Component message = Component.translatable(messageKey, name);
        minecraft.execute(() -> {
            if (minecraft.player != null) {
                minecraft.player.displayClientMessage(message, false);
            }
        });
        return true;
    }

    private enum Status {
        PENDING,
        OK,
        NOT_FOUND,
        INVALID_NAME,
        NETWORK_ERROR
    }

    private record Lookup(Status status, @Nullable PlayerSkin skin, long retryAt, int attempt) {
    }

    private static final class EntityState {
        private String name = "";
        private boolean announced;
    }
}
