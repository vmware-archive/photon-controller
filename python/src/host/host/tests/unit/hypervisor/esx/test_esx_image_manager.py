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

import errno
import os
import tempfile
import time
import unittest

from hamcrest import *  # noqa
from host.hypervisor.placement_manager import NoSuchResourceException
from mock import MagicMock
from mock import patch
from mock import call
from nose_parameterized import parameterized
from nose.tools import raises
from pyVmomi import vim

from common import file_util
from common import services
from common.service_name import ServiceName
from gen.resource.ttypes import DatastoreType
from host.hypervisor.disk_manager import DiskAlreadyExistException
from host.hypervisor.esx.vm_config import IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.esx.vm_config import compond_path_join
from host.hypervisor.esx.vm_config import TMP_IMAGE_FOLDER_NAME_PREFIX
from host.hypervisor.image_manager import DirectoryNotFound
from host.hypervisor.image_manager import ImageNotFoundException

from host.hypervisor.esx.image_manager import EsxImageManager, GC_IMAGE_FOLDER
from host.hypervisor.esx.vim_client import VimClient
from host.hypervisor.esx.vm_config import METADATA_FILE_EXT


class TestEsxImageManager(unittest.TestCase):
    """Image Manager tests."""

    # We can use even more unit test coverage of the image manager here

    @patch.object(VimClient, "acquire_credentials")
    @patch.object(VimClient, "update_cache")
    @patch("pysdk.connect.Connect")
    def setUp(self, connect, update, creds):
        creds.return_value = ["username", "password"]
        self.vim_client = VimClient(auto_sync=False)
        self.ds_manager = MagicMock()
        services.register(ServiceName.AGENT_CONFIG, MagicMock())
        self.image_manager = EsxImageManager(self.vim_client, self.ds_manager)

    def tearDown(self):
        self.vim_client.disconnect(wait=True)

    @patch("os.path.isdir", return_value=False)
    @patch("os.makedirs", side_effect=OSError)
    def test_make_image_dir(self, _makedirs, _isdir):
        path = "/vmfs/volumes/ds/image_fake_iid"
        self.assertRaises(
            OSError, self.image_manager._make_image_dir, "ds", "fake_iid")
        _isdir.assert_called_once_with(path)
        self.assertEqual(
            _makedirs.call_count, EsxImageManager.NUM_MAKEDIRS_ATTEMPTS)
        for i in range(0, EsxImageManager.NUM_MAKEDIRS_ATTEMPTS):
            self.assertEqual(_makedirs.call_args_list[i][0], (path,))

    @patch(
        "host.hypervisor.esx.image_manager.EsxImageManager.reap_tmp_images")
    def test_periodic_reaper(self, mock_reap):
        """ Test that the we invoke the image reaper periodically """
        image_manager = EsxImageManager(self.vim_client, self.ds_manager)
        image_manager.monitor_for_cleanup(reap_interval=0.1)

        self.assertFalse(image_manager._image_reaper is None)

        retry = 0
        while mock_reap.call_count < 2 and retry < 10:
            time.sleep(0.1)
            retry += 1
        image_manager.cleanup()
        assert_that(mock_reap.call_count, greater_than(1))
        assert_that(retry, is_not(10), "reaper cleanup not called repeatedly")

    @parameterized.expand([
        (True, ),
        (False, )
    ])
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("host.hypervisor.esx.vm_config.os_datastore_path")
    def test_reap_tmp_images(self, _allow_grace_period, _os_datastore_path,
                             _uuid):
        """ Test that stray images are found and deleted by the reaper """

        def _fake_ds_folder(datastore, folder):
            return "%s/%s" % (datastore, folder)

        ds = MagicMock()
        ds.id = "dsid"
        ds.type = DatastoreType.EXT3

        # In a random transient directory, set up a directory to act as the
        # tmp images folder and to contain a stray image folder with a file.
        tmpdir = file_util.mkdtemp(delete=True)
        tmp_ds_dir = os.path.join(tmpdir, ds.id)
        os.mkdir(tmp_ds_dir)
        tmp_image_dir = os.path.join(tmp_ds_dir, compond_path_join(TMP_IMAGE_FOLDER_NAME_PREFIX, "stray_image"))
        os.mkdir(tmp_image_dir)
        (fd, path) = tempfile.mkstemp(prefix='strayimage_', dir=tmp_image_dir)

        self.assertTrue(os.path.exists(path))

        def _fake_os_datastore_path(datastore, folder):
            return os.path.join(tmpdir, _fake_ds_folder(datastore, folder))

        _os_datastore_path.side_effect = _fake_os_datastore_path

        ds_manager = MagicMock()
        ds_manager.get_datastores.return_value = [ds]
        image_manager = EsxImageManager(self.vim_client, ds_manager)
        if not _allow_grace_period:
            image_manager.REAP_TMP_IMAGES_GRACE_PERIOD = 0.0
            time.sleep(0.1)
        image_manager.reap_tmp_images()

        if _allow_grace_period:
            # verify stray image is not deleted due to grace period
            self.assertTrue(os.path.exists(path))
        else:
            # verify stray image is deleted
            self.assertFalse(os.path.exists(path))

    @patch("os.path.isdir")
    @patch("os.makedirs")
    def test_vmdk_mkdir_eexist(self, _makedirs, _isdir):
        eexist = OSError()
        eexist.errno = errno.EEXIST
        _makedirs.side_effect = eexist
        _isdir.side_effect = (False,  # dest image dir missing
                              True)   # dest image dir is created

        self.image_manager._make_image_dir("ds", "fake_iid")
        _isdir.assert_called("/vmfs/volumes/ds/image_fake_iid")

    @patch("pysdk.task.WaitForTask")
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("os.path.exists")
    @patch("shutil.copy")
    @patch.object(VimClient, "move_file")
    @patch.object(EsxImageManager, "_manage_disk")
    @patch.object(EsxImageManager, "_get_datastore_type", return_value=DatastoreType.EXT3)
    @patch.object(EsxImageManager, "_check_image_repair", return_value=False)
    @patch.object(EsxImageManager, "check_and_validate_image", return_value=False)
    @patch.object(EsxImageManager, "_create_image_timestamp_file")
    @patch("host.hypervisor.esx.image_manager.FileBackedLock")
    def test_copy_image(self, _flock, _create_image_timestamp, check_image, _check_image_repair,
                        _get_ds_type, _manage_disk, _mv_dir, _copy, _exists, _uuid, _wait_for_task):
        _exists.side_effect = (True,  # tmp_dir exists
                               True,  # dest image vmdk missing
                               True)  # source meta file present

        self.image_manager.copy_image("ds1", "foo", "ds2", "bar")

        os_path_prefix1 = '/vmfs/volumes/ds1'
        os_path_prefix2 = '/vmfs/volumes/ds2'
        ds_tmp_path_prefix = '[] /vmfs/volumes/ds2'

        assert_that(_copy.call_count, equal_to(1))
        _copy.assert_has_calls([
            call('%s/image_foo/foo.%s' % (os_path_prefix1, METADATA_FILE_EXT),
                 '/vmfs/volumes/ds2/tmp_image_fake_id/bar.%s' %
                 METADATA_FILE_EXT),
        ])

        ds_path_prefix1 = '[] ' + os_path_prefix1

        expected_tmp_disk_ds_path = '%s/tmp_image_fake_id/bar.vmdk' % (ds_tmp_path_prefix)

        _vd_spec = _manage_disk.call_args_list[0][1]['destSpec']

        self.assertEqual("thin", _vd_spec.diskType)
        self.assertEqual("lsiLogic", _vd_spec.adapterType)

        copy_call = call(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                         sourceName='%s/image_foo/foo.vmdk' % ds_path_prefix1,
                         destName=expected_tmp_disk_ds_path,
                         destSpec=_vd_spec)
        expected_vim_calls = [copy_call]
        self.assertEqual(expected_vim_calls, _manage_disk.call_args_list)

        _mv_dir.assert_called_once_with('/vmfs/volumes/ds2/tmp_image_fake_id',
                                        '%s/image_bar' % os_path_prefix2)

        _create_image_timestamp.assert_called_once_with(
            "/vmfs/volumes/ds2/tmp_image_fake_id")

    @patch("pysdk.task.WaitForTask")
    @patch("uuid.uuid4", return_value="fake_id")
    @patch("os.path.exists")
    @patch("os.makedirs")
    @patch("shutil.copy")
    @patch.object(EsxImageManager, "_manage_disk")
    @patch.object(EsxImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(EsxImageManager, "check_image", return_value=False)
    @patch.object(EsxImageManager, "_create_image_timestamp_file")
    def test_create_tmp_image(self, _create_image_timestamp, check_image, _get_ds_type,
                              _manage_disk, _copy, _makedirs, _exists, _uuid, _wait_for_task):

        # Common case is the same as the one covered by test_copy_image.

        # Check that things work when the src metadata file doesn't exist.
        _exists.side_effect = (False, False, True)
        ds_path_prefix1 = '[] /vmfs/volumes/ds1'
        expected_tmp_disk_ds_path = \
            "[] /vmfs/volumes/ds2/tmp_image_fake_id/bar.vmdk"
        self.image_manager._copy_to_tmp_image("ds1", "foo", "ds2", "bar")
        # Verify that we don't copy the metadata file.
        self.assertFalse(_copy.called)

        # Verify that we copy the disk correctly
        _vd_spec = _manage_disk.call_args_list[0][1]['destSpec']

        self.assertEqual("thin", _vd_spec.diskType)
        self.assertEqual("lsiLogic", _vd_spec.adapterType)
        copy_call = call(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                         sourceName='%s/image_foo/foo.vmdk' % ds_path_prefix1,
                         destName=expected_tmp_disk_ds_path,
                         destSpec=_vd_spec)
        expected_vim_calls = [copy_call]
        self.assertEqual(expected_vim_calls, _manage_disk.call_args_list)

        # check that we return an IO error if the copy of metadata fails.
        _copy.side_effect = IOError
        _exists.side_effect = (True, True)
        _manage_disk.reset_mock()
        _flock.reset_mock()
        self.assertRaises(IOError, self.image_manager._copy_to_tmp_image,
                          "ds1", "foo", "ds2", "bar")
        self.assertFalse(_manage_disk.called)
        _flock.assert_called_once_with("/vmfs/volumes/ds2/tmp_image_fake_id",
                                       DatastoreType.EXT3)
        _create_image_timestamp.assert_called_once_with(
            "/vmfs/volumes/ds2/tmp_image_fake_id")

    @patch("os.path.exists", return_value=True)
    @patch("os.makedirs")
    @patch("shutil.rmtree")
    @patch("shutil.move")
    @patch.object(EsxImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(EsxImageManager, "_check_image_repair", return_value=True)
    @patch("host.hypervisor.esx.image_manager.FileBackedLock")
    @raises(DiskAlreadyExistException)
    def test_move_image(self, _flock, check_image, _get_ds_type, _mv_dir,
                        _rmtree, _makedirs, _exists):
        # Common case is covered in test_copy_image.

        # check that if destination image directory exists we don't call move
        # and just bail after removing the tmp dir
        _rmtree.reset_mock()
        _mv_dir.reset_mock()
        expected_tmp_disk_folder = '/vmfs/volumes/ds2/tmp_images/bar'
        expected_rm_calls = [call(expected_tmp_disk_folder)]
        self.image_manager._move_image("foo", "ds1", expected_tmp_disk_folder)
        self.assertEqual(expected_rm_calls, _rmtree.call_args_list)
        _makedirs.assert_called_once_with('/vmfs/volumes/ds1/images/fo')
        _flock.assert_called_once_with('/vmfs/volumes/ds1/image_foo',
                                       DatastoreType.EXT3, 3)

    @parameterized.expand([
        (True, ),
        (False, )
    ])
    @patch("os.path.exists")
    @patch.object(EsxImageManager, "_get_datastore_type",
                  return_value=DatastoreType.EXT3)
    @patch.object(EsxImageManager, "_create_image_timestamp_file")
    @patch.object(EsxImageManager, "_delete_renamed_image_timestamp_file")
    @patch("host.hypervisor.esx.image_manager.FileBackedLock")
    def test_validate_existing_image(self,
                                     create,
                                     _flock,
                                     _delete_renamed_timestamp_file,
                                     _create_timestamp_file,
                                     _get_ds_type,
                                     _path_exists):
        self._create_image_timestamp_file = create
        _path_exists.side_effect = self._local_os_path_exists
        _disk_folder = '/vmfs/volumes/ds1/image_foo'
        self.image_manager._check_image_repair("foo", "ds1")

        if create:
            _create_timestamp_file.assert_called_once_with(_disk_folder)
            _delete_renamed_timestamp_file.assert_called_once()
        else:
            assert not _create_timestamp_file.called
            assert not _delete_renamed_timestamp_file.called

    def _local_os_path_exists(self, pathname):
        if not self._create_image_timestamp_file:
            return True
        if pathname.endswith(EsxImageManager.IMAGE_TIMESTAMP_FILE_NAME):
            return False
        else:
            return True

    @patch.object(EsxImageManager, "_clean_gc_dir")
    @patch.object(EsxImageManager, "_gc_image_dir")
    @patch.object(EsxImageManager, "_lock_data_disk")
    @patch.object(EsxImageManager, "create_image_tombstone")
    @patch.object(EsxImageManager, "check_image_dir")
    def test_delete(self, check_image_dir, create_image_tombstone,
                    lock_data_disk, gc_image_dir, clean_gc_dir):

        # Test successful delete
        check_image_dir.return_value = True
        self.image_manager.delete_image("ds1", "foo", 0, False)
        check_image_dir.assert_called_with("foo", "ds1")
        create_image_tombstone.assert_called_with("ds1", "foo")

        # Test successful delete with force option
        self.image_manager.delete_image("ds1", "foo", 0, True)
        check_image_dir.assert_called_with("foo", "ds1")
        create_image_tombstone.assert_called_with("ds1", "foo")
        lock_data_disk.assert_called_with("ds1", "foo")
        gc_image_dir.assert_called_with("ds1", "foo")
        clean_gc_dir.assert_called()

        # Test image not found
        check_image_dir.return_value = False
        self.assertRaises(ImageNotFoundException,
                          self.image_manager.delete_image,
                          "ds1", "foo", 0, False)

    @patch("host.hypervisor.esx.image_manager.os_vmdk_path")
    @patch("host.hypervisor.esx.image_manager.os_datastore_path")
    def test_gc_image_dir(self, dst_path, src_path):
        """ Test that we move the directory correctly to the GC location """
        src_dir = file_util.mkdtemp(delete=True)
        dst_dir = file_util.mkdtemp(delete=True)
        src_path.return_value = os.path.join(src_dir, "test.vmdk")
        dst_path.return_value = dst_dir

        self.image_manager._gc_image_dir("ds1", "foo")
        uuid_dir = os.path.join(dst_dir, os.listdir(dst_dir)[0])

        # Verify the src directory has been moved into the garbage dir.
        self.assertEqual(os.listdir(uuid_dir), [os.path.basename(src_dir)])

        src_path.assert_called_once_with("ds1", "foo", IMAGE_FOLDER_NAME_PREFIX)
        dst_path.assert_called_once_with("ds1", GC_IMAGE_FOLDER)

    def test_image_path(self):
        image_path = "/vmfs/volumes/ds/image_ttylinux/ttylinux.vmdk"
        ds = self.image_manager.get_datastore_id_from_path(image_path)
        image = self.image_manager.get_image_id_from_path(image_path)
        self.assertEqual(ds, "ds")
        self.assertEqual(image, "ttylinux")

    @patch("host.hypervisor.esx.image_manager.os_vmdk_flat_path")
    @patch("host.hypervisor.esx.image_manager.os.remove")
    def test_lock_data_disk(self, mock_rm, vmdk_flat_path):
        """ Test acquisition of the lock on the flat file. """
        vmdk_flat_path.return_value = "fake_f_name"
        self.assertTrue(self.image_manager._lock_data_disk("ds1", "foo"))
        vmdk_flat_path.assert_called_once_with("ds1", "foo")
        mock_rm.side_effect = OSError
        self.assertFalse(self.image_manager._lock_data_disk("ds1", "foo"))

    @patch.object(EsxImageManager, "_get_datastore_type")
    def test_create_image(self, _get_ds_type):
        image_id = "image_id"
        datastore_id = "ds1"
        _get_ds_type.side_effect = (DatastoreType.LOCAL_VMFS, DatastoreType.VSAN)

        tmp_image_path = self.image_manager.create_image(image_id, datastore_id)
        prefix = "[] /vmfs/volumes/%s/tmp_image_" % datastore_id
        self.assertTrue(tmp_image_path.startswith(prefix))

        tmp_image_path = self.image_manager.create_image(image_id, datastore_id)
        prefix = "[] /vmfs/volumes/%s/image_%s/tmp_image_" % (datastore_id, image_id)
        self.assertTrue(tmp_image_path.startswith(prefix))

    @patch.object(EsxImageManager, "_move_image")
    @patch.object(EsxImageManager, "_create_image_timestamp_file_from_ids")
    @patch("os.path.exists")
    def test_finalize_image(self, _exists, _create_timestamp, move_image):

        # Happy path verify move is called with the right args.
        _exists.side_effect = ([True])
        self.image_manager.finalize_image("ds1", "[] /vmfs/volumes/ds1/foo", "img_1")
        move_image.assert_called_once_with('img_1', 'ds1', '/vmfs/volumes/ds1/foo')
        _create_timestamp.assert_called_once_with("ds1", "img_1")

    @patch.object(EsxImageManager, "finalize_image")
    @patch.object(EsxImageManager, "_manage_disk")
    @patch("os.path.exists", return_value=True)
    def test_create_image_with_vm_disk(self, _exists, _manage_disk,
                                       _create_image):
        vm_disk_path = "/vmfs/volumes/dsname/vms/ab/cd.vmdk"
        self.image_manager.create_image_with_vm_disk(
            "ds1", "[] /vmfs/volumes/ds1/foo", "img_1", vm_disk_path)

        # Verify that we copy the disk correctly
        expected_tmp_disk_ds_path = \
            "[] /vmfs/volumes/ds1/foo/img_1.vmdk"
        _vd_spec = _manage_disk.call_args_list[0][1]['destSpec']
        self.assertEqual("thin", _vd_spec.diskType)
        self.assertEqual("lsiLogic", _vd_spec.adapterType)
        copy_call = call(vim.VirtualDiskManager.CopyVirtualDisk_Task,
                         sourceName='[] %s' % vm_disk_path,
                         destName=expected_tmp_disk_ds_path,
                         destSpec=_vd_spec)
        expected_vim_calls = [copy_call]
        self.assertEqual(expected_vim_calls, _manage_disk.call_args_list)

        _create_image.assert_called_once_with("ds1", "[] /vmfs/volumes/ds1/foo", "img_1")

    @patch("shutil.rmtree")
    @patch("os.path.exists")
    def test_delete_tmp_dir(self, _exists, _rmtree):
        self.image_manager.delete_tmp_dir("ds1", "foo")
        _exists.assert_called_once("/vmfs/volumes/ds1/foo")
        _rmtree.assert_called_once("/vmfs/volumes/ds1/foo")

        _exists.reset_mock()
        _exists.return_value = False
        _rmtree.reset_mock()
        self.assertRaises(DirectoryNotFound,
                          self.image_manager.delete_tmp_dir,
                          "ds1", "foo")
        _exists.assert_called_once("/vmfs/volumes/ds1/foo")
        self.assertFalse(_rmtree.called)

    def test_image_size(self):
        self.ds_manager.image_datastores.return_value = ["ds1", "ds2"]
        with patch("host.hypervisor.esx.image_manager.os_vmdk_flat_path"
                   "") as image_path:
            tmpdir = file_util.mkdtemp(delete=True)
            image_path.return_value = tmpdir

            size = self.image_manager.image_size("image_id")
            self.assertTrue(size > 0)

    def test_image_size_not_exist(self):
        self.ds_manager.image_datastores.return_value = ["ds1", "ds2"]
        self.assertRaises(NoSuchResourceException,
                          self.image_manager.image_size,
                          "image_id")
