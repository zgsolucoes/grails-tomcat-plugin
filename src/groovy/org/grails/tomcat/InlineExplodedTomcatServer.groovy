/*
 * Copyright 2011 the original author or authors.
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
package org.grails.tomcat

import org.apache.catalina.Container
import org.apache.catalina.Lifecycle
import org.apache.catalina.LifecycleListener
import org.apache.catalina.Loader
import org.apache.catalina.core.StandardContext
import org.apache.catalina.deploy.ContextEnvironment
import org.apache.catalina.startup.Tomcat

import org.apache.juli.logging.Log
import org.apache.juli.logging.LogFactory

import org.apache.naming.resources.DirContextURLStreamHandlerFactory
import org.apache.naming.resources.DirContextURLStreamHandler

import grails.util.*
import org.codehaus.groovy.grails.plugins.PluginManagerHolder

import java.beans.PropertyChangeListener

/**
 * Serves the app, without packaging as a war and runs it in the same JVM.
 */
class InlineExplodedTomcatServer extends TomcatServer {

    final Tomcat tomcat

    def context

    InlineExplodedTomcatServer(String basedir, String webXml, String contextPath, ClassLoader classLoader) {
        super()

        tomcat = new Tomcat()

        if (contextPath == '/') {
            contextPath = ''
        }

        tomcat.basedir = tomcatDir
        context = tomcat.addWebapp(contextPath, basedir)
        tomcat.enableNaming()

        // we handle reloading manually
        context.reloadable = false
        context.setAltDDName(getWorkDirFile("resources/web.xml").absolutePath)

        def aliases = []
        def pluginManager = PluginManagerHolder.getPluginManager()

        if (pluginManager != null) {
            for (plugin in pluginManager.userPlugins) {
                def dir = pluginSettings.getPluginDirForName(GrailsNameUtils.getScriptName(plugin.name))
                def webappDir = dir ? new File("${dir.file.absolutePath}/web-app") : null
                if (webappDir?.exists()) {
                    aliases << "/plugins/${plugin.fileSystemName}=${webappDir.absolutePath}"
                }
            }
        }

        if (aliases) {
            context.setAliases(aliases.join(','))
        }

        def loader = new TomcatLoader(classLoader)
        loader.container = context
        context.loader = loader
    }

    void doStart(String host, int httpPort, int httpsPort) {
        preStart()
        tomcat.hostname = host
        tomcat.port = httpPort
        tomcat.connector.URIEncoding = 'UTF-8'

        if (host != "localhost") {
            tomcat.connector.setAttribute("address", host)
        }

        if (httpsPort) {
            def sslConnector = loadInstance('org.apache.catalina.connector.Connector')
            sslConnector.scheme = "https"
            sslConnector.secure = true
            sslConnector.port = httpsPort
            sslConnector.setProperty("SSLEnabled","true")
            sslConnector.setAttribute("keystore", keystoreFile.absolutePath)
            sslConnector.setAttribute("keystorePass", keyPassword)
            sslConnector.URIEncoding = 'UTF-8'

            if (host != "localhost") {
                sslConnector.setAttribute("address", host)
            }

            tomcat.service.addConnector(sslConnector)
        }

        tomcat.start()
    }

    void stop() {
        tomcat.stop()
    }

    private loadInstance(String name) {
        tomcat.class.classLoader.loadClass(name).newInstance()
    }

    private preStart() {
        eventListener?.event("ConfigureTomcat", [tomcat])
        def jndiEntries = grailsConfig?.grails?.naming?.entries

        if (jndiEntries instanceof Map) {
            jndiEntries.each { name, resCfg ->
                if (resCfg) {
                    if (!resCfg["type"]) {
                        throw new IllegalArgumentException("Must supply a resource type for JNDI configuration")
                    }
                    def res = loadInstance('org.apache.catalina.deploy.ContextResource')
                    res.name = name
                    res.type = resCfg.remove("type")
                    res.auth = resCfg.remove("auth")
                    res.description = resCfg.remove("description")
                    res.scope = resCfg.remove("scope")
                    // now it's only the custom properties left in the Map...
                    resCfg.each {key, value ->
                        res.setProperty (key, value)
                    }

                    context.namingResources.addResource res
                }
            }
        }
    }

    static class TomcatLoader implements Loader, Lifecycle {

        private static Log log = LogFactory.getLog(TomcatLoader.name)
        private static boolean first = true

        ClassLoader classLoader
        Container container
        boolean delegate
        boolean reloadable

        TomcatLoader(ClassLoader classLoader) {
            // Class loader that only searches the parent
            this.classLoader = new ClassLoader(classLoader) {
                Class<?> findClass(String name) {
                    parent.findClass(name)
                }
            }
        }

        void addPropertyChangeListener(PropertyChangeListener listener) {

        }

        void addRepository(String repository) {
            log.warn "Call to addRepository($repository) was ignored."
        }

        void backgroundProcess() {

        }

        String[] findRepositories() {
            log.warn "Call to findRepositories() returned null."
        }

        String getInfo() {
            "MyLoader/1.0"
        }

        boolean modified() {
            false
        }

        void removePropertyChangeListener(PropertyChangeListener listener) {

        }

        void addLifecycleListener(LifecycleListener listener) {
            log.warn "Call to addLifecycleListener($listener) was ignored."
        }

        LifecycleListener[] findLifecycleListeners() {
            log.warn "Call to findLifecycleListeners() returned null."
        }

        void removeLifecycleListener(LifecycleListener listener) {
            log.warn "Call to removeLifecycleListener(${listener}) was ignored."
        }

        void start()  {
          URLStreamHandlerFactory streamHandlerFactory = new DirContextURLStreamHandlerFactory()

          if (first) {
              first = false
              try {
                  URL.setURLStreamHandlerFactory(streamHandlerFactory)
              } catch (Exception e) {
                  // Log and continue anyway, this is not critical
                  log.error("Error registering jndi stream handler", e)
              } catch (Throwable t) {
                  // This is likely a dual registration
                  log.info("Dual registration of jndi stream handler: "
                           + t.getMessage())
              }
          }

          DirContextURLStreamHandler.bind(classLoader, container.getResources())
        }

        void stop()  {
            classLoader = null
        }
    }
}