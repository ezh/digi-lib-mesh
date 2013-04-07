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

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Receptor
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app

import com.escalatesoft.subcut.inject.BindingModule

import javax.crypto.BadPaddingException

abstract class Message(
  val word: String,
  val sourceHexapod: UUID,
  val destinationHexapod: Option[UUID],
  val conversation: UUID = UUID.randomUUID(),
  val timestamp: Long = System.currentTimeMillis()) extends Receptor with Loggable with java.io.Serializable {
  val distance: Byte = 0
  val isReplyRequired: Boolean
  val timeToLive: Long = Communication.holdTTL
  assert(word.nonEmpty, "word of message is absent")
  protected lazy val labelSuffix = Mesh(sourceHexapod) + "->" + destinationHexapod.flatMap(Mesh(_))
  val messageType: Message.Type.Value

  def content(): Array[Byte]
  def createRawMessage(from: Hexapod, to: Hexapod, rawKey: Option[Array[Byte]]): Array[Byte] = {
    log.debug("create raw message %s -> %s".format(from, to))
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    // write message type
    messageType match {
      case Message.Type.Standard if rawKey.isEmpty =>
        w.writeByte(Message.Type.Unencrypted.id)
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

object Message extends DependencyInjection.PersistentInjectable with Loggable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  implicit def bindingModule = DependencyInjection()
  private var factory = inject[Seq[Factory]].map(factory => factory.word -> factory).toMap
  /** number of hexapods that added to original destination */
  @volatile private var recipientRedundancy = 1
  Communication // start initialization if needed

  def apply(rawMessage: Array[Byte], sendAcknowledgementIfSuccess: Boolean): Option[Message] =
    parseRawMessage(rawMessage, sendAcknowledgementIfSuccess)
  def getRecipients(message: Message): Seq[Hexapod] = {
    log.debug("get recipients for message " + message)
    /*
     * add 1st recipient if it is exists and has suitable endpoints
     */
    var recipients = message.destinationHexapod.flatMap(Mesh(_).map {
      hexapod =>
        /*
         * 1. for all endpoints with Out/InOut of local hexapod do 'exists'
         *  2. in all endpoints with In/InOut of remote hexapod do 'exists'
         *   3. suitable
         */
        val available = Hexapod.getEndpoints.filter(ep => ep.direction == Endpoint.Direction.Out ||
          ep.direction == Endpoint.Direction.InOut).exists(lEndpoint =>
          hexapod.getEndpoints.filter(ep => ep.direction == Endpoint.Direction.In ||
            ep.direction == Endpoint.Direction.InOut).exists(rEndpoint => lEndpoint.suitable(rEndpoint)))
        if (available) {
          log.debug("add hexapod %s to recipient list")
          Seq(hexapod)
        } else {
          log.debug("skip hexapod %s for recipient list, no suitable endpoints")
          Seq()
        }
    }) getOrElse Seq()
    /*
     * add redundant recipients from Peer.pool if needed
     */
    recipients
  }
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
    val fromHexapod: Hexapod = Hexapod(fromHexapodUUID)
    val decryptedBody = if (messageType == Message.Type.Unencrypted.id) {
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
          log.error("unable to decrypt: %s<->%s session key not found".format(fromHexapod, Hexapod.inner))
          return None
      }
    }
    val (toHexapod, conversation, timestamp, word, distance, content) = parseRawMessageBody(decryptedBody)
    assert(fromHexapodUUID != Hexapod.uuid, "illegal message \"%s\" from AppHexapod".format(word))
    if (sendAcknowledgementIfSuccess)
      Communication.push(Acknowledgement(conversation.hashCode(), Some(fromHexapodUUID)))
    if (distance == 127) {
      log.warn("drop message %s, maximum distance riched")
      return None
    }
    factory.get(word) match {
      case Some(factory) =>
        factory.build(fromHexapod, toHexapod, conversation, timestamp, word, (distance + 1).toByte, content)
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
    val toHexapod: Hexapod = Hexapod(toHexapodUUID)
    val conversationUUID = new UUID(conversationMSB, conversationLSB)
    (toHexapod, conversationUUID, creationTimestamp, word, distance, content)
  }
  /*
   * dependency injection
   */
  override def afterInjection(newModule: BindingModule) {
    factory = inject[Seq[Factory]].map(factory => factory.word -> factory).toMap
    factory.values.foreach(Communication.registerGlobal)
  }
  override def onClearInjection(oldModule: BindingModule) {
    factory.values.foreach(Communication.unregisterGlobal)
  }

  /**
   * marker with message group
   */
  object Type extends Enumeration {
    val Acknowledgement = Value(0)
    val Unencrypted = Value(1)
    val Standard = Value(2)
  }
  trait Factory extends Receptor {
    val word: String
    /** recreate message from various parameters */
    def build(from: Hexapod, to: Hexapod, conversation: UUID, timestamp: Long, word: String, distance: Byte, content: Array[Byte]): Option[Message]
  }
}


