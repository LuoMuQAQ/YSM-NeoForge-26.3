import os
from conan import ConanFile
from conan.tools.cmake import CMake, CMakeDeps, CMakeToolchain, cmake_layout
from conan.tools.files import apply_conandata_patches, collect_libs, copy
from conan.tools.scm import Git


class YsmDependencyBaseConan(ConanFile):
    name = "ysm_dependency_base"
    version = "0.1"
    package_type = "python-require"
    settings = "os", "arch", "compiler", "build_type"
    exports_sources = "patches/*", "src/*"

    @property
    def _recipe_data(self):
        return self.conan_data or {}

    @property
    def _source_data(self):
        return self._recipe_data.get("sources", {}).get(str(self.version), {})

    @property
    def _build_data(self):
        return self._recipe_data.get("build", {})

    @property
    def _package_data(self):
        return self._recipe_data.get("package", {})

    @property
    def _is_header_only(self):
        return self.package_type == "header-library"

    @property
    def _target_os(self):
        target_os = self.conf.get("user.target:os", default=None)
        if target_os is None or not str(target_os).strip():
            target_os = self.settings.os
        return str(target_os).strip().lower()

    def requirements(self):
        for item in self._recipe_data.get("requirements", []):
            if isinstance(item, str):
                self.requires(item)
            else:
                skip_os = {str(value).strip().lower() for value in item.get("skip_os", [])}
                if self._target_os in skip_os:
                    continue
                self.requires(item["ref"])

    def package_id(self):
        if self._is_header_only:
            self.info.clear()
        else:
            self.info.conf.define("user.ysm:rev", str(self.conf.get("user.ysm:rev")))
            self.info.conf.define("user.toolchain:rev", str(self.conf.get("user.toolchain:rev", default="undefined")))

    def layout(self):
        self.folders.source = "src"
        if not self._is_header_only:
            cmake_layout(self, src_folder="src")

    def source(self):
        source_data = self._source_data
        if source_data.get("local"):
            return

        self._clone_git(source_data, target=".")

        for item in source_data.get("extra_git", []):
            self._clone_git(item, target=item["target"], folder=self._source_root())

        apply_conandata_patches(self)

    def _clone_git(self, data, target, folder=None):
        git = Git(self, folder=folder) if folder else Git(self)
        if data.get("tag"):
            args = ["-b", data["tag"]]
        else:
            args = ["--revision=" + data["commit"]]
        git.clone(
            url=data["url"],
            target=target,
            args=["--depth", "1"] + args
        )

    def generate(self):
        if self._is_header_only:
            return

        deps = CMakeDeps(self)
        deps.generate()

        required_packages = []
        for dependency in self.dependencies.direct_host.values():
            package_name = deps.get_cmake_package_name(dependency)
            if package_name not in required_packages:
                required_packages.append(package_name)

        tc = CMakeToolchain(self)
        install_directories = {
            "CMAKE_INSTALL_BINDIR": "bin",
            "CMAKE_INSTALL_SBINDIR": "bin",
            "CMAKE_INSTALL_LIBEXECDIR": "bin",
            "CMAKE_INSTALL_LIBDIR": "lib",
            "CMAKE_INSTALL_INCLUDEDIR": "include",
            "CMAKE_INSTALL_OLDINCLUDEDIR": "include",
            "CMAKE_INSTALL_DATAROOTDIR": "share",
        }
        for key, value in install_directories.items():
            tc.variables[key] = value
        for key, value in self._build_data.get("options", {}).items():
            tc.variables[key] = self._cmake_value(value)
        for key, value in self._build_data.get("cache_variables", {}).items():
            resolved_value = self._cmake_value(value)
            if resolved_value is not None:
                tc.cache_variables[key] = resolved_value
        tc.variables["YSM_REQUIRED_PACKAGES"] = ";".join(required_packages)
        tc.variables["YSM_GLOBAL_LINK_TARGETS"] = ";".join(
            self._build_data.get("global_link_targets", [])
        )
        tc.generate()

    def _cmake_value(self, value):
        if isinstance(value, dict):
            platform_values = {str(key).strip().lower(): item for key, item in value.items()}
            value = platform_values.get(self._target_os, platform_values.get("default"))
        if isinstance(value, str):
            return value.replace("{source_folder}", self.source_folder.replace("\\", "/"))
        return value

    def _source_cmake_dir(self):
        subdir = self._build_data.get("subdir", "")
        return os.path.join(self._source_root(), subdir) if subdir else self._source_root()

    def _source_root(self):
        return self.source_folder

    def build(self):
        if self._is_header_only:
            return
        cmake = CMake(self)
        cmake.configure(build_script_folder=self._source_cmake_dir())
        cmake.build()

    def package(self):
        source_root = self._source_root()

        if not self._is_header_only:
            cmake = CMake(self)
            if self._build_data.get("install", True):
                cmake.install()
                if self._build_data.get("copy_built_libraries"):
                    self._copy_built_libraries()
            else:
                self._copy_built_libraries()

        for header_set in self._package_data.get("manual_headers", []):
            copy(
                self,
                header_set["pattern"],
                src=os.path.join(source_root, header_set["src"]),
                dst=os.path.join(self.package_folder, header_set["dst"]),
            )
        for header_set in self._package_data.get("manual_build_headers", []):
            copy(
                self,
                header_set["pattern"],
                src=os.path.join(self.build_folder, header_set["src"]),
                dst=os.path.join(self.package_folder, header_set["dst"]),
            )

    def _copy_built_libraries(self):
        copy(self, "*.lib", src=self.build_folder, dst=os.path.join(self.package_folder, "lib"), keep_path=False)
        copy(self, "*.a", src=self.build_folder, dst=os.path.join(self.package_folder, "lib"), keep_path=False)

    def package_info(self):
        package_data = self._package_data
        self.cpp_info.set_property("cmake_file_name", package_data.get("cmake_file_name", self.name))

        if package_data.get("includedirs"):
            self.cpp_info.includedirs = package_data["includedirs"]

        platform_includedirs = package_data.get("platform_includedirs", {})
        if platform_includedirs:
            self.cpp_info.includedirs = platform_includedirs.get(str(self.settings.os), ["include"])

        components = package_data.get("components")
        if components:
            self.cpp_info.set_property("cmake_target_name", package_data.get("cmake_file_name", self.name))
            for component_name, component_data in components.items():
                component = self.cpp_info.components[component_name]
                component.set_property("cmake_target_name", component_data["target"])
                component.libs = component_data.get("libs", [])
                component.includedirs = component_data.get("includedirs", ["include"])
            return

        self.cpp_info.set_property("cmake_target_name", package_data["cmake_target_name"])
        if self._is_header_only:
            self.cpp_info.includedirs = self.cpp_info.includedirs or ["include"]
            self.cpp_info.libdirs = []
            self.cpp_info.bindirs = []
            self.cpp_info.frameworkdirs = []
        else:
            libs = package_data.get("libs")
            self.cpp_info.libs = libs if libs is not None else collect_libs(self)
