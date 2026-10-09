project(ysm-benchmark CXX)

file(GLOB_RECURSE benchmark_code_files CONFIGURE_DEPENDS src/*.cpp src/*.cc)
add_executable(ysm-benchmark ${benchmark_code_files})
unset(benchmark_code_files)
set_property(TARGET ysm-benchmark PROPERTY CXX_STANDARD 23)
target_link_libraries(ysm-benchmark PRIVATE
        ysm-core
        ysm-gfx-internal
        benchmark::benchmark)

if (YSM_X64)
    if (CMAKE_CXX_COMPILER_FRONTEND_VARIANT STREQUAL "MSVC")
        set_source_files_properties(src/cglm_avx.cc PROPERTIES
                COMPILE_OPTIONS "/clang:-mavx2;/clang:-mfma")
    else ()
        set_source_files_properties(src/cglm_avx.cc PROPERTIES
                COMPILE_OPTIONS "-mavx2;-mfma")
    endif ()
endif ()
