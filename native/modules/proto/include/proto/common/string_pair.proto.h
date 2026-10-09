#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::common {

struct StringPair : public iguana::base_impl<StringPair>  {
	StringPair() = default;
	StringPair(std::string a, std::string b) : key(std::move(a)), value(std::move(b)) {}
	std::string key;
	std::string value;
};
YLT_REFL(StringPair, key, value);

}