#pragma once
#include <optional>
#include <ylt/struct_pb.hpp>
#include "proto/common/image.proto.h"

namespace ysm::proto::manifest::asset {


struct PBRTextureSet : public iguana::base_impl<PBRTextureSet>  {
	PBRTextureSet() = default;
	PBRTextureSet(::ysm::proto::common::Image a, std::optional<::ysm::proto::common::Image> b, std::optional<::ysm::proto::common::Image> c) : uv(a), normal(std::move(b)), specular(std::move(c)) {}
	::ysm::proto::common::Image uv;
	std::optional<::ysm::proto::common::Image> normal;
	std::optional<::ysm::proto::common::Image> specular;
};
YLT_REFL(PBRTextureSet, uv, normal, specular);

}
