#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::common {

struct Sound : public iguana::base_impl<Sound>  {
	Sound() = default;
	Sound(std::string a, std::string b, uint32_t c, uint32_t d, uint64_t e, uint32_t f) : name(std::move(a)), encoding(std::move(b)), channels(c), sample_rate(d), samples(e), stream_id(f) {}
	std::string name;
	std::string encoding;
	uint32_t channels;
	uint32_t sample_rate;
	uint64_t samples;
	uint32_t stream_id;
};
YLT_REFL(Sound, name, encoding, channels, sample_rate, samples, stream_id);

}