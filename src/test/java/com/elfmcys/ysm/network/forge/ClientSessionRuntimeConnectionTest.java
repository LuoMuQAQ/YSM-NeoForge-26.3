package com.elfmcys.ysm.network.forge;

import com.elfmcys.ysm.model.catalog.snapshot.CatalogSnapshot;
import com.elfmcys.ysm.model.session.client.ClientModelSession;
import com.elfmcys.ysm.model.session.client.state.RemotePublicationSnapshot;
import com.elfmcys.ysm.network.protocol.ProtocolVersion;
import com.elfmcys.ysm.network.session.SessionMode;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientSessionRuntimeConnectionTest {
    @AfterEach
    void closeRuntimeOwner() throws Exception {
        var field = currentField();
        var owner = field.get(null);
        field.set(null, null);
        if (owner instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }

    @Test
    void repeatedPendingHelloReusesTheConnectionOwnerAndActiveHelloIsIgnored()
            throws Exception {
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        var session = session();
        var owner = connectionState(connection, session, ignored -> { });
        setCollectionPublication(owner,
                new SessionCollectionPublication.Receiver(Map.of()));
        currentField().set(null, owner);

        assertTrue(ClientSessionRuntime.onServerHello(
                connection, ProtocolVersion.TRANSPORT_VERSION, true)
                .orElseThrow().accepted());
        assertTrue(ClientSessionRuntime.businessSession().isEmpty());
        ClientSessionRuntime.commitAcceptedSession(connection, true);
        var businessSession = ClientSessionRuntime.businessSession().orElseThrow();
        assertTrue(ClientSessionRuntime.onServerHello(
                connection, ProtocolVersion.TRANSPORT_VERSION, true)
                .orElseThrow().accepted());
        ClientSessionRuntime.commitAcceptedSession(connection, true);
        assertSame(businessSession, ClientSessionRuntime.businessSession().orElseThrow());
        assertEquals(ClientModelSession.State.NEGOTIATING, session.state());

        session.publishFull(new RemotePublicationSnapshot(
                List.of(), Set.of(), List.of(), Map.of()));
        assertTrue(ClientSessionRuntime.onServerHello(
                connection, ProtocolVersion.TRANSPORT_VERSION, true).isEmpty());
        assertEquals(ClientModelSession.State.ACTIVE, session.state());
        assertSame(businessSession, ClientSessionRuntime.businessSession().orElseThrow());
    }

    @Test
    void staleConnectionWorkCannotMutateTheReplacementSession() throws Exception {
        var connectionA = new Connection(PacketFlow.CLIENTBOUND);
        var connectionB = new Connection(PacketFlow.CLIENTBOUND);
        var sessionA = session();
        var sessionB = session();
        var field = currentField();
        var ownerA = connectionState(connectionA, sessionA, ignored -> { });
        var ownerB = connectionState(connectionB, sessionB, ignored -> { });
        field.set(null, ownerA);
        var actions = new AtomicInteger();
        assertTrue(ClientSessionRuntime.runIfCurrent(connectionA, actions::incrementAndGet));

        field.set(null, ownerB);
        ((AutoCloseable) ownerA).close();
        assertFalse(ClientSessionRuntime.runIfCurrent(connectionA, actions::incrementAndGet));
        ClientSessionRuntime.failProtocolSession(connectionA);

        assertEquals(ClientModelSession.State.NEGOTIATING, sessionB.state());
        assertEquals(1, actions.get());
        assertTrue(ClientSessionRuntime.runIfCurrent(connectionB, actions::incrementAndGet));
        assertEquals(2, actions.get());
    }

    @Test
    void catalogFailurePreservesBusinessSessionButProtocolFailureClearsIt()
            throws Exception {
        var connection = new Connection(PacketFlow.CLIENTBOUND);
        var session = session();
        var owner = connectionState(connection, session, ignored -> { });
        setCollectionPublication(owner,
                new SessionCollectionPublication.Receiver(Map.of()));
        currentField().set(null, owner);

        assertTrue(ClientSessionRuntime.onServerHello(
                connection, ProtocolVersion.TRANSPORT_VERSION, false)
                .orElseThrow().accepted());
        ClientSessionRuntime.commitAcceptedSession(connection, false);
        var businessSession = ClientSessionRuntime.businessSession().orElseThrow();

        ClientSessionRuntime.failCatalogSession(connection);

        assertSame(businessSession, ClientSessionRuntime.businessSession().orElseThrow());
        assertEquals(ClientModelSession.State.INTRINSIC_DEFAULT_ONLY, session.state());

        ClientSessionRuntime.failProtocolSession(connection);

        assertTrue(ClientSessionRuntime.businessSession().isEmpty());
    }

    @Test
    void deltaFailureNoticeIsOncePerExactConnection() throws Exception {
        var connectionA = new Connection(PacketFlow.CLIENTBOUND);
        var connectionB = new Connection(PacketFlow.CLIENTBOUND);
        var noticesA = new ArrayList<String>();
        var noticesB = new ArrayList<String>();
        var field = currentField();
        var ownerA = connectionState(connectionA, session(), noticesA::add);
        field.set(null, ownerA);

        ClientSessionRuntime.notifyCatalogDeltaFailure(connectionA,
                SessionCollectionPublication.Status.BASELINE_DRIFT);
        ClientSessionRuntime.notifyCatalogDeltaFailure(connectionA,
                SessionCollectionPublication.Status.INTRINSIC_INVALID);
        assertEquals(List.of("message.yes_steve_model.client.catalog_delta_drift"), noticesA);

        var ownerB = connectionState(connectionB, session(), noticesB::add);
        field.set(null, ownerB);
        ((AutoCloseable) ownerA).close();
        ClientSessionRuntime.notifyCatalogDeltaFailure(connectionA,
                SessionCollectionPublication.Status.INTRINSIC_INVALID);
        ClientSessionRuntime.notifyCatalogDeltaFailure(connectionB,
                SessionCollectionPublication.Status.FULL);
        ClientSessionRuntime.notifyCatalogDeltaFailure(connectionB,
                SessionCollectionPublication.Status.INTRINSIC_INVALID);
        assertEquals(List.of("message.yes_steve_model.client.catalog_delta_invalid"), noticesB);
    }

    @Test
    void connectionCloseTerminatesItsMissingServerProducer() throws Exception {
        var owner = connectionState(new Connection(PacketFlow.CLIENTBOUND), session(),
                ignored -> { });
        var entered = new CountDownLatch(1);
        var producer = new Thread(() -> {
            entered.countDown();
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignored) {
                // Connection close is the producer's terminal signal.
            }
        });
        setMissingServerNotice(owner, producer);
        producer.start();
        assertTrue(entered.await(5, TimeUnit.SECONDS));

        ((AutoCloseable) owner).close();

        assertFalse(producer.isAlive());
    }

    private static ClientModelSession session() {
        return new ClientModelSession(SessionMode.AUTO, true, CatalogSnapshot.empty());
    }

    private static Object connectionState(Connection connection, ClientModelSession session,
                                          Consumer<String> notice) throws Exception {
        var type = Class.forName(ClientSessionRuntime.class.getName() + "$ConnectionState");
        var constructor = type.getDeclaredConstructor(Connection.class,
                ClientModelSession.class, Consumer.class, Object.class);
        constructor.setAccessible(true);
        return constructor.newInstance(connection, session, notice, new Object());
    }

    private static Field currentField() throws Exception {
        var field = ClientSessionRuntime.class.getDeclaredField("current");
        field.setAccessible(true);
        return field;
    }

    private static void setCollectionPublication(
            Object owner, SessionCollectionPublication.Receiver receiver) throws Exception {
        var field = owner.getClass().getDeclaredField("collectionPublication");
        field.setAccessible(true);
        field.set(owner, receiver);
    }

    private static void setMissingServerNotice(Object owner, Thread producer) throws Exception {
        var field = owner.getClass().getDeclaredField("missingServerNotice");
        field.setAccessible(true);
        field.set(owner, producer);
    }
}
