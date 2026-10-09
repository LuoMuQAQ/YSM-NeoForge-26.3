#pragma once
#include <optional>
#include <ylt/struct_pb.hpp>
#include "proto/common/image.proto.h"
#include "proto/common/string_pair.proto.h"

namespace ysm::proto::manifest::info {


struct Author : public iguana::base_impl<Author>  {
	Author() = default;
	Author(std::string a, std::string b, std::vector<::ysm::proto::common::StringPair>c, std::optional<std::string> d, std::optional<::ysm::proto::common::Image> e) : name(std::move(a)), role(std::move(b)), contacts(std::move(c)), comment(std::move(d)), avatar(std::move(e)) {}
	std::string name;
	std::string role;
	std::vector<::ysm::proto::common::StringPair>contacts;
	std::optional<std::string> comment;
	std::optional<::ysm::proto::common::Image> avatar;
};
YLT_REFL(Author, name, role, contacts, comment, avatar);

struct License : public iguana::base_impl<License>  {
	License() = default;
	License(std::string a, std::optional<std::string> b) : type(std::move(a)), desc(std::move(b)) {}
	std::string type;
	std::optional<std::string> desc;
};
YLT_REFL(License, type, desc);

struct Metadata : public iguana::base_impl<Metadata>  {
	Metadata() = default;
	Metadata(std::string a, std::optional<std::string> b, License c, std::vector<Author>d, std::vector<::ysm::proto::common::StringPair>e) : name(std::move(a)), tips(std::move(b)), license(c), authors(std::move(d)), links(std::move(e)) {}
	std::string name;
	std::optional<std::string> tips;
	License license;
	std::vector<Author>authors;
	std::vector<::ysm::proto::common::StringPair>links;
};
YLT_REFL(Metadata, name, tips, license, authors, links);

}
