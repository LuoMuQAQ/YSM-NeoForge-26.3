package com.elfmcys.ysm.model.resource.client;

import com.elfmcys.ysm.geckolib3.geo.render.built.GeoModel;

import java.util.Objects;

public final class PlayerModelVariant {
    private final GeoModel mainModel;
    private final GeoModel armModel;

    public PlayerModelVariant(GeoModel mainModel, GeoModel armModel) {
        this.mainModel = Objects.requireNonNull(mainModel, "mainModel");
        this.armModel = Objects.requireNonNull(armModel, "armModel");
    }

    public GeoModel mainModel() {
        return mainModel;
    }

    public GeoModel armModel() {
        return armModel;
    }

}
