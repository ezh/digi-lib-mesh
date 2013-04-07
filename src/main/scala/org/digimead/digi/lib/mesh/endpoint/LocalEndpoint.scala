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

package org.digimead.digi.lib.mesh.endpoint

import java.util.UUID

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.NDC
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.message.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class LocalEndpoint(
  val parent: WeakReference[Hexapod],
  val direction: Endpoint.Direction,
  val nature: LocalEndpoint.Nature,
  override val initialPriority: Int)
  extends Endpoint[LocalEndpoint.Nature] {
  def this(direction: Endpoint.Direction, nature: LocalEndpoint.Nature, initialPriority: Int)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, initialPriority)
  def this(direction: Endpoint.Direction, nature: LocalEndpoint.Nature)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, Endpoint.Priority.HIGH.id)
  def this(direction: Endpoint.Direction, initialPriority: Int)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, new LocalEndpoint.Nature(UUID.randomUUID), Endpoint.Priority.HIGH.id)
  def this(direction: Endpoint.Direction)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, new LocalEndpoint.Nature(UUID.randomUUID), Endpoint.Priority.HIGH.id)
  @volatile var destination: Option[LocalEndpoint] = None
  log.debug("%s %s".format(this, nature.address))

  def connect(): Boolean = {
    log.debug("initiate fake connection sequence for " + this)
    connectionActive = true
    publish(Endpoint.Event.Connect(this))
    true
  }
  def disconnect(): Boolean = {
    log.debug("initiate fake disconnection sequence for " + this)
    connectionActive = false
    publish(Endpoint.Event.Disconnect(this))
    true
  }
  def loopbackConnect(endpoint: LocalEndpoint) = {
    log.debug("connect %s to %s".format(this, endpoint))
    destination = Some(endpoint)
  }
  @log
  def receive(message: Array[Byte]) = try {
    Message.parseRawMessage(message, true) match {
      case Some(message) =>
        log.debug("receive message \"%s\" from %s".format(message.word, message.sourceHexapod))
        message.destinationHexapod.flatMap(Mesh(_)) match {
          case Some(hexapod: AppHexapod) =>
            NDC.push("R_" + hexapod.toString)
            hexapod.receive(message)
            NDC.pop
            true
          case Some(hexapod) =>
            log.fatal("broken hexapod " + hexapod)
            false
          case None =>
            log.fatal("lost destination hexapod")
            false
        }
      case None =>
        false
    }
  } catch {
    case e: Throwable =>
      log.error(e.getMessage())
      false
  }
  protected def send(message: Message, key: Option[Array[Byte]], remoteEndpoint: Endpoint[LocalEndpoint.Nature]): Boolean = false
  private def getDestination(message: Message): Option[Hexapod] =
    message.destinationHexapod match {
      case Some(hexapodUUID) =>
        this.destination match {
          case Some(dep) =>
            if (dep.parent.get.exists(_.uuid == hexapodUUID)) {
              Mesh(hexapodUUID) match {
                case Some(entity: Hexapod) =>
                  Some(entity)
                case entity =>
                  log.fatal("broken reference " + entity + " for uuid " + hexapodUUID)
                  None
              }
            } else {
              log.debug("unable to send message to %s via %s".format(hexapodUUID, dep.parent.get))
              None
            }
          case None =>
            log.info("destination hexapod not found")
            None
        }
      case None =>
        this.destination.flatMap(_.parent.get)
    }
  override def toString = "LoopbackEndpoint[%08X/%08X/%s]".format(parent.get.map(_.hashCode).getOrElse(0), nature.addr.hashCode(), direction)
}

object LocalEndpoint extends Endpoint.Factory with Loggable {
  val protocol = "local"
  private val localEndpoints = new HashMap[UUID, LocalEndpoint] with SynchronizedMap[UUID, LocalEndpoint]

  def fromSignature(hexapod: Hexapod, signature: String): Option[LocalEndpoint] = signature.split("'") match {
    case Array(protocol, address, actualPriority, Endpoint.Direction(direction), options @ _*) =>
      try {
        Some(new LocalEndpoint(new WeakReference(hexapod), direction.reverse, new Nature(UUID.fromString(address)), actualPriority.toInt))
      } catch {
        case e: Throwable =>
          log.error(e.getMessage(), e)
          None
      }
    case _ =>
      log.error("incorrect signature " + signature)
      None
  }

  class Nature(val addr: UUID) extends Endpoint.Nature {
    val protocol = LocalEndpoint.protocol
    override def address() = addr.toString()
  }
}
