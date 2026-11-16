package com.chinaex123.player_partner.world;

import com.chinaex123.player_partner.PlayerPartner;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.chinaex123.player_partner.config.PPConfig;
import com.chinaex123.player_partner.entity.PartnerPlayer;
import com.chinaex123.player_partner.network.PartnerConnection;
import com.chinaex123.player_partner.util.PartnerSkinLookup;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class PartnerManager {
    private static final Map<MinecraftServer, PartnerManager> MANAGERS = new WeakHashMap<>();
    private static final String FILE_NAME = "player_partners.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final ClientInformation PARTNER_CLIENT_INFO = new ClientInformation(
            "zh_cn", 2, ChatVisiblity.FULL, true, 0x7F, HumanoidArm.RIGHT, false, false);

    private final MinecraftServer server;
    private final List<PartnerRecord> records = new ArrayList<>();
    private final Map<UUID, PartnerPlayer> active = new HashMap<>();

    private PartnerManager(MinecraftServer server) {
        this.server = server;
    }

    public static PartnerManager get(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, PartnerManager::new);
    }

    public record PartnerRecord(String displayName, String skinName, @Nullable UUID owner,
                                ResourceKey<Level> dimension, double x, double y, double z, float yaw) {
    }

    public void onServerStarted() {
        this.records.clear();
        this.records.addAll(this.readRecords());
        int kept = 0;
        for (PartnerRecord record : List.copyOf(this.records)) {
            // 超出数量上限的旧记录直接丢弃，避免改小配置后启动出问题。
            if (kept++ >= PPConfig.PARTNER_LIMIT.get()) {
                this.records.remove(record);
                continue;
            }
            this.spawnFromRecord(record, null);
        }
    }

    public void onServerStopping() {
        this.syncPositions();
        this.save();
    }

    public void spawn(String displayName, String skinName, @Nullable UUID owner, ServerLevel level, Vec3 pos,
                      float yaw, @Nullable ServerPlayer feedback) {
        // 同名伙伴已经存在时先移除旧的，避免玩家列表里出现两个同名条目。
        PartnerPlayer existing = this.getPartner(displayName);
        if (existing == null && this.active.size() >= PPConfig.PARTNER_LIMIT.get()) {
            if (feedback != null) {
                feedback.sendSystemMessage(
                        Component.translatable("message.player_partner.limit", PPConfig.PARTNER_LIMIT.get()));
            }
            return;
        }

        PartnerSkinLookup.lookup(skinName).thenAccept(result -> this.server.execute(() -> {
            boolean skinOk = result.status() == PartnerSkinLookup.Status.OK && result.profile() != null;
            // 皮肤查不到也要照常生成，使用模组内置的默认皮肤，避免离线/限流时召不出伙伴。
            this.placeAndRemember(displayName, skinName, owner, level, pos, yaw,
                    skinOk ? result.profile() : null);

            if (feedback != null) {
                feedback.sendSystemMessage(Component.translatable("message.player_partner.spawned", displayName));
                if (!skinOk) {
                    feedback.sendSystemMessage(Component.translatable(
                            result.status() == PartnerSkinLookup.Status.NETWORK_ERROR
                                    ? "message.player_partner.skin_network_error_default"
                                    : "message.player_partner.skin_not_found_default",
                            skinName));
                }
            }
        }));
    }

    private void placeAndRemember(String displayName, String skinName, @Nullable UUID owner, ServerLevel level,
                                  Vec3 pos, float yaw, @Nullable GameProfile skinProfile) {
        PartnerPlayer existing = this.getPartner(displayName);
        if (existing != null) {
            this.remove(existing, false);
        }

        PartnerRecord record = new PartnerRecord(displayName, skinName, owner,
                level.dimension(), pos.x, pos.y, pos.z, yaw);
        this.records.removeIf(old -> old.displayName().equalsIgnoreCase(displayName));
        this.records.add(record);
        this.place(record, skinProfile);
        this.save();
    }

    public void rename(PartnerPlayer partner, String newName, @Nullable ServerPlayer feedback) {
        PartnerRecord record = this.findRecord(partner.getUUID());
        if (record == null) {
            return;
        }

        String trimmed = newName.trim();
        PartnerSkinLookup.lookup(trimmed).thenAccept(result -> this.server.execute(() -> {
            switch (result.status()) {
                case OK -> {
                    // 先记下旧伙伴的位置与朝向，改名后原地重新生成。
                    double x = partner.getX();
                    double y = partner.getY();
                    double z = partner.getZ();
                    float yaw = partner.getYRot();
                    ResourceKey<Level> dimension = partner.level().dimension();

                    PartnerPlayer existing = this.getPartner(trimmed);
                    if (existing != null && existing != partner) {
                        this.remove(existing, false);
                    }
                    // 先把旧伙伴背包里的东西收起来，改名后原样放回新伙伴身上。
                    List<net.minecraft.world.item.ItemStack> savedItems = new java.util.ArrayList<>();
                    Inventory oldInventory = partner.getInventory();
                    for (int slot = 0; slot < oldInventory.getContainerSize(); slot++) {
                        savedItems.add(oldInventory.getItem(slot).copy());
                    }

                    // 旧伙伴直接移除。
                    this.remove(partner, false);

                    this.records.removeIf(old -> old.displayName().equalsIgnoreCase(trimmed));
                    PartnerRecord renamed = new PartnerRecord(trimmed, trimmed, record.owner(),
                            dimension, x, y, z, yaw);
                    this.records.add(renamed);
                    if (result.profile() != null) {
                        this.place(renamed, result.profile());
                    }

                    // 把备份的物品放回新伙伴身上。
                    PartnerPlayer fresh = this.getPartner(trimmed);
                    if (fresh != null) {
                        Inventory newInventory = fresh.getInventory();
                        for (int slot = 0; slot < savedItems.size() && slot < newInventory.getContainerSize(); slot++) {
                            newInventory.setItem(slot, savedItems.get(slot));
                        }
                    }
                    this.save();
                    if (feedback != null) {
                        feedback.sendSystemMessage(Component.translatable("message.player_partner.renamed", trimmed));
                    }
                }
                case NOT_FOUND, INVALID_NAME -> {
                    if (feedback != null) {
                        feedback.sendSystemMessage(
                                Component.translatable("message.player_partner.skin_not_found", trimmed));
                    }
                }
                case NETWORK_ERROR -> {
                    if (feedback != null) {
                        feedback.sendSystemMessage(Component.translatable("message.player_partner.skin_network_error"));
                    }
                }
            }
        }));
    }

    private void spawnFromRecord(PartnerRecord record, @Nullable ServerPlayer feedback) {
        PartnerSkinLookup.lookup(record.skinName()).thenAccept(result -> this.server.execute(() -> {
            // 查询失败就先用内置默认皮肤把伙伴放回来，不要因为断网把存档里的伙伴弄丢。
            boolean skinOk = result.status() == PartnerSkinLookup.Status.OK && result.profile() != null;
            this.place(record, skinOk ? result.profile() : null);
        }));
    }

    private void place(PartnerRecord record, @Nullable GameProfile skinProfile) {
        GameProfile profile = new GameProfile(PartnerPlayer.createUuid(record.displayName()), record.displayName());
        if (skinProfile != null) {
            profile.getProperties().putAll(skinProfile.getProperties());
        }
        profile.getProperties().put(PartnerPlayer.MARKER, new Property(PartnerPlayer.MARKER, "1"));

        ServerLevel spawnLevel = this.server.getLevel(record.dimension());
        if (spawnLevel == null) {
            spawnLevel = this.server.overworld();
        }

        PartnerPlayer partner = new PartnerPlayer(this.server, this.server.overworld(), profile,
                PARTNER_CLIENT_INFO);
        partner.setOwnerUuid(record.owner());
        partner.moveTo(record.x(), record.y(), record.z(), record.yaw(), 0.0F);

        this.server.getPlayerList().placeNewPlayer(new PartnerConnection(), partner,
                new CommonListenerCookie(profile, 0, PARTNER_CLIENT_INFO, false, ConnectionType.OTHER));

        partner.setGameMode(GameType.SURVIVAL);
        partner.setHealth(partner.getMaxHealth());
        partner.getFoodData().setFoodLevel(20);
        partner.getFoodData().setSaturation(5.0F);

        if (partner.serverLevel() != spawnLevel) {
            partner.teleportTo(spawnLevel, record.x(), record.y(), record.z(), record.yaw(), 0.0F);
        } else {
            partner.teleportTo(record.x(), record.y(), record.z());
        }

        this.active.put(partner.getUUID(), partner);
    }

    public void remove(PartnerPlayer partner, boolean dropInventory) {
        this.active.remove(partner.getUUID());
        this.records.removeIf(record -> PartnerPlayer.createUuid(record.displayName()).equals(partner.getUUID()));
        if (dropInventory) {
            partner.getInventory().dropAll();
        }
        if (!partner.isRemoved() && this.server.getPlayerList().getPlayer(partner.getUUID()) != null) {
            this.server.getPlayerList().remove(partner);
        }
        this.save();
    }

    public void handleDeath(PartnerPlayer partner) {
        UUID uuid = partner.getUUID();
        PartnerRecord record = this.findRecord(uuid);
        this.active.remove(uuid);
        if (this.server.getPlayerList().getPlayer(uuid) != null) {
            this.server.getPlayerList().remove(partner);
        }
        if (record == null) {
            return;
        }

        this.records.remove(record);
        this.save();
    }

    @Nullable
    private PartnerRecord findRecord(UUID partnerUuid) {
        for (PartnerRecord record : this.records) {
            if (PartnerPlayer.createUuid(record.displayName()).equals(partnerUuid)) {
                return record;
            }
        }
        return null;
    }

    public List<PartnerPlayer> getActivePartners() {
        return List.copyOf(this.active.values());
    }

    public List<PartnerRecord> getRecords() {
        return List.copyOf(this.records);
    }

    @Nullable
    public PartnerPlayer getPartner(String displayName) {
        UUID uuid = PartnerPlayer.createUuid(displayName);
        return this.active.get(uuid);
    }

    private void syncPositions() {
        Map<UUID, PartnerPlayer> alive = new HashMap<>(this.active);
        List<PartnerRecord> updated = new ArrayList<>();
        for (PartnerRecord record : this.records) {
            UUID uuid = PartnerPlayer.createUuid(record.displayName());
            PartnerPlayer partner = alive.get(uuid);
            if (partner == null || partner.isRemoving()) {
                updated.add(record);
                continue;
            }
            updated.add(new PartnerRecord(record.displayName(), record.skinName(), record.owner(),
                    partner.level().dimension(), partner.getX(), partner.getY(), partner.getZ(), partner.getYRot()));
        }
        this.records.clear();
        this.records.addAll(updated);
    }

    public void save() {
        Path path = this.getFilePath();
        try {
            Files.createDirectories(path.getParent());
            JsonArray array = new JsonArray();
            for (PartnerRecord record : this.records) {
                JsonObject object = new JsonObject();
                object.addProperty("name", record.displayName());
                object.addProperty("skin", record.skinName());
                object.addProperty("owner", record.owner() == null ? "" : record.owner().toString());
                object.addProperty("dimension", record.dimension().location().toString());
                object.addProperty("x", record.x());
                object.addProperty("y", record.y());
                object.addProperty("z", record.z());
                object.addProperty("yaw", record.yaw());
                array.add(object);
            }
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(array, writer);
            }
        } catch (IOException exception) {
            PlayerPartner.LOGGER.warn("无法保存玩家伙伴列表", exception);
        }
    }

    private List<PartnerRecord> readRecords() {
        Path path = this.getFilePath();
        if (!Files.exists(path)) {
            return List.of();
        }
        List<PartnerRecord> result = new ArrayList<>();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonArray array = JsonParser.parseReader(reader).getAsJsonArray();
            for (var element : array) {
                JsonObject object = element.getAsJsonObject();
                String ownerText = object.get("owner").getAsString();
                UUID owner = ownerText.isEmpty() ? null : UUID.fromString(ownerText);
                ResourceLocation dimensionId = ResourceLocation.parse(object.get("dimension").getAsString());
                ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
                result.add(new PartnerRecord(
                        object.get("name").getAsString(),
                        object.get("skin").getAsString(),
                        owner,
                        dimension,
                        object.get("x").getAsDouble(),
                        object.get("y").getAsDouble(),
                        object.get("z").getAsDouble(),
                        object.get("yaw").getAsFloat()));
            }
        } catch (Exception exception) {
            PlayerPartner.LOGGER.warn("无法读取玩家伙伴列表", exception);
        }
        return result;
    }

    private Path getFilePath() {
        return this.server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME);
    }
}
