// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.client.animation.condition;

import com.elfmcys.ysm.util.RegistryIds;
import com.elfmcys.ysm.util.EnumUtil;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

public class ConditionalHold {
    private static final String EMPTY_MAINHAND = "hold_mainhand:empty";
    private static final String EMPTY_OFFHAND = "hold_offhand:empty";
    private static final String EMPTY = "";
    private final int preSize;
    private final String idPre;
    private final String tagPre;
    private final String extraPre;
    private final ObjectOpenHashSet<Identifier> idTest = new ObjectOpenHashSet<>();
    private final ReferenceArrayList<TagKey<Item>> tagTest = new ReferenceArrayList<>();
    private final ReferenceOpenHashSet<ItemUseAnimation> extraTest = new ReferenceOpenHashSet<>();
    private final ObjectOpenHashSet<String> innerTest = new ObjectOpenHashSet<>();

    public ConditionalHold(InteractionHand hand) {
        if (hand == InteractionHand.MAIN_HAND) {
            idPre = "hold_mainhand$";
            tagPre = "hold_mainhand#";
            extraPre = "hold_mainhand:";
            preSize = 14;
        } else {
            idPre = "hold_offhand$";
            tagPre = "hold_offhand#";
            extraPre = "hold_offhand:";
            preSize = 13;
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
            return hand == InteractionHand.MAIN_HAND ? EMPTY_MAINHAND : EMPTY_OFFHAND;
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