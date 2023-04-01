/*
 * Copyright 2023 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.extension.elytron.dynamicform;

import org.jboss.as.controller.capability.RuntimeCapability;

import io.undertow.server.HttpHandler;

/**
 * The capabilities provided by and required by this subsystem.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class Capabilities {

    private static final String CAPABILITY_BASE = "org.wildfly.security.";

    static final String FORM_AUTH_CONTEXT_CAPABILITY = CAPABILITY_BASE + "form-auth-context";

    static final RuntimeCapability<Void> FORM_AUTH_CONTEXT_RUNTIME_CAPABILITY = RuntimeCapability
        .Builder.of(FORM_AUTH_CONTEXT_CAPABILITY, true, HttpHandler.class)
        .build();

}
