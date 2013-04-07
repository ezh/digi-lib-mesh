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

package org.digimead.digi.lib.mesh.hexapod

import java.util.UUID

import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import scala.math.BigInt.int2bigInt

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.enc.DiffieHellman
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.endpoint.Endpoint

import com.escalatesoft.subcut.inject.BindingModule

import language.implicitConversions

class Hexapod private[hexapod] (val uuid: UUID) extends Hexapod.Pub with Loggable {
  /** Hexapod endpoints, head/best, last/worst */
  @volatile protected var endpoint = Seq[Endpoint[_ <: Endpoint.Nature]]()
  @volatile protected var authDiffieHellman: Option[DiffieHellman] = None
  protected val peerSessionKey = new WeakHashMap[Hexapod, (BigInt, Array[Byte])] with SynchronizedMap[Hexapod, (BigInt, Array[Byte])]
  protected val endpointSubscriber = new Endpoint.Sub {
    def notify(pub: Endpoint.Pub, event: Endpoint.Event) = rearrangeEndpoints
  }
  log.debug("alive %s %s".format(this, uuid))
  Mesh.register(this)

  @log
  def getDiffieHellman() = authDiffieHellman
  @log
  def getEndpoints(): Seq[Endpoint[_ <: Endpoint.Nature]] = endpoint
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
  @log
  def addEndpoint[T <: Endpoint[_ <: Endpoint.Nature]](endpoint: T): T = {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    this.endpoint = this.endpoint :+ endpoint
    endpoint.subscribe(endpointSubscriber)
    endpoint
  }
  @log
  def addEndpoint[T <: Endpoint[_ <: Endpoint.Nature]](f: Hexapod => T): T = addEndpoint(f(this))
  @log
  def delEndpoint[T <: Endpoint[_ <: Endpoint.Nature]](endpoint: T): T = {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    endpoint.removeSubscription(endpointSubscriber)
    this.endpoint = this.endpoint.filterNot(_ == endpoint)
    endpoint
  }
  @log
  def setDiffieHellman(g: Int, p: BigInt, publicKey: BigInt, secretKey: BigInt = 0): DiffieHellman = {
    val dh = new DiffieHellman(g, p, secretKey, publicKey)
    authDiffieHellman = Some(dh)
    publish(Hexapod.Event.SetDiffieHellman(this))
    dh
  }
  override def equals(that: Any): Boolean =
    that.isInstanceOf[Hexapod] && (this.hashCode() == that.asInstanceOf[Hexapod].hashCode())
  override protected def publish(event: Hexapod.Event) = try {
    super.publish(event)
  } catch {
    case e: Throwable =>
      log.error(e.getMessage(), e)
  }
  @log
  def rearrangeEndpoints() =
    endpoint = endpoint.sortBy(-_.actualPriority).sortBy(!_.connected)
  override def toString = "Hexapod[%08X]".format(uuid.hashCode())
}

object Hexapod extends DependencyInjection.PersistentInjectable with Loggable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def hexapod2app(h: Hexapod.type): AppHexapod = h.applicationHexapod
  implicit def bindingModule = DependencyInjection()
  /** The Hexapod instance cache */
  private var applicationHexapod = inject[AppHexapod]
  Endpoint // start initialization if needed

  def apply(uuid: UUID): Hexapod = Mesh(uuid) getOrElse { new Hexapod(uuid) }
  def inner() = applicationHexapod
  def recreateFromSignature(): Option[Hexapod] = {
    None
  }

  /*
   * dependency injection
   */
  override def afterInjection(newModule: BindingModule) {
    applicationHexapod = inject[AppHexapod]
  }

  sealed trait Event
  object Event {
    case class Connect[T <: Endpoint[_ <: Endpoint.Nature]](val endpoint: T) extends Event
    case class Disconnect[T <: Endpoint[_ <: Endpoint.Nature]](val endpoint: T) extends Event
    case class SetDiffieHellman(val hexapod: Hexapod) extends Event
  }
}
