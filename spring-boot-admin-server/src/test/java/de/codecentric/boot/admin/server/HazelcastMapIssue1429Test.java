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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.impl.clientside.HazelcastClientProxy;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import de.codecentric.boot.admin.server.HazelcastMapIssue1429Registration.FillType;
import de.codecentric.boot.admin.server.domain.events.InstanceEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceRegisteredEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceRegistrationUpdatedEvent;
import de.codecentric.boot.admin.server.domain.values.InstanceId;
import de.codecentric.boot.admin.server.domain.values.Registration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = HazelcastMapIssue1429Test.HazelcastClientConfig.class)
@Testcontainers
public class HazelcastMapIssue1429Test {

	@Container
	private static GenericContainer hazelcastServer = new GenericContainer("hazelcast/hazelcast:3.10.6")
			.withExposedPorts(5701);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("hazelcast.host", hazelcastServer::getContainerIpAddress);
		registry.add("hazelcast.port", hazelcastServer::getFirstMappedPort);
	}

	@Autowired
	private HazelcastInstance hazelcastInstance;

	private InstanceId instanceId = InstanceId.of("id");

	@Test
	public void verifyReplaceWithEmptyEventLogWithoutMetadata() {
		IMap<InstanceId, List<InstanceEvent>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithEmptyEventLogWithoutMetadata");

		Registration registration = Registration.create("foo", "http://health").build();
		InstanceEvent event0 = new InstanceRegisteredEvent(instanceId, 0L, registration);

		List<InstanceEvent> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).isEmpty();

		List<InstanceEvent> newEvents = new ArrayList<>();
		newEvents.add(event0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithEmptyEventLogWithMetadata() {
		IMap<InstanceId, List<InstanceEvent>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithEmptyEventLogWithMetadata");

		Registration registration = Registration.create("foo", "http://health").metadata("dummy", "1234567").build();
		InstanceEvent event0 = new InstanceRegisteredEvent(instanceId, 0L, registration);

		List<InstanceEvent> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).isEmpty();

		List<InstanceEvent> newEvents = new ArrayList<>();
		newEvents.add(event0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithNoneEmptyEventLogWithoutMetadata() {
		IMap<InstanceId, List<InstanceEvent>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithNoneEmptyEventLogWithoutMetadata");

		Registration registration = Registration.create("foo", "http://health").build();
		InstanceEvent event0 = new InstanceRegisteredEvent(instanceId, 0L, registration);

		registration = Registration.create("foo", "http://health").build();
		InstanceEvent event1 = new InstanceRegistrationUpdatedEvent(instanceId, 0L, registration);

		List<InstanceEvent> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<InstanceEvent> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(event0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(event1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithNoneEmptyEventLogWithMetadata() {
		IMap<InstanceId, List<InstanceEvent>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithNoneEmptyEventLogWithMetadata");

		Registration registration = Registration.create("foo", "http://health").metadata("dummy", "1234567").build();
		InstanceEvent event0 = new InstanceRegisteredEvent(instanceId, 0L, registration);

		registration = Registration.create("foo", "http://health").metadata("dummy", "987654").build();
		InstanceEvent event1 = new InstanceRegistrationUpdatedEvent(instanceId, 0L, registration);

		List<InstanceEvent> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<InstanceEvent> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(event0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(event1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfTestRegistrationWithMetadataFilledByPut() {
		IMap<InstanceId, List<HazelcastMapIssue1429Registration>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfTestRegistrationWithMetadataFilledByPut");

		HazelcastMapIssue1429Registration registration0 = new HazelcastMapIssue1429Registration(FillType.PUT, "dummy",
				"1234567");

		HazelcastMapIssue1429Registration registration1 = new HazelcastMapIssue1429Registration(FillType.PUT, "dummy",
				"987654");

		List<HazelcastMapIssue1429Registration> oldEvents = eventLog.computeIfAbsent(instanceId,
				(key) -> new ArrayList<>(101));

		List<HazelcastMapIssue1429Registration> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfTestRegistrationWithMetadataFilledBySingeltonMap() {
		IMap<InstanceId, List<HazelcastMapIssue1429Registration>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfTestRegistrationWithMetadataFilledBySingeltonMap");

		HazelcastMapIssue1429Registration registration0 = new HazelcastMapIssue1429Registration(
				FillType.CONSTRUCTOR_SINGLETON_MAP, "dummy", "1234567");

		HazelcastMapIssue1429Registration registration1 = new HazelcastMapIssue1429Registration(
				FillType.CONSTRUCTOR_SINGLETON_MAP, "dummy", "987654");

		List<HazelcastMapIssue1429Registration> oldEvents = eventLog.computeIfAbsent(instanceId,
				(key) -> new ArrayList<>(101));

		List<HazelcastMapIssue1429Registration> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfTestRegistrationWithMetadataFilledByHashMap() {
		IMap<InstanceId, List<HazelcastMapIssue1429Registration>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfTestRegistrationWithMetadataFilledByHashMap");

		HazelcastMapIssue1429Registration registration0 = new HazelcastMapIssue1429Registration(
				FillType.CONSTRUCTOR_HASH_MAP, "dummy", "1234567");

		HazelcastMapIssue1429Registration registration1 = new HazelcastMapIssue1429Registration(
				FillType.CONSTRUCTOR_HASH_MAP, "dummy", "987654");

		List<HazelcastMapIssue1429Registration> oldEvents = eventLog.computeIfAbsent(instanceId,
				(key) -> new ArrayList<>(101));

		List<HazelcastMapIssue1429Registration> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfTestRegistrationWithMetadataBuilderStyle() {
		IMap<InstanceId, List<HazelcastMapIssue1429Registration>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfTestRegistrationWithMetadataBuilderStyle");

		HazelcastMapIssue1429Registration registration0 = HazelcastMapIssue1429Registration.builder()
				.metadata("dummy", "1234567").build();

		HazelcastMapIssue1429Registration registration1 = HazelcastMapIssue1429Registration.builder()
				.metadata("dummy", "987654").build();

		List<HazelcastMapIssue1429Registration> oldEvents = eventLog.computeIfAbsent(instanceId,
				(key) -> new ArrayList<>(101));

		List<HazelcastMapIssue1429Registration> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfRegistrationWithMetadata() {
		IMap<InstanceId, List<Registration>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfRegistrationWithMetadata");

		Registration registration0 = Registration.create("foo", "http://health").metadata("dummy", "1234567").build();

		Registration registration1 = Registration.create("foo", "http://health").metadata("dummy", "987654").build();

		List<Registration> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Registration> newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		newEvents.add(registration1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceOfRegistrationWithoutMetadata() {
		IMap<InstanceId, Registration> cache = hazelcastInstance.getMap("verifyReplaceOfRegistrationWithoutMetadata");

		Registration registration0 = Registration.create("foo2", "http://health").build();

		Registration oldRegistration = cache.computeIfAbsent(instanceId, (key) -> registration0);

		Registration newRegistration = Registration.create("foo1", "http://health").build();

		boolean replaced = cache.replace(instanceId, oldRegistration, newRegistration);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceOfRegistrationWithMetadata() {
		IMap<InstanceId, Registration> cache = hazelcastInstance.getMap("verifyReplaceOfRegistrationWithMetadata");

		Registration registration0 = Registration.create("foo2", "http://health").metadata("dummy", "1234567").build();

		Registration oldRegistration = cache.computeIfAbsent(instanceId, (key) -> registration0);

		Registration newRegistration = Registration.create("foo1", "http://health").metadata("dummy", "987654").build();

		boolean replaced = cache.replace(instanceId, oldRegistration, newRegistration);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfHashMapValue() {
		IMap<InstanceId, List<Map<String, String>>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfHashMapValue");

		List<Map<String, String>> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Map<String, String>> newEvents = new ArrayList<>(oldEvents);
		HashMap<String, String> hashMap0 = new HashMap<>();
		hashMap0.put("dummy", "1234567");
		newEvents.add(hashMap0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		HashMap<String, String> hashMap1 = new HashMap<>();
		hashMap1.put("dummy", "987654");
		newEvents.add(hashMap1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfHashMapValueFilledByConstructor() {
		IMap<InstanceId, List<Map<String, String>>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfHashMapValueFilledByConstructor");

		List<Map<String, String>> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Map<String, String>> newEvents = new ArrayList<>(oldEvents);
		HashMap<String, String> hashMap0 = new HashMap<>(Collections.singletonMap("dummy", "1234567"));
		newEvents.add(hashMap0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		HashMap<String, String> hashMap1 = new HashMap<>(Collections.singletonMap("dummy", "987654"));
		newEvents.add(hashMap1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfLinkedHashMapValue() {
		IMap<InstanceId, List<Map<String, String>>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfLinkedHashMapValue");

		List<Map<String, String>> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Map<String, String>> newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap0 = new LinkedHashMap<>();
		hashMap0.put("dummy", "1234567");
		newEvents.add(hashMap0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap1 = new LinkedHashMap<>();
		hashMap1.put("dummy", "987654");
		newEvents.add(hashMap1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfLinkedHashMapValueFilledByConstructor() {
		IMap<InstanceId, List<Map<String, String>>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfLinkedHashMapValueFilledByConstructor");

		List<Map<String, String>> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Map<String, String>> newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap0 = new LinkedHashMap<>(Collections.singletonMap("dummy", "1234567"));
		newEvents.add(hashMap0);

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap1 = new LinkedHashMap<>(Collections.singletonMap("dummy", "987654"));
		newEvents.add(hashMap1);

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	// https://github.com/hazelcast/hazelcast/issues/12574
	@Test
	public void verifySerializeWithListOfLinkedHashMapValueFilledByConstructor() {
		IMap<InstanceId, List<Map<String, String>>> cache = hazelcastInstance
				.getMap("verifyReplaceWithListOfLinkedHashMapValueFilledByConstructor");

		ArrayList<Map<String, String>> orgValue = new ArrayList<>();
		LinkedHashMap<String, String> hashMap0 = new LinkedHashMap<>(Collections.singletonMap("dummy", "1234567"));
		orgValue.add(hashMap0);

		cache.put(instanceId, orgValue);
		List<Map<String, String>> cacheValue = cache.get(instanceId);

		byte[] dataCacheValue = ((HazelcastClientProxy) hazelcastInstance).getSerializationService()
				.toBytes(cacheValue);
		byte[] dataOrgValue = ((HazelcastClientProxy) hazelcastInstance).getSerializationService().toBytes(orgValue);

		assertThat(Arrays.equals(dataOrgValue, dataCacheValue)).isTrue();
	}

	@Test
	public void verifyReplaceWithListOfUnmodifiableMapValue() {
		IMap<InstanceId, List<Map<String, String>>> eventLog = hazelcastInstance
				.getMap("verifyReplaceWithListOfUnmodifiableMapValue");

		List<Map<String, String>> oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));

		List<Map<String, String>> newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap0 = new LinkedHashMap<>();
		hashMap0.put("dummy", "1234567");
		newEvents.add(Collections.unmodifiableMap(hashMap0));

		boolean replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();

		oldEvents = eventLog.computeIfAbsent(instanceId, (key) -> new ArrayList<>(101));
		assertThat(oldEvents).hasSize(1);

		newEvents = new ArrayList<>(oldEvents);
		LinkedHashMap<String, String> hashMap1 = new LinkedHashMap<>();
		hashMap1.put("dummy", "987654");
		newEvents.add(Collections.unmodifiableMap(hashMap1));

		replaced = eventLog.replace(instanceId, oldEvents, newEvents);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithHashMapValue() {
		IMap<InstanceId, Map<String, String>> cache = hazelcastInstance.getMap("verifyReplaceWithHashMapValue");
		Map<String, String> oldMap = cache.computeIfAbsent(instanceId, (key) -> new HashMap<>());

		Map<String, String> newMap = new HashMap<>(oldMap);
		newMap.put("dummy", "1234567");

		boolean replaced = cache.replace(instanceId, oldMap, newMap);
		assertThat(replaced).isTrue();

		oldMap = cache.computeIfAbsent(instanceId, (key) -> new HashMap<>());

		newMap = new HashMap<>(oldMap);
		newMap.put("dummy", "987654");

		replaced = cache.replace(instanceId, oldMap, newMap);
		assertThat(replaced).isTrue();
	}

	@Test
	public void verifyReplaceWithLinkedHashMapValue() {
		IMap<InstanceId, Map<String, String>> cache = hazelcastInstance.getMap("verifyReplaceWithLinkedHashMapValue");
		Map<String, String> oldMap = cache.computeIfAbsent(instanceId, (key) -> new HashMap<>());

		Map<String, String> newMap = new LinkedHashMap<>(oldMap);
		newMap.put("dummy", "1234567");

		boolean replaced = cache.replace(instanceId, oldMap, newMap);
		assertThat(replaced).isTrue();

		oldMap = cache.computeIfAbsent(instanceId, (key) -> new HashMap<>());

		newMap = new LinkedHashMap<>(oldMap);
		newMap.put("dummy", "987654");

		replaced = cache.replace(instanceId, oldMap, newMap);
		assertThat(replaced).isTrue();
	}

	@ImportAutoConfiguration(classes = HazelcastAutoConfiguration.class)
	@Configuration
	public static class HazelcastClientConfig {

		@Bean
		public ClientConfig hcConfig(@Value("${hazelcast.host}") String hazelcastHost,
				@Value("${hazelcast.port}") String hazelcastPort) {
			ClientConfig clientConfig = new ClientConfig();
			clientConfig.getNetworkConfig().addAddress(hazelcastHost + ":" + hazelcastPort);
			return clientConfig;
		}

	}

}
