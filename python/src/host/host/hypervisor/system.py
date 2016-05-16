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

import abc

from six import with_metaclass


class DatastoreInaccessibleException(Exception):
    pass


class DatastoreInfo(object):
    def __init__(self, total, used):
        self.total = total
        self.used = used


class System(with_metaclass(abc.ABCMeta, object)):
    """Hypervisor host system interface."""

    @abc.abstractmethod
    def datastore_info(self, datastore_id):
        """Datastore info.

        :rtype: DatastoreInfo
        """
        pass

    @abc.abstractmethod
    def total_vmusable_memory_mb(self):
        """Total memory in MB that can be used to create Vms

        :rtype: int
        """
        pass

    @abc.abstractmethod
    def num_physical_cpus(self):
        """Total number of pCPUs on the hosts. Includes HT
        if HT is enabled.

        :rtype int
        """
        pass

    @abc.abstractmethod
    def host_consumed_memory_mb(self):
        """Total memory in MB that is currently used up in the host.

        :rtype: int
        """
        pass

    @abc.abstractmethod
    def host_version(self):
        """Version of the host.

        :rtype: string
        """
        pass
