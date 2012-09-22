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

import scala.Option.option2Iterable
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import scala.collection.mutable.Publisher
import scala.collection.mutable.SynchronizedBuffer

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod

class Peer extends Peer.Interface with Logging {
  protected val pool = new ArrayBuffer[Hexapod] with SynchronizedBuffer[Hexapod]
  def add(node: Hexapod) = {
    log.debug("add %s to peer pool".format(node))
    pool += node
    Peer.Event.publish(Peer.Event.Add(node))
  }
  def remove(node: Hexapod) = {
    log.debug("remove %s to peer pool".format(node))
    pool -= node
    Peer.Event.publish(Peer.Event.Remove(node))
  }
  def get(transport: Option[Class[_ <: Endpoint]], direction: Endpoint.Direction*): Seq[Hexapod] = {
    val message = "search best peer" + (if (transport.nonEmpty || direction.nonEmpty) " for " else "")
    val messageTransport = transport.map("transport " + _.getName()).getOrElse("")
    val messageDirrection = (if (transport.nonEmpty) " and " else "") +
      (if (direction.nonEmpty) "direction %s".format(direction.mkString(" or ")) else "")
    log.debug(message + messageTransport + messageDirrection)
    var result = pool.toSeq
    transport.foreach(transport => result = result.filter(_.getEndpoints.exists(ep => {
      transport.isAssignableFrom(ep.getClass()) && (direction.isEmpty || direction.contains(ep.direction))
    })))
    result.take(5)
  }
  override def toString = "default peers pool implemetation"
}

object Peer extends Logging {
  implicit def hub2implementation(h: Peer.type): Interface = h.implementation
  private var implementation: Interface = null

  def init(arg: Init): Unit = synchronized {
    assert(Mesh.isInitialized, "Mesh not initialized")
    log.debug("initialize peers pool with " + arg.implementation)
    implementation = arg.implementation
  }
  def isInitialized(): Boolean = implementation != null

  trait Interface {
    protected val pool: Buffer[Hexapod]

    /** add Hexapod to hub pool */
    def add(node: Hexapod)
    /** remove Hexapod to hub pool */
    def remove(node: Hexapod)
    /** get best hexapod */
    def get(transport: Option[Class[_ <: Endpoint]], direction: Endpoint.Direction*): Seq[Hexapod]
  }
  trait Init {
    val implementation: Interface
  }
  class DefaultInit extends Init {
    val implementation: Interface = new Peer
  }
  sealed trait Event
  object Event extends Publisher[Event] {
    override protected[Peer] def publish(event: Event) = super.publish(event)

    case class Add(hexapod: Hexapod) extends Event
    case class Remove(hexapod: Hexapod) extends Event
  }
}
