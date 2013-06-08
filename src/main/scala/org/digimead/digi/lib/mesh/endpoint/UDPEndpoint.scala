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

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import scala.Option.option2Iterable
import scala.annotation.tailrec
import scala.ref.WeakReference
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.log.NDC
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.message.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.Acknowledgement
import java.net.SocketException
import org.digimead.digi.lib.aop.log

class UDPEndpoint(
  val parent: WeakReference[Hexapod],
  val direction: Endpoint.Direction,
  val nature: UDPEndpoint.Nature,
  override val initialPriority: Int = Endpoint.Priority.LOW.id)
  extends Endpoint[UDPEndpoint.Nature] {
  def this(direction: Endpoint.Direction, nature: UDPEndpoint.Nature, initialPriority: Int)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, initialPriority)
  def this(direction: Endpoint.Direction, nature: UDPEndpoint.Nature)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, Endpoint.Priority.HIGH.id)
  val protocol = "udp"
  log.debug("%s %s".format(this, nature))
  /** listen interface address, port */
  @volatile protected var receiveSocket: Option[DatagramSocket] = None
  protected val sendSocket = new DatagramSocket()
  @volatile protected var packet: Option[DatagramPacket] = None
  @volatile protected var serverThread: Option[Thread] = None
  protected val buffer = new Array[Byte](4096)

  @log
  def connect(): Boolean = synchronized {
    receiveSocket.map(_.close())
    receiveSocket = nature match {
      case UDPEndpoint.Nature(Some(bindaddr), Some(port)) =>
        Some(new DatagramSocket(port, bindaddr))
      case UDPEndpoint.Nature(None, Some(port)) =>
        Some(new DatagramSocket(port))
      case UDPEndpoint.Nature(_, None) =>
        None
    }
    packet = receiveSocket.map(_ => new DatagramPacket(buffer, buffer.length))
    serverThread = for {
      receiveSocket <- receiveSocket
      packet <- packet
    } yield {
      assert(direction == Endpoint.Direction.In || direction == Endpoint.Direction.InOut, "illegal server for Endpoint.Direction.Out direction")
      val thread = new Thread("UDPEndpoint server at %s:%s".format(nature.addr.getOrElse("0.0.0.0"), nature.port.get)) {
        log.info("bind %s to %s:%s".format(UDPEndpoint.this, nature.addr.getOrElse("0.0.0.0"), nature.port.get))
        this.setDaemon(true)
        @tailrec
        override def run() = {
          if (UDPEndpoint.this.serverThread.nonEmpty) {
            try {
              receiveSocket.receive(packet)
              log.debug("received packet from %s %db".format(packet.getAddress().getHostAddress(), packet.getLength()))
              try {
                receive(packet.getData())
              } catch {
                case e: Throwable =>
                  log.error(e.getMessage, e)
              }
            } catch {
              case e: SocketException if e.getMessage == "Socket closed" =>
                log.debug("socket closed")
              case e: Throwable =>
                log.error(e.getMessage, e)
                throw e
            }
            if (!receiveSocket.isClosed())
              run
          }
        }
      }
      thread.start
      thread
    }
    connectionActive = true
    publish(Endpoint.Event.Connect(this))
    true
  }
  @log
  def disconnect() = synchronized {
    serverThread = None
    receiveSocket.map(_.close())
    connectionActive = false
    publish(Endpoint.Event.Disconnect(this))
    true
  }
  @log
  def receive(message: Array[Byte]) = try {
    Message.parseRawMessage(message, true) match {
      case Some(message: Acknowledgement) =>
        log.debug("receive message \"%s\" from %s with conversation hash %08X".format(message.word, message.sourceHexapod, message.conversationHash))
        Communication.acknowledge(message.conversationHash)
        true
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
  @log
  protected def send(message: Message, key: Option[Array[Byte]], remoteEndpoint: Endpoint[UDPEndpoint.Nature]): Boolean = false
  override def toString = "UDPEndpoint[%08X/%s]".format(parent.get.map(_.hashCode).getOrElse(0), direction)
}

object UDPEndpoint {
  class Nature(override val addr: Option[InetAddress] = None,
    override val port: Option[Int] = None) extends UDPRemoteEndpoint.Nature(addr, port)
  object Nature {
    def unapply(nature: Nature) = Some(nature.addr, nature.port)
  }
}
