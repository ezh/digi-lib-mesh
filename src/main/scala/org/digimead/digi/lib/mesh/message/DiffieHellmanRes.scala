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

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Receptor
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.util.Util

case class DiffieHellmanRes(val publicKey: BigInt,
  override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val timeToLive: Long = Communication.holdTimeToLive,
  override val conversation: UUID = UUID.randomUUID(),
  override val timestamp: Long = System.currentTimeMillis())
  extends Message(DiffieHellmanRes.word, true, sourceHexapod, destinationHexapod, timeToLive, conversation, timestamp) {
  DiffieHellmanRes.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content(): Array[Byte] = publicKey.toByteArray
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ Ping(_, _, _, _, _)) if message.conversation == conversation =>
      Some(true)
    case _ =>
      None
  }
  override def toString = "DiffieHellmanRes[%08X %s]".format(this.hashCode(), labelSuffix)
}

class DiffieHellmanResBuilder extends Message.MessageBuilder with Logging {
  def buildMessage(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, content: Array[Byte]): Option[Message] = try {
    val bais = new ByteArrayInputStream(content)
    val r = new DataInputStream(bais)
    val publicKey = new BigInt(new java.math.BigInteger(content))
    r.close()
    Some(DiffieHellmanRes(publicKey, from.uuid, Some(to.uuid), Communication.holdTimeToLive, conversation, timestamp))
  } catch {
    case e =>
      log.warn(e.getMessage())
      None
  }
}

class DiffieHellmanResReceptor extends Receptor {
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ DiffieHellmanReq(publicKey, g, p, _, _, _, _, _)) =>
      /*      Mesh(message.sourceHexapod) match {
        case Some(source: Hexapod) =>
          DiffieHellmanReq.log.debug("generate new DiffieHellmanRes for %s from %s".format(source, DiffieHellmanReq.word))
          val dh = new DiffieHellman(g, p)
          dh.createSecretKey()
          dh.setPeerPublicKey(publicKey)
          Hexapod.setDiffieHellman(source, Some(dh))
          Hexapod.setSessionKey(source, Some(dh.createSharedKey))
          val responseSource = message.destinationHexapod.getOrElse(Hexapod.uuid)
          val responseDestination = Some(source.uuid)
          DiffieHellmanReq.log.___glance("!" + responseSource + " -> " + responseDestination)
          Communication.push(DiffieHellmanRes(dh.getPublicKey, responseSource, responseDestination, None))
          Some(true)
        case None =>
          DiffieHellmanReq.log.error("unable to find source hexapod " + message.sourceHexapod)
          Some(false)
      }*/
      None
    case _ =>
      None
  }
}

object DiffieHellmanRes extends Logging {
  val word = "dh_res"
  private val initializationArgument = new AtomicReference[Option[Init]](None)

  def init(arg: Init): Unit = synchronized {
    assert(!isInitialized, "DiffieHellmanRes already initialized")
    assert(Communication.isInitialized, "Communication not initialized")
    log.debug("initialize DiffieHellmanRes")
    initializationArgument.set(Some(arg))
    Message.add(word, arg.builder)
    Communication.registerGlobal(arg.receptor)
  }
  def isInitialized(): Boolean = initializationArgument.get.nonEmpty

  trait Init {
    val builder: Message.MessageBuilder
    val receptor: Receptor
  }
  class DefaultInit extends Init {
    val builder: Message.MessageBuilder = new DiffieHellmanResBuilder
    val receptor: Receptor = new DiffieHellmanResReceptor
  }
}
