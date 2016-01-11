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

import common
from common.lock import locked
from common.service_name import ServiceName
from gen.resource.constants import LOCAL_VMFS_TAG
from gen.resource.constants import SHARED_VMFS_TAG
from gen.resource.constants import NFS_TAG
from gen.resource.ttypes import HostServiceTicket, Datastore, DatastoreType
from host.hypervisor.datastore_manager import DatastoreManager
from host.hypervisor.esx.disk_manager import datastore_mkdirs_vmomi
from host.hypervisor.hypervisor import UpdateListener


class EsxDatastoreManager(DatastoreManager, UpdateListener):

    def __init__(self, hypervisor, datastores, image_datastores):
        self.lock = threading.Lock()
        self.logger = logging.getLogger(__name__)
        self._hypervisor = hypervisor
        self._configured_datastores = datastores
        self._configured_image_datastores = image_datastores
        self.ds_user_tags = common.services.get(ServiceName.DATASTORE_TAGS)
        self._initialize_datastores()

    @locked
    def _initialize_datastores(self):
        self.initialized = False

        # Initialize datastores and image_datastores. The provision request
        # can specify both datastore names and IDs.
        datastores = set([self._to_thrift_datastore(ds) for ds in
                          self._hypervisor.vim_client.get_all_datastores()])
        if self._configured_datastores:
            vm_datastores = set([ds for ds in datastores
                                if ds.name in self._configured_datastores or
                                ds.id in self._configured_datastores])
        else:
            vm_datastores = datastores
        image_datastores = set([ds["name"] for ds in
                               self._configured_image_datastores])
        self._image_datastores = set([ds for ds in datastores
                                      if ds.name in image_datastores or
                                      ds.id in image_datastores])
        self._datastores = vm_datastores | self._image_datastores

        self._datastore_id_to_name_map = {}
        for ds in self._datastores:
            try:
                datastore_mkdirs_vmomi(self._hypervisor.vim_client, ds.name)
                self._datastore_id_to_name_map[ds.id] = ds.name
            except:
                self.logger.exception("Failed to initialize %s" % ds)

        # make sure there is at least one datastore for cloud VMs. Here we
        # can't simply throw an exception since the agent needs to continue
        # running even if the current datastore configuration is invalid
        # so that the deployer can provision the agent.
        image_ds_for_vms = any([ds["used_for_vms"]
                                for ds in self._configured_image_datastores])
        if not vm_datastores and not image_ds_for_vms:
            self.logger.critical("Datastore(s) %s not found in %s, %s" % (
                                 self._configured_datastores, datastores,
                                 self._configured_image_datastores))
            return

        # make sure there is at least one image datastore.
        if not self._image_datastores:
            self.logger.critical("Image datastore(s) %s not found in %s" % (
                                 self._configured_image_datastores,
                                 datastores))
            return
        self.initialized = True

    @locked
    def get_datastore_ids(self):
        return [ds.id for ds in self._datastores]

    @locked
    def get_datastores(self):
        # Extend user defined tags into datastore's tags list
        datastores = copy.copy(self._datastores)
        user_tags = self.ds_user_tags.get()
        for ds in datastores:
            if ds.id in user_tags:
                # ds.tags are builtin tags saved while initiating.
                ds.tags = list(set(ds.tags).union(user_tags[ds.id]))
        return datastores

    @locked
    def image_datastores(self):
        return [ds.id for ds in self._image_datastores]

    def datastore_nfc_ticket(self, datastore_name):
        ticket = self._hypervisor.vim_client.get_nfc_ticket_by_ds_name(
            datastore_name)

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
        return self._hypervisor.system.datastore_info(
            self._datastore_id_to_name_map[datastore_id])

    def _to_thrift_datastore(self, ds):
        """ From vim.Datastore to gen.resource.ttypes.Datastore
        """
        uuid = ds.info.url.rsplit("/", 1)[1]
        name = ds.name
        type = ds.summary.type
        system_tag = None
        tags = []

        if type == "VMFS":
            if ds.info.vmfs.local:
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
        else:
            thrift_type = DatastoreType.OTHER

        # Set datastore tags
        if system_tag:
            tags.append(system_tag)

        return Datastore(uuid, name, thrift_type, tags)

    def datastores_updated(self):
        """vim client callback for datastore change"""
        self._initialize_datastores()

    def networks_updated(self):
        """vim client callback for network change"""
        pass

    def virtual_machines_updated(self):
        """vim client callback for vm change"""
        pass
