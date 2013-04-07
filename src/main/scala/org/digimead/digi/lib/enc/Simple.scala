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

import java.security.SecureRandom

import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec

object Simple {
  val keyLength = 128 // 192 and 256 bits may not be available

  def encrypt(key: BigInt, cleartext: String): Array[Byte] = {
    val rawKey = getRawKey(key.toByteArray)
    encrypt(rawKey, cleartext.getBytes("UTF8"))
  }
  def encrypt(key: BigInt, clear: Array[Byte]): Array[Byte] = {
    val rawKey = getRawKey(key.toByteArray)
    encrypt(rawKey, clear)
  }
  def encrypt(rawKey: Array[Byte], clear: Array[Byte]): Array[Byte] = {
    val secretKeySpec = new SecretKeySpec(rawKey, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
    cipher.doFinal(clear)
  }
  def decrypt(key: BigInt, encrypted: Array[Byte]): Array[Byte] = {
    val rawKey = getRawKey(key.toByteArray)
    decrypt(rawKey, encrypted)
  }
  def decrypt(rawKey: Array[Byte], encrypted: Array[Byte]): Array[Byte] = {
    val secretKeySpec = new SecretKeySpec(rawKey, "AES")
    val cipher = Cipher.getInstance("AES")
    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec)
    cipher.doFinal(encrypted)
  }
  def getRawKey(seed: Array[Byte]): Array[Byte] = {
    val keyGenerator = KeyGenerator.getInstance("AES")
    val secureRandom = SecureRandom.getInstance("SHA1PRNG")
    secureRandom.setSeed(seed)
    keyGenerator.init(keyLength, secureRandom)
    val secretKey = keyGenerator.generateKey()
    secretKey.getEncoded()
  }
}
