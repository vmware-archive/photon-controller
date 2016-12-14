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

from functools import wraps

from common import services
from common.exclusive_set import DuplicatedValue
from common.service_name import ServiceName


class ConcurrentVmOperation(Exception):
    pass


def lock_vm(func):

    @wraps(func)
    def f(self, request):
        locked_vms = services.get(ServiceName.LOCKED_VMS)

        try:
            locked_vms.add(request.vm_id)
            try:
                return func(self, request)
            finally:
                locked_vms.remove(request.vm_id)
        except DuplicatedValue:
            raise ConcurrentVmOperation("%s is locked" % request.vm_id)

    return f
