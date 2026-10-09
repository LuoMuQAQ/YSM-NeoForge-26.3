project(ysm-proto CXX)

file(GLOB_RECURSE proto_header_files CONFIGURE_DEPENDS include/proto/*.h)
add_library(ysm-proto INTERFACE ${proto_header_files})
unset(proto_header_files)

target_include_directories(ysm-proto INTERFACE include)
target_link_libraries(ysm-proto INTERFACE yalantinglibs)
