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

package org.digimead.digi.lib.enc

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.ConsoleLogger
import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.log.Record
import org.scalatest.BeforeAndAfter
import org.scalatest.fixture.FunSuite
import org.scalatest.matchers.ShouldMatchers.convertToAnyRefShouldWrapper
import org.scalatest.matchers.ShouldMatchers.equal

class DiffieHellmanTestMultiJvmNode1 extends FunSuite with BeforeAndAfter {
  type FixtureParam = Map[String, Any]

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
  }

  after {
    Logging.deinit
  }

  test("DiffieHellman test") {
    config =>
      val p = DiffieHellman.randomPrime(128)
      val g = 5
      // alice and bob initialise with the public parameters for DH, g and p
      val alice = new DiffieHellman(g, p)
      val bob = new DiffieHellman(g, p)

      val a1 = alice.publicKey
      val b1 = bob.publicKey

      alice.log.debug("a1 = " + a1)
      bob.log.debug("b1 = " + b1)

      // alice and bob compute their shared secret key
      val alicesk = alice.getSharedKey(b1)
      val bobsk = bob.getSharedKey(a1)

      Logging.commonLogger.debug("Done, alice sk = " + alicesk + ", bob sk = " + bobsk)

      alicesk should equal(bobsk)
  }
}
