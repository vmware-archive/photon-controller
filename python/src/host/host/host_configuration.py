# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.  You may obtain a copy
# of the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, without
# warranties or conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the
# License for then specific language governing permissions and limitations
# under the License.


class HostConfiguration():
    def __init__(self, availability_zone=None, scheduler=None,
                 roles=None, vsan_cluster_config=None, host_id=None):
        self.availability_zone = availability_zone
        self.scheduler = scheduler
        self.roles = roles
        self.vsan_cluster_config = vsan_cluster_config
        self.host_id = host_id
