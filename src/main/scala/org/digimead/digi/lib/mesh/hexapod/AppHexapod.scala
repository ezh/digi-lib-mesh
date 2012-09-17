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

import scala.Option.option2Iterable
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.auth.DiffieHellman
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.EndpointEvent
import org.digimead.digi.lib.mesh.message.DiffieHellmanReq
import org.digimead.digi.lib.mesh.message.DiffieHellmanRes

class AppHexapod(override val uuid: UUID) extends Hexapod.AppHexapod(uuid) {
  @volatile protected var endpoint = Seq[Endpoint]()
  protected val endpointSubscribers = new WeakHashMap[Endpoint, Endpoint#Sub] with SynchronizedMap[Endpoint, Endpoint#Sub]
  log.debug("alive %s %s".format(this, uuid))

  def registerEndpoint(endpoint: Endpoint) {
    log.debug("register %s endpoint at %s".format(endpoint, this))
    this.endpoint = this.endpoint :+ endpoint
    val endpointSubscriber = new endpoint.Sub {
      def notify(pub: endpoint.Pub, event: EndpointEvent): Unit = event match {
        case Endpoint.Event.Connect(endpoint) =>
          Hexapod.publish(Hexapod.Event.Connect(endpoint))
        case Endpoint.Event.Disconnect(endpoint) =>
          Hexapod.publish(Hexapod.Event.Disconnect(endpoint))
      }
    }
    endpoint.subscribe(endpointSubscriber)
    endpointSubscribers(endpoint) = endpointSubscriber
  }
  def send(message: Message): Option[Endpoint] = {
    log.debug("send " + message)
    if (!message.isInstanceOf[DiffieHellmanReq] && !message.isInstanceOf[DiffieHellmanRes]) {
      log.___glance("!!!!" + checkAuthExistsDH + checkAuthExistsSessionKey)
      if (!checkAuthExistsDH) {
        log.debug("Diffie Hellman authentification data not found, generate new")
        val p = DiffieHellman.randomPrime(128)
        val g = 5
        authDiffieHellman = Some(new DiffieHellman(g, p))
        Communication.push(DiffieHellmanReq(authDiffieHellman.get.getPublicKey, g, p, uuid, None, None))
        Communication.fail(message)
        return None
      }
      if (!checkAuthExistsSessionKey) {
        log.debug("session key not found")
        Communication.fail(message)
        return None
      }
    }
    val bestEndpoint = endpoint.filter(ep =>
      ep.connected &&
        (ep.direction == Endpoint.Out || ep.direction == Endpoint.InOut)).sortBy(_.priority).headOption
    bestEndpoint.flatMap(_.send(message))
  }
  def receive(message: Message) = {
    log.debug("receive message! " + message)
    message.destinationHexapod match {
      case Some(hexapod) if uuid == this.uuid =>
        Communication.react(Stimulus.IncomingMessage(message))
      case Some(hexapod) =>
        log.fatal("receive message instead of neighbor".format(message))
      case None =>
        Communication.react(Stimulus.IncomingMessage(message))
    }
  }
  @Loggable
  def connect(): Boolean = endpoint.filter(_.connect).nonEmpty
  @Loggable
  def reconnect() {}
  def disconnect() {}
  protected def bestEndpoint(target: Endpoint): Option[Endpoint] = {
    None
  }
  protected def bestEndpoint(target: Hexapod): Option[Endpoint] = {
    None
  }
  protected def bestEndpoint(target: UUID): Option[Endpoint] = {
    None
  }
  protected def sendCommand[T](f: => T): Boolean = {
    false
  }
  protected def checkAuthExistsDH(): Boolean = authDiffieHellman.nonEmpty
  protected def checkAuthExistsSessionKey(): Boolean = authSessionKey.nonEmpty
  override def toString = "AppHexapod[%08X]".format(this.hashCode())
}
