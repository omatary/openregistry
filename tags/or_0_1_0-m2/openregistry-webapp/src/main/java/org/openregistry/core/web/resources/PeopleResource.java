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

import org.openregistry.core.domain.*;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Scope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectFactory;
import org.openregistry.core.domain.sor.ReconciliationCriteria;
import org.openregistry.core.domain.sor.SorPerson;
import org.openregistry.core.domain.sor.SorRole;
import org.openregistry.core.domain.sor.SorSponsor;
import org.openregistry.core.service.PersonService;
import org.openregistry.core.service.ServiceExecutionResult;
import org.openregistry.core.service.IdentifierChangeService;
import org.openregistry.core.service.reconciliation.PersonMatch;
import org.openregistry.core.service.reconciliation.ReconciliationException;
import org.openregistry.core.web.resources.representations.LinkRepresentation;
import org.openregistry.core.web.resources.representations.PersonRequestRepresentation;
import org.openregistry.core.web.resources.representations.PersonResponseRepresentation;
import org.openregistry.core.web.resources.representations.RoleRepresentation;
import org.openregistry.core.repository.ReferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.validation.ConstraintViolation;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.annotation.Resource;
import java.net.URI;
import java.util.*;

import com.sun.jersey.api.NotFoundException;
import com.sun.jersey.api.representation.Form;
import org.springframework.util.Assert;

/**
 * Root RESTful resource representing people in Open Registry.
 * This component is managed and autowired by Spring by means of context-component-scan,
 * and served by Jersey when URI is matched against the @Path definition. This bean is a singleton,
 * and therefore is thread-safe.
 *
 * @author Dmitriy Kopylenko
 * @since 1.0
 */
@Component
@Scope("singleton")
@Path("/people")
public final class PeopleResource {

    //Jersey specific injection
    @Context
    UriInfo uriInfo;

    @Autowired(required = true)
    private PersonService personService;

    @Autowired(required = true)
    private ReferenceRepository referenceRepository;

    @Resource(name = "reconciliationCriteriaFactory")
    private ObjectFactory<ReconciliationCriteria> reconciliationCriteriaObjectFactory;

	//JSR-250 injection which is more appropriate here for 'autowiring by name' in the case of multiple types
    //are defined in the app ctx (Strings). The looked up bean name defaults to the property name which
    //needs an injection.
    @Resource
    private String preferredPersonIdentifierType;

    @Autowired
    private IdentifierChangeService identifierChangeService;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final String FORCE_ADD_FLAG = "y";

    @POST
    @Path("sor/{sorSource}/{sorId}/roles")
    @Consumes(MediaType.APPLICATION_XML)
    public Response processIncomingRole(@PathParam("sorSource") final String sorSource,
                                        @PathParam("sorId") final String sorId,
                                        RoleRepresentation roleRepresentation) {

        final SorPerson sorPerson = this.personService.findBySorIdentifierAndSource(sorSource, sorId);
        if (sorPerson == null) {
            //HTTP 404
            throw new NotFoundException(
                    String.format("The person resource identified by [%s/%s] URI does not exist.",
                            sorSource, sorId));
        }

        final SorRole sorRole = buildSorRoleFrom(sorPerson, roleRepresentation);
        final ServiceExecutionResult result = this.personService.validateAndSaveRoleForSorPerson(sorPerson, sorRole);
        if (result.getValidationErrors().size() > 0) {
            //throw new WebApplicationException(400);
            //HTTP 400
            return Response.status(Response.Status.BAD_REQUEST).entity(buildValidationErrorsResponse(result.getValidationErrors())).build();
        }
        //HTTP 201
        return Response.created(this.uriInfo.getAbsolutePath()).build();
    }

    @GET
    @Path("{personIdType}/{personId}")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    //auto content negotiation!
    public PersonResponseRepresentation showPerson(@PathParam("personId") String personId,
                                                   @PathParam("personIdType") String personIdType) {

        final Person person = findPersonOrThrowNotFoundException(personIdType, personId);
        logger.info("Person is found. Building a suitable representation...");
        return new PersonResponseRepresentation(buildPersonIdentifierRepresentations(person.getIdentifiers()));
    }

    @POST
    @Path("{personIdType}/{personId}")
    @Consumes(MediaType.APPLICATION_XML)
    public Response linkSorPersonWithCalculatedPerson(@PathParam("personId") String personId,
                                                      @PathParam("personIdType") String personIdType,
                                                      PersonRequestRepresentation personRequestRepresentation) {

        Response response = validate(personRequestRepresentation);
        if (response != null) {
            return response;
        }
        final Person person = findPersonOrThrowNotFoundException(personIdType, personId);
        final ReconciliationCriteria reconciliationCriteria = buildReconciliationCriteriaFrom(personRequestRepresentation);
        logger.info("Trying to link incoming SOR person with calculated person...");
        try {
            this.personService.addPersonAndLink(reconciliationCriteria, person);
        }
        catch (IllegalStateException ex) {
            return Response.status(409).entity(ex.getMessage()).build();
        }
        //HTTP 204
        return null;
    }

    @POST
    @Consumes(MediaType.APPLICATION_XML)
    public Response processIncomingPerson(PersonRequestRepresentation personRequestRepresentation, @QueryParam("force") String forceAdd) {
        Response response = validate(personRequestRepresentation);
        if (response != null) {
            return response;
        }
        final ReconciliationCriteria reconciliationCriteria = buildReconciliationCriteriaFrom(personRequestRepresentation);
        logger.info("Trying to add incoming person...");

        if (FORCE_ADD_FLAG.equals(forceAdd)) {
            logger.warn("Multiple people found, but doing a 'force add'");
            try {
                final ServiceExecutionResult<Person> result = this.personService.forceAddPerson(reconciliationCriteria);
                final Person forcefullyAddedPerson = result.getTargetObject();
                final URI uri = buildPersonResourceUri(forcefullyAddedPerson);
                response = Response.created(uri).entity(buildPersonActivationKeyRepresentation(forcefullyAddedPerson)).type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).build();
                logger.info(String.format("Person successfully created (with 'force add' option). The person resource URI is %s", uri.toString()));
            } catch (final IllegalStateException e) {
                response = Response.status(409).entity(e.getMessage()).type(MediaType.TEXT_PLAIN).build();
            }
            return response;
        }

        try {
            final ServiceExecutionResult<Person> result = this.personService.addPerson(reconciliationCriteria);

            if (!result.succeeded()) {
                logger.info("The incoming person payload did not pass validation. Validation errors: " + result.getValidationErrors());
                return Response.status(Response.Status.BAD_REQUEST).entity("The incoming request is malformed.").build();
            }

            final Person person = result.getTargetObject();
            final URI uri = buildPersonResourceUri(person);
            response = Response.created(uri).entity(buildPersonActivationKeyRepresentation(person)).type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).build();
            logger.info(String.format("Person successfully created. The person resource URI is %s", uri.toString()));
        }
        catch (final ReconciliationException ex) {
            switch (ex.getReconciliationType()) {
                case MAYBE:
                    final List<PersonMatch> conflictingPeopleFound = ex.getMatches();
                    response = Response.status(409).entity(buildLinksToConflictingPeopleFound(conflictingPeopleFound)).type(MediaType.APPLICATION_XHTML_XML).build();
                    logger.info("Multiple people found: " + response.getEntity());
                    break;

                case EXACT:
                    final URI uri = buildPersonResourceUri(ex.getMatches().get(0).getPerson());
                    //HTTP 303 ("See other with GET")
                    response = Response.seeOther(uri).build();
                    logger.info(String.format("Person already exists. The existing person resource URI is %s.", uri.toString()));
                    break;
            }
        }
        return response;
    }

    @DELETE
    @Path("{personIdType}/{personId}/roles/{roleCode}")
    public Response deleteRoleForPerson(@PathParam("personIdType") String personIdType,
                                        @PathParam("personId") String personId,
                                        @PathParam("roleCode") String roleCode,
                                        @QueryParam("reason") String terminationReason) {
        logger.info(String.format("Received a request to delete a role for a person with the following params: " +
                "{personIdType:%s, personId:%s, roleCode:%s, reason:%s}", personIdType, personId, roleCode, terminationReason));
        if (terminationReason == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Please specify the <reason> for termination.").build();
        }
        logger.info("Searching for a person...");
        final Person person = this.personService.findPersonByIdentifier(personIdType, personId);
        if (person == null) {
            logger.info("Person is not found...");
            return Response.status(Response.Status.NOT_FOUND).entity("The specified person is not found in the system").build();
        }
        logger.info("Person is found. Picking out the role for a provided 'roleId'...");
        final Role role = person.pickOutRole(roleCode);
        if (role == null) {
            logger.info("The Role with the specified 'roleId' is not found in the collection of Person Roles");
            return Response.status(Response.Status.NOT_FOUND).entity("The specified role is not found for this person").build();
        }
        logger.info("The Role is found");
        if (role.isTerminated()) {
            logger.info("The Role is already terminated.");
            //Results in HTTP 204
            return null;
        }
        try {
            // TODO re-implement this

/*            if (!this.personService.deleteSorRole(person, role, terminationReason)) {
                //HTTP 500. Is this OK?
                logger.info("The call to PersonService.deleteSorRole returned <false>. Assuming it's an internal error.");
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("The operation resulted in an internal error")
                        .build();
            }*/
        }
        catch (final IllegalArgumentException ex) {
            logger.info("The 'terminationReason' did not pass the validation");
            return Response.status(Response.Status.BAD_REQUEST).entity(ex.getMessage()).build();
        }
        //If we got here, everything went well. HTTP 204
        logger.info("The Role resource has been successfully DELETED");
        return null;
    }

    @DELETE
    @Path("sor/{sorSource}/{sorId}")
    public Response deleteSystemOfRecordPerson(@PathParam("sorSource") final String sorSource,
                                               @PathParam("sorId") final String sorId,
                                               @QueryParam("mistake") @DefaultValue("false") final boolean mistake,
                                               @QueryParam("terminationType") @DefaultValue("UNSPECIFIED") final String terminationType) {
        try {
            if (!this.personService.deleteSystemOfRecordPerson(sorSource, sorId, mistake, terminationType)) {
                throw new WebApplicationException(new RuntimeException(String.format("Unable to Delete SorPerson for SoR [ %s ] with ID [ %s ]", sorSource, sorId)), 500);
            }
            //HTTP 204
            logger.debug("The SOR Person resource has been successfully DELETEd");
            return null;
        }
        catch (final PersonNotFoundException e) {
            throw new NotFoundException(String.format("The system of record person resource identified by /people/sor/%s/%s URI does not exist",
                    sorSource, sorId));
        }
    }

    //TODO: what happens if the role (identified by RoleInfo) has been added already?
    private SorRole buildSorRoleFrom(final SorPerson person, final RoleRepresentation roleRepresentation) {
        RoleInfo roleInfo = null;
        try {
            roleInfo = this.referenceRepository.getRoleInfoByCode(roleRepresentation.roleCode);
        } catch (Exception ex){
            throw new NotFoundException(
                    String.format("The role identified by [%s] does not exist", roleRepresentation.roleCode));
        }

        final SorRole sorRole = person.addRole(roleInfo);
        if (roleRepresentation.roleId != null) sorRole.setSorId(roleRepresentation.roleId);
        sorRole.setSourceSorIdentifier(person.getSourceSor());
        sorRole.setPersonStatus(referenceRepository.findType(Type.DataTypes.STATUS, Type.PersonStatusTypes.ACTIVE));
        sorRole.setStart(roleRepresentation.startDate);
        if (roleRepresentation.endDate != null) sorRole.setEnd(roleRepresentation.endDate);
        if (roleRepresentation.percentage != null) sorRole.setPercentage(new Integer(roleRepresentation.percentage).intValue());
        sorRole.setSponsor();
        setSponsorInfo(sorRole.getSponsor(), referenceRepository.findType(Type.DataTypes.SPONSOR, roleRepresentation.sponsorType), roleRepresentation);

        //Emails
        for (final RoleRepresentation.Email e : roleRepresentation.emails) {
            final EmailAddress email = sorRole.addEmailAddress();
            email.setAddress(e.address);
            email.setAddressType(referenceRepository.findType(Type.DataTypes.EMAIL, e.type));
        }

        //Phones
        for (final RoleRepresentation.Phone ph : roleRepresentation.phones) {
            final Phone phone = sorRole.addPhone();
            phone.setNumber(ph.number);
            phone.setAddressType(referenceRepository.findType(Type.DataTypes.ADDRESS, ph.addressType));
            phone.setPhoneType(referenceRepository.findType(Type.DataTypes.PHONE, ph.type));
            phone.setCountryCode(ph.countryCode);
            phone.setAreaCode(ph.areaCode);
            phone.setExtension(ph.extension);
        }

        //Addresses
        for (final RoleRepresentation.Address a : roleRepresentation.addresses) {
            final Address address = sorRole.addAddress();
            address.setType(referenceRepository.findType(Type.DataTypes.ADDRESS, a.type));
            address.setLine1(a.line1);
            address.setLine2(a.line2);
            address.setLine3(a.line3);
            address.setCity(a.city);
            address.setPostalCode(a.postalCode);
            Country country = referenceRepository.getCountryByCode(a.countryCode);
            address.setCountry(country);
            if (country != null) address.setRegion(referenceRepository.getRegionByCodeAndCountryId(a.regionCode, country.getCode()));
        }
        return sorRole;
    }
    
    private void setSponsorInfo(SorSponsor sponsor, Type type, RoleRepresentation roleRepresentation){
        sponsor.setType(type);
        if (type.getDescription().equals(Type.SponsorTypes.ORG_UNIT.name())){
            try {
                OrganizationalUnit org = referenceRepository.getOrganizationalUnitByCode(roleRepresentation.sponsorId);
                sponsor.setSponsorId(org.getId());
            } catch (Exception ex){
                throw new NotFoundException(
                    String.format("The department identified by [%s] does not exist", roleRepresentation.sponsorId));
            }

        }
        if (type.getDescription().equals(Type.SponsorTypes.PERSON.name())){
            final String sponsorIdType = roleRepresentation.sponsorIdType != null ? roleRepresentation.sponsorIdType : this.preferredPersonIdentifierType;
            try {
                Person person = this.personService.findPersonByIdentifier(sponsorIdType, roleRepresentation.sponsorId);
                sponsor.setSponsorId(person.getId());
            } catch (Exception ex){
                throw new NotFoundException(
                    String.format("The sponsor identified by [%s] does not exist", roleRepresentation.sponsorId));
            }
        }
        //TODO other sponsor types?
    }

    private ReconciliationCriteria buildReconciliationCriteriaFrom(final PersonRequestRepresentation request) {
        final ReconciliationCriteria ps = this.reconciliationCriteriaObjectFactory.getObject();
        ps.getSorPerson().setSourceSor(request.systemOfRecordId);
        ps.getSorPerson().setSorId(request.systemOfRecordPersonId);
        final Name name = ps.getSorPerson().addName(referenceRepository.findType(Type.DataTypes.NAME, Type.NameTypes.FORMAL));
        name.setGiven(request.firstName);
        name.setFamily(request.lastName);
        ps.setEmailAddress(request.email);
        ps.setPhoneNumber(request.phoneNumber);
        ps.getSorPerson().setDateOfBirth(request.dateOfBirth);
        ps.getSorPerson().setSsn(request.ssn);
        ps.getSorPerson().setGender(request.gender);
        ps.setAddressLine1(request.addressLine1);
        ps.setAddressLine2(request.addressLine2);
        ps.setCity(request.city);
        ps.setRegion(request.region);
        ps.setPostalCode(request.postalCode);

        for (final PersonRequestRepresentation.Identifier identifier : request.identifiers) {
            final IdentifierType identifierType = this.referenceRepository.findIdentifierType(identifier.identifierType);
            Assert.notNull(identifierType);

            ps.getIdentifiersByType().put(identifierType, identifier.identifierValue);
        }
        return ps;
    }

    private URI buildPersonResourceUri(final Person person) {
        for (final Identifier id : person.getIdentifiers()) {
            if (this.preferredPersonIdentifierType.equals(id.getType().getName())) {
                return this.uriInfo.getAbsolutePathBuilder().path(this.preferredPersonIdentifierType)
                        .path(id.getValue()).build();
            }
        }
        //Person MUST have at least one id of the preferred configured type. Results in HTTP 500
        throw new IllegalStateException("The person must have at least one id of the preferred configured type " +
                "which is <" + this.preferredPersonIdentifierType + ">");
    }

    private LinkRepresentation buildLinksToConflictingPeopleFound(List<PersonMatch> matches) {
        //A little defensive stuff. Will result in HTTP 500
        if (matches.isEmpty()) {
            throw new IllegalStateException("Person matches cannot be empty if reconciliation result is <MAYBE>");
        }
        final List<LinkRepresentation.Link> links = new ArrayList<LinkRepresentation.Link>();
        for (final PersonMatch match : matches) {
            links.add(new LinkRepresentation.Link("person", buildPersonResourceUri(match.getPerson()).toString()));
        }
        return new LinkRepresentation(links);
    }

    private List<PersonResponseRepresentation.PersonIdentifierRepresentation> buildPersonIdentifierRepresentations(final Set<Identifier> identifiers) {

        final List<PersonResponseRepresentation.PersonIdentifierRepresentation> idsRep = new ArrayList<PersonResponseRepresentation.PersonIdentifierRepresentation>();

        for (final Identifier id : identifiers) {
            idsRep.add(new PersonResponseRepresentation.PersonIdentifierRepresentation(id.getType().getName(), id.getValue()));
        }

        if (idsRep.isEmpty()) {
            throw new IllegalStateException("Person identifiers cannot be empty");
        }
        return idsRep;
    }

    //Content-Type: application/x-www-form-urlencoded
    private Form buildPersonActivationKeyRepresentation(final Person person) {
        final Form f = new Form();
        f.putSingle("activationKey", person.getCurrentActivationKey().asString());
        return f;
    }

    private Response validate(PersonRequestRepresentation personRequestRepresentation) {
        if (!personRequestRepresentation.checkRequiredData()) {
            //HTTP 400
            return Response.status(Response.Status.BAD_REQUEST).entity("The person entity payload is incomplete.").build();
        }
        //Returns null response indicating that the representation is valid
        return null;
    }

    private StringBuilder buildValidationErrorsResponse(final Set<ConstraintViolation> validationErrors) {
        final StringBuilder builder = new StringBuilder();

        if (!validationErrors.isEmpty()) {
            return builder;
        }

        builder.append("Validation errors were found:");

        for (final ConstraintViolation violation : validationErrors) {
            builder.append(" ");
            builder.append(violation.getPropertyPath().toString());
            builder.append(" ");
            builder.append(violation.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        }

        return builder;
    }

    private Person findPersonOrThrowNotFoundException(final String personIdType, final String personId) {
        logger.info(String.format("Searching for a person with  {personIdType:%s, personId:%s} ...", personIdType, personId));
        final Person person = this.personService.findPersonByIdentifier(personIdType, personId);
        if (person == null) {
            //HTTP 404
            logger.info("Person is not found.");
            throw new NotFoundException(
                    String.format("The person resource identified by /people/%s/%s URI does not exist",
                            personIdType, personId));
        }
        return person;
    }
    
	/**
	 * @param reconciliationCriteriaObjectFactory the reconciliationCriteriaObjectFactory to set
	 */
	public void setReconciliationCriteriaObjectFactory(final ObjectFactory<ReconciliationCriteria> reconciliationCriteriaObjectFactory) {
		this.reconciliationCriteriaObjectFactory = reconciliationCriteriaObjectFactory;
	}

    public void setPersonService(final PersonService personService) {
        this.personService = personService;
    }

    public void setReferenceRepository(final ReferenceRepository referenceRepository) {
        this.referenceRepository = referenceRepository;
    }

    public void setPreferredPersonIdentifierType(final String preferredPersonIdentifierType) {
        this.preferredPersonIdentifierType = preferredPersonIdentifierType;
    }

    public void setIdentifierChangeService(final IdentifierChangeService identifierChangeService) {
        this.identifierChangeService = identifierChangeService;
    }
}