#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::common {

struct Image : public iguana::base_impl<Image>  {
	Image() = default;
	Image(uint32_t a, std::string b, uint32_t c, uint32_t d, uint32_t e) : blob_id(a), format(std::move(b)), width(c), height(d), frame_count(e) {}
	uint32_t blob_id;
	std::string format;
	uint32_t width;
	uint32_t height;
	uint32_t frame_count;
};
YLT_REFL(Image, blob_id, format, width, height, frame_count);

}