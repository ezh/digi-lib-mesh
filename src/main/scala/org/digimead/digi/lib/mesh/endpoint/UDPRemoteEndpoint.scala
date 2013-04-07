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

import java.net.InetAddress

import scala.Option.option2Iterable
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.message.Message
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class UDPRemoteEndpoint(
  val parent: WeakReference[Hexapod],
  val direction: Endpoint.Direction,
  val nature: UDPRemoteEndpoint.Nature,
  override val initialPriority: Int = Endpoint.Priority.LOW.id)
  extends Endpoint[UDPRemoteEndpoint.Nature] {
  def this(direction: Endpoint.Direction, nature: UDPEndpoint.Nature, initialPriority: Int)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, initialPriority)
  def this(direction: Endpoint.Direction, nature: UDPEndpoint.Nature)(implicit parent: Hexapod) =
    this(new WeakReference(parent), direction, nature, Endpoint.Priority.HIGH.id)
  assert(nature.addr.nonEmpty && nature.port.nonEmpty,
    "UDPRemoteEndpoint transportIdentifier incomlete: address %s / port %s".format(nature.addr, nature.port))

  def send(message: Message): Option[Endpoint.Nature] = throw new UnsupportedOperationException
  override def receive(message: Array[Byte]) = throw new UnsupportedOperationException
  override def connect(): Boolean = throw new UnsupportedOperationException
  override def connected(): Boolean = true
  override def disconnect(): Boolean = throw new UnsupportedOperationException
  override def toString = "UDPRemoteEndpoint[%08X/%s]".format(parent.get.map(_.hashCode).getOrElse(0), direction)
  /** return signature with reverse direction, as original udp endpoint is */
  override def signature(): String = Seq(nature.protocol, nature.address, actualPriority, direction.reverse, options).mkString("'")
  protected def send(message: Message, key: Option[Array[Byte]], remoteEndpoint: Endpoint[UDPRemoteEndpoint.Nature]): Boolean = false
}

object UDPRemoteEndpoint extends Endpoint.Factory with Loggable {
  val protocol = "udp"

  def fromSignature(hexapod: Hexapod, signature: String): Option[UDPRemoteEndpoint] = signature.split("'") match {
    case Array(protocol, address, priority, Endpoint.Direction(direction), options @ _*) =>
      val (ip, port) = address.split(":") match {
        case Array(address, port @ _*) if address.nonEmpty =>
          try {
            (Some(InetAddress.getByName(address)), port.headOption.map(_.toInt))
          } catch {
            case e: Throwable =>
              log.error("incorrect endpoint address \"%s\" at signature %s".format(address, signature))
              return None
          }
        case address =>
          log.error("incorrect endpoint address \"%s\" at signature %s".format(address, signature))
          return None
      }
      Some(new UDPRemoteEndpoint(new WeakReference(hexapod), direction.reverse, new Nature(ip, port)))
    case _ =>
      log.error("incorrect signature " + signature)
      None
  }

  class Nature(val addr: Option[InetAddress] = None, val port: Option[Int] = None)
    extends Endpoint.Nature {
    val protocol = UDPRemoteEndpoint.protocol
    override def address() = "%s:%s".format(addr.map(_.getHostAddress()).getOrElse(""), port.getOrElse(""))
  }
}
