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
import scala.collection.mutable.Subscriber

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.auth.DiffieHellman
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Entity
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.endpoint.AbstractEndpoint
import org.digimead.digi.lib.mesh.endpoint.Endpoint

class Hexapod(val uuid: UUID) extends Entity with Hexapod.Pub with Logging {
  /** Hexapod endpoints */
  @volatile protected var endpoint = Seq[Endpoint]()
  @volatile protected var authSessionKey: Option[BigInt] = None
  @volatile protected var authDiffieHellman: Option[DiffieHellman] = None
  log.debug("alive %s %s".format(this, uuid))

  override protected def publish(event: Hexapod.Event) = try {
    super.publish(event)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
  }
  def registerEndpoint(endpoint: Endpoint) {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    this.endpoint = this.endpoint :+ endpoint
  }
  def getEndpoints() = endpoint
  @Loggable
  def updateAuth(publicKey: BigInt) {
    authDiffieHellman.foreach {
      dh =>
        log.debug("update authentication parameters")
        dh.setPeerPublicKey(publicKey)
        authSessionKey = Some(dh.createSharedKey)
        publish(Hexapod.Event.UpdateAuth(this))
    }
  }
  @Loggable
  def setDiffieHellman(publicKey: BigInt, g: Int, p: BigInt): DiffieHellman = {
    val dh = new DiffieHellman(g, p)
    dh.createSecretKey()
    authDiffieHellman = Some(dh)
    updateAuth(publicKey)
    dh
  }
  override def toString = "Hexapod[%08X]".format(this.hashCode())
}

object Hexapod extends Logging {
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def hexapod2app(h: Hexapod.type): AppHexapod = h.applicationHexapod
  private var applicationHexapod: AppHexapod = null

  def init(arg: AppHexapod): Unit = synchronized {
    assert(!isInitialized, "Hexapod is already initialized")
    assert(Mesh.isInitialized, "Mesh not initialized")
    assert(Peer.isInitialized, "Peer not initialized")
    log.debug("initialize application hexapod with " + arg)
    applicationHexapod = arg
  }
  def isInitialized(): Boolean = applicationHexapod != null

  abstract class AppHexapod(override val uuid: UUID) extends Hexapod(uuid) with AbstractEndpoint with Logging {
    def receive(message: Message)
    def connected(): Boolean
  }

  sealed trait Event
  object Event {
    case class Connect(val endpoint: Endpoint) extends Event
    case class Disconnect(val endpoint: Endpoint) extends Event
    case class UpdateAuth(val hexapod: Hexapod) extends Event
  }
}
