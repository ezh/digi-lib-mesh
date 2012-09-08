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

package org.digimead.digi.lib.auth

import scala.math.BigInt.int2bigInt
import scala.util.Random

import org.digimead.digi.lib.aop.Loggable
import org.digimead.digi.lib.log.Logging

class DiffieHellman(val g: Int, p: BigInt) extends Logging {
  @volatile var secretKey: BigInt = 0
  @volatile var peerPublicKey: BigInt = 0

  @Loggable
  def createSecretKey(): BigInt = {
    secretKey = DiffieHellman.random(128)
    secretKey
  }
  @Loggable
  def setPeerPublicKey(x: BigInt) = peerPublicKey = x
  @Loggable
  def getPublicKey(): BigInt = doExpMod(g, secretKey, p)
  @Loggable
  def createSharedKey(): BigInt = doExpMod(peerPublicKey, secretKey, p)
  private def doExpMod(x: BigInt): BigInt = doExpMod(g, x, p)
  private def doExpMod(g: BigInt, x: BigInt, m: BigInt): BigInt = g.modPow(x, m)
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
}
