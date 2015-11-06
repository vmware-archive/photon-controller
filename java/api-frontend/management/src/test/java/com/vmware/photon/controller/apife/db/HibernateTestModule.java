/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.apife.db;

import com.vmware.photon.controller.api.common.db.CustomH2Dialect;
import com.vmware.photon.controller.api.common.db.Transactional;
import com.vmware.photon.controller.api.common.db.TransactionalInterceptor;
import com.vmware.photon.controller.apife.TestModule;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.matcher.Matchers;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.ImprovedNamingStrategy;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.reflections.Reflections;

import javax.persistence.Entity;

import java.util.Set;

/**
 * the hibernate test module.
 */
public class HibernateTestModule extends AbstractModule {
  @Override
  protected void configure() {
    install(new TestModule());

    TransactionalInterceptor interceptor = new TransactionalInterceptor();
    requestInjection(interceptor);
    bindInterceptor(Matchers.any(), Matchers.annotatedWith(Transactional.class), interceptor);
  }

  @Provides
  @Singleton
  public SessionFactory getSessionFactory() {
    Configuration configuration = new Configuration();

    Reflections reflections = new Reflections("com.vmware.photon.controller.apife.entities");
    Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Entity.class);

    Reflections baseReflections = new Reflections("com.vmware.photon.controller.apife.entities.base");
    classes.addAll(baseReflections.getTypesAnnotatedWith(Entity.class));

    Reflections commonReflections = new Reflections("com.vmware.photon.controller.api.common");
    classes.addAll(commonReflections.getTypesAnnotatedWith(Entity.class));

    for (final Class<?> clazz : classes) {
      configuration.addAnnotatedClass(clazz);
    }

    configuration.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "managed");
    configuration.setProperty(AvailableSettings.DIALECT, CustomH2Dialect.class.getName());
    configuration.setProperty(AvailableSettings.DRIVER, "org.h2.Driver");
    // in memory DB, wait up to 10 seconds after last connection closed before deleting data
    configuration.setProperty(AvailableSettings.URL, "jdbc:h2:mem:test;DB_CLOSE_DELAY=10");
    configuration.setProperty(AvailableSettings.HBM2DDL_AUTO, "create");
    configuration.setProperty(AvailableSettings.SHOW_SQL, "true");
    configuration.setNamingStrategy(ImprovedNamingStrategy.INSTANCE);

    ServiceRegistry serviceRegistry = new ServiceRegistryBuilder()
        .applySettings(configuration.getProperties())
        .build();

    return configuration.buildSessionFactory(serviceRegistry);
  }
}
