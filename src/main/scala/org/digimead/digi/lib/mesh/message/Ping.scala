/**
 * Digi-Lib-Mesh - distributed mesh library for Digi components
 *
 * Copyright (c) 2012-2013 Alexey Aksenov ezh@ezh.msk.ru
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

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.util.Util

import com.escalatesoft.subcut.inject.BindingModule

case class Ping(override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val conversation: UUID = UUID.randomUUID(),
  override val timestamp: Long = System.currentTimeMillis())(val isReplyRequired: Boolean, override val distance: Byte = 0)
  extends Message(Ping.word, sourceHexapod, destinationHexapod, conversation, timestamp) {
  val messageType = Message.Type.Standard
  Ping.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content(): Array[Byte] = Array()
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ Ping(_, _, conversation, _)) if message.conversation == conversation =>
      Some(true)
    case _ =>
      None
  }
  override def toString = "Ping[%08X %s]".format(conversation.hashCode(), labelSuffix)
}

class PingFactory extends Ping.Interface {
  def build(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String,
    distance: Byte, content: Array[Byte]): Option[Message] =
    Some(Ping(from.uuid, Some(to.uuid), conversation, timestamp)(false, distance))
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message: Ping) =>
      Mesh(message.sourceHexapod) match {
        case Some(source: Hexapod) =>
          DiffieHellman.log.debug("generate Ping response for %s".format(source))
          val responseSource = message.destinationHexapod.getOrElse(Hexapod.uuid)
          val responseDestination = Some(source.uuid)
          Communication.push(Ping(responseSource, responseDestination, message.conversation)(false))
          Some(true)
        case None =>
          DiffieHellman.log.error("unable to find source hexapod " + message.sourceHexapod)
          Some(false)
      }
    case _ =>
      None
  }
}

object Ping extends Message.Factory with Loggable {
  val word = "ping"

  def build(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, distance: Byte,
    content: Array[Byte]) = DI.implementation.build(from, to, conversation, timestamp, word, distance, content)
  def react(stimulus: Stimulus) = DI.implementation.react(stimulus)

  trait Interface extends Loggable {
    def build(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String,
      distance: Byte, content: Array[Byte]): Option[Message]
    def react(stimulus: Stimulus): Option[Boolean]
  }
  /**
   * Dependency injection routines
   */
  private object DI extends DependencyInjection.PersistentInjectable {
    /** The Ping instance */
    lazy val implementation = injectIfBound[Interface] { new PingFactory }
  }
}
