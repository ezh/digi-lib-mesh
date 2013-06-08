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

import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.Mesh.mesh2implementation
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.lib.test.LoggingHelper
import org.digimead.lib.test.MatcherHelper
import org.scalatest.PrivateMethodTester
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.NewBindingModule

class MeshSpec extends WordSpec with ShouldMatchers with MatcherHelper with LoggingHelper with Loggable with PrivateMethodTester {
  val custom = new NewBindingModule(module => {
    module.bind[Mesh.Interface] toModuleSingle { implicit module => new MyMesh }
  })

  after { adjustLoggingAfter }
  before {
    DependencyInjection(custom ~ org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  "A Mesh" should {
    "be the same even after reinitialization" in {
      val mesh1 = DependencyInjection().inject[Peer.Interface](None)
      Mesh().foreach(Mesh.unregister)
      val mesh2 = DependencyInjection().inject[Peer.Interface](None)
      mesh1 should be theSameInstanceAs mesh2
    }
    "register and unregister hexapods and do garbage collection" in {
      Mesh().foreach(Mesh.unregister)
      log.___glance("begin clearing Mesh")
      // Mesh persistent
      log.___glance("complete clearing Mesh")
      val entity = Mesh.inner.asInstanceOf[MyMesh].getEntity
      val gcLimit = Mesh.inner.asInstanceOf[MyMesh].getGCLimit
      val gcCounter = Mesh.inner.asInstanceOf[MyMesh].getGCCounter
      log.___glance("start tests " + Mesh().mkString(","))
      gcCounter.get should equal(gcLimit)
      entity should be('empty)
      log.___glance("stop tests " + Mesh().mkString(","))

      val hexapod = Hexapod(UUID.randomUUID())
      gcCounter.get should be(gcLimit - 1)
      entity should have size (1)
      Mesh.register(hexapod) should be(false)

      Mesh.unregister(hexapod) should be(true)
      gcCounter.get should be(gcLimit - 1)
      entity should be('empty)
      Mesh.unregister(hexapod) should be(false)

      Mesh.register(hexapod)
      gcCounter.set(1)
      for (i <- 1 to gcLimit - 2)
        entity(UUID.randomUUID()) = new WeakReference(null)
      entity should have size (gcLimit - 1)
      Hexapod(UUID.randomUUID())
      entity.size should be(2)
      gcCounter.get should be(gcLimit)
    }
    "provide access to registered entities" in {
      Mesh().foreach(Mesh.unregister)
      val entity = Mesh.inner.asInstanceOf[MyMesh].getEntity
      val hexapod = Hexapod(UUID.randomUUID())

      // AppHexapod already registered
      entity should have size (1)
      Mesh(hexapod.uuid) should be(Some(hexapod))
      Mesh() should have size (1)
    }
    "should publish register and unregister events" in {
      Mesh().foreach(Mesh.unregister)
      var event: Mesh.Event = null
      val subscriber = new Mesh.Sub {
        override def notify(pub: Mesh.Pub, evt: Mesh.Event) {
          assert(event == null)
          event = evt
        }
      }
      Mesh.subscribe(subscriber)
      val hexapod = Hexapod(UUID.randomUUID())
      expectDefined(event) { case Mesh.Event.Register(hexapod) => }

      event = null
      Mesh.unregister(hexapod)
      expectDefined(event) { case Mesh.Event.Unregister(hexapod) => }
      Mesh.removeSubscription(subscriber)
    }
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }

  class MyMesh(implicit override val bindingModule: BindingModule) extends Mesh {
    def getEntity = entity
    def getGCLimit = gcLimit
    def getGCCounter = gcCounter
  }
  // val gcLimitFactory = PrivateMethod[Int]('gcLimit)
  // val gcCouterFactory = PrivateMethod[AtomicInteger]('gcCouter)}
  // Mesh.instance invokePrivate gcLimitFactory()
}
