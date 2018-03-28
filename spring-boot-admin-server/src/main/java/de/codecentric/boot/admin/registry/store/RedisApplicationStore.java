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
package de.codecentric.boot.admin.registry.store;

import java.util.Collection;

import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisOperations;

import de.codecentric.boot.admin.model.Application;
import de.codecentric.boot.admin.registry.store.ApplicationStore;

/**
 * Application-Store backed by a Redis.
 */
public class RedisApplicationStore implements ApplicationStore {

    private final String keyPrefix;

    private final HashOperations<String, String, Application> hasOperation;

    public RedisApplicationStore ( String keyPrefix, RedisOperations<String, Application> redisTemplate ) {
        this.keyPrefix = keyPrefix;
        this.hasOperation = redisTemplate.opsForHash ();
    }

    @Override
    public Application save ( Application app ) {
        String idsKey = getApplicationKeyPrefix () + "ids";
        String nameKey = getApplicationKeyPrefix () + app.getName ();

        Application previouseApplication = hasOperation.get ( idsKey, app.getId () );

        hasOperation.put ( idsKey, app.getId (), app );
        hasOperation.put ( nameKey, app.getId (), app );

        return previouseApplication;
    }

    @Override
    public Collection<Application> findAll () {
        return hasOperation.values ( getApplicationKeyPrefix () + "ids" );
    }

    @Override
    public Application find ( String id ) {
        return hasOperation.get ( getApplicationKeyPrefix () + "ids",  id );
    }

    @Override
    public Collection<Application> findByName ( String name ) {
        return hasOperation.values ( getApplicationKeyPrefix () + name );
    }

    @Override
    public Application delete ( String id ) {
        String idsKey = getApplicationKeyPrefix () + "ids";

        Application application = hasOperation.get ( idsKey, id );
        if ( application != null ) {
            hasOperation.delete ( idsKey, id );
            hasOperation.delete ( getApplicationKeyPrefix () + application.getName (), id );
        }
        return application;
    }

    private String getApplicationKeyPrefix () {
        return this.keyPrefix + "applications:";
    }

}
