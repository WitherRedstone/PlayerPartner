package com.chinaex123.player_partner.world;

import com.chinaex123.player_partner.PlayerPartner;
import com.chinaex123.player_partner.config.PPConfig;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class MiningRules extends SavedData {
    private static final String DATA_NAME = PlayerPartner.MODID + "_mining";
    private static final String TAG_MODE = "Whitelist";
    private static final String TAG_ENTRIES = "Entries";

    private boolean whitelistMode = true;
    private final Set<String> entries = new LinkedHashSet<>();

    public MiningRules() {
    }

    public static MiningRules get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MiningRules::createDefault, MiningRules::load),
                DATA_NAME);
    }

    private static MiningRules createDefault() {
        MiningRules rules = new MiningRules();
        List<? extends String> whitelist = PPConfig.MINING_WHITELIST.get();
        List<? extends String> blacklist = PPConfig.MINING_BLACKLIST.get();
        if (!whitelist.isEmpty()) {
            whitelist.forEach(rules::add);
        } else if (!blacklist.isEmpty()) {
            rules.setWhitelistMode(false);
            blacklist.forEach(rules::add);
        }
        return rules;
    }

    private static MiningRules load(CompoundTag tag, HolderLookup.Provider registries) {
        MiningRules rules = new MiningRules();
        rules.whitelistMode = !tag.contains(TAG_MODE) || tag.getBoolean(TAG_MODE);
        ListTag list = tag.getList(TAG_ENTRIES, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            rules.add(list.getString(i));
        }
        return rules;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_MODE, this.whitelistMode);
        ListTag list = new ListTag();
        this.entries.forEach(entry -> list.add(StringTag.valueOf(entry)));
        tag.put(TAG_ENTRIES, list);
        return tag;
    }

    public boolean isWhitelistMode() {
        return this.whitelistMode;
    }

    public void setWhitelistMode(boolean whitelistMode) {
        if (this.whitelistMode != whitelistMode) {
            this.whitelistMode = whitelistMode;
            this.setDirty();
        }
    }

    public Set<String> getEntries() {
        return Set.copyOf(this.entries);
    }

    public boolean add(String rawEntry) {
        String entry = normalize(rawEntry);
        if (entry.isEmpty() || !isKnownEntry(entry)) {
            return false;
        }
        if (this.entries.add(entry)) {
            this.setDirty();
            return true;
        }
        return false;
    }

    public boolean remove(String rawEntry) {
        String entry = normalize(rawEntry);
        if (this.entries.remove(entry)) {
            this.setDirty();
            return true;
        }
        return false;
    }

    public void clear() {
        if (!this.entries.isEmpty()) {
            this.entries.clear();
            this.setDirty();
        }
    }

    public boolean matches(BlockState state) {
        if (state.isAir()) {
            return false;
        }
        boolean listed = false;
        for (String entry : this.entries) {
            if (matchesEntry(state, entry)) {
                listed = true;
                break;
            }
        }
        if (this.whitelistMode) {
            // 白名单是空的就当作“什么都能挖”，避免设错之后伙伴彻底不干活。
            return this.entries.isEmpty() || listed;
        }
        return !listed;
    }

    public static String normalize(String rawEntry) {
        return rawEntry == null ? "" : rawEntry.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean isKnownEntry(String entry) {
        if (entry.startsWith("#")) {
            return ResourceLocation.tryParse(entry.substring(1)) != null;
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        return id != null && BuiltInRegistries.BLOCK.containsKey(id);
    }

    private static boolean matchesEntry(BlockState state, String entry) {
        if (entry.startsWith("#")) {
            ResourceLocation id = ResourceLocation.tryParse(entry.substring(1));
            return id != null && state.is(TagKey.create(Registries.BLOCK, id));
        }
        ResourceLocation id = ResourceLocation.tryParse(entry);
        return id != null && BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(id);
    }

    public String describe() {
        String mode = this.whitelistMode ? "白名单" : "黑名单";
        if (this.entries.isEmpty()) {
            return mode + "（空）";
        }
        List<String> list = new ArrayList<>(this.entries);
        return mode + "：" + String.join("、", list);
    }

    @Nullable
    public static String describeEntry(String entry) {
        return isKnownEntry(entry) ? entry : null;
    }
}
