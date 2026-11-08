package com.github.pjfanning.pekko.rabbitmq

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito._
import org.apache.pekko.testkit.TestFSMRef
import org.apache.pekko.actor.{ ActorRef, Props }
import ConnectionActor._
import com.rabbitmq.client.{ ShutdownListener, ShutdownSignalException }

import java.io.IOException
import scala.concurrent.duration._
import scala.collection.immutable.Iterable

/**
 * @author Yaroslav Klymko
 */
class ConnectionActorSpec extends ActorSpec {

  "ConnectionActor" should {

    "try to connect on startup" in new TestScope {
      actorRef ! Connect
      state mustEqual connectedAfterRecovery
      verify(factory).newConnection()
      verify(recoveredConnection).addShutdownListener(any(classOf[ShutdownListener]))
      verify(setup).apply(recoveredConnection, actorRef)
    }

    "not reconnect if has connection" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef ! Connect
      state mustEqual connectedInitially
    }

    "try to reconnect if failed to connect" in new TestScope {
      when(factory.newConnection()).thenThrow(new IOException)
      actorRef ! Connect
      state mustEqual disconnected
    }

    "try to reconnect if can't create new channel" in new TestScope {
      when(initialConnection.createChannel()).thenThrow(new IOException)
      when(recoveredConnection.createChannel()).thenThrow(new IOException)
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef ! create
      state mustEqual disconnected
    }

    "attempt to connect on Connect message" in new TestScope {
      when(factory.newConnection()).thenThrow(new IOException).thenReturn(recoveredConnection)
      actorRef ! Connect
      state mustEqual disconnected
      actorRef ! Connect
      state mustEqual connectedAfterRecovery
    }

