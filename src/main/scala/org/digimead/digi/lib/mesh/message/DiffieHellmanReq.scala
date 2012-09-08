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

package org.digimead.digi.lib.mesh.message

import java.util.Date
import java.util.UUID

import org.digimead.digi.lib.log.Logging
import org.digimead.digi.lib.mesh.communication.Message
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.util.Util

case class DiffieHellmanReq(val publicKey: BigInt, val g: Int, val p: BigInt,
  override val sourceHexapod: UUID,
  override val destinationHexapod: Option[UUID] = None,
  override val transportEndpoint: Option[UUID] = None)
  extends Message("dh_req", true, sourceHexapod, destinationHexapod, transportEndpoint) {
  DiffieHellmanReq.log.debug("alive %s %s %s".format(this, conversation, Util.dateString(new Date(timestamp))))

  def content() = publicKey.toByteArray
  def react(stimulus: Stimulus) = stimulus match {
    case Stimulus.IncomingMessage(message @ Ping(_, _, _)) if message.conversation == conversation =>
      Some(true)
    case _ =>
      None
  }
  override def toString = "DiffieHellmanReq[%08X]".format(this.hashCode())
}

object DiffieHellmanReq extends Logging
