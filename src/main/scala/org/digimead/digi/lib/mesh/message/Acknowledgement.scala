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

package org.digimead.digi.lib.mesh.message

import java.util.UUID
import org.digimead.digi.lib.mesh.communication.Stimulus
import org.digimead.digi.lib.mesh.hexapod.Hexapod
import org.digimead.digi.lib.mesh.hexapod.Hexapod.hexapod2app
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

case class Acknowledgement(val conversationHash: Int, destination: Option[UUID] = None)
  extends Message(Acknowledgement.word, Hexapod.uuid, destination, Acknowledgement.zeroUUID) {
  val isReplyRequired: Boolean = false
  val messageType = Message.Type.Acknowledgement

  def content(): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val w = new DataOutputStream(baos)
    w.writeInt(conversationHash)
    w.flush()
    val data = baos.toByteArray()
    w.close()
    data
  }
  def react(stimulus: Stimulus) = None
  override def toString = "Acknowledgement[%08X]".format(conversationHash)
}

object Acknowledgement {
  val word = "acknowledgement"
  lazy val zeroUUID = new UUID(0, 0)
}
