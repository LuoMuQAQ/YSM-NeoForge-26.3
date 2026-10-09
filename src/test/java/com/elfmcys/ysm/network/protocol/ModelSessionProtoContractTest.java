package com.elfmcys.ysm.network.protocol;

import com.elfmcys.ysm.model.session.client.ClientModelSession;
import com.elfmcys.ysm.model.session.client.state.RemotePublicationSnapshot;
import com.elfmcys.ysm.model.session.server.state.Selection;
import com.elfmcys.ysm.proto.network.ChunkFragment;
import com.elfmcys.ysm.proto.network.IconFragment;
import com.elfmcys.ysm.proto.network.MetadataPrefixFragment;
import com.elfmcys.ysm.proto.network.MetadataPrefixRequest;
import com.elfmcys.ysm.proto.network.ModelChunkRequest;
import com.elfmcys.ysm.proto.network.PackCoverFragment;
import com.elfmcys.ysm.proto.network.PresentationPageRequest;
import com.elfmcys.ysm.proto.network.PreviewFragment;
import com.elfmcys.ysm.proto.network.ResourceTransferCancel;
import com.elfmcys.ysm.proto.network.ResourceTransferFailure;
import com.elfmcys.ysm.proto.network.SessionDecision;
import com.elfmcys.ysm.proto.network.SessionDeltaFragment;
import com.elfmcys.ysm.proto.network.SessionFullFragment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSessionProtoContractTest {
    private static final Path PROTO = Path.of(
            "src/main/proto/network/model_session.proto");
    private static final Pattern FIELD = Pattern.compile(
            "(?:repeated\\s+)?[A-Za-z][A-Za-z0-9_.]*\\s+[a-z][a-z0-9_]*\\s*=\\s*\\d+;");

    @Test
    void messagesHaveTheExactFrozenFieldsAndTags() throws IOException {
        var source = Files.readString(PROTO);
        assertFields(source, "ServerHello",
                "string protocol_version = 1;", "bool sync_roaming = 2;");
        assertFields(source, "SessionResponse", "SessionDecision decision = 1;");
        assertFields(source, "SessionFullFragment",
                "uint64 transfer_id = 1;", "uint32 sequence = 2;",
                "bool final_fragment = 3;", "CatalogCollectionOperation catalog = 4;",
                "GrantCollectionOperation grants = 5;",
                "PackPresentationCollectionOperation pack_presentations = 6;",
                "DefaultAnimationCollectionOperation default_animations = 7;");
        assertFields(source, "SessionDeltaFragment",
                "uint64 transfer_id = 1;", "uint32 sequence = 2;",
                "bool final_fragment = 3;", "CatalogCollectionOperation catalog = 4;",
                "GrantCollectionOperation grants = 5;",
                "PackPresentationCollectionOperation pack_presentations = 6;",
                "DefaultAnimationCollectionOperation default_animations = 7;");
        assertFields(source, "CatalogCollectionOperation",
                "CollectionOperationType op_type = 1;",
                "repeated CatalogPublication entries = 2;",
                "repeated bytes model_ids = 3;");
        assertFields(source, "GrantCollectionOperation",
                "CollectionOperationType op_type = 1;", "repeated bytes model_ids = 2;");
        assertFields(source, "PackPresentationCollectionOperation",
                "CollectionOperationType op_type = 1;",
                "repeated PackPresentation entries = 2;",
                "repeated string hierarchy_paths = 3;");
        assertFields(source, "DefaultAnimationCollectionOperation",
                "CollectionOperationType op_type = 1;",
                "repeated DefaultAnimation entries = 2;",
                "repeated string names = 3;");
        assertFields(source, "MetadataPrefixRequest",
                "uint64 data_transfer_id = 1;", "repeated bytes container_ids = 2;");
        assertFields(source, "ModelChunkRequest",
                "uint64 data_transfer_id = 1;", "bytes model_id = 2;",
                "bytes container_id = 3;", "repeated ChunkMember chunks = 4;");
        assertFields(source, "ChunkMember", "string name = 1;",
                "bytes expected_hash = 2;", "uint64 stored_size = 3;",
                "uint64 decoded_size = 4;", "string encoding = 5;");
        assertFields(source, "PresentationPageRequest",
                "uint64 data_transfer_id = 1;", "repeated PreviewMember previews = 2;",
                "repeated IconMember icons = 3;",
                "repeated PackCoverMember pack_covers = 4;");
        assertFields(source, "PreviewMember", "uint32 slot = 1;",
                "bytes model_id = 2;", "bytes container_id = 3;");
        assertTrue(block(source, "message", "PreviewMember").contains("reserved 4 to 8;"));
        assertPresentationMember(source, "IconMember");
        assertFields(source, "PackCoverMember", "uint32 slot = 1;",
                "string hierarchy_path = 2;", "bytes expected_hash = 3;",
                "uint64 stored_size = 4;", "uint64 decoded_size = 5;",
                "string encoding = 6;");
        assertFields(source, "ResourceTransferCancel", "uint64 data_transfer_id = 1;");
        assertFields(source, "ResourceTransferFailure", "uint64 data_transfer_id = 1;",
                "ResourceFailureReason reason = 2;");
        assertFields(source, "SelectModelRequest", "bytes model_id = 1;",
                "bool intrinsic_default = 2;", "string texture_id = 3;");
        assertFields(source, "SelectModelResult", "SelectionStatus status = 1;");
        assertFields(source, "MetadataPrefixFragment", "uint64 data_transfer_id = 1;",
                "bytes container_id = 2;", "uint32 sequence = 3;",
                "bool final_fragment = 4;", "bytes payload = 5;");
        assertFields(source, "ChunkFragment", "uint64 data_transfer_id = 1;",
                "string name = 2;", "uint64 offset = 3;",
                "bool final_fragment = 4;");
        assertPresentationFragment(source, "PreviewFragment");
        assertPresentationFragment(source, "IconFragment");
        assertPresentationFragment(source, "PackCoverFragment");
    }

    @Test
    void oldCrossBusinessSchemaAndGeneratedTypesAreAbsent() throws IOException {
        var source = Files.readString(PROTO);
        for (var token : List.of("message DataFragment", "enum DataKind",
                "message AssetItem", "message AssetDelivery", "enum AssetKind",
                "message AssetBatch", "message ModelResourceRequest",
                "item_key", "session_id", "batch_id", "logical_metadata",
                "total_size", "fragment_count")) {
            assertFalse(source.contains(token), token);
        }
        var generated = Stream.of(
                        SessionFullFragment.class,
                        SessionDeltaFragment.class,
                        MetadataPrefixRequest.class,
                        ModelChunkRequest.class,
                        PresentationPageRequest.class,
                        ResourceTransferCancel.class,
                        ResourceTransferFailure.class,
                        MetadataPrefixFragment.class,
                        ChunkFragment.class,
                        PreviewFragment.class,
                        IconFragment.class,
                        PackCoverFragment.class)
                .map(Class::getSimpleName).collect(Collectors.toSet());
        assertTrue(generated.containsAll(Set.of("SessionFullFragment", "SessionDeltaFragment",
                "MetadataPrefixRequest", "ModelChunkRequest", "PresentationPageRequest",
                "ResourceTransferCancel", "ResourceTransferFailure",
                "MetadataPrefixFragment", "ChunkFragment", "PreviewFragment",
                "IconFragment", "PackCoverFragment")));
        assertFalse(generated.removeAll(Set.of("DataFragment", "DataKind", "AssetItem",
                "AssetDelivery", "AssetKind", "AssetBatchRequest", "AssetBatchCancel",
                "AssetBatchFailure", "SessionFull", "SessionDelta", "Selection")));
    }

    @Test
    void collectionPublicationHasNoSelectionOrGenericAssemblyState() throws IOException {
        for (var path : List.of(
                Path.of("src/main/java/com/elfmcys/ysm/model/session/client/state/RemotePublicationSnapshot.java"),
                Path.of("src/main/java/com/elfmcys/ysm/model/session/client/ClientModelSession.java"),
                Path.of("src/main/java/com/elfmcys/ysm/network/forge/SessionCollectionPublication.java"))) {
            assertFalse(Files.readString(path).contains("Selection"), path.toString());
        }
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/elfmcys/ysm/network/forge/ModelSessionSnapshots.java")));
        assertFalse(Files.exists(Path.of(
                "src/main/java/com/elfmcys/ysm/network/fragment/DataFragmentAssembler.java")));
    }

    @Test
    void protocolEnumsKeepTheirExactWireNumbers() {
        assertEquals(0, SessionDecision.SESSION_DECISION_UNSPECIFIED.getNumber());
        assertEquals(1, SessionDecision.SESSION_DECISION_ACCEPT.getNumber());
        assertEquals(2, SessionDecision.SESSION_DECISION_DECLINE.getNumber());
        assertEquals(0, com.elfmcys.ysm.proto.network.CollectionOperationType
                .COLLECTION_OPERATION_UNSPECIFIED.getNumber());
        assertEquals(1, com.elfmcys.ysm.proto.network.CollectionOperationType
                .COLLECTION_OPERATION_FULL.getNumber());
        assertEquals(2, com.elfmcys.ysm.proto.network.CollectionOperationType
                .COLLECTION_OPERATION_ADD.getNumber());
        assertEquals(3, com.elfmcys.ysm.proto.network.CollectionOperationType
                .COLLECTION_OPERATION_REMOVE.getNumber());
        assertEquals(4, com.elfmcys.ysm.proto.network.CollectionOperationType
                .COLLECTION_OPERATION_CLEAR.getNumber());
        assertEquals(6, com.elfmcys.ysm.proto.network.ResourceFailureReason
                .RESOURCE_FAILURE_UNAVAILABLE.getNumber());
        assertEquals(4, com.elfmcys.ysm.proto.network.SelectionStatus
                .SELECTION_STATUS_INVALID_REQUEST.getNumber());
        assertEquals(2, com.elfmcys.ysm.proto.network.PresentationDelivery
                .PRESENTATION_DELIVERY_UNAVAILABLE.getNumber());
    }

    private static void assertPresentationMember(String source, String message) {
        assertFields(source, message, "uint32 slot = 1;", "bytes model_id = 2;",
                "bytes container_id = 3;", "string name = 4;",
                "bytes expected_hash = 5;", "uint64 stored_size = 6;",
                "uint64 decoded_size = 7;", "string encoding = 8;");
    }

    private static void assertPresentationFragment(String source, String message) {
        assertFields(source, message, "uint64 data_transfer_id = 1;",
                "uint32 slot = 2;", "uint64 offset = 3;",
                "bool final_fragment = 4;", "PresentationDelivery delivery = 5;");
    }

    private static void assertFields(String source, String message, String... expected) {
        var actual = block(source, "message", message).lines().map(String::trim)
                .filter(line -> FIELD.matcher(line).matches()).toList();
        assertEquals(List.of(expected), actual, message);
    }

    private static String block(String source, String kind, String name) {
        var start = source.indexOf(kind + " " + name + " {");
        if (start < 0) {
            throw new AssertionError("Missing " + kind + " " + name);
        }
        var open = source.indexOf('{', start);
        var depth = 0;
        for (var index = open; index < source.length(); index++) {
            if (source.charAt(index) == '{') depth++;
            if (source.charAt(index) == '}' && --depth == 0) {
                return source.substring(open + 1, index);
            }
        }
        throw new AssertionError("Unclosed " + kind + " " + name);
    }
}
