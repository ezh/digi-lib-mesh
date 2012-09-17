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

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.util.Util

case class Ping(override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val transportEndpoint: Option[UUID] = None,
  override val conversation: UUID = UUID.randomUUID(),
  override val timestamp: Long = System.currentTimeMillis())
  extends Message(Ping.word, true, sourceHexapod, destinationHexapod, transportEndpoint, conversation, timestamp) {
  Ping.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content(): Array[Byte] = Array()
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ Ping(_, _, _, conversation, _)) if message.conversation.compareTo(conversation) == 0 =>
      Some(true)
    case _ =>
      None
  }
  override def toString = "Ping[%08X %s]".format(this.hashCode(), labelSuffix)
}

object Ping extends Message.MessageBuilder with Logging {
  val word = "ping"
  Message.add(word, this)

  def buildMessage(from: Hexapod, fromEndpoint: UUID, to: Hexapod, toEndpoint: UUID,
    conversation: UUID, timestamp: Long, word: String, content: Array[Byte]): Option[Message] = {
    Some(Ping(from.uuid, Some(to.uuid), Some(toEndpoint), conversation, timestamp))
  }
}
