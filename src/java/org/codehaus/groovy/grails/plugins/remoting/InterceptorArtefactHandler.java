/*
 * Copyright 2007-2012 Peter Ledbrook.
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

import org.codehaus.groovy.grails.commons.AbstractInjectableGrailsClass;
import org.codehaus.groovy.grails.commons.ArtefactHandlerAdapter;
import org.codehaus.groovy.grails.commons.InjectableGrailsClass;

/**
 * @author Peter Ledbrook
 */
public final class InterceptorArtefactHandler extends ArtefactHandlerAdapter {

	public static final String TYPE = "RemotingInterceptor";

	public InterceptorArtefactHandler() {
		super(TYPE, InterceptorGrailsClass.class, DefaultInterceptorGrailsClass.class, null);
	}

	@Override
	public boolean isArtefactClass(@SuppressWarnings("rawtypes") Class clazz) {
		return clazz != null && clazz.getName().endsWith(TYPE);
	}

	public static interface InterceptorGrailsClass extends InjectableGrailsClass {
		// no extra methods
	}

	public static class DefaultInterceptorGrailsClass extends AbstractInjectableGrailsClass implements InterceptorGrailsClass {
		public DefaultInterceptorGrailsClass(Class<?> wrappedClass) {
			super(wrappedClass, InterceptorArtefactHandler.TYPE);
		}
	}
}
