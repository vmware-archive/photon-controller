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


class HostClient(object):
    __metaclass__ = abc.ABCMeta

    """ Connect and setup
    """
    @abc.abstractmethod
    def connect_local(self):
        pass

    @abc.abstractmethod
    def connect_userpwd(self, host, user, pwd):
        pass

    @abc.abstractmethod
    def connect_ticket(self, host, ticket):
        pass

    @abc.abstractmethod
    def disconnect(self, wait=False):
        pass

    @abc.abstractmethod
    def add_update_listener(self, listener):
        pass

    @abc.abstractmethod
    def remove_update_listener(self, listener):
        pass

    """ Disk and file operations
    """
    @abc.abstractmethod
    def create_disk(self, path, size):
        pass

    @abc.abstractmethod
    def copy_disk(self, src, dst):
        pass

    @abc.abstractmethod
    def move_disk(self, src, dst):
        pass

    @abc.abstractmethod
    def delete_disk(self, path):
        pass

    @abc.abstractmethod
    def set_disk_uuid(self, path, uuid):
        pass

    @abc.abstractmethod
    def query_disk_uuid(self, path):
        pass

    @abc.abstractmethod
    def make_directory(self, path):
        pass

    @abc.abstractmethod
    def delete_file(self, path):
        pass

    @abc.abstractmethod
    def move_file(self, src, dest):
        pass

    """ Misc
    """
    @abc.abstractmethod
    def acquire_clone_ticket(self):
        pass

    @abc.abstractmethod
    def set_large_page_support(self, disable=False):
        pass
