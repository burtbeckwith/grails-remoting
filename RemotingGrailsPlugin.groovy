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

import grails.plugin.remoting.RemotingPluginHelper
import org.codehaus.groovy.grails.plugins.remoting.InterceptorArtefactHandler
import org.springframework.web.context.support.StaticWebApplicationContext
import org.springframework.web.servlet.DispatcherServlet

class RemotingGrailsPlugin {

	def version = '1.3'
	def grailsVersion = '2.0 > *'
	def author = 'Peter Ledbrook'
	def authorEmail = 'peter@cacoethes.co.uk'
	def title = 'Remoting Plugin'
	def description = '''This plugin makes it easy to expose your Grails services to remote clients via RMI, Hessian, Burlap and Spring's HttpInvoker protocol.
In addition, you can easily access remote services via the same set of protocols.
'''
	def documentation = 'http://grails.org/plugin/remoting'

	def artefacts = [InterceptorArtefactHandler]
	def observe = ['services']
	def loadAfter = ['services']
	def watchedResources = "file:./grails-app/remoting/*RemotingInterceptor.groovy"

	def license = "APACHE"
	def developers = [[name: 'Burt Beckwith', email: 'beckwithb@vmware.com']]
	def issueManagement = [system: 'JIRA', url: 'http://jira.grails.org/browse/GPREMOTING']
	def scm = [url: 'https://github.com/burtbeckwith/grails-remoting']

	def doWithWebDescriptor = { xml ->
		// Set up servlets for each of the exporter types.
		for (String invokerType in RemotingPluginHelper.remoteExporters.keySet()) {
			// Add a servlet definition for this invoker type
			xml.servlet[xml.servlet.size() - 1] + {
				servlet {
					'servlet-name'(invokerType)
					'servlet-class'(DispatcherServlet.name)
					'init-param'() {
						'param-name'('contextClass')
						'param-value'(StaticWebApplicationContext.name)
					}
					'init-param'() {
						'param-name'('detectAllHandlerExceptionResolvers')
						'param-value'('false')
					}
					'load-on-startup'(1)
				}
			}

			xml.'servlet-mapping'[xml.'servlet-mapping'.size() - 1] + {
				'servlet-mapping' {
					'servlet-name'(invokerType)
					'url-pattern'("/$invokerType/*")
				}
			}
		}
	}

	def doWithSpring = {
		def helper = new RemotingPluginHelper()
		helper.registerBeans.delegate = delegate
		helper.registerBeans application
	}
}
