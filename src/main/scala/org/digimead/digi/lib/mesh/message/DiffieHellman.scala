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
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.Date
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

import scala.Option.option2Iterable

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Receptor
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.util.Util

case class DiffieHellman(val key: BigInt, val g: Int, val p: BigInt,
  override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val conversation: UUID = UUID.randomUUID(),
  override val timestamp: Long = System.currentTimeMillis())(val isReplyRequired: Boolean, override val distance: Byte = 0)
  extends Message(DiffieHellman.word, sourceHexapod, destinationHexapod, conversation, timestamp) {
  val messageType = Message.Type.Unencripted
  DiffieHellman.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content(): Array[Byte] = {
    val keyBytes = key.toByteArray
    val pBytes = p.toByteArray
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    w.writeInt(keyBytes.length)
    w.write(keyBytes, 0, keyBytes.length)
    w.writeInt(g)
    w.writeInt(pBytes.length)
    w.write(pBytes, 0, pBytes.length)
    w.flush()
    val data = baos.toByteArray()
    w.close()
    data
  }
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ DiffieHellman(key, 0, _, _, _, _, _)) if message.conversation == conversation =>
      Mesh(sourceHexapod) match {
        case Some(source: AppHexapod) =>
          Mesh(message.sourceHexapod) match {
            case Some(hexapod: Hexapod) =>
              assert(hexapod.uuid != sourceHexapod, "illegal to update of source hexapod")
              hexapod.setDiffieHellman(0, 0, key)
              Communication.processMessages
              Some(true)
            case None =>
              DiffieHellman.log.error("unable to update unexists hexapod " + message.sourceHexapod)
              Some(false)
          }
        case _ =>
          DiffieHellman.log.error("unable to find source hexapod " + sourceHexapod)
          Some(false)
      }
    case _ =>
      None
  }
  override def toString = "DiffieHellman[%08X %s]".format(conversation.hashCode(), labelSuffix)
}

class DiffieHellmanBuilder extends Message.MessageBuilder with Logging {
  def buildMessage(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, distance: Byte, content: Array[Byte]): Option[Message] = try {
    val bais = new ByteArrayInputStream(content)
    val r = new DataInputStream(bais)
    val publicKeyLength = r.readInt()
    val publicKeyBytes = new Array[Byte](publicKeyLength)
    val actualPublicKeyLength = r.read(publicKeyBytes)
    assert(actualPublicKeyLength == publicKeyLength)
    val publicKey = new BigInt(new java.math.BigInteger(publicKeyBytes))
    val g = r.readInt()
    val pLength = r.readInt()
    val pBytes = new Array[Byte](pLength)
    val actualPLength = r.read(pBytes)
    assert(actualPLength == pLength)
    val p = new BigInt(new java.math.BigInteger(pBytes))
    r.close()
    Some(DiffieHellman(publicKey, g, p, from.uuid, Some(to.uuid), conversation, timestamp)(false, distance))
  } catch {
    case e =>
      log.warn(e.getMessage())
      None
  }
}

class DiffieHellmanReceptor extends Receptor {
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ DiffieHellman(publicKey, g, p, _, _, conversation, _)) if g != 0 =>
      Mesh(message.sourceHexapod) match {
        case Some(source: Hexapod) if Hexapod.getDiffieHellman.nonEmpty =>
          DiffieHellman.log.debug("generate DiffieHellman response for %s".format(source))
          val dh = source.setDiffieHellman(g, p, publicKey)
          val responseSource = message.destinationHexapod.getOrElse(Hexapod.uuid)
          val responseDestination = Some(source.uuid)
          Communication.push(DiffieHellman(dh.publicKey, 0, 0, responseSource, responseDestination, conversation)(false))
          Some(true)
        case Some(source: Hexapod) =>
          DiffieHellman.log.error(Hexapod + " DiffieHellman parameter is absent")
          Some(false)
        case None =>
          DiffieHellman.log.error("unable to find source hexapod " + message.sourceHexapod)
          Some(false)
      }
    case _ =>
      None
  }
}

object DiffieHellman extends Logging {
  val word = "dh"
  private val initializationArgument = new AtomicReference[Option[Init]](None)

  def init(arg: Init): Unit = synchronized {
    assert(!isInitialized, "DiffieHellman already initialized")
    assert(Communication.isInitialized, "Communication not initialized")
    log.debug("initialize DiffieHellman")
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
    val builder: Message.MessageBuilder = new DiffieHellmanBuilder
    val receptor: Receptor = new DiffieHellmanReceptor
  }
}
