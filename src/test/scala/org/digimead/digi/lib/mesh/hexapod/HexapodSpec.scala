package org.digimead.digi.lib.mesh.hexapod

import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers
import org.digimead.lib.test.TestHelperLogging
import org.digimead.digi.lib.DependencyInjection
import org.scala_tools.subcut.inject.NewBindingModule
import org.digimead.digi.lib.mesh.Mesh
import java.util.UUID
import org.digimead.digi.lib.mesh.endpoint.LocalEndpoint
import scala.ref.WeakReference
import org.digimead.digi.lib.mesh.endpoint.Endpoint

class HexapodSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("A Hexapod") {
    it("should find proper links") {
      config =>
        val hexapodA = Hexapod(UUID.randomUUID)
        val hexapodALocalIn = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.In, new LocalEndpoint.Nature(UUID.randomUUID()))
        hexapodA.registerEndpoint(hexapodALocalIn)
        val hexapodB = Hexapod(UUID.randomUUID)
        log.___glance("hexapodA " + hexapodA.hashCode())
        log.___glance("hexapodB " + hexapodB.hashCode())
        assert(hexapodA != hexapodB, "hexapod the same")
        val hexapodBLocalIn = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.Out, new LocalEndpoint.Nature(UUID.randomUUID()))
        hexapodA.getLinks(hexapodB) should be ('empty)
        hexapodA.registerEndpoint(hexapodALocalIn)
        hexapodA.getLinks(hexapodB) should be ('empty)
        hexapodA.getLinks(hexapodB, false) should not be ('empty)
        
    }
  }
}