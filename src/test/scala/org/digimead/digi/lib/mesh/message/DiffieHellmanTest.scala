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

package org.digimead.digi.lib.mesh.message

import java.util.UUID

import scala.math.BigInt.int2bigInt
import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LocalEndpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class DiffieHellmanTest_j1 extends FunSuite with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.defaultFakeHexapod ~ org.digimead.digi.lib.mesh.default ~
      defaultConfig(test.configMap), { Mesh })
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  test("DiffieHellman (de)serialization test") {
    conf =>
      val sourceHexapod = Hexapod(UUID.randomUUID())
      val destinationHexapod = Hexapod(UUID.randomUUID())
      val publicKey = 1
      val g = 2
      val p = 3
      val reqA = DiffieHellman(publicKey, g, p, sourceHexapod.uuid, Some(destinationHexapod.uuid))(false)
      val rawMessage = reqA.createRawMessage(sourceHexapod, destinationHexapod, None)
      rawMessage.length should be > (0)
      log.___glance("raw message length: " + rawMessage.length + " byte")
      val deserializedMessage = Message.parseRawMessage(rawMessage, false) match {
        case Some(message) =>
          log.debug("receive message \"%s\" from %s".format(message.word, message.sourceHexapod))
          Some(message)
        case None =>
          None
      }
      deserializedMessage should not be (None)
      val reqB = deserializedMessage.get
      assert(reqA === reqB)
      log.___glance("original req: " + reqB)
      log.___glance("(de)serialized req: " + reqB)
      assert(reqA.toString === reqB.toString)
      val reqAX = DiffieHellman(publicKey, g, p, sourceHexapod.uuid, Some(destinationHexapod.uuid), reqA.conversation, reqA.timestamp)(false, 0)
      val reqBX = new DiffieHellman(publicKey, g, p, sourceHexapod.uuid, Some(destinationHexapod.uuid), reqA.conversation, reqA.timestamp)(true, 1)
      assert(reqA.toString === reqAX.toString)
      assert(reqAX === reqBX)
      assert(reqA === reqAX)
  }
}
