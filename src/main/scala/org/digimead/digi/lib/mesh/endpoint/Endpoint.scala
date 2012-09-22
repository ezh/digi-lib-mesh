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

package org.digimead.digi.lib.mesh.endpoint

import scala.collection.mutable.Publisher
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.Peer.hub2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.DiffieHellman

abstract class Endpoint(
  /** transport level endpoint id */
  val identifier: Endpoint.TransportIdentifier,
  /** hexapod container */
  val terminationPoint: WeakReference[Hexapod],
  /** direction */
  val direction: Endpoint.Direction)
  extends Publisher[Endpoint.Event] with AbstractEndpoint {
  this: Logging =>
  @volatile var priority = Endpoint.Priority.LOW
  @volatile var connected = false
  @volatile var lastActivity = System.currentTimeMillis
  protected val peerSessionKey = new WeakHashMap[Hexapod, Endpoint.SessionKey] with SynchronizedMap[Hexapod, Endpoint.SessionKey]
  assert(Peer.isInitialized, "Peer not initialized")
  terminationPoint.get.foreach(_.registerEndpoint(this))

  def receive(message: Array[Byte])
  def send(message: Message): Option[Endpoint] = {
    for {
      localHexapod <- terminationPoint.get
      remoteEndpoint <- getRemoteEndpoint(message)
      remoteHexapod <- remoteEndpoint.terminationPoint.get
    } yield message.messageType match {
      case Message.Type.Unencripted =>
        send(message, None, localHexapod, remoteHexapod, remoteEndpoint)
      case Message.Type.Acknowledgement =>
        send(message, None, localHexapod, remoteHexapod, remoteEndpoint)
      case _ =>
        getKeyForHexapod(remoteHexapod) match {
          case key: Some[_] =>
            send(message, key, localHexapod, remoteHexapod, remoteEndpoint)
          case None =>
            localHexapod.getDiffieHellman() match {
              case Some(dh) =>
                Communication.push(DiffieHellman(dh.publicKey, dh.g, dh.p, localHexapod.uuid, Some(remoteHexapod.uuid))(true), false)
              case None =>
                log.error("unable to find DiffieHellman parameter for " + localHexapod)
            }
            None
        }
    }
  } getOrElse {
    val remoteEndpoint = getRemoteEndpoint(message)
    val remoteHexapod = remoteEndpoint.map(_.terminationPoint.get)
    log.error("unable to send: incomlete route [[%s %s]] -> [[%s %s]]".format(terminationPoint.get, this, remoteHexapod, remoteEndpoint))
    None
  }
  protected def send(message: Message, key: Option[Array[Byte]], localHexapod: Hexapod, remoteHexapod: Hexapod, remoteEndpoint: Endpoint): Option[Endpoint]
  /**
   * tests against other endpoint
   */
  protected def suitable(ep: Endpoint, directionFilter: Endpoint.Direction*): Boolean =
    this.getClass.isAssignableFrom(ep.getClass()) && (directionFilter.isEmpty || directionFilter.contains(ep.direction))
  protected def getRemoteEndpoint(message: Message): Option[Endpoint] =
    message.destinationHexapod match {
      case Some(hexapodUUID) =>
        Mesh(hexapodUUID) match {
          case Some(hexapod: Hexapod) if hexapod.getEndpoints.exists(ep => suitable(ep, Endpoint.In, Endpoint.InOut)) =>
            hexapod.getEndpoints.filter(ep => suitable(ep, Endpoint.In, Endpoint.InOut)).headOption
          case _ =>
            Peer.get(Some(this.getClass()), Endpoint.In, Endpoint.InOut).map(_.getEndpoints).flatten.
              filter(ep => suitable(ep, Endpoint.In, Endpoint.InOut)).headOption
        }
      case None =>
        Peer.get(Some(this.getClass()), Endpoint.In, Endpoint.InOut).map(_.getEndpoints).flatten.
          filter(ep => suitable(ep, Endpoint.In, Endpoint.InOut)).headOption
    }
  protected def getKeyForHexapod(peerHexapod: Hexapod): Option[Array[Byte]] = {
    compactRawKeys()
    peerSessionKey.get(peerHexapod) match {
      case Some(sessionKey) if !sessionKey.isExpired =>
        Some(sessionKey.get)
      case _ =>
        terminationPoint.get.flatMap {
          localHexapod =>
            localHexapod.getKeyForHexapod(peerHexapod) match {
              case Some((sharedKey, internalKey)) =>
                log.debug("update key for " + peerHexapod)
                val rawKey = Simple.getRawKey(sharedKey.toByteArray)
                peerSessionKey(peerHexapod) = Endpoint.SessionKey(rawKey)
                Some(rawKey)
              case None =>
                log.debug("unable to find shared key for " + peerHexapod)
                None
            }
        }
    }
  }
  protected def compactRawKeys() {
    peerSessionKey.foreach {
      case (key, sessionKey) =>
        if (sessionKey.isExpired)
          peerSessionKey.remove(key)
    }
  }
  override protected def publish(event: Endpoint.Event) = try {
    super.publish(event)
  } catch {
    case e =>
      log.error(e.getMessage(), e)
  }
}

object Endpoint {
  @volatile private var maxKeyLifeTime = 60000L
  @volatile private var maxKeyAccessTime = 100

  trait TransportIdentifier {
    override def toString = "EmptyTransportIdentifier"
  }
  object Priority extends Enumeration {
    val LOW = Value(10, "LOW")
    val MEDUIM = Value(20, "MEDIUM")
    val HIGH = Value(30, "HIGH")
  }
  case class SessionKey(rawKey: Array[Byte]) {
    @volatile var lastAccess = System.currentTimeMillis()
    @volatile var accessCounter = 0
    def get(): Array[Byte] = {
      lastAccess = System.currentTimeMillis()
      accessCounter += 1
      rawKey
    }
    def isExpired(): Boolean =
      System.currentTimeMillis() - lastAccess > maxKeyLifeTime ||
        accessCounter > maxKeyAccessTime
  }
  // direction
  sealed trait Direction
  trait In extends Direction
  case object In extends In
  trait Out extends Direction
  case object Out extends Out
  case object InOut extends In with Out
  // event
  sealed trait Event
  object Event {
    case class Connect(endpoint: Endpoint) extends Event
    case class Disconnect(endpoint: Endpoint) extends Event
  }
}
