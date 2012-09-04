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
package grails.plugin.remoting

import org.aopalliance.intercept.MethodInterceptor
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsServiceClass
import org.codehaus.groovy.grails.plugins.remoting.InterceptorArtefactHandler
import org.codehaus.groovy.grails.plugins.remoting.InterceptorWrapper
import org.codehaus.groovy.grails.plugins.remoting.RemotingUrlHandlerMapping
import org.codehaus.groovy.grails.plugins.remoting.InterceptorArtefactHandler.InterceptorGrailsClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.aop.framework.ProxyFactoryBean
import org.springframework.aop.target.HotSwappableTargetSource
import org.springframework.remoting.caucho.BurlapProxyFactoryBean
import org.springframework.remoting.caucho.BurlapServiceExporter
import org.springframework.remoting.caucho.HessianProxyFactoryBean
import org.springframework.remoting.caucho.HessianServiceExporter
import org.springframework.remoting.httpinvoker.HttpInvokerProxyFactoryBean
import org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter
import org.springframework.remoting.rmi.RmiProxyFactoryBean
import org.springframework.remoting.rmi.RmiServiceExporter
import org.springframework.web.servlet.mvc.HttpRequestHandlerAdapter

class RemotingPluginHelper {

	static final Map remoteExporters = [
		rmi:         RmiServiceExporter,
		hessian:     HessianServiceExporter,
		burlap:      BurlapServiceExporter,
		httpinvoker: HttpInvokerServiceExporter]

	static final Map proxyFactories = [
		rmi:         RmiProxyFactoryBean,
		hessian:     HessianProxyFactoryBean,
		burlap:      BurlapProxyFactoryBean,
		httpinvoker: HttpInvokerProxyFactoryBean]

	private final Logger log = LoggerFactory.getLogger(getClass())

	def registerBeans = { GrailsApplication application ->
		configureInterceptor.delegate = delegate
		configureNewService.delegate = delegate
		configureProxy.delegate = delegate
		exposeProtocol.delegate = delegate
		registerInterceptorGrailsClasses.delegate = delegate
		registerServiceClasses.delegate = delegate

		def interceptorMap = [:]
		registerInterceptorGrailsClasses application, interceptorMap
		registerServiceClasses application, interceptorMap
	}

	private registerInterceptorGrailsClasses = { GrailsApplication application, Map interceptorMap ->

		for (InterceptorGrailsClass interceptorClass in application.remotingInterceptorClasses) {
			log.info "Registering remoting interceptor: $interceptorClass.fullName"

			// What next? Get the pointcut patterns from the interceptor, split each one into class
			// and method patterns, convert the patterns to regular expressions, and add to map of
			// class patterns -> interceptors.

			// Register the user-defined interceptor.
			"$interceptorClass.propertyName"(interceptorClass.clazz)

			// Find out which classes/methods the interceptor applies to
			for (String pointcut in GrailsClassUtils.getStaticPropertyValue(interceptorClass.clazz, 'pointcuts')) {
				int pos = pointcut.lastIndexOf('.')
				if (pos < 1) {
					log.error "Invalid pointcut expression: $pointcut"
					continue
				}

				String classPattern = pointcut[0..pos-1]
				String methodPattern = pointcut[pos+1..-1]

				// Configure an interceptor bean that can be used on proxies
				String beanName = configureInterceptor(methodPattern, interceptorClass.propertyName)

				// Save the bean name for the interceptor against the class pattern
				def interceptorBeans = interceptorMap[classPattern]
				if (!interceptorBeans) {
					interceptorBeans = []
					interceptorMap[classPattern] = interceptorBeans
				}

				interceptorBeans << beanName + 'Proxy'
			}
		}
	}

	private registerServiceClasses = { GrailsApplication application, Map interceptorMap ->
		if (!application.serviceClasses) {
			return
		}

		// Iterate through each of the declared services and configure them for remoting
		for (serviceWrapper in application.serviceClasses) {
			configureNewService serviceWrapper, interceptorMap
		}

		// Required for the HTTP based remoting protocols.
		httpRequestHandlerAdapter(HttpRequestHandlerAdapter)

		// Finally add the custom HandlerMapping.
		remotingUrlHandlerMapping(RemotingUrlHandlerMapping) {
			invokerTypes = new HashSet(remoteExporters.keySet())
		}
	}

	private configureNewService = { GrailsServiceClass serviceWrapper, Map interceptorMap ->
		Class serviceClass = serviceWrapper.clazz
		String exposedName = serviceWrapper.shortName

		// If this service has a static 'remote' property, then it is acting as a proxy for a remote service
		def remoteDef = GrailsClassUtils.getStaticPropertyValue(serviceClass, 'remote')
		if (remoteDef) {
			// Create a proxy for the configured remote service.
			configureClientProxy log, delegate, remoteDef, serviceWrapper.propertyName, exposedName
			return
		}

		// OK, the service isn't configured as a proxy to a remote service, so check whether it
		// should be exposed as a remote exporter by looking for a static 'expose' property.
		def exposeList = GrailsClassUtils.getStaticPropertyValue(serviceClass, 'expose')
		if (!exposeList) {
			// This service has not been configured for remoting
			return
		}

		// Check that the service has an interface to expose.
		if (serviceClass.interfaces.size() == 0 || serviceClass.interfaces[0] == GroovyObject) {
			log.error "Cannot expose service '$exposedName' via remoting: service does not implement any interfaces."
			return
		}

		// Set up Spring invokers for each type specified in the 'expose' list
		Class exposedInterface = serviceClass.interfaces[0]
		if (exposeList) {
			// Create a proxy to the service so that we can hot-swap changes in
			configureProxy serviceWrapper, exposedInterface, interceptorMap
		}

		for (type in exposeList) {
			exposeProtocol type, exposedName, exposedInterface
		}
	}

