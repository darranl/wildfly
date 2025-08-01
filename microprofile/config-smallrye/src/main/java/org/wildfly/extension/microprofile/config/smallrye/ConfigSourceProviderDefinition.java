/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.extension.microprofile.config.smallrye;

import static org.jboss.as.controller.ModuleIdentifierUtil.parseCanonicalModuleIdentifier;

import static org.jboss.as.controller.SimpleAttributeDefinitionBuilder.create;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MODULE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.wildfly.extension.microprofile.config.smallrye._private.MicroProfileConfigLogger.ROOT_LOGGER;

import java.util.Arrays;
import java.util.Collection;

import org.eclipse.microprofile.config.spi.ConfigSourceProvider;
import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshaller;
import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PersistentResourceDefinition;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.modules.Module;

/**
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2017 Red Hat inc.
 */
class ConfigSourceProviderDefinition extends PersistentResourceDefinition {

    static ObjectTypeAttributeDefinition CLASS = ObjectTypeAttributeDefinition.Builder.of("class",
            create(NAME, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build(),
            create(MODULE, ModelType.STRING, false)
                    .setAllowExpression(false)
                    .build())
            .setRequired(false)
            .setAttributeMarshaller(AttributeMarshaller.ATTRIBUTE_OBJECT)
            .setRestartAllServices()
            .build();

    static AttributeDefinition[] ATTRIBUTES = { CLASS };

    protected ConfigSourceProviderDefinition(Registry<ConfigSourceProvider> providers) {
        super(new SimpleResourceDefinition.Parameters(MicroProfileConfigExtension.CONFIG_SOURCE_PROVIDER_PATH,
                MicroProfileConfigExtension.getResourceDescriptionResolver(MicroProfileConfigExtension.CONFIG_SOURCE_PROVIDER_PATH.getKey()))
                .setAddHandler(new ConfigSourceProviderDefinitionAddHandler(providers))
                .setRemoveHandler(ReloadRequiredRemoveStepHandler.INSTANCE));
    }

    @Override
    public Collection<AttributeDefinition> getAttributes() {
        return Arrays.asList(ATTRIBUTES);
    }

    private static Class unwrapClass(ModelNode classModel) throws OperationFailedException {
        String className = classModel.get(NAME).asString();
        String moduleName = classModel.get(MODULE).asString();
        try {
            Module module = Module.getCallerModuleLoader().loadModule(parseCanonicalModuleIdentifier(moduleName));
            Class<?> clazz = module.getClassLoader().loadClass(className);
            return clazz;
        } catch (Exception e) {
            throw ROOT_LOGGER.unableToLoadClassFromModule(className, moduleName);
        }
    }

    private static class ConfigSourceProviderDefinitionAddHandler extends AbstractAddStepHandler {
        private final Registry<ConfigSourceProvider> providers;

        private ConfigSourceProviderDefinitionAddHandler(Registry<ConfigSourceProvider> providers) {
            super(ATTRIBUTES);
            this.providers = providers;
        }

        @Override
        protected void performRuntime(OperationContext context, ModelNode operation, ModelNode model) throws OperationFailedException {
            super.performRuntime(context, operation, model);
            ModelNode classModel = CLASS.resolveModelAttribute(context, model);
            if (classModel.isDefined()) {
                Class configSourceProviderClass = unwrapClass(classModel);
                try {
                    ConfigSourceProviderRegistrationService.install(context,
                            context.getCurrentAddressValue(),
                            ConfigSourceProvider.class.cast(configSourceProviderClass.getDeclaredConstructor().newInstance()),
                            providers);
                } catch (Exception e) {
                    throw new OperationFailedException(e);
                }
            }
        }
    }
}
