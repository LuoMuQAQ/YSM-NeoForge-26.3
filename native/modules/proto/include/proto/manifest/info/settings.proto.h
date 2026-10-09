#pragma once
#include <optional>
#include <ylt/struct_pb.hpp>

#include "proto/common/image.proto.h"
#include "proto/common/program.proto.h"
#include "proto/common/string_pair.proto.h"

namespace ysm::proto::manifest::info {

struct ConfigLabel : public iguana::base_impl<ConfigLabel> {
    ConfigLabel() = default;
    ConfigLabel(std::string a, ::ysm::proto::common::Program b)
        : name(std::move(a)), action_program(std::move(b)) {}
    std::string name;
    ::ysm::proto::common::Program action_program;
};
YLT_REFL(ConfigLabel, name, action_program);

struct ConfigForms : public iguana::base_impl<ConfigForms> {
    ConfigForms() = default;
    ConfigForms(std::string a, std::string b, std::string c,
                ::ysm::proto::common::Program d, float e, float f,
                float g, std::vector<ConfigLabel> h,
                ::ysm::proto::common::Program i)
        : type(std::move(a)), title(std::move(b)), description(std::move(c)),
          read_program(std::move(d)), step(e), min(f), max(g),
          labels(std::move(h)), write_program(std::move(i)) {}
    std::string type;
    std::string title;
    std::string description;
    ::ysm::proto::common::Program read_program;
    float step;
    float min;
    float max;
    std::vector<ConfigLabel> labels;
    ::ysm::proto::common::Program write_program;
};
YLT_REFL(ConfigForms, type, title, description, read_program, step, min, max,
         labels, write_program);

struct ExtraAnimationButton : public iguana::base_impl<ExtraAnimationButton> {
    ExtraAnimationButton() = default;
    ExtraAnimationButton(std::string a, std::string b, std::string c,
                         std::vector<ConfigForms> d)
        : id(std::move(a)), name(std::move(b)), sound(std::move(c)),
          config_forms(std::move(d)) {}
    std::string id;
    std::string name;
    std::string sound;
    std::vector<ConfigForms> config_forms;
};
YLT_REFL(ExtraAnimationButton, id, name, sound, config_forms);

struct ExtraAnimationClassify
    : public iguana::base_impl<ExtraAnimationClassify> {
    ExtraAnimationClassify() = default;
    ExtraAnimationClassify(
        std::string a,
        std::vector<::ysm::proto::common::StringPair> b)
        : id(std::move(a)), extra_animation(std::move(b)) {}
    std::string id;
    std::vector<::ysm::proto::common::StringPair> extra_animation;
};
YLT_REFL(ExtraAnimationClassify, id, extra_animation);

struct Settings : public iguana::base_impl<Settings> {
    Settings() = default;
    Settings(std::vector<::ysm::proto::common::StringPair> a,
             std::vector<ExtraAnimationButton> b,
             std::vector<ExtraAnimationClassify> c,
             std::optional<std::string> d, std::optional<std::string> e,
             bool f,
             std::optional<::ysm::proto::common::Image> g,
             std::optional<::ysm::proto::common::Image> h)
        : extra_animation(std::move(a)),
          extra_animation_buttons(std::move(b)),
          extra_animation_classify(std::move(c)),
          default_texture(std::move(d)), preview_animation(std::move(e)),
          disable_preview_rotation(f), gui_foreground(std::move(g)),
          gui_background(std::move(h)) {}
    std::vector<::ysm::proto::common::StringPair> extra_animation;
    std::vector<ExtraAnimationButton> extra_animation_buttons;
    std::vector<ExtraAnimationClassify> extra_animation_classify;
    std::optional<std::string> default_texture;
    std::optional<std::string> preview_animation;
    bool disable_preview_rotation;
    std::optional<::ysm::proto::common::Image> gui_foreground;
    std::optional<::ysm::proto::common::Image> gui_background;
};
YLT_REFL(Settings, extra_animation, extra_animation_buttons,
         extra_animation_classify, default_texture, preview_animation,
         disable_preview_rotation, gui_foreground, gui_background);

}
