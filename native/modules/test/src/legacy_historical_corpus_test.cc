#include <gtest/gtest.h>

#include <absl/status/status.h>
#include <cryptopp/sha.h>

#include <algorithm>
#include <array>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <future>
#include <limits>
#include <map>
#include <optional>
#include <ranges>
#include <span>
#include <stdexcept>
#include <string>
#include <string_view>
#include <vector>

#include <legacy/v3.h>

#include <v3/codec/envelope.h>
#include <v3/container/decoder.h>
#include <v3/conversion/projector.h>
#include <legacy/result_owner.h>

namespace ysm::legacy::v3::container {
namespace {
std::string Hex(std::span<const Byte> bytes) {
    constexpr std::string_view kDigits = "0123456789abcdef";
    std::string result(bytes.size() * 2, '0');
    for (std::size_t index = 0; index < bytes.size(); ++index) {
        result[index * 2] = kDigits[bytes[index] >> 4U];
        result[index * 2 + 1] = kDigits[bytes[index] & 0x0FU];
    }
    return result;
}

std::string Sha256(std::span<const Byte> bytes) {
    std::array<Byte, CryptoPP::SHA256::DIGESTSIZE> digest{};
    CryptoPP::SHA256 hasher;
    hasher.Update(bytes.data(), bytes.size());
    hasher.Final(digest.data());
    return Hex(digest);
}

std::string FileSha256(const std::filesystem::path& path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        throw std::runtime_error("Unable to hash corpus source");
    }
    CryptoPP::SHA256 hasher;
    std::array<char, 64 * 1024> buffer{};
    while (input) {
        input.read(buffer.data(), buffer.size());
        hasher.Update(reinterpret_cast<const Byte*>(buffer.data()),
                      static_cast<std::size_t>(input.gcount()));
    }
    std::array<Byte, CryptoPP::SHA256::DIGESTSIZE> digest{};
    hasher.Final(digest.data());
    return Hex(digest);
}

std::vector<Byte> ReadFile(const std::filesystem::path& path) {
    const auto size = std::filesystem::file_size(path);
    std::vector<Byte> bytes(size);
    std::ifstream input(path, std::ios::binary);
    input.read(reinterpret_cast<char*>(bytes.data()),
               static_cast<std::streamsize>(bytes.size()));
    if (!input) {
        throw std::runtime_error("Unable to read corpus source");
    }
    return bytes;
}

std::string RelativePathId(const std::filesystem::path& root,
                           const std::filesystem::path& path) {
    const auto relative = path.lexically_relative(root).generic_u8string();
    return Sha256(std::span(reinterpret_cast<const Byte*>(relative.data()),
                            relative.size()));
}

std::uint32_t ReadU32(std::span<const Byte> bytes, std::size_t offset) {
    return static_cast<std::uint32_t>(bytes[offset]) |
           static_cast<std::uint32_t>(bytes[offset + 1]) << 8U |
           static_cast<std::uint32_t>(bytes[offset + 2]) << 16U |
           static_cast<std::uint32_t>(bytes[offset + 3]) << 24U;
}

std::optional<std::uint32_t> RawVersion(const std::filesystem::path& path) {
    std::ifstream input(path, std::ios::binary);
    std::array<Byte, 8> header{};
    input.read(reinterpret_cast<char*>(header.data()), header.size());
    if (input.gcount() != static_cast<std::streamsize>(header.size()) ||
        !std::equal(header.begin(), header.begin() + 4,
                    std::string_view("YSGP").begin())) {
        return std::nullopt;
    }
    return static_cast<std::uint32_t>(header[4]) << 24U |
           static_cast<std::uint32_t>(header[5]) << 16U |
           static_cast<std::uint32_t>(header[6]) << 8U |
           static_cast<std::uint32_t>(header[7]);
}

std::string PayloadSetSha256(const ImportResult& result) {
    CryptoPP::SHA256 hasher;
    for (const auto& payload : result.payloads) {
        std::array<Byte, 8> size{};
        auto value = static_cast<std::uint64_t>(payload.bytes.size());
        for (auto& byte : size) {
            byte = static_cast<Byte>(value);
            value >>= 8U;
        }
        hasher.Update(size.data(), size.size());
        hasher.Update(payload.bytes.data(), payload.bytes.size());
    }
    std::array<Byte, CryptoPP::SHA256::DIGESTSIZE> digest{};
    hasher.Final(digest.data());
    return Hex(digest);
}

std::string ImageTokens(const ImportResult& imported) {
    std::string result;
    for (const auto& payload : imported.payloads) {
        if (payload.kind != PayloadKind::kBlobImage &&
            payload.kind != PayloadKind::kNamedImage) {
            continue;
        }
        if (!result.empty()) {
            result += ',';
        }
        result += std::to_string(static_cast<std::uint16_t>(payload.encoding));
        result += ':';
        result += std::to_string(payload.image->width);
        result += 'x';
        result += std::to_string(payload.image->height);
    }
    return result;
}

std::string StatusName(absl::StatusCode status) {
    return std::string(absl::StatusCodeToString(status));
}

std::size_t SoundKeyframeCount(const AnimationFile& file) {
    std::size_t result = 0;
    for (const auto& animation : file.animations) {
        result += animation.sounds.size();
    }
    return result;
}

std::size_t SoundKeyframeCount(const LegacyModel& model) {
    std::size_t result = 0;
    if (model.player) {
        for (const auto& [_, animation] : model.player->animations) {
            result += SoundKeyframeCount(animation.value);
        }
    }
    const auto add_replacements = [&](const auto& replacements) {
        for (const auto& replacement : replacements) {
            if (replacement.animation) {
                result += SoundKeyframeCount(replacement.animation->value);
            }
        }
    };
    add_replacements(model.projectiles);
    add_replacements(model.vehicles);
    return result;
}

std::uint32_t OuterVersion(const std::filesystem::path& path) {
    if (const auto raw = RawVersion(path)) {
        return *raw;
    }
    std::ifstream input(path, std::ios::binary);
    std::array<Byte, 7> header{};
    input.read(reinterpret_cast<char*>(header.data()), header.size());
    constexpr std::array<Byte, 7> kEncrypted{0xEF, 0xBB, 0xBF, 'Y',
                                             'S',  'G',  'P'};
    return input.gcount() == static_cast<std::streamsize>(header.size()) &&
                   header == kEncrypted
               ? 3
               : 0;
}

void WriteManifestRow(std::ostream& output, const std::filesystem::path& root,
                      const std::filesystem::path& source,
                      const absl::Status& status, bool routed_raw,
                      const LegacyModel* model, const ImportResult* imported) {
    const auto model_id =
        model == nullptr ? std::string{} : Hex(model->model_id);
    const auto info_hash =
        model == nullptr
            ? std::string{}
            : Hex(std::span(model->model_id.data(), std::size_t{16}));
    output << RelativePathId(root, source) << '\t' << FileSha256(source) << '\t'
           << OuterVersion(source) << '\t'
           << (model == nullptr ? 0 : model->version) << '\t' << info_hash
           << '\t' << model_id << '\t'
           << (routed_raw ? -1 : static_cast<int>(status.code())) << '\t'
           << (routed_raw ? "ROUTED_RAW" : StatusName(status.code())) << '\t';
    if (imported != nullptr) {
        const auto descriptor = java::EncodeDescriptor(*imported);
        if (!descriptor.ok()) {
            throw std::runtime_error(
                std::string(descriptor.status().message()));
        }
        output << Sha256(*descriptor) << '\t' << PayloadSetSha256(*imported)
               << '\t' << ImageTokens(*imported) << '\t'
               << ReadU32(*descriptor, 16);
    } else {
        output << "\t\t\t";
    }
    output << '\t' << (model == nullptr ? 0 : model->sound_field_count) << '\t'
           << (model == nullptr ? 0 : model->common.sounds.size()) << '\t'
           << (model == nullptr ? 0 : model->omitted_sound_count) << '\t'
           << (model == nullptr ? 0 : SoundKeyframeCount(*model)) << '\t'
           << (model != nullptr && model->player ? 1 : 0) << '\t'
           << (model == nullptr ? 0 : model->projectiles.size()) << '\t'
           << (model == nullptr ? 0 : model->vehicles.size()) << '\t'
           << (model == nullptr
                   ? 0
                   : std::ranges::count_if(
                         model->projectiles,
                         [](const auto& item) { return !item.texture; }))
           << '\t'
           << (model == nullptr
                   ? 0
                   : std::ranges::count_if(
                         model->vehicles,
                         [](const auto& item) { return !item.texture; }))
           << '\n';
}

TEST(LegacyHistoricalCorpusTest, ConvertsConfiguredReadOnlyCorpus) {
    const auto* configured = std::getenv("YSM_LEGACY_CORPUS");
    if (configured == nullptr || *configured == '\0') {
        GTEST_SKIP() << "YSM_LEGACY_CORPUS is not configured";
    }

    const std::filesystem::path root(configured);
    ASSERT_TRUE(std::filesystem::is_directory(root));
    std::optional<std::ofstream> manifest;
    if (const auto* path = std::getenv("YSM_LEGACY_NATIVE_MANIFEST");
        path != nullptr && *path != '\0') {
        manifest.emplace(std::filesystem::path(path),
                         std::ios::binary | std::ios::trunc);
        ASSERT_TRUE(*manifest);
        *manifest
            << "path_id_sha256\tsource_sha256\touter_version\tinner_version\t"
               "decoded_info_hash\tmodel_id\tstatus_code\tstatus_name\t"
               "descriptor_sha256\tpayload_set_sha256\timage_tokens\t"
               "reserved_zero\tsound_fields\temitted_sounds\t"
               "omitted_sounds\tsound_keyframes\tplayer_targets\t"
               "projectile_targets\tvehicle_targets\t"
               "textureless_projectiles\ttextureless_vehicles\n";
    }

    std::vector<std::filesystem::path> sources;
    for (const auto& entry : std::filesystem::recursive_directory_iterator(
             root,
             std::filesystem::directory_options::skip_permission_denied)) {
        if (entry.is_regular_file() && entry.path().extension() == ".ysm") {
            sources.push_back(entry.path());
        }
    }
    std::ranges::sort(sources, [](const auto& left, const auto& right) {
        return left.generic_u8string() < right.generic_u8string();
    });

    std::size_t examined = 0;
    std::size_t routed_raw = 0;
    std::size_t parsed = 0;
    std::size_t sound_fields = 0;
    std::size_t emitted_sounds = 0;
    std::size_t omitted_sounds = 0;
    std::map<std::uint32_t, std::size_t> versions;
    std::map<absl::StatusCode, std::size_t> rejected;
    std::string first_rejection;
    for (const auto& source : sources) {
        ++examined;
        const auto raw_version = RawVersion(source);
        if (raw_version == 1 || raw_version == 2) {
            ++routed_raw;
            if (manifest) {
                WriteManifestRow(*manifest, root, source, absl::OkStatus(),
                                 true, nullptr, nullptr);
            }
            continue;
        }

        const auto source_bytes = ReadFile(source);
        codec::ContainerReader reader(source_bytes);
        const auto initialized = reader.Initialize();
        absl::StatusOr<LegacyModel> decoded =
            initialized.ok() ? Decode(reader)
                             : absl::StatusOr<LegacyModel>(initialized);
        if (!decoded.ok()) {
            if (first_rejection.empty()) {
                first_rejection = source.generic_string() + ": " +
                                  std::string(decoded.status().message());
            }
            if (manifest) {
                WriteManifestRow(*manifest, root, source, decoded.status(),
                                 false, nullptr, nullptr);
            }
            ++rejected[decoded.status().code()];
            continue;
        }
        auto projected = conversion::Convert(*decoded, reader.source_size());
        if (!projected.ok()) {
            if (first_rejection.empty()) {
                first_rejection = source.generic_string() + ": " +
                                  std::string(projected.status().message());
            }
            if (manifest) {
                WriteManifestRow(*manifest, root, source, projected.status(),
                                 false, &*decoded, nullptr);
            }
            ++rejected[projected.status().code()];
            continue;
        }
        if (manifest) {
            WriteManifestRow(*manifest, root, source, absl::OkStatus(), false,
                             &*decoded, &*projected);
        }
        sound_fields += decoded->sound_field_count;
        omitted_sounds += decoded->omitted_sound_count;
        emitted_sounds += decoded->common.sounds.size();
        ++parsed;
        ++versions[decoded->version];
    }

    EXPECT_EQ(examined, 197);
    EXPECT_EQ(routed_raw, 12);
    EXPECT_EQ(parsed, 185);
    EXPECT_EQ(sound_fields, 778);
    EXPECT_EQ(emitted_sounds, 777);
    EXPECT_EQ(omitted_sounds, 1);
    EXPECT_EQ(versions, (std::map<std::uint32_t, std::size_t>{
                            {1, 35}, {4, 15}, {9, 57}, {15, 78}}));
    EXPECT_TRUE(rejected.empty()) << first_rejection;
    if (manifest) {
        manifest->flush();
        EXPECT_TRUE(*manifest);
    }
}

TEST(LegacyHistoricalCorpusTest, ImportsOneContainerConcurrently) {
    const auto* configured = std::getenv("YSM_LEGACY_CORPUS");
    if (configured == nullptr || *configured == '\0') {
        GTEST_SKIP() << "YSM_LEGACY_CORPUS is not configured";
    }

    std::optional<std::filesystem::path> sample;
    std::uintmax_t sample_size = (std::numeric_limits<std::uintmax_t>::max)();
    for (const auto& entry : std::filesystem::recursive_directory_iterator(
             configured,
             std::filesystem::directory_options::skip_permission_denied)) {
        if (!entry.is_regular_file() || entry.path().extension() != ".ysm" ||
            RawVersion(entry.path()).has_value() ||
            OuterVersion(entry.path()) != 3) {
            continue;
        }
        const auto size = entry.file_size();
        if (size < sample_size) {
            sample = entry.path();
            sample_size = size;
        }
    }
    ASSERT_TRUE(sample.has_value());

    std::vector<std::future<absl::StatusOr<ImportResult>>> imports;
    for (int index = 0; index < 4; ++index) {
        imports.emplace_back(std::async(std::launch::async, [source = *sample] {
            return ::ysm::legacy::v3::Import(ReadFile(source));
        }));
    }
    std::optional<std::array<Byte, 32>> model_id;
    std::optional<std::size_t> payload_count;
    for (auto& pending : imports) {
        auto imported = pending.get();
        ASSERT_TRUE(imported.ok()) << imported.status();
        if (!model_id) {
            model_id = imported->metadata.model_id;
            payload_count = imported->payloads.size();
        } else {
            EXPECT_EQ(imported->metadata.model_id, *model_id);
            EXPECT_EQ(imported->payloads.size(), *payload_count);
        }
    }
}
}  // namespace
}  // namespace ysm::legacy::v3::container
