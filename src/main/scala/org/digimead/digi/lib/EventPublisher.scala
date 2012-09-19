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

package org.digimead.digi.lib

import java.util.concurrent.atomic.AtomicReference

import scala.collection.mutable.Publisher

import org.digimead.digi.lib.log.Logging

trait EventPublisher[Event] extends Logging {
  this: Publisher[Event] =>
  protected val lastEvent = new AtomicReference[Event]()

  def nextEvent(timeout: Long, id: String = "nextEvent"): Option[Event] = {
    var rest = timeout
    while ((Option(lastEvent.getAndSet(null.asInstanceOf[Event])) match {
      case Some(result) => return Some(result)
      case _ => true
    }) && rest > 0) {
      /**
       * Defending against the system clock going backward
       * by counting time elapsed directly.  Loop required
       * to deal with spurious wakeups.
       */
      val elapsed = waitMeasuringElapsed(id, rest)
      rest -= elapsed
    }
    Option(lastEvent.getAndSet(null.asInstanceOf[Event]))
  }
  def waitEvent(timeout: Long): Option[Event] = {
    lastEvent.set(null.asInstanceOf[Event])
    nextEvent(timeout, "waitEvent")
  }
  /**
   * Waits `timeout` millis. If `timeout <= 0` just returns 0. If the system clock
   * went backward, it will return 0, so it never returns negative results.
   */
  protected def waitMeasuringElapsed(fName: String, timeout: Long): Long = if (timeout <= 0) 0 else {
    val start = System.currentTimeMillis
    lastEvent.synchronized {
      log.traceWhere(this + " " + fName + "(" + timeout + ") waiting", -4)
      lastEvent.wait(timeout)
    }
    val elapsed = System.currentTimeMillis - start
    val result = if (elapsed < 0) 0 else elapsed
    log.traceWhere(this + " " + fName + "(" + timeout + ") running, reserve " + (timeout - result), -4)
    result
  }
}
