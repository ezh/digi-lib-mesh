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

package org.digimead.digi.lib.mesh.hexapod

import java.util.UUID

import scala.collection.mutable.Publisher

import org.digimead.digi.lib.auth.DiffieHellman
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Entity
import org.digimead.digi.lib.mesh.Hub
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.endpoint.AbstractEndpoint
import org.digimead.digi.lib.mesh.endpoint.Endpoint

class Hexapod(val uuid: UUID) extends Entity with Logging {
  @volatile protected var authSessionKey: Option[BigInt] = None
  @volatile protected var authDiffieHellman: Option[DiffieHellman] = None
  log.debug("alive %s %s".format(this, uuid))

  override def toString = "Hexapod[%08X]".format(this.hashCode())
}

object Hexapod extends Logging {
  implicit def hexapod2app(h: Hexapod.type): AppHexapod = h.applicationHexapod
  private var applicationHexapod: AppHexapod = null

  def init(arg: AppHexapod): Unit = synchronized {
    assert(!isInitialized, "Hexapod is already initialized")
    assert(Mesh.isInitialized, "Mesh not initialized")
    assert(Hub.isInitialized, "Hub not initialized")
    assert(Communication.isInitialized, "Communication not initialized")
    log.debug("initialize application hexapod with " + arg)
    applicationHexapod = arg
  }
  def isInitialized(): Boolean = applicationHexapod != null
  def setDiffieHellman(hexapod: Hexapod, dh: Option[DiffieHellman]) {
    hexapod.log.debug("set DiffieHellman parameter for " + hexapod)
    hexapod.authDiffieHellman = dh
  }
  def setSessionKey(hexapod: Hexapod, key: Option[BigInt]) {
    hexapod.log.debug("set session key parameter for " + hexapod)
    hexapod.authSessionKey = key
  }
  abstract class AppHexapod(override val uuid: UUID) extends Hexapod(uuid) with AbstractEndpoint with Logging {
    /** Hexapod endpoints */
    protected var endpoint: Seq[Endpoint]

    def registerEndpoint(endpoint: Endpoint)
    def receive(message: Message)
    def connected(): Boolean
  }

  sealed trait Event
  object Event extends Publisher[Event] {
    override protected[hexapod] def publish(event: Event) = super.publish(event)

    case class Connect(val endpoints: Endpoint) extends Event
    case class Disconnect(val endpoints: Endpoint) extends Event
  }
}
