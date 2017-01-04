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

package com.vmware.photon.controller.housekeeper.xenon;

import com.vmware.xenon.common.Service;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.EnumSet;

/**
 * Test {@link ImageSeederSyncServiceFactory}.
 */
public class ImageSeederSyncServiceFactoryTest {

  private ImageSeederSyncServiceFactory factory;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests for the constructors.
   */
  public class InitializationTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      factory = new ImageSeederSyncServiceFactory();
    }

    @Test
    void testServiceOptions() {
      // Factory capability is implicitly added as part of the factory constructor.
      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.INSTRUMENTATION,
          Service.ServiceOption.FACTORY,
          Service.ServiceOption.CONCURRENT_GET_HANDLING,
          Service.ServiceOption.CONCURRENT_UPDATE_HANDLING);
      assertThat(factory.getOptions(), is(expected));
    }
  }

  /**
   * Tests for the createServiceInstance method.
   */
  public class CreateServiceInstanceTest {

    @BeforeMethod
    public void setUp() throws Throwable {
      factory = new ImageSeederSyncServiceFactory();
    }

    @Test
    void testSuccess() throws Throwable {
      Service service = factory.createServiceInstance();
      assertThat(service, is(notNullValue()));
      assertThat(service, instanceOf(ImageSeederSyncService.class));
    }
  }
}
