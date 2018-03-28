/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codecentric.boot.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate;
import org.springframework.boot.autoconfigure.hazelcast.HazelcastAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import de.codecentric.boot.admin.event.ClientApplicationEvent;
import de.codecentric.boot.admin.journal.store.JournaledEventStore;
import de.codecentric.boot.admin.journal.store.RedisJournaledEventStore;
import de.codecentric.boot.admin.model.Application;
import de.codecentric.boot.admin.registry.store.ApplicationStore;
import de.codecentric.boot.admin.registry.store.RedisApplicationStore;


/**
 * Configuration for the Application-Store and Journal-Store backed by Redis.
 */
@Configuration
@ConditionalOnSingleCandidate ( RedisConnectionFactory.class )
@ConditionalOnProperty ( prefix = "spring.boot.admin.redis", name = "enabled", matchIfMissing = true )
@AutoConfigureBefore(AdminServerWebConfiguration.class)
@AutoConfigureAfter(HazelcastAutoConfiguration.class)
public class RedisStoreConfiguration {

    @Value ( "${spring.boot.admin.redis.prefix:spring-boot-admin:}" )
    private String keyPrefix;

    @Bean
    public ApplicationStore applicationStore ( RedisTemplate<String, Application> redisTemplate ) {
        return new RedisApplicationStore ( keyPrefix, redisTemplate );
    }

    @Bean
    public RedisTemplate<String, Application> redisTemplate4ApplicationStore ( RedisConnectionFactory redisConnectionFactory ) {
        RedisTemplate<String, Application> template = new RedisTemplate<String, Application> ();
        template.setConnectionFactory ( redisConnectionFactory );

        RedisSerializer<String> stringSerializer = new StringRedisSerializer ();
        template.setKeySerializer ( stringSerializer );
        template.setHashKeySerializer ( stringSerializer );
        return template;
    }

    @Bean
    public JournaledEventStore journaledEventStore ( RedisTemplate<String, ClientApplicationEvent> redisTemplate ) {
        return new RedisJournaledEventStore ( keyPrefix, redisTemplate );
    }

    @Bean
    public RedisTemplate<String, ClientApplicationEvent> redisTemplate4journal ( RedisConnectionFactory redisConnectionFactory ) {
        RedisTemplate<String, ClientApplicationEvent> template = new RedisTemplate<String, ClientApplicationEvent> ();
        template.setConnectionFactory ( redisConnectionFactory );

        RedisSerializer<String> stringSerializer = new StringRedisSerializer ();
        template.setKeySerializer ( stringSerializer );
        template.setHashKeySerializer ( stringSerializer );
        return template;
    }

}
