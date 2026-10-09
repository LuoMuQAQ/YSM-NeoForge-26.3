#pragma once
#include <ylt/struct_pb.hpp>
#include "proto/asset/strings/data/user_func.proto.h"

namespace ysm::proto::asset::strings {


struct StringData : public iguana::base_impl<StringData>  {
	StringData() = default;
	StringData(std::vector<::ysm::proto::asset::strings::data::UserFunction>a) : user_functions(std::move(a)) {}
	std::vector<::ysm::proto::asset::strings::data::UserFunction>user_functions;
};
YLT_REFL(StringData, user_functions);

}
