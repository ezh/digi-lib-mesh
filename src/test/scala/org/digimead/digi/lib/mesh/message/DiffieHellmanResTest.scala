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

import org.digimead.digi.lib.log.ConsoleLogger
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.mesh.Hub
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LoopbackEndpoint
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class DiffieHellmanResTest  extends FunSuite with BeforeAndAfter with ShouldMatchers {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log"))
        Logging.addLogger(ConsoleLogger)
      test(test.configMap)
    } finally {
      Logging.delLogger(ConsoleLogger)
    }
  }

  before {
    Record.init(new Record.DefaultInit)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
    Mesh.init(new Mesh.DefaultInit)
    Hub.init(new Hub.DefaultInit)
  }

  after {
    Logging.deinit
  }

  test("DiffieHellmanRes (de)serialization test") {
    conf =>
      val sourceHexapod = new Hexapod(UUID.randomUUID())
      val destinationHexapod = new Hexapod(UUID.randomUUID())
      val transportEndpoint = new LoopbackEndpoint(UUID.randomUUID, "userA@email", "deviceAIMEI",
        new Endpoint.TransportIdentifier {}, new WeakReference(null), Endpoint.InOut)
      val publicKey = 123
      val resA = DiffieHellmanRes(publicKey, sourceHexapod.uuid, Some(destinationHexapod.uuid), Some(transportEndpoint.uuid))
      val rawMessage = resA.createRawMessage(sourceHexapod, destinationHexapod, transportEndpoint, None)
      rawMessage.length should be > (0)
      log.___glance("raw message length: " + rawMessage.length + " byte")
      val deserializedMessage = Message.parseRawMessage(rawMessage, transportEndpoint) match {
        case Some((message, remoteEndpointUUID)) =>
          log.debug("receive message \"%s\" from %s via remote endpoint %s".format(message.word, message.sourceHexapod, remoteEndpointUUID))
          remoteEndpointUUID should equal(transportEndpoint.uuid)
          Some(message)
        case None =>
          None
      }
      deserializedMessage should not be (None)
      val resB = deserializedMessage.get
      assert(resA === resB)
      log.___glance("original res: " + resB)
      log.___glance("(de)serialized res: " + resB)
      assert(resA.toString === resB.toString)
  }
}
