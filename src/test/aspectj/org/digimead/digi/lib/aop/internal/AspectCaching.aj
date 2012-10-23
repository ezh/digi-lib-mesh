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

package org.digimead.digi.lib.aop.internal;

import org.digimead.digi.lib.aop.cache;

privileged public final aspect AspectCaching extends
		org.digimead.digi.lib.aop.Caching {
	public pointcut cachedAccessPoint(cache c):
		execution(@cache * *(..)) && @annotation(c);

	Object around(final cache c): cachedAccessPoint(c) {
		Invoker aspectJInvoker = new Invoker() {
			public Object invoke() {
				return proceed(c);
			}
		};
		return execute(aspectJInvoker, c, thisJoinPointStaticPart.toLongString(),
				thisJoinPointStaticPart.toShortString(), thisJoinPoint.getArgs());
	}
}
