package org.openregistry.core.service;

import org.openregistry.core.service.identifier.IdentifierAssigner;
import org.openregistry.core.service.identifier.IdentifierGenerator;
import org.openregistry.core.service.reconciliation.ReconciliationResult;
import org.openregistry.core.service.reconciliation.Reconciler;
import org.openregistry.core.service.reconciliation.PersonMatch;
import org.openregistry.core.service.reconciliation.FieldMatch;
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
import org.openregistry.core.service.reconciliation.PersonMatchImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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

    @Autowired(required = true)
    private PersonRepository personRepository;

    @Autowired(required = true)
    private ReferenceRepository referenceRepository;

    @Autowired(required = false)
    private AnnotationValidator<Object> annotationValidator = new AnnotationValidatorImpl(JvConfiguration.JV_CONFIG_FILE_FIELD);

    @Autowired(required=true)
    private Reconciler reconciler;

    @Autowired(required=true)
    @Qualifier(value = "person")
    private ObjectFactory<Person> personObjectFactory;

    @Autowired(required=false)
    private List<IdentifierAssigner> identifierAssigners = new ArrayList<IdentifierAssigner>();

    @Autowired(required=true)
    private IdentifierGenerator identifierGenerator;

    protected final Logger logger = LoggerFactory.getLogger(getClass());

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
    public SorPerson findSorPersonByIdentifierAndSourceIDentifier(final String identifierType, final String identifierValue, final String sorSourceId) {
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

            final SorRole sorRole = sorPerson.removeRoleByRoleId(role.getId());

            if (sorRole != null) {
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
    public boolean deleteSorRole(SorPerson sorPerson, SorRole sorRole, String terminationReason) throws IllegalArgumentException {
        try {
            final Person person = this.personRepository.findByInternalId(sorPerson.getPersonId());
            if (person == null) {
                return false;
            }

            for (final Role role : person.getRoles()) {
                if (role.getId().equals(sorRole.getRoleId())) {
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
    public ServiceExecutionResult validateAndSaveRoleForSorPerson(final SorPerson sorPerson, final SorRole sorRole) {
        final String serviceName = "PersonService.validateAndSaveRoleForSorPerson";
        final List<ValidationError> validationErrors = validateAndConvert(sorRole);

        if (!validationErrors.isEmpty()) {
            return new ReconciliationServiceExecutionResult(serviceName, sorRole, validationErrors);
        }

        SorPerson savedSorPerson = this.personRepository.saveSorPerson(sorPerson);
        
        Person person = this.personRepository.findByInternalId(savedSorPerson.getPersonId());
        person.addRole(sorRole.getRoleInfo(), sorRole);
        this.personRepository.savePerson(person);
// TODO       sorRole.setRoleId(person.getRoles());
        
        return new GeneralServiceExecutionResult(serviceName, sorRole);
    }

    @Transactional
    public ServiceExecutionResult addPerson(final ReconciliationCriteria reconciliationCriteria, final ReconciliationResult oldReconciliationResult) {
        logger.info("In personService.addPerson.");
        logger.info("In personService.addPerson: entered following for gender: "+ reconciliationCriteria.getPerson().getGender());
        final List<ValidationError> validationErrors = validateAndConvert(reconciliationCriteria);
        final String serviceName = "PersonService.addPerson";

        if (!validationErrors.isEmpty()) {
            return new ReconciliationServiceExecutionResult(serviceName, reconciliationCriteria, validationErrors);
        }


        if (oldReconciliationResult == null) {
            final ReconciliationResult result = this.reconciler.reconcile(reconciliationCriteria);

            if (result.getReconciliationType() == ReconciliationResult.ReconciliationType.NONE) {
                return new ReconciliationServiceExecutionResult(serviceName, magic(reconciliationCriteria), result);
            } else if (result.getReconciliationType() == ReconciliationResult.ReconciliationType.EXACT) {
            	return new ReconciliationServiceExecutionResult(serviceName, magicUpdate(reconciliationCriteria, result), result);
            }
            logger.info("In personService.addPerson: reconciliation result: "+ result.getReconciliationType().toString());
            // ReconciliationResult.ReconciliationType.MAYBE
            return new ReconciliationServiceExecutionResult(serviceName, reconciliationCriteria, result);
        }
        // Here if we were called a second time.  This means even though we found partial matches, this is a new person.
        return new ReconciliationServiceExecutionResult(serviceName, magic(reconciliationCriteria));
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

        for (int i=0; i<personMatches.size(); i++){
            logger.info("PersonMatch: "+ i + personMatches.get(i).getPerson().getPreferredName());
        }

        return personMatches;
    }

    protected void updateCalculatedPersonOnDeleteOfSor(final SorPerson sorPerson) {
        final Person person = this.personRepository.findByInternalId(sorPerson.getPersonId());

        if (person == null) {
            throw new IllegalStateException("No calculated preson for SorPerson");
        }

        final List<SorRole> sorRoles = new ArrayList<SorRole>(sorPerson.getRoles());
        for (final SorRole sorRole : sorRoles) {
            for (final Role role : person.getRoles()) {
                if (sorRole.getRoleId().equals(role.getId())) {
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
        logger.info("In personService.validateAndConvert: size of messages: "+ validationMessages.size());
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
        SorPerson sorPerson = reconciliationCriteria.getPerson();

        if (!StringUtils.hasText(sorPerson.getSorId())) {
            sorPerson.setSorId(this.identifierGenerator.generateNextString());
        }

        // Save Sor Person
        sorPerson = this.personRepository.saveSorPerson(sorPerson);

        // Construct actual person from Sor Information
        Person person = personObjectFactory.getObject();
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

        // Save into the repository
        person = this.personRepository.savePerson(person);
        
        // Now connect the SorPerson to the actual person
        sorPerson.setPersonId(person.getId());
        
        return person;
    }
    
    protected Person magicUpdate(final ReconciliationCriteria reconciliationCriteria, final ReconciliationResult result) {
    	if (result.getMatches().size() != 1) {
        	throw new IllegalStateException("ReconciliationResult should be 'EXACT' and there should only be one person.  The result is '" + result.getReconciliationType() + "' and the number of people is " + result.getMatches().size() + ".");
        }
        Person person = result.getMatches().iterator().next().getPerson();
        
    	SorPerson sorPerson = reconciliationCriteria.getPerson();

        String sorIdentifier = sorPerson.getSourceSorIdentifier();
        SorPerson registrySorPerson = null;
        try {
            registrySorPerson = personRepository.findByPersonIdAndSorIdentifier(person.getId(),sorIdentifier);
        } catch (Exception ex) {
        }
        if (registrySorPerson != null) {
            //TODO update the registry sor person record with the changes from sorPerson
            sorPerson = this.personRepository.saveSorPerson(registrySorPerson);
        } else {
            if (!StringUtils.hasText(sorPerson.getSorId())) {
                sorPerson.setSorId(this.identifierGenerator.generateNextString());
            }
            // Now connect the SorPerson to the actual person
            sorPerson.setPersonId(person.getId());
            // Save Sor Person
            sorPerson = this.personRepository.saveSorPerson(sorPerson);
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
    public ServiceExecutionResult updateSorPerson(SorPerson sorPerson) {
        final String serviceName = "PersonService.updateSorPerson";
        logger.info("PersonService:updateSorPerson:");

        final List<ValidationError> validationErrors = validateAndConvert(sorPerson);

        if (!validationErrors.isEmpty()) {
            logger.info("PersonService:updateSorPerson: validation errors found");
            return new GeneralServiceExecutionResult(serviceName, sorPerson, validationErrors);
        }

        // Save Sor Person
        logger.info("PersonService:updateSorPerson: updating person...");
        sorPerson = this.personRepository.saveSorPerson(sorPerson);

        return new GeneralServiceExecutionResult(serviceName, sorPerson);

        // TODO Need to update the calculated person. Need to establish rules to do this. OR-59
    }

    /**
     * Persists an SorRole on update.
     *
     * @param role to update.
     * @return serviceExecutionResult.
     */
    @Transactional
    public ServiceExecutionResult updateSorRole(SorRole role) {
        final String serviceName = "PersonService.updateSorRole";
        logger.info("PersonService:updateSorRole:");

        final List<ValidationError> validationErrors = validateAndConvert(role);
        
        if (!validationErrors.isEmpty()) {
            logger.info("PersonService:updateSorRole: validation errors found");
            return new GeneralServiceExecutionResult(serviceName, role, validationErrors);
        }

        logger.info("PersonService:updateSorPerson: updating role...");
        // Save Sor Person
        role = this.personRepository.saveSorRole(role);

        return new GeneralServiceExecutionResult(serviceName, role);

        // TODO Need to update the calculated role. Need to establish rules to do this. OR-58
    }


    @Transactional
    public boolean removeSorName(SorPerson sorPerson, Long nameId){
        Name name = sorPerson.findNameByNameId(nameId);
        if (name == null) return false;

        //personRepository.deleteName(name);
        sorPerson.removeName(name);

        sorPerson = this.personRepository.saveSorPerson(sorPerson);
        return true;
    }

    /**
     * Move All Sor Records from one person to another.
     *
     * @param fromPerson person losing sor records.
     * @param toPerson person receiving sor records.
     * @return Result of move. Validation errors if they occurred or the Person receiving sor records.
     */
    public boolean moveSorRecords(final Person fromPerson, final Person toPerson){
        //get the list of sor person records that will be moving.
        List<SorPerson> sorPersonList =  personRepository.getSoRRecordsForPerson(fromPerson);
        return true;
    }

    /**
     * Move one Sor Record from one person to another.
     *
     * @param fromPerson person losing sor record.
     * @param toPerson person receiving sor record.
     * @return Result of move. Validation errors if they occurred or the to Person receiving sor records.
     */
    public boolean moveSorRecord(final Person fromPerson, final Person toPerson, final SorPerson sorPerson){
        String sourceSorId = sorPerson.getSourceSorIdentifier();

        //search through sor records of toPerson to see if they have sorPerson records for the same source sor id.
        List<SorPerson> sorPersonList =  personRepository.getSoRRecordsForPerson(toPerson);
        
        //if toPerson has an sor record matching the source of the sor record being moved then:
        //move the roles
        //move the names
        //delete the moving sorperson record

        //if no matching sor record is found, then move the sorPerson record to point to the toPerson.
        sorPerson.setPersonId(toPerson.getId());
        return true;
    }

}
