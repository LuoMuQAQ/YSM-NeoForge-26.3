# suppress warnings
set(_ysm_disabled_warnings
    deprecated-declarations
    deprecated-this-capture
    unused-function
    unused-parameter
    unused-variable
)
foreach(_warning IN LISTS _ysm_disabled_warnings)
    add_c_compile_options(LLVM "-Wno-${_warning}")
endforeach()
unset(_ysm_disabled_warnings)


# target specific settings
include("${CMAKE_CURRENT_LIST_DIR}/target-dispatch.cmake")
if (NOT YSM_WINDOWS)
    set(CMAKE_C_VISIBILITY_PRESET hidden)
    set(CMAKE_CXX_VISIBILITY_PRESET hidden)
    set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

    set(CMAKE_SKIP_RPATH ON)
    set(CMAKE_SKIP_BUILD_RPATH ON)
    set(CMAKE_BUILD_WITH_INSTALL_RPATH OFF)
endif ()


# DI
get_property(_ysm_dependencies_injected GLOBAL PROPERTY YSM_DEPENDENCIES_INJECTED)
if((DEFINED YSM_REQUIRED_PACKAGES OR DEFINED YSM_GLOBAL_LINK_TARGETS)
    AND NOT _ysm_dependencies_injected)
    set_property(GLOBAL PROPERTY YSM_DEPENDENCIES_INJECTED TRUE)

    foreach(_ysm_package IN LISTS YSM_REQUIRED_PACKAGES)
        find_package("${_ysm_package}" CONFIG REQUIRED)
    endforeach()

    foreach(_ysm_target IN LISTS YSM_GLOBAL_LINK_TARGETS)
        if(NOT TARGET "${_ysm_target}")
            message(FATAL_ERROR "Conan dependency target is missing: ${_ysm_target}")
        endif()
    endforeach()

    if(YSM_GLOBAL_LINK_TARGETS)
        link_libraries(${YSM_GLOBAL_LINK_TARGETS})
    endif()

endif()
unset(_ysm_dependencies_injected)
