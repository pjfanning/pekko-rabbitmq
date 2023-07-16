package com.github.pjfanning.pekko.rabbitmq

import org.apache.pekko.actor.{ Props, ActorRef }

case class CreateChannel(props: Props, name: Option[String] = None)

case class ChannelCreated(channel: ActorRef)

case class ChannelMessage(onChannel: OnChannel, dropIfNoChannel: Boolean = true)
