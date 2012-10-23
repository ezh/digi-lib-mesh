package org.digimead.digi.lib.mesh

import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.digimead.lib.test.TestHelperLogging
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import java.util.UUID
import org.scalatest.PrivateMethodTester
import scala.collection.mutable.HashMap
import scala.ref.WeakReference
import org.scala_tools.subcut.inject.BindingModule
import org.scala_tools.subcut.inject.NewBindingModule
import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.BeforeAndAfter
import org.digimead.lib.test.TestHelperMatchers

class MeshSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging with PrivateMethodTester with TestHelperMatchers {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    val custom = new NewBindingModule(module => {
      module.bind[Mesh.Interface] toModuleSingle { implicit module => new MyMesh }
    })
    DependencyInjection.set(custom ~ org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap))
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig() = DependencyInjection.reset(DependencyInjection() ~ new NewBindingModule(module => {}))

  describe("A Mesh") {
    it("should be the same even after reinitialization") {
      config =>
        resetConfig
        val mesh1 = DependencyInjection().inject[Peer.Interface](None)
        resetConfig
        val mesh2 = DependencyInjection().inject[Peer.Interface](None)
        mesh1 should be theSameInstanceAs mesh1
    }
    it("should register and unregister hexapods and do garbage collection") {
      conf =>
        resetConfig
        val entity = Mesh.instance.asInstanceOf[MyMesh].getEntity
        val gcLimit = Mesh.instance.asInstanceOf[MyMesh].getGCLimit
        val gcCounter = Mesh.instance.asInstanceOf[MyMesh].getGCCounter
        val hexapod = new Hexapod(UUID.randomUUID())
        gcCounter.get should equal(gcLimit)
        entity should be('empty)

        Mesh.register(hexapod) should be(true)
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
        Mesh.register(new Hexapod(UUID.randomUUID()))
        entity.size should be(2)
        gcCounter.get should be(gcLimit)
    }
    it("should provide access to registered entities") {
      config =>
        resetConfig
        val entity = Mesh.instance.asInstanceOf[MyMesh].getEntity
        val hexapod = new Hexapod(UUID.randomUUID())

        Mesh.register(hexapod) should be(true)
        entity should have size (1)
        Mesh(hexapod.uuid) should be(Some(hexapod))
        Mesh() should not be ('empty)
    }
    it("should publish register and unregister events") {
      config =>
        resetConfig
        var event: Mesh.Event = null
        val subscriber = new Mesh.Sub {
          override def notify(pub: Mesh.Pub, evt: Mesh.Event) {
            assert(event == null)
            event = evt
          }
        }
        Mesh.subscribe(subscriber)
        val hexapod = new Hexapod(UUID.randomUUID())
        Mesh.register(hexapod)
        expectDefined(event) { case Mesh.Event.Register(hexapod) => }

        event = null
        Mesh.unregister(hexapod)
        expectDefined(event) { case Mesh.Event.Unregister(hexapod) => }
        Mesh.removeSubscription(subscriber)
    }
  }

  class MyMesh(implicit override val bindingModule: BindingModule) extends Mesh {
    def getEntity = entity
    def getGCLimit = gcLimit
    def getGCCounter = gcCounter
  }
  // val gcLimitFactory = PrivateMethod[Int]('gcLimit)
  // val gcCouterFactory = PrivateMethod[AtomicInteger]('gcCouter)}
  // Mesh.instance invokePrivate gcLimitFactory()
}
