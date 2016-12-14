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

"""Provides some basic filesystem utilities."""

import atexit
import errno
import os
import shutil
import tempfile


class atomic_write_file(object):
    """
    Atomic write to file with atomic guarantee provided by
    'rename' system call.
    """
    def __init__(self, file_path, mode="w"):
        """
        :param file_path [str]: file_path to write into.
        :parap mode [str]: open mode. default: "w"
        """
        self.file_path = file_path
        self.mode = mode
        self.tmp_file_path = file_path + ".bak"

    def __enter__(self):
        self.fd = open(self.tmp_file_path, self.mode)
        return self.fd

    def __exit__(self, type, value, traceback):
        # close file object while leaving caller's 'with' block
        self.fd.close()

        # no exception occurs within caller's 'with' block
        if not type:
            # atomic rename. pointing to the same i-node.
            os.rename(self.tmp_file_path, self.file_path)


def read_file(path):
    """Read in a file.

    Args:
        path: The path to read in.

    Returns:
        The contents of the file.

    """
    with open(path, "r") as f:
        contents = f.read()
        f.close()
        return contents


def mkdir_p(path):
    """The equivalent of mkdir -p.

    Args:
        path: The path to create.

    """
    try:
        os.makedirs(path)
    except OSError as exc:
        if exc.errno == errno.EEXIST and os.path.isdir(path):
            pass
        else:
            raise exc


def write_file(path, contents):
    """Write contents to the path.

    Args:
        path: The path to create the file at.
        contents: The file contents.

    """
    mkdir_p(os.path.dirname(path))
    with open(path, "w") as f:
        f.write(contents)
        f.close()


def rm_rf(dir):
    """The equivalent of rm -rf.

    Args:
        dir: The directory to delete.

    """
    try:
        if os.path.islink(dir):
            # if dir is a symlink, delete the link target
            link = os.readlink(dir)
            shutil.rmtree(link)
            os.remove(dir)
        else:
            shutil.rmtree(dir)
    except OSError as exc:
        if exc.errno == errno.ENOENT:
            pass
        else:
            raise exc


def mkdtemp(*args, **kwargs):
    """A wrapper of tempfile.mkdtemp to support auto delete
    :param kwargs: if delete is set as True, the temp directory is
    automatically deleted at exit
    """
    delete = False
    if 'delete' in kwargs:
        if kwargs['delete']:
            delete = True
        del kwargs['delete']

    tempdir = tempfile.mkdtemp(*args, **kwargs)
    if delete:
        atexit.register(lambda dir: rm_rf(dir), tempdir)
    return tempdir
