# Unofficial 26.3 port changes

Maintainer: LuoMuQAQ. Upstream authors and license notices remain in place.
This is an AI-assisted adaptation of upstream YSM for Minecraft 26.3 and
NeoForge 26.3.0.58-beta, not an official upstream release.

Changes include NeoForge attachments, registry and animation inputs, texture
upload and audio compatibility, SDL3 keys, submit-based entity/GUI/first-person
rendering, model labels, the Z action wheel, HUD preview and item locator indices.
Publication changes add portable, hash-pinned compile-only build inputs and
release documentation, a JDK 25 build helper and explicit Maven Central protoc resolution, identify the unofficial distribution in mod metadata,
and exclude three built-in assets lacking a redistribution grant.

The Unofficial 2 release rebuilds against NeoForge 58-beta, closes client and
process model owners before FML unloads their module, and adds an offline world
copy tool plus persistent player and entity migration records. Legacy selection, grants,
favorites, projectile/vehicle ownership and Molang values are retained; source bytes and current catalog
admission bind each restored identity. Missing sources stay pending. No ignore-
grants permission is introduced by migration. Game upgrade/save and LAN still
require manual verification.

Iris 1.11.7 defers new PBR holders until its loading queue is drained. Model
publication now drains that public queue before validating ownership of supplied
normal/specular components, and logs underlying publication failures. It retains
strict component checks and rejects actual upload/adoption failures.

The default swing predicate reloads each newly accepted host swing by description
identity instead of requiring an exact tick-zero observation. Entity-local tracking
prevents the same swing from restarting across ticks or repeated render passes;
model controller overrides keep their existing precedence. Repeated-attack gameplay
after this change still requires manual verification.

The Unofficial 3 development candidate obtains the host equipment renderer from
the AddLayers resource-generation event and submits WINGS equipment assets,
including the host's texture overrides and foil. GUI player previews extract their
own avatar state for wings and shoulder parrots after refreshing the preview
player's current world reference. HUD body yaw uses angular
interpolation; bone snapshots keep their two visibility channels separate; the
AnimationEvent movement threshold is corrected. Replacement rendering no longer
temporarily changes entity death ticks or the auto-spin flag. Gameplay verification
of these changes is pending.

Native business code is unchanged from the recorded upstream revision. The
Windows DLL is built from that source with the profile in release/windows-clang-profile.
No Minecraft/NeoForge binary or external optional-mod JAR is bundled in this repository.
The two upstream QuickBuffers build JARs and Gradle wrapper are retained with their notices.

## Files changed during the port relative to the pinned Java upstream

