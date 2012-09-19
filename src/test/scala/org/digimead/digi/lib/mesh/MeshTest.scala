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

import scala.collection.mutable.SynchronizedQueue
import scala.ref.WeakReference

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.ConsoleLogger
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.digimead.digi.lib.mesh.Peer.hub2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.UDPEndpoint
import org.digimead.digi.lib.mesh.endpoint.UDPRemoteEndpoint
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
      if (test.configMap.contains("log") || System.getProperty("log") != null)
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
      val events = new SynchronizedQueue[Any]

      // subscribe
      Hexapod.Event.subscribe(new Hexapod.Event.Sub {
        def notify(pub: Hexapod.Event.Pub, event: Hexapod.Event) {
          log.___glance("HE:" + event)
          events += event
        }
      })
      Communication.Event.subscribe(new Communication.Event.Sub {
        def notify(pub: Communication.Event.Pub, event: Communication.Event) {
          log.___glance("CE:" + event)
          events += event
        }
      })

      // init
      Mesh.init(new Mesh.DefaultInit)
      val comm = new Communication {
        def getPending = pending
        def getActive = active
        def getProgress = sendPendingProgress
        def getGlobal = global
      }
      Communication.init(new Communication.DefaultInit {
        override val implementation: Communication.Interface = comm
        override val deliverTTL = 1000L
      })
      DiffieHellmanReq.init(new DiffieHellmanReq.DefaultInit)
      DiffieHellmanRes.init(new DiffieHellmanRes.DefaultInit)
      Peer.init(new Peer.DefaultInit)
      val local = new AppHexapod(Common.node1UUID)
      val localEndpointIn = new UDPEndpoint(UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23456)), new WeakReference(local), Endpoint.In)
      val localEndpointOut = new UDPEndpoint(UDPEndpoint.TransportIdentifier(), new WeakReference(local), Endpoint.Out)
      Hexapod.init(local)

      comm.getActive should have size (0)
      comm.getPending should have size (0)
      comm.getProgress should have size (0)
      comm.getGlobal should have size (2)

      Communication.push(new Ping(local.uuid, None))

      (events.dequeue match {
        case Communication.Event.Add(msg: Ping) => true
        case e => log.error("unextected event " + e); false
      }) should be(true)

      (events.dequeue match {
        case Communication.Event.Add(msg: DiffieHellmanReq) => true
        case e => log.error("unextected event " + e); false
      }) should be(true)

      comm.getActive should have size (0)
      comm.getPending should have size (2)
      comm.getProgress.toString should be("Map(dh_req -> 0, ping -> 0)")

      log.___glance("CONNECT ENDPOINT")
      localEndpointIn.connected should equal(false)
      localEndpointOut.connected should equal(false)

      events should have size (0)

      local.connect()
      localEndpointIn.connected should equal(true)
      localEndpointOut.connected should equal(true)
      log.___glance("ENDPOINT CONNECTED")

      val connectedEvents = events.dequeueAll(_ => true).map(_ match {
        case Hexapod.Event.Connect(ep: UDPEndpoint) => 1
        case e => log.error("unextected event " + e); 0
      })

      connectedEvents.foldLeft(0)(_ + _) should be(2)

      connectedEvents should have size (2)

      events should have size (0)

      log.___glance("ADD REMOTE PEER")
      val remote = new Hexapod(Common.node2UUID)
      val remoteEndpointIn = new UDPRemoteEndpoint(UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23457)), new WeakReference(remote), Endpoint.In)
      Peer.add(remote)

      (events.dequeue match {
        case Communication.Event.Active(msg: DiffieHellmanReq) => true
        case e => log.error("unextected event " + e); false
      }) should be(true)

      events should have size (0)
      log.___glance("REMOTE PEER ADDED")

      comm.getActive should have size (1)
      comm.getPending should have size (1)
      Thread.sleep(2000)
      Communication.processPendingMessages
      comm.getActive should have size (0)
      comm.getPending should have size (0)
  }
}

class MeshTestMultiJvmNode2 extends FunSuite with ShouldMatchers with BeforeAndAfter {
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
    Record.init(new Record.InitWithDiagnosticContext)
    Logging.init(new Logging.DefaultInit)
    Logging.resume
  }

  after {
    Logging.deinit
  }

  test("node 2 pong") {
    config =>
      val events = new SynchronizedQueue[Any]

      // subscribe
      Hexapod.Event.subscribe(new Hexapod.Event.Sub {
        def notify(pub: Hexapod.Event.Pub, event: Hexapod.Event) {
          log.___glance("HE:" + event)
          events += event
        }
      })
      Communication.Event.subscribe(new Communication.Event.Sub {
        def notify(pub: Communication.Event.Pub, event: Communication.Event) {
          log.___glance("CE:" + event)
          events += event
        }
      })

      // init
      Mesh.init(new Mesh.DefaultInit)
      val comm = new Communication {
        def getPending = pending
        def getActive = active
        def getProgress = sendPendingProgress
        def getGlobal = global
      }
      Communication.init(new Communication.DefaultInit { override val implementation: Communication.Interface = comm })
      DiffieHellmanReq.init(new DiffieHellmanReq.DefaultInit)
      DiffieHellmanRes.init(new DiffieHellmanRes.DefaultInit)
      Peer.init(new Peer.DefaultInit)
      val local = new AppHexapod(Common.node1UUID)
      val localEndpointIn = new UDPEndpoint(UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23457)), new WeakReference(local), Endpoint.In)
      val localEndpointOut = new UDPEndpoint(UDPEndpoint.TransportIdentifier(), new WeakReference(local), Endpoint.Out)
      Hexapod.init(local)

      comm.getActive should have size (0)
      comm.getPending should have size (0)
      comm.getProgress should have size (0)
      comm.getGlobal should have size (2)

      Thread.sleep(2000)

      local.connect()

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
      Peer.init(new Peer.DefaultInit)
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

      Peer.add(remote)

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

object Common {
  val node1UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val node2UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
}