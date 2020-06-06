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

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.HazelcastInstance;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastClientFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import de.codecentric.boot.admin.server.config.EnableAdminServer;
import de.codecentric.boot.admin.server.domain.events.InstanceEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceRegisteredEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceRegistrationUpdatedEvent;
import de.codecentric.boot.admin.server.domain.events.InstanceStatusChangedEvent;
import de.codecentric.boot.admin.server.domain.values.InstanceId;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
public class HazelcastMapIssue1429IntegrationTest {

	private static ParameterizedTypeReference<Map<String, Object>> RESPONSE_TYPE = new ParameterizedTypeReference<Map<String, Object>>() {
	};

	private static String HAZELCAST_EVENT_STORE_NAME = "spring-boot-admin-event-store";

	@Container
	private GenericContainer hazelcastServer = new GenericContainer("hazelcast/hazelcast:3.10.6").withExposedPorts(5701)
			.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("Hazelcast-Server")));

	@Test
	public void verifyRegistration() throws InterruptedException {
		String instanceId;
		try (ConfigurableApplicationContext adminContext = createAdminApplication()) {
			int localPort = adminContext.getEnvironment().getProperty("local.server.port", Integer.class, 0);

			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + localPort)
					.responseTimeout(Duration.ofSeconds(60)).build();

			instanceId = sendRegistration(client, true);
			assertThat(instanceId).isNotEmpty();
			Thread.sleep(3000);
		}

		ConcurrentMap<InstanceId, List<InstanceEvent>> eventLog = getEventLogMap();
		InstanceId key = InstanceId.of(instanceId);
		assertThat(eventLog).containsOnlyKeys(key);
		List<InstanceEvent> events = eventLog.get(key);
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(InstanceRegisteredEvent.class);
		assertThat(events.get(1)).isInstanceOf(InstanceStatusChangedEvent.class);
	}

	@Test
	public void verifyRegistrationAfterRestart() throws InterruptedException {
		String instanceId;
		try (ConfigurableApplicationContext adminContext = createAdminApplication()) {
			int localPort = adminContext.getEnvironment().getProperty("local.server.port", Integer.class, 0);

			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + localPort)
					.responseTimeout(Duration.ofSeconds(10)).build();

			instanceId = sendRegistration(client, true);
			assertThat(instanceId).isNotEmpty();
			Thread.sleep(3000);
		}

		ConcurrentMap<InstanceId, List<InstanceEvent>> eventLog = getEventLogMap();
		InstanceId key = InstanceId.of(instanceId);
		assertThat(eventLog).containsOnlyKeys(key);
		List<InstanceEvent> events = eventLog.get(key);
		assertThat(events).hasSize(2);
		assertThat(events.get(0)).isInstanceOf(InstanceRegisteredEvent.class);
		assertThat(events.get(1)).isInstanceOf(InstanceStatusChangedEvent.class);

		try (ConfigurableApplicationContext adminContext = createAdminApplication()) {
			int localPort = adminContext.getEnvironment().getProperty("local.server.port", Integer.class, 0);

			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + localPort)
					.responseTimeout(Duration.ofSeconds(60)).build();

			instanceId = sendRegistration(client, true);
			assertThat(instanceId).isNotEmpty();
		}

		eventLog = getEventLogMap();
		key = InstanceId.of(instanceId);
		assertThat(eventLog).containsOnlyKeys(key);
		events = eventLog.get(key);
		assertThat(events).hasSize(4);
		assertThat(events.get(0)).isInstanceOf(InstanceRegisteredEvent.class);
		assertThat(events.get(1)).isInstanceOf(InstanceStatusChangedEvent.class);
		assertThat(events.get(2)).isInstanceOf(InstanceRegistrationUpdatedEvent.class);
		assertThat(events.get(3)).isInstanceOf(InstanceStatusChangedEvent.class);
	}

	@Test
	public void verifyRegistrationWithoutRestart() throws InterruptedException {
		String instanceId;
		try (ConfigurableApplicationContext adminContext = createAdminApplication()) {
			int localPort = adminContext.getEnvironment().getProperty("local.server.port", Integer.class, 0);

			WebTestClient client = WebTestClient.bindToServer().baseUrl("http://localhost:" + localPort)
					.responseTimeout(Duration.ofSeconds(60)).build();

			instanceId = sendRegistration(client, true);
			assertThat(instanceId).isNotEmpty();
			Thread.sleep(3000);

			ConcurrentMap<InstanceId, List<InstanceEvent>> eventLog = getEventLogMap();
			InstanceId key = InstanceId.of(instanceId);
			assertThat(eventLog).containsOnlyKeys(key);
			List<InstanceEvent> events = eventLog.get(key);
			assertThat(events).hasSize(2);
			assertThat(events.get(0)).isInstanceOf(InstanceRegisteredEvent.class);
			assertThat(events.get(1)).isInstanceOf(InstanceStatusChangedEvent.class);

			instanceId = sendRegistration(client, true);
			assertThat(instanceId).isNotEmpty();
		}

		ConcurrentMap<InstanceId, List<InstanceEvent>> eventLog = getEventLogMap();
		InstanceId key = InstanceId.of(instanceId);
		assertThat(eventLog).containsOnlyKeys(key);
		List<InstanceEvent> events = eventLog.get(key);
		assertThat(events).hasSize(4);
		assertThat(events.get(0)).isInstanceOf(InstanceRegisteredEvent.class);
		assertThat(events.get(1)).isInstanceOf(InstanceStatusChangedEvent.class);
		assertThat(events.get(2)).isInstanceOf(InstanceRegistrationUpdatedEvent.class);
		assertThat(events.get(3)).isInstanceOf(InstanceStatusChangedEvent.class);
	}

	private ConfigurableApplicationContext createAdminApplication() {
		return new SpringApplicationBuilder().sources(TestAdminApplication.class).web(WebApplicationType.SERVLET).run(
				"--server.port=0", "--spring.boot.admin.monitor.default-timeout=2500",
				"--spring.boot.admin.hazelcast.event-store=" + HAZELCAST_EVENT_STORE_NAME,
				"--hazelcast.host=" + hazelcastServer.getContainerIpAddress(),
				"--hazelcast.port=" + hazelcastServer.getMappedPort(5701), "--logging.level.de.codecentric=DEBUG");
	}

	private String sendRegistration(WebTestClient client) {
		return sendRegistration(client, false);
	}

	// send random metadata to be sure that a InstanceRegistrationUpdatedEvent is produced
	// see de.codecentric.boot.admin.server.domain.entities.Instance.register
	private String sendRegistration(WebTestClient client, boolean withRandomMetadata) {
		String managementUrl = "http://localhost:325489";

		final String registration;
		if (withRandomMetadata) {
			//@formatter:off
			registration = "{ \"name\": \"test\", " +
				"\"healthUrl\": \"" + managementUrl + "/health\", " +
				"\"managementUrl\": \"" + managementUrl + "\", " +
				"\"metadata\": { \"random-dummy\": \"" + System.currentTimeMillis() + "\"} }";
			//@formatter:on

		}
		else {
			//@formatter:off
			registration = "{ \"name\": \"test\", " +
				"\"healthUrl\": \"" + managementUrl + "/health\", " +
				"\"managementUrl\": \"" + managementUrl + "\" }";
			//@formatter:on
		}

		EntityExchangeResult<Map<String, Object>> result = client.post().uri("/instances")
				.accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).bodyValue(registration)
				.exchange().expectStatus().isCreated().expectBody(RESPONSE_TYPE).returnResult();
		//@formatter:on
		assertThat(result.getResponseBody()).containsKeys("id");
		return result.getResponseBody().get("id").toString();
	}

	protected ConcurrentMap<InstanceId, List<InstanceEvent>> getEventLogMap() {
		HazelcastInstance hazelcast = createHazelcastInstance();
		return hazelcast.getMap(HAZELCAST_EVENT_STORE_NAME);
	}

	private HazelcastInstance createHazelcastInstance() {
		String address = hazelcastServer.getContainerIpAddress() + ":" + hazelcastServer.getMappedPort(5701);

		ClientConfig clientConfig = new ClientConfig();
		clientConfig.getNetworkConfig().addAddress(address);

		return (new HazelcastClientFactory(clientConfig)).getHazelcastInstance();
	}

	@EnableAdminServer
	@EnableAutoConfiguration
	@SpringBootConfiguration
	public static class TestAdminApplication {

		@Configuration(proxyBeanMethods = false)
		public static class HazelcastConfiguration {

			@Bean
			public ClientConfig hcConfig(@Value("${hazelcast.host}") String hazelcastHost,
					@Value("${hazelcast.port}") String hazelcastPort) {
				ClientConfig clientConfig = new ClientConfig();
				clientConfig.getNetworkConfig().addAddress(hazelcastHost + ":" + hazelcastPort);
				return clientConfig;
			}

		}

		@Configuration(proxyBeanMethods = false)
		public static class SecurityConfiguration extends WebSecurityConfigurerAdapter {

			@Override
			protected void configure(HttpSecurity http) throws Exception {
				http.authorizeRequests().anyRequest().permitAll()//
						.and().csrf().disable();
			}

		}

	}

}
