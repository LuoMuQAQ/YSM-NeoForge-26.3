project(ysm-java CXX)

file(GLOB_RECURSE java_code_files CONFIGURE_DEPENDS src/*.cpp src/*.cc)
add_library(ysm-java STATIC ${java_code_files})
unset(java_code_files)
set_property(TARGET ysm-java PROPERTY CXX_STANDARD 23)
target_include_directories(ysm-java PUBLIC include PRIVATE src)

if (YSM_ENABLE_DEBUG_LOG)
    target_compile_definitions(ysm-java PRIVATE
            YSM_ENABLE_DEBUG_LOG=1
            ABSL_MAX_VLOG_VERBOSITY=2)
else ()
    target_compile_definitions(ysm-java PRIVATE
            YSM_ENABLE_DEBUG_LOG=0
            ABSL_MAX_VLOG_VERBOSITY=0)
endif ()

target_link_libraries(ysm-java PUBLIC ysm-core jni)
target_link_libraries(ysm-java PRIVATE absl::synchronization)
