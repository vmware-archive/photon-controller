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

import liquibase.Liquibase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.ImprovedNamingStrategy;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.service.ServiceRegistryBuilder;
import org.reflections.Reflections;
import org.testng.annotations.Test;

import javax.persistence.Entity;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Set;

/**
 * Verify that the migrations contain everything expected by the hibernate entities.
 */
public class MigrationTest {

  @Test
  public void testMigrations() throws SQLException, LiquibaseException {
    try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:migrations", "sa", "")) {
      Liquibase liquibase = new Liquibase("migrations.xml", new ClassLoaderResourceAccessor(),
          new JdbcConnection(connection));
      liquibase.update("");

      Configuration configuration = new Configuration();

      Reflections reflections = new Reflections("com.vmware.photon.controller.apife.entities");
      Set<Class<?>> classes = reflections.getTypesAnnotatedWith(Entity.class);

      Reflections commonReflections = new Reflections("com.vmware.photon.controller.api.common");
      classes.addAll(commonReflections.getTypesAnnotatedWith(Entity.class));
      for (final Class<?> clazz : classes) {
        configuration.addAnnotatedClass(clazz);
      }

      configuration.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "thread");
      configuration.setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect");
      configuration.setProperty(AvailableSettings.DRIVER, "org.h2.Driver");
      configuration.setProperty(AvailableSettings.URL, "jdbc:h2:mem:migrations");
      configuration.setProperty(AvailableSettings.USER, "sa");
      configuration.setProperty(AvailableSettings.PASS, "");
      configuration.setProperty(AvailableSettings.HBM2DDL_AUTO, "validate");
      configuration.setNamingStrategy(ImprovedNamingStrategy.INSTANCE);

      ServiceRegistry serviceRegistry = new ServiceRegistryBuilder()
          .applySettings(configuration.getProperties())
          .build();

      SessionFactory factory = configuration.buildSessionFactory(serviceRegistry);
      factory.close();
    }
  }

}
