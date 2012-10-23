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

package org.digimead.digi.lib.aop.external;

import org.aspectj.lang.reflect.SourceLocation;
import org.digimead.digi.lib.aop.log;
import org.digimead.digi.lib.log.Loggable;

privileged public final aspect AspectLogging {
	public pointcut loggingNonVoid(Loggable obj, log l) : target(obj) && execution(@log !void *(..)) && @annotation(l);

	public pointcut loggingVoid(Loggable obj, log l) : target(obj) && execution(@log void *(..)) && @annotation(l);

	public pointcut logging(Loggable obj, log l) : loggingVoid(obj, l) || loggingNonVoid(obj, l);

	before(final Loggable obj, final log log) : logging(obj, log) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.enteringMethod(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Loggable obj, final log log) returning(final Object result) : loggingNonVoid(obj, log) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		if (log != null && log.result())
			org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
					location.getFileName(), location.getLine(),
					thisJoinPointStaticPart.getSignature(), obj, result);
		else
			org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
					location.getFileName(), location.getLine(),
					thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Loggable obj, final log log) returning() : loggingVoid(obj, log) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethod(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj);
	}

	after(final Loggable obj, final log log) throwing(final Exception ex) : logging(obj, log) {
		SourceLocation location = thisJoinPointStaticPart.getSourceLocation();
		org.digimead.digi.lib.aop.Logging$.MODULE$.leavingMethodException(
				location.getFileName(), location.getLine(),
				thisJoinPointStaticPart.getSignature(), obj, ex);
	}
}
