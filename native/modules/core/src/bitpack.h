#pragma once

#include <bit>
#include <cstddef>
#include <limits>
#include <stdexcept>
#include <tuple>
#include <type_traits>
#include <utility>

#include <absl/status/statusor.h>

#include "enum.h"
#include "err.h"
#include "macro.h"

#define YSM_BIT_FIELD(name, type, bits) (name, type, bits)

#define YSM_BIT_ENUM_VALIDATE(...) \
    YSM_RETURN_IF_ERROR(::ysm::bitpack::EnumValidate(__VA_ARGS__))

#define YSM_BIT_PACK_DECLARE_TAG_I(name, type, bits) \
    struct name##_tag {};

#define YSM_BIT_PACK_DECLARE_TAG(field) YSM_BIT_PACK_DECLARE_TAG_I field

#define YSM_BIT_PACK_DECLARE_DEFINITION_I(name, type, bits) \
    ::ysm::bitpack::FieldDefinition<name##_tag, type, bits>

#define YSM_BIT_PACK_DECLARE_DEFINITION(field) \
    YSM_BIT_PACK_DECLARE_DEFINITION_I field

#define YSM_BIT_PACK_UNPACK_FIELD_I(name, type, bits)               \
    ::ysm::bitpack::FieldCodec<                                     \
        ysm_bit_pack_unsigned_storage_type,                         \
        typename ysm_bit_pack_layout::template field<name##_tag>>:: \
        Read(ysm_bit_pack_packed_value)

#define YSM_BIT_PACK_UNPACK_FIELD(field) YSM_BIT_PACK_UNPACK_FIELD_I field

#define YSM_BIT_PACK_PARAMETER_I(name, type, bits) auto name

#define YSM_BIT_PACK_PARAMETER(field) YSM_BIT_PACK_PARAMETER_I field

#define YSM_BIT_PACK_VALIDATE_FIELD_I(name, type, bits)                   \
    if (!::ysm::bitpack::FieldCodec<                                      \
            ysm_bit_pack_unsigned_storage_type,                           \
            typename ysm_bit_pack_layout::template field<name##_tag>>::   \
            Fits(name)) {                                                 \
        return ::absl::InvalidArgumentError("Bit-pack field " #name       \
                                            " is out of range");          \
    }                                                                     \
    if (auto status =                                                     \
            ::ysm::bitpack::ValidatePackedField(static_cast<type>(name)); \
        !status.ok()) {                                                   \
        return status;                                                    \
    }

#define YSM_BIT_PACK_VALIDATE_FIELD(field) YSM_BIT_PACK_VALIDATE_FIELD_I field

#define YSM_BIT_PACK_ENCODE_FIELD_I(name, type, bits)                   \
    ysm_bit_pack_packed_value |=                                        \
        ::ysm::bitpack::FieldCodec<                                     \
            ysm_bit_pack_unsigned_storage_type,                         \
            typename ysm_bit_pack_layout::template field<name##_tag>>:: \
            Encode(static_cast<type>(name))                             \
        << ysm_bit_pack_layout::template field<name##_tag>::offset;

#define YSM_BIT_PACK_ENCODE_FIELD(field) YSM_BIT_PACK_ENCODE_FIELD_I field

#define YSM_BIT_PACK(name, storage, ...)                                       \
    class name {                                                               \
        using ysm_bit_pack_storage_type = storage;                             \
        using ysm_bit_pack_unsigned_storage_type =                             \
            std::make_unsigned_t<ysm_bit_pack_storage_type>;                   \
        YSM_PP_FOR_EACH(YSM_BIT_PACK_DECLARE_TAG, __VA_ARGS__)                 \
        using ysm_bit_pack_layout = ::ysm::bitpack::Layout<YSM_PP_MAP_COMMA(   \
            YSM_BIT_PACK_DECLARE_DEFINITION, __VA_ARGS__)>;                    \
        static_assert(ysm_bit_pack_layout::total_bits <=                       \
                          std::numeric_limits<                                 \
                              ysm_bit_pack_unsigned_storage_type>::digits,     \
                      "Bit-pack fields exceed storage width");                 \
        name() = delete;                                                       \
                                                                               \
       public:                                                                 \
        [[nodiscard]] static constexpr auto unpack(                            \
            ysm_bit_pack_storage_type ysm_bit_pack_storage_value) noexcept {   \
            const auto ysm_bit_pack_packed_value =                             \
                std::bit_cast<ysm_bit_pack_unsigned_storage_type>(             \
                    ysm_bit_pack_storage_value);                               \
            return std::tuple{                                                 \
                YSM_PP_MAP_COMMA(YSM_BIT_PACK_UNPACK_FIELD, __VA_ARGS__)};     \
        }                                                                      \
        [[nodiscard]] static ::absl::StatusOr<ysm_bit_pack_storage_type> pack( \
            YSM_PP_MAP_COMMA(YSM_BIT_PACK_PARAMETER, __VA_ARGS__)) {           \
            YSM_PP_FOR_EACH(YSM_BIT_PACK_VALIDATE_FIELD, __VA_ARGS__)          \
            ysm_bit_pack_unsigned_storage_type ysm_bit_pack_packed_value{};    \
            YSM_PP_FOR_EACH(YSM_BIT_PACK_ENCODE_FIELD, __VA_ARGS__)            \
            return std::bit_cast<ysm_bit_pack_storage_type>(                   \
                ysm_bit_pack_packed_value);                                    \
        }                                                                      \
    }

