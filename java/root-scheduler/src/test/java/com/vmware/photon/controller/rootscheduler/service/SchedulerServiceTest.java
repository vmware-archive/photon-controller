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
import com.vmware.photon.controller.scheduler.gen.ConfigureRequest;
import com.vmware.photon.controller.scheduler.gen.ConfigureResponse;
import com.vmware.photon.controller.scheduler.gen.FindRequest;
import com.vmware.photon.controller.scheduler.gen.FindResponse;
import com.vmware.photon.controller.scheduler.gen.PlaceRequest;
import com.vmware.photon.controller.scheduler.gen.PlaceResponse;
import com.vmware.photon.controller.status.gen.GetStatusRequest;
import com.vmware.photon.controller.status.gen.Status;

import org.apache.thrift.TException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
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
  private RootSchedulerService rootSchedulerService;

  @Mock
  private FlatSchedulerService flatSchedulerService;

  private SchedulerService schedulerService;

  @BeforeMethod
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @DataProvider(name = "schedulerMode")
  public Object[][] getSchedulerModes() {
    return new Object[][] {
        {"flat"},
        {"root"}
    };
  }

  @Test(dataProvider = "schedulerMode")
  public void testGetSchedulers(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    doReturn(new GetSchedulersResponse()).when(flatSchedulerService).get_schedulers();
    doReturn(new GetSchedulersResponse()).when(rootSchedulerService).get_schedulers();
    schedulerService.get_schedulers();
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).get_schedulers();
    } else {
      verify(rootSchedulerService, times(1)).get_schedulers();
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testGetStatus(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    GetStatusRequest request = new GetStatusRequest();
    doReturn(new Status()).when(flatSchedulerService).get_status(request);
    doReturn(new Status()).when(rootSchedulerService).get_status(request);
    schedulerService.get_status(request);
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).get_status(request);
    } else {
      verify(rootSchedulerService, times(1)).get_status(request);
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testConfigure(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    ConfigureRequest request = new ConfigureRequest();
    doReturn(new ConfigureResponse()).when(flatSchedulerService).configure(request);
    doReturn(new ConfigureResponse()).when(rootSchedulerService).configure(request);
    schedulerService.configure(request);
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).configure(request);
    } else {
      verify(rootSchedulerService, times(1)).configure(request);
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testPlace(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    PlaceRequest request = new PlaceRequest();
    doReturn(new PlaceResponse()).when(flatSchedulerService).place(request);
    doReturn(new PlaceResponse()).when(rootSchedulerService).place(request);
    schedulerService.place(request);
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).place(request);
    } else {
      verify(rootSchedulerService, times(1)).place(request);
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testFind(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    FindRequest request = new FindRequest();
    doReturn(new FindResponse()).when(flatSchedulerService).find(request);
    doReturn(new FindResponse()).when(rootSchedulerService).find(request);
    schedulerService.find(request);
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).find(request);
    } else {
      verify(rootSchedulerService, times(1)).find(request);
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testOnJoin(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    doNothing().when(flatSchedulerService).onJoin();
    doNothing().when(rootSchedulerService).onJoin();
    schedulerService.onJoin();
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).onJoin();
    } else {
      verify(rootSchedulerService, times(1)).onJoin();
    }
  }

  @Test(dataProvider = "schedulerMode")
  public void testOnLeave(String schedulerMode) throws TException {
    schedulerService = new SchedulerService(config, rootSchedulerService, flatSchedulerService);
    doReturn(schedulerMode).when(config).getMode();
    doNothing().when(flatSchedulerService).onLeave();
    doNothing().when(rootSchedulerService).onLeave();
    schedulerService.onLeave();
    if (schedulerMode.equals("flat")) {
      verify(flatSchedulerService, times(1)).onLeave();
    } else {
      verify(rootSchedulerService, times(1)).onLeave();
    }
  }
}
