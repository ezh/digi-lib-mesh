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

package org.digimead.digi.lib.mesh

import java.net.InetAddress
import java.util.UUID
import scala.collection.mutable.SynchronizedQueue
import scala.ref.WeakReference
import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.mesh.Peer.peer2implementation
import org.digimead.digi.lib.mesh.communication.Communication
import org.digimead.digi.lib.mesh.communication.Communication.communication2implementation
import org.digimead.digi.lib.mesh.endpoint.Endpoint
import org.digimead.digi.lib.mesh.endpoint.UDPEndpoint
import org.digimead.digi.lib.mesh.endpoint.UDPRemoteEndpoint
import org.digimead.digi.lib.mesh.hexapod.AppHexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import org.digimead.digi.lib.mesh.message.DiffieHellman
import org.digimead.digi.lib.mesh.message.Ping
import org.digimead.lib.test.EventPublisher
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.ShouldMatchers
import com.escalatesoft.subcut.inject.BindingModule
import com.escalatesoft.subcut.inject.NewBindingModule
import org.digimead.lib.test.MatcherHelper
import org.scalatest.FunSuite
import org.digimead.lib.test.LoggingHelper
import org.digimead.digi.lib.log.api.Loggable

class MeshTest_j1 extends FunSuite with ShouldMatchers with LoggingHelper with Loggable with MatcherHelper {
  val custom = new NewBindingModule(module => {
    module.bind[AppHexapod] toSingle { new AppHexapod(Common.node1UUID) }
    lazy val communicationSingleton = DependencyInjection.makeInitOnce(implicit module => new MyCommunication)
    module.bind[Communication.Interface] toModuleSingle { communicationSingleton(_) }
    module.bind[Long] identifiedBy ("Mesh.Communication.DeliverTTL") toSingle { 1000L }
  }) ~ org.digimead.digi.lib.mesh.default
  after { adjustLoggingAfter }
  before {
    DependencyInjection(custom, false)
    adjustLoggingBefore
  }

