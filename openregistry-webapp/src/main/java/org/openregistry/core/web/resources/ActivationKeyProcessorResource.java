/**
 * Copyright (C) 2009 Jasig, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openregistry.core.web.resources;

import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.openregistry.core.service.ActivationService;
import org.openregistry.core.domain.PersonNotFoundException;
import org.openregistry.core.domain.ActivationKey;
import org.openregistry.core.domain.LockingException;

import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.Context;

import com.sun.jersey.api.NotFoundException;

/**
 * RESTful resource exposing the activation key processing functions i.e. 'invalidation' and 'verification' of activation keys
 * for people in Open Registry
 *
 * @author Dmitriy Kopylenko
 * @since 1.0
 */
@Component
@Scope("singleton")
@Path("/people/{personIdType}/{personId}/activation/{activationKey}")
public final class ActivationKeyProcessorResource {

    @Autowired
    private ActivationService activationService;

    @DELETE
    public Response invalidateActivationKey(@PathParam("personIdType") String personIdType,
                                            @PathParam("personId") String personId,
                                            @PathParam("activationKey") String activationKey,
                                            @Context SecurityContext securityContext) {
        try {
            this.activationService.invalidateActivationKey(personIdType, personId, activationKey, securityContext.getUserPrincipal().getName());
        } catch (final IllegalStateException e) {
            return Response.status(409).entity(String.format("The activation key [%s] is not valid.", activationKey)).type(MediaType.TEXT_PLAIN).build();
            
        }
        catch (final IllegalArgumentException e) {
            return Response.status(400).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }
        catch (final PersonNotFoundException e) {
            throw new NotFoundException(String.format("The person resource identified by /people/%s/%s URI does not exist", personIdType, personId));
        } catch (final LockingException e) {
            return Response.status(409).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }         
        //If response is null, that means HTTP 204
        return null;
    }

    @GET
    public Response verifyActivationKey(@PathParam("personIdType") String personIdType,
                                          @PathParam("personId") String personId,
                                          @PathParam("activationKey") String activationKey,
                                          @Context SecurityContext securityContext) {
        try {
            final ActivationKey ak = this.activationService.getActivationKey(personIdType, personId, activationKey, securityContext.getUserPrincipal().getName());

            if (ak == null) {
                throw new NotFoundException(String.format("The activation key [%s] does not exist", activationKey));
            }
            if(ak.isNotYetValid()) {
                //HTTP 409
                return Response.status(409).entity(String.format("The activation key [%s] is not yet valid for use", activationKey)).type(MediaType.TEXT_PLAIN).build();
            }
            else if(ak.isExpired()) {
                //HTTP 410
                return Response.status(410).entity(String.format("The activation key [%s] has expired", activationKey)).type(MediaType.TEXT_PLAIN).build();
            }
        } catch(final PersonNotFoundException e) {
            throw new NotFoundException(String.format("The person resource identified by /people/%s/%s URI does not exist", personIdType, personId));    
        } catch (final IllegalArgumentException e) {
            return Response.status(400).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
        } catch (final LockingException e) {
            return Response.status(409).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
        }
        //If response is null, that means HTTP 204
        return null;
    }
}
