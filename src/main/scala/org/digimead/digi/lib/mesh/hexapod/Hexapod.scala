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
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.endpoint.AbstractEndpoint
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.Mesh

class Hexapod(val uuid: UUID) extends Hexapod.Pub with Loggable {
  /** Hexapod endpoints */
  @volatile protected var endpoint = Seq[Endpoint]()
  @volatile protected var authDiffieHellman: Option[DiffieHellman] = None
  protected val peerSessionKey = new WeakHashMap[Hexapod, (BigInt, Array[Byte])] with SynchronizedMap[Hexapod, (BigInt, Array[Byte])]
  log.debug("alive %s %s".format(this, uuid))

  def init() = Mesh.register(this)
  def dispose() = Mesh.unregister(this)
  def registerEndpoint(endpoint: Endpoint) {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    this.endpoint = this.endpoint :+ endpoint
  }
  def getEndpoints() = endpoint
  @log
  def getDiffieHellman() = authDiffieHellman
  @log
  def setDiffieHellman(g: Int, p: BigInt, publicKey: BigInt, secretKey: BigInt = 0): DiffieHellman = {
    val dh = new DiffieHellman(g, p, secretKey, publicKey)
    authDiffieHellman = Some(dh)
    //publish(Hexapod.Event.SetDiffieHellman(this))
    dh
  }
  @log
  def getKeyForHexapod(peerHexapod: Hexapod): Option[(BigInt, Array[Byte])] = {
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
  override protected def publish(event: Hexapod.Event) = try {
    super.publish(event)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
  }
  override def toString = "Hexapod[%08X]".format(this.hashCode())
}

object Hexapod extends PersistentInjectable with Loggable {
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def hexapod2app(h: Hexapod.type): AppHexapod = h.applicationHexapod
  implicit def bindingModule = DependencyInjection()
  @volatile private var applicationHexapod = inject[AppHexapod]

  def instance() = applicationHexapod
  def reloadInjection() {
    applicationHexapod = inject[AppHexapod]
  }
  def recreateFromSignature(): Option[Hexapod] = {
    None
  }

  abstract class AppHexapod(override val uuid: UUID) extends Hexapod(uuid) with AbstractEndpoint with Loggable {
    def receive(message: Message)
    def connected(): Boolean
  }
  sealed trait Event
  object Event {
    case class Connect(val endpoint: Endpoint) extends Event
    case class Disconnect(val endpoint: Endpoint) extends Event
    case class SetDiffieHellman(val hexapod: Hexapod) extends Event
  }
}
