#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::manifest::info {

struct ExportInfo : public iguana::base_impl<ExportInfo>  {
	ExportInfo() = default;
	ExportInfo(uint64_t a, std::string b, std::string c) : timestamp(a), version(std::move(b)), extra(std::move(c)) {}
	uint64_t timestamp;
	std::string version;
	std::string extra;
};
YLT_REFL(ExportInfo, timestamp, version, extra);

}