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

import scala.Option.option2Iterable
import scala.ref.WeakReference

import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod

class UDPRemoteEndpoint(
  override val transportIdentifier: UDPEndpoint.TransportIdentifier,
  override val hexapod: WeakReference[AppHexapod],
  override val direction: Endpoint.Direction)
  extends UDPEndpoint(transportIdentifier, hexapod, direction) {
  assert(transportIdentifier.addr.nonEmpty && transportIdentifier.port.nonEmpty, "UDPRemoteEndpoint transportIdentifier incomlete: address %s / port %s".
    format(transportIdentifier.addr, transportIdentifier.port))

  override def send(message: Message, key: Option[BigInt]): Option[Endpoint] = throw new UnsupportedOperationException
  override def receive(message: Array[Byte]) = throw new UnsupportedOperationException
  override def connect(): Boolean = throw new UnsupportedOperationException
  override def reconnect() = throw new UnsupportedOperationException
  override def disconnect() = throw new UnsupportedOperationException
  override def toString = "UDPRemoteEndpoint[%08X/%s]".format(hexapod.get.map(_.hashCode).getOrElse(0), direction)
}
