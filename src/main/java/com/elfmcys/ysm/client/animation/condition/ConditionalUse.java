// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.condition;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.util.EnumUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class ConditionalUse {
    private static final String EMPTY = "";
    private final int preSize;
    private final String idPre;
    private final String tagPre;
    private final String extraPre;
    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();
    private final ReferenceArrayList<TagKey<Item>> tagTest = new ReferenceArrayList<>();
    private final ObjectOpenHashSet<ItemUseAnimation> extraTest = new ObjectOpenHashSet<>();
    private final ObjectOpenHashSet<String> innerTest = new ObjectOpenHashSet<>();

    public ConditionalUse(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            idPre = "use_mainhand$";
            tagPre = "use_mainhand#";
            extraPre = "use_mainhand:";
            preSize = 13;
        } else {
            idPre = "use_offhand$";
            tagPre = "use_offhand#";
            extraPre = "use_offhand:";
            preSize = 12;
        }
    }

    public void addTest(String name) {
        if (name.length() <= preSize) {
            return;
        }
        String substring = name.substring(preSize);
        if (name.startsWith(idPre) && RegistryIds.isValidIdentifier(substring)) {
            idTest.add(Identifier.parse(substring));
        }
        if (name.startsWith(tagPre) && RegistryIds.isValidIdentifier(substring)) {
            TagKey<Item> tagKey = RegistryIds.itemTag(Identifier.parse(substring));
            tagTest.add(tagKey);
        }
        if (name.startsWith(extraPre)) {
            if (substring.equals(ItemUseAnimation.NONE.name().toLowerCase(Locale.US))) {
                return;
            }
            EnumUtil.getUseAnim(substring).ifPresent(extraTest::add);
            innerTest.add(name);
        }
    }

    public String doTest(LivingEntity livingEntity, InteractionHand hand) {
        if (livingEntity.getItemInHand(hand).isEmpty()) {
            return EMPTY;
        }
        String result = doIdTest(livingEntity, hand);
        if (result.isEmpty()) {
            result = doTagTest(livingEntity, hand);
            if (result.isEmpty()) {
                return doExtraTest(livingEntity, hand);
            }
            return result;
        }
        return result;
    }

    private String doIdTest(LivingEntity livingEntity, InteractionHand hand) {
        if (idTest.isEmpty()) {
            return EMPTY;
        }
        ItemStack itemInHand = livingEntity.getItemInHand(hand);
        Identifier registryName = RegistryIds.itemId(itemInHand.getItem());
        if (registryName == null) {
            return EMPTY;
        }
        if (idTest.contains(registryName)) {
            return idPre + registryName;
        }
        return EMPTY;
    }

    private String doTagTest(LivingEntity livingEntity, InteractionHand hand) {
        if (tagTest.isEmpty()) {
            return EMPTY;
        }
        ItemStack itemInHand = livingEntity.getItemInHand(hand);
        return tagTest.stream().filter(tag -> itemInHand.typeHolder().is(tag)).findFirst().map(itemTagKey -> tagPre + itemTagKey.location()).orElse(EMPTY);
    }

    private String doExtraTest(LivingEntity livingEntity, InteractionHand hand) {
        if (extraTest.isEmpty() && innerTest.isEmpty()) {
            return EMPTY;
        }
        String innerName = InnerClassify.doClassifyTest(extraPre, livingEntity, hand);
        if (StringUtils.isNotBlank(innerName) && this.innerTest.contains(innerName)) {
            return innerName;
        }
        ItemUseAnimation anim = livingEntity.getItemInHand(hand).getUseAnimation();
        if (this.extraTest.contains(anim)) {
            return extraPre + anim.name().toLowerCase(Locale.US);
        }
        return EMPTY;
    }
}
