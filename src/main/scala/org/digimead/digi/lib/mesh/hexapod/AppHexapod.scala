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
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import org.digimead.digi.lib.enc.DiffieHellman
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.message.Message
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.aop.log

class AppHexapod(override val uuid: UUID) extends Hexapod.AppHexapod(uuid) {
  protected val endpointSubscribers = new WeakHashMap[Endpoint[_ <: Endpoint.Nature], Endpoint[Endpoint.Nature]#Sub] with SynchronizedMap[Endpoint[_ <: Endpoint.Nature], Endpoint[Endpoint.Nature]#Sub]

  if (authDiffieHellman.isEmpty) {
    log.debug("Diffie Hellman authentification data not found, generate new")
    val p = DiffieHellman.randomPrime(128)
    val g = 5
    authDiffieHellman = Some(new DiffieHellman(g, p))
  }

  override def registerEndpoint(endpoint: Endpoint[_ <: Endpoint.Nature]) {
    super.registerEndpoint(endpoint)
    val endpointSubscriber = new endpoint.Sub {
      def notify(pub: endpoint.Pub, event: Endpoint.Event): Unit = event match {
        case Endpoint.Event.Connect(endpoint) =>
          publish(Hexapod.Event.Connect(endpoint))
        case Endpoint.Event.Disconnect(endpoint) =>
          publish(Hexapod.Event.Disconnect(endpoint))
      }
    }
    endpoint.subscribe(endpointSubscriber)
    endpointSubscribers(endpoint) = endpointSubscriber
  }
  def send(message: Message): Option[Endpoint[_ <: Endpoint.Nature]] = {
    log.debug("send " + message)
/*    val remoteHexapods = Hexapod.getRemoteHexapods(message)
    val localEndpoint = endpoint.filter(ep => ep.connected && (ep.direction == Endpoint.Direction.Out ||
      ep.direction == Endpoint.Direction.InOut)).sortBy(_.priority)
    if (localEndpoint.isEmpty) {
      log.warn("AppHexapod: There is no endpoints with direction Out/InOut. Sending aborted.")
    } else {
      for {
        lEndpoint <- localEndpoint
        rHexapod <- remoteHexapods
      } {
        val rEndpoints = Hexapod.getRemoteEndpoints(rHexapod, lEndpoint)

        
        log.___glance("SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSS")
        //localEndpoint.send(message)
        None

      }
    }*/
    None
  }
  def receive(message: Message) = {
    log.debug("receive message " + message)
    message.destinationHexapod match {
      case Some(hexapod) if uuid == this.uuid =>
        Communication.react(Stimulus.IncomingMessage(message))
      case Some(hexapod) =>
        log.fatal("receive message instead of neighbor".format(message))
      case None =>
        Communication.react(Stimulus.IncomingMessage(message))
    }
  }
  @log
  def connect(): Boolean = endpoint.filter(_.connect).nonEmpty
  @log
  def reconnect(): Boolean = endpoint.forall(_.reconnect)
  @log
  def disconnect() = endpoint.forall(_.disconnect)
  @log
  def connected() = endpoint.exists(_.connected)
  protected def bestEndpoint(target: Endpoint[_ <: Endpoint.Nature]): Option[Endpoint[_ <: Endpoint.Nature]] = {
    None
  }
  protected def bestEndpoint(target: Hexapod): Option[Endpoint[_ <: Endpoint.Nature]] = {
    None
  }
  protected def bestEndpoint(target: UUID): Option[Endpoint[_ <: Endpoint.Nature]] = {
    None
  }
  protected def sendCommand[T](f: => T): Boolean = {
    false
  }
  override def toString = "AppHexapod[%08X]".format(this.hashCode())
}
