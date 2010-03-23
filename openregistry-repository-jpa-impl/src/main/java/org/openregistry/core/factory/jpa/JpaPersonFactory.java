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

package org.openregistry.core.factory.jpa;

import org.openregistry.core.domain.jpa.JpaPersonImpl;
import org.openregistry.core.domain.Person;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.BeansException;

import javax.inject.Named;

/**
 * Autowired component that will construct a new JpaPersonImpl to be fed to our other layers.  There should only be one
 * of these configured at a given time.
 *
 * @author Scott Battaglia
 * @version $Revision$ $Date$
 * @since 1.0.0
 */
@Named("personFactory")
public final class JpaPersonFactory implements ObjectFactory<Person> {

    public Person getObject() throws BeansException {
        return new JpaPersonImpl();
    }
}
