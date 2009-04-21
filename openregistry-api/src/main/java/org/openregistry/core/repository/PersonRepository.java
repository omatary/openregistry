package org.openregistry.core.repository;

import java.util.List;

import org.openregistry.core.domain.Person;
import org.openregistry.core.domain.Role;
import org.openregistry.core.domain.sor.SorPerson;
import org.openregistry.core.domain.sor.SorRole;
import org.openregistry.core.service.SearchCriteria;

/**
 * Repository abstraction to deal with persistence concerns for <code>Person</code> entities
 * and their related entities
 *
 * @since 1.0
 */
public interface PersonRepository {

    /**
     * Find canonical <code>Person</code> entity in the Open Registry.
     *
     * @param id a primary key internal identifier for a person in Open Registry person repository
     * @return person found in the Open Registry's person repository or null if no person
     *         exist in this repository for a given internal id.
     * @throws RepositoryAccessException if the operation does not succeed for any number of
     *                                   technical reasons.
     */
    Person findByInternalId(Long id) throws RepositoryAccessException;

    /**
     * Finds the <code>Person</code> based on the identifier type and value.
     * @param identifierType the identifier type
     * @param identifierValue the identifier value.
     * @return the person.  CANNOT be null.
     * @throws RepositoryAccessException if the operation does not succeed for any number of
     *                                   technical reasons.
     */
    Person findByIdentifier(String identifierType, String identifierValue) throws RepositoryAccessException;

    /**
     * Searches for a person based on some or all of the supplied criteria.
     *
     * @param searchCriteria the search criteria.
     * @return a list of people that match.
     * 
     * @throws RepositoryAccessException if the operation does not succeed for any number of
     *                                   technical reasons.
     */
    List<Person> searchByCriteria(SearchCriteria searchCriteria) throws RepositoryAccessException;

    /**
     * Find a list of <code>Person</code> entities from the supplied Family Name.
     * 
     * @param family the Family Name to search for
     * @return List of people find in the Open Registry's person repository or an empty list if 
     *         no people exist with this Family Name.
     * @throws RepositoryAccessException
     */
    List<Person> findByFamilyName(String family) throws RepositoryAccessException;
    
    /**
     * Persist or update an instance of a canonical <code>Person</code> entity in the Open Registry.
     *
     * @param person a person to persist or update in the person repository.
     * @return person which has been saved in the repository.
     * @throws RepositoryAccessException if the operation does not succeed for any number of
     *                                   technical reasons.
     */
    Person savePerson(Person person) throws RepositoryAccessException;

    /**
     * Saves the System of Record person.
     *
     * @param person the person to save.
     * @return the person which was saved in the repository.
     * @throws RepositoryAccessException if the operation does not succeed for any number of
     *                                   technical reasons.
     */
    SorPerson saveSorPerson(SorPerson person) throws RepositoryAccessException;

    void addPerson(Person person) throws RepositoryAccessException;

    /**
     * Removes the SoR Role from the database.  This method ASSUMES your code is handling the removal from the person's
     * role from the person object BEFORE calling deleteSorRole.
     *
     * @param person the person who the role is being deleted from.
     * @param role the role that is being removed.
     */
    void deleteSorRole(SorPerson person, SorRole role);

    /**
     * Updates the role in the database.
     *
     * @param person the person who's role is being updated.
     * @param role the role that is being updated.
     */
    void updateRole(Person person, Role role);

    /**
     * Locates a System of Record for a Person based on their internal personId and the internal system of record
     * role identifier.
     *
     * @param personId the internal identifier for the person.
     * @param sorRoleId the internal SoR Role identifier for the person, as assigned by OpenRegistry.
     *
     * @return the person if they exist.  In theory if you have both ids this should never return null.
     */
    SorPerson findSorPersonByPersonIdAndSorRoleId(Long personId, Long sorRoleId);
}