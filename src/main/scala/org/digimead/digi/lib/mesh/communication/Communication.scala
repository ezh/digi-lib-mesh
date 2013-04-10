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

package org.digimead.digi.lib.mesh.communication

import scala.collection.mutable.HashMap
import scala.collection.mutable.Publisher
import scala.collection.mutable.Subscriber
import scala.collection.mutable.SynchronizedMap

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.Peer.peer2implementation
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.mesh.message.Acknowledgement
import org.digimead.digi.lib.mesh.message.Message
import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.Injectable

import language.implicitConversions

class Communication(implicit val bindingModule: BindingModule) extends Injectable with Communication.Interface {
  val deliverMax = injectIfBound[Int]("Mesh.Communication.DeliverMax") { 3 }
  val deliverTTL = injectIfBound[Long]("Mesh.Communication.DeliverTTL") { 60000L } // 1 minute
  val holdTTL = injectIfBound[Long]("Mesh.Communication.HoldTTL") { 3600000L } // 1 hour
  /** acknowledgment messages buffer */
  @volatile protected var acknowledgement = Seq[Acknowledgement]()
  /** global Seq[Receptor] */
  @volatile protected var global = Seq[Receptor]()
  /** active Message.word -> Parcel with Receptor */
  protected val buffer = new HashMap[String, Communication.Parcel] with SynchronizedMap[String, Communication.Parcel]
  /** pending message progress counter */
  protected val deliverMessageCounter = new HashMap[String, Int] with SynchronizedMap[String, Int]
  protected val appHexapodSubscriber = new Hexapod.Sub {
    def notify(pub: Hexapod.Pub, event: Hexapod.Event): Unit = event match {
      case Hexapod.Event.Connect(endpoint) =>
        processMessages()
      case Hexapod.Event.Disconnect(endpoint) =>
      case Hexapod.Event.SetDiffieHellman(hexapod) =>
    }
  }
  protected val peerSubscriber = new Peer.Sub {
    def notify(pub: Peer.Pub, event: Peer.Event): Unit = event match {
      case Peer.Event.Add(hexapod) =>
        if (Hexapod.connected)
          processMessages()
      case Peer.Event.Remove(hexapod) =>
    }
  }

  def registerGlobal(receptor: Receptor): Unit = synchronized {
    log.debug("add new receptor to global buffer")
    if (global.contains(receptor)) {
      log.error("global buffer already contains such receptor")
      return
    }
    global = global :+ receptor
  }
  def unregisterGlobal(receptor: Receptor): Unit = synchronized {
    log.debug("remove receptor from global buffer")
    if (!global.contains(receptor)) {
      log.error("global buffer not contains such receptor")
      return
    }
    global = global.filter(_ != receptor)
  }
  def acknowledge(conversationHash: Int) {
    buffer.map {
      case (word, parcel @ Communication.Parcel(message, condition)) =>
        if (!condition.isInstanceOf[Communication.Event.Delivered] && message.conversation.hashCode() == conversationHash) {
          log.debug("conversation %s with message %s acknowledged".format(message.conversation, message))
          parcel.condition = Communication.Condition.Delivered
          publish(Communication.Event.Delivered(message))
          if (!message.isReplyRequired) {
            buffer.remove(word)
            deliverMessageCounter.remove(word)
            publish(Communication.Event.Success(message))
          }
        }
    }
  }
  def push(message: Message, force: Boolean = false): Boolean = synchronized {
    assert(Some(message.sourceHexapod) != message.destinationHexapod, "unable to send message to itself")
    val result = if (message.isInstanceOf[Acknowledgement]) {
      log.debug("push acknowledgement message to buffer".format(message))
      acknowledgement = acknowledgement :+ message.asInstanceOf[Acknowledgement]
      true
    } else {
      if (force) {
        log.debug("forced push message %s to buffer".format(message))
        publish(Communication.Event.Add(message))
        buffer(message.word) = Communication.Parcel(message, Communication.Condition.Pending)
        true
      } else if (!force && !buffer.contains(message.word)) {
        log.debug("push message %s to buffer".format(message))
        publish(Communication.Event.Add(message))
        buffer(message.word) = Communication.Parcel(message, Communication.Condition.Pending)
        true
      } else {
        log.debug("push message skipped, message %s already in buffer".format(message))
        false
      }
    }
    deliverMessages()
    result
  }
  def react(stimulus: Stimulus): Option[Boolean] = {
    global.foreach(_.react(stimulus))
    buffer.map {
      case (word, Communication.Parcel(message, condition)) =>
        message.react(stimulus) match {
          case Some(true) =>
            log.debug("conversation %s with message %s success".format(message.conversation, message))
            buffer.remove(word)
            deliverMessageCounter.remove(word)
            publish(Communication.Event.Success(message))
          case Some(false) =>
            log.debug("conversation %s with message %s failed".format(message.conversation, message))
            buffer.remove(word)
            deliverMessageCounter.remove(word)
            publish(Communication.Event.Fail(message))
          case None =>
        }
    }
    None
  }
  @log
  def processMessages() = synchronized {
    compactMessages()
    deliverMessages()
  }
  @log
  protected[communication] def init() {
    Hexapod.subscribe(appHexapodSubscriber)
    Peer.subscribe(peerSubscriber)
  }
  @log
  protected[communication] def deinit() {
    Peer.removeSubscription(peerSubscriber)
    Hexapod.removeSubscription(appHexapodSubscriber)
  }

