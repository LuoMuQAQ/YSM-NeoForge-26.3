package com.elfmcys.ysm.mock.classpath;

import com.elfmcys.ysm.network.protocol.MessageDirection;
import com.elfmcys.ysm.proto.network.*;
import com.elfmcys.ysm.mock.evidence.EvidenceJson;
import us.hebi.quickbuf.ProtoMessage;

import java.util.LinkedHashMap;
import java.util.Map;

/** Byte-carrier observation that never interprets business outcomes. */
public final class MockWireIo {
    private MockWireIo() {
    }

    public static void send(SocketByteCarrier carrier, EndpointEvidence evidence,
                            String connectionId, ProtoMessage<?> message) throws Exception {
        var encoded = ProductionWire.encode(message, null);
        record(evidence, connectionId, actionId(message), "typed-frame-send",
                encoded.messageId(), encoded.direction().name(), encoded.messageType(),
                encoded.bytes(), encoded.attachmentBytes());
        carrier.send(encoded.bytes());
    }

    public static void send(SocketByteCarrier carrier, EndpointEvidence evidence,
                            String connectionId, byte[] bytes, MessageDirection direction)
            throws Exception {
        var decoded = ProductionWire.decode(bytes, direction);
        record(evidence, connectionId, actionId(decoded.message()), "typed-frame-send",
                decoded.messageId(), decoded.direction().name(), decoded.messageType(),
                bytes, decoded.attachment().length);
        carrier.send(bytes);
    }

    public static ProductionWire.AnyDecoded receive(
            SocketByteCarrier carrier, EndpointEvidence evidence, String connectionId,
            MessageDirection direction) throws Exception {
        var bytes = carrier.receive();
        var decoded = ProductionWire.decode(bytes, direction);
        record(evidence, connectionId, actionId(decoded.message()), "typed-frame-receive",
                decoded.messageId(), decoded.direction().name(), decoded.messageType(),
                bytes, decoded.attachment().length);
        return decoded;
    }

    public static String actionId(ProtoMessage<?> message) {
        if (message instanceof ServerHello) return "hello";
        if (message instanceof SessionResponse) return "session-response";
        if (message instanceof SessionFullFragment value) {
            return "publication-full-" + Long.toUnsignedString(value.transferId())
                    + "-" + value.sequence();
        }
        if (message instanceof SessionDeltaFragment value) {
            return "publication-delta-" + Long.toUnsignedString(value.transferId())
                    + "-" + value.sequence();
        }
        if (message instanceof MetadataPrefixRequest value) {
            return "metadata-request-" + Long.toUnsignedString(value.dataTransferId());
        }
        if (message instanceof MetadataPrefixFragment value) {
            return "metadata-fragment-" + Long.toUnsignedString(value.dataTransferId())
                    + "-" + value.sequence();
        }
        if (message instanceof ModelChunkRequest value) {
            return "chunk-request-" + Long.toUnsignedString(value.dataTransferId());
        }
        if (message instanceof ChunkFragment value) {
            return "chunk-fragment-" + Long.toUnsignedString(value.dataTransferId())
                    + "-" + value.name() + "-" + value.offset();
        }
        if (message instanceof ResourceTransferFailure value) {
            return "resource-failure-" + Long.toUnsignedString(value.dataTransferId());
        }
        if (message instanceof ResourceTransferCancel value) {
            return "resource-cancel-" + Long.toUnsignedString(value.dataTransferId());
        }
        if (message instanceof SelectModelRequest) return "select-request";
        if (message instanceof SelectModelResult) return "select-response";
        return "typed-" + message.getClass().getSimpleName();
    }

    private static void record(EndpointEvidence evidence, String connectionId,
                               String actionId, String eventKind, int messageId,
                               String direction, String messageType, byte[] bytes,
                               int attachmentBytes) throws Exception {
        var payload = new LinkedHashMap<String, String>();
        payload.put("attachmentBytes", Integer.toString(attachmentBytes));
        payload.put("codec", "ProtocolMessages+ProtocolBuffer+FrameCodec");
        payload.put("direction", direction);
        payload.put("frameBytes", Integer.toString(bytes.length));
        payload.put("messageId", Integer.toString(messageId));
        payload.put("messageType", messageType);
        payload.put("sha256", EvidenceJson.sha256(bytes));
        evidence.append(connectionId, actionId, "typed-frame-" + actionId,
                eventKind, Map.copyOf(payload));
    }
}
