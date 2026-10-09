#include <legacy/v3.h>

#include <new>
#include <utility>

#include <v3/codec/envelope.h>
#include <v3/container/decoder.h>
#include <v3/conversion/projector.h>
#include <err.h>

namespace ysm::legacy::v3 {
absl::StatusOr<ImportResult> Import(BufferViewR source) {
    try {
        codec::ContainerReader reader(source);
        YSM_RETURN_IF_ERROR(reader.Initialize());
        container::LegacyModel model;
        YSM_ASSIGN_OR_RETURN(model, container::Decode(reader));
        return conversion::Convert(std::move(model), reader.source_size());
    } catch (const std::bad_alloc&) {
        return absl::ResourceExhaustedError(
            "Legacy import exhausted native memory");
    } catch (const std::exception&) {
        return absl::InternalError("Legacy import failed internally");
    } catch (...) {
        return absl::InternalError("Legacy import failed internally");
    }
}
}  // namespace ysm::legacy::v3