  @log
  protected def deliverMessages() = {
    acknowledgement.foreach(acknowledgement => deliverAcknowledgementMessage(acknowledgement))
    buffer.foreach {
      case (word, Communication.Parcel(message, Communication.Condition.Pending)) =>
        // always try to send pending messages
        deliverMessageCounter(word) = deliverMessageCounter.get(word).getOrElse(0) + 1
        if (deliverMessageCounter(word) == 1)
          deliverMessage(message)
        deliverMessageCounter(word) = deliverMessageCounter.get(word).getOrElse(0) - 1
      case (word, parcel @ Communication.Parcel(message, Communication.Condition.Sent)) =>
        // try to resend messages only at next deliver attempt
        if ((System.currentTimeMillis() - message.timestamp) / deliverTTL == parcel.counter) {
          if (parcel.counter < deliverMax) {
            log.debug("resend message %s, attempt #%d".format(message, parcel.counter + 1))
            deliverMessageCounter(word) = deliverMessageCounter.get(word).getOrElse(0) + 1
            if (deliverMessageCounter(word) == 1)
              deliverMessage(message)
            deliverMessageCounter(word) = deliverMessageCounter.get(word).getOrElse(0) - 1
          }
        }
      case _ =>
    }
  }
  protected def deliverAcknowledgementMessage(message: Acknowledgement) {
    assert(acknowledgement.contains(message), "unable to deliver acknowledgement message " + message)
    Mesh(message.sourceHexapod) match {
      case Some(hexapod: AppHexapod) =>
        hexapod.send(message) match {
          case Some(endpoint) =>
            log.debug("acknowledgement message %s sent".format(message))
            acknowledgement = acknowledgement.filter(_ != message)
          case None =>
            log.debug("acknowledgement message %s send failed".format(message))
        }
      case _ =>
        log.warn("unable to sent %s: hexapod %s not found".format(message, message.sourceHexapod))
    }
  }
  protected def deliverMessage(message: Message) = synchronized {
    try {
      assert(buffer.contains(message.word), "unable to deliver message " + message)
      Mesh(message.sourceHexapod) match {
        case Some(hexapod: AppHexapod) =>
          hexapod.send(message) match {
            case Some(endpoint) =>
              log.debug("message %s sent".format(message))
              buffer(message.word).condition = Communication.Condition.Sent
              buffer(message.word).counter += 1
              publish(Communication.Event.Sent(message))
            case None =>
              log.debug("message %s send failed".format(message))
          }
        case _ =>
          log.warn("unable to sent %s: hexapod %s not found".format(message, message.sourceHexapod))
      }
    } catch {
      case e: Throwable =>
        log.error(e.getMessage(), e)
    }
  }
  @log
  protected def compactMessages() = synchronized {
    acknowledgement.foreach {
      acknowledgement =>
        if ((System.currentTimeMillis() - acknowledgement.timestamp) > acknowledgement.timeToLive) {
          log.debug("drop acknowledgement message %s, hold time expired".format(acknowledgement))
          this.acknowledgement = this.acknowledgement.filter(_ != acknowledgement)
        }
    }
    buffer.foreach {
      case (word, parcel @ Communication.Parcel(message, Communication.Condition.Pending)) =>
        if (System.currentTimeMillis() - message.timestamp > deliverTTL * deliverMax) {
          log.debug("conversation %s with message %s failed, delivery time expired".format(message.conversation, message))
          buffer.remove(word)
          deliverMessageCounter.remove(word)
          publish(Communication.Event.Fail(message))
        }
      case (word, parcel @ Communication.Parcel(message, Communication.Condition.Sent)) =>
        if ((System.currentTimeMillis() - message.timestamp) / deliverTTL == parcel.counter) {
          if (parcel.counter >= deliverMax) {
            log.debug("conversation %s with message %s failed, delivery time expired".format(message.conversation, message))
            buffer.remove(word)
            deliverMessageCounter.remove(word)
            publish(Communication.Event.Fail(message))
          }
        }
      case (word, parcel @ Communication.Parcel(message, Communication.Condition.Delivered)) =>
        if ((System.currentTimeMillis() - message.timestamp) > message.timeToLive) {
          log.debug("conversation %s with message %s failed, hold time expired".format(message.conversation, message))
          buffer.remove(word)
          deliverMessageCounter.remove(word)
          publish(Communication.Event.Fail(message))
        }
    }
  }
  override def toString = "default communication implemetation"
}

