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

package org.digimead.digi.lib.mesh

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.Peer.peer2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.lib.test.LoggingHelper
import org.digimead.lib.test.MatcherHelper
import org.scalatest.PrivateMethodTester
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.NewBindingModule

class PeerSpec extends WordSpec with ShouldMatchers with LoggingHelper with PrivateMethodTester with MatcherHelper with Loggable {
  val custom = new NewBindingModule(module => {
    lazy val peerSingleton = DependencyInjection.makeInitOnce(implicit module => new MyPeer)
    module.bind[Peer.Interface] toModuleSingle { peerSingleton(_) }
  })

  after { adjustLoggingAfter }
  before {
    DependencyInjection(custom ~ org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  "A Peer" should {
    "be the same even after reinitialization" in {
      val peer1 = DependencyInjection().inject[Peer.Interface](None)
      Peer().foreach(Peer.remove)
      val peer2 = DependencyInjection().inject[Peer.Interface](None)
      peer1 should be theSameInstanceAs peer2
    }
    "register and unregister hexapods" in {
      Mesh().foreach(Mesh.unregister)
      Peer().foreach(Peer.remove)
      val pool = Peer.inner.asInstanceOf[MyPeer].getPool
      val hexapod = Hexapod(UUID.randomUUID())
      pool should be('empty)
      Mesh(hexapod.uuid) should not be ('empty)
      Peer.add(hexapod) should be(true)
      Mesh(hexapod.uuid) should be(Some(hexapod))
      pool should contain(hexapod)
      Peer.add(hexapod) should be(false)

      Peer.remove(hexapod) should be(true)
      pool should be('empty)
      Peer.remove(hexapod) should be(false)
    }
    "provide access to registered entities" in {
      Peer().foreach(Peer.remove)
      val pool = Peer.inner.asInstanceOf[MyPeer].getPool
      val hexapod = Hexapod(UUID.randomUUID())
      Peer() should be('empty)
      Peer.add(hexapod) should be(true)
      Peer() should not be ('empty)
    }
    "should publish register and unregister events" in {
      Peer().foreach(Peer.remove)

      var event: Peer.Event = null
      val subscriber = new Peer.Sub {
        override def notify(pub: Peer.Pub, evt: Peer.Event) {
          assert(event == null)
          event = evt
        }
      }
      Peer.subscribe(subscriber)
      val hexapod = Hexapod(UUID.randomUUID())
      Peer.add(hexapod)
      expectDefined(event) { case Peer.Event.Add(hexapod) => }

      event = null
      Peer.remove(hexapod)
      expectDefined(event) { case Peer.Event.Remove(hexapod) => }
      Peer.removeSubscription(subscriber)
    }
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }

  class MyPeer(implicit override val bindingModule: BindingModule) extends Peer {
    def getPool = pool
  }
}
