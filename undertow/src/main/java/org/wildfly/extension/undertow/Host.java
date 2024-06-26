/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.undertow;

import static org.wildfly.extension.undertow.HostDefinition.QUEUE_REQUESTS_ON_START;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import io.undertow.Handlers;
import io.undertow.security.api.AuthenticationMechanism;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.server.handlers.PathHandler;
import io.undertow.server.handlers.resource.PathResourceManager;
import io.undertow.server.handlers.resource.ResourceHandler;
import io.undertow.server.handlers.resource.ResourceManager;
import io.undertow.servlet.api.Deployment;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.util.CopyOnWriteMap;
import io.undertow.util.Methods;
import org.jboss.as.controller.ControlledProcessState;
import org.jboss.as.controller.ControlledProcessStateService;
import org.jboss.as.server.suspend.ServerActivity;
import org.jboss.as.server.suspend.ServerActivityCallback;
import org.jboss.as.server.suspend.SuspendController;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.wildfly.extension.undertow.deployment.GateHandlerWrapper;
import org.wildfly.extension.undertow.logging.UndertowLogger;
import org.wildfly.security.manager.WildFlySecurityManager;
import org.wildfly.service.descriptor.BinaryServiceDescriptor;

/**
 * @author <a href="mailto:tomaz.cerar@redhat.com">Tomaz Cerar</a> (c) 2013 Red Hat Inc.
 * @author Radoslav Husar
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class Host implements Service<Host>, FilterLocation {
    // TODO Extract proper interface from this service implementation
    // TODO Relocate ServiceDescriptor and interface to a separate SPI module.
    public static final BinaryServiceDescriptor<Host> SERVICE_DESCRIPTOR = BinaryServiceDescriptor.of("org.wildfly.undertow.host", Host.class);

    private final Consumer<Host> serviceConsumer;
    private final Supplier<Server> server;
    private final Supplier<UndertowService> undertowService;
    private final Supplier<ControlledProcessStateService> controlledProcessStateService;
    private final Supplier<SuspendController> suspendController;
    private final PathHandler pathHandler = new PathHandler();
    private final Set<String> allAliases;
    private final String name;
    private final String defaultWebModule;
    private final List<UndertowFilter> filters = new CopyOnWriteArrayList<>();
    private final Set<Deployment> deployments = new CopyOnWriteArraySet<>();
    private final Map<String, LocationService> locations = new CopyOnWriteMap<>();
    private final Map<String, AuthenticationMechanism> additionalAuthenticationMechanisms = new ConcurrentHashMap<>();
    private final HostRootHandler hostRootHandler = new HostRootHandler();
    private final DefaultResponseCodeHandler defaultHandler;
    private final Boolean queueRequestsOnStart;
    private final int defaultResponseCode;
    private volatile HttpHandler rootHandler = null;
    private volatile AccessLogService  accessLogService;
    private volatile GateHandlerWrapper gateHandlerWrapper;
    private volatile Function<HttpHandler, HttpHandler> accessLogHttpHandler;

    ServerActivity suspendListener = new ServerActivity() {
        @Override
        public void preSuspend(ServerActivityCallback listener) {
            defaultHandler.setSuspended(true);
            listener.done();
        }

        @Override
        public void suspended(ServerActivityCallback listener) {
            listener.done();
        }

        @Override
        public void resume() {
            defaultHandler.setSuspended(false);
        }
    };

    public Host(final Consumer<Host> serviceConsumer, final Supplier<Server> server, final Supplier<UndertowService> undertowService,
            final Supplier<ControlledProcessStateService> controlledProcessStateService, final Supplier<SuspendController> suspendController,
            final String name, final List<String> aliases, final String defaultWebModule, final int defaultResponseCode, final Boolean queueRequestsOnStart ) {
        this.serviceConsumer = serviceConsumer;
        this.server = server;
        this.undertowService = undertowService;
        this.controlledProcessStateService = controlledProcessStateService;
        this.suspendController = suspendController;
        this.name = name;
        this.defaultWebModule = defaultWebModule;
        Set<String> hosts = new HashSet<>(aliases.size() + 1);
        hosts.add(name);
        hosts.addAll(aliases);
        allAliases = Collections.unmodifiableSet(hosts);
        this.queueRequestsOnStart = queueRequestsOnStart;
        this.defaultHandler = new DefaultResponseCodeHandler(defaultResponseCode);
        this.defaultResponseCode = defaultResponseCode;
        this.setupDefaultResponseCodeHandler();
    }

    private String getDeployedContextPath(DeploymentInfo deploymentInfo) {
        return "".equals(deploymentInfo.getContextPath()) ? "/" : deploymentInfo.getContextPath();
    }

    @Override
    public void start(StartContext context) throws StartException {
        suspendController.get().registerActivity(suspendListener);
        SuspendController.State state = suspendController.get().getState();
        if(state == SuspendController.State.RUNNING) {
            defaultHandler.setSuspended(false);
        } else {
            defaultHandler.setSuspended(true);
        }
        ControlledProcessStateService controlledProcessStateService = this.controlledProcessStateService.get();
        //may be null for tests
        if(controlledProcessStateService != null && controlledProcessStateService.getCurrentState() == ControlledProcessState.State.STARTING) {
            // Non-graceful is ControlledProcessState.State.STARTING && SuspendController.State.RUNNING. We know from above
            // that we are STARTING, so we just need to check that state of the SuspendController
            boolean nonGraceful = state == SuspendController.State.RUNNING;

            int statusCode = defaultResponseCode;
            if (nonGraceful && queueRequestsOnStart == null) {
                UndertowLogger.ROOT_LOGGER.debug("Running in non-graceful mode and " + QUEUE_REQUESTS_ON_START.getName() +
                        " not explicitly set. Requests will not be queued.");
            } else {
                if (queueRequestsOnStart == null || Boolean.TRUE.equals(queueRequestsOnStart)) {
                    UndertowLogger.ROOT_LOGGER.info("Queuing requests.");
                    statusCode = -1;
                }

                gateHandlerWrapper = new GateHandlerWrapper(statusCode);
                controlledProcessStateService.addPropertyChangeListener(new PropertyChangeListener() {
                    @Override
                    public void propertyChange(PropertyChangeEvent evt) {
                        controlledProcessStateService.removePropertyChangeListener(this);
                        if (gateHandlerWrapper != null) {
                            gateHandlerWrapper.open();
                            gateHandlerWrapper = null;
                        }
                        rootHandler = null;
                    }
                });
            }
        }
        server.get().registerHost(this);
        UndertowLogger.ROOT_LOGGER.hostStarting(name);
        serviceConsumer.accept(this);
    }

    private HttpHandler configureRootHandler() {
        AccessLogService logService = accessLogService;
        HttpHandler rootHandler = pathHandler;
        final Function<HttpHandler, HttpHandler> accessLogHttpHandler = this.accessLogHttpHandler;

        ArrayList<UndertowFilter> filters = new ArrayList<>(this.filters);

        //handle options * requests
        rootHandler = new OptionsHandler(rootHandler);

        //handle requests that use the Expect: 100-continue header
        rootHandler = Handlers.httpContinueRead(rootHandler);

        rootHandler = LocationService.configureHandlerChain(rootHandler, filters);
        if (logService != null) {
            rootHandler = logService.configureAccessLogHandler(rootHandler);
        }
        if (accessLogHttpHandler != null) {
            rootHandler = accessLogHttpHandler.apply(rootHandler);
        }

        // handle .well-known requests from ACME certificate authorities
        String path = WildFlySecurityManager.getPropertyPrivileged("jboss.home.dir", ".");
        Path base;
        try {
            base = Paths.get(path).normalize().toRealPath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final int cacheBufferSize = 1024;
        final int cacheBuffers = 1024;
        PathResourceManager resourceManager = new PathResourceManager(base, cacheBufferSize * cacheBuffers, true, false);
        rootHandler = new AcmeResourceHandler(resourceManager, rootHandler);

        GateHandlerWrapper gateHandlerWrapper = this.gateHandlerWrapper;
        if(gateHandlerWrapper != null) {
            rootHandler = gateHandlerWrapper.wrap(rootHandler);
        }
        return rootHandler;
    }

    @Override
    public void stop(final StopContext context) {
        serviceConsumer.accept(null);
        server.get().unregisterHost(this);
        pathHandler.clearPaths();
        if(gateHandlerWrapper != null) {
            gateHandlerWrapper.open();
            gateHandlerWrapper = null;
        }
        UndertowLogger.ROOT_LOGGER.hostStopping(name);
        suspendController.get().unRegisterActivity(suspendListener);
    }

    @Override
    public Host getValue() throws IllegalStateException, IllegalArgumentException {
        return this;
    }

    void setAccessLogService(AccessLogService service) {
        this.accessLogService = service;
        rootHandler = null;
    }

    void setAccessLogHandler(final Function<HttpHandler, HttpHandler> accessLogHttpHandler) {
        this.accessLogHttpHandler = accessLogHttpHandler;
        rootHandler = null;
    }

    public Server getServer() {
        return server.get();
    }

    public Set<String> getAllAliases() {
        return allAliases;
    }

    public String getName() {
        return name;
    }

    protected HttpHandler getRootHandler() {
        return hostRootHandler;
    }

    List<UndertowFilter> getFilters() {
        return Collections.unmodifiableList(filters);
    }

    protected HttpHandler getOrCreateRootHandler() {
        HttpHandler root = rootHandler;
        if(root == null) {
            synchronized (this) {
                root = rootHandler;
                if(root == null) {
                    return rootHandler = configureRootHandler();
                }
            }
        }
        return root;
    }

    public String getDefaultWebModule() {
        return defaultWebModule;
    }

    public void registerDeployment(final Deployment deployment, HttpHandler handler) {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        String path = getDeployedContextPath(deploymentInfo);
        registerHandler(path, handler);
        deployments.add(deployment);
        UndertowLogger.ROOT_LOGGER.registerWebapp(path, getServer().getName());
        undertowService.get().fireEvent(listener -> listener.onDeploymentStart(deployment, Host.this));
    }

    public void unregisterDeployment(final Deployment deployment) {
        DeploymentInfo deploymentInfo = deployment.getDeploymentInfo();
        String path = getDeployedContextPath(deploymentInfo);
        undertowService.get().fireEvent(listener -> listener.onDeploymentStop(deployment, Host.this));
        unregisterHandler(path);
        deployments.remove(deployment);
        UndertowLogger.ROOT_LOGGER.unregisterWebapp(path, getServer().getName());
    }

    void registerLocation(String path) {
        String realPath = path.startsWith("/") ? path : "/" + path;
        locations.put(realPath, null);
        undertowService.get().fireEvent(listener -> listener.onDeploymentStart(realPath, Host.this));
    }

    void unregisterLocation(String path) {
        String realPath = path.startsWith("/") ? path : "/" + path;
        locations.remove(realPath);
        undertowService.get().fireEvent(listener -> listener.onDeploymentStop(realPath, Host.this));
    }

    public void registerHandler(String path, HttpHandler handler) {
        pathHandler.addPrefixPath(path, handler);
    }

    public void unregisterHandler(String path) {
        pathHandler.removePrefixPath(path);
        // if there is registered location for given path, serve it from now on
        LocationService location = locations.get(path);
        if (location != null) {
            pathHandler.addPrefixPath(location.getLocationPath(), location.getLocationHandler());
        }
        // else serve the default response code
        else if (path.equals("/")) {
            this.setupDefaultResponseCodeHandler();
        }
    }

    void registerLocation(LocationService location) {
        locations.put(location.getLocationPath(), location);
        registerHandler(location.getLocationPath(), location.getLocationHandler());
        undertowService.get().fireEvent(listener -> listener.onDeploymentStart(location.getLocationPath(), Host.this));
    }

    void unregisterLocation(LocationService location) {
        locations.remove(location.getLocationPath());
        unregisterHandler(location.getLocationPath());
        undertowService.get().fireEvent(listener -> listener.onDeploymentStop(location.getLocationPath(), Host.this));
    }

    public Set<String> getLocations() {
        return Collections.unmodifiableSet(locations.keySet());
    }

    /**
     * @return set of currently registered {@link Deployment}s on this host
     */
    public Set<Deployment> getDeployments() {
        return Collections.unmodifiableSet(deployments);
    }

    void registerAdditionalAuthenticationMechanism(String name, AuthenticationMechanism authenticationMechanism){
        additionalAuthenticationMechanisms.put(name, authenticationMechanism);
    }

    void unregisterAdditionalAuthenticationMechanism(String name){
        additionalAuthenticationMechanisms.remove(name);
    }

    public Map<String, AuthenticationMechanism> getAdditionalAuthenticationMechanisms() {
        return new LinkedHashMap<>(additionalAuthenticationMechanisms);
    }

    @Override
    public void addFilter(UndertowFilter filterRef) {
        filters.add(filterRef);
        rootHandler = null;
    }

    @Override
    public void removeFilter(UndertowFilter filterRef) {
        filters.remove(filterRef);
        rootHandler = null;
    }

    public SuspendController.State getSuspendState() {
        return this.suspendController.get().getState();
    }

    protected void setupDefaultResponseCodeHandler(){
        if(this.defaultHandler != null){
            this.registerHandler("/", this.defaultHandler);
        }
    }

    private static final class OptionsHandler implements HttpHandler {

        private final HttpHandler next;

        private OptionsHandler(HttpHandler next) {
            this.next = next;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if(exchange.getRequestMethod().equals(Methods.OPTIONS) &&
                    exchange.getRelativePath().equals("*")) {
                //handle the OPTIONS requests
                //basically just return an empty response
                exchange.endExchange();
                return;
            }
            next.handleRequest(exchange);
        }
    }

    private static final class AcmeResourceHandler extends ResourceHandler {
        private static final String ACME_CHALLENGE_CONTEXT = "/.well-known/acme-challenge/";
        private static final Pattern ACME_CHALLENGE_PATTERN = Pattern.compile("/\\.well-known/acme-challenge/[A-Za-z0-9_-]+");

        private final HttpHandler next;

        private AcmeResourceHandler(ResourceManager resourceManager, HttpHandler next) {
            super(resourceManager, next);
            this.next = next;
        }

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            if (exchange.getRequestMethod().equals(Methods.GET)
                    && exchange.getRelativePath().startsWith(ACME_CHALLENGE_CONTEXT, 0)
                    && ACME_CHALLENGE_PATTERN.matcher(exchange.getRelativePath()).matches()) {
                super.handleRequest(exchange);
            } else {
                next.handleRequest(exchange);
            }
        }
    }

    private class HostRootHandler implements HttpHandler {
        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            getOrCreateRootHandler().handleRequest(exchange);
        }
    }
}
