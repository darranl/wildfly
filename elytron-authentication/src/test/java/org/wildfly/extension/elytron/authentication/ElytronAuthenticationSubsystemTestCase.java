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

package org.wildfly.extension.elytron.authentication;

import java.io.IOException;

import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;

/**
 * Subsystem test for the Elytron Authentication subsystem.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class ElytronAuthenticationSubsystemTestCase extends AbstractSubsystemBaseTest {

    public ElytronAuthenticationSubsystemTestCase() {
        super(ElytronAuthenticationExtension.SUBSYSTEM_NAME, new ElytronAuthenticationExtension());
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return "<subsystem xmlns=\"urn:wildfly:elytron-authentication:1.0\"/>";
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return "schema/wildfly-elytron-authentication_1_0.xsd";
    }

    //no point in testing 1.0.0 (current) --> 1.0.0 (all previous) for transformers
}
