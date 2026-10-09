#pragma once
#include <string>
#include <string_view>
#include <variant>
#include <ylt/struct_pb.hpp>

namespace ysm::proto::common {

struct Program : public iguana::base_impl<Program> {
    Program() = default;
    Program(uint32_t a, std::variant<std::string_view, std::string> b)
        : format(a), payload(std::move(b)) {}
    uint32_t format = 0;
    // struct_pb assigns consecutive oneof tags by variant index. The distinct
    // string types keep bytecode (field 2) separate from owned source (field 3).
    std::variant<std::string_view, std::string> payload;
};
YLT_REFL(Program, format, payload);

}
