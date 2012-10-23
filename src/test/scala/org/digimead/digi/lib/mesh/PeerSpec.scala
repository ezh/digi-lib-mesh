package org.digimead.digi.lib.mesh

import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.scalatest.BeforeAndAfter
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.PrivateMethodTester
import org.digimead.digi.lib.DependencyInjection
import org.scala_tools.subcut.inject.NewBindingModule
import org.scala_tools.subcut.inject.BindingModule
import org.scala_tools.subcut.inject.LazyModuleInstanceProvider
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import java.util.UUID
import org.digimead.lib.test.TestHelperMatchers

class PeerSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging with PrivateMethodTester with TestHelperMatchers {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    val custom = new NewBindingModule(module => {
      lazy val peerSingleton = DependencyInjection.makeSingleton(implicit module => new MyPeer, true)
      module.bind[Peer.Interface] toModuleSingle { peerSingleton(_) }
    })
    DependencyInjection.set(custom ~ org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap))
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig() = DependencyInjection.reset(DependencyInjection() ~ new NewBindingModule(module => {}))

  describe("A Peer") {
    it("should be the same even after reinitialization") {
      config =>
        resetConfig()
        val peer1 = DependencyInjection().inject[Peer.Interface](None)
        resetConfig()
        val peer2 = DependencyInjection().inject[Peer.Interface](None)
        peer1 should be theSameInstanceAs peer2
    }
    it("should register and unregister hexapods") {
      config =>
        resetConfig()
        val pool = Peer.instance.asInstanceOf[MyPeer].getPool
        val hexapod = new Hexapod(UUID.randomUUID())
        pool should be('empty)
        Mesh(hexapod.uuid) should be('empty)
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
        resetConfig()
        val pool = Peer.instance.asInstanceOf[MyPeer].getPool
        val hexapod = new Hexapod(UUID.randomUUID())
        Peer() should be('empty)
        Peer.add(hexapod) should be(true)
        Peer() should not be ('empty)
    }
    it("should publish register and unregister events") {
      config =>
        resetConfig()
        var event: Peer.Event = null
        val subscriber = new Peer.Sub {
          override def notify(pub: Peer.Pub, evt: Peer.Event) {
            assert(event == null)
            event = evt
          }
        }
        Peer.subscribe(subscriber)
        val hexapod = new Hexapod(UUID.randomUUID())
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