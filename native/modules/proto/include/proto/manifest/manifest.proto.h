#pragma once
#include <ylt/struct_pb.hpp>
#include "proto/manifest/asset/render_target.proto.h"
#include "proto/manifest/asset/common.proto.h"
#include "proto/manifest/info/info.proto.h"

namespace ysm::proto::manifest {


struct Manifest : public iguana::base_impl<Manifest>  {
	Manifest() = default;
	Manifest(std::vector<::ysm::proto::manifest::asset::RenderTarget>a, ::ysm::proto::manifest::asset::Common b, ::ysm::proto::manifest::info::Info c) : render_targets(std::move(a)), common_assets(b), info(c) {}
	std::vector<::ysm::proto::manifest::asset::RenderTarget>render_targets;
	::ysm::proto::manifest::asset::Common common_assets;
	::ysm::proto::manifest::info::Info info;
};
YLT_REFL(Manifest, render_targets, common_assets, info);

}
