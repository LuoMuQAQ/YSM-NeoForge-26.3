package com.elfmcys.ysm.model.resource.client.remote;

import com.elfmcys.ysm.buffer.UniBuffer;
import com.elfmcys.ysm.format.container.AssetContainerView;
import com.elfmcys.ysm.model.domain.ModelFileIdentity;
import com.elfmcys.ysm.model.domain.ModelPackDescriptor;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Typed page-transfer seam; one invocation owns one page action and its publication terminal. */
@FunctionalInterface
public interface RemotePresentationFetcher {
    CompletableFuture<Void> fetch(List<Member> members, PresentationReceiver receiver);

    sealed interface Member permits Preview, Icon, PackCover {
        int slot();
    }

    record Preview(int slot, ModelFileIdentity identity) implements Member {
    }

    record Icon(int slot, ModelFileIdentity identity,
                AssetContainerView.ChunkInfo chunk) implements Member {
    }

    record PackCover(int slot, ModelPackDescriptor pack) implements Member {
    }

    sealed interface Outcome permits Data, Unavailable {
    }

    record Data(UniBuffer bytes) implements Outcome {
    }

    enum Unavailable implements Outcome {
        INSTANCE
    }

    @FunctionalInterface
    interface PresentationReceiver {
        void accept(Member member, Outcome outcome) throws IOException;
    }
}