object Communication extends DependencyInjection.PersistentInjectable with Loggable {
  assert(org.digimead.digi.lib.mesh.isReady, "Mesh not ready, please build it first")
  type Pub = Publisher[Event]
  type Sub = Subscriber[Event, Pub]
  implicit def communication2implementation(communication: Communication.type): Interface = communication.inner
  implicit def bindingModule = DependencyInjection()
  Hexapod // start initialization if needed
  Peer // start initialization if needed

  /*
   * dependency injection
   */
  def inner() = inject[Interface]
  override def injectionAfter(newModule: BindingModule) {
    inner.init
  }
  override def injectionBefore(newModule: BindingModule) {
    DependencyInjection.assertLazy[Interface](None, newModule)
  }
  override def injectionOnClear(oldModule: BindingModule) {
    inner.deinit()
  }

  trait Interface extends Communication.Pub with Receptor with Loggable {
    /** Number of deliver attempt */
    val deliverMax: Int
    /** Amount of time for deliver attempt */
    val deliverTTL: Long
    /** Amount of time that message hold in communication buffer */
    val holdTTL: Long
    /** registry of global callbacks */
    protected var global: Seq[Receptor]
    protected var acknowledgement: Seq[Acknowledgement]
    protected val buffer: HashMap[String, Parcel]

    def registerGlobal(receptor: Receptor)
    def unregisterGlobal(receptor: Receptor)
    def acknowledge(conversationHash: Int)
    def push(message: Message, force: Boolean = false): Boolean
    def processMessages()
    protected[communication] def init()
    protected[communication] def deinit()
    override protected def publish(event: Communication.Event) = try {
      super.publish(event)
    } catch {
      case e: Throwable =>
        log.error(e.getMessage(), e)
    }
  }

  /**
   * message wrapper, that contain delivery status
   */
  case class Parcel(val message: Message, initialCondition: Condition) {
    @volatile var condition: Condition = initialCondition
    @volatile var counter: Int = 0
  }

  sealed trait Condition
  object Condition {
    case object Pending extends Condition
    case object Sent extends Condition
    case object Delivered extends Condition
  }

  sealed trait Event
  object Event {
    case class Add(message: Message) extends Event
    case class Sent(message: Message) extends Event
    case class Delivered(message: Message) extends Event
    case class Success(message: Message) extends Event
    case class Fail(message: Message) extends Event
  }
}
