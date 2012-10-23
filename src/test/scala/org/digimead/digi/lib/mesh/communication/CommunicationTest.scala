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

import java.util.UUID
import scala.collection.mutable.SynchronizedQueue
import org.digimead.digi.lib.log.Loggable
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.DiffieHellman
import org.digimead.digi.lib.mesh.message.Ping
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers
import org.digimead.digi.lib.DependencyInjection
import org.scala_tools.subcut.inject.NewBindingModule

class CommunicationTest_j1 extends FunSuite with ShouldMatchers with BeforeAndAfter with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap))
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  test("communication test") {
    conf =>
      val events = new SynchronizedQueue[Any]
      val custom =  new NewBindingModule(module => {
        module.bind[Hexapod.AppHexapod] toSingle { new AppHexapod(UUID.randomUUID()) }
      })

      val comm = new Communication {
        def getBuffer = buffer
        def getDeliverMessageCounter = deliverMessageCounter
        def getGlobal = global
      }
      Communication.init(new Communication.DefaultInit {
        override val implementation: Communication.Interface = comm
        override val deliverTTL = 1000L
      })
      Ping.init(new Ping.DefaultInit)
      DiffieHellman.init(new DiffieHellman.DefaultInit)

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
        case e => log.error("unextected event " + e); false
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
        case e => log.error("unextected event " + e); false
      }) should be(true)

      log.___glance("ACKNOWLEDGMENT")
      Communication.push(ping) should equal(true)
      events.clear
      Communication.acknowledge(ping.conversation.hashCode())
      (events.dequeue match {
        case Communication.Event.Delivered(msg: Ping) => true
        case e => log.error("unextected event " + e); false
      }) should be(true)
      Thread.sleep(1000)
      Communication.processMessages
      (events.dequeue match {
        case Communication.Event.Fail(msg: Ping) => true
        case e => log.error("unextected event " + e); false
      }) should be(true)
  }
}
