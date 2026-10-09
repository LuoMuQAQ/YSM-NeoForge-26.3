#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <buffer_managed.h>

namespace ysm::legacy::v3::container {
template <typename Key, typename Value>
using OrderedMap = std::vector<std::pair<Key, Value>>;

enum class ImageEncoding : std::uint32_t {
    kRgba = 1,
    kPng = 2,
    kJpeg = 3,
    kWebp = 4,
    kAvif = 5,
    kZtx = 6,
};

enum class ImageRole : std::uint8_t {
    kTexture,
    kGui,
    kAvatar,
    kIcon,
    kThumbnail,
};

enum class SoundEncoding : std::uint8_t { kVorbis, kOpus };

enum class PbrType : std::uint32_t { kNormal = 1, kSpecular = 2 };

enum class MolangValueType : std::uint32_t {
    kNone = 0,
    kDouble = 1,
    kString = 2,
};

enum class EasingType : std::uint32_t {
    kNone = 0,
    kLinear = 1,
    kCatmullRom = 2,
};

enum class LoopType : std::uint32_t {
    kLoop = 1,
    kPlayOnce = 2,
    kHoldOnLastFrame = 3,
};

enum class PlayerModelType : std::uint32_t { kMain = 1, kArm = 2 };

enum class PlayerAnimationType : std::uint32_t {
    kMain = 1,
    kArm = 2,
    kExtra = 3,
    kTac = 4,
    kCarryOn = 6,
    kParCool = 7,
    kSwem = 8,
    kSlashBlade = 9,
    kTlm = 10,
    kFirstPersonArm = 11,
    kImmersiveMelodies = 12,
    kIronsSpellBooks = 13,
};

struct Vec3 {
    float x{};
    float y{};
    float z{};
};

struct Vertex {
    Vec3 position;
    float texture_u{};
    float texture_v{};
};

struct Quad {
    Vec3 normal;
    std::array<Vertex, 4> vertices;
};

struct Cube {
    std::vector<Quad> quads;
    std::array<bool, 3> zero_size{};
};

struct ExtraInfo {
    std::string name;
    std::string tips;
    std::vector<std::string> extra_animation_names;
    std::vector<std::string> authors;
    std::string license;
    bool free{};
};

struct ModelScript {
    OrderedMap<std::string, std::string> variables;
    std::vector<std::string> initialize;
    std::vector<std::string> pre_animation;
};

struct GeoProperties {
    std::string identifier;
    double texture_height{};
    double texture_width{};
    double visible_bounds_height{};
    double visible_bounds_width{};
    std::vector<double> visible_bounds_offset;
    double height_scale{0.7};
    double width_scale{0.7};
    std::optional<ExtraInfo> extra_info;
    ModelScript script;
};

struct Bone {
    std::string parent;
    std::vector<Cube> cubes;
    std::string name;
    bool dont_render{};
    bool hidden{};
    bool cubes_hidden{};
    bool hide_children{};
    bool reset{};
    Vec3 pivot;
    Vec3 rotation;
};

struct GeoModel {
    std::vector<Bone> bones;
    GeoProperties properties;
    std::optional<std::uint32_t> v5_global_cube_count;
};

struct MolangValue {
    MolangValueType type{MolangValueType::kNone};
    double double_value{};
    std::string string_value;
};

struct MolangVec3 {
    MolangValue x;
    MolangValue y;
    MolangValue z;
};

struct BoneKeyFrame {
    double start_tick{};
    EasingType easing{EasingType::kNone};
    MolangVec3 pre;
    std::optional<MolangVec3> post;
};

struct BoneAnimation {
    std::string bone_name;
    std::vector<BoneKeyFrame> rotations;
    std::vector<BoneKeyFrame> positions;
    std::vector<BoneKeyFrame> scales;
};

struct EventKeyFrame {
    std::string data;
    double start_tick{};
};

struct InstructionKeyFrame {
    std::vector<std::string> data;
    double start_tick{};
};

struct Animation {
    std::string name;
    double length{-1};
    LoopType loop{LoopType::kLoop};
    std::optional<MolangValue> start_delay;
    std::optional<MolangValue> loop_delay;
    std::optional<MolangValue> blend_weight;
    std::optional<bool> override_previous;
    std::vector<BoneAnimation> bones;
    std::vector<InstructionKeyFrame> instructions;
    std::vector<EventKeyFrame> sounds;
};

struct AnimationFile {
    std::vector<Animation> animations;
};

struct AnimationEntry {
    std::string name;
    std::string condition;
};

struct Transition {
    std::string destination;
    std::string condition;
};

struct BlendTransition {
    std::optional<float> linear_length;
    OrderedMap<float, float> points;
};

struct ControllerState {
    std::vector<AnimationEntry> animations;
    std::vector<Transition> transitions;
    std::vector<std::string> on_entry;
    std::vector<std::string> on_exit;
    BlendTransition blend_transition;
    bool blend_via_shortest_path{};
    std::vector<std::string> sound_effects;
};

struct AnimationController {
    std::string initial_state{"default"};
    OrderedMap<std::string, ControllerState> states;
};

struct AnimationControllerFile {
    OrderedMap<std::string, AnimationController> controllers;
};

struct LegacyImage {
    BufferManaged bytes;
    std::uint32_t width{};
    std::uint32_t height{};
    ImageEncoding encoding{ImageEncoding::kRgba};
    std::uint32_t frame_count{1};
    ImageRole role{ImageRole::kTexture};
};

template <typename Value>
struct WithSourceHash {
    std::string source_hash;
    Value value;
};

struct TextureSet {
    WithSourceHash<LegacyImage> uv;
    OrderedMap<PbrType, WithSourceHash<LegacyImage>> pbr;
};

struct ModelAuthor {
    std::string name;
    std::string role;
    OrderedMap<std::string, std::string> contact;
    std::string comment;
};

struct ModelLicense {
    std::string type{"All Rights Reserved"};
    std::string description;
};

struct ModelMetadata {
    std::string name;
    std::string tips;
    ModelLicense license;
    std::vector<ModelAuthor> authors;
    OrderedMap<std::string, std::string> links;
};

struct ConfigForm {
    std::string type;
    std::string title;
    std::string description;
    std::string value;
    double step{1};
    double minimum{};
    double maximum{100};
    OrderedMap<std::string, std::string> labels;
};

struct ExtraAnimationButton {
    std::string id;
    std::string name;
    std::string sound;
    std::vector<ConfigForm> forms;
};

struct ExtraAnimationClassify {
    std::string id;
    OrderedMap<std::string, std::string> animations;
};

struct ModelProperties {
    float height_scale{0.7F};
    float width_scale{0.7F};
    OrderedMap<std::string, std::string> extra_animation;
    std::vector<ExtraAnimationButton> extra_animation_buttons;
    std::vector<ExtraAnimationClassify> extra_animation_classify;
    std::string default_texture;
    std::string preview_animation;
    bool render_layers_first{};
    bool free{};
    bool all_cutout{};
    bool disable_preview_rotation{};
    bool gui_no_lighting{};
    bool merge_multiline_expr{};
    std::string gui_foreground;
    std::string gui_background;
};

struct ModelInfo {
    std::string hash;
    std::uint32_t origin_version{};
    std::optional<ModelMetadata> metadata;
    ModelProperties properties;
    OrderedMap<std::string, LegacyImage> author_avatars;
    OrderedMap<std::string, LegacyImage> gui_images;
};

struct ExportInfo {
    std::string random;
    std::uint64_t timestamp{};
    std::string extra;
};

struct LanguageFile {
    OrderedMap<std::string, std::string> entries;
};

struct LegacySound {
    BufferManaged bytes;
    SoundEncoding encoding{SoundEncoding::kVorbis};
    std::uint32_t channels{};
    std::uint32_t sample_rate{};
    std::uint64_t samples{};
};

struct CommonAssets {
    OrderedMap<std::string, WithSourceHash<LegacySound>> sounds;
    OrderedMap<std::string, WithSourceHash<std::string>> user_functions;
    OrderedMap<std::string, WithSourceHash<LanguageFile>> languages;
};

struct PlayerModel {
    OrderedMap<PlayerModelType, WithSourceHash<GeoModel>> geo_models;
    OrderedMap<PlayerAnimationType, WithSourceHash<AnimationFile>> animations;
    OrderedMap<std::string, WithSourceHash<AnimationControllerFile>>
        controllers;
    OrderedMap<std::string, TextureSet> textures;
};

struct ReplaceModel {
    std::string source_key;
    std::vector<std::string> match;
    std::optional<WithSourceHash<GeoModel>> geo;
    std::optional<WithSourceHash<AnimationFile>> animation;
    std::optional<WithSourceHash<AnimationControllerFile>> controller;
    std::optional<TextureSet> texture;
};

struct LegacyModel {
    std::uint32_t version{};
    std::uint32_t sound_field_count{};
    std::uint32_t omitted_sound_count{};
    CommonAssets common;
    std::vector<ReplaceModel> vehicles;
    std::vector<ReplaceModel> projectiles;
    std::optional<PlayerModel> player;
    ModelInfo info;
    std::optional<ExportInfo> export_info;
    std::string order_info;
    std::array<Byte, 32> model_id{};
};
}  // namespace ysm::legacy::v3::container
