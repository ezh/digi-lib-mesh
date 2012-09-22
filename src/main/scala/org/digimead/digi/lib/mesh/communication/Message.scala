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

package org.digimead.digi.lib.mesh.communication

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import scala.math.BigInt.int2bigInt

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.mesh.message.Acknowledgement

import javax.crypto.BadPaddingException

abstract class Message(
  val word: String,
  val sourceHexapod: UUID,
  val destinationHexapod: Option[UUID],
  val conversation: UUID = UUID.randomUUID(),
  val timestamp: Long = System.currentTimeMillis()) extends Receptor {
  val distance: Byte = 0
  val isReplyRequired: Boolean
  val timeToLive: Long = Communication.holdTimeToLive
  assert(word.nonEmpty, "word of message is absent")
  protected lazy val labelSuffix = Mesh(sourceHexapod) + "->" + destinationHexapod.flatMap(Mesh(_))
  val messageType: Message.Type.Value

  def content(): Array[Byte]
  def createRawMessage(from: Hexapod, to: Hexapod, rawKey: Option[Array[Byte]]): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    // write message type
    messageType match {
      case Message.Type.Standard if rawKey.isEmpty =>
        w.writeByte(Message.Type.Unencripted.id)
      case _ =>
        w.writeByte(messageType.id)
    }
    if (messageType == Message.Type.Acknowledgement) {
      // write acknowledgement
      val content = this.content()
      w.write(content, 0, content.length)
    } else {
      // write plain message header
      w.writeLong(from.uuid.getLeastSignificantBits())
      w.writeLong(from.uuid.getMostSignificantBits())
      // write body
      val body = rawKey match {
        case Some(rawKey) =>
          Message.log.debug("encrypt message body")
          Simple.encrypt(rawKey, createRawMessageBody(to))
        case None =>
          createRawMessageBody(to)
      }
      w.writeInt(body.length)
      w.write(body, 0, body.length)
    }
    w.flush()
    val data = baos.toByteArray()
    w.close()
    data
  }
  private def createRawMessageBody(to: Hexapod): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    w.writeLong(to.uuid.getLeastSignificantBits())
    w.writeLong(to.uuid.getMostSignificantBits())
    w.writeLong(conversation.getLeastSignificantBits())
    w.writeLong(conversation.getMostSignificantBits())
    w.writeLong(timestamp)
    w.writeByte(distance)
    w.writeInt(content.length)
    w.writeUTF(word)
    w.write(content, 0, content.length)
    val data = baos.toByteArray()
    w.close()
    data
  }
}

object Message extends Logging {
  @volatile var aaa: BigInt = 0
  private val messageMap = new HashMap[String, MessageBuilder] with SynchronizedMap[String, MessageBuilder]

  def add(word: String, builder: MessageBuilder) = {
    assert(!messageMap.contains(word), "message with word \"%s\" already defined".format(word))
    messageMap(word) = builder
  }
  def apply(rawMessage: Array[Byte], sendAcknowledgementIfSuccess: Boolean): Option[Message] = parseRawMessage(rawMessage, sendAcknowledgementIfSuccess)
  def parseRawMessage(rawMessage: Array[Byte], sendAcknowledgementIfSuccess: Boolean): Option[Message] = {
    val bais = new ByteArrayInputStream(rawMessage)
    val r = new DataInputStream(bais)
    val messageType = r.readByte()
    if (messageType == Message.Type.Acknowledgement.id)
      return Some(Acknowledgement(r.readInt()))
    val fromHexapodLSB = r.readLong()
    val fromHexapodMSB = r.readLong()
    val bodyLength = r.readInt()
    val body = new Array[Byte](bodyLength)
    val actualBodyLength = r.read(body, 0, bodyLength)
    r.close()
    assert(actualBodyLength == bodyLength, "raw message body is incomplete %d vs %d".format(bodyLength, actualBodyLength))
    val fromHexapodUUID = new UUID(fromHexapodMSB, fromHexapodLSB)
    val fromHexapod: Hexapod = Mesh(fromHexapodUUID) match {
      case Some(hexapod: Hexapod) => hexapod
      case _ => new Hexapod(fromHexapodUUID)
    }
    val decryptedBody = if (messageType == Message.Type.Unencripted.id) {
      body
    } else {
      Hexapod.getKeyForHexapod(fromHexapod) match {
        case Some((sharedKey, rawKey)) =>
          try {
            val result = Simple.decrypt(rawKey, body)
            Message.log.debug("decrypt message body")
            result
          } catch {
            case e: BadPaddingException =>
              log.error("unable to decrypt: possible wrong key. " + e.getMessage())
              return None
          }
        case None =>
          log.error("unable to decrypt: %s<->%s session key not found".format(fromHexapod, Hexapod.instance))
          return None
      }
    }
    val (toHexapod, conversation, timestamp, word, distance, content) = parseRawMessageBody(decryptedBody)
    assert(!Hexapod.isInitialized || fromHexapodUUID != Hexapod.uuid, "illegal message \"%s\" from AppHexapod".format(word))
    if (sendAcknowledgementIfSuccess)
      Communication.push(Acknowledgement(conversation.hashCode(), Some(fromHexapodUUID)))
    if (distance == 127) {
      log.warn("drop message %s, maximum distance riched")
      return None
    }
    messageMap.get(word) match {
      case Some(builder) =>
        builder.buildMessage(fromHexapod, toHexapod, conversation, timestamp, word, (distance + 1).toByte, content)
      case None =>
        log.warn("unable to parse message \"%s\" from %s".format(word, fromHexapod))
        None
    }
  }
  private def parseRawMessageBody(body: Array[Byte]): (Hexapod, UUID, Long, String, Byte, Array[Byte]) = {
    val bais = new ByteArrayInputStream(body)
    val r = new DataInputStream(bais)
    val toHexapodLSB = r.readLong()
    val toHexapodMSB = r.readLong()
    val conversationLSB = r.readLong()
    val conversationMSB = r.readLong()
    val creationTimestamp = r.readLong()
    val distance = r.readByte()
    val contentLength = r.readInt()
    val word = r.readUTF()
    val (content, actualContentLength) = if (contentLength != 0) {
      val content = new Array[Byte](contentLength)
      (content, r.read(content, 0, contentLength))
    } else
      (Array[Byte](), 0)
    r.close()
    assert(actualContentLength == contentLength, "raw message content is incomplete %d vs %d".format(contentLength, actualContentLength))
    val toHexapodUUID = new UUID(toHexapodMSB, toHexapodLSB)
    val toHexapod: Hexapod = Mesh(toHexapodUUID) match {
      case Some(hexapod: Hexapod) => hexapod
      case _ => new Hexapod(toHexapodUUID)
    }
    val conversationUUID = new UUID(conversationMSB, conversationLSB)
    (toHexapod, conversationUUID, creationTimestamp, word, distance, content)
  }

  trait Type extends Enumeration {
    val Acknowledgement = Value(0)
    val Unencripted = Value(1)
    val Standard = Value(2)
  }
  object Type extends Type
  trait MessageBuilder {
    /** recreate message from various parameters */
    def buildMessage(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, distance: Byte, content: Array[Byte]): Option[Message]
  }
}