	/**
	 * Configures a proxy bean for the given service class that allows
	 * us to update the underlying service without forcing a server restart.
	 * @param serviceClass The GrailsClass instance for the service we want to proxy
	 * @param iface The interface that the service will expose to the outside world
	 */
	private configureProxy = { GrailsServiceClass serviceClass, Class iface, Map interceptorMap ->
		log.debug "Configuring proxy for: $serviceClass.shortName"

		// Find out which interceptors should be added.
		def matchingInterceptors = []
		interceptorMap.each { classPattern, interceptorBeans ->
			if (serviceClass.fullName.matches(classPattern)) {
				matchingInterceptors.addAll interceptorBeans
			}
		}

		String exposedName = serviceClass.shortName
		"${exposedName}TargetSource"(HotSwappableTargetSource, ref(serviceClass.propertyName))

		"${exposedName}Proxy"(ProxyFactoryBean) {
			targetSource = ref("${exposedName}TargetSource")
			proxyInterfaces = [iface]

			if (matchingInterceptors) {
				interceptorNames = matchingInterceptors
			}
		}
	}

	/**
	 * Configures an exporter bean for a named service that allows remote clients to access the service via the specified protocol
	 * @param protocol The remote protocol to expose the service via.
	 * @param exposedName The name of the service - this is used to reference the appropriate proxy bean.
	 * @param iface The interface (instance of Class) that the service exposes to remote clients
	 */
	private exposeProtocol = { String protocol, String exposedName, Class iface ->
		if (!remoteExporters.containsKey(protocol)) {
			log.info "Unrecognised invoker protocol: $protocol - ignoring for this service ('$exposedName')."
			return
		}

		log.debug "Exposing protocol '$protocol' for: $exposedName"

		if (log.infoEnabled) log.info "Adding remote bean '${protocol}.${exposedName}' (class=${remoteExporters[protocol]})"

		// Create the exporter bean.
		"${protocol}.${exposedName}"(remoteExporters[protocol]) {
			// The RMI exporter requires a service name in addition to the standard service bean and
			// interface. We also use a non-standard port to ensure that the service does not conflict
			// with an RMI registry on the server.
			if (protocol == 'rmi') {
				serviceName = exposedName

				// Look for an rmi port defined in the current environment's config
				registryPort = (application.config.remoting.rmi.port ?: 1199) as Integer
			}

			// The exporter references a proxy to the service implementation rather than the service itself
			service = ref("${exposedName}Proxy")
			serviceInterface = iface
		}
	}

	// Create a remote proxy for the service, using the config provided by the 'remote' property
	private void configureClientProxy(log, bb, config, String beanName, String exposedName) {
		if (!(config instanceof Map)) {
			log.error "Invalid value for 'remote' property in service '$exposedName' - must be a map."
			return
		}

		if (!config.iface) {
			log.error "Cannot access service '$exposedName' via remoting: service does not specify an interface."
			return
		}

		def protocol = config.protocol
		if (!protocol) {
			log.error "Cannot access service '$exposedName' via remoting: no protocol specified."
			return
		}
		if (!proxyFactories.keySet().contains(protocol)) {
			log.error "Invalid protocol '$protocol' specified; the valid options are: ${proxyFactories.keySet().sort()}"
			return
		}

		// Build the URL of the remote service if one hasn't been specified
		String host = config.host ?: 'localhost'

		if (!config.url) {
			switch (protocol) {
				case 'rmi':
					int port = (config.port ?: 1199) as Integer
					config.url = "rmi://$host:$port/$exposedName"
					break

				case 'httpinvoker':
				case 'hessian':
				case 'burlap':
					int port = (config.port ?: 8080) as Integer
					String context = config.webcontext
					if (context) context += '/'

					config.url = "http://$host:$port/$context$protocol/$exposedName"
					break
			}
		}

		// Now create the proxy client.
		log.info "Creating proxy for '$beanName'"
		bb."$beanName"(proxyFactories[protocol]) { bean ->
			bean.autowire = 'byName'
			bb.serviceUrl = config.url.toString()
			bb.serviceInterface = config.iface
		}

		log.debug "Created proxy client for interface $config.iface.name with URL $config.url for service"
	}

	private configureInterceptor = { String methodPattern, String interceptorName ->
		// Create a wrapper for the given interceptor.
		String wrapperName = "${interceptorName}Wrapper_${methodPattern}"

		"$wrapperName"(InterceptorWrapper) {
			interceptor = ref(interceptorName)
			pattern = methodPattern.replaceAll(/\*/, '.*')
		}

		// Hot-swapping for the interceptor.
		"${wrapperName}TargetSource"(HotSwappableTargetSource, ref(wrapperName))

		"${wrapperName}Proxy"(ProxyFactoryBean) {
			targetSource = ref("${wrapperName}TargetSource")
			proxyInterfaces = [MethodInterceptor]
		}

		return wrapperName
	}
}
