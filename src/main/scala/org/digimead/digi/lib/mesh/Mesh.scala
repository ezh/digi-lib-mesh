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

import scala.collection.mutable.HashMap
import scala.collection.mutable.SynchronizedMap
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.DiffieHellmanReq
import org.digimead.digi.lib.mesh.message.DiffieHellmanRes
import org.digimead.digi.lib.mesh.message.Ping

class Mesh extends Mesh.Interface {
  private val gcLimit = 100
  private val gcCouter = new AtomicInteger(gcLimit)
  val entity = new HashMap[UUID, WeakReference[Entity]]() with SynchronizedMap[UUID, WeakReference[Entity]]

  def register(entity: Entity) {
    assert(!this.entity.contains(entity.uuid), "entity %s already registered".format(entity))
    log.debug("register entity " + entity)
    this.entity(entity.uuid) = new WeakReference(entity)
    if (gcCouter.decrementAndGet() == 0) {
      gcCouter.set(gcLimit)
      compact()
    }
  }
  def unregister(entity: Entity) {
    assert(this.entity.contains(entity.uuid), "entity %s not registered".format(entity))
    log.debug("unregister entity " + entity)
    this.entity.remove(entity.uuid)
  }
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

object Mesh extends Logging {
  implicit def mesh2implementation(m: Mesh.type): Interface = m.implementation
  private var implementation: Interface = null

  def apply(uuid: UUID): Option[Entity] = implementation.entity.get(uuid).flatMap(_.get)
  def init(arg: Init): Unit = synchronized {
    log.debug("initialize mesh with " + arg.implementation)
    implementation = arg.implementation
  }
  def isInitialized(): Boolean = implementation != null
  def isReady(): Boolean = {
    assert(Mesh.isInitialized, "Mesh not initialized")
    assert(Peer.isInitialized, "Peer not initialized")
    assert(Hexapod.isInitialized, "Hexapod not initialized")
    assert(Communication.isInitialized, "Communication not initialized")
    assert(DiffieHellmanReq.isInitialized, "DiffieHellmanReq not initialized")
    assert(DiffieHellmanRes.isInitialized, "DiffieHellmanRes not initialized")
    assert(Ping.isInitialized, "Ping not initialized")
    true
  }

  trait Interface extends Logging {
    protected[Mesh] val entity: HashMap[UUID, WeakReference[Entity]]

    def register(entity: Entity)
    def unregister(entity: Entity)
  }
  trait Init {
    val implementation: Interface
  }
  class DefaultInit extends Init {
    val implementation: Interface = new Mesh
  }
}
