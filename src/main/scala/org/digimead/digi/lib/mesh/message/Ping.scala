/**
 * Digi-Lib-Mesh - distributed mesh library for Digi components
 *
 * Copyright (c) 2012 Alexey Aksenov ezh@ezh.msk.ru
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.digimead.digi.lib.mesh.message

import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.util.Util

case class Ping(override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val conversation: UUID = UUID.randomUUID(),
  override val timeToLive: Long = Communication.holdTimeToLive,
  override val timestamp: Long = System.currentTimeMillis())
  extends Message(Ping.word, true, sourceHexapod, destinationHexapod, conversation, timeToLive, timestamp) {
  Ping.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content(): Array[Byte] = Array()
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ Ping(_, _, conversation, _, _)) if message.conversation.compareTo(conversation) == 0 =>
      Some(true)
    case _ =>
      None
  }
  override def toString = "Ping[%08X %s]".format(this.hashCode(), labelSuffix)
}

class PingBuilder extends Message.MessageBuilder with Logging {
  def buildMessage(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, content: Array[Byte]): Option[Message] = {
    Some(Ping(from.uuid, Some(to.uuid), conversation, Communication.holdTimeToLive, timestamp))
  }
}

object Ping extends Logging {
  val word = "ping"
  private val initializationArgument = new AtomicReference[Option[Init]](None)

  def init(arg: Init): Unit = synchronized {
    assert(!isInitialized, "DiffieHellmanReq already initialized")
    assert(Communication.isInitialized, "Communication not initialized")
    log.debug("initialize DiffieHellmanReq")
    initializationArgument.set(Some(arg))
    Message.add(word, arg.builder)
  }
  def isInitialized(): Boolean = initializationArgument.get.nonEmpty

  trait Init {
    val builder: Message.MessageBuilder
  }
  class DefaultInit extends Init {
    val builder: Message.MessageBuilder = new PingBuilder
  }
}