  test("node 1 ping") {
    val events = new SynchronizedQueue[Any]

    // init
    val localEndpointIn = new UDPEndpoint(new WeakReference(Hexapod.inner), Endpoint.Direction.In,
      new UDPEndpoint.Nature(Some(InetAddress.getLocalHost()), Some(23456)))
    val localEndpointOut = new UDPEndpoint(new WeakReference(Hexapod.inner), Endpoint.Direction.Out,
      new UDPEndpoint.Nature())

    // subscribe
    Hexapod.subscribe(new Hexapod.Sub {
      def notify(pub: Hexapod.Pub, event: Hexapod.Event) {
        log.___glance("HE:" + event)
        events += event
      }
    })
    Communication.subscribe(new Communication.Sub {
      def notify(pub: Communication.Pub, event: Communication.Event) {
        log.___glance("CE:" + event)
        events += event
      }
    })

    val comm = Communication.inner.asInstanceOf[MyCommunication]

    comm.getBuffer should have size (0)
    comm.getDeliverMessageCounter should have size (0)
    comm.getGlobal should have size (2)

    // ping Hexapod -> None(someone at mesh)
    Communication.push(Ping(Hexapod.uuid, None)(true))

    expectDefined(events.dequeue) { case Communication.Event.Add(msg: Ping) => }

    comm.getBuffer should have size (1)
    comm.getBuffer.forall(_._2.condition == Communication.Condition.Pending)

    comm.getDeliverMessageCounter.toString should be("Map(ping -> 0)")

    log.___glance("CONNECT ENDPOINT")
    localEndpointIn.connected should equal(false)
    localEndpointOut.connected should equal(false)

    events should have size (0)

    Hexapod.connect()
    localEndpointIn.connected should equal(true)
    localEndpointOut.connected should equal(true)
    log.___glance("ENDPOINT CONNECTED")

    expectDefined(events.dequeue) { case Hexapod.Event.Connect(ep: UDPEndpoint) => }

    expectDefined(events.dequeue) { case Hexapod.Event.Connect(ep: UDPEndpoint) => }

    events should have size (0)

    log.___glance("ADD REMOTE PEER")
    val remote = Hexapod(Common.node2UUID)
    val remoteEndpointIn = new UDPRemoteEndpoint(new WeakReference(remote), Endpoint.Direction.In,
      new UDPEndpoint.Nature(Some(InetAddress.getLocalHost()), Some(23457)))
    Peer.add(remote)

    expectDefined(events.dequeue) { case Communication.Event.Add(msg: DiffieHellman) => }

    expectDefined(events.dequeue) { case Communication.Event.Sent(msg: DiffieHellman) => }

    comm.getBuffer(DiffieHellman.word).condition should be(Communication.Condition.Sent)
    comm.getBuffer(DiffieHellman.word).counter should be(1)

    events should have size (0)
    log.___glance("REMOTE PEER ADDED")

    /*
       * FAIL(by timeout) TEST
       * shift 100ms
       */
    Thread.sleep(100)
    Communication.processMessages
    events should have size (0)

    /*
       * 2n node 1s sleep
       */
    Thread.sleep(1000)
    Communication.processMessages
    expectDefined(events.dequeue) { case Communication.Event.Sent(msg: DiffieHellman) => }
    events should have size (0)

    /*
       * 2n node 2s sleep
       */
    Thread.sleep(1000)
    Communication.processMessages
    expectDefined(events.dequeue) { case Communication.Event.Sent(msg: DiffieHellman) => }
    events should have size (0)

    /*
       * 2n node 3s sleep
       */
    Thread.sleep(1000)
    Communication.processMessages

    expectDefined(events.dequeue) { case Communication.Event.Fail(msg: DiffieHellman) => }

    expectDefined(events.dequeue) { case Communication.Event.Fail(msg: Ping) => }

    events should have size (0)
    comm.getBuffer should have size (0)
    comm.getDeliverMessageCounter should have size (0)

    /*
       * SUCCESSFULL TEST
       */
    Communication.push(Ping(Hexapod.uuid, None)(true))
    Communication.processMessages

    expectDefined(events.dequeue) { case Communication.Event.Add(msg: Ping) => }

    expectDefined(events.dequeue) { case Communication.Event.Add(msg: DiffieHellman) => }

    expectDefined(events.dequeue) { case Communication.Event.Sent(msg: DiffieHellman) => }

    events should have size (0)

    Thread.sleep(1000)
    Communication.processMessages
    events.dequeueAll(_ => true) should have size (1)

    // should be success
    Thread.sleep(1000)
    Communication.processMessages
    events.dequeueAll(_ => true) should have size (1)

    // receive acknowledgement
    expectDefined(comm.waitEvent(5000)) { case Some(Communication.Event.Delivered(message: DiffieHellman)) => }

    // receive DiffieHellman response(event #1, update keys, trying to send all pending messages, that require encryption)
    expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Sent(message: Ping)) => }

    // receive DiffieHellman response(event #2)
    expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Success(message: DiffieHellman)) => }

    // receive acknowledgement
    expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Delivered(message: Ping)) => }

    // receive Ping response
    expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Success(message: Ping)) => }

    comm.getBuffer should have size (0)

    Thread.sleep(1000)
  }
  class MyCommunication(implicit override val bindingModule: BindingModule) extends Communication with EventPublisher[Communication.Event] {
    def getBuffer = buffer
    def getDeliverMessageCounter = deliverMessageCounter
    def getGlobal = global
    override protected def publish(event: Communication.Event) = lastEvent.synchronized {
      lastEvent.set(event)
      lastEvent.notifyAll()
      super.publish(event)
    }
  }
}

