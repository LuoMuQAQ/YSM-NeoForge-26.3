package com.elfmcys.ysm.geckolib3.geo.render.built;

import com.elfmcys.ysm.geckolib3.core.molang.util.StringPool;
import com.elfmcys.ysm.proto.mixel.asset.model.data.Bone;
import org.joml.Vector3f;

public class GeoBone {
    private final String name;
    private final int pooledName;
    private final GeoLocator locatorType;
    private final Vector3f rotation;
    private final Vector3f pivot;
    private final boolean debug;

    public GeoBone(Bone bone, GeoLocator locator) {
        if (bone.nameNullOrBlank()
                || bone.rotate().size() != 3 || bone.pivot().size() != 3) {
            throw new IllegalArgumentException("Invalid bone metadata");
        }
        this.name = bone.name();
        this.pooledName = StringPool.computeIfAbsent(name);
        this.locatorType = locator;
        this.rotation = new Vector3f(bone.rotate().getFloat(0),
                bone.rotate().getFloat(1), bone.rotate().getFloat(2));
        this.pivot = new Vector3f(bone.pivot().getFloat(0),
                bone.pivot().getFloat(1), bone.pivot().getFloat(2));
        this.debug = bone.debug().orElse(false);
    }

    public String name() {
        return name;
    }

    public int pooledName() {
        return pooledName;
    }

    public GeoLocator locatorType() {
        return locatorType;
    }

    public Vector3f rotation() {
        return rotation;
    }

    public Vector3f pivot() {
        return pivot;
    }

    public boolean debug() {
        return debug;
    }
}
