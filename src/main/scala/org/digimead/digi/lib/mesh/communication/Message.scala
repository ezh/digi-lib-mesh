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

import scala.Option.option2Iterable
import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.hexapod.Hexapod

abstract class Message(
  val word: String,
  val isReplyRequired: Boolean,
  val sourceHexapod: UUID,
  val destinationHexapod: Option[UUID],
  val timeToLive: Long = Communication.holdTimeToLive,
  val conversation: UUID = UUID.randomUUID(),
  val timestamp: Long = System.currentTimeMillis()) extends Receptor {
  assert(word.nonEmpty, "word of message is absent")
  protected lazy val labelSuffix = Mesh(sourceHexapod) + "->" + destinationHexapod.flatMap(Mesh(_))

  def content(): Array[Byte]
  def createRawMessage(from: Hexapod, to: Hexapod, key: Option[BigInt]): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    w.writeBoolean(key.nonEmpty)
    w.writeLong(from.uuid.getLeastSignificantBits())
    w.writeLong(from.uuid.getMostSignificantBits())
    val body = key match {
      case Some(key) =>
        null // encript message.content
      case None =>
        createRawMessageBody(to)
    }
    w.writeInt(body.length)
    w.write(body, 0, body.length)
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
    w.writeInt(content.length)
    w.writeUTF(word)
    w.write(content, 0, content.length)
    val data = baos.toByteArray()
    w.close()
    data
  }
}

object Message extends Logging {
  private val messageMap = new HashMap[String, MessageBuilder] with SynchronizedMap[String, MessageBuilder]

  def add(word: String, builder: MessageBuilder) = {
    assert(!messageMap.contains(word), "message with word \"%s\" already defined".format(word))
    messageMap(word) = builder
  }
  def apply(rawMessage: Array[Byte]): Option[Message] = parseRawMessage(rawMessage)
  def parseRawMessage(rawMessage: Array[Byte]): Option[Message] = {
    val bais = new ByteArrayInputStream(rawMessage)
    val r = new DataInputStream(bais)
    val encrypted = r.readBoolean()
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
    val decryptedBody = if (encrypted) {
      // decrypt
      null
    } else {
      body
    }
    val (toHexapod, conversation, timestamp, word, content) = parseRawMessageBody(decryptedBody)
    messageMap.get(word) match {
      case Some(builder) =>
        builder.buildMessage(fromHexapod, toHexapod, conversation, timestamp, word, content)
      case None =>
        log.warn("unable to parse message \"%s\" from %s".format(word, fromHexapod))
        None
    }
  }
  private def parseRawMessageBody(body: Array[Byte]): (Hexapod, UUID, Long, String, Array[Byte]) = {
    val bais = new ByteArrayInputStream(body)
    val r = new DataInputStream(bais)
    val toHexapodLSB = r.readLong()
    val toHexapodMSB = r.readLong()
    val conversationLSB = r.readLong()
    val conversationMSB = r.readLong()
    val creationTimestamp = r.readLong()
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
    (toHexapod, conversationUUID, creationTimestamp, word, content)
  }

  trait MessageBuilder {
    /** recreate message from various parameters */
    def buildMessage(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, content: Array[Byte]): Option[Message]
  }
}


