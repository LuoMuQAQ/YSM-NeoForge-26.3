#include "string_cvt.h"

#include <cerrno>
#include <string>
#include <type_traits>

#if YSM_WINDOWS
#include <windows.h>
#else
#include <iconv.h>
#endif

namespace ysm {
#if YSM_WINDOWS
std::string U16ToU8(std::u16string_view str) {
    if (str.empty()) return {};

    int size = WideCharToMultiByte(
        CP_UTF8,
        0,
        reinterpret_cast<LPCWCH>(str.data()),
        static_cast<int>(str.size()),
        nullptr,
        0,
        nullptr,
        nullptr);

    std::string result(size, '\0');

    WideCharToMultiByte(
        CP_UTF8,
        0,
        reinterpret_cast<LPCWCH>(str.data()),
        static_cast<int>(str.size()),
        result.data(),
        size,
        nullptr,
        nullptr);

    return result;
}

std::string U8ToNative(std::string_view str) {
    if (str.empty()) {
        return {};
    }

    int wide_len = MultiByteToWideChar(
        CP_UTF8,
        0,
        str.data(),
        static_cast<int>(str.size()),
        nullptr,
        0);

    std::wstring wide(wide_len, L'\0');

    MultiByteToWideChar(
        CP_UTF8,
        0,
        str.data(),
        static_cast<int>(str.size()),
        wide.data(),
        wide_len);

    // UTF-16 -> ANSI(ACP)
    int ansi_len = WideCharToMultiByte(
        CP_ACP,
        0,
        wide.data(),
        static_cast<int>(wide.size()),
        nullptr,
        0,
        nullptr,
        nullptr);

    std::string ansi(ansi_len, '\0');

    WideCharToMultiByte(
        CP_ACP,
        0,
        wide.data(),
        static_cast<int>(wide.size()),
        ansi.data(),
        ansi_len,
        nullptr,
        nullptr);

    return ansi;
}

std::string U16ToNative(std::u16string_view str) {
    if (str.empty()) return {};

    int size = WideCharToMultiByte(
        CP_ACP,
        0,
        reinterpret_cast<LPCWCH>(str.data()),
        static_cast<int>(str.size()),
        nullptr,
        0,
        nullptr,
        nullptr);

    std::string result(size, '\0');

    WideCharToMultiByte(
        CP_ACP,
        0,
        reinterpret_cast<LPCWCH>(str.data()),
        static_cast<int>(str.size()),
        result.data(),
        size,
        nullptr,
        nullptr);

    return result;
}

std::string NativeToU8(std::string_view str) {
    if (str.empty()) {
        return {};
    }

    // multibyte -> UTF-16
    int wide_len = MultiByteToWideChar(
        CP_ACP,
        0,
        str.data(),
        static_cast<int>(str.size()),
        nullptr,
        0);

    if (wide_len <= 0) {
        return {};
    }

    std::wstring wide(wide_len, L'\0');

    MultiByteToWideChar(
        CP_ACP,
        0,
        str.data(),
        static_cast<int>(str.size()),
        wide.data(),
        wide_len);

    // UTF-16 -> UTF-8
    int utf8_len = WideCharToMultiByte(
        CP_UTF8,
        0,
        wide.data(),
        static_cast<int>(wide.size()),
        nullptr,
        0,
        nullptr,
        nullptr);

    if (utf8_len <= 0) {
        return {};
    }

    std::string utf8(utf8_len, '\0');

    WideCharToMultiByte(
        CP_UTF8,
        0,
        wide.data(),
        static_cast<int>(wide.size()),
        utf8.data(),
        utf8_len,
        nullptr,
        nullptr);

    return utf8;
}

static_assert(std::is_same_v<fs::path::value_type, wchar_t> &&
              sizeof(wchar_t) == 2);

std::string PathToU8(const fs::path& path) {
    auto str = path.c_str();
    return U16ToU8(reinterpret_cast<const char16_t*>(str));
}
#else
std::string U16ToU8(std::u16string_view input) {
    if (input.empty()) {
        return {};
    }

    iconv_t cd = iconv_open("UTF-8", "UTF-16LE");
    if (cd == reinterpret_cast<iconv_t>(-1)) {
        return {};
    }

    std::string output(input.size() * 4 + 16, '\0');
    char* in = const_cast<char*>(reinterpret_cast<const char*>(input.data()));
    size_t in_left = input.size();
    char* out = output.data();
    size_t out_left = output.size();

    while (in_left > 0) {
        if (iconv(cd, &in, &in_left, &out, &out_left) != static_cast<size_t>(-1)) {
            continue;
        }

        if (errno != E2BIG) {
            iconv_close(cd);
            return {};
        }

        const size_t used = output.size() - out_left;
        output.resize(output.size() * 2);
        out = output.data() + used;
        out_left = output.size() - used;
    }

    while (iconv(cd, nullptr, nullptr, &out, &out_left) == static_cast<size_t>(-1)) {
        if (errno != E2BIG) {
            iconv_close(cd);
            return {};
        }

        const size_t used = output.size() - out_left;
        output.resize(output.size() * 2);
        out = output.data() + used;
        out_left = output.size() - used;
    }

    output.resize(output.size() - out_left);
    iconv_close(cd);
    return output;
}

std::string U16ToNative(std::u16string_view str) {
    return U16ToU8(str);
}

std::string U8ToNative(std::string_view str) {
    return {str.data(), str.size()};
}

std::string NativeToU8(std::string_view str) {
    return {str.data(), str.size()};
}

std::string PathToU8(const fs::path& path) {
    static_assert(std::is_same_v<fs::path::value_type, char>);
    return path.native();
}
#endif

fs::path U8ToPath(std::string_view str) {
    return std::u8string_view{reinterpret_cast<const char8_t*>(str.data()),
                              str.size()};
}
}  // namespace ysm
