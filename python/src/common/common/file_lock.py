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
from enum import Enum
import errno
import fcntl
import logging
import os
import shutil
import time
import uuid

from six import with_metaclass

from gen.resource.ttypes import DatastoreType

LOCK_EXTENSION = "ec_lock"
# From lib/public/fileIO.h
O_EXCLUSIVE_LOCK = 0x10000000


class UnsupportedFileSystem(Exception):
    """
    Exception thrown if we are trying to create a file on a
    filesystem that we don't support.
    """


class InvalidFile(Exception):
    """
    Exception thrown if the file is invalid or can't be read.
    """
    pass


class AcquireLockFailure(Exception):
    """
    Exception thrown if the lock file can't be acquired
    """
    pass


class _FileSystemType(Enum):
    # Vmware VMFS filesystem, implies we are running on ESX.
    vmfs = 0
    # NFS, also implies we are running in ESX.
    vmw_nfs = 1
    # VSAN
    vsan = 2
    # Any posix compliant implementation like ext3, assumes a non esx
    # runtime (e.g. dev setup/fake agent)
    posix = 3

    @staticmethod
    def from_thrift(thrift_fs_type):
        """
        Returns the ref count type to use for the datastore type
        """
        if thrift_fs_type is DatastoreType.LOCAL_VMFS or thrift_fs_type is DatastoreType.SHARED_VMFS:
            return _FileSystemType.vmfs
        if thrift_fs_type is DatastoreType.NFS_3 or thrift_fs_type is DatastoreType.NFS_41:
            return _FileSystemType.vmw_nfs
        if thrift_fs_type is DatastoreType.VSAN:
            return _FileSystemType.vsan
        if thrift_fs_type is DatastoreType.EXT3:
            return _FileSystemType.posix
        raise UnsupportedFileSystem("FS type %d" % thrift_fs_type)


class _FileIOBaseImpl(with_metaclass(abc.ABCMeta, object)):
    """ Interface for file i/o.
        Specific filesystem implementations will extend this class.
    """

    @abc.abstractmethod
    def build_lock_path(self, lock_target):
        pass

    @abc.abstractmethod
    def lock_and_open(self, file_name):
        """
        Lock the file with an exclusive lock and return the open file
        descriptor
        :param: The fully qualified file path for the filename.
        :type: string
        :returns: open file descriptor
        :rtype: int
        raises AcquireLockFailure if file is already locked.
        raises InvalidFile for all other failures, e.g. permissions issues.
        """
        pass


class _EsxFileIOImpl(_FileIOBaseImpl):
    """
    File i/o implementation for NFS and VMFS.
    Issues relating to vmfs:
        1 - Atomic Move: Atomic move only works if the destination file_name is
        not locked. Hence, the scheme of writing to a temp file while holding a
        lock on the ref file and attempting an atomic move to replace the
        original ref file doesn't work on vmfs.
        2 - os.ftruncate doesn't work.
    While writing we just lseek to the start of the file and clobber the write
    with the data we have. This prevents the allocation of new blocks during a
    file write.

    NFS:
        We just reuse the implementation for VMFS here for NFS.
        NFS_FILE_SYNC is always set for all writes so writes update both data
        and metadata on media when it is processed by NFS server.
        NFSv3 on ESX uses advisory locking and NFSv4 uses mandatory locking,
        it is transparent to user space here.
    """

    def build_lock_path(self, lock_target):
        return "%s.%s" % (lock_target, LOCK_EXTENSION)

    def lock_and_open(self, file_name):
        """
        See comments in _FileIOBaseImpl
        """
        # Opening with O_EXCLUSIVE_LOCK will cause the exclusive FS lock to be
        # held.
        # If the file doesn't exist we need to create a file so we prevent
        # concurrent edits of the file.
        try:
            fd = os.open(file_name, os.O_CREAT | os.O_RDWR | os.O_DIRECT | O_EXCLUSIVE_LOCK)
        except OSError as e:
            if e.errno == errno.EBUSY:
                logging.info("File %s already locked" % file_name)
                raise AcquireLockFailure("File %s locked" % file_name)
            else:
                logging.info("Failed to open file:", exc_info=True)
                raise InvalidFile("Can't access file %s" % file_name)
        except Exception:
            logging.info("Failed to open file:", exc_info=True)
            raise InvalidFile("Can't read file %s " % file_name)
        return fd


class _VsanFileIOImpl(_FileIOBaseImpl):
    """
    Implementation of file i/o operations for VSAN
    """
    def build_lock_path(self, lock_target):
        return os.path.join(lock_target, "lock.%s" % LOCK_EXTENSION)

    def lock_and_open(self, file_name):
        # Opening with O_EXCLUSIVE_LOCK will cause the exclusive FS lock to be
        # held.
        # If the file doesn't exist we need to create a file so we prevent
        # concurrent edits of the file.
        try:
            fd = os.open(file_name, os.O_CREAT | os.O_RDWR | os.O_DIRECT | O_EXCLUSIVE_LOCK)
        except OSError as e:
            if e.errno == errno.EBUSY:
                logging.info("File %s already locked" % file_name)
                raise AcquireLockFailure("File %s locked" % file_name)
            else:
                logging.info("Failed to open file:", exc_info=True)
                raise InvalidFile("Can't access file %s" % file_name)
        except Exception:
            logging.info("Failed to open file:", exc_info=True)
            raise InvalidFile("Can't read file %s " % file_name)
        return fd


