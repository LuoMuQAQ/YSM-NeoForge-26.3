// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.condition;

import com.elfmcys.ysm.util.RegistryIds;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

public class ConditionalPassenger {
    private static final String EMPTY = "";
    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();
    private final ReferenceArrayList<TagKey<EntityType<?>>> tagTest = new ReferenceArrayList<>();
    private final String idPre;
    private final String tagPre;

    public ConditionalPassenger() {
        this.idPre = "passenger$";
        this.tagPre = "passenger#";
    }

    public void addTest(String name) {
        int preSize = this.idPre.length();
        if (name.length() <= preSize) {
            return;
        }
        String substring = name.substring(preSize);
        if (name.startsWith(idPre) && RegistryIds.isValidIdentifier(substring)) {
            idTest.add(Identifier.parse(substring));
        }
        if (name.startsWith(tagPre) && RegistryIds.isValidIdentifier(substring)) {
            TagKey<EntityType<?>> tagKey = RegistryIds.entityTypeTag(Identifier.parse(substring));
            tagTest.add(tagKey);
        }
    }

    public String doTest(LivingEntity livingEntity) {
        Entity passenger = livingEntity.getFirstPassenger();
        if (passenger == null || !passenger.isAlive()) {
            return EMPTY;
        }
        String result = doIdTest(passenger);
        if (result.isEmpty()) {
            return doTagTest(passenger);
        }
        return result;
    }

    private String doIdTest(Entity passenger) {
        if (idTest.isEmpty()) {
            return EMPTY;
        }
        Identifier registryName = RegistryIds.entityTypeId(passenger.getType());
        if (registryName == null) {
            return EMPTY;
        }
        if (idTest.contains(registryName)) {
            return idPre + registryName;
        }
        return EMPTY;
    }

    private String doTagTest(Entity passenger) {
        if (tagTest.isEmpty()) {
            return EMPTY;
        }
        return tagTest.stream().filter(tag -> RegistryIds.hasEntityTypeTag(passenger.getType(), tag)).findFirst().map(itemTagKey -> tagPre + itemTagKey.location()).orElse(EMPTY);
    }
}
