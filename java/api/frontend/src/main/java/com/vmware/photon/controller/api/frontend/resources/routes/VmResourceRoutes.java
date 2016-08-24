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

package com.vmware.photon.controller.api.frontend.resources.routes;

/**
 * String constants of routes used by vm related resource classes.
 */
public class VmResourceRoutes {

  public static final String API = "/vms";

  public static final String VM_PATH = "/vms/{id}";

  public static final String VM_ATTACH_DISK_PATH = "/vms/{id}/attach_disk";

  public static final String VM_DETACH_DISK_PATH = "/vms/{id}/detach_disk";

  public static final String VM_ATTACH_ISO_PATH = "/vms/{id}/attach_iso";

  public static final String VM_DETACH_ISO_PATH = "/vms/{id}/detach_iso";

  public static final String VM_SUBNETS_PATH = "/vms/{id}/subnets";

  public static final String VM_MKS_TICKET_PATH = "/vms/{id}/mks_ticket";

  public static final String VM_SET_METADATA_PATH = "/vms/{id}/set_metadata";

  public static final String VM_CREATE_IMAGE_PATH = "/vms/{id}/create_image";

  public static final String VM_TAGS_PATH = "/vms/{id}/tags";

  public static final String VM_TASKS_PATH = "/vms/{id}/tasks";

  public static final String VM_START_ACTION = "/start";

  public static final String VM_STOP_ACTION = "/stop";

  public static final String VM_RESTART_ACTION = "/restart";

  public static final String VM_RESUME_ACTION = "/resume";

  public static final String VM_SUSPEND_ACTION = "/suspend";

  public static final String VM_AQUIRE_FLOATING_IP_ACTION = "/aquire_floating_ip";

}
