#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::manifest::info {

struct Properties : public iguana::base_impl<Properties>  {
	Properties() = default;
	Properties(std::string a, bool b, std::string c) : model_id(std::move(a)), free(b), origin_ver(std::move(c)) {}
	std::string model_id;
	bool free;
	std::string origin_ver;
};
YLT_REFL(Properties, model_id, free, origin_ver);

}
