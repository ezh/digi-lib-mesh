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

import scala.math.BigInt.int2bigInt
import scala.util.Random
import org.digimead.digi.lib.log.api.Loggable
import org.digimead.digi.lib.aop.log

class DiffieHellman(val g: Int, val p: BigInt, val secretKey: BigInt, val publicKey: BigInt) extends Loggable {
  def this(g: Int, p: BigInt, secretKey: BigInt) = this(g, p, secretKey, DiffieHellman.doExpMod(g, secretKey, p))
  def this(g: Int, p: BigInt) = this(g, p, DiffieHellman.randomPrime(128))
  @log
  def getSharedKey(peerPublicKey: BigInt): BigInt = {
    assert(secretKey != 0, "secret key not found")
    DiffieHellman.doExpMod(peerPublicKey, secretKey, p)
  }
}

object DiffieHellman {
  /** return a random value with n digits */
  def randomPrime(n: Int): BigInt = {
    val rnd = new Random()
    BigInt.probablePrime(n, rnd)
  }
  /** return a random value with n digits */
  def random(n: Int): BigInt = {
    val rnd = new Random()
    BigInt(n, rnd)
  }
  def doExpMod(g: BigInt, x: BigInt, m: BigInt): BigInt = g.modPow(x, m)
}
