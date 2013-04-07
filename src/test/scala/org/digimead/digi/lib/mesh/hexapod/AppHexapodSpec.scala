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

package org.digimead.digi.lib.mesh.hexapod

import java.util.UUID

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.Peer.peer2implementation
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LocalEndpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.mesh.message.Ping
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.fixture.FunSpec
import org.scalatest.matchers.ShouldMatchers

import com.escalatesoft.subcut.inject.NewBindingModule

class AppHexapodSpec_j1 extends FunSpec with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.mesh.defaultFakeHexapod ~ defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  def resetConfig(newConfig: NewBindingModule = new NewBindingModule(module => {})) = DependencyInjection.reset(newConfig ~ DependencyInjection())

  describe("An AppHexapod") {
    it("should collect all available destination hexapods in proper order") {
      config =>
        val hexapod1 = Hexapod(UUID.randomUUID())
        Peer.add(hexapod1)
        val hexapod2 = Hexapod(UUID.randomUUID())
        Peer.add(hexapod2)
        val hexapod3 = Hexapod(UUID.randomUUID())
        Peer.add(hexapod3)
        val hexapod4NotInPeer = Hexapod(UUID.randomUUID())
        val hexapod5OnlyEPOut = Hexapod(UUID.randomUUID())
        // message from AppHexapod to any
        val messageToAnyHexapod = Ping(Hexapod.uuid, None)(true)
        // message from AppHexapod to particular hexapod that not available
        val messageToParticularHexapodNotInPeer = Ping(Hexapod.uuid, None)(true)
        // message from AppHexapod to particular hexapod that available
        val messageToParticularHexapodInPeer = Ping(Hexapod.uuid, None)(true)
        // hexapod without endpoints
        /*Hexapod.getDestination(messageToAnyHexapod) should be('empty)
        Hexapod.getDestination(messageToParticularHexapodNotInPeer) should be('empty)
        Hexapod.getDestination(messageToParticularHexapodInPeer) should be('empty)*/
        // register destination endpoints
        val ep1 = hexapod1.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut)(_))
        ep1.actualPriority = Endpoint.Priority.NONE.id
        val ep2 = hexapod2.addEndpoint(new LocalEndpoint(Endpoint.Direction.InOut)(_))
        ep2.actualPriority = Endpoint.Priority.LOW.id
        val ep3 = hexapod3.addEndpoint(new LocalEndpoint(Endpoint.Direction.In)(_))
        ep3.actualPriority = Endpoint.Priority.MEDUIM.id
        val ep4 = hexapod4NotInPeer.addEndpoint(new LocalEndpoint(Endpoint.Direction.In)(_))
        val ep5 = hexapod5OnlyEPOut.addEndpoint(new LocalEndpoint(Endpoint.Direction.Out)(_))
      // hexapod with endpoints
      /*Hexapod.getDestination(messageToAnyHexapod) should be('empty)
        Hexapod.getDestination(messageToParticularHexapodNotInPeer) should be('empty)
        Hexapod.getDestination(messageToParticularHexapodInPeer) should be('empty)*/
      /*Hexapod.getDestination(messageToAnyHexapod) should be(Seq(hexapod3, hexapod2))
        val toNotInPeer = Hexapod.getDestination(messageToParticularHexapodNotInPeer)
        val toInPeer = Hexapod.getDestination(messageToParticularHexapodInPeer)*/
    }
  }
}
