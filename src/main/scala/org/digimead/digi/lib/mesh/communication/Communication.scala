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

package org.digimead.digi.lib.mesh.communication

import scala.collection.mutable.HashMap
import scala.collection.mutable.Publisher
import scala.collection.mutable.SynchronizedMap

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.HexapodEvent

class Communication extends Communication.Interface {
  /** Message.word -> Receptor */
  protected val active = new HashMap[String, Message] with SynchronizedMap[String, Message]
  protected val pending = new HashMap[String, Message] with SynchronizedMap[String, Message]
  protected val appHexapodSubscriber = new Hexapod.Sub {
    def notify(pub: Hexapod.Pub, event: HexapodEvent): Unit = event match {
      case Hexapod.Event.Connect(endpoint) =>
        processPendingMessages()
      case Hexapod.Event.Disconnect(endpoint) =>
    }
  }
  Hexapod.subscribe(appHexapodSubscriber)

  def push(message: Message, force: Boolean = false): Boolean = synchronized {
    val result = if (force) {
      log.debug("push " + message + " to pending buffer")
      pending(message.word) = message
      Communication.publish(Communication.Event.Add(message))
      true
    } else if (!force && !pending.contains(message.word)) {
      log.debug("push " + message + " to pending buffer")
      Communication.publish(Communication.Event.Add(message))
      pending(message.word) = message
      true
    } else
      false
    sendPendingMessages()
    result
  }
  def react(stimulus: Stimulus): Option[Boolean] = {
    val result = active.map {
      case (word, message) =>
        message.react(stimulus) match {
          case Some(true) =>
            log.debug("conversation %s with message %s success".format(message.conversation, message))
            active.remove(word)
            Communication.publish(Communication.Event.Success(message))
          case Some(false) =>
            log.debug("conversation %s with message %s failed".format(message.conversation, message))
            active.remove(word)
            Communication.publish(Communication.Event.Fail(message))
          case None =>
        }
    }.nonEmpty
    if (result) Some(true) else None
  }
  def processPendingMessages() = synchronized {
    sendPendingMessages()
    compactPendingMessages()
  }
  protected def sendPendingMessages() = synchronized {
    pending.foreach {
      case (word, message) =>
        Mesh(message.sourceHexapod) match {
          case Some(hexapod: AppHexapod) =>
            hexapod.send(message) match {
              case Some(endpoint) =>
                log.debug("%s sending successfully".format(message))
                pending.remove(word)
                if (message.isReplyRequired) {
                  active(word) = message
                  Communication.publish(Communication.Event.Active(message))
                } else {
                  Communication.publish(Communication.Event.Active(message))
                  Communication.publish(Communication.Event.Success(message))
                }
              case None =>
                log.debug("%s sending failed".format(message))
            }
          case _ =>
            log.warn("unable to sent %s: hexapod %s not found".format(message, message.sourceHexapod))
        }
    }
  }
  protected def compactPendingMessages() = synchronized {

  }
  override def toString = "default communication implemetation"
}

sealed trait CommunicationEvent

object Communication extends Publisher[CommunicationEvent] with Logging {
  implicit def communication2implementation(communication: Communication.type): Interface =
    communication.implementation
  private var implementation: Interface = null

  def init(arg: Init): Unit = synchronized {
    assert(Mesh.isInitialized, "Mesh not initialized")
    log.debug("initialize communication with " + arg.implementation)
    implementation = arg.implementation
  }
  def isInitialized(): Boolean = implementation != null
  override protected[communication] def publish(event: CommunicationEvent) = super.publish(event)

  trait Interface extends Receptor with Logging {
    protected val active: HashMap[String, Message]
    protected val pending: HashMap[String, Message]

    def push(message: Message, force: Boolean = false): Boolean
    def processPendingMessages()
  }
  trait Init {
    val implementation: Interface
  }
  class DefaultInit extends Init {
    val implementation: Interface = new Communication
  }
  object Event {
    case class Add(message: Message) extends CommunicationEvent
    case class Active(message: Message) extends CommunicationEvent
    case class Success(message: Message) extends CommunicationEvent
    case class Fail(message: Message) extends CommunicationEvent
  }
}
