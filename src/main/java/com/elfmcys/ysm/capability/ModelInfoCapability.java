// Modified by LuoMuQAQ for the unofficial Minecraft 26.3 / NeoForge port (2026).
package com.elfmcys.ysm.capability;

import com.elfmcys.ysm.model.domain.Hash256;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;

/** Persistent player model selection and synchronization state. */
public final class ModelInfoCapability {
    private Hash256 modelId;
    private String selectTexture = "";
    private boolean mandatory;
    private boolean ignoreGrants;
    private boolean disabled;
    private boolean dirty;
    private SavedSelection savedSelection;
    private final RoamingVariableStore roamingVariables = new RoamingVariableStore();
    private ServerDrivenPlayerPropertiesTracker propertiesTracker =
            new ServerDrivenPlayerPropertiesTracker();

    public void setModelAndTexture(Hash256 modelId, String selectTexture) {
        clearSavedSelection();
        applyModelAndTexture(modelId, selectTexture);
    }

    private void applyModelAndTexture(Hash256 modelId, String selectTexture) {
        var keepIgnoreGrants = modelId != null && Objects.equals(this.modelId, modelId)
                && ignoreGrants;
        if (Objects.equals(this.modelId, modelId)
                && this.selectTexture.equals(selectTexture)
                && ignoreGrants == keepIgnoreGrants) {
            return;
        }
        this.modelId = modelId;
        this.selectTexture = selectTexture;
        ignoreGrants = keepIgnoreGrants;
        markDirty();
    }

    public void setCommandSelection(Hash256 modelId, String selectTexture,
                                    boolean ignoreGrants) {
        Objects.requireNonNull(modelId, "modelId");
        Objects.requireNonNull(selectTexture, "selectTexture");
        clearSavedSelection();
        if (modelId.equals(this.modelId)
                && selectTexture.equals(this.selectTexture)
                && mandatory
                && this.ignoreGrants == ignoreGrants) {
            return;
        }
        this.modelId = modelId;
        this.selectTexture = selectTexture;
        mandatory = true;
        this.ignoreGrants = ignoreGrants;
        markDirty();
    }

    public boolean ignoresGrantsFor(Hash256 modelId) {
        return ignoreGrants && Objects.equals(this.modelId, modelId);
    }

    void clearIgnoreGrants() {
        if (ignoreGrants) {
            ignoreGrants = false;
            markDirty();
        }
    }

    /** Runtime fallback must not replace the last saved choice while catalog work is pending. */
    public void setFallbackModelAndTexture(Hash256 modelId, String texture) {
        if (savedSelection == null && this.modelId != null) {
            savedSelection = new SavedSelection(this.modelId, selectTexture, ignoreGrants);
        }
        applyModelAndTexture(modelId, texture);
        clearIgnoreGrants();
    }

    public Optional<SavedSelection> savedSelection() {
        return Optional.ofNullable(savedSelection);
    }

    /** Called only after the current session accepts the saved choice. */
    public void restoreSavedSelection() {
        var saved = savedSelection;
        if (saved == null) {
            return;
        }
        savedSelection = null;
        applyModelAndTexture(saved.modelId(), saved.texture());
        ignoreGrants = saved.ignoreGrants();
        markDirty();
    }

    private void clearSavedSelection() {
        if (savedSelection != null) {
            savedSelection = null;
            markDirty();
        }
    }

    public record SavedSelection(Hash256 modelId, String texture, boolean ignoreGrants) {}

    public void moveFrom(ModelInfoCapability source) {
        modelId = source.modelId;
        selectTexture = source.selectTexture;
        mandatory = source.mandatory;
        ignoreGrants = source.ignoreGrants;
        disabled = source.disabled;
        savedSelection = source.savedSelection;
        propertiesTracker = source.propertiesTracker;
        roamingVariables.moveFrom(source.roamingVariables);
        markDirty();
    }

    public Hash256 getModelId() {
        return modelId;
    }

    public String getSelectTexture() {
        return selectTexture;
    }

    public void setSelectTexture(String selectTexture) {
        clearSavedSelection();
        this.selectTexture = selectTexture;
        markDirty();
    }

    public void setDisabled(boolean disabled) {
        if (this.disabled != disabled) {
            this.disabled = disabled;
            markDirty();
        }
    }

    public void playAnimation(ServerPlayer player, String animation) {
        propertiesTracker.setExtraAnimation(player, !dirty, animation);
    }

    public void stopAnimation(ServerPlayer player) {
        propertiesTracker.setExtraAnimation(player, !dirty, "");
    }

    public void executeWithMolangVars(
            Consumer<Object2FloatOpenHashMap<String>> consumer) {
        roamingVariables.execute(modelId, consumer);
    }

    public Optional<Object2FloatOpenHashMap<String>> getMolangVars() {
        return roamingVariables.get(modelId);
    }

    public void updateRoamingVars(ServerPlayer player, int modelKey,
                                  Object2FloatMap<String> variables) {
        roamingVariables.update(modelKey, variables);
        propertiesTracker.updateMolangVars(player, !dirty,
                modelKey, variables);
    }

    public void applyClientAnimation(String animation) {
        propertiesTracker.acceptClientAnimation(animation);
    }

    public void applyClientRoaming(int modelKey,
                                   Object2FloatMap<String> variables,
                                   boolean full) {
        if (full) {
            roamingVariables.replace(modelKey, variables);
        } else {
            roamingVariables.update(modelKey, variables);
        }
        propertiesTracker.acceptClientRoaming();
    }

    public void trimRoamingStorage(IntSet retainedHashes) {
        roamingVariables.trim(retainedHashes);
    }

    RoamingVariableStore roamingVariables() {
        return roamingVariables;
    }

    public ServerDrivenPlayerPropertiesTracker getPropertiesTracker() {
        return propertiesTracker;
    }

    public boolean isDisabled() {
        return disabled;
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean consumeDirty() {
        var changed = dirty;
        dirty = false;
        return changed;
    }

    public void setMandatory(boolean mandatory) {
        if (this.mandatory != mandatory) {
            this.mandatory = mandatory;
            markDirty();
        }
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public CompoundTag serializeNBT() {
        var tag = new CompoundTag();
        var storedModelId = savedSelection == null ? modelId : savedSelection.modelId();
        var storedTexture = savedSelection == null ? selectTexture : savedSelection.texture();
        tag.putString("model_hash", storedModelId == null ? "" : storedModelId.toString());
        tag.putString("select_texture", storedTexture);
        tag.putBoolean("mandatory", mandatory);
        tag.putBoolean("ignore_grants", savedSelection == null ? ignoreGrants : savedSelection.ignoreGrants());
        tag.putBoolean("disabled", disabled);
        tag.put("molang_storage", roamingVariables.serialize());
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        savedSelection = null;
        var storedHash = tag.getStringOr("model_hash", "");
        try {
            modelId = storedHash.isEmpty() ? null : Hash256.parse(storedHash);
        } catch (IllegalArgumentException invalidHash) {
            modelId = null;
        }
        selectTexture = tag.getStringOr("select_texture", "");
        if (selectTexture.length() > 4 && selectTexture.toLowerCase().endsWith(".png")) {
            selectTexture = selectTexture.substring(0, selectTexture.length() - 4);
        }
        mandatory = tag.getBooleanOr("mandatory", false);
        ignoreGrants = modelId != null && tag.getBooleanOr("ignore_grants", false);
        disabled = tag.getBooleanOr("disabled", false);
        roamingVariables.deserialize(tag.getCompoundOrEmpty("molang_storage"));
    }
}
