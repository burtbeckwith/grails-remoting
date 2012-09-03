/*
 * Copyright 2007 the original author or authors.
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

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.remoting.support.RemoteExporter;
import org.springframework.web.HttpRequestHandler;

/**
 * A Spring remote exporter that can be used in place of any HTTP based
 * exporter. Its role is to replace existing exporters, thereby "switching" them
 * off. This is because you can not simply remove an exporter from the Spring
 * context at the moment.
 *
 * @author Peter Ledbrook
 */
public final class DummyHttpExporter extends RemoteExporter implements HttpRequestHandler {

	/**
	 * Always returns a 404 HTTP status.
	 */
	public void handleRequest(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		response.sendError(HttpServletResponse.SC_NOT_FOUND);
	}
}
