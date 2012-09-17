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

package org.digimead.digi.lib.mesh

import java.net.InetAddress
import java.util.UUID

import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.ConsoleLogger
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.CommunicationEvent
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.UDPEndpoint
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.DiffieHellmanReq
import org.digimead.digi.lib.mesh.message.DiffieHellmanRes
import org.digimead.digi.lib.mesh.message.Ping
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers

class MeshTestMultiJvmNode1 extends FunSuite with ShouldMatchers with BeforeAndAfter {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log") || System.getProperty("log").nonEmpty)
        Logging.addLogger(ConsoleLogger)
      test(test.configMap)
    } finally {
      Logging.delLogger(ConsoleLogger)
    }
  }

  before {
    Record.init(new Record.InitWithDiagnosticContext)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
  }

  after {
    Logging.deinit
  }

  test("node 1 ping") {
    config =>
      Mesh.init(new Mesh.DefaultInit)
      Communication.init(new Communication.DefaultInit)
      DiffieHellmanReq.init(new DiffieHellmanReq.DefaultInit)
      DiffieHellmanRes.init(new DiffieHellmanRes.DefaultInit)
      Hub.init(new Hub.DefaultInit)
      val local = new AppHexapod(UUID.randomUUID)
      val localEndpointIn = new UDPEndpoint(UUID.randomUUID, "userA@email", "deviceAIMEI",
        UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23456)), new WeakReference(local), Endpoint.In)
      val localEndpointOut = new UDPEndpoint(UUID.randomUUID, "userA@email", "deviceAIMEI",
        UDPEndpoint.TransportIdentifier(), new WeakReference(local), Endpoint.Out)
      Hexapod.init(local)

      @volatile var pingSendStatus = true
      Communication.subscribe(new Communication.Sub {
        def notify(pub: Communication.Pub, event: CommunicationEvent) = event match {
          case Communication.Event.Fail(message: Ping) =>
            pingSendStatus = false
          case event =>
        }
      })
      Communication.push(new Ping(local.uuid, None, None))
      pingSendStatus should equal(false)

      local.connect()
      log.___glance("CONNECT LOCAL")

      localEndpointIn.connected should equal(false)
      localEndpointOut.connected should equal(false)

      local.connect()
  }
}

class MeshTestMultiJvmNode2 extends FunSuite with ShouldMatchers with BeforeAndAfter {
  type FixtureParam = Map[String, Any]
  val log = Logging.commonLogger

  override def withFixture(test: OneArgTest) {
    try {
      if (test.configMap.contains("log") || System.getProperty("log").nonEmpty)
        Logging.addLogger(ConsoleLogger)
      test(test.configMap)
    } finally {
      Logging.delLogger(ConsoleLogger)
    }
  }

  before {
    Record.init(new Record.InitWithDiagnosticContext)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
  }

  after {
    Logging.deinit
  }

  test("node 2 pong") {
    config =>
  }
  /*finally {
      Logging.delLogger(ConsoleLogger)
    }
  }

  before {
    Record.init(new Record.InitWithDiagnosticContext)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
  }

  after {
    Logging.deinit
  }

  test("loopback mesh test") {
    config =>
      Mesh.init(new Mesh.DefaultInit)
      Communication.init(new Communication.DefaultInit)
      DiffieHellmanReq.init(new DiffieHellmanReq.DefaultInit)
      DiffieHellmanRes.init(new DiffieHellmanRes.DefaultInit)
      Hub.init(new Hub.DefaultInit)
      val local = new AppHexapod(UUID.randomUUID)
      val localEndpointIn = new LoopbackEndpoint(UUID.randomUUID, "userA@email", "deviceAIMEI",
        new Endpoint.TransportIdentifiers {}, new WeakReference(local), Endpoint.In)
      val localEndpointOut = new LoopbackEndpoint(UUID.randomUUID, "userA@email", "deviceAIMEI",
        new Endpoint.TransportIdentifiers {}, new WeakReference(local), Endpoint.Out)
      Hexapod.init(local)

      val remote = new AppHexapod(UUID.randomUUID)
      val remoteEndpointIn = new LoopbackEndpoint(UUID.randomUUID, "userB@email", "deviceBIMEI",
        new Endpoint.TransportIdentifiers {}, new WeakReference(remote), Endpoint.In)
      val remoteEndpointOut = new LoopbackEndpoint(UUID.randomUUID, "userB@email", "deviceBIMEI",
        new Endpoint.TransportIdentifiers {}, new WeakReference(remote), Endpoint.Out)

      Hub.add(remote)

      localEndpointOut.loopbackConnect(remoteEndpointIn)
      remoteEndpointOut.loopbackConnect(localEndpointIn)

      localEndpointIn.connected should equal(false)
      localEndpointOut.connected should equal(false)
      remoteEndpointIn.connected should equal(false)
      remoteEndpointOut.connected should equal(false)

      @volatile var pingSendStatus = true
      Communication.subscribe(new Communication.Sub {
        def notify(pub: Communication.Pub, event: CommunicationEvent) = event match {
          case Communication.Event.Fail(message: Ping) =>
            pingSendStatus = false
          case event =>
        }
      })
      Communication.push(new Ping(local.uuid, None, None))
      pingSendStatus should equal(false)

      log.___glance("CONNECT LOCAL")
      local.connect()
      //log.___glance("CONNECT REMOTE")
      //remote.connect()

      //localEndpointIn.connected should equal(true)
      //localEndpointOut.connected should equal(true)
      //remoteEndpointIn.connected should equal(true)
      //remoteEndpointOut.connected should equal(true)

  }
}*/
} 