    "reconnect on ShutdownSignalException from server" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actor.shutdownCompleted(shutdownSignal())
      eventually(state mustEqual connectedAfterRecovery)
    }

    "keep trying to reconnect on ShutdownSignalException from server" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      when(factory.newConnection()).thenThrow(new IOException).thenThrow(new IOException).thenReturn(recoveredConnection)
      actor.shutdownCompleted(shutdownSignal())
      state mustEqual disconnected
    }

    "not reconnect on ShutdownSignalException for different connection" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actor.shutdownCompleted(shutdownSignal(reference = mock(classOf[Connection])))
      verify(factory, never()).newConnection()
    }

    "create children actor with channel" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef ! create
      expectMsg(channel)
      expectMsg(ChannelCreated(testActor))
    }

    "create children actor without channel if failed to create new channel" in new TestScope {
      when(initialConnection.createChannel()).thenThrow(new IOException)
      when(recoveredConnection.createChannel()).thenThrow(new IOException)
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef ! create
      expectMsg(ChannelCreated(testActor))
      expectMsg(ParentShutdownSignal)
      state mustEqual disconnected
    }

    "create children actor without channel" in new TestScope {
      actorRef ! create
      expectMsg(ChannelCreated(testActor))
    }

    "notify children if connection lost" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actor.shutdownCompleted(shutdownSignal())
      expectMsg(ParentShutdownSignal)
    }

    "notify children when connection established" in new TestScope {
      actorRef ! Connect
      expectMsg(channel)
    }

    "close connection on shutdown" in new TestScope {
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef.stop()
      verify(initialConnection).close()
    }

    "not become Disconnected when getting an AmqpShutdownSignal because of its own reconnection procedure" in new TestScope {
      // connection actor starts out connected
      actorRef.setState(Connected, Connected(initialConnection))

      // give it a channel actor
      actorRef ! create

      // so the connection actor will send a channel to the newly created channel actor, and a ChannelCreated to the
      // sender of CreateChannel. Both the recipients are testActor here.
      expectMsg(channel)
      expectMsg(ChannelCreated(testActor))

      // tell the actor the connection went away
      actor.shutdownCompleted(shutdownSignal())

      // give connection actor the time to close and reconnect
      expectMsg(ParentShutdownSignal)
      verify(initialConnection).close()
      expectMsg(channel)

      // now because of this close, RabbitMQ may tell the actor that the previous connection was shut down by the app
      actor.shutdownCompleted(shutdownSignal(initiatedByApplication = true, reference = initialConnection))

      // now, let's see if the ConnectionActor stays responsive
      actorRef ! ProvideChannel
      expectMsg(channel)
    }

    "respond to GetState message" in new TestScope {
      actorRef ! GetState
      expectMsg(Disconnected)
      actorRef.setState(Connected, Connected(initialConnection))
      actorRef ! GetState
      expectMsg(Connected)
    }

    "create only one channel when reconnecting" in new TestScopeBase {
      override val reconnectionDelay: FiniteDuration = FiniteDuration(0, SECONDS)

      val setupChannel: (Channel, ActorRef) => Unit = mock(classOf[(Channel, ActorRef) => Unit])
      val createChannel: CreateChannel = CreateChannel(ChannelActor.props(setupChannel))

      class TestConnectionActor extends ConnectionActor(factory, reconnectionDelay, setup) {
        override def preStart(): Unit = {}
      }

      val connectionActorRef: TestFSMRef[State, Data, TestConnectionActor] = TestFSMRef(new TestConnectionActor)

      connectionActorRef.setState(Connected, Connected(initialConnection))
      connectionActorRef ! createChannel
      expectMsgType[ChannelCreated]
      verify(initialConnection).createChannel()

      connectionActorRef ! AmqpShutdownSignal(shutdownSignal())
      (1 to 10).map(_ => verify(recoveredConnection, Mockito.atMostOnce()).createChannel())
    }
  }

  private abstract class TestScope extends TestScopeBase {
    class TestConnectionActor extends ConnectionActor(factory, reconnectionDelay, setup) {
      override def children: Iterable[ActorRef] = Iterable(testActor)
      override def newChild(props: Props, name: Option[String]): ActorRef = testActor
      override def preStart(): Unit = {}
    }

    val actorRef: TestFSMRef[State, Data, TestConnectionActor] = TestFSMRef(new TestConnectionActor)

    def actor: ConnectionActor = actorRef.underlyingActor.asInstanceOf[ConnectionActor]
    def state: (State, Data) = actorRef.stateName -> actorRef.stateData
  }

  private abstract class TestScopeBase extends ActorScope {
    val channel: Channel = mock(classOf[Channel])

    def createMockConnection(): Connection = {
      val connection = mock(classOf[Connection])
      when(connection.isOpen).thenReturn(true)
      when(connection.createChannel()).thenReturn(channel)
      connection
    }

    val initialConnection: Connection = createMockConnection()
    val recoveredConnection: Connection = createMockConnection()

    val factory: ConnectionFactory = {
      val factory = mock(classOf[ConnectionFactory])
      when(factory.newConnection()).thenReturn(recoveredConnection)
      factory
    }
    val create: CreateChannel = CreateChannel(null)
    val reconnectionDelay: FiniteDuration = FiniteDuration(1, SECONDS)
    val setup: (Connection, ActorRef) => Any = mock(classOf[(Connection, ActorRef) => Any])

    def disconnected: (ConnectionActor.Disconnected.type, ConnectionActor.NoConnection.type) = Disconnected -> NoConnection
    def connectedInitially: (ConnectionActor.Connected.type, Connected) = Connected -> Connected(initialConnection)
    def connectedAfterRecovery: (ConnectionActor.Connected.type, Connected) = Connected -> Connected(recoveredConnection)

    def shutdownSignal(initiatedByApplication: Boolean = false, reference: AnyRef = initialConnection): ShutdownSignalException = {
      val shutdownSignal = mock(classOf[ShutdownSignalException])
      when(shutdownSignal.isInitiatedByApplication).thenReturn(initiatedByApplication)
      when(shutdownSignal.getReference).thenReturn(reference, Nil: _*)
      shutdownSignal
    }
  }
}
