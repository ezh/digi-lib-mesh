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

import scala.Option.option2Iterable
import scala.annotation.tailrec
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.NDC
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.Acknowledgement

class UDPEndpoint(
  override val transportIdentifier: UDPEndpoint.TransportIdentifier,
  override val hexapod: WeakReference[Hexapod],
  override val direction: Endpoint.Direction)
  extends Endpoint(transportIdentifier, hexapod, direction) with Logging {
  log.debug("%s %s".format(this, transportIdentifier))
  /** listen interface address, port */
  @volatile protected var receiveSocket: Option[DatagramSocket] = None
  protected val sendSocket = new DatagramSocket()
  @volatile protected var packet: Option[DatagramPacket] = None
  @volatile protected var serverThread: Option[Thread] = None
  protected val buffer = new Array[Byte](4096)

  def send(message: Message, key: Option[BigInt]): Option[Endpoint] = (for {
    remoteEndpoint <- findDestinationEndpoint(message)
    hexapod <- hexapod.get
  } yield remoteEndpoint match {
    case endpoint: UDPEndpoint =>
      for {
        addr <- endpoint.transportIdentifier.addr
        port <- endpoint.transportIdentifier.port
        destinationHexapod <- endpoint.hexapod.get
      } yield {
        log.debug("send message %s via %s to %s:%s".format(message, endpoint, addr, port))
        val rawMessage = message.createRawMessage(hexapod, destinationHexapod, key)
        val data = new DatagramPacket(rawMessage, 0, rawMessage.length, addr, port)
        sendSocket.send(data)
        this
      }
    case error =>
      log.fatal("unexpected endpoint type: " + error)
      None
  }) getOrElse {
    log.debug("unable to send: suitable remote endpoint not found")
    None
  }
  @Loggable
  def receive(message: Array[Byte]) = try {
    Message.parseRawMessage(message) match {
      case Some(message: Acknowledgement) =>
        log.debug("receive message \"%s\" from %s with conversation hash %d".format(message.word, message.sourceHexapod, message.conversationHash))
        Communication.acknowledge(message.conversationHash)
      case Some(message) =>
        log.debug("receive message \"%s\" from %s".format(message.word, message.sourceHexapod))
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
    receiveSocket.map(_.close())
    receiveSocket = transportIdentifier match {
      case UDPEndpoint.TransportIdentifier(Some(bindaddr), Some(port)) =>
        Some(new DatagramSocket(port, bindaddr))
      case UDPEndpoint.TransportIdentifier(None, Some(port)) =>
        Some(new DatagramSocket(port))
      case UDPEndpoint.TransportIdentifier(_, None) =>
        None
    }
    packet = receiveSocket.map(_ => new DatagramPacket(buffer, buffer.length))
    serverThread = for {
      receiveSocket <- receiveSocket
      packet <- packet
    } yield {
      assert(direction == Endpoint.In || direction == Endpoint.InOut, "illegal server for Endpoint.Out direction")
      val thread = new Thread("UDPEndpoint server at %s:%s".format(transportIdentifier.addr.getOrElse("0.0.0.0"), transportIdentifier.port.get)) {
        log.info("bind %s to %s:%s".format(UDPEndpoint.this, transportIdentifier.addr.getOrElse("0.0.0.0"), transportIdentifier.port.get))
        this.setDaemon(true)
        @tailrec
        override def run() = {
          if (UDPEndpoint.this.serverThread.nonEmpty) {
            receiveSocket.receive(packet)
            log.debug("received packet from %s %db".format(packet.getAddress().getHostAddress(), packet.getLength()))
            try {
              receive(packet.getData())
            } catch {
              case e =>
                log.error(e.getMessage, e)
            }
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
    receiveSocket.map(_.close())
    connected = false
    publish(Endpoint.Event.Disconnect(this))
  }
  override def toString = "UDPEndpoint[%08X/%s]".format(hexapod.get.map(_.hashCode).getOrElse(0), direction)
}

object UDPEndpoint {
  case class TransportIdentifier(addr: Option[InetAddress] = None, port: Option[Int] = None) extends Endpoint.TransportIdentifier
}
