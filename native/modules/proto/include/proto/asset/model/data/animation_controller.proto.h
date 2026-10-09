#pragma once
#include <optional>
#include <ylt/struct_pb.hpp>

#include "proto/common/program.proto.h"

namespace ysm::proto::asset::model::data {

struct BlendPoint : public iguana::base_impl<BlendPoint> {
    BlendPoint() = default;
    BlendPoint(float a, float b) : key(a), value(b) {}
    float key;
    float value;
};
YLT_REFL(BlendPoint, key, value);

struct BlendTransition : public iguana::base_impl<BlendTransition> {
    BlendTransition() = default;
    BlendTransition(std::optional<float> a, std::vector<BlendPoint> b)
        : linear_length(a), points(std::move(b)) {}
    std::optional<float> linear_length;
    std::vector<BlendPoint> points;
};
YLT_REFL(BlendTransition, linear_length, points);

struct Transition : public iguana::base_impl<Transition> {
    Transition() = default;
    Transition(std::string a, ::ysm::proto::common::Program b)
        : dst(std::move(a)), condition(std::move(b)) {}
    std::string dst;
    ::ysm::proto::common::Program condition;
};
YLT_REFL(Transition, dst, condition);

struct AnimationReference : public iguana::base_impl<AnimationReference> {
    AnimationReference() = default;
    AnimationReference(std::string a,
                       ::ysm::proto::common::Program b)
        : name(std::move(a)), condition(std::move(b)) {}
    std::string name;
    ::ysm::proto::common::Program condition;
};
YLT_REFL(AnimationReference, name, condition);

struct State : public iguana::base_impl<State> {
    State() = default;
    State(std::string a, std::vector<AnimationReference> b,
          std::vector<Transition> c,
          std::optional<::ysm::proto::common::Program> d,
          std::optional<::ysm::proto::common::Program> e,
          std::optional<BlendTransition> f, std::vector<std::string> g)
        : name(std::move(a)), animations(std::move(b)),
          transitions(std::move(c)), on_entry(std::move(d)),
          on_exit(std::move(e)), blend_transition(std::move(f)),
          sound_effects(std::move(g)) {}
    std::string name;
    std::vector<AnimationReference> animations;
    std::vector<Transition> transitions;
    std::optional<::ysm::proto::common::Program> on_entry;
    std::optional<::ysm::proto::common::Program> on_exit;
    std::optional<BlendTransition> blend_transition;
    std::vector<std::string> sound_effects;
};
YLT_REFL(State, name, animations, transitions, on_entry, on_exit,
         blend_transition, sound_effects);

struct AnimationController : public iguana::base_impl<AnimationController> {
    AnimationController() = default;
    AnimationController(std::string a, std::optional<std::string> b,
                        std::vector<State> c)
        : name(std::move(a)), default_state(std::move(b)),
          states(std::move(c)) {}
    std::string name;
    std::optional<std::string> default_state;
    std::vector<State> states;
};
YLT_REFL(AnimationController, name, default_state, states);

struct AnimationControllerFile
    : public iguana::base_impl<AnimationControllerFile> {
    AnimationControllerFile() = default;
    explicit AnimationControllerFile(std::vector<AnimationController> a)
        : controllers(std::move(a)) {}
    std::vector<AnimationController> controllers;
};
YLT_REFL(AnimationControllerFile, controllers);

}
