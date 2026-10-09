package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.buffer.ArrayBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.model.catalog.source.CatalogRootKind;
import com.elfmcys.ysm.model.domain.Hash256;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPackDescriptor;
import com.elfmcys.ysm.model.resource.client.remote.RemotePresentationFetcher;
import com.elfmcys.ysm.model.storage.PreviewStore;
import com.elfmcys.ysm.proto.network.IconFragment;
import com.elfmcys.ysm.proto.network.PackCoverFragment;
import com.elfmcys.ysm.proto.network.PresentationDelivery;
import com.elfmcys.ysm.proto.network.PresentationPageRequest;
import com.elfmcys.ysm.proto.network.PreviewFragment;
import com.elfmcys.ysm.proto.network.PreviewMember;
import com.elfmcys.ysm.proto.network.ResourceTransferCancel;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;
import us.hebi.quickbuf.ProtoMessage;

import static org.junit.jupiter.api.Assertions.*;

class ClientPresentationActionContractTest {
    @Test
    void fittingPageUsesOneIdAndUnavailableIsACompletedSlotOutcome() {
        var sender = new RecordingSender();
        var transfers = new ClientAssetTransfer(sender);
        var preview = new RemotePresentationFetcher.Preview(0, identity());
        var icon = new RemotePresentationFetcher.Icon(0, identity(), chunk("icon", 1));
        var cover = new RemotePresentationFetcher.PackCover(1, pack("pack", 1));
        var outcomes = new ArrayList<RemotePresentationFetcher.Outcome>();
        var result = transfers.fetch(List.of(cover, icon, preview),
                (member, outcome) -> outcomes.add(outcome));

        var request = assertInstanceOf(PresentationPageRequest.class,
                sender.requests.get(0));
        assertEquals(1, request.dataTransferId());
        assertEquals(1, request.previews().size());
        assertEquals(0, request.previews().get(0).getSerializedSize()
                - PreviewMember.newBuilder()
                .setSlot(0)
                .setModelId(ByteBuffer.wrap(identity().modelId().bytes()))
                .setContainerId(ByteBuffer.wrap(identity().containerId().bytes()))
                .build().getSerializedSize());
        assertEquals(1, request.icons().size());
        assertEquals(1, request.packCovers().size());

        acceptPreview(transfers, 1, 0, 0, true,
                PresentationDelivery.PRESENTATION_DELIVERY_DATA, 1, 2);
        acceptIcon(transfers, 1, 0, 0, true,
                PresentationDelivery.PRESENTATION_DELIVERY_UNAVAILABLE);
        acceptCover(transfers, 1, 1, 0, true,
                PresentationDelivery.PRESENTATION_DELIVERY_DATA, 3);

        result.join();
        assertEquals(3, outcomes.size());
        assertEquals(1, outcomes.stream()
                .filter(RemotePresentationFetcher.Unavailable.class::isInstance).count());
    }

    @Test
    void wrongTypedSlotFailsTheWholeChild() {
        var sender = new RecordingSender();
        var transfers = new ClientAssetTransfer(sender);
        var result = transfers.fetch(List.of(new RemotePresentationFetcher.Preview(
                4, identity())), (member, outcome) -> fail());

        acceptIcon(transfers, 1, 4, 0, true,
                PresentationDelivery.PRESENTATION_DELIVERY_DATA, 1);

        assertThrows(CompletionException.class, result::join);
        assertEquals(List.of(1L), sender.cancels);
    }

    @Test
    void descriptorlessPreviewUsesTerminalLengthAndRejectsOverlapAndOversize() {
        var sender = new RecordingSender();
        var transfers = new ClientAssetTransfer(sender);
        var outcomes = new ArrayList<RemotePresentationFetcher.Outcome>();
        var result = transfers.fetch(List.of(
                new RemotePresentationFetcher.Preview(0, identity())),
                (member, outcome) -> outcomes.add(outcome));

        acceptPreview(transfers, 1, 0, 1, true,
                com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_DATA, 2);
        acceptPreview(transfers, 1, 0, 0, false,
                com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_DATA, 1);
        result.join();
        assertEquals(1, outcomes.size());

        var overlapSender = new RecordingSender();
        var overlap = new ClientAssetTransfer(overlapSender);
        var rejected = overlap.fetch(List.of(
                new RemotePresentationFetcher.Preview(0, identity())),
                (member, outcome) -> fail());
        acceptPreview(overlap, 1, 0, 0, false,
                com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_DATA, 1, 2);
        acceptPreview(overlap, 1, 0, 1, true,
                com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_DATA, 3);
        assertThrows(CompletionException.class, rejected::join);
        assertEquals(List.of(1L), overlapSender.cancels);

        var oversizeSender = new RecordingSender();
        var oversize = new ClientAssetTransfer(oversizeSender);
        var tooLarge = oversize.fetch(List.of(
                new RemotePresentationFetcher.Preview(0, identity())),
                (member, outcome) -> fail());
        acceptPreview(oversize, 1, 0,
                PreviewStore.MAX_STORED_BYTES, true,
                com.elfmcys.ysm.proto.network.PresentationDelivery
                        .PRESENTATION_DELIVERY_DATA, 1);
        assertThrows(CompletionException.class, tooLarge::join);
    }

