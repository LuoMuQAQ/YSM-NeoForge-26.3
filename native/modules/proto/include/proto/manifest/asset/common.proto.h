#pragma once
#include <ylt/struct_pb.hpp>
#include "proto/common/sound.proto.h"

namespace ysm::proto::manifest::asset {


struct Common : public iguana::base_impl<Common>  {
	Common() = default;
	Common(std::vector<::ysm::proto::common::Sound>a, uint32_t b) : sounds(std::move(a)), strings_blob_id(b) {}
	std::vector<::ysm::proto::common::Sound>sounds;
	uint32_t strings_blob_id;
};
YLT_REFL(Common, sounds, strings_blob_id);

}
