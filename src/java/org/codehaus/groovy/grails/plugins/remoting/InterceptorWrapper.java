/*
 * Copyright 2007 Peter Ledbrook.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.codehaus.groovy.grails.plugins.remoting;

import groovy.lang.GroovyObject;
import groovy.lang.MissingMethodException;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;

/**
 * Wraps user-declared interceptors so that they can take part in Spring's AOP framework.
 */
public class InterceptorWrapper implements MethodInterceptor {

	private GroovyObject interceptor;
	private String methodPattern;
	private boolean interceptAllMethods = false;

	public Object invoke(MethodInvocation invocation) throws Throwable {
		// Check that the method matches at least one of the patterns
		// specified by the user-declared interceptor.
		String methodName = invocation.getMethod().getName();
		if (!interceptAllMethods && !methodName.matches(methodPattern)) {
			// No match, so we don't invoke the interceptor.
			return invocation.proceed();
		}

		// Invoke 'before' on the interceptor if it exists. If the
		// method returns 'false', then the method returns immediately,
		// i.e. the target method is not called.
		Object[] args = invocation.getArguments();
		try {
			interceptor.invokeMethod("before", new Object[] { methodName, args });
		}
		catch (MissingMethodException e) {
			// The 'before' method does not exist, so it isn't invoked.
		}

		// Call the target method.
		Object retval = null;
		try {
			retval = invocation.proceed();
		}
		finally {
			try {
				// Call the interceptor's 'after' method if it has one.
				interceptor.invokeMethod("after", new Object[] { methodName, args, retval });
			}
			catch (MissingMethodException e) {
				// The 'after' method does not exist, so it isn't invoked.
			}
		}

		return retval;
	}

	public void setInterceptor(GroovyObject interceptor) {
		this.interceptor = interceptor;
	}

	public void setPattern(String pattern) {
		methodPattern = pattern;

		if (".*".equals(pattern)) {
			interceptAllMethods = true;
		}
	}
}