    @Test
    void duplicateTypedSlotIsRejectedBeforeIdAllocation() {
        var sender = new RecordingSender();
        var transfers = new ClientAssetTransfer(sender);
        var result = transfers.fetch(List.of(
                new RemotePresentationFetcher.Icon(2, identity(), chunk("a", 1)),
                new RemotePresentationFetcher.Icon(2, identity(), chunk("b", 1))),
                (member, outcome) -> fail());

        assertThrows(CompletionException.class, result::join);
        assertTrue(sender.requests.isEmpty());
    }

    @Test
    void oversizedPageStartsChildrenSequentiallyAndCancelStopsTheCurrentChild() {
        var sender = new RecordingSender();
        var transfers = new ClientAssetTransfer(sender);
        var random = new Random(31);
        var members = new ArrayList<RemotePresentationFetcher.Member>();
        for (var slot = 0; slot < ClientAssetTransfer.MAX_ACTION_MEMBERS; slot++) {
            members.add(new RemotePresentationFetcher.PackCover(
                    slot, pack(randomText(random, 240) + slot, 1)));
        }
        var result = transfers.fetch(members, (member, outcome) -> { });

        assertEquals(1, sender.requests.size());
        var first = (PresentationPageRequest) sender.requests.get(0);
        assertTrue(first.packCovers().size() < members.size());
        for (var member : first.packCovers()) {
            acceptCover(transfers, first.dataTransferId(), member.slot(), 0, true,
                    PresentationDelivery.PRESENTATION_DELIVERY_DATA, 1);
        }
        assertEquals(2, sender.requests.size());
        var second = (PresentationPageRequest) sender.requests.get(1);

        assertTrue(result.cancel(false));
        assertEquals(List.of(second.dataTransferId()), sender.cancels);
        acceptCover(transfers, second.dataTransferId(),
                second.packCovers().get(0).slot(), 0, true,
                PresentationDelivery.PRESENTATION_DELIVERY_DATA, 1);
        assertEquals(2, sender.requests.size());
    }

    private static void acceptPreview(ClientAssetTransfer transfers, long id, int slot,
                                      long offset, boolean terminal,
                                      PresentationDelivery delivery,
                                      int... values) {
        try (var bytes = buffer(values)) {
            transfers.acceptPreview(PreviewFragment.newBuilder()
                    .setDataTransferId(id).setSlot(slot).setOffset(offset)
                    .setFinalFragment(terminal).setDelivery(delivery).build(), bytes);
        }
    }

    private static void acceptIcon(ClientAssetTransfer transfers, long id, int slot,
                                   long offset, boolean terminal,
                                   PresentationDelivery delivery,
                                   int... values) {
        try (var bytes = buffer(values)) {
            transfers.acceptIcon(IconFragment.newBuilder()
                    .setDataTransferId(id).setSlot(slot).setOffset(offset)
                    .setFinalFragment(terminal).setDelivery(delivery).build(), bytes);
        }
    }

    private static void acceptCover(ClientAssetTransfer transfers, long id, int slot,
                                    long offset, boolean terminal,
                                    PresentationDelivery delivery,
                                    int... values) {
        try (var bytes = buffer(values)) {
            transfers.acceptPackCover(PackCoverFragment.newBuilder()
                    .setDataTransferId(id).setSlot(slot).setOffset(offset)
                    .setFinalFragment(terminal).setDelivery(delivery).build(), bytes);
        }
    }

    private static ArrayBuffer buffer(int... values) {
        var bytes = new byte[values.length];
        for (var index = 0; index < values.length; index++) bytes[index] = (byte) values[index];
        return ArrayBuffer.borrow(bytes);
    }

    private static AssetContainerView.ChunkInfo chunk(String name, int size) {
        return new AssetContainerView.ChunkInfo(
                name, "png", 0, size, size, 0, 0, 0, hash(3).bytes());
    }

    private static ModelPackDescriptor pack(String hierarchy, int size) {
        return new ModelPackDescriptor(CatalogRootKind.CUSTOM, hierarchy,
                "", "", Map.of(), hash(hierarchy.hashCode()), "png", size);
    }

    private static ModelFileIdentity identity() {
        return new ModelFileIdentity(hash(1), hash(2));
    }

    private static Hash256 hash(int value) {
        var bytes = new byte[Hash256.SIZE];
        Arrays.fill(bytes, (byte) value);
        return new Hash256(bytes);
    }

    private static String randomText(Random random, int size) {
        var alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        var result = new StringBuilder(size);
        for (var index = 0; index < size; index++) {
            result.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return result.toString();
    }

    private static final class RecordingSender implements ClientAssetTransfer.Sender {
        private final List<ProtoMessage<?>> requests = new ArrayList<>();
        private final List<Long> cancels = new ArrayList<>();

        @Override
        public void request(ProtoMessage<?> request) {
            requests.add(request);
        }

        @Override
        public void cancel(ResourceTransferCancel cancel) {
            cancels.add(cancel.dataTransferId());
        }
    }
}
