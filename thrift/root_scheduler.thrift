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

namespace java com.vmware.photon.controller.scheduler.root.gen
namespace py gen.scheduler.root

include 'roles.thrift'
include 'scheduler.thrift'
include 'status.thrift'

// Root scheduler service
service RootScheduler {
  roles.GetSchedulersResponse get_schedulers()
  status.Status get_status(1:status.GetStatusRequest request)
  scheduler.PlaceResponse place(1: scheduler.PlaceRequest request)
}
