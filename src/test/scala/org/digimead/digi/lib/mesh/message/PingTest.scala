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
import org.digimead.digi.lib.mesh.Mesh
import org.digimead.digi.lib.mesh.Peer
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.LoopbackEndpoint
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class PingTestMultiJvmNode1 extends FunSuite with BeforeAndAfter with ShouldMatchers {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log") || System.getProperty("log") != null)
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
    Peer.init(new Peer.DefaultInit)
    Hexapod.init(new AppHexapod(UUID.randomUUID()))
    Communication.init(new Communication.DefaultInit)
    Ping.init(new Ping.DefaultInit)
    DiffieHellmanReq.init(new DiffieHellmanReq.DefaultInit)
    DiffieHellmanRes.init(new DiffieHellmanRes.DefaultInit)
    Mesh.isReady
  }

  after {
    Logging.deinit
  }

  test("ping (de)serialization test") {
    conf =>
      val sourceHexapod = new Hexapod(UUID.randomUUID())
      val destinationHexapod = new Hexapod(UUID.randomUUID())
      val transportEndpoint = new LoopbackEndpoint(new Endpoint.TransportIdentifier {}, new WeakReference(null), Endpoint.InOut)
      val pingA = Ping(sourceHexapod.uuid, Some(destinationHexapod.uuid))
      val rawMessage = pingA.createRawMessage(sourceHexapod, destinationHexapod, None)
      rawMessage.length should be > (0)
      log.___glance("raw message length: " + rawMessage.length + " byte")
      val deserializedMessage = Message.parseRawMessage(rawMessage) match {
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
}
