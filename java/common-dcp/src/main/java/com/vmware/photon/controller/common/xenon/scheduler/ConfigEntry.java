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

package com.vmware.photon.controller.common.xenon.scheduler;

import com.vmware.xenon.common.StatefulService;

/**
 * This is the config entry, one for each service registered with TaskSchedulerService.
 */
public class ConfigEntry {
  private Class<? extends StatefulService> service;

  private Integer tasksLimit;

  public ConfigEntry() {
  }

  public ConfigEntry(Class<? extends StatefulService> service, int tasksLimit) {
    this.service = service;
    this.tasksLimit = tasksLimit;
  }

  public Class<? extends StatefulService> getService() {
    return service;
  }

  public void setService(Class<? extends StatefulService> service) {
    this.service = service;
  }

  public Integer getTasksLimit() {
    return tasksLimit;
  }

  public void setTasksLimit(Integer tasksLimit) {
    this.tasksLimit = tasksLimit;
  }
}
