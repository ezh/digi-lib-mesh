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

import java.util.UUID

import scala.collection.mutable.SynchronizedQueue

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.message.Ping
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.NewBindingModule

class CommunicationTest extends FunSuite with ShouldMatchers with LoggingHelper with Loggable {
  val custom = new NewBindingModule(module => {
    module.bind[AppHexapod] toSingle { new AppHexapod(UUID.randomUUID()) }
    lazy val communicationSingleton = DependencyInjection.makeInitOnce(implicit module => new MyCommunication)
    module.bind[Communication.Interface] toModuleSingle { communicationSingleton(_) }
    module.bind[Long] identifiedBy ("Mesh.Communication.DeliverTTL") toSingle { 1000L }
  })
  after { adjustLoggingAfter }
  before {
    DependencyInjection(custom ~ org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  test("communication test") {
    val events = new SynchronizedQueue[Any]
    val comm = Communication.inner.asInstanceOf[MyCommunication]

    Communication.subscribe(new Communication.Sub {
      def notify(pub: Communication.Pub, event: Communication.Event) {
        log.___glance("CE:" + event)
        events += event
      }
    })

    val ping = Ping(UUID.randomUUID(), None, UUID.randomUUID(), 1000)(true)
    Communication.push(ping) should equal(true)
    Communication.push(ping) should equal(false)
    comm.getBuffer should have size (1)
    comm.getBuffer(ping.word).condition should be(Communication.Condition.Pending)
    comm.getBuffer(ping.word).condition = Communication.Condition.Delivered

    events.clear

    log.___glance("REACT for delivered")
    Communication.react(Stimulus.IncomingMessage(ping))

    (events.dequeue match {
      case Communication.Event.Success(msg: Ping) => true
      case e => log.error("unexpected event " + e); false
    }) should be(true)
    events should have size (0)
    comm.getBuffer should have size (0)

    log.___glance("REACT for sent")
    Communication.push(ping) should equal(true)
    comm.getBuffer(ping.word).condition = Communication.Condition.Sent
    events.clear
    Communication.react(Stimulus.IncomingMessage(ping))
    (events.dequeue match {
      case Communication.Event.Success(msg: Ping) => true
      case e => log.error("unexpected event " + e); false
    }) should be(true)

    log.___glance("ACKNOWLEDGMENT")
    Communication.push(ping) should equal(true)
    events.clear
    Communication.acknowledge(ping.conversation.hashCode())
    (events.dequeue match {
      case Communication.Event.Delivered(msg: Ping) => true
      case e => log.error("unexpected event " + e); false
    }) should be(true)
    Thread.sleep(1000)
    Communication.processMessages
    (events.dequeue match {
      case Communication.Event.Fail(msg: Ping) => true
      case e => log.error("unexpected event " + e); false
    }) should be(true)
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }

  class MyCommunication(implicit override val bindingModule: BindingModule) extends Communication {
    def getBuffer = buffer
    def getDeliverMessageCounter = deliverMessageCounter
    def getGlobal = global
  }
}
