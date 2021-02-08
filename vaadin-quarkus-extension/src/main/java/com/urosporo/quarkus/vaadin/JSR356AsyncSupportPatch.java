/*
 * Copyright 2018 Async-IO.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.urosporo.quarkus.vaadin;

import org.atmosphere.container.JSR356Endpoint;
import org.atmosphere.container.Servlet30CometSupport;
import org.atmosphere.cpr.ApplicationConfig;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereFramework;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.util.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletContext;
import javax.websocket.DeploymentException;
import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerContainer;
import javax.websocket.server.ServerEndpointConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * A copy of the JSR356AsyncSupport Atmosphere file. The class is identical to the original, however
 * the {@link AtmosphereConfigurator} is patched to work around https://github.com/mvysny/vaadin-quarkus/issues/17.
 * See {@link AtmosphereConfigurator} for more details. Also see
 * https://github.com/Atmosphere/atmosphere/issues/2427 for more details.
 */
public class JSR356AsyncSupportPatch extends Servlet30CometSupport {

    private static final Logger logger = LoggerFactory.getLogger(JSR356AsyncSupportPatch.class);
    private final AtmosphereConfigurator configurator;

    public JSR356AsyncSupportPatch(AtmosphereConfig config) {
        this(config, config.getServletContext());
    }

    public JSR356AsyncSupportPatch(AtmosphereConfig config, ServletContext ctx) {
        super(config);
        ServerContainer container = (ServerContainer) ctx.getAttribute(ServerContainer.class.getName());

        if (container == null) {
            if (ctx.getServerInfo().contains("WebLogic")) {
                logger.error("{} must use JDK 1.8+ with WebSocket", ctx.getServerInfo());
            }
            throw new IllegalStateException("Unable to configure jsr356 at that stage. ServerContainer is null");
        }

        int pathLength = 5;
        String s = config.getInitParameter(ApplicationConfig.JSR356_PATH_MAPPING_LENGTH);
        if (s != null) {
            pathLength = Integer.valueOf(s);
        }
        logger.trace("JSR356 Path mapping Size {}", pathLength);

        String servletPath = config.getInitParameter(ApplicationConfig.JSR356_MAPPING_PATH);
        if (servletPath == null) {
            servletPath = IOUtils.guestServletPath(config);
            if (servletPath.equals("/") || servletPath.equals("/*")) {
                servletPath = "";
            }
        }
        logger.info("JSR 356 Mapping path {}", servletPath);
        configurator = new AtmosphereConfigurator(config.framework());

        // Register endpoints at
        // /servletPath
        // /servletPath/
        // /servletPath/{path1}
        // /servletPath/{path1}/{path2}
        // etc with up to `pathLength` parameters 

        StringBuilder b = new StringBuilder(servletPath);
        List<String> endpointPaths = new ArrayList<>();
        endpointPaths.add(servletPath);
        if (!servletPath.endsWith("/")) {
            endpointPaths.add(servletPath+"/");
        }
        for (int i = 0; i < pathLength; i++) {
            b.append("/{path" + i + "}");
            endpointPaths.add(b.toString());
        }

        for (String endpointPath : endpointPaths) {
            if ("".equals(endpointPath)) {
                // Spec says: The path is always non-null and always begins with a leading "/".
                // Root mapping is covered by /{path1}
                continue;
            }
            try {
                ServerEndpointConfig endpointConfig = ServerEndpointConfig.Builder.create(JSR356Endpoint.class, endpointPath).configurator(configurator).build();
                container.addEndpoint(endpointConfig);
            } catch (DeploymentException e) {
                logger.warn("Duplicate Servlet Mapping Path {}. Use {} init-param to prevent this message", servletPath, ApplicationConfig.JSR356_MAPPING_PATH);
                logger.trace("", e);
                servletPath = IOUtils.guestServletPath(config);
                logger.warn("Duplicate guess {}", servletPath, e);
            }
        }
    }

    public boolean supportWebSocket() {
        return true;
    }

    @Override
    public String getContainerName() {
        return super.getContainerName() + " and jsr356/WebSocket API";
    }

    public final static class AtmosphereConfigurator extends ServerEndpointConfig.Configurator {

        private final AtmosphereFramework framework;
        /**
         * TODO: UGLY!
         * Quarkus call modifyHandshake BEFORE getEndpointInstance() where other jsr356 do the reverse.
         * However, because of VertX the thread pool of Undertow handling regular http requests
         * differ to the thread pool handling websocket requests, so we can't use ThreadLocals to remember
         * the HandshakeRequest. Use BlockingDeque with the capacity of 1 since we don't want
         * the requests to get mixed up in case of high concurrency.
         *
         * The reasoning for BlockingDeque is that we expect that a call to getEndpointInstance() will
         * immediately follow call to modifyHandshake(). We will therefore store the request into this deque,
         * and we will pick it up in getEndpointInstance().
         *
         * The deque has a capacity of 1: if there are any concurrent attempts to initialize the endpoint,
         * any follow-up modifyHandshake() will be delayed until the getEndpointInstance() is called.
         *
         * For the ThreadLocal-based solution see the original JSR356AsyncSupport class from Atmosphere.
         */
        private final BlockingDeque<HandshakeRequest> requests = new LinkedBlockingDeque<>(1);

        public AtmosphereConfigurator(AtmosphereFramework framework) {
            this.framework = framework;
        }

        public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
            if (JSR356Endpoint.class.isAssignableFrom(endpointClass)) {
                JSR356Endpoint e = new JSR356Endpoint(framework, WebSocketProcessorFactory.getDefault().getWebSocketProcessor(framework));
                final HandshakeRequest request = requests.removeFirst();
                e.handshakeRequest(request);
                return (T) e;
            } else {
                return super.getEndpointInstance(endpointClass);
            }
        }

        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            try {
                // if there's an ongoing endpoint initialization, delay this call
                // until the getEndpointInstance() has been called.
                if (!requests.offer(request, 1, TimeUnit.SECONDS)) {
                    throw new RuntimeException("Timed out waiting for getEndpointInstance() to be called");
                }
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}