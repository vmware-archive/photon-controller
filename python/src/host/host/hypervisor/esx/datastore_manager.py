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
import logging
import threading

from common.lock import locked
from gen.resource.constants import LOCAL_VMFS_TAG
from gen.resource.constants import SHARED_VMFS_TAG
from gen.resource.constants import NFS_TAG
from gen.resource.constants import VSAN_TAG
from gen.resource.ttypes import HostServiceTicket, Datastore, DatastoreType
from host.hypervisor.datastore_manager import DatastoreManager
from host.hypervisor.hypervisor import UpdateListener


class EsxDatastoreManager(DatastoreManager, UpdateListener):

    def __init__(self, hypervisor, datastores, image_datastores):
        self.lock = threading.Lock()
        self.logger = logging.getLogger(__name__)
        self._hypervisor = hypervisor
        self._configured_datastores = datastores
        self._configured_image_datastores = image_datastores
        self._initialize_datastores()

    @locked
    def _initialize_datastores(self):
        self.initialized = False

        # host_datastores is the list of datastores reported by hostd
        host_datastores = set()
        for ds in self._hypervisor.vim_client.get_all_datastores():
            datastore = self._to_thrift_datastore(ds)
            if datastore:
                host_datastores.add(datastore)

        # vm_datastores is the intersection of _configured_datastores
        # (aka ALLOWED_DATASTORES from deployer yml) and host_datastores
        if self._configured_datastores:
            vm_datastores = set([ds for ds in host_datastores
                                 if ds.name in self._configured_datastores or
                                 ds.id in self._configured_datastores])
        else:
            vm_datastores = host_datastores

        # image_datastores is the intersection of _configured_image_datastores
        # (aka IMAGE_DATASTORES from deployer yml) and host_datastores
        image_ds_names = set([ds["name"] for ds in
                              self._configured_image_datastores])
        image_datastores = set([ds for ds in host_datastores
                                if ds.name in image_ds_names or
                                ds.id in image_ds_names])

        # combined_datastores is the union of vm_datastores and image_datastores
        combined_datastores = vm_datastores | image_datastores

        # populate class members
        self._datastores = set()
        self._image_datastores = set()
        self._datastore_id_to_name_map = {}
        for ds in combined_datastores:
            self._datastores.add(ds)
            if ds in image_datastores:
                self._image_datastores.add(ds)
            self._datastore_id_to_name_map[ds.id] = ds.name

        # make sure there is at least one datastore for cloud VMs. Here we
        # can't simply throw an exception since the agent needs to continue
        # running even if the current datastore configuration is invalid
        # so that the deployer can provision the agent.
        image_ds_for_vms = any([ds["used_for_vms"]
                                for ds in self._configured_image_datastores])
        if not vm_datastores and not image_ds_for_vms:
            self.logger.critical("Datastore(s) %s not found in %s, %s" % (
                                 self._configured_datastores, host_datastores,
                                 self._configured_image_datastores))
            return

        # make sure there is at least one image datastore.
        if not self._image_datastores:
            self.logger.critical("Image datastore(s) %s not found in %s" % (
                                 self._configured_image_datastores,
                                 host_datastores))
            return

        # mark initialize complete, and logging
        self.initialized = True
        self.logger.info("EsxDatastoreManager._datastores: %s",
                         self._datastores)
        self.logger.info("EsxDatastoreManager._image_datastores: %s",
                         self._image_datastores)
        self.logger.info("EsxDatastoreManager._datastore_id_to_name_map: %s",
                         self._datastore_id_to_name_map)

    @locked
    def get_datastore_ids(self):
        return [ds.id for ds in self._datastores]

    @locked
    def get_datastores(self):
        return copy.copy(self._datastores)

    @locked
    def image_datastores(self):
        return [ds.id for ds in self._image_datastores]

    def datastore_nfc_ticket(self, datastore_name):
        ticket = self._hypervisor.vim_client.get_nfc_ticket_by_ds_name(datastore_name)

        return HostServiceTicket(host=ticket.host, port=ticket.port,
                                 ssl_thumbprint=ticket.sslThumbprint,
                                 service_type=ticket.service,
                                 service_version=ticket.serviceVersion,
                                 session_id=ticket.sessionId)

    @locked
    def datastore_name(self, datastore_id):
        return self._datastore_id_to_name_map[datastore_id]

    @locked
    def datastore_info(self, datastore_id):
        return self._hypervisor.system.datastore_info(self._datastore_id_to_name_map[datastore_id])

    def _to_thrift_datastore(self, ds):
        """ From vim.Datastore to gen.resource.ttypes.Datastore
        """
        # Ignore this datastore if it has no url
        if not ds.info.url:
            self.logger.critical("Ignoring %s because info.url of this datastore is empty" % ds.name)
            return None

        uuid = ds.info.url.rsplit("/", 1)[1]
        name = ds.name
        type = ds.summary.type
        system_tag = None
        tags = []

        if type == "VMFS":
            # if 'local' property is not available then we fall back to old
            # way of getting to know local/shared access which was used in API versions before 5.5.
            if hasattr(ds.info.vmfs, 'local'):
                shared = not ds.info.vmfs.local
            else:
                shared = ds.summary.multipleHostAccess

            if not shared:
                thrift_type = DatastoreType.LOCAL_VMFS
                system_tag = LOCAL_VMFS_TAG
            else:
                thrift_type = DatastoreType.SHARED_VMFS
                system_tag = SHARED_VMFS_TAG
        elif type == "NFS":
            thrift_type = DatastoreType.NFS_3
            system_tag = NFS_TAG
        elif type == "NFSV41":
            thrift_type = DatastoreType.NFS_41
            system_tag = NFS_TAG
        elif type == "vsan":
            thrift_type = DatastoreType.VSAN
            system_tag = VSAN_TAG
        else:
            thrift_type = DatastoreType.OTHER

        # Set datastore tags
        if system_tag:
            tags.append(system_tag)

        return Datastore(uuid, name, thrift_type, frozenset(tags))

    def datastores_updated(self):
        """vim client callback for datastore change"""
        self._initialize_datastores()

    def networks_updated(self):
        """vim client callback for network change"""
        pass

    def virtual_machines_updated(self):
        """vim client callback for vm change"""
        pass
