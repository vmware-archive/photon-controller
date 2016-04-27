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

from gen.resource.constants import LOCAL_VMFS_TAG
from gen.resource.constants import SHARED_VMFS_TAG
from gen.resource.constants import NFS_TAG
from gen.resource.constants import VSAN_TAG
from gen.resource.ttypes import Datastore
from gen.resource.ttypes import DatastoreType as DSType
from host.hypervisor.esx.datastore_manager import EsxDatastoreManager


class TestEsxDatastoreManager(unittest.TestCase):

    def test_get_datastores(self):
        """ Test esx datastore manager with different datastore types.
        Verify the datastore types are correctly parsed and all the
        datastores are populated.
        """
        hypervisor = MagicMock()
        host_client = MagicMock()

        host_client.get_all_datastores.return_value = self.get_datastore_mock([
            # name, url, type, local
            ["datastore1", "/vmfs/volumes/id-1", "VMFS", True],
            ["datastore2", "/vmfs/volumes/id-2", "VMFS", False],
            ["datastore3", "/vmfs/volumes/id-3", "NFS", None],
            ["datastore4", "/vmfs/volumes/id-4", "NFSV41", None],
            ["datastore5", "/vmfs/volumes/id-5", "vsan", None],
            ["datastore6", "/vmfs/volumes/id-6", "VFFS", None],
        ])

        hypervisor.host_client = host_client

        ds_list = ["datastore1", "datastore2", "datastore3",
                   "datastore4", "datastore5", "datastore6"]
        image_ds = [{"name": "datastore2", "used_for_vms": False}]
        ds_manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)

        assert_that(ds_manager.get_datastore_ids(),
                    contains_inanyorder("id-1", "id-2", "id-3", "id-4",
                                        "id-5", "id-6"))

        assert_that(ds_manager.vm_datastores(),
                    contains_inanyorder("id-1", "id-3", "id-4", "id-5",
                                        "id-6"))

        datastores = ds_manager.get_datastores()
        assert_that(datastores, contains_inanyorder(
            Datastore("id-1", "datastore1", type=DSType.LOCAL_VMFS,
                      tags=set([LOCAL_VMFS_TAG])),
            Datastore("id-2", "datastore2", type=DSType.SHARED_VMFS,
                      tags=set([SHARED_VMFS_TAG])),
            Datastore("id-3", "datastore3", type=DSType.NFS_3,
                      tags=set([NFS_TAG])),
            Datastore("id-4", "datastore4", type=DSType.NFS_41,
                      tags=set([NFS_TAG])),
            Datastore("id-5", "datastore5", type=DSType.VSAN, tags=set([VSAN_TAG])),
            Datastore("id-6", "datastore6", type=DSType.OTHER, tags=set())))

        assert_that(ds_manager.image_datastores(), is_(["id-2"]))
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

    @patch("os.mkdir")
    def test_single_datastore(self, mkdir_mock):
        """Test that datastore manager works with a single datastore."""
        hypervisor = MagicMock()
        host_client = MagicMock()

        host_client.get_all_datastores.return_value = self.get_datastore_mock([
            # name, url, type, local
            ["datastore1", "/vmfs/volumes/id-1", "VMFS", True],
        ])
        hypervisor.host_client = host_client

        # No valid datastore. One image datastore for cloud VMs.
        ds_list = []
        image_ds = [{"name": "datastore1", "used_for_vms": True}]
        ds_manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)
        assert_that(ds_manager.initialized, is_(True))
        assert_that(ds_manager.get_datastore_ids(), is_(["id-1"]))
        assert_that(ds_manager.vm_datastores(), is_([]))
        assert_that(ds_manager.image_datastores(), is_(["id-1"]))

        # No valid datastore. No image datastore for cloud VMs.
        ds_list = ["bad-ds"]
        image_ds = [{"name": "datastore1", "used_for_vms": False}]
        ds_manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)
        assert_that(ds_manager.initialized, is_(False))

    @patch("os.mkdir")
    def test_multiple_image_datastores(self, mkdir_mock):
        """Test that datastore manager works with multiple image datastores."""
        host_client = MagicMock()
        host_client.get_all_datastores.return_value = self.get_datastore_mock([
            ["datastore1", "/vmfs/volumes/id-1", "VMFS", True],
            ["datastore2", "/vmfs/volumes/id-2", "VMFS", True],
            ["datastore3", "/vmfs/volumes/id-3", "VMFS", True],
        ])
        hypervisor = MagicMock()
        hypervisor.host_client = host_client

        ds_list = ["datastore1"]
        image_ds = [
            {"name": "datastore2", "used_for_vms": True},
            {"name": "datastore3", "used_for_vms": False},
        ]
        ds_manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)
        assert_that(ds_manager.get_datastore_ids(),
                    contains_inanyorder("id-1", "id-2", "id-3"))
        assert_that(ds_manager.vm_datastores(), is_(["id-1"]))
        assert_that(ds_manager.image_datastores(),
                    contains_inanyorder("id-2", "id-3"))
        assert_that(ds_manager.initialized, is_(True))

    @patch("os.mkdir")
    def test_nonexistent_datastores(self, mkdir_mock):
        """Test that non-existent datastore get filtered out."""
        host_client = MagicMock()
        host_client.get_all_datastores.return_value = self.get_datastore_mock([
            ["datastore1", "/vmfs/volumes/id-1", "VMFS", True],
            ["datastore2", "/vmfs/volumes/id-2", "VMFS", True],
            ["datastore3", "/vmfs/volumes/id-3", "VMFS", True],
        ])
        hypervisor = MagicMock()
        hypervisor.host_client = host_client

        ds_list = ["datastore1", "bad-datastore1"]
        image_ds = [
            {"name": "datastore2", "used_for_vms": True},
            {"name": "datastore3", "used_for_vms": False},
            {"name": "bad-datastores2", "used_for_vms": False},
        ]
        manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)
        assert_that(manager.get_datastore_ids(),
                    contains_inanyorder("id-1", "id-2", "id-3"))
        assert_that(manager.vm_datastores(), is_(["id-1"]))
        assert_that(manager.image_datastores(),
                    contains_inanyorder("id-2", "id-3"))
        assert_that(manager.initialized, is_(True))

    @patch("os.mkdir")
    def test_empty_url_datastores(self, mkdir_mock):
        """Test that datastores with empty url get filtered out."""
        host_client = MagicMock()
        host_client.get_all_datastores.return_value = self.get_datastore_mock([
            ["datastore1", "", "VMFS", True],
            ["datastore2", None, "VMFS", True],
            ["datastore3", "/vmfs/volumes/id-3", "VMFS", True],
            ])
        hypervisor = MagicMock()
        hypervisor.host_client = host_client

        ds_list = ["datastore1", "datastore2", "datastore3"]
        image_ds = [{"name": "datastore3", "used_for_vms": True}]
        manager = EsxDatastoreManager(hypervisor, ds_list, image_ds)
        assert_that(manager.get_datastore_ids(), contains_inanyorder("id-3"))

    def get_datastore_mock(self, datastores):
        result = []
        for datastore in datastores:
            mock = MagicMock()
            mock.name = datastore[0]
            mock.info.url = datastore[1]
            mock.summary.type = datastore[2]
            mock.info.vmfs.local = datastore[3]
            result.append(mock)
        return result
