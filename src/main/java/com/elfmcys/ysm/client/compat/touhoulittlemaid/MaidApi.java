package com.elfmcys.ysm.client.compat.touhoulittlemaid;

import com.elfmcys.ysm.client.compat.OptionalApi;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Provider state reads retain their original names; no external entity classes are synthesized. */
public final class MaidApi {
    public static final String ID = "touhou_little_maid";
    public static final String ROOT = "com.github.tartaricacid.touhoulittlemaid.";
    public static final String MAID = ROOT + "entity.passive.EntityMaid";

    private MaidApi() {}

    public static boolean isMaid(Entity entity) {
        return entity instanceof LivingEntity && OptionalApi.query(ID, false, () -> OptionalApi.instance(MAID, entity));
    }

    public static boolean is(String relativeType, Object value) {
        return OptionalApi.query(ID, false, () -> OptionalApi.instance(ROOT + relativeType, value));
    }

    public static boolean flag(Object maid, String method) {
        return OptionalApi.query(ID, false, () -> OptionalApi.bool(OptionalApi.call(maid, method)));
    }

    public static Object read(Object maid, String method) {
        return OptionalApi.call(maid, method);
    }

    public static String text(Object maid, String method) {
        return OptionalApi.query(ID, "", () -> (String) OptionalApi.call(maid, method));
    }

    public static boolean renderState(Object maid, String name) {
        return OptionalApi.query(ID, false, () -> OptionalApi.enumName(OptionalApi.get(maid, "renderState")).equals(name));
    }

    public static void action(Object maid, String method, Object... args) {
        OptionalApi.query(ID, null, () -> OptionalApi.call(maid, method, args));
    }

    public static String tag(String name) { return (String) OptionalApi.getStatic(MAID, name); }
}