- `build.gradle`
- `docs/README.md`
- `docs/architecture/animation/controllers-and-playback.md`
- `docs/architecture/animation/state-inputs-and-sync.md`
- `docs/architecture/asset-pipeline/conversion-and-export.md`
- `docs/architecture/client-presentation/README.md`
- `docs/architecture/integration/README.md`
- `docs/architecture/model-management/default-model.md`
- `docs/architecture/model-management/ownership-and-lifecycle.md`
- `docs/architecture/native-runtime/jni-and-memory.md`
- `docs/architecture/network/README.md`
- `docs/architecture/rendering/README.md`
- `docs/architecture/rendering/frame-execution.md`
- `docs/architecture/rendering/vertex-output.md`
- `docs/architecture/runtime-model.md`
- `docs/build.md`
- `docs/product-decisions/reverse-index.md`
- `docs/status/known-issues/animation.md`
- `docs/status/known-issues/format-and-schema.md`
- `docs/status/known-issues/rendering.md`
- `docs/status/support-and-verification.md`
- `gradle.properties`
- `gradle/builtin-index.gradle`
- `gradle/native.gradle`
- `gradle/wrapper/gradle-wrapper.properties`
- `scripts/prepare-world-copy.py`
- `src/main/java/com/elfmcys/ysm/AssetPaths.java`
- `src/main/java/com/elfmcys/ysm/YesSteveModel.java`
- `src/main/java/com/elfmcys/ysm/accessor/IArrowExtraInfo.java`
- `src/main/java/com/elfmcys/ysm/accessor/IEntityMovementInfo.java`
- `src/main/java/com/elfmcys/ysm/accessor/IExtendedBufferSource.java`
- `src/main/java/com/elfmcys/ysm/accessor/ILivingRenderer.java`
- `src/main/java/com/elfmcys/ysm/accessor/VertexBufferAccessor.java`
- `src/main/java/com/elfmcys/ysm/api/internal/event/YsmEventHandlerLoader.java`
- `src/main/java/com/elfmcys/ysm/api/model/v0/event/RegisterModelLocatorEvent.java`
- `src/main/java/com/elfmcys/ysm/api/rendering/v0/event/RegisterRenderStateModifierEvent.java`
- `src/main/java/com/elfmcys/ysm/api/rendering/v0/event/RenderLayerEvent.java`
- `src/main/java/com/elfmcys/ysm/api/rendering/v0/event/RenderModelEvent.java`
- `src/main/java/com/elfmcys/ysm/buffer/UniBufferIO.java`
- `src/main/java/com/elfmcys/ysm/capability/AuthModelsCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/AuthModelsCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/ClientEntityAttachments.java`
- `src/main/java/com/elfmcys/ysm/capability/ClientLazyCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/ClientLazyCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/ClientRoamingSession.java`
- `src/main/java/com/elfmcys/ysm/capability/ClientRuntimeAttachment.java`
- `src/main/java/com/elfmcys/ysm/capability/EntityAttachments.java`
- `src/main/java/com/elfmcys/ysm/capability/LegacyEntityData.java`
- `src/main/java/com/elfmcys/ysm/capability/LegacyPlayerData.java`
- `src/main/java/com/elfmcys/ysm/capability/ModelInfoCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/ModelInfoCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/PlayerAnimatableCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/PlayerAnimatableCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/PlayerStateTracker.java`
- `src/main/java/com/elfmcys/ysm/capability/ProjectileAnimatableCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/ProjectileAnimatableCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/ProjectileModelInfoCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/ProjectileModelInfoCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/RoamingVariableStore.java`
- `src/main/java/com/elfmcys/ysm/capability/ServerDrivenPlayerPropertiesTracker.java`
- `src/main/java/com/elfmcys/ysm/capability/StarModelsCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/StarModelsCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/VehicleAnimatableCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/VehicleAnimatableCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/capability/VehicleModelInfoCapability.java`
- `src/main/java/com/elfmcys/ysm/capability/VehicleModelInfoCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/client/animation/SwingQueries.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionArmor.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionTAC.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalChair.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalHold.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalPassenger.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalSwing.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalUse.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/ConditionalVehicle.java`
- `src/main/java/com/elfmcys/ysm/client/animation/condition/InnerClassify.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/CtrlBinding.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/YSMBinding.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/ArmorCheck.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/DumpEquippedItem.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/DumpRelativeBlock.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/EffectLevel.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/EquippedEnchantmentLevel.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/HandItemCheck.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/InputCheck.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/ModVersion.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/RelativeBlockName.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/RelativeBlockNameAny.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/RideCheck.java`
- `src/main/java/com/elfmcys/ysm/client/animation/molang/functions/Rot2Camera.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/GunFirePredicate.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/MainhandPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/OffhandPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/ProjectileMainPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/SwingPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/animation/predicate/VehiclePredicate.java`
- `src/main/java/com/elfmcys/ysm/client/command/ClientRootCommand.java`
- `src/main/java/com/elfmcys/ysm/client/command/sub/MolangCommand.java`
- `src/main/java/com/elfmcys/ysm/client/command/sub/SimpleWatchCommand.java`
- `src/main/java/com/elfmcys/ysm/client/compat/ARCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/CosmeticArmorCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/ElytraSlotCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/FirstPersonCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/ImmersiveAircraftCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/IrisCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/OptionalApi.java`
- `src/main/java/com/elfmcys/ysm/client/compat/PlayerAnimatorCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/SimplePlaneCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/backpack/sophisticated/SophisticatedCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/backpack/sophisticated/YsmBackpackLayerRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/compat/bettercombat/BetterCombatCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/bettercombat/BetterCombatCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/bettercombat/event/PlayerAttackEvent.java`
- `src/main/java/com/elfmcys/ysm/client/compat/carryon/CarryOnCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/create/CreateCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/create/CreateCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/curios/CuriosCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/curios/CuriosCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/curios/functions/HasAnyCurios.java`
- `src/main/java/com/elfmcys/ysm/client/compat/immersivemelodies/ImmersiveMelodiesCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/immersivemelodies/ImmersiveMelodiesCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/ironsspellbooks/IronsSpellBooksCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/ironsspellbooks/IronsSpellBooksCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/jade/JadePlugin.java`
- `src/main/java/com/elfmcys/ysm/client/compat/parcool/ParCoolAnimationManger.java`
- `src/main/java/com/elfmcys/ysm/client/compat/parcool/ParCoolCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/realcamera/RealCameraCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/simplehat/HatCuriosCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/simplehat/SimpleHatsCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/slashblade/SlashBladeAnimation.java`
- `src/main/java/com/elfmcys/ysm/client/compat/slashblade/SlashBladeCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/slashblade/SlashBladeRender.java`
- `src/main/java/com/elfmcys/ysm/client/compat/slashblade/SlashBladeResharped.java`
- `src/main/java/com/elfmcys/ysm/client/compat/slashblade/SlashBladeUnsafe.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swarfare/ReplacePlayerArmRender.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swarfare/SWarfareCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swarfare/SWarfareCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swem/SwemCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swem/SwemCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/swem/SwemCtrlBinding.java`
- `src/main/java/com/elfmcys/ysm/client/compat/tacz/TACZCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/tacz/TacCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/tacz/TacCtrlBinding.java`
- `src/main/java/com/elfmcys/ysm/client/compat/tacz/TacEvent.java`
- `src/main/java/com/elfmcys/ysm/client/compat/top/TopPlugin.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/MaidApi.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/TlmCommonCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/TlmCommonCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/capability/YsmMaidCapabilityProvider.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/CustomYsmMaidEntity.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/MaidStateTracker.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/TlmClientCompat.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/TlmClientCompatInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/GeoMaidAnimatedRegister.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/molang/TLMBindingInner.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/predicate/MaidMiscPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/predicate/MaidStatuePredicate.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/predicate/MaidVehiclePredicate.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/predicate/YsmMaidMainPredicate.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/event/SyncCapability.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/event/UpdateRemoteStruct.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/event/YsmMaidScreenEvent.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/event/YsmMaidTickEvent.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/gui/MaidModelScreen.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/gui/MaidTextureScreen.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/input/OpenRouletteScreen.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/client/render/CustomYsmMaidRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/event/CopyYsmModelEvent.java`
- `src/main/java/com/elfmcys/ysm/client/compat/touhoulittlemaid/util/TlmConverterHelper.java`
- `src/main/java/com/elfmcys/ysm/client/controller/ArmorControllerDiscovery.java`
- `src/main/java/com/elfmcys/ysm/client/entity/CustomFirstPersonArmEntity.java`
- `src/main/java/com/elfmcys/ysm/client/entity/CustomHumanoidEntity.java`
- `src/main/java/com/elfmcys/ysm/client/entity/CustomProjectileEntity.java`
- `src/main/java/com/elfmcys/ysm/client/entity/CustomVehicleEntity.java`
- `src/main/java/com/elfmcys/ysm/client/entity/HumanoidStateTracker.java`
- `src/main/java/com/elfmcys/ysm/client/event/ClientLoggedEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/ClientSetupEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/ClientShutdownEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/ClientTickEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/DownloadScreenInterModEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/EntityLoadEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/LocalPlayerRespawnEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/ModInputEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/PlayerMoveEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/RegisterEntityRenderersEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/RenderFirstPlayerBackground.java`
- `src/main/java/com/elfmcys/ysm/client/event/ReplacePlayerHandRenderEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/ReplacePlayerRenderEvent.java`
- `src/main/java/com/elfmcys/ysm/client/event/VanillaPlayerRenderEvent.java`
- `src/main/java/com/elfmcys/ysm/client/gui/AndroidCompat.java`
- `src/main/java/com/elfmcys/ysm/client/gui/AnimationRouletteScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/ConfigScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/DisclaimerScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/DownloadScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/ExtraPlayerConfigScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/GuiDrawing.java`
- `src/main/java/com/elfmcys/ysm/client/gui/ModelInfoScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/OpenModelFolderScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/PlayerModelScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/PlayerTextureScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/PreviewRenderHost.java`
- `src/main/java/com/elfmcys/ysm/client/gui/RouletteSectorRenderState.java`
- `src/main/java/com/elfmcys/ysm/client/gui/YsmModelPreviewState.java`
- `src/main/java/com/elfmcys/ysm/client/gui/YsmPreviewDraw.java`
- `src/main/java/com/elfmcys/ysm/client/gui/YsmPreviewRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/AuthorButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/CatalogModelButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/CatalogTextureButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/ConfigCheckBox.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FailedCatalogModelButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FlatCheckbox.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FlatColorButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FlatIconButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FlatRatioBox.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/FlatSlider.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/PackButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/PositionButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/button/StarButton.java`
- `src/main/java/com/elfmcys/ysm/client/gui/overlay/DebugAnimationScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/overlay/ExtraPlayerScreen.java`
- `src/main/java/com/elfmcys/ysm/client/gui/overlay/LoadingStateScreen.java`
- `src/main/java/com/elfmcys/ysm/client/input/AnimationRouletteKey.java`
- `src/main/java/com/elfmcys/ysm/client/input/DebugAnimationKey.java`
- `src/main/java/com/elfmcys/ysm/client/input/ExtraAnimationKey.java`
- `src/main/java/com/elfmcys/ysm/client/input/ExtraPlayerConfigKey.java`
- `src/main/java/com/elfmcys/ysm/client/input/ModelInputCodes.java`
- `src/main/java/com/elfmcys/ysm/client/input/PlayerModelScreenKey.java`
- `src/main/java/com/elfmcys/ysm/client/input/YsmKeyMappings.java`
- `src/main/java/com/elfmcys/ysm/client/model/locator/FirstPersonLocator.java`
- `src/main/java/com/elfmcys/ysm/client/model/locator/PlayerLocator.java`
- `src/main/java/com/elfmcys/ysm/client/model/locator/ProjectileLocator.java`
- `src/main/java/com/elfmcys/ysm/client/model/locator/VehicleLocator.java`
- `src/main/java/com/elfmcys/ysm/client/particle/ParticleSpawner.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/CustomFirstPersonArmRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/CustomPlayerRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/CustomProjectileRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/CustomVehicleRenderer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/YsmClientRenderSetup.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/YsmEntityLookup.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/YsmSubmitContext.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/layer/CustomParrotOnShoulderLayer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/layer/CustomPlayerElytraLayer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/layer/CustomPlayerHeadLayer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/layer/CustomPlayerItemInHandLayer.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/replace/EntityRendererReplace.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/replace/FishingHookRendererReplace.java`
- `src/main/java/com/elfmcys/ysm/client/renderer/replace/ProjectileRendererReplace.java`
- `src/main/java/com/elfmcys/ysm/client/sound/instance/SoundInstanceManager.java`
- `src/main/java/com/elfmcys/ysm/client/sound/stream/VorbisAudioStream.java`
- `src/main/java/com/elfmcys/ysm/client/texture/CustomPBRTextureSet.java`
- `src/main/java/com/elfmcys/ysm/client/texture/CustomTexture.java`
- `src/main/java/com/elfmcys/ysm/client/texture/CustomTextureManager.java`
- `src/main/java/com/elfmcys/ysm/client/texture/ModelTexture.java`
- `src/main/java/com/elfmcys/ysm/client/texture/TextureHolder.java`
- `src/main/java/com/elfmcys/ysm/command/sub/AuthCommand.java`
- `src/main/java/com/elfmcys/ysm/command/sub/ModelCommand.java`
- `src/main/java/com/elfmcys/ysm/command/sub/PingCommand.java`
- `src/main/java/com/elfmcys/ysm/command/sub/PlayAnimationCommand.java`
- `src/main/java/com/elfmcys/ysm/config/ClientConfig.java`
- `src/main/java/com/elfmcys/ysm/config/ExtraPlayerScreenConfig.java`
- `src/main/java/com/elfmcys/ysm/config/LoadingStateScreenConfig.java`
- `src/main/java/com/elfmcys/ysm/config/ServerConfig.java`
- `src/main/java/com/elfmcys/ysm/event/CapabilityEvent.java`
- `src/main/java/com/elfmcys/ysm/event/CommandRegistry.java`
- `src/main/java/com/elfmcys/ysm/event/CommonEvent.java`
- `src/main/java/com/elfmcys/ysm/event/InterModMsg.java`
- `src/main/java/com/elfmcys/ysm/event/LivingShieldBlockEvent.java`
- `src/main/java/com/elfmcys/ysm/event/LoggedOutEvent.java`
- `src/main/java/com/elfmcys/ysm/event/LoginEvent.java`
- `src/main/java/com/elfmcys/ysm/event/MobEffectSyncEvent.java`
- `src/main/java/com/elfmcys/ysm/event/ServerRuntimeShutdownEvent.java`
- `src/main/java/com/elfmcys/ysm/event/ServerStartingEvent.java`
- `src/main/java/com/elfmcys/ysm/event/api/SpecialPlayerRenderEvent.java`
- `src/main/java/com/elfmcys/ysm/format/schema/model/ModelFileWriter.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/binding/ContextBinding.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/QueryBinding.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/BiomeHasAllTags.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/BiomeHasAnyTag.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/EquippedItemAllTags.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/EquippedItemAnyTags.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/ItemNameAny.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/RelativeBlockHasAllTags.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/builtin/query/RelativeBlockHasAnyTag.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/context/MolangContext.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/function/entity/AbstractArrowEntityFunction.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/function/entity/ArrowEntityFunction.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/variable/entity/AbstractArrowVariable.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/variable/entity/ArrowVariable.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/core/molang/variable/entity/ThrowableItemProjectileVariable.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/CustomTranslucentRenderType.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/GeoEntityRenderer.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/GeoLayerRenderer.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/GeoProjectilesRenderer.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/GeoRenderData.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/GeoReplacedEntityRenderer.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/IGeoRenderer.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/animated/GeoModelState.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/exception/GeckoLibException.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/render/built/GeoLocatorType.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/geo/render/built/GeoLocatorTypeRegistry.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/model/AnimatableEntity.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/util/AnimationUtils.java`
- `src/main/java/com/elfmcys/ysm/geckolib3/util/json/JsonKeyFrameUtils.java`
- `src/main/java/com/elfmcys/ysm/info/type/PBRTextureType.java`
- `src/main/java/com/elfmcys/ysm/init/ModItemTags.java`
- `src/main/java/com/elfmcys/ysm/init/ModSounds.java`
- `src/main/java/com/elfmcys/ysm/mixin/AbstractArrowEntityMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/ServerPlayerMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/ArrowEntityAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/BufferBuilderMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/BufferSourceMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/EntityMovementMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/EntityRenderDispatcherMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/GameRendererMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/InventoryScreenMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/LevelRendererMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/LivingEntityAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/LivingEntitySwingStateAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/LivingRendererMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/ProjectionMatrixBufferMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/ThrowableItemProjectileAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/create/PlayerSkyhookRendererAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/embeddium/SodiumBufferBuilderMixin.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/AnimationAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/DodgeAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/FlippingAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/HorizontalWallRunAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/RollAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/SpeedVaultAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/client/parcool/WallJumpAnimatorAccessor.java`
- `src/main/java/com/elfmcys/ysm/mixin/plugin/MixinTweaker.java`
- `src/main/java/com/elfmcys/ysm/model/ModelRuntime.java`
- `src/main/java/com/elfmcys/ysm/model/catalog/client/ClientCatalogManager.java`
- `src/main/java/com/elfmcys/ysm/model/catalog/source/ModelCatalogSources.java`
- `src/main/java/com/elfmcys/ysm/model/resource/client/ModelRenderTarget.java`
- `src/main/java/com/elfmcys/ysm/model/resource/client/preview/ClientPreviewGenerator.java`
- `src/main/java/com/elfmcys/ysm/model/resource/client/render/HostTexturePublisher.java`
- `src/main/java/com/elfmcys/ysm/model/resource/client/render/ModelRenderTargetLoader.java`
- `src/main/java/com/elfmcys/ysm/model/service/ClientModelService.java`
- `src/main/java/com/elfmcys/ysm/model/service/ServerModelService.java`
- `src/main/java/com/elfmcys/ysm/molang/parser/ast/StringExpression.java`
- `src/main/java/com/elfmcys/ysm/molang/runtime/Function.java`
- `src/main/java/com/elfmcys/ysm/natives/render/BonePoseView.java`
- `src/main/java/com/elfmcys/ysm/natives/render/DeferredModelDraw.java`
- `src/main/java/com/elfmcys/ysm/natives/render/FallbackVertexWriter.java`
- `src/main/java/com/elfmcys/ysm/natives/render/HostProjection.java`
- `src/main/java/com/elfmcys/ysm/natives/render/NativeRenderer.java`
- `src/main/java/com/elfmcys/ysm/network/NetworkHandler.java`
- `src/main/java/com/elfmcys/ysm/network/ProtocolInbound.java`
- `src/main/java/com/elfmcys/ysm/network/YsmFramePayload.java`
- `src/main/java/com/elfmcys/ysm/network/dispatch/ServerAssetTransfers.java`
- `src/main/java/com/elfmcys/ysm/network/forge/ClientSessionRuntime.java`
- `src/main/java/com/elfmcys/ysm/network/forge/ControlHandler.java`
- `src/main/java/com/elfmcys/ysm/network/forge/ForgeTransportPort.java`
- `src/main/java/com/elfmcys/ysm/network/forge/ForgeUniBufferIO.java`
- `src/main/java/com/elfmcys/ysm/network/forge/MinecraftStateHandler.java`
- `src/main/java/com/elfmcys/ysm/network/forge/PlayerStateHandler.java`
- `src/main/java/com/elfmcys/ysm/network/forge/SessionProtocolHandler.java`
- `src/main/java/com/elfmcys/ysm/network/forge/YsmPacketCompressionBypass.java`
- `src/main/java/com/elfmcys/ysm/network/protocol/ProtocolMessageSpec.java`
- `src/main/java/com/elfmcys/ysm/network/protocol/ProtocolMessages.java`
- `src/main/java/com/elfmcys/ysm/util/AnimatableCacheUtil.java`
- `src/main/java/com/elfmcys/ysm/util/CommandUtil.java`
- `src/main/java/com/elfmcys/ysm/util/EnumUtil.java`
- `src/main/java/com/elfmcys/ysm/util/InputCheckUtil.java`
- `src/main/java/com/elfmcys/ysm/util/ModelIdUtil.java`
- `src/main/java/com/elfmcys/ysm/util/NativeLibUtil.java`
- `src/main/java/com/elfmcys/ysm/util/RegistryIds.java`
- `src/main/java/com/elfmcys/ysm/util/RenderUtil.java`
- `src/main/resources/META-INF/neoforge.mods.toml`
- `src/main/resources/assets/ysm/lang/en_us.json`
- `src/main/resources/assets/ysm/lang/ko_kr.json`
- `src/main/resources/assets/ysm/lang/ru_ru.json`
- `src/main/resources/assets/ysm/lang/zh_cn.json`
- `src/main/resources/ysm.mixins.json`

Publication also updates README.md, NOTICE.md, .gitignore, buildSrc/build.gradle, mod metadata and the build scripts. Supplemental native license texts are in native/LICENSES/.
