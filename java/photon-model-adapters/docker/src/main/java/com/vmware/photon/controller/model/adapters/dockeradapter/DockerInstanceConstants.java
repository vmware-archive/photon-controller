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

package com.vmware.photon.controller.model.adapters.dockeradapter;

/**
 * Represents the docker related constant values.
 */
public class DockerInstanceConstants {

  // Default docker port number.
  public static final int DEFAULT_DOCKER_PORT = 2375;

  // Custom property keys for the docker client.
  public static final String CP_DOCKER_PORT = "DOCKER_PORT";
  public static final String CP_DOCKER_CREATE_NET = "DOCKER_CREATE_NET";
  public static final String CP_DOCKER_CREATE_RESTART_POLICY = "DOCKER_CREATE_RESTART_POLICY";
  public static final String CP_DOCKER_CREATE_VOLUME_BINDING = "DOCKER_CREATE_VOLUME_BINDING";
  public static final String CP_DOCKER_CREATE_COMMAND = "DOCKER_CREATE_COMMAND";
}
