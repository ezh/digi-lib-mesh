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

package org.digimead.digi.lib.enc

import org.digimead.digi.lib.DependencyInjection
import org.digimead.digi.lib.aop.log
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.lib.test.LoggingHelper
import org.scalatest.WordSpec
import org.scalatest.matchers.ShouldMatchers.convertToAnyRefShouldWrapper
import org.scalatest.matchers.ShouldMatchers.equal

class DiffieHellmanTest extends WordSpec with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  "DiffieHellman test" in {
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

    log.debug("Done, alice sk = " + alicesk + ", bob sk = " + bobsk)

    alicesk should equal(bobsk)
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
