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

package org.digimead.digi.lib.mesh.message

import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.enc.{ DiffieHellman => DiffieHellmanEnc }
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.logger.RichLogger.rich2slf4j
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LocalEndpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.lib.test.TestHelperLogging
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class PingTest_j1 extends FunSuite with ShouldMatchers with TestHelperLogging {
  type FixtureParam = Map[String, Any]
  @volatile private var init = false

  override def withFixture(test: OneArgTest) {
    DependencyInjection.get.foreach(_ => DependencyInjection.clear)
    DependencyInjection.set(org.digimead.digi.lib.mesh.default ~ defaultConfig(test.configMap), { Mesh })
    Mesh
    withLogging(test.configMap) {
      test(test.configMap)
    }
  }

  test("ping (de)serialization test") {
    conf =>
      val sourceHexapod = Hexapod(UUID.randomUUID())
      val destinationHexapod = Hexapod(UUID.randomUUID())
      val transportEndpoint = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.InOut)
      val pingA = Ping(sourceHexapod.uuid, Some(destinationHexapod.uuid))(true)
      val rawMessage = pingA.createRawMessage(sourceHexapod, destinationHexapod, None)
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
      val pingB = deserializedMessage.get
      assert(pingA === pingB)
      log.___glance("original ping: " + pingA)
      log.___glance("(de)serialized ping: " + pingB)
      assert(pingA.toString === pingB.toString)
  }

  test("ping encripted (de)serialization test") {
    conf =>
      val dhPeer = new DiffieHellmanEnc(5, DiffieHellmanEnc.randomPrime(128))
      val sourceHexapod = Hexapod(UUID.randomUUID())
      val transportEndpoint = new LocalEndpoint(new WeakReference(null), Endpoint.Direction.InOut)
      val pingA = Ping(sourceHexapod.uuid, Some(Hexapod.uuid))(true)
      pingA.distance should be(0)
      val sharedKey = Hexapod.getDiffieHellman.get.getSharedKey(dhPeer.publicKey)
      val rawMessage = pingA.createRawMessage(sourceHexapod, Hexapod.inner, Some(Simple.getRawKey(sharedKey.toByteArray)))
      rawMessage.length should be > (0)
      log.___glance("raw message length: " + rawMessage.length + " byte")
      val emptyMessage = Message.parseRawMessage(rawMessage, false) match {
        case Some(message) =>
          log.debug("receive message \"%s\" from %s".format(message.word, message.sourceHexapod))
          Some(message)
        case None =>
          None
      }
      assert(emptyMessage === None)
      Hexapod.getDiffieHellman should not be ('empty)
      sourceHexapod.setDiffieHellman(dhPeer.g, dhPeer.p, dhPeer.publicKey)
      val deserializedMessage = Message.parseRawMessage(rawMessage, false) match {
        case Some(message) =>
          log.debug("receive message \"%s\" from %s".format(message.word, message.sourceHexapod))
          Some(message)
        case None =>
          None
      }
      deserializedMessage should not be ('empty)
      deserializedMessage.get.distance should be(1)
      assert(deserializedMessage === Some(pingA))
  }
}
