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

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AbstractRemoveStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelNode;

/**
 * Definition of a single dynamic form instance.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class DynamicFormDefinition extends SimpleResourceDefinition {

    DynamicFormDefinition() {
        super(new Parameters(PathElement.pathElement(ElytronDynamicFormConstants.DYNAMIC_FORM),
                ElytronDynamicFormExtension.getResolver(ElytronDynamicFormConstants.DYNAMIC_FORM))
                .setAddHandler(DynamicFormAddHandler.INSTANCE)
                .setRemoveHandler(DynamicFormRemoveHandler.INSTANCE)
                .setAddRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES)
                .setRemoveRestartLevel(OperationEntry.Flag.RESTART_RESOURCE_SERVICES));
    }


    static class DynamicFormAddHandler extends AbstractAddStepHandler {
        public static DynamicFormAddHandler INSTANCE = new DynamicFormAddHandler();

        private DynamicFormAddHandler() {
            super();
        }

        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
        }
    }

    static class DynamicFormRemoveHandler extends AbstractRemoveStepHandler {
        public static DynamicFormRemoveHandler INSTANCE = new DynamicFormRemoveHandler();

        DynamicFormRemoveHandler() {
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
        }
    }
}
