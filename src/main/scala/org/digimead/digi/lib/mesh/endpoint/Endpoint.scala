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

package org.digimead.digi.lib.mesh.endpoint

import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedMap
import scala.collection.mutable.WeakHashMap
import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.Message

import com.escalatesoft.subcut.inject.BindingModule

/**
 * unique source/destination
 */
trait Endpoint[T <: Endpoint.Nature] extends Publisher[Endpoint.Event] with Loggable {
  /** hexapod container */
  val parent: WeakReference[Hexapod]
  /** endpoint direction */
  val direction: Endpoint.Direction
  /** endpoint nature (at least protocol and address) */
  val nature: T
  /** endpoint options (optional parameters, that passed to signature) */
  val options: String = ""
  /** endpoint initial priority */
  val initialPriority: Int = Endpoint.Priority.LOW.id
  /**
   * endpoint actual priority
   * decrement on failure
   * restore on success
   * on every modification publish Event.PriorityShift
   */
  @volatile var actualPriority: Int = initialPriority
  /** is endpoint connected/available */
  @volatile protected var connectionActive = false
  /** session keys between this endpoint and destination */
  protected val peerSessionKey = new WeakHashMap[Hexapod, Endpoint.SessionKey] with SynchronizedMap[Hexapod, Endpoint.SessionKey]

  /**
   * connect endpoint to mesh
   * set connected to true is successful
   * @return true if successful
   */
  def connect(): Boolean
  /**
   * return connection state
   * @return true if successful
   */
  def connected(): Boolean = connectionActive
  /**
   * disconnect endpoint from mesh
   * set connected to false is successful
   * @return true if successful
   */
  def disconnect(): Boolean
  /**
   * receive and process message from internal listener
   * @return true if successful
   */
  def receive(message: Array[Byte]): Boolean
  /**
   * reconnect endpoint if disconnected
   * @return true if successful
   */
  def reconnect(): Boolean = {
    disconnect
    connect
  }
  /**
   * write transport signature in form
   * protocol,address,options
   * in form:
   * protocol'address'priority'options
   * ' is separator
   */
  def signature(): String = Seq(nature.protocol, nature.address, actualPriority, direction, options).mkString("'")
  /**
   * tests against other endpoint
   */
  def suitable(ep: Endpoint[_ <: Endpoint.Nature], directionFilter: Endpoint.Direction*): Boolean =
    this.getClass.isAssignableFrom(ep.getClass()) && (directionFilter.isEmpty || directionFilter.contains(ep.direction))

  /**
   * send message
   */
  def send(message: Message, remoteEndpoint: Endpoint[T]): Boolean = {
    false
  }
  protected def send(message: Message, key: Option[Array[Byte]], remoteEndpoint: Endpoint[T]): Boolean
  protected def getKeyForHexapod(peerHexapod: Hexapod): Option[Array[Byte]] = {
    compactRawKeys()
    peerSessionKey.get(peerHexapod) match {
      case Some(sessionKey) if !sessionKey.isExpired =>
        Some(sessionKey.get)
      case _ =>
        parent.get.flatMap {
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
  override def equals(that: Any): Boolean =
    that.getClass() == this.getClass() && (this.hashCode() == that.hashCode())
  override protected def publish(event: Endpoint.Event) = try {
    super.publish(event)
  } catch {
    case e: Throwable =>
      log.error(e.getMessage(), e)
  }
}

object Endpoint extends DependencyInjection.PersistentInjectable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def bindingModule = DependencyInjection()
  @volatile private var factory = inject[Seq[Factory]].map(factory => factory.protocol -> factory).toMap
  @volatile private var maxKeyLifeTime = injectOptional[Long]("Mesh.Endpoint.MaxKeyLifeTime") getOrElse 60000L
  @volatile private var maxKeyAccessTime = injectOptional[Int]("Mesh.Endpoint.MaxKeyAccessTime") getOrElse 100

  def fromSignature(hexapod: Hexapod, signature: String): Option[Endpoint[_ <: Endpoint.Nature]] =
    signature.split("""'""").headOption.flatMap(protocol => factory.get(protocol).flatMap(_.fromSignature(hexapod, signature)))

  /*
   * dependency injection
   */
  override def injectionAfter(newModule: BindingModule) {
    factory = inject[Seq[Factory]].map(factory => factory.protocol -> factory).toMap
    maxKeyLifeTime = injectOptional[Long]("Mesh.Endpoint.MaxKeyLifeTime") getOrElse 60000L
    maxKeyAccessTime = injectOptional[Int]("Mesh.Endpoint.MaxKeyAccessTime") getOrElse 100
  }

  object Priority extends Enumeration {
    val NONE = Value(0, "NONE")
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
  trait Factory {
    /** protocol name */
    val protocol: String
    /** endpoint factory */
    def fromSignature(hexapod: Hexapod, signature: String): Option[Endpoint[_ <: Endpoint.Nature]]
  }
  /**
   * marker trait that contain toString method with endpoint address
   */
  trait Nature {
    /** protocol name */
    val protocol: String
    /** return address of endpoint as string */
    def address(): String = throw new UnsupportedOperationException
    override def toString(): String = protocol + "://" + address()
    override def equals(that: Any): Boolean =
      that.isInstanceOf[Nature] && (this.address == that.asInstanceOf[Nature].address)
  }
  // direction
  sealed trait Direction {
    def reverse(): Direction
  }
  sealed trait In extends Direction
  sealed trait Out extends Direction
  object Direction {
    def unapply(in: String): Option[Direction] = in match {
      case "In" => Some(In)
      case "Out" => Some(Out)
      case "InOut" => Some(InOut)
      case _ => None
    }
    case object In extends In {
      def reverse() = Out
      override def toString() = "In"
    }
    case object Out extends Out {
      def reverse() = In
      override def toString() = "Out"
    }
    case object InOut extends In with Out {
      override def reverse() = InOut
      override def toString() = "InOut"
    }
  }
  // event
  sealed trait Event
  object Event {
    case class Connect[T <: Endpoint[_ <: Endpoint.Nature]](endpoint: T) extends Event
    case class PriorityShift[T <: Endpoint[_ <: Endpoint.Nature]](endpoint: T) extends Event
    case class Disconnect[T <: Endpoint[_ <: Endpoint.Nature]](endpoint: T) extends Event
  }
}
