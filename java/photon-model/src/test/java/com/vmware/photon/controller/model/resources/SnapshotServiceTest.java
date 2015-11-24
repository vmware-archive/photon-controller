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

package com.vmware.photon.controller.model.resources;

import com.vmware.dcp.common.Service;
import com.vmware.photon.controller.model.ModelServices;
import com.vmware.photon.controller.model.helpers.BaseModelTest;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.testng.Assert.assertNotNull;

import java.util.EnumSet;
import java.util.UUID;

/**
 * This class implements tests for the {@link ResourceDescriptionService} class.
 */
public class SnapshotServiceTest {

  private SnapshotService.SnapshotState buildValidStartState() throws Throwable {
    SnapshotService.SnapshotState st = new SnapshotService.SnapshotState();
    st.id = UUID.randomUUID().toString();
    st.name = "friendly-name";
    st.computeLink = "compute-link";

    return st;
  }

  @Test
  private void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    private SnapshotService snapshotService;

    @BeforeMethod
    public void setUpTest() {
      snapshotService = new SnapshotService();
    }

    @Test
    public void testServiceOptions() {

      EnumSet<Service.ServiceOption> expected = EnumSet.of(
          Service.ServiceOption.PERSISTENCE,
          Service.ServiceOption.REPLICATION,
          Service.ServiceOption.OWNER_SELECTION,
          Service.ServiceOption.INSTRUMENTATION);

      assertThat(snapshotService.getOptions(), is(expected));
    }
  }

  /**
   * This class implements tests for the handleStart method.
   */
  public class HandleStartTest extends BaseModelTest {
    @Override
    protected Class[] getFactoryServices() {
      return ModelServices.FACTORIES;
    }



    @Test
    public void testValidStartState() throws Throwable {

      SnapshotService.SnapshotState startState = buildValidStartState();
      assertNotNull(host);

      SnapshotService.SnapshotState returnState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class);


      assertNotNull(returnState);
      assertThat(returnState.id, is(startState.id));
      assertThat(returnState.name, is(startState.name));
      assertThat(returnState.computeLink, is(startState.computeLink));

    }


    @Test
    public void testMissingId() throws Throwable {

      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.id = null;

      SnapshotService.SnapshotState returnState = host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class);

      assertNotNull(returnState);
      assertNotNull(returnState.id);
    }


    @Test
    public void testMissingName() throws Throwable {

      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.name = null;

      host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class,
          IllegalArgumentException.class);
    }


    @Test
    public void testMissingComputeLink() throws Throwable {

      SnapshotService.SnapshotState startState = buildValidStartState();
      startState.computeLink = null;

      host.postServiceSynchronously(
          SnapshotFactoryService.SELF_LINK,
          startState,
          SnapshotService.SnapshotState.class,
          IllegalArgumentException.class);
    }
  }
}
