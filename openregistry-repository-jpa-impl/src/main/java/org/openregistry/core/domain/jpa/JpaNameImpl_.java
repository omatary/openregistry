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
package org.openregistry.core.domain.jpa;

import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.metamodel.StaticMetamodel;

@StaticMetamodel(JpaNameImpl.class)
public abstract class JpaNameImpl_ {

	public static volatile SingularAttribute<JpaNameImpl, Long> id;
	public static volatile SingularAttribute<JpaNameImpl, JpaTypeImpl> type;
	public static volatile SingularAttribute<JpaNameImpl, String> prefix;
	public static volatile SingularAttribute<JpaNameImpl, String> given;
	public static volatile SingularAttribute<JpaNameImpl, String> middle;
	public static volatile SingularAttribute<JpaNameImpl, String> family;
	public static volatile SingularAttribute<JpaNameImpl, String> suffix;
	public static volatile SingularAttribute<JpaNameImpl, JpaPersonImpl> person;
	public static volatile SingularAttribute<JpaNameImpl, Boolean> officialName;
	public static volatile SingularAttribute<JpaNameImpl, Boolean> preferredName;

}

