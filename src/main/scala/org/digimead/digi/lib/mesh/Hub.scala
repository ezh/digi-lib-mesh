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

import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.Buffer
import scala.collection.mutable.SynchronizedBuffer

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.message.DiffieHellmanReq
import org.digimead.digi.lib.mesh.message.DiffieHellmanRes

class Hub extends Hub.Interface with Logging {
  protected val pool = new ArrayBuffer[Hexapod] with SynchronizedBuffer[Hexapod]
  def add(node: Hexapod) = {

    log.debug("add %s to hub pool".format(node))
    pool += node
  }
  def getBest(): Option[Hexapod] = None
  override def toString = "default hub implemetation"
}

object Hub extends Logging {
  implicit def hub2implementation(h: Hub.type): Interface = h.implementation
  private var implementation: Interface = null

  def init(arg: Init): Unit = synchronized {
    assert(Mesh.isInitialized, "Mesh not initialized")
    assert(DiffieHellmanReq.isInitialized, "DiffieHellmanReq not initialized")
    assert(DiffieHellmanRes.isInitialized, "DiffieHellmanRes not initialized")
    log.debug("initialize hub with " + arg.implementation)
    implementation = arg.implementation
  }
  def isInitialized(): Boolean = implementation != null

  trait Interface {
    protected val pool: Buffer[Hexapod]

    /** add Hexapod to hub pool */
    def add(node: Hexapod)
    /** get best hexapod */
    def getBest(): Option[Hexapod]
  }
  trait Init {
    val implementation: Interface
  }
  class DefaultInit extends Init {
    val implementation: Interface = new Hub
  }
}
