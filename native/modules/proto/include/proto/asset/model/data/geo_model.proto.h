#pragma once
#include <optional>
#include <ylt/struct_pb.hpp>

namespace ysm::proto::asset::model::data {

struct CubeLegacy : public iguana::base_impl<CubeLegacy>  {
	CubeLegacy() = default;
	CubeLegacy(uint32_t a, std::vector<float> b, std::vector<uint32_t> c, std::vector<float> d, std::vector<uint32_t> e, std::vector<float> f) : faceCount(a), pos(std::move(b)), pos_indices(std::move(c)), uv(std::move(d)), uv_indices(std::move(e)), normal(std::move(f)) {}
	uint32_t faceCount = 0;
	std::vector<float>pos;
	std::vector<uint32_t>pos_indices;
	std::vector<float>uv;
	std::vector<uint32_t>uv_indices;
	std::vector<float>normal;
};
YLT_REFL(CubeLegacy, faceCount, pos, pos_indices, uv, uv_indices, normal);

struct Cubes : public iguana::base_impl<Cubes>  {
	Cubes() = default;
	Cubes(std::vector<CubeLegacy>a) : cubes_legacy(std::move(a)) {}
	std::vector<CubeLegacy>cubes_legacy;
};
YLT_REFL(Cubes, cubes_legacy);

struct Bone : public iguana::base_impl<Bone>  {
	Bone() = default;
	Bone(std::string a, std::optional<std::string> b, std::vector<float> c, std::vector<float> d, std::optional<bool> e, std::optional<uint32_t> f) : name(std::move(a)), parent(std::move(b)), pivot(std::move(c)), rotate(std::move(d)), debug(e), cubeCount(f) {}
	std::string name;
	std::optional<std::string> parent;
	std::vector<float>pivot;
	std::vector<float>rotate;
	std::optional<bool> debug;
	std::optional<uint32_t> cubeCount;
};
YLT_REFL(Bone, name, parent, pivot, rotate, debug, cubeCount);

struct GeoProperties : public iguana::base_impl<GeoProperties>  {
	GeoProperties() = default;
	GeoProperties(float a, float b) : texture_height(a), texture_width(b) {}
	float texture_height = 0;
	float texture_width = 0;
};
YLT_REFL(GeoProperties, texture_height, texture_width);

struct GeoModel : public iguana::base_impl<GeoModel>  {
	GeoModel() = default;
	GeoModel(std::vector<Bone>a, GeoProperties b, Cubes c) : bones(std::move(a)), properties(b), cubes(std::move(c)) {}
	std::vector<Bone>bones;
	GeoProperties properties;
	Cubes cubes;
};
YLT_REFL(GeoModel, bones, properties, cubes);

}
