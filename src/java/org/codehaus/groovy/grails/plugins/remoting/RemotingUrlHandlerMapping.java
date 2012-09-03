/*
 * Copyright 2007-2012 the original author or authors.
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

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.springframework.web.servlet.handler.AbstractUrlHandlerMapping;

/**
 * @author Peter Ledbrook
 */
@SuppressWarnings("rawtypes")
public class RemotingUrlHandlerMapping extends AbstractUrlHandlerMapping {

	private static final Pattern INVOKER_URL_PATTERN = Pattern.compile("/(\\w+)/(\\w+)");

	private Set invokerTypes;

	public RemotingUrlHandlerMapping() {
		setAlwaysUseFullPath(true);
	}

	public void setInvokerTypes(Set types) {
		invokerTypes = types;
	}

	@Override
	protected Object lookupHandler(String urlPath, HttpServletRequest request) {
		Matcher m = INVOKER_URL_PATTERN.matcher(urlPath);
		if (m.matches()) {
			// Extract the invoker type...
			String invokerType = m.group(1);
			if (invokerTypes.contains(invokerType)) {
				return invokerType + '.' + m.group(2);
			}
		}

		// No matching invoker found.
		return null;
	}
}
