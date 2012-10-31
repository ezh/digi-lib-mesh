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
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import scala.math.BigInt.int2bigInt

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.DependencyInjection.PersistentInjectable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.enc.DiffieHellman
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.message.Message

class Hexapod private (val uuid: UUID) extends Hexapod.Pub with Loggable {
  /** Hexapod endpoints */
  @volatile protected var endpoint = Seq[Endpoint[_ <: Endpoint.Nature]]()
  @volatile protected var authDiffieHellman: Option[DiffieHellman] = None
  protected val peerSessionKey = new WeakHashMap[Hexapod, (BigInt, Array[Byte])] with SynchronizedMap[Hexapod, (BigInt, Array[Byte])]
  log.debug("alive %s %s".format(this, uuid))
  Mesh.register(this)

  def registerEndpoint(endpoint: Endpoint[_ <: Endpoint.Nature]) {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    this.endpoint = this.endpoint :+ endpoint
  }
  def getEndpoints(): Seq[Endpoint[_ <: Endpoint.Nature]] = endpoint
  @log
  def getDiffieHellman() = authDiffieHellman
  @log
  def setDiffieHellman(g: Int, p: BigInt, publicKey: BigInt, secretKey: BigInt = 0): DiffieHellman = {
    val dh = new DiffieHellman(g, p, secretKey, publicKey)
    authDiffieHellman = Some(dh)
    publish(Hexapod.Event.SetDiffieHellman(this))
    dh
  }
  @log
  def getKeyForHexapod(peerHexapod: Hexapod): Option[(BigInt, Array[Byte])] = {
    log.debug("search key %s <-> %s ".format(this, peerHexapod))
    peerSessionKey.get(peerHexapod) match {
      case result: Some[(BigInt, Array[Byte])] => result
      case None =>
        for {
          localDiffieHellman <- getDiffieHellman
          remoteDiffieHellman <- peerHexapod.getDiffieHellman
        } yield {
          val sharedKey = localDiffieHellman.getSharedKey(remoteDiffieHellman.publicKey)
          val rawKey = Simple.getRawKey(sharedKey.toByteArray)
          peerSessionKey(peerHexapod) = (sharedKey, rawKey)
          (sharedKey, rawKey)
        }
    }
  }
  def getLinks(hexapod: Hexapod, filterConnected: Boolean = true): Seq[Hexapod.Link] = {
    if (hexapod == this) {
      Seq()
    } else {
      endpoint.filter(_.connected || !filterConnected).map(lEndpoint => Hexapod.Link(lEndpoint, Seq()))
    }
  }
  /*.exists(lEndpoint =>
      hexapod.getEndpoints.filter(ep => directionFilter.map(_.reverse).exists(_ == ep.direction)).
        exists(rEndpoint => lEndpoint.suitable(rEndpoint)))*/
  override def equals(that: Any): Boolean =
    that.isInstanceOf[Hexapod] && (this.hashCode() == that.asInstanceOf[Hexapod].hashCode())
  override protected def publish(event: Hexapod.Event) = try {
    super.publish(event)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
  }
  override def toString = "Hexapod[%08X]".format(uuid.hashCode())
}

object Hexapod extends PersistentInjectable with Loggable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def hexapod2app(h: Hexapod.type): AppHexapod = h.applicationHexapod
  implicit def bindingModule = DependencyInjection()
  @volatile private var applicationHexapod = inject[AppHexapod]
  Endpoint // start initialization if needed

  def apply(uuid: UUID): Hexapod = Mesh(uuid) getOrElse { new Hexapod(uuid) }
  def inner() = applicationHexapod
  def commitInjection() {}
  def updateInjection() { applicationHexapod = inject[AppHexapod] }
  def recreateFromSignature(): Option[Hexapod] = {
    None
  }

  case class Link(localEndpoint: Endpoint[_ <: Endpoint.Nature], remoteEndpoints: Seq[Endpoint[_ <: Endpoint.Nature]])
  abstract class AppHexapod(override val uuid: UUID) extends Hexapod(uuid) with Loggable {
    def connect(): Boolean
    def disconnect(): Boolean
    def receive(message: Message)
    def reconnect(): Boolean
    def connected(): Boolean
  }
  sealed trait Event
  object Event {
    case class Connect(val endpoint: Endpoint[_ <: Endpoint.Nature]) extends Event
    case class Disconnect(val endpoint: Endpoint[_ <: Endpoint.Nature]) extends Event
    case class SetDiffieHellman(val hexapod: Hexapod) extends Event
  }
}
