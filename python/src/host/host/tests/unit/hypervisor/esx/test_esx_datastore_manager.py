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

import unittest
from mock import patch
from mock import MagicMock

from hamcrest import *  # noqa

import common
from common.service_name import ServiceName
from gen.resource.constants import LOCAL_VMFS_TAG
from gen.resource.constants import SHARED_VMFS_TAG
from gen.resource.constants import NFS_TAG
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType as DSType
from host.hypervisor.esx.datastore_manager import EsxDatastoreManager
from host.hypervisor.esx.folder import DISK_FOLDER_NAME
from host.hypervisor.esx.folder import IMAGE_FOLDER_NAME
from host.hypervisor.esx.folder import TMP_IMAGE_FOLDER_NAME
from host.hypervisor.esx.folder import VM_FOLDER_NAME


class TestEsxDatastoreManager(unittest.TestCase):

    @patch("os.mkdir")
    def test_get_datastores(self, mkdir_mock):
        """ Test esx datastore manager with different datastore types.
        Verify the datastore types are correctly parsed and all the
        datastores are populated.
        """
        hypervisor = MagicMock()
        vim_client = MagicMock()

        dstags = MagicMock()
        dstags.get.return_value = []
        common.services.register(ServiceName.DATASTORE_TAGS, dstags)

        vim_client.get_datastore.side_effect = self._get_datastore
        hypervisor.vim_client = vim_client

        ds_list = ["datastore1", "datastore2", "datastore3",
                   "datastore4", "datastore5", "datastore6"]
        ds_manager = EsxDatastoreManager(hypervisor, ds_list, set(["datastore2"]))

        expected_call_args = []
        for ds in ds_list:
            for folder in [DISK_FOLDER_NAME, VM_FOLDER_NAME, IMAGE_FOLDER_NAME,
                           TMP_IMAGE_FOLDER_NAME]:
                expected_call_args.append('/vmfs/volumes/%s/%s' % (ds, folder))
        called_args = [c[0][0] for c in mkdir_mock.call_args_list]
        assert_that(called_args, equal_to(expected_call_args))

        assert_that(ds_manager.get_datastore_ids(), has_length(6))
        assert_that(ds_manager.get_datastore_ids(),
                    contains_inanyorder("id-1", "id-2", "id-3", "id-4",
                                        "id-5", "id-6"))

        assert_that(ds_manager.vm_datastores(), has_length(5))
        assert_that(ds_manager.vm_datastores(),
                    contains_inanyorder("id-1", "id-3", "id-4", "id-5",
                                        "id-6"))

        datastores = ds_manager.get_datastores()
        assert_that(datastores[0], is_(Datastore("id-1", "datastore1",
                                                 type=DSType.LOCAL_VMFS,
                                                 tags=[LOCAL_VMFS_TAG])))
        assert_that(datastores[1], is_(Datastore("id-2", "datastore2",
                                                 type=DSType.SHARED_VMFS,
                                                 tags=[SHARED_VMFS_TAG])))
        assert_that(datastores[2], is_(Datastore("id-3", "datastore3",
                                                 type=DSType.NFS_3,
                                                 tags=[NFS_TAG])))
        assert_that(datastores[3], is_(Datastore("id-4", "datastore4",
                                                 type=DSType.NFS_41,
                                                 tags=[NFS_TAG])))
        assert_that(datastores[4], is_(Datastore("id-5", "datastore5",
                                                 type=DSType.VSAN,
                                                 tags=[])))
        assert_that(datastores[5], is_(Datastore("id-6", "datastore6",
                                                 type=DSType.OTHER,
                                                 tags=[])))

        assert_that(ds_manager.image_datastores(), is_(set(["id-2"])))
        assert_that(ds_manager.datastore_type("id-1"),
                    is_(DSType.LOCAL_VMFS))
        assert_that(ds_manager.datastore_type("id-2"),
                    is_(DSType.SHARED_VMFS))
        assert_that(ds_manager.datastore_type("id-3"),
                    is_(DSType.NFS_3))
        assert_that(ds_manager.datastore_type("id-4"),
                    is_(DSType.NFS_41))
        assert_that(ds_manager.datastore_type("id-5"),
                    is_(DSType.VSAN))
        assert_that(ds_manager.datastore_type("id-6"),
                    is_(DSType.OTHER))

        # test normalize
        assert_that(ds_manager.normalize("id-1"), is_("id-1"))
        assert_that(ds_manager.normalize("datastore1"), is_("id-1"))

    def _get_datastore(self, name):
        local = None
        if name == "datastore1":
            url = "/vmfs/volumes/id-1"
            type = "VMFS"
            local = True
        elif name == "datastore2":
            url = "/vmfs/volumes/id-2"
            type = "VMFS"
            local = False
        elif name == "datastore3":
            url = "/vmfs/volumes/id-3"
            type = "NFS"
        elif name == "datastore4":
            url = "/vmfs/volumes/id-4"
            type = "NFSV41"
        elif name == "datastore5":
            url = "/vmfs/volumes/id-5"
            type = "vsan"
        elif name == "datastore6":
            url = "/vmfs/volumes/id-6"
            type = "VFFS"
        else:
            return None

        datastore = MagicMock()
        datastore.name = name
        datastore.summary.type = type
        datastore.info.url = url
        datastore.info.vmfs.local = local
        return datastore
