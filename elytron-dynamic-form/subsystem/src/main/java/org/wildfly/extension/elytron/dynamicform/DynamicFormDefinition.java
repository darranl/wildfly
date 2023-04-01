/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2023 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.dynamicform;
import static org.wildfly.extension.elytron.dynamicform.Capabilities.FORM_AUTH_CONTEXT_RUNTIME_CAPABILITY;

import java.util.function.Consumer;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ServiceRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

/**
 * Definition of a single dynamic form instance.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DynamicFormDefinition extends SimpleResourceDefinition {

    DynamicFormDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDynamicFormConstants.DYNAMIC_FORM),
                ElytronDynamicFormExtension.getResolver(ElytronDynamicFormConstants.DYNAMIC_FORM))
                .setCapabilities(FORM_AUTH_CONTEXT_RUNTIME_CAPABILITY)
                .setAddHandler(DynamicFormAddHandler.INSTANCE)
                .setRemoveHandler(new ServiceRemoveStepHandler(DynamicFormAddHandler.INSTANCE))
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES));
    }

    static ServiceName fromRuntimeCapability(RuntimeCapability<Void> capability, OperationContext context, Class<?> type) {
        RuntimeCapability<Void> runtimeCapability = capability.fromBaseCapability(context.getCurrentAddressValue());
        return runtimeCapability.getCapabilityServiceName(type);
    }

    static class DynamicFormAddHandler extends AbstractAddStepHandler {
        public static DynamicFormAddHandler INSTANCE = new DynamicFormAddHandler();

        private DynamicFormAddHandler() {
            super();
        }

        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            ServiceName formAuthContextName = fromRuntimeCapability(FORM_AUTH_CONTEXT_RUNTIME_CAPABILITY, context, HttpHandler.class);

            HttpHandler theHttpHandler = new SimpleHttpHandler();

            ServiceTarget serviceTarget = context.getServiceTarget();
            ServiceBuilder<?> serviceBuilder = serviceTarget.addService(formAuthContextName);
            Consumer<HttpHandler> consumer = serviceBuilder.provides(formAuthContextName);

            serviceBuilder.setInstance(Service.newInstance(consumer, theHttpHandler));
            serviceBuilder.setInitialMode(Mode.ON_DEMAND);
            serviceBuilder.install();
        }
    }

    static class SimpleHttpHandler implements HttpHandler {

        @Override
        public void handleRequest(HttpServerExchange exchange) throws Exception {
            exchange.getResponseSender().send("Hello from dynamic FORM.");
        }

    }
}
