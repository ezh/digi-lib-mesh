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

import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.NDC
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.CommunicationEvent
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class LoopbackEndpoint(
  override val uuid: UUID,
  override val userIdentifier: String,
  override val deviceIdentifier: String,
  override val transportIdentifier: Endpoint.TransportIdentifier,
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
    val rawMessage = message.createRawMessage(hexapod, destinationHexapod, this, key)
    val sub = new Communication.Sub {
      def notify(pub: Communication.Pub, event: CommunicationEvent) = event match {
        case Communication.Event.Active(passed_message) if passed_message == message =>
          Communication.removeSubscription(this)
          destination.foreach(_.receive(rawMessage))
        case _ =>
      }
    }
    Communication.subscribe(sub)
    this
  }
  def receive(message: Array[Byte]) = try {
    Message.parseRawMessage(message, this) match {
      case Some((message, remoteEndpointUUID)) =>
        log.debug("receive message \"%s\" from %s via remote endpoint %s".format(message.word, message.sourceHexapod, remoteEndpointUUID))
        message.destinationHexapod.flatMap(Mesh(_)) match {
          case Some(hexapod: AppHexapod) =>
            NDC.push("R_" + hexapod.toString)
            hexapod.receive(message)
            NDC.pop
          case Some(hexapod) =>
            log.fatal("broken hexapod " + hexapod)
          case None =>
            log.fatal("lost destination hexapod")
        }
      case None =>
    }
  } catch {
    case e =>
      log.error(e.getMessage())
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
  override def toString = "LoopbackEndpoint[%08X/%08X/%s]".format(hexapod.get.map(_.hashCode).getOrElse(0), uuid.hashCode(), direction)
}
