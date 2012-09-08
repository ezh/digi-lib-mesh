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

import java.util.UUID

import scala.collection.mutable.Publisher
import scala.ref.WeakReference

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Hub
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.hexapod.AppHexapod

abstract class Endpoint(
  /** autogenerated endpoint id */
  val uuid: UUID,
  /** for example google account */
  val userIdentifier: String,
  /** for example MAC or IMEI or IMSI or MEID number (depending on the device's technology) */
  val deviceIdentifier: String,
  /** transport level endpoint id */
  val transportIdentifier: Endpoint.TransportIdentifiers,
  /** hexapod container */
  val hexapod: WeakReference[AppHexapod],
  /** direction */
  val direction: Endpoint.Direction)
  extends Publisher[EndpointEvent] with AbstractEndpoint {
  this: Logging =>
  @volatile var priority = Endpoint.Priority.LOW
  @volatile var connected = false
  @volatile var lastActivity = System.currentTimeMillis
  assert(Hub.isInitialized, "Hub not initialized")
  hexapod.get.foreach(_.registerEndpoint(this))

  def send(message: Message) = send(message, None)
  def send(message: Message, key: Option[BigInt]): Option[Endpoint]
  def receive(message: Array[Byte])
}

sealed trait EndpointEvent

object Endpoint {
  trait TransportIdentifiers {
    override def toString = ""
  }
  object Priority extends Enumeration {
    val LOW = Value(10, "LOW")
    val MEDUIM = Value(20, "MEDIUM")
    val HIGH = Value(30, "HIGH")
  }
  // direction
  sealed trait Direction
  trait In extends Direction
  case object In extends In
  trait Out extends Direction
  case object Out extends Out
  case object InOut extends In with Out
  object Event {
    case class Connect(endpoint: Endpoint) extends EndpointEvent
    case class Disconnect(endpoint: Endpoint) extends EndpointEvent
  }
}