class MeshTest_j2 extends FunSuite with ShouldMatchers with LoggingHelper with Loggable with MatcherHelper {
  val custom = new NewBindingModule(module => {
    module.bind[AppHexapod] toSingle { new AppHexapod(Common.node1UUID) }
//    lazy val communicationSingleton = DependencyInjection.makeInitOnce(implicit module => new MyCommunication)
//    module.bind[Communication.Interface] toModuleSingle { communicationSingleton(_) }
    module.bind[Long] identifiedBy ("Mesh.Communication.DeliverTTL") toSingle { 1000L }
  }) ~ org.digimead.digi.lib.mesh.default
  after { adjustLoggingAfter }
  before {
    DependencyInjection(custom, false)
    adjustLoggingBefore
  }

  test("node 2 pong") {
    val events = new SynchronizedQueue[Any]

    /*      // init
      Mesh.init(new Mesh.DefaultInit)
      Peer.init(new Peer.DefaultInit)
      val local = new AppHexapod(Common.node2UUID)
      val localEndpointIn = new UDPEndpoint(UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23457)), new WeakReference(local), Endpoint.Direction.In)
      val localEndpointOut = new UDPEndpoint(UDPEndpoint.TransportIdentifier(), new WeakReference(local), Endpoint.Out)
      Hexapod.init(local)
      val comm = new Communication with EventPublisher[Communication.Event] {
        def getBuffer = buffer
        def getDeliverMessageCounter = deliverMessageCounter
        def getGlobal = global
        override protected def publish(event: Communication.Event) = lastEvent.synchronized {
          lastEvent.set(event)
          lastEvent.notifyAll()
          super.publish(event)
        }
      }
      Communication.init(new Communication.DefaultInit { override val implementation: Communication.Interface = comm })
      Ping.init(new Ping.DefaultInit)
      DiffieHellman.init(new DiffieHellman.DefaultInit)
      Mesh.isReady should be(true)

      // subscribe
      Hexapod.subscribe(new Hexapod.Sub {
        def notify(pub: Hexapod.Pub, event: Hexapod.Event) {
          log.___glance("HE:" + event)
          events += event
        }
      })
      Communication.subscribe(new Communication.Sub {
        def notify(pub: Communication.Pub, event: Communication.Event) {
          log.___glance("CE:" + event)
          events += event
        }
      })

      comm.getBuffer should have size (0)
      comm.getDeliverMessageCounter should have size (0)
      comm.getGlobal should have size (2)

      log.___glance("ADD REMOTE PEER")
      val remote = new Hexapod(Common.node1UUID) {
        def getAuthDiffieHellman = authDiffieHellman
      }
      val remoteEndpointIn = new UDPRemoteEndpoint(UDPEndpoint.TransportIdentifier(Some(InetAddress.getLocalHost()), Some(23456)), new WeakReference(remote), Endpoint.Direction.In)
      Peer.add(remote)
      log.___glance("REMOTE PEER ADDED")

      Thread.sleep(5000)

      local.connect()

      // receive DiffieHellman request(event #1)
      expectDefined(comm.waitEvent(5000)) { case Some(Communication.Event.Add(message: DiffieHellman)) => }

      // receive DiffieHellman request(event #2)
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Sent(message: DiffieHellman)) => }

      // receive acknowledgement(event #1)
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Delivered(message: DiffieHellman)) => }

      // receive acknowledgement(event #2)
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Success(message: DiffieHellman)) => }

      Mesh(Common.node1UUID) match {
        case Some(hexapod: Hexapod) =>
          remote.getAuthDiffieHellman should not be ('empty)
          val remoteDH = remote.getAuthDiffieHellman.get
          assert(remoteDH.publicKey != 0)
          assert(remoteDH.secretKey === 0)
        case None =>
          assert(false, "node 1 hexapod not found")
      }

      // receive Ping request(event #1)
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Add(message: Ping)) if message.isReplyRequired == false => }

      // receive Ping request(event #2)
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Sent(message: Ping)) => }

      // receive acknowledgement
      expectDefined(comm.nextEvent(5000)) { case Some(Communication.Event.Success(message: Ping)) => }

      Thread.sleep(1000)*/
  }
}

object Common {
  val node1UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")
  val node2UUID = UUID.fromString("00000000-0000-0000-0000-000000000002")
}
