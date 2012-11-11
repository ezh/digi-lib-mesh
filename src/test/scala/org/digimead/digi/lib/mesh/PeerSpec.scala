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

package org.digimead.digi.lib.mesh

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.Peer.peer2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.lib.test.TestHelperLogging
import org.digimead.lib.test.TestHelperMatchers
import org.scalatest.PrivateMethodTester
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.NewBindingModule

class PeerSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging with PrivateMethodTester with TestHelperMatchers {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    val custom = new NewBindingModule(module => {
      lazy val peerSingleton = DependencyInjection.makeInitOnce(implicit module => new MyPeer)
      module.bind[Peer.Interface] toModuleSingle { peerSingleton(_) }
    })
    DependencyInjection.set(custom ~ org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("A Peer") {
    it("should be the same even after reinitialization") {
      config =>
        Peer().foreach(Peer.remove)
        resetConfig()
        val peer1 = DependencyInjection().inject[Peer.Interface](None)
        resetConfig()
        val peer2 = DependencyInjection().inject[Peer.Interface](None)
        peer1 should be theSameInstanceAs peer2
    }
    it("should register and unregister hexapods") {
      config =>
        Mesh().foreach(Mesh.unregister)
        Peer().foreach(Peer.remove)
        resetConfig()
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
    it("should provide access to registered entities") {
      config =>
        Peer().foreach(Peer.remove)
        resetConfig()
        val pool = Peer.inner.asInstanceOf[MyPeer].getPool
        val hexapod = Hexapod(UUID.randomUUID())
        Peer() should be('empty)
        Peer.add(hexapod) should be(true)
        Peer() should not be ('empty)
    }
    it("should publish register and unregister events") {
      config =>
        Peer().foreach(Peer.remove)
        resetConfig()
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

  class MyPeer(implicit override val bindingModule: BindingModule) extends Peer {
    def getPool = pool
  }
}
