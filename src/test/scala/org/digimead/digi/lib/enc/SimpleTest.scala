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
import org.scalatest.matchers.ShouldMatchers

class SimpleTest extends WordSpec with ShouldMatchers with LoggingHelper with Loggable {
  after { adjustLoggingAfter }
  before {
    DependencyInjection(org.digimead.digi.lib.mesh.default ~ org.digimead.digi.lib.default, false)
    adjustLoggingBefore
  }

  "Simple test encript/decript" in {
    val dh1 = new DiffieHellman(5, DiffieHellman.randomPrime(128))
    val dh2 = new DiffieHellman(5, DiffieHellman.randomPrime(128))
    val key = dh1.getSharedKey(dh1.publicKey)
    log.___glance("generate key with length " + key.toByteArray.length * 8 + "bit")
    val rawKey1 = Simple.getRawKey(key.toByteArray)
    log.___glance("generate raw key 1 with length " + rawKey1.length * 8 + "bit")
    val rawKey2 = Simple.getRawKey(key.toByteArray)
    log.___glance("generate raw key 2 with length " + rawKey2.length * 8 + "bit")
    assert(rawKey1 != rawKey2)
    val enc1 = Simple.encrypt(rawKey1, "1234567890".getBytes())
    val enc2 = Simple.encrypt(rawKey2, "1234567890".getBytes())
    assert(enc1 != enc2)
    val dec1 = Simple.decrypt(rawKey1, enc1)
    val dec2 = Simple.decrypt(rawKey2, enc2)
    assert("1234567890".getBytes() === dec1)
    assert("1234567890".getBytes() === dec2)
    val dec3 = Simple.decrypt(rawKey2, enc1)
    val dec4 = Simple.decrypt(rawKey1, enc2)
    assert("1234567890".getBytes() === dec3)
    assert("1234567890".getBytes() === dec4)
    assert("1234567890".getBytes() === Simple.decrypt(key, enc1))
    val encN = Simple.encrypt(key, "1234567890ABC")
    assert("1234567890ABC".getBytes() === Simple.decrypt(key, encN))
  }

  override def beforeAll(configMap: Map[String, Any]) { adjustLoggingBeforeAll(configMap) }
}
