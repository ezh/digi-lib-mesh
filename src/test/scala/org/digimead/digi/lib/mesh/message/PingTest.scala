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

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.enc.{ DiffieHellman => DiffieHellmanEnc }
import org.digimead.digi.lib.enc.Simple
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.lib.test.LoggingHelper
import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

class PingTest extends FunSuite with ShouldMatchers with LoggingHelper with Loggable {
  @volatile private var init = false
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.defaultFakeHexapod ~
      org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  test("ping (de)serialization test") {
    val sourceHexapod = Hexapod(UUID.randomUUID())
    val destinationHexapod = Hexapod(UUID.randomUUID())
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
    val dhPeer = new DiffieHellmanEnc(5, DiffieHellmanEnc.randomPrime(128))
    val sourceHexapod = Hexapod(UUID.randomUUID())
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

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
