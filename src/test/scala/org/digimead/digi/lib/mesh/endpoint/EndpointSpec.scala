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

package org.digimead.digi.lib.mesh.endpoint

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.NewBindingModule

class EndpointSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.defaultFakeHexapod ~ org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("An Endpoint") {
    it("should should proper comparison of nature based on address") {
      config =>
        val ep1 = new LocalEndpoint.Nature(UUID.fromString("00000000-0000-0000-0000-000000000000"))
        val ep2 = new LocalEndpoint.Nature(UUID.randomUUID())
        val ep3 = new LocalEndpoint.Nature(UUID.fromString("00000000-0000-0000-0000-000000000000"))
        ep1 should not be (ep2)
        ep1 should be(ep3)
    }
  }
}
