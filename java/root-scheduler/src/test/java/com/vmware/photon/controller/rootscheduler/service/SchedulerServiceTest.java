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
package com.vmware.photon.controller.rootscheduler.service;

import com.vmware.photon.controller.roles.gen.GetSchedulersResponse;
import com.vmware.photon.controller.rootscheduler.Config;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import org.apache.thrift.TException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test cases for SchedulerServiceTest.
 */
public class SchedulerServiceTest {
  @Mock
  private Config config;

  @Mock
  private FlatSchedulerService flatSchedulerService;

  private SchedulerService schedulerService;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetStatus() throws TException {
    schedulerService = new SchedulerService(config, flatSchedulerService);
    GetStatusRequest request = new GetStatusRequest();
    doReturn(new Status()).when(flatSchedulerService).get_status(request);
    schedulerService.get_status(request);
    verify(flatSchedulerService, times(1)).get_status(request);
  }

  @Test
  public void testPlace() throws TException {
    schedulerService = new SchedulerService(config, flatSchedulerService);
    PlaceRequest request = new PlaceRequest();
    doReturn(new PlaceResponse()).when(flatSchedulerService).place(request);
    schedulerService.place(request);
    verify(flatSchedulerService, times(1)).place(request);
  }

  @Test
  public void testOnJoin() throws TException {
    schedulerService = new SchedulerService(config, flatSchedulerService);
    doNothing().when(flatSchedulerService).onJoin();
    schedulerService.onJoin();
    verify(flatSchedulerService, times(1)).onJoin();
  }

  @Test
  public void testOnLeave() throws TException {
    schedulerService = new SchedulerService(config, flatSchedulerService);
    doNothing().when(flatSchedulerService).onLeave();
    schedulerService.onLeave();
    verify(flatSchedulerService, times(1)).onLeave();
  }
}
