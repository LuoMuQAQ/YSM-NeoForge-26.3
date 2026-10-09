project(ysm-legacy CXX)

FILE(GLOB_RECURSE legacy_code_files CONFIGURE_DEPENDS src/*.cpp src/*.cc)
add_library(ysm-legacy STATIC ${legacy_code_files})
unset(legacy_code_files)
set_property(TARGET ysm-legacy PROPERTY CXX_STANDARD 23)
target_include_directories(ysm-legacy PUBLIC include)
target_include_directories(ysm-legacy PRIVATE src)
if (YSM_ENABLE_DEBUG_LOG)
    target_compile_definitions(ysm-legacy PRIVATE
            YSM_ENABLE_DEBUG_LOG=1
            ABSL_MAX_VLOG_VERBOSITY=2)
else ()
    target_compile_definitions(ysm-legacy PRIVATE
            YSM_ENABLE_DEBUG_LOG=0
            ABSL_MAX_VLOG_VERBOSITY=0)
endif ()

target_link_libraries(ysm-legacy
        PUBLIC
        ysm-core
        ysm-codec
        PRIVATE
        ysm-proto
        libzstd_static
        cryptopp)
