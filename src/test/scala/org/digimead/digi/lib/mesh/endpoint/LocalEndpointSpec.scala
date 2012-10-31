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

package org.digimead.digi.lib.mesh.endpoint

import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.lib.test.TestHelperLogging
import org.scala_tools.subcut.inject.NewBindingModule
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

class LocalEndpointSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("A LocalEndpoint") {
    it("should have (de)serialization via signature") {
      config =>
        val epaddr = UUID.randomUUID()
        val ep = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.InOut, new LocalEndpoint.Nature(epaddr))
        ep.nature.address should be(epaddr.toString())
        ep.nature.toString should be("local://" + epaddr.toString())
        ep.signature should be("local'" + epaddr.toString() + "'30'InOut'")
        val serialized = ep.signature
        val epCopy = Endpoint.fromSignature(null, serialized)
        epCopy.get.nature.toString should be(ep.nature.toString)
        epCopy.get.signature.toString should be(ep.signature.toString)
    }
  }
}
