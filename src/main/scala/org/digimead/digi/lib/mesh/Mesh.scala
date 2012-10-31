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

package org.digimead.digi.lib.mesh

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import scala.Option.option2Iterable
import scala.collection.mutable.HashMap
import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedMap
import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.DependencyInjection.PersistentInjectable
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.Message
import org.scala_tools.subcut.inject.BindingModule
import org.scala_tools.subcut.inject.Injectable

/**
 * Class Mesh is default implementation for Mesh singleton.
 * It provide global registry of discovered Hexapods
 */
class Mesh(implicit val bindingModule: BindingModule) extends Injectable with Mesh.Interface {
  protected val gcLimit = injectOptional[Int]("Mesh.GC.Limit") getOrElse 100
  protected val gcCounter = new AtomicInteger(gcLimit)
  protected val entity = new HashMap[UUID, WeakReference[Hexapod]]() with SynchronizedMap[UUID, WeakReference[Hexapod]]

  /**
   * Register hexapod in mesh registry
   */
  @log
  def register(hexapod: Hexapod): Boolean = {
    log.debug("register hexapod " + hexapod)
    if (entity.contains(hexapod.uuid)) {
      log.error("hexapod %s already registered".format(hexapod))
      return false
    }
    entity(hexapod.uuid) = new WeakReference(hexapod)
    publish(Mesh.Event.Register(hexapod))
    if (gcCounter.decrementAndGet() == 0) {
      gcCounter.set(gcLimit)
      compact()
    }
    true
  }
  /**
   * Unregister hexapod in mesh registry
   */
  @log
  def unregister(hexapod: Hexapod): Boolean = {
    log.debug("unregister hexapod " + hexapod)
    if (!entity.contains(hexapod.uuid)) {
      log.error("hexapod %s not registered".format(hexapod))
      return false
    }
    publish(Mesh.Event.Unregister(hexapod))
    entity.remove(hexapod.uuid)
    true
  }
  /**
   * Start garbage collector
   */
  private def compact() {
    entity.foreach {
      case (uuid, entity) if entity.get == None =>
        log.debug("compact " + uuid)
        this.entity.remove(uuid)
      case _ =>
    }
  }
  override def toString = "default mesh implemetation"
}

/**
 * Singleton Mesh contains global registry of discovered Hexapods
 * Mesh -> Message -> Communication -> Hexapod -> Endpoint
 *                                  -> Peer
 */
object Mesh extends PersistentInjectable with Loggable {
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def mesh2implementation(m: Mesh.type): Interface = m.implementation
  implicit def bindingModule = DependencyInjection()
  @volatile private var implementation = inject[Interface]
  log.debug("build mesh infrastructure")
  org.digimead.digi.lib.mesh.isReady = true
  Message // start initialization if needed

  def inner() = implementation
  def commitInjection() {}
  def updateInjection() { implementation = inject[Interface] }

  trait Interface extends Mesh.Pub with Loggable {
    protected val entity: HashMap[UUID, WeakReference[Hexapod]]

    def apply(): Iterable[Hexapod] = implementation.entity.values.flatMap(_.get)
    def apply(uuid: UUID): Option[Hexapod] = implementation.entity.get(uuid).flatMap(_.get)
    def register(entity: Hexapod): Boolean
    def unregister(entity: Hexapod): Boolean
    override protected def publish(event: Mesh.Event) = try {
      super.publish(event)
    } catch {
      case e =>
        log.error(e.getMessage(), e)
    }
  }
  sealed trait Event
  object Event {
    case class Register(hexapod: Hexapod) extends Event
    case class Unregister(hexapod: Hexapod) extends Event
  }
}
