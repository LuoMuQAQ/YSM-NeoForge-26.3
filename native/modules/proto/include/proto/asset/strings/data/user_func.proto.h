#pragma once
#include <ylt/struct_pb.hpp>
#include "proto/common/program.proto.h"

namespace ysm::proto::asset::strings::data {

struct UserFunction : public iguana::base_impl<UserFunction>  {
	UserFunction() = default;
	UserFunction(std::string a, ::ysm::proto::common::Program b) : name(std::move(a)), body(std::move(b)) {}
	std::string name;
	::ysm::proto::common::Program body;
};
YLT_REFL(UserFunction, name, body);

}