namespace ysm::bitpack {
template <typename T>
absl::Status ValidatePackedField(T value) {
    if constexpr (std::is_scoped_enum_v<T>) {
        return ::ysm::EnumValidate(value);
    }
    return absl::OkStatus();
}

template <typename T>
    requires std::is_scoped_enum_v<T>
class EnumValue {
   public:
    constexpr explicit EnumValue(T value) noexcept
        : value_(value), valid_(magic_enum::enum_contains(value)) {}

    [[nodiscard]] constexpr operator T() const { return Value(); }

    [[nodiscard]] constexpr T Value() const {
        if (!valid_) {
            throw std::invalid_argument(Status().ToString());
        }
        return value_;
    }

    [[nodiscard]] constexpr T ValueOr(T fallback) const noexcept {
        return valid_ ? value_ : fallback;
    }

    [[nodiscard]] constexpr bool Valid() const noexcept { return valid_; }

    [[nodiscard]] absl::Status Status() const {
        return valid_ ? absl::OkStatus()
                      : ::ysm::internal::InvalidEnumValueError(value_);
    }

   private:
    T value_;
    bool valid_;
};

template <typename T, bool = std::is_enum_v<T>>
struct ScalarType {
    using type = T;
};

template <typename T>
struct ScalarType<T, true> {
    using type = std::underlying_type_t<T>;
};

template <typename T>
using ScalarTypeT = typename ScalarType<T>::type;

template <typename Tag, typename T, std::size_t kBits>
struct FieldDefinition {
    using tag = Tag;
    using value_type = T;
    static constexpr std::size_t bits = kBits;
};

template <typename Tag, std::size_t kOffset, typename First, typename... Rest>
struct FindField;

template <typename Tag, std::size_t kOffset, typename First, typename... Rest>
    requires std::is_same_v<Tag, typename First::tag>
struct FindField<Tag, kOffset, First, Rest...> : First {
    static constexpr std::size_t offset = kOffset;
};

template <typename Tag, std::size_t kOffset, typename First, typename... Rest>
    requires(!std::is_same_v<Tag, typename First::tag> && sizeof...(Rest) > 0)
struct FindField<Tag, kOffset, First, Rest...>
    : FindField<Tag, kOffset + First::bits, Rest...> {};

template <typename... Definitions>
struct Layout {
    static constexpr std::size_t total_bits = (Definitions::bits + ...);

    template <typename Tag>
    using field = FindField<Tag, 0, Definitions...>;
};

template <typename Storage, typename Field>
struct FieldCodec {
    using value_type = typename Field::value_type;
    using scalar_type = ScalarTypeT<value_type>;
    using unsigned_scalar_type = std::make_unsigned_t<scalar_type>;

    static constexpr std::size_t scalar_bits =
        std::numeric_limits<unsigned_scalar_type>::digits;

    static consteval Storage LowMask() noexcept {
        if constexpr (Field::bits == std::numeric_limits<Storage>::digits) {
            return ~Storage{0};
        } else {
            return (Storage{1} << Field::bits) - Storage{1};
        }
    }

    static constexpr value_type Decode(Storage storage) noexcept {
        const auto raw = (storage >> Field::offset) & LowMask();
        auto value = static_cast<unsigned_scalar_type>(raw);

        if constexpr (std::is_signed_v<scalar_type> &&
                      Field::bits < scalar_bits) {
            constexpr auto sign_bit = unsigned_scalar_type{1}
                                      << (Field::bits - 1);
            if ((value & sign_bit) != 0) {
                constexpr auto value_mask =
                    (unsigned_scalar_type{1} << Field::bits) -
                    unsigned_scalar_type{1};
                value |= ~value_mask;
            }
        }
        return static_cast<value_type>(std::bit_cast<scalar_type>(value));
    }

    static constexpr auto Read(Storage storage) noexcept {
        const auto decoded = Decode(storage);
        if constexpr (std::is_scoped_enum_v<value_type>) {
            return EnumValue<value_type>(decoded);
        } else {
            return decoded;
        }
    }

    static constexpr Storage Encode(value_type value) noexcept {
        return static_cast<Storage>(std::bit_cast<unsigned_scalar_type>(
                   static_cast<scalar_type>(value))) &
               LowMask();
    }

    template <typename T>
        requires((std::is_integral_v<T> || std::is_enum_v<T>) &&
                 std::is_convertible_v<T, value_type>)
    static constexpr bool Fits(T value) noexcept {
        using input_scalar_type = ScalarTypeT<T>;
        const auto scalar = static_cast<input_scalar_type>(value);
        if constexpr (std::is_same_v<std::remove_cv_t<input_scalar_type>,
                                     bool>) {
            return true;
        } else {
            if (!std::in_range<scalar_type>(scalar)) {
                return false;
            }
            const auto converted =
                static_cast<value_type>(static_cast<scalar_type>(scalar));
            return Decode(Encode(converted) << Field::offset) == converted;
        }
    }
};

template <typename E, typename... Enums>
absl::Status EnumValidate(const EnumValue<E>& value,
                          const EnumValue<Enums>&... values) {
    if (!value.Valid()) {
        return value.Status();
    }
    if constexpr (sizeof...(Enums) > 0) {
        return EnumValidate(values...);
    }
    return absl::OkStatus();
}
}  // namespace ysm::bitpack
