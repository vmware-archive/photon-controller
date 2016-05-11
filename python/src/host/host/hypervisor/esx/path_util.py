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
import os
import re

COMPOND_PATH_SEPARATOR = '_'
VMFS_VOLUMES = "/vmfs/volumes"

DISK_FOLDER_NAME_PREFIX = "disk"
IMAGE_FOLDER_NAME_PREFIX = "image"
VM_FOLDER_NAME_PREFIX = "vm"
TMP_IMAGE_FOLDER_NAME_PREFIX = "tmp_image"
SHADOW_VM_NAME_PREFIX = "shadow_"
METADATA_FILE_EXT = "ecv"


def os_datastore_root(datastore):
    return os.path.join(VMFS_VOLUMES, datastore)


def os_datastore_path(datastore, folder1, folder2=None):
    path = os.path.join(VMFS_VOLUMES, datastore, folder1)
    if folder2:
        path = os.path.join(path, folder2)
    return path


def datastore_path(datastore, folder):
    return "[] %s" % os_datastore_path(datastore, folder)


def datastore_to_os_path(datastore_path):
    if datastore_path.startswith(VMFS_VOLUMES):
        return datastore_path

    spl = datastore_path.split('[', 1)[1].split(']', 1)
    return os.path.join(VMFS_VOLUMES, spl[0], spl[1].strip())


def os_to_datastore_path(os_path):
    if os_path.startswith("["):
        return os_path
    return "[] %s" % os_path


def compond_path_join(s1, s2, s3=None):
    dir = s1 + COMPOND_PATH_SEPARATOR + s2
    if s3:
        dir += COMPOND_PATH_SEPARATOR + s3
    return dir


def os_vmdk_path(datastore, disk_id, folder=DISK_FOLDER_NAME_PREFIX):
    return os_datastore_path(datastore, compond_path_join(folder, disk_id), vmdk_add_suffix(disk_id))


def os_vmdk_flat_path(datastore, disk_id, folder=IMAGE_FOLDER_NAME_PREFIX):
    return os_datastore_path(datastore, compond_path_join(folder, disk_id), vmdk_add_suffix("%s-flat" % disk_id))


def vmdk_path(datastore, disk_id, folder=DISK_FOLDER_NAME_PREFIX):
    return os_to_datastore_path(os_vmdk_path(datastore, disk_id, folder))


def vmdk_add_suffix(vm_id):
    return "%s.%s" % (vm_id, "vmdk")


def vmx_add_suffix(vm_id):
    return "%s.%s" % (vm_id, "vmx")


def metadata_filename(disk_id):
    return "%s.%s" % (disk_id, METADATA_FILE_EXT)


def os_metadata_path(datastore, disk_id, folder=DISK_FOLDER_NAME_PREFIX):
    return os_datastore_path(datastore, compond_path_join(folder, disk_id), metadata_filename(disk_id))


def image_directory_path(datastore, image_id):
    """Returns absolute path of the image directory. It looks something like:
        /vmfs/volumes/$datastore/image_$image_id
    """
    return os_datastore_path(datastore, compond_path_join(IMAGE_FOLDER_NAME_PREFIX, image_id))


def list_top_level_directory(datastore, folder_prefix):
    """List datastore top level directories that has given prefix.

       On VSAN, this is much faster than glob.glob, because VSAN caches folder names locally,
       but stores folder attributes distributedly. os.listdir only accesses names, while
       glob.glob reads attributes.
    """
    folder_prefix += COMPOND_PATH_SEPARATOR
    root = os_datastore_root(datastore)
    return [os.path.join(root, d) for d in os.listdir(root) if d.startswith(folder_prefix)]


def is_persistent_disk(disk_files):
    return _find_root_in_disk_files(disk_files) == DISK_FOLDER_NAME_PREFIX


def is_ephemeral_disk(disk_files):
    return _find_root_in_disk_files(disk_files) == VM_FOLDER_NAME_PREFIX


def is_image(disk_files):
    return _find_root_in_disk_files(disk_files) == IMAGE_FOLDER_NAME_PREFIX


def _find_root_in_disk_files(disk_files):
    if not disk_files:
        return None

    if len(disk_files) == 1:
        return _root_folder(disk_files[0])

    for disk_file in disk_files:
        root = _root_folder(disk_file)
        if root != IMAGE_FOLDER_NAME_PREFIX:
            return root


def uuid_to_vmdk_uuid(uuid):
    """Converts a uuid string to the format used for vmdk uuids."""

    # vmdk UUID is expected in the format of:
    # 'hh hh hh hh hh hh hh hh-hh hh hh hh hh hh hh hh'

    uuid = uuid.translate(None, " -")
    if len(uuid) != 32:
        raise ValueError("unexpected format for uuid: %s" % uuid)
    pairs = [uuid[i:i+2].lower() for i in range(0, len(uuid), 2)]
    return " ".join(pairs[:8]) + "-" + " ".join(pairs[8:])


def get_root_disk(disk_files):
    """Find the COW child disk from the disk chain with an image parent.

    :type disk_files: list of str
    :param disk_files: list of files paths comprising the chain of disks making
                       up a single VM disk. Typically this list comes from one
                       vm.layout.disk.diskFile field in vim client.
    """
    # TODO(Vui): Should return path to disk full-cloned from image in non
    #            linked-clone case.

    # XXX Assumes first non image disk path is the child disk.
    # TODO(Vui) Fix cached disk layout so this is more reliable.
    for disk_file in disk_files:
        if _root_folder(disk_file) != IMAGE_FOLDER_NAME_PREFIX:
            return datastore_to_os_path(disk_file)
    return None


def _root_folder(path):
    return re.sub('^\[.*\] ', '', path).split('/')[0].split(COMPOND_PATH_SEPARATOR)[0]
