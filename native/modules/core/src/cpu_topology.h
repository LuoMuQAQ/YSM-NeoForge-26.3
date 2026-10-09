#pragma once

#include <cstddef>

#include <functional>
#include <optional>

namespace ysm {
struct CpuTopology {
    size_t logical_core_count = 0;
    size_t physical_core_count = 0;
    size_t performance_core_count = 0;
    bool hybrid = false;
    bool has_smt = false;
    std::function<void()> bind_current_thread_to_performance_cores;
};

std::optional<CpuTopology> DetectCpuTopology();
}  // namespace ysm
