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

package org.jasig.openregistry.test.domain;

import org.openregistry.core.domain.*;
import org.openregistry.core.domain.sor.SorName;
import org.openregistry.core.domain.sor.SorRole;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @version $Revision$ $Date$
 * @since 0.1
 */
public class MockPerson implements Person {

    private ActivationKey activationKey = new MockActivationKey(UUID.randomUUID().toString(), new Date(), new Date());

    private final String identifierType = "NETID";

    private final String identifierValue;

	private List<Role> roles = new ArrayList<Role>();

    private long id = -1L;

    private Set<MockName> names = new HashSet<MockName>();

    private MockEmailAddress mockEmailAddress = new MockEmailAddress();

    private MockPhoneNumber mockPhoneNumber = new MockPhoneNumber();

    public MockPerson() {
        this("testId", false, false);
    }

    public MockPerson(final String identifierValue, final boolean notActive, final boolean expired) {
        this.identifierValue = identifierValue;

        if (notActive && expired) {
            throw new IllegalArgumentException("You're crazy!");
        }

        final Date startDate;
        final Date endDate;

        if (notActive) {
            startDate = new Date(System.currentTimeMillis() + 50000);
            endDate = new Date(System.currentTimeMillis() + 50000000);
        } else if (expired) {
            startDate = new Date(System.currentTimeMillis() - 500000);
            endDate = new Date(System.currentTimeMillis() - 50000);
        } else {
            startDate = new Date();
            endDate = new Date(System.currentTimeMillis() + 50000000);
        }

        this.activationKey = new MockActivationKey("key", startDate, endDate);
    }

    public Long getId() {
        return this.id;
    }

    public void setId(final Long id) {
        this.id = id;
    }

    @Override
    public ContactEmailAddress getPreferredContactEmailAddress() {
        return this.mockEmailAddress;
    }

    @Override
    public ContactPhone getPreferredContactPhoneNumber() {
        return this.mockPhoneNumber;
    }

    public void addRole(final Role role) {
        this.roles.add(role);
    }

    public Set<? extends Name> getNames() {
        return this.names;
    }

    public Name addName() {
        final MockName name = new MockName();
        this.names.add(name);
        return name;
    }

    public Name addName(Type type) {
        final MockName name = new MockName(type);
        this.names.add(name);
        return name;
    }

    @Override
    public void addName(final SorName sorName) {
        final MockName name = new MockName();
        name.setType(sorName.getType());
        name.setGiven(sorName.getGiven());
        name.setFamily(sorName.getFamily());
        name.setMiddle(sorName.getMiddle());
        name.setPrefix(sorName.getPrefix());
        name.setSuffix(sorName.getSuffix());
        name.setSourceNameId(sorName.getId());
        this.names.add(name);

        //To change body of implemented methods use File | Settings | File Templates.
    }

    public List<Role> getRoles() {
        return this.roles;
    }

    public Role addRole(final SorRole sorRole) {
        final MockRole mockRole = new MockRole(sorRole);
        this.roles.add(mockRole);
        return mockRole;
    }

    public Set<Identifier> getIdentifiers() {
        final Set<Identifier> identifiers = new HashSet<Identifier>();

        final Identifier id = new Identifier() {

            private final Date creationDate = new Date();
            public IdentifierType getType() {
                return new IdentifierType() {
                    public Long getId() {
                        return 1L;
                    }

                    @Override
                    public String getDescription() {
                        return "description";
                    }

                    @Override
                    public String getFormatAsString() {
                        return "\\d+";
                    }

                    public Pattern getFormatAsPattern() {
                        return null;
                    }

                    @Override
                    public boolean isPrivate() {
                        return false;
                    }

                    @Override
                    public boolean isModifiable() {
                        return false;
                    }

                    @Override
                    public boolean isDeleted() {
                        return false;
                    }

                    public String getName() {
                        return identifierType;
                    }

                    public boolean equals(final Object o) {
                        if (o == null) {
                            return false;
                        }

                        if (!(o instanceof IdentifierType)) {
                            return false;
                        }

                        final IdentifierType idType = (IdentifierType) o;
                        return getName().equals(idType.getName());
                    }
                 };
             }

            @Override
            public Date getCreationDate() {
                return this.creationDate;
            }

            @Override
            public Date getDeletedDate() {
                return null;
            }

            public String getValue() {
                return identifierValue;
            }

            public boolean isPrimary() {
                return true;
            }

            public boolean isDeleted() {
                return false;
            }

            public void setPrimary(boolean value) {

            }

            public void setDeleted(final boolean value) {

            }

            public boolean equals(final Object o) {
                if (o == null) {
                    return false;
                }

                if (!(o instanceof Identifier)) {
                    return false;
                }

                final Identifier id = (Identifier) o;

                return (identifierValue.equals(id.getValue()) && getType().equals(id.getType()));
            }
        };

        identifiers.add(id);
        return identifiers;
    }

    public Identifier addIdentifier(IdentifierType identifierType, String value) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Name getPreferredName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Name getOfficialName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getGender() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Date getDateOfBirth() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setDateOfBirth(Date dateOfBirth) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public void setGender(String gender) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Identifier addIdentifier() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Name addOfficialName() {
        MockName name = new MockName();
        name.setOfficialName(true);
        return name;
    }

    public Name addPreferredName() {
        MockName name = new MockName();
        name.setPreferredName(true);
        return name;
    }

    public void setPreferredName(Name name) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Role pickOutRole(String code) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public Map<String, Identifier> getPrimaryIdentifiersByType() {
        final Map<String,Identifier> primaryIdentifiers = new HashMap<String,Identifier>();

        for (final Identifier identifier : getIdentifiers()) {
            if (identifier.isPrimary()) {
                primaryIdentifiers.put(identifier.getType().getName(), identifier);
            }
        }

        return primaryIdentifiers;
    }

    @Override
    public Map<String, Deque<Identifier>> getIdentifiersByType() {
        return new HashMap<String, Deque<Identifier>>();
    }

    public ActivationKey generateNewActivationKey(Date start, Date end) {
        this.activationKey = new MockActivationKey(UUID.randomUUID().toString(), start, end);
        return this.activationKey;
    }

    public ActivationKey generateNewActivationKey(Date end) {
        this.activationKey = new MockActivationKey(UUID.randomUUID().toString(), new Date(), end);
        return this.activationKey;
    }

    public ActivationKey getCurrentActivationKey() {
        return this.activationKey;
    }

    public void removeCurrentActivationKey() {
        this.activationKey = null;
    }

    public Role findRoleBySoRRoleId(final Long sorRoleId) {
        for (final Role role : this.roles) {
            if (sorRoleId.equals(role.getSorRoleId())) {
                return role;
            }
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        final MockPerson that = (MockPerson) o;

        return id == that.id;
    }

    @Override
    public int hashCode() {
        return 31 * (int) (id ^ (id >>> 32));
    }

    @Override
    public String toString() {
        return "MockPerson{" +
                "id=" + id +
                ", identifierValue='" + identifierValue + '\'' +
                '}';
    }
}
