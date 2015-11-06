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


class PseudoChairmanServer(object):
    """
    Chairman host / port informantion
    """
    def __init__(self, a_host="127.0.0.1", a_port=13000):
        self._host = a_host
        self._port = a_port

    def __str__(self):
        return str({'host': self._host, 'port': self._port})

    @property
    def host(self):
        return self._host

    @property
    def port(self):
        return self._port


class PseudoConfig(object):
    """
    Pseudo config, mimic AgentConfig.
    Used for feeding to ChairmanRegistrant.
    """
    def __init__(
        self,
        a_hostname,
        a_host_port,
        a_availability_zone,
        a_datastore_ids,
        a_chairman_list,
            a_networks=None):
        """
        :param a_hostname: fake agent's hostname
        :type a_hostname: string

        :param a_host_port: fake agent's thrift listening port
        :type a_host_port: integer

        :param a_availability_zone: fake agent's availability zone
        :type a_availability_zone: string

        :param a_datastore_ids: fake agent's datastore id
        :type a_datastore_ids: list of string

        :param a_chairman_list: target Chairman server address/port info
        :type a_chairman_list: list of PseudoChairmanServer type objects

        :param a_networks: fake agent's networks. Default: None
        :type a_networks: list of string
        """
        self._hostname = a_hostname
        self._host_port = a_host_port
        self._availability_zone_id = a_availability_zone
        self._datastore_ids = a_datastore_ids
        self._networks = [] if a_networks is None else a_networks
        self._chairman_list = a_chairman_list

        # Running multiple agent thrift server, each has default 3 threads
        self._host_service_threads = 3

    def __str__(self):
        return str((
            self._hostname,
            self._host_port,
            self._availability_zone_id,
            self._datastore_ids,
            self._chairman_list))

    @property
    def chairman_list(self):
        return self._chairman_list

    @property
    def datastore_ids(self):
        return self._datastore_ids

    @property
    def availability_zone_id(self):
        return self._availability_zone_id

    @property
    def host_port(self):
        return self._host_port

    @property
    def host_service_threads(self):
        return self._host_service_threads

    @property
    def hostname(self):
        return self._hostname

    @property
    def networks(self):
        return self._networks
