tomcatVersion = "7.0.72"

Map<String, String> ENV = System.getenv()
String mvnRepoHostDeploy = ENV['MVN_REPO_HOST']
String mvnRepoUserDeploy = ENV['MVN_REPO_USER']
String mvnRepoPasswordDeploy = ENV['MVN_REPO_PASSWORD']

// ---------------------------------------------------------------------------------------------------------------------

grails.project.class.dir = 'target/classes'
grails.project.test.class.dir = 'target/test-classes'
grails.project.test.reports.dir = 'target/test-reports'

grails.project.dependency.resolution = {

    inherits "global"
    log "warn"

    repositories {
        grailsPlugins()
        grailsHome()
        grailsCentral()
        mavenLocal()
        mavenCentral()
        mavenRepo ENV['MVN_REPO_REPOSITORIES_URL_LIBS']
        mavenRepo ENV['MVN_REPO_REPOSITORIES_GRAILS_PLUGINS']
    }
    credentials {
        realm = ENV['MVN_REPO_REALM']
        host = mvnRepoHostDeploy
        username = mvnRepoUserDeploy
        password = mvnRepoPasswordDeploy
    }

    dependencies {
        runtime("org.apache.tomcat:tomcat-catalina-ant:$tomcatVersion") {
            excludes 'tomcat-catalina', 'tomcat-coyote'
        }
        compile "org.apache.tomcat.embed:tomcat-embed-core:$tomcatVersion"
        runtime "org.apache.tomcat.embed:tomcat-embed-jasper:$tomcatVersion"
        runtime "org.apache.tomcat.embed:tomcat-embed-logging-log4j:$tomcatVersion"
        runtime "org.apache.tomcat.embed:tomcat-embed-logging-juli:$tomcatVersion"
        runtime "org.apache.tomcat.embed:tomcat-embed-websocket:$tomcatVersion"

		// needed for JSP compilation
		runtime "org.eclipse.jdt.core.compiler:ecj:3.7.2"
		
        compile("org.grails:grails-plugin-tomcat:$grailsVersion") {
            excludes group: "org.grails", name: "grails-core"
            excludes group: "org.grails", name: "grails-bootstrap"
            excludes group: "org.grails", name: "grails-web"

        }
    }

    plugins {
        build(':release:2.2.1', ':rest-client-builder:1.0.3') {
            export = false
        }
    }
}

grails.project.repos.releases.url = ENV['MVN_REPO_REPOSITORIES_URL_PLUGINS_RELEASE']
grails.project.repos.releases.username = mvnRepoUserDeploy
grails.project.repos.releases.password = mvnRepoPasswordDeploy

grails.project.repos.snapshots.url = ENV['MVN_REPO_REPOSITORIES_URL_PLUGINS_SNAPSHOT']
grails.project.repos.snapshots.username = mvnRepoUserDeploy
grails.project.repos.snapshots.password = mvnRepoPasswordDeploy

grails.project.repos.default = 'snapshots'
