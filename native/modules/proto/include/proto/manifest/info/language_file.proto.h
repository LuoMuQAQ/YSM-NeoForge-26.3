#pragma once
#include <ylt/struct_pb.hpp>

namespace ysm::proto::manifest::info {
struct EntriesEntry : public iguana::base_impl<EntriesEntry> {
	EntriesEntry() = default;
	EntriesEntry(std::string a, std::string b) : key(std::move(a)), value(std::move(b)) {}
	std::string key;
	std::string value;
};
YLT_REFL(EntriesEntry, key, value);

struct LanguageFile : public iguana::base_impl<LanguageFile>  {
	LanguageFile() = default;
	LanguageFile(std::string a, std::vector<EntriesEntry>b) : locale(std::move(a)), entries(std::move(b)) {}
	std::string locale;
	std::vector<EntriesEntry>entries;
};
YLT_REFL(LanguageFile, locale, entries);

}
