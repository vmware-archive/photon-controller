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


class ServiceLocator(object):

    def __init__(self):
        self._services = {}

    def register(self, service_type, service):
        self._services[service_type] = service

    def get(self, service_type):
        if service_type not in self._services:
            if hasattr(service_type, "__name__"):
                service_type = service_type.__name__
            raise ValueError("service: '%s' was not registered" %
                             service_type)
        return self._services[service_type]

    def reset(self):
        self._services.clear()
