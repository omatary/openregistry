/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.openregistry.core.web.resources;

import com.sun.jersey.spi.spring.container.servlet.SpringServlet;
import com.sun.jersey.test.framework.WebAppDescriptor;
import org.junit.Test;
import org.openregistry.core.web.resources.representations.PersonRequestRepresentation;
import org.springframework.web.context.ContextLoaderListener;

import static org.junit.Assert.assertNotNull;

/**
 * @since 1.0
 * @author Dmitriy Kopylenko
 */
public class PostNewPersonToPeopleResourceTests extends JerseyTestSupport {

    private static final String RESOURCE_UNDER_TEST_URI = "/sor/test/people";

    public PostNewPersonToPeopleResourceTests() {
        super(new WebAppDescriptor.Builder("org.openregistry.core.web.resources")
                .contextPath("openregistry")
                .contextParam("contextConfigLocation", "classpath:testPOSTPersonViaRESTApplicationContext.xml")
                .servletClass(SpringServlet.class)
                .contextListenerClass(ContextLoaderListener.class)
                .build());
    }

    @Test
    public void incorrectIncomingMediaType() {
        assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(415, RESOURCE_UNDER_TEST_URI,
                POST_HTTP_METHOD, "incorect media type");
    }

    @Test
    public void addingNonExistentPerson() {
        assertNotNull(assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(201, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.forNewPerson()).getHeaders().getFirst("Location"));
    }

    @Test
    public void addingExistingPersonNoSoRRecord() {
        assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(204, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.forExistingPersonNoSoRRecord());
    }

    @Test
    public void addingExistingPersonExistingSoRRecord() {
        assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(409, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.forExistingPersonExistingSoRRecord());
    }

    @Test
    public void addingPersonWithValidationErrors() {
        assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(400, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.withValidationErrors());
    }

    @Test
    public void forceAddingPersonWithMultiplePeopleFound() {
        assertNotNull(assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntityWithQueryParam(201, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.forMultiplePeople(), "force", "y").getHeaders().getFirst("Location"));
    }

    @Test
    public void addingPersonWithMultiplePeopleFound() {
        assertStatusCodeEqualsForRequestUriAndHttpMethodAndEntity(409, RESOURCE_UNDER_TEST_URI, POST_HTTP_METHOD,
                PersonRequestRepresentation.forMultiplePeople());
    }
}
