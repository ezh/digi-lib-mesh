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

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.UUID

import scala.Option.option2Iterable
import scala.annotation.tailrec
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.NDC
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class UDPEndpoint(
  override val uuid: UUID,
  override val userIdentifier: String,
  override val deviceIdentifier: String,
  override val transportIdentifier: UDPEndpoint.TransportIdentifier,
  override val hexapod: WeakReference[AppHexapod],
  override val direction: Endpoint.Direction)
  extends Endpoint(uuid, userIdentifier, deviceIdentifier, transportIdentifier, hexapod, direction) with Logging {
  log.debug("%s ids are %s [%s] [%s] [%s]".format(this, uuid, userIdentifier, deviceIdentifier, transportIdentifier))
  /** listen interface address, port */
  @volatile protected var socket: Option[DatagramSocket] = None
  @volatile protected var packet: Option[DatagramPacket] = None
  @volatile protected var serverThread: Option[Thread] = None
  protected val buffer = new Array[Byte](4096)

  def send(message: Message, key: Option[BigInt]): Option[Endpoint] = for {
    destinationHexapod <- findDestination(message)
    hexapod <- hexapod.get
  } yield {
    this
  }
  @Loggable
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
  @Loggable
  def connect(): Boolean = synchronized {
    socket = transportIdentifier match {
      case UDPEndpoint.TransportIdentifier(Some(bindaddr), Some(port)) =>
        Some(new DatagramSocket(port, bindaddr))
      case UDPEndpoint.TransportIdentifier(None, Some(port)) =>
        Some(new DatagramSocket(port))
      case UDPEndpoint.TransportIdentifier(_, None) =>
        None
    }
    packet = socket.map(_ => new DatagramPacket(buffer, buffer.length))
    serverThread = for {
      socket <- socket
      packet <- packet
    } yield {
      assert(direction == Endpoint.In || direction == Endpoint.InOut, "illegal server for Endpoint.Out direction")
      val thread = new Thread("UDPEndpoint server at %s:%s".format(transportIdentifier.addr.getOrElse("0.0.0.0"), transportIdentifier.port.get)) {
        log.info("bind %s to %s:%s".format(UDPEndpoint.this, transportIdentifier.addr.getOrElse("0.0.0.0"), transportIdentifier.port.get))
        this.setDaemon(true)
        @tailrec
        override def run() = {
          if (UDPEndpoint.this.serverThread.nonEmpty) {
            socket.receive(packet)
            log.debug("received packet from: " + packet.getAddress())
            run
          }
        }
      }
      thread.start
      thread
    }
    connected = true
    publish(Endpoint.Event.Connect(this))
    true
  }
  @Loggable
  def reconnect() {}
  @Loggable
  def disconnect() = synchronized {
    serverThread = None
    socket.map(_.close())
    connected = false
    publish(Endpoint.Event.Disconnect(this))
  }
  private def findDestination(message: Message): Option[Hexapod] =
    message.destinationHexapod match {
      case Some(hexapodUUID) =>
        None
      case None =>
        None
    }
  override def toString = "UDPEndpoint[%08X/%08X/%s]".format(hexapod.get.map(_.hashCode).getOrElse(0), uuid.hashCode(), direction)
}

object UDPEndpoint {
  case class TransportIdentifier(addr: Option[InetAddress] = None, port: Option[Int] = None) extends Endpoint.TransportIdentifier
}
