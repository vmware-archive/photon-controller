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
import copy
import uuid

import common
from common.service_name import ServiceName

from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType
from gen.resource.ttypes import HostServiceTicket

from host.hypervisor.datastore_manager import DatastoreManager


class FakeDatastoreManager(DatastoreManager):

    def __init__(self, system, datastores, image_datastore):
        self._system = system
        self._datastores = []
        self._datastore_names = datastores
        self._datastore_id_to_name = {}
        self._image_datastore = None

        for name in datastores:
            ds_id = str(uuid.uuid5(uuid.NAMESPACE_DNS, str(name)))
            ds = Datastore(ds_id, name, DatastoreType.EXT3, [])
            self._datastores.append(ds)
            self._datastore_id_to_name[ds_id] = name
            if name == image_datastore:
                self._image_datastore = ds_id
            self._system.add_datastore_gb(ds_id)

    def get_datastore_ids(self):
        return [ds.id for ds in self._datastores]

    def get_datastores(self):
        return copy.copy(self._datastores)

    def image_datastores(self):
        if not self._image_datastore:
            return set()
        else:
            return set([self._image_datastore])

    def datastore_nfc_ticket(self, datastore_name):
        return HostServiceTicket(port=902, service_type="nfc",
                                 service_version="1.1",
                                 session_id="52 b9 d5 63 37 8d 20 f0-" +
                                            "7e 83 60 39 ea 72 da 4b",
                                 ssl_thumbprint="38:D8:BD:62:78:79:CC:36:06:" +
                                                "12:DB:53:0D:03:25:EC:5F:8C:" +
                                                "D4:BE")

    def datastore_info(self, datastore_id):
        return self._system.datastore_info(datastore_id)

    def datastore_name(self, datastore_id):
        return self._datastore_id_to_name[datastore_id]
