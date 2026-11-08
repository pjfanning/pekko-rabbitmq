package com.github.pjfanning.pekko.rabbitmq

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.apache.pekko.testkit.TestFSMRef
import org.apache.pekko.actor.ActorRef
import ChannelActor._
import com.rabbitmq.client.ShutdownSignalException

import collection.immutable.Queue
import java.io.IOException
import ConnectionActor.ProvideChannel

/**
 * @author Yaroslav Klymko
 */
class ChannelActorSpec extends ActorSpec {
  "ChannelActor" should {
    "setup channel when channel received" in new TestScope {
      actorRef ! channel
      state mustEqual connected()
      verify(setupChannel).apply(channel, actorRef)
      verify(channel).addShutdownListener(actor)
    }
    "close channel if received unexpectedly" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      val newChannel: Channel = mock(classOf[Channel])
      actorRef ! newChannel
      verify(newChannel).close()
      state mustEqual connected(channel)
    }
    "process message if has channel" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      actorRef ! ChannelMessage(onChannel)
      verify(onChannel).apply(channel)
      state mustEqual connected()
    }
    "process message if has channel, and when fails but channel is still open, drops the message and reconnects" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      actorRef ! ChannelMessage(onChannelFailure, dropIfNoChannel = false)
      state mustEqual disconnected()
      expectMsg(ProvideChannel)
    }
    "process message if has channel, and when fails and channel is not open, retains the message for retry and reconnects" in new TestScope {
      actorRef.setState(Connected, Connected(closedChannel))
      actorRef ! ChannelMessage(onChannelFailure, dropIfNoChannel = false)
      state mustEqual disconnected(Retrying(3, onChannelFailure))
      expectMsg(ProvideChannel)
    }
    "process message if has channel, and when fails and channel is not open, retains the message with retry count decremented and reconnects" in new TestScope {
      actorRef.setState(Connected, Connected(closedChannel))
      actorRef ! ChannelMessage(Retrying(2, onChannelFailure), dropIfNoChannel = false)
      state mustEqual disconnected(Retrying(1, onChannelFailure))
      expectMsg(ProvideChannel)
    }
    "process message if has channel, and when fails and channel is not open and retry count is 0, drops the message and reconnects" in new TestScope {
      actorRef.setState(Connected, Connected(closedChannel))
      actorRef ! ChannelMessage(Retrying(0, onChannelFailure), dropIfNoChannel = false)
      state mustEqual disconnected()
      expectMsg(ProvideChannel)
    }
    "process message if has channel, and when fails and channel is not open and dropIfNoChannel is true, drops the message and reconnects" in new TestScope {
      actorRef.setState(Connected, Connected(closedChannel))
      actorRef ! ChannelMessage(onChannelFailure)
      state mustEqual disconnected()
      expectMsg(ProvideChannel)
    }
    "collect channel message if no channel" in new TestScope {
      actorRef ! ChannelMessage(onChannel, dropIfNoChannel = false)
      state mustEqual disconnected(onChannel)
    }
    "drop channel message if no channel and allowed to drop" in new TestScope {
      actorRef ! ChannelMessage(onChannel)
      state mustEqual disconnected()
    }
    "leave channel if told by parent" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      actorRef ! ParentShutdownSignal
      state mustEqual disconnected()
      expectNoMessage()
    }
    "leave channel on ShutdownSignal" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      actor.shutdownCompleted(channelShutdownSignal)
      state mustEqual disconnected()
      expectMsg(ProvideChannel)
    }
    "stay connected on connection-level ShutdownSignal (waiting for ParentShutdown from ConnectionActor)" in new TestScope {
      actorRef.setState(Connected, Connected(channel))
      actor.shutdownCompleted(connectionShutdownSignal)
      state mustEqual connected()
    }
    "process queued channel messages when channel received" in new TestScope {
      actorRef.setState(Disconnected, InMemory(Queue(onChannel, onChannel)))
      actorRef ! channel
      verify(onChannel, times(2)).apply(channel)
      state mustEqual connected()
    }
    "process queued channel messages when channel received and failed" in new TestScope {
      val last: OnChannel = mock(classOf[OnChannel])
      actorRef.setState(Disconnected, InMemory(Queue(onChannel, onChannelFailure, last)))
      actorRef ! channel
      verify(onChannel).apply(channel)
      state mustEqual disconnected(last)
    }
    "respond to GetState message" in new TestScope {
      actorRef ! GetState
      expectMsg(Disconnected)
      actorRef.setState(Connected, Connected(channel))
      actorRef ! GetState
      expectMsg(Connected)
    }
    "request channel on postRestart" in new TestScope {
      actor.postRestart(new RuntimeException(""))
      expectMsg(ProvideChannel)
    }
  }

  private abstract class TestScope extends ActorScope {
    val setupChannel: (Channel, ActorRef) => Unit = mock(classOf[(Channel, ActorRef) => Unit])
    val onChannel: OnChannel = mock(classOf[OnChannel])
    val channel: Channel = {
      val channel = mock(classOf[Channel])
      when(channel.isOpen).thenReturn(true)
      channel
    }
    val closedChannel: Channel = {
      val channel = mock(classOf[Channel])
      when(channel.isOpen).thenReturn(false)
      channel
    }
    val channelShutdownSignal: ShutdownSignalException = mock(classOf[ShutdownSignalException])
    when(channelShutdownSignal.getReference).thenReturn(channel, Nil: _*)

    val connectionShutdownSignal: ShutdownSignalException = mock(classOf[ShutdownSignalException])
    when(connectionShutdownSignal.getReference).thenReturn(mock(classOf[Connection]), Nil: _*)

    val actorRef: TestFSMRef[State, Data, TestChannelActor] = TestFSMRef(new TestChannelActor)

    def actor: ChannelActor = actorRef.underlyingActor.asInstanceOf[ChannelActor]
    def state: (State, Data) = actorRef.stateName -> actorRef.stateData
    def disconnected(xs: OnChannel*): (ChannelActor.Disconnected.type, InMemory) = Disconnected -> InMemory(Queue(xs: _*))
    def connected(x: Channel = channel): (ChannelActor.Connected.type, Connected) = Connected -> Connected(x)
    val onChannelFailure: Channel => Any = { _ => throw new IOException() }

    class TestChannelActor extends ChannelActor(setupChannel) {
      override def connectionActor: ActorRef = testActor
    }
  }
}
