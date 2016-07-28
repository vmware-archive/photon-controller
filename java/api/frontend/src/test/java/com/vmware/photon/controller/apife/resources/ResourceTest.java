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

package com.vmware.photon.controller.apife.resources;

import com.vmware.photon.controller.apife.InfrastructureTestModule;
import com.vmware.photon.controller.apife.providers.ConstraintViolationExceptionMapper;
import com.vmware.photon.controller.apife.providers.ExternalExceptionMapper;
import com.vmware.photon.controller.apife.providers.JsonProcessingExceptionMapper;
import com.vmware.photon.controller.apife.providers.LoggingExceptionMapper;
import com.vmware.photon.controller.apife.providers.WebApplicationExceptionMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import com.google.common.collect.Sets;
import com.google.inject.Inject;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.servlet.ServletProperties;
import org.glassfish.jersey.test.DeploymentContext;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.ServletDeploymentContext;
import org.glassfish.jersey.test.inmemory.InMemoryTestContainerFactory;
import org.glassfish.jersey.test.spi.TestContainerException;
import org.glassfish.jersey.test.spi.TestContainerFactory;
import org.powermock.modules.testng.PowerMockTestCase;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Guice;

import javax.validation.Validator;
import javax.ws.rs.client.Client;

import java.util.Set;

/**
 * Initially adapted from dropwizard (0.6.2) ResourceTest class which was later modified
 * to accommodate the changes introduced in dropwizard 0.8.2 based on ResourceTestRule.
 * This test does the following in addition to the default dropwizard ResourceTestRule:
 * - extend PowerMockTestCase so we can use @Mock annotations;
 * - setup/teardown Jersey test server for each test, so we are more mock-friendly. This also
 * serves as a replacement for Junit @Rule which is not available in TestNG;
 * - clear providers, resources and features before each test to minimize state sharing between tests.
 */
@Guice(modules = {InfrastructureTestModule.class})
public abstract class ResourceTest extends PowerMockTestCase {

  static {
    SLF4JBridgeHandler.removeHandlersForRootLogger();
    SLF4JBridgeHandler.install();
  }

  private final Set<Object> singletons = Sets.newHashSet();
  private final Set<Class<?>> providers = Sets.newHashSet();
  private JerseyTest test;

  @Inject
  private Validator validator;

  protected abstract void setUpResources() throws Exception;

  protected void addResource(Object resource) {
    singletons.add(resource);
  }

  public void addProvider(Object provider) {
    singletons.add(provider);
  }

  public void clearSingletons() {
    singletons.clear();
  }

  protected Client client() {
    return test.client();
  }

  @BeforeMethod
  public void setUpJersey() throws Exception {
    clearSingletons();

    addProvider(new LoggingExceptionMapper());
    addProvider(new WebApplicationExceptionMapper());
    addProvider(new ConstraintViolationExceptionMapper());
    addProvider(new ExternalExceptionMapper());
    addProvider(new JsonProcessingExceptionMapper());

    setUpResources();

    this.test = new JerseyTest() {
      @Override
      protected TestContainerFactory getTestContainerFactory() throws TestContainerException {
        return new InMemoryTestContainerFactory();
      }

      @Override
      protected DeploymentContext configureDeployment() {
        final DropwizardResourceConfig config = new DropwizardResourceConfig();
        for (Class<?> provider : providers) {
          config.getClasses().add(provider);
        }
        final ObjectMapper mapper = new ObjectMapper();
        config.register(new JacksonMessageBodyProvider(mapper, validator));

        for (Object singleton : singletons) {
          config.register(singleton);
        }

        ServletDeploymentContext deploymentContext = ServletDeploymentContext.builder(config)
            .initParam(ServletProperties.JAXRS_APPLICATION_CLASS, DropwizardResourceConfig.class.getName())
            .build();
        return deploymentContext;
      }

      @Override
      protected void configureClient(final ClientConfig config) {
        JacksonJsonProvider jsonProvider = new JacksonJsonProvider();
        jsonProvider.setMapper(Jackson.newObjectMapper());
        config.register(jsonProvider);
      }
    };
    test.setUp();
  }

  @AfterMethod
  public void tearDownJersey() throws Exception {
    if (test != null) {
      test.tearDown();
    }
  }
}
