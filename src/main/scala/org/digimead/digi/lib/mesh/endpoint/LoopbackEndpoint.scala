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

package org.digimead.digi.lib.mesh.endpoint

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

import scala.Option.option2Iterable
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class LoopbackEndpoint(
  override val uuid: UUID,
  override val userIdentifier: String,
  override val deviceIdentifier: String,
  override val transportIdentifier: Endpoint.TransportIdentifiers,
  override val hexapod: WeakReference[AppHexapod],
  override val direction: Endpoint.Direction)
  extends Endpoint(uuid, userIdentifier, deviceIdentifier, transportIdentifier, hexapod, direction) with Logging {
  log.debug("%s ids are %s [%s] [%s] [%s]".format(this, uuid, userIdentifier, deviceIdentifier, transportIdentifier))
  @volatile var destination: Option[LoopbackEndpoint] = None

  def loopbackConnect(endpoint: LoopbackEndpoint) = {
    log.debug("connect %s to %s".format(this, endpoint))
    destination = Some(endpoint)
  }
  def send(message: Message, key: Option[BigInt]): Option[Endpoint] = for {
    destinationHexapod <- findDestination(message)
    hexapod <- hexapod.get
  } yield {
    log.debug("send message %s to %s via %s".format(message, destinationHexapod, this))
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    val content = key match {
      case Some(key) =>
        null // encript message.content
      case None =>
        message.content
    }
    w.writeBoolean(key.nonEmpty)
    w.writeLong(hexapod.uuid.getLeastSignificantBits())
    w.writeLong(hexapod.uuid.getMostSignificantBits())
    w.writeLong(uuid.getLeastSignificantBits())
    w.writeLong(uuid.getMostSignificantBits())
    w.writeLong(destinationHexapod.uuid.getLeastSignificantBits())
    w.writeLong(destinationHexapod.uuid.getMostSignificantBits())
    w.writeLong(message.conversation.getLeastSignificantBits())
    w.writeLong(message.conversation.getMostSignificantBits())
    w.writeLong(message.timestamp)
    w.writeInt(content.length)
    w.writeUTF(message.word)
    w.write(content, 0, content.length)
    w.flush()
    val data = baos.toByteArray()
    w.close()
    destination.foreach(_.receive(data))
    this
  }
  def receive(message: Array[Byte]) = {
    try {
      val bais = new ByteArrayInputStream(message)
      val r = new DataInputStream(bais)
      val encrypted = r.readBoolean()
      val fromHexapodLSB = r.readLong()
      val fromHexapodMSB = r.readLong()
      val fromEndpointLSB = r.readLong()
      val fromEndpointMSB = r.readLong()
      val toHexapodLSB = r.readLong()
      val toHexapodMSB = r.readLong()
      val conversationLSB = r.readLong()
      val conversationMSB = r.readLong()
      val creationTimestamp = r.readLong()
      val contentLength = r.readInt()
      val word = r.readUTF()
      val content = new Array[Byte](contentLength)
      val actualContentLength = r.read(content, 0, contentLength)
      assert(actualContentLength == contentLength)
      val fromHexapodUUID = new UUID(fromHexapodMSB, fromHexapodLSB)
      val fromHexapod: Hexapod = Mesh(fromHexapodUUID) match {
        case Some(hexapod: Hexapod) => hexapod
        case _ => new Hexapod(fromHexapodUUID)
      }
      val fromEndpointUUID = new UUID(fromEndpointMSB, fromEndpointLSB)
      val toHexapodUUID = new UUID(toHexapodMSB, toHexapodLSB)
      val toHexapod: Hexapod = Mesh(toHexapodUUID) match {
        case Some(hexapod: Hexapod) => hexapod
        case _ => new Hexapod(toHexapodUUID)
      }
      val conversationUUID = new UUID(conversationMSB, conversationLSB)
      log.debug("receive message \"%s\" from %s via remote endpoint %s".format(word, fromHexapod, fromEndpointUUID))
      if (encrypted) {
        // decrypt
        val decrypted = content
        this.hexapod.get.foreach(_.receive(fromHexapod, toHexapod, this, word, decrypted, conversationUUID, creationTimestamp))
      } else
        this.hexapod.get.foreach(_.receive(fromHexapod, toHexapod, this, word, content, conversationUUID, creationTimestamp))
    } catch {
      case e =>
        log.error(e.getMessage())
    }
  }
  def connect(): Boolean = {
    log.debug("initiate fake connection sequence for " + this)
    connected = true
    publish(Endpoint.Event.Connect(this))
    true
  }
  def reconnect() {}
  def disconnect() {
    log.debug("initiate fake disconnection sequence for " + this)
    connected = false
    publish(Endpoint.Event.Disconnect(this))
  }
  private def findDestination(message: Message): Option[Hexapod] =
    message.destinationHexapod match {
      case Some(hexapodUUID) =>
        this.destination match {
          case Some(dep) =>
            if (dep.hexapod.get.exists(_.uuid == hexapodUUID)) {
              Mesh(hexapodUUID) match {
                case Some(entity: Hexapod) =>
                  Some(entity)
                case entity =>
                  log.fatal("broken reference " + entity + " for uuid " + hexapodUUID)
                  None
              }
            } else {
              log.debug("unable to send message to %s via %s".format(hexapodUUID, dep.hexapod.get))
              None
            }
          case None =>
            log.info("destination hexapod not found")
            None
        }
      case None =>
        this.destination.flatMap(_.hexapod.get)
    }
  override def toString = "Loopback[%08X/%08X/%s]".format(hexapod.get.map(_.hashCode).getOrElse("unknown"), uuid.hashCode(), direction)
}
