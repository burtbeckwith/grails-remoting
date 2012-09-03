grails.project.work.dir = 'target'
grails.project.docs.output.dir = 'docs/manual'
grails.project.source.level = 1.6

grails.project.dependency.resolution = {

	inherits 'global'
	log 'warn'

	repositories {
		grailsCentral()
		mavenLocal()
		mavenCentral()
	}

	dependencies {
		runtime 'com.caucho:hessian:4.0.7'
	}

	plugins {
		build(':release:2.0.4', ':rest-client-builder:1.0.2') {
			export = false
		}
	}
}