class _PosixFileIOImpl(_FileIOBaseImpl):
    """
    Implementation of file i/o operations for posix FS
    """

    def build_lock_path(self, lock_target):
        return "%s.%s" % (lock_target, LOCK_EXTENSION)

    def lock_and_open(self, file_name):
        """
        See comments in _FileIOBaseImpl
        Lock the file and open it on ext3 (posix filesystems)
        Doesn't use O_DIRECT as python os.read() call doesn't work for
        O_DIRECT, which should be ok for the fake agent.
        """
        # Now open the file.
        try:
            fd = os.open(file_name, os.O_CREAT | os.O_RDWR)
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except IOError as e:
            if (e.errno == errno.EAGAIN):
                logging.info("File %s already locked" % file_name)
                raise AcquireLockFailure("File %s locked" % file_name)
        except Exception:
            # The open fd for the lock file will be closed by the subsequent
            # call to self._close()
            logging.info("Failed to open file:", exc_info=True)
            raise InvalidFile("Can't read file %s " % file_name)
        return fd


class FileIOImplFactory(object):
    """ Simple factory class to create filesystem specific i/o objects """

    file_io_objects = {_FileSystemType.vmfs: _EsxFileIOImpl,
                       _FileSystemType.vmw_nfs: _EsxFileIOImpl,
                       _FileSystemType.vsan: _VsanFileIOImpl,
                       _FileSystemType.posix: _PosixFileIOImpl}

    @staticmethod
    def createFileIOImplObj(thrift_type):
        """ Create a i/o class based on the thrift type """
        return FileIOImplFactory.file_io_objects[
            _FileSystemType.from_thrift(thrift_type)]()


class _FileLock(object):
    """ Class implementing filesystem specific lock operations """

    def __init__(self, fs_type):
        """
        Constructor
        fs_type: The thrift fs_type
        """
        self._f_obj = FileIOImplFactory.createFileIOImplObj(fs_type)
        self._fds = []
        self._closed = False

    def build_lock_path(self, lock_target):
        return self._f_obj.build_lock_path(lock_target)

    def lock_and_open(self, file_name, retry=0, wait=0.1):
        """
        Lock and open a file with the specified filename.
        retry: number of times to retry fetching lock
        wait: number of seconds between retries
        return: file descriptor of the opened file.
        rtype: int
        """
        while retry >= 0:
            try:
                return self._lock_and_open(file_name)
            except (AcquireLockFailure, InvalidFile):
                time.sleep(wait)
                retry -= 1
        raise

    def _lock_and_open(self, file_name):
        fd = self._f_obj.lock_and_open(file_name)
        # remember the open file handles.
        self._fds.append(fd)
        self._closed = False
        return fd

    @property
    def closed(self):
        return self._closed

    def close(self):
        """ Close the open file descriptors """
        for fd in self._fds:
            if fd:
                try:
                    os.close(fd)
                except:
                    logging.info("Failed to close file", exc_info=True)
        self._fds = []
        self._closed = True

    def __enter__(self):
        return self

    def __exit__(self, type, value, traceback):
        if not self._closed:
            self.close()


class FileBackedLock(object):
    """Class implementing FileIO-based lock on a single filesystem path

    The class can be used as a context manager. Alternatively instances of
    this class can be explicitly locked or unlocked.

    For now there is no support for lock expiration/renewal. The one main case
    of needing to clear locks on abnormal termination of process holding the
    locks is already handled by the underlying (nfs/vmfs) file system.
    TODO(vui): for posix NFS, to handle case where the process dies without
    getting a chance to close the lock file (e.g. host lost connectivity to NFS
    share), the lock acquisition logic can determine lock staleness by checking
    the last modification time of the lock file.
    """

    def __init__(self, lock_target, ds_type, retry=0, wait_secs=1.0):
        """
        :param path: file system path based on which the lock is created
        :param ds_type: DatastoreType
        :param retry: number of times to retry fetching lock
        :param wait_secs: number of seconds between retries
        """

        self._file_io = _FileLock(ds_type)
        self._lock_path = self._file_io.build_lock_path(lock_target)
        self._acquired = False
        self.retry = retry
        self.wait_secs = wait_secs

    def lock(self):
        try:
            self._file_io.lock_and_open(self._lock_path, self.retry, self.wait_secs)
            self._acquired = True
        except (AcquireLockFailure, InvalidFile):
            logging.info("Unable to lock %s", self._lock_path)
            raise

        return self

    def __enter__(self):
        return self.lock()

    def unlock(self):
        if not self._acquired:
            return

        # Move lock file away to another location while locked before
        # deleting said location. This is to guard against someone else
        # relocking the file prior to us deleting it.
        lock_path_to_delete = "%s.%s" % (self._lock_path, str(uuid.uuid4()))
        try:
            shutil.move(self._lock_path, lock_path_to_delete)
            self._file_io.close()
            os.remove(lock_path_to_delete)
        except:
            logging.info("Unable to clean up lock file %s", self._lock_path, exc_info=True)
            if not self._file_io.closed:
                self._file_io.close()

        self._acquired = False

    def __exit__(self, type, value, traceback):
        self.unlock()

    def acquired(self):
        return self._acquired
