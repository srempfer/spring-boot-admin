/*
 * Copyright 2014-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.codecentric.boot.admin.server;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import lombok.ToString;

@lombok.Data
@ToString(exclude = "metadata")
public class HazelcastMapIssue1429Registration implements Serializable {

	public enum FillType {

		CONSTRUCTOR_SINGLETON_MAP, CONSTRUCTOR_HASH_MAP, PUT

	}

	private final Map<String, String> metadata;

	public HazelcastMapIssue1429Registration(FillType type, String key, String value) {
		switch (type) {
		case CONSTRUCTOR_SINGLETON_MAP:
			this.metadata = new LinkedHashMap<>(Collections.singletonMap("dummy", "1234567"));
			break;
		case CONSTRUCTOR_HASH_MAP:
			HashMap<String, String> tmp = new HashMap<>();
			tmp.put(key, value);
			this.metadata = new LinkedHashMap<>(tmp);
			break;
		case PUT:
			this.metadata = new LinkedHashMap<>();
			this.metadata.put(key, value);
			break;
		default:
			throw new IllegalStateException();
		}
	}

	@lombok.Builder(builderClassName = "Builder", toBuilder = true)
	private HazelcastMapIssue1429Registration(@lombok.Singular("metadata") Map<String, String> metadata) {
		this.metadata = new LinkedHashMap<>(metadata);
	}

}
