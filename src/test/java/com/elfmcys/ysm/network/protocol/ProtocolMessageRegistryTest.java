package com.elfmcys.ysm.network.protocol;

import com.elfmcys.ysm.network.frame.FrameCodec;
import com.elfmcys.ysm.proto.network.ChunkFragment;
import com.elfmcys.ysm.proto.network.EmitMolangSync;
import com.elfmcys.ysm.proto.network.EntityAnimationActionRequest;
import com.elfmcys.ysm.proto.network.ExecuteMolangEvent;
import com.elfmcys.ysm.proto.network.IconFragment;
import com.elfmcys.ysm.proto.network.MetadataPrefixFragment;
import com.elfmcys.ysm.proto.network.MetadataPrefixRequest;
import com.elfmcys.ysm.proto.network.ModelChunkRequest;
import com.elfmcys.ysm.proto.network.MolangSyncEvent;
import com.elfmcys.ysm.proto.network.PackCoverFragment;
import com.elfmcys.ysm.proto.network.PlayerStateReport;
import com.elfmcys.ysm.proto.network.PlayerStateUpdate;
import com.elfmcys.ysm.proto.network.PresentationPageRequest;
import com.elfmcys.ysm.proto.network.PreviewFragment;
import com.elfmcys.ysm.proto.network.ProjectileModelState;
import com.elfmcys.ysm.proto.network.ResourceTransferCancel;
import com.elfmcys.ysm.proto.network.ResourceTransferFailure;
import com.elfmcys.ysm.proto.network.SelectModelRequest;
import com.elfmcys.ysm.proto.network.SelectModelResult;
import com.elfmcys.ysm.proto.network.ServerHello;
import com.elfmcys.ysm.proto.network.SessionDeltaFragment;
import com.elfmcys.ysm.proto.network.SessionFullFragment;
import com.elfmcys.ysm.proto.network.SessionResponse;
import com.elfmcys.ysm.proto.network.StarredModelsSnapshot;
import com.elfmcys.ysm.proto.network.SubmitRouletteExpressionRequest;
import com.elfmcys.ysm.proto.network.SwingHandRequest;
import com.elfmcys.ysm.proto.network.UpdateStarredModelRequest;
import com.elfmcys.ysm.proto.network.VehicleModelState;
import java.util.List;
import org.junit.jupiter.api.Test;
import us.hebi.quickbuf.ProtoMessage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolMessageRegistryTest {
    @Test
    void rejectsDuplicateIdsAndTypes() {
        var first = spec(1, SessionResponse.class,
                SessionResponse::parseFrom);
        var duplicateId = spec(1, ServerHello.class,
                ServerHello::parseFrom);
        var duplicateType = spec(2, SessionResponse.class,
                SessionResponse::parseFrom);

        assertThrows(IllegalArgumentException.class,
                () -> ProtocolMessageRegistry.builder().add(first).add(duplicateId));
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolMessageRegistry.builder().add(first).add(duplicateType));
    }

    @Test
    void modelSessionTableHasExactTypedIdentityAndAttachmentPolicy() {
        var expected = List.of(
                row(0, MessageDirection.SERVER_TO_CLIENT, ServerHello.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(1, MessageDirection.CLIENT_TO_SERVER, SessionResponse.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(2, MessageDirection.SERVER_TO_CLIENT,
                        SessionFullFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(3, MessageDirection.SERVER_TO_CLIENT,
                        SessionDeltaFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(4, MessageDirection.CLIENT_TO_SERVER,
                        MetadataPrefixRequest.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(5, MessageDirection.CLIENT_TO_SERVER,
                        ModelChunkRequest.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(6, MessageDirection.CLIENT_TO_SERVER,
                        PresentationPageRequest.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(7, MessageDirection.CLIENT_TO_SERVER,
                        ResourceTransferCancel.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(8, MessageDirection.SERVER_TO_CLIENT,
                        ResourceTransferFailure.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(9, MessageDirection.CLIENT_TO_SERVER,
                        SelectModelRequest.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(10, MessageDirection.SERVER_TO_CLIENT,
                        SelectModelResult.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(11, MessageDirection.SERVER_TO_CLIENT,
                        MetadataPrefixFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN),
                row(12, MessageDirection.SERVER_TO_CLIENT,
                        ChunkFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.REQUIRED),
                row(13, MessageDirection.SERVER_TO_CLIENT,
                        PreviewFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.CONDITIONAL),
                row(14, MessageDirection.SERVER_TO_CLIENT,
                        IconFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.CONDITIONAL),
                row(15, MessageDirection.SERVER_TO_CLIENT,
                        PackCoverFragment.class,
                        ProtocolMessageSpec.AttachmentPolicy.CONDITIONAL),
                row(16, MessageDirection.CLIENT_TO_SERVER,
                        PlayerStateReport.class,
                        ProtocolLimits.MAX_PLAYER_STATE_BYTES),
                row(17, MessageDirection.SERVER_TO_CLIENT,
                        PlayerStateUpdate.class,
                        ProtocolLimits.MAX_PLAYER_STATE_BYTES),
                row(18, MessageDirection.SERVER_TO_CLIENT,
                        StarredModelsSnapshot.class,
                        ProtocolLimits.MAX_MODEL_SET_BYTES),
                row(19, MessageDirection.CLIENT_TO_SERVER,
                        UpdateStarredModelRequest.class,
                        ProtocolLimits.MAX_STAR_UPDATE_BYTES),
                row(20, MessageDirection.CLIENT_TO_SERVER,
                        EntityAnimationActionRequest.class,
                        ProtocolLimits.MAX_ENTITY_ACTION_BYTES),
                row(21, MessageDirection.SERVER_TO_CLIENT,
                        ExecuteMolangEvent.class,
                        ProtocolLimits.MAX_MOLANG_EVENT_BYTES),
                row(22, MessageDirection.CLIENT_TO_SERVER,
                        SubmitRouletteExpressionRequest.class,
                        ProtocolLimits.MAX_ENTITY_ACTION_BYTES),
                row(23, MessageDirection.CLIENT_TO_SERVER,
                        EmitMolangSync.class,
                        ProtocolLimits.MAX_STAR_UPDATE_BYTES),
                row(24, MessageDirection.SERVER_TO_CLIENT,
                        MolangSyncEvent.class,
                        ProtocolLimits.MAX_MOLANG_SYNC_BYTES),
                row(25, MessageDirection.CLIENT_TO_SERVER,
                        SwingHandRequest.class,
                        ProtocolLimits.MAX_SWING_HAND_BYTES),
                row(26, MessageDirection.SERVER_TO_CLIENT,
                        ProjectileModelState.class,
                        ProtocolLimits.MAX_MINECRAFT_STATE_BYTES),
                row(27, MessageDirection.SERVER_TO_CLIENT,
                        VehicleModelState.class,
                        ProtocolLimits.MAX_MINECRAFT_STATE_BYTES));

        for (var row : expected) {
            var actual = ProtocolMessages.REGISTRY.find(row.id).orElseThrow();
            assertEquals(row.direction, actual.direction());
            assertEquals(row.type, actual.messageType());
            assertEquals(row.maxEncodedBytes, actual.maxEncodedBytes());
            assertEquals(row.attachmentPolicy, actual.attachmentPolicy());
        }
        assertEquals(28, ProtocolMessages.REGISTRY.messages().size());
    }

    @Test
    void attachmentPolicyRejectsOnlyStructurallyImpossibleShapes() {
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN.validate(1));
        assertThrows(IllegalArgumentException.class,
                () -> ProtocolMessageSpec.AttachmentPolicy.REQUIRED.validate(0));
        ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN.validate(0);
        ProtocolMessageSpec.AttachmentPolicy.REQUIRED.validate(1);
        ProtocolMessageSpec.AttachmentPolicy.CONDITIONAL.validate(0);
        ProtocolMessageSpec.AttachmentPolicy.CONDITIONAL.validate(1);
    }

    private static Expected row(int id, MessageDirection direction, Class<?> type,
                                ProtocolMessageSpec.AttachmentPolicy attachmentPolicy) {
        return new Expected(id, direction, type, FrameCodec.MAX_DECODED_PROTO_BYTES,
                attachmentPolicy);
    }

    private static Expected row(int id, MessageDirection direction, Class<?> type,
                                int maxEncodedBytes) {
        return new Expected(id, direction, type, maxEncodedBytes,
                ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN);
    }

    private static <T extends ProtoMessage<T>> ProtocolMessageSpec<T> spec(
            int id, Class<T> type, ProtocolMessageSpec.Parser<T> parser) {
        return new ProtocolMessageSpec<>(id, MessageDirection.CLIENT_TO_SERVER,
                type, 1024, ProtocolMessageSpec.AttachmentPolicy.FORBIDDEN,
                parser, (payload, context) -> payload.close());
    }

    private record Expected(int id, MessageDirection direction, Class<?> type,
                            int maxEncodedBytes,
                            ProtocolMessageSpec.AttachmentPolicy attachmentPolicy) {
    }
}
