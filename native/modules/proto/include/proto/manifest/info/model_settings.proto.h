#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::manifest::info {

struct ModelSettings : public iguana::base_impl<ModelSettings>  {
	ModelSettings() = default;
	ModelSettings(float a, float b, bool c, bool d, bool e, bool f) : height_scale(a), width_scale(b), render_layers_first(c), force_culling(d), gui_no_lighting(e), merge_multiline_expr(f) {}
	float height_scale;
	float width_scale;
	bool render_layers_first;
	bool force_culling;
	bool gui_no_lighting;
	bool merge_multiline_expr;
};
YLT_REFL(ModelSettings, height_scale, width_scale, render_layers_first, force_culling, gui_no_lighting, merge_multiline_expr);

}