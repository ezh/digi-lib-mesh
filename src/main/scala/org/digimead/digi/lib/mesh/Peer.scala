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

package org.digimead.digi.lib.mesh

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedBuffer

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable

import language.implicitConversions

/**
 * Class Peer is default implementation for Peer singleton.
 * It provide global pool of peers. Peer is subset of Mesh with contains only reachable Hexapods.
 */
class Peer(implicit val bindingModule: BindingModule) extends Injectable with Peer.Interface {
  protected val pool = new ArrayBuffer[Hexapod] with SynchronizedBuffer[Hexapod]

  /**
   * Add hexapod to peer pool. Register hexapod in Mesh if necessary
   */
  @log
  def add(hexapod: Hexapod): Boolean = {
    log.debug("add %s to peer pool".format(hexapod))
    if (pool.contains(hexapod)) {
      log.error("hexapod %s already registered".format(hexapod))
      return false
    }
    if (Mesh(hexapod.uuid).isEmpty)
      if (!Mesh.register(hexapod)) {
        log.error("unable registers %s in mesh".format(hexapod))
        return false
      }
    pool += hexapod
    log.___glance("PUBLISH")
    publish(Peer.Event.Add(hexapod))
    true
  }
  /**
   * Remove hexapod from peer pool.
   */
  @log
  def remove(hexapod: Hexapod): Boolean = {
    log.debug("remove %s to peer pool".format(hexapod))
    if (!pool.contains(hexapod)) {
      log.error("hexapod %s not registered".format(hexapod))
      return false
    }
    pool -= hexapod
    publish(Peer.Event.Remove(hexapod))
    true
  }
  /**
   *
   * @log
   * def get(transport: Option[Class[_ <: Endpoint]], direction: Endpoint.Direction*): Seq[Hexapod] = {
   * val message = "search best peer" + (if (transport.nonEmpty || direction.nonEmpty) " for " else "")
   * val messageTransport = transport.map("transport " + _.getName()).getOrElse("")
   * val messageDirrection = (if (transport.nonEmpty) " and " else "") +
   * (if (direction.nonEmpty) "direction %s".format(direction.mkString(" or ")) else "")
   * log.debug(message + messageTransport + messageDirrection)
   * var result = pool.toSeq
   * transport.foreach(transport => result = result.filter(_.getEndpoints.exists(ep => {
   * transport.isAssignableFrom(ep.getClass()) && (direction.isEmpty || direction.contains(ep.direction))
   * })))
   * result.take(5)
   * }
   *
   */
  /**
   * Clear peer pool
   */
  @log
  def clear() = pool.foreach(remove)
  override def toString = "default peers pool implemetation"
}

/**
 * Singleton Peer contains global registry of discovered Hexapods
 */
object Peer extends DependencyInjection.PersistentInjectable with Loggable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def peer2implementation(p: Peer.type): Interface = p.inner
  implicit def bindingModule = DependencyInjection()

  /*
   * dependency injection
   */
  def inner() = inject[Interface]

  trait Interface extends Peer.Pub with Loggable {
    protected val pool: Buffer[Hexapod]

    def apply(): Iterable[Hexapod] = Peer.inner.pool.toIterable
    /** add Hexapod to peer pool */
    def add(hexapod: Hexapod): Boolean
    /** remove Hexapod to peer pool */
    def remove(hexapod: Hexapod): Boolean
    /** get best hexapod */
    //def get(transport: Option[Class[_ <: Endpoint]], direction: Endpoint.Direction*): Seq[Hexapod]
    /** clear peer pool */
    def clear()
    override protected def publish(event: Peer.Event) = try {
      super.publish(event)
    } catch {
      case e: Throwable =>
        log.error(e.getMessage(), e)
    }
  }
  sealed trait Event
  object Event {
    case class Add(hexapod: Hexapod) extends Event
    case class Remove(hexapod: Hexapod) extends Event
  }
}
