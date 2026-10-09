#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::manifest::info {

struct ModelStats : public iguana::base_impl<ModelStats>  {
	ModelStats() = default;
	ModelStats(uint32_t a, uint32_t b, uint32_t c) : bones(a), cubes(b), faces(c) {}
	uint32_t bones;
	uint32_t cubes;
	uint32_t faces;
};
YLT_REFL(ModelStats, bones, cubes, faces);

}