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
package org.openregistry.core.service;

import org.openregistry.core.service.identifier.IdentifierAssigner;
import org.openregistry.core.service.identifier.IdentifierGenerator;
import org.openregistry.core.service.identifier.NoOpIdentifierGenerator;
import org.openregistry.core.domain.Person;
import org.openregistry.core.domain.Role;
import org.openregistry.core.domain.Name;
import org.openregistry.core.domain.Type;
import org.openregistry.core.domain.sor.SorPerson;
import org.openregistry.core.domain.sor.ReconciliationCriteria;
import org.openregistry.core.domain.sor.SorRole;
import org.openregistry.core.repository.PersonRepository;
import org.openregistry.core.repository.ReferenceRepository;
import org.openregistry.core.repository.RepositoryAccessException;
import org.openregistry.core.service.reconciliation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.util.Assert;
import org.javalid.core.AnnotationValidator;
import org.javalid.core.ValidationMessage;
import org.javalid.core.AnnotationValidatorImpl;
import org.javalid.core.config.JvConfiguration;
import org.javalid.annotations.core.JvGroup;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Default implementation of the {@link PersonService}.
 *
 * @author Scott Battaglia
 * @author Dmitriy Kopylenko
 * @since 1.0.0
 */
@Service("personService")
public class DefaultPersonService implements PersonService {

    private final PersonRepository personRepository;

    private final ReferenceRepository referenceRepository;


    private final Reconciler reconciler;

    private final ObjectFactory<Person> personObjectFactory;

    private final IdentifierGenerator identifierGenerator;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    @Autowired(required=false)
    private List<IdentifierAssigner> identifierAssigners = new ArrayList<IdentifierAssigner>();

    @Autowired(required = false)
    private AnnotationValidator<Object> annotationValidator = new AnnotationValidatorImpl(JvConfiguration.JV_CONFIG_FILE_FIELD);

    @Autowired
    public DefaultPersonService(final PersonRepository personRepository, final ReferenceRepository referenceRepository, final IdentifierGenerator identifierGenerator, @Qualifier("person") final ObjectFactory<Person> personObjectFactory, final Reconciler reconciler) {
        this.personRepository = personRepository;
        this.referenceRepository = referenceRepository;
        this.identifierGenerator = identifierGenerator;
        this.personObjectFactory = personObjectFactory;
        this.reconciler = reconciler;
    }

    @Transactional
    public Person findPersonById(final Long id) {
        return this.personRepository.findByInternalId(id);
    }
    
    @Transactional
    public SorPerson findSorPersonById(final Long id) {
        return this.personRepository.findSorByInternalId(id);
    }
    
    @Transactional
    public Person findPersonByIdentifier(final String identifierType, final String identifierValue) {
        try {
            return this.personRepository.findByIdentifier(identifierType,identifierValue);
        } catch (final Exception e) {
            return null;
        }
    }

    @Transactional
    public SorPerson findSorPersonByIdentifierAndSourceIdentifier(final String identifierType, final String identifierValue, final String sorSourceId) {
        try {
            final Person person = this.personRepository.findByIdentifier(identifierType, identifierValue);

            if (person == null) {
                return null;
            }

            return this.personRepository.findByPersonIdAndSorIdentifier(person.getId(), sorSourceId);
        } catch (final Exception e) {
            return null;
        }
    }

    @Transactional
    public SorPerson findByPersonIdAndSorIdentifier(final Long personId, final String sorSourceIdentifier) {
        try {
          return this.personRepository.findByPersonIdAndSorIdentifier(personId, sorSourceIdentifier);
        } catch (Exception e){
          return null;
        }
    }

    @Transactional
    public boolean deletePerson(final Person person) {
        try {
            final Number number = this.personRepository.getCountOfSoRRecordsForPerson(person);

            if (number.intValue() == 0) {
                this.personRepository.deletePerson(person);
                return true;
            }
            
            return false;
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean deleteSystemOfRecordPerson(final SorPerson sorPerson) {
        try {
            updateCalculatedPersonOnDeleteOfSor(sorPerson);
            this.personRepository.deleteSorPerson(sorPerson);
            return true;
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean deleteSystemOfRecordPerson(final String sorSourceIdentifier, final String sorId) {
        try {
          final SorPerson sorPerson = this.personRepository.findBySorIdentifierAndSource(sorSourceIdentifier, sorId);

            if (sorPerson == null) {
                return false;
            }

            updateCalculatedPersonOnDeleteOfSor(sorPerson);
            this.personRepository.deleteSorPerson(sorPerson);
            return true;
        } catch (final Exception e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean deleteSorRole(final Person person, final Role role, final String terminationReason) {
        try {
            final SorPerson sorPerson = this.personRepository.findSorPersonByPersonIdAndSorRoleId(person.getId(), role.getId());
            if (sorPerson == null) {
                return false;
            }

            final SorRole sorRole = this.personRepository.findSorRoleByInternalId(role.getSorRoleId());
            
            if (sorRole != null) {
                sorPerson.removeRole(sorRole);
                removeSorRoleFromDatabase(sorPerson, sorRole, person, role, terminationReason);
                return true;
            }
            return false;
        } catch (final RepositoryAccessException e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }

    @Transactional
    public boolean deleteSorRole(final SorPerson sorPerson, final SorRole sorRole, final String terminationReason) throws IllegalArgumentException {
        try {
            final Person person = this.personRepository.findByInternalId(sorPerson.getPersonId());
            if (person == null) {
                return false;
            }

            for (final Role role : person.getRoles()) {
                if (role.getSorRoleId().equals(sorRole.getId())) {
                    removeSorRoleFromDatabase(sorPerson, sorRole, person, role, terminationReason);
                    return true;
                }
            }

            return false;
        } catch (final RepositoryAccessException e) {
            return false;
        }
    }

    @Transactional
    public ServiceExecutionResult<SorRole> validateAndSaveRoleForSorPerson(final SorPerson sorPerson, final SorRole sorRole) {
        final String serviceName = "PersonService.validateAndSaveRoleForSorPerson";
        final List<ValidationError> validationErrors = validateAndConvert(sorRole);

        if (!validationErrors.isEmpty()) {
            return new GeneralServiceExecutionResult<SorRole>(serviceName, sorRole, validationErrors);
        }

        logger.info("validateAndSaveRoleForSorPerson: sorPerson.getPersonId(): "+ sorPerson.getPersonId() + "role info code: " +sorRole.getRoleInfo().getCode()) ;
        //find the calculated person and create and add a calculated role
        Person person = this.personRepository.findByInternalId(sorPerson.getPersonId());

        SorPerson savedSorPerson = this.personRepository.saveSorPerson(sorPerson);
        SorRole savedSorRole = savedSorPerson.pickOutRole(sorRole.getRoleInfo().getCode());

        logger.info("validateAndSaveRoleForSorPerson: sorrole found: "+ savedSorRole.getId()) ;

        person.addRole(savedSorRole.getRoleInfo(), savedSorRole);

        Person savedPerson = this.personRepository.savePerson(person);

        return new GeneralServiceExecutionResult<SorRole>(serviceName, sorRole);
    }

    @Transactional
    public ServiceExecutionResult<Person> addPerson(final ReconciliationCriteria reconciliationCriteria) throws ReconciliationException, IllegalArgumentException {
        Assert.notNull(reconciliationCriteria,"reconciliationCriteria cannot be null");
        final List<ValidationError> validationErrors = validateAndConvert(reconciliationCriteria);
        final String serviceName = "PersonService.addPerson";

        if (!validationErrors.isEmpty()) {
            return new GeneralServiceExecutionResult<Person>(serviceName, validationErrors);
        }

        final ReconciliationResult result = this.reconciler.reconcile(reconciliationCriteria);

        if (result.getReconciliationType() == ReconciliationResult.ReconciliationType.NONE) {
            return new GeneralServiceExecutionResult<Person>(serviceName, magic(reconciliationCriteria));
        } else if (result.getReconciliationType() == ReconciliationResult.ReconciliationType.EXACT) {
            // TODO this method should not be doing this update
            return new GeneralServiceExecutionResult<Person>(serviceName, magicUpdate(reconciliationCriteria, result));
        }

        throw new ReconciliationException(result);
    }

    @Transactional
    public ServiceExecutionResult<Person> forceAddPerson(ReconciliationCriteria reconciliationCriteria, ReconciliationResult reconciliationResult) throws IllegalArgumentException {
        Assert.notNull(reconciliationCriteria, "reconciliationCriteria cannot be null.");
        Assert.notNull(reconciliationResult, "reconciliationResult cannot be null.");
        final String serviceName = "PersonService.addPerson";
        return new GeneralServiceExecutionResult<Person>(serviceName, magic(reconciliationCriteria));
    }

    @Transactional
    public List<PersonMatch> searchForPersonBy(final SearchCriteria searchCriteria) {
        final List<PersonMatch> personMatches = new ArrayList<PersonMatch>();

        if (StringUtils.hasText(searchCriteria.getIdentifierValue())) {
            try {
                final Person person = this.personRepository.findByIdentifier(searchCriteria.getIdentifierType(), searchCriteria.getIdentifierValue());

                if (person != null) {
                    final PersonMatch p = new PersonMatchImpl(person, 100, new ArrayList<FieldMatch>());
                    personMatches.add(p);
                    return personMatches;
                }
            } catch (final Exception e) {

            }
        }

        final List<Person> persons = this.personRepository.searchByCriteria(searchCriteria);

        for (final Person person : persons) {
            // TODO actually calculate the value for the match
            final PersonMatch p = new PersonMatchImpl(person, 50, new ArrayList<FieldMatch>());
            personMatches.add(p);
        }

        return personMatches;
    }

    protected void updateCalculatedPersonOnDeleteOfSor(final SorPerson sorPerson) {
        final Person person = this.personRepository.findByInternalId(sorPerson.getPersonId());
        Assert.notNull(person, "Calculated Person expected for System of Record Person.  Expected Id: [" + sorPerson.getPersonId() + "]");

        final List<SorRole> sorRoles = new ArrayList<SorRole>(sorPerson.getRoles());
        for (final SorRole sorRole : sorRoles) {
            for (final Role role : person.getRoles()) {
                if (sorRole.getId().equals(role.getSorRoleId())) {
                    removeSorRoleFromDatabase(sorPerson, sorRole, person, role, "Fired");
                }
            }
        }
        // TODO what do we need to recalculate, if anything?
    }


    /**
     * Removes the SoR record from the database as well as updates the calculated role.
     *
     * @param sorPerson the system of record person
     * @param sorRole the system of record role
     * @param person the calculated person
     * @param role the calculated role
     * @param terminationReason the reason for termination
     */
    protected void removeSorRoleFromDatabase(final SorPerson sorPerson, final SorRole sorRole, final Person person, final Role role, final String terminationReason) {
        sorPerson.getRoles().remove(sorRole);
        this.personRepository.deleteSorRole(sorPerson, sorRole);
        try {
            final Type terminationType = this.referenceRepository.findType(Type.DataTypes.TERMINATION, terminationReason);

            role.setEnd(new Date());
            role.setTerminationReason(terminationType);
            this.personRepository.updateRole(person, role);
        } catch (final Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Validates the object using the JaValid annotation framework and then converts the errors into the OpenRegistry API for errors.
     *
     * @param object the object to check
     * @return the list of validation errors.  CANNOT be NULL.  Can be empty.
     */
    protected List<ValidationError> validateAndConvert(final Object object) {
        final List<ValidationMessage> validationMessages = this.annotationValidator.validateObject(object, JvGroup.DEFAULT_GROUP, "", true, 5);
        return convertToValidationErrors(validationMessages);
    }

    /**
     * Converts the validation errors from JaValid into the OpenRegistry API validation errors.
     *
     * @param messages the JaValid messages to convert.
     * @return the list of validation errors.  CANNOT be null.  CAN be empty.
     */
    protected List<ValidationError> convertToValidationErrors(final List<ValidationMessage> messages) {
        final List<ValidationError> validationErrors = new ArrayList<ValidationError>();

        for (final ValidationMessage message : messages) {
            validationErrors.add(new JaValidValidationError(message));
        }

        return validationErrors;
    }

    /**
     * Current workflow for converting an SorPerson into the actual Person.
     *
     * @param reconciliationCriteria the original search criteria.
     * @return the newly saved Person.
     */
    protected Person magic(final ReconciliationCriteria reconciliationCriteria) {
        if (!StringUtils.hasText(reconciliationCriteria.getPerson().getSorId())) {
            reconciliationCriteria.getPerson().setSorId(this.identifierGenerator.generateNextString());
        }

        // Save Sor Person
        final SorPerson sorPerson = this.personRepository.saveSorPerson(reconciliationCriteria.getPerson());

        // Construct actual person from Sor Information
        final Person person = constructPersonFromSorData(sorPerson);

        // Now connect the SorPerson to the actual person
        sorPerson.setPersonId(person.getId());
        
        return person;
    }

    protected Person constructPersonFromSorData(SorPerson sorPerson){
        // Construct actual person from Sor Information
        final Person person = personObjectFactory.getObject();
        person.setDateOfBirth(sorPerson.getDateOfBirth());
        person.setGender(sorPerson.getGender());

        //initialize the name to be both official and preferred.
        final Name name = person.addOfficialName();
        person.setPreferredName(name);

        // There should only be one at this point.
        // TODO generalize this to all names
        final Name sorName = sorPerson.getNames().iterator().next();
        name.setFamily(sorName.getFamily());
        name.setGiven(sorName.getGiven());
        name.setMiddle(sorName.getMiddle());
        name.setPrefix(sorName.getPrefix());
        name.setSuffix(sorName.getSuffix());

        // Assign identifiers, including SSN from the SoR Person
        for (final IdentifierAssigner ia : this.identifierAssigners) {
            ia.addIdentifierTo(sorPerson, person);
        }

        return this.personRepository.savePerson(person);
    }
    
    protected Person magicUpdate(final ReconciliationCriteria reconciliationCriteria, final ReconciliationResult result) {
        Assert.isTrue(result.getMatches().size() == 1, "ReconciliationResult should be 'EXACT' and there should only be one person.  The result is '" + result.getReconciliationType() + "' and the number of people is " + result.getMatches().size() + ".");

        final Person person = result.getMatches().iterator().next().getPerson();
        final SorPerson sorPerson = reconciliationCriteria.getPerson();

        final SorPerson registrySorPerson = this.findByPersonIdAndSorIdentifier(person.getId(), sorPerson.getSourceSor());

        if (registrySorPerson != null) {
            //TODO update the registry sor person record with the changes from sorPerson
            this.personRepository.saveSorPerson(registrySorPerson);
        } else {
            if (!StringUtils.hasText(sorPerson.getSorId())) {
                sorPerson.setSorId(this.identifierGenerator.generateNextString());
            }
            // Now connect the SorPerson to the actual person
            sorPerson.setPersonId(person.getId());
            // Save Sor Person
            this.personRepository.saveSorPerson(sorPerson);
        }
        
		return person;
	}

    /**
     * Persists an SorPerson on update.
     *
     * @param sorPerson the person to update.
     * @return serviceExecutionResult.
     */
    @Transactional
    public ServiceExecutionResult<SorPerson> updateSorPerson(SorPerson sorPerson) {
        final String serviceName = "PersonService.updateSorPerson";

        final List<ValidationError> validationErrors = validateAndConvert(sorPerson);

        if (!validationErrors.isEmpty()) {
            return new GeneralServiceExecutionResult<SorPerson>(serviceName, sorPerson, validationErrors);
        }

        // Save Sor Person
        logger.info("PersonService:updateSorPerson: updating person...");
        sorPerson = this.personRepository.saveSorPerson(sorPerson);

        return new GeneralServiceExecutionResult<SorPerson>(serviceName, sorPerson);

        // TODO Need to update the calculated person. Need to establish rules to do this. OR-59
    }

    /**
     * Persists an SorRole on update.
     *
     * @param role to update.
     * @return serviceExecutionResult.
     */
    @Transactional
    public ServiceExecutionResult<SorRole> updateSorRole(SorRole role) {
        final String serviceName = "PersonService.updateSorRole";
        logger.info("PersonService:updateSorRole:");

        final List<ValidationError> validationErrors = validateAndConvert(role);
        
        if (!validationErrors.isEmpty()) {
            return new GeneralServiceExecutionResult<SorRole>(serviceName, role, validationErrors);
        }

        logger.info("PersonService:updateSorPerson: updating role...");
        // Save Sor Person
        role = this.personRepository.saveSorRole(role);

        return new GeneralServiceExecutionResult<SorRole>(serviceName, role);

        // TODO Need to update the calculated role. Need to establish rules to do this. OR-58
    }

    @Transactional
    public boolean removeSorName(SorPerson sorPerson, Long nameId){
        Name name = sorPerson.findNameByNameId(nameId);
        if (name == null) return false;

        // remove name from the set (annotation in set to delete orphans)
        sorPerson.removeName(name);

        // save changes
        this.personRepository.saveSorPerson(sorPerson);
        return true;
    }

    /**
     * Move All Sor Records from one person to another.
     *
     * @param fromPerson person losing sor records.
     * @param toPerson person receiving sor records.
     * @return Result of move. Validation errors if they occurred or the Person receiving sor records.
     */
    @Transactional
    public boolean moveAllSystemOfRecordPerson(Person fromPerson, Person toPerson){
        // get the list of sor person records that will be moving.
        List<SorPerson> sorPersonList =  personRepository.getSoRRecordsForPerson(fromPerson);

        // move each sorRecord
        for (final SorPerson sorPerson : sorPersonList) {
            moveSystemOfRecordPerson(fromPerson, toPerson, sorPerson);
        }

        // TODO Delete from person - need to determine how to deal with names before this can work.
        //this.personRepository.deletePerson(fromPerson);
        logger.info("moveAllSystemOfRecordPerson: Deleted From Person");
        return true;
    }

    /**
     * Move one Sor Record from one person to another.
     *
     * @param fromPerson person losing sor record.
     * @param toPerson person receiving sor record.
     * @return Success or failure.
     */
    @Transactional
    public boolean moveSystemOfRecordPerson(Person fromPerson, Person toPerson, SorPerson movingSorPerson){
        logger.info("moveSystemOfRecordPerson: fromPerson: " + fromPerson.getId() + " toPerson: "+ toPerson.getId());

        movingSorPerson.setPersonId(toPerson.getId());
        updateCalculatedPersonsOnMoveOfSor(toPerson, fromPerson, movingSorPerson);
        this.personRepository.saveSorPerson(movingSorPerson);
        return true;
    }

    /**
     * Move one Sor Record from one person to another where the to person is not in the registry
     *
     * @param fromPerson person losing sor record.
     * @param movingSorPerson record that is moving.
     * @return Success or failure.
     */
    @Transactional
    public boolean moveSystemOfRecordPersonToNewPerson(Person fromPerson, SorPerson movingSorPerson){
        logger.info("MoveToNew Person: creating new person record");
        // create the new person in the registry
        Person toPerson = constructPersonFromSorData(movingSorPerson);
        return moveSystemOfRecordPerson(fromPerson, toPerson, movingSorPerson);
    }

    /**
     * Update the calcuated person data. This method and updateCalculatedPersonOnDeleteOfSor
     * need to be generalized to handle recalcuations.
     *
     * @param toPerson
     * @param fromPerson
     * @param sorPerson
     *
     * Adjust calculated roles...
     * Point prc_role to prc_person receiving role
     * Add the role to the set of roles in receiving prc_person
     * Remove role from prc person losing role
     */
    protected void updateCalculatedPersonsOnMoveOfSor(final Person toPerson, final Person fromPerson, final SorPerson sorPerson) {
        Assert.notNull(toPerson, "toPerson cannot be null");
        Assert.notNull(fromPerson, "fromPerson cannot be null");
        logger.info("UpdateCalculated: recalculating person data for move.");

        final List<Role> rolesToDelete = new ArrayList<Role>();

        final List<SorRole> sorRoles = new ArrayList<SorRole>(sorPerson.getRoles());
        for (final SorRole sorRole : sorRoles) {
            for (final Role role : fromPerson.getRoles()) {
                if (sorRole.getId().equals(role.getSorRoleId())) {
                    toPerson.addRole(sorRole.getRoleInfo(),sorRole);
                    rolesToDelete.add(role);
                }
            }
        }
        for (final Role role : rolesToDelete){
            fromPerson.removeRole(role);
        }

        // TODO recalculate names for person receiving role? Anything else?
        // TODO recalculate names for person losing role? Anything else?
        this.personRepository.savePerson(fromPerson);
        this.personRepository.savePerson(toPerson);
    }

    public SorPerson hasSorRecordFromSameSource(Person fromPerson, Person toPerson){
        SorPerson sorPerson = null;

        //TODO need to complete this.
        return sorPerson;
    }

}