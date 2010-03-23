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

package org.openregistry.core.domain.jpa;

import java.util.Date;
import javax.persistence.metamodel.ListAttribute;
import javax.persistence.metamodel.SetAttribute;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;
import org.openregistry.core.domain.Role;

@StaticMetamodel(JpaPersonImpl.class)
public abstract class JpaPersonImpl_ {

	public static volatile SingularAttribute<JpaPersonImpl, Long> id;
	public static volatile SetAttribute<JpaPersonImpl, JpaNameImpl> names;
	public static volatile ListAttribute<JpaPersonImpl, Role> roles;
	public static volatile SetAttribute<JpaPersonImpl, JpaIdentifierImpl> identifiers;
	public static volatile SingularAttribute<JpaPersonImpl, Date> dateOfBirth;
	public static volatile SingularAttribute<JpaPersonImpl, String> gender;
	public static volatile SingularAttribute<JpaPersonImpl, JpaActivationKeyImpl> activationKey;

}

