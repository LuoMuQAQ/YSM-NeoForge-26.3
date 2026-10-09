package com.elfmcys.ysm.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * Builtin and datapack registry lookups after ForgeRegistries was removed.
 * Tag keys are created without a live tag manager; an unbound tag simply does not match.
 */
public final class RegistryIds {
    private RegistryIds() {
    }

    public static boolean isValidIdentifier(String id) {
        return id != null && Identifier.tryParse(id) != null;
    }

    public static @Nullable Identifier itemId(Item item) {
        return item == null ? null : BuiltInRegistries.ITEM.getKey(item);
    }

    public static @Nullable Identifier blockId(Block block) {
        return block == null ? null : BuiltInRegistries.BLOCK.getKey(block);
    }

    public static @Nullable Identifier entityTypeId(EntityType<?> type) {
        return type == null ? null : EntityType.getKey(type);
    }

    public static @Nullable Identifier effectId(Holder<MobEffect> effect) {
        if (effect == null) {
            return null;
        }
        return effect.unwrapKey().map(ResourceKey::identifier).orElseGet(() -> BuiltInRegistries.MOB_EFFECT.getKey(effect.value()));
    }

    public static TagKey<Item> itemTag(Identifier id) {
        return TagKey.create(Registries.ITEM, id);
    }

    public static TagKey<Block> blockTag(Identifier id) {
        return TagKey.create(Registries.BLOCK, id);
    }

    public static TagKey<EntityType<?>> entityTypeTag(Identifier id) {
        return TagKey.create(Registries.ENTITY_TYPE, id);
    }

    public static TagKey<Biome> biomeTag(Identifier id) {
        return TagKey.create(Registries.BIOME, id);
    }

    public static boolean hasEntityTypeTag(EntityType<?> type, TagKey<EntityType<?>> tag) {
        return type != null && type.builtInRegistryHolder().is(tag);
    }

    public static @Nullable Holder<MobEffect> effectHolder(Identifier id) {
        if (id == null) {
            return null;
        }
        MobEffect effect = BuiltInRegistries.MOB_EFFECT.getValue(id);
        return effect == null ? null : BuiltInRegistries.MOB_EFFECT.wrapAsHolder(effect);
    }

    public static @Nullable Holder<Enchantment> enchantmentHolder(Level level, Identifier id) {
        if (level == null || id == null) {
            return null;
        }
        Registry<Enchantment> registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT, id);
        Enchantment value = registry.getValue(key);
        return value == null ? null : registry.wrapAsHolder(value);
    }
}
