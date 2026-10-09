import os

from conan import ConanFile


class CpuFeaturesConan(ConanFile):
    python_requires = "ysm_dependency_base/0.1@ysm/stable"
    python_requires_extend = "ysm_dependency_base.YsmDependencyBaseConan"

    settings = "os", "arch", "compiler", "build_type"
    exports_sources = "patches/*", "src/*"
    name = "cpu_features"
    version = "0.11.0"
    license = ""
    package_type = "static-library"

    options = {"fPIC": [True, False]}
    default_options = {"fPIC": True}

    def package_info(self):
        super().package_info()

        ndk_compat_libraries = [
            os.path.join(self.package_folder, "lib", "ndk_compat.lib"),
            os.path.join(self.package_folder, "lib", "libndk_compat.a"),
        ]
        if not any(os.path.exists(path) for path in ndk_compat_libraries):
            return

        includedirs = list(self.cpp_info.includedirs or ["include"])
        if "include/ndk_compat" not in includedirs:
            includedirs.append("include/ndk_compat")
        self.cpp_info.includedirs = includedirs

        libs = [lib for lib in self.cpp_info.libs if lib not in ("ndk_compat", "cpu_features")]
        self.cpp_info.libs = ["ndk_compat", "cpu_features", *libs]
