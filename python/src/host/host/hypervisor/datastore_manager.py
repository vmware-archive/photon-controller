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


class DatastoreNotFoundException(Exception):
    pass


class DatastoreManager(object):
    """ A class that wraps datastore management
    """

    @abc.abstractmethod
    def get_datastore_ids(self):
        """ Get all datastores configured on host including image datastore
        :return: list of str, datastore id
        """
        pass

    @abc.abstractmethod
    def get_datastores(self):
        """ Get all datastores configured on host including image datastore
        :return: list of gen.resource.ttype.Datastore
        """
        pass

    @abc.abstractmethod
    def image_datastore(self):
        """ Image datasotre is the image shared datastore that is accessible
        by all hosts.
        :return: str, image datastore id
        """
        pass

    @abc.abstractmethod
    def datastore_info(self, datastore_id):
        """ Get datastore info
        :return: host.hypervisor.system.DatastoreInfo
        """
        pass

    @abc.abstractmethod
    def datastore_nfc_ticket(self, datastore_name):
        """ Get nfc ticket of a datastore
        :datastore_name: str, name of the datastore
        :return: gen.resource.ttypes.HostServiceTicket
        """
        pass

    @abc.abstractmethod
    def datastore_name(self, datastore_id):
        """ Get datastore_name from datastore_id
        :return: str, datastore name
        """
        pass

    def datastore_type(self, datastore_id):
        """ Get datastore_type from datastore_id

        It throws DatastoreNotFoundException if the datastore is not found.

        :return: gen.resource.ttype.DatastoreType
        """
        for datastore in self._datastores:
            if datastore.id == datastore_id:
                return datastore.type
        raise DatastoreNotFoundException("%s is not found" % datastore_id)

    def vm_datastores(self):
        """
        This property returns the ids of the datastores
        accessible from this hypervisor minus the image_datastore
        """
        assert(self.get_datastore_ids() is not None)
        return [ds_id for ds_id in self.get_datastore_ids()
                if ds_id != self.image_datastore()]

    def normalize(self, id_or_name, to_id=True):
        """Normalizes datastore id and name to datastore id.

        :param id_or_name: str, datastore id or name
        :param to_id: normalize to id if True, to name otherwise
        :return: str, datastore id
        :raise: DatastoreNotFoundException
        """
        for ds in self.get_datastores():
            if ds.id == id_or_name or ds.name == id_or_name:
                if to_id:
                    return ds.id
                else:
                    return ds.name
        raise DatastoreNotFoundException("%s not found" % id_or_name)

    def normalize_to_name(self, id_or_name):
        return self.normalize(id_or_name, to_id=False)
