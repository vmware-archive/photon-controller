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

""" Parse Physical Layout DC Map like yml """

import logging
import socket
import sys
import yaml

from optparse import OptionParser
from os.path import basename

from psim.error import ParseError
from psim.tools.pseudo_config import PseudoConfig
from psim.tools.pseudo_config import PseudoChairmanServer

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)


class PhysicalLayoutYmlParser(object):
    """
    Parse DC yml file into RegisterHostRequest taken format.
    """
    def __init__(
        self,
        a_filename,
        a_chairman_list,
        a_local_addr="127.0.0.1",
        a_beginning_port=8000,
            a_discrete_port=True):
        """
        :param a_filename: input dc map yml file name
        :type a_filename: string

        :param a_local_addr: pseudo agent local address
        :type a_local_addr: string

        :param a_default_port: pseudo agent local listening port
        :type a_default_port: integer
        """

        self._dc_map_file = a_filename
        self._chairman_list = a_chairman_list
        self._local_address = a_local_addr
        self._default_port = a_beginning_port
        self._beginning_port = a_beginning_port
        self._discrete_port = a_discrete_port

    def _parse_chairman_list(self):
        """
        Converts a list/tuple of chairman address format,
        e.g ('127.0.0.1:1234',)
        into a list of PseudoChairmanServer type.

        :param chairman_list: The list of chairman as a comma separated string.
        :rtype list of chairman service addresses of type PseudoChairmanServer
        :raise: ParseError
        """

        server_list = []

        for server in self._chairman_list:
            try:
                host, port = server.split(":")
                server_list.append(
                    PseudoChairmanServer(host, int(port)))
            except ValueError as ve:
                logger.warning(
                    "Failed to parse server %s, Invalid delemiter" % server)
                raise ParseError(str(ve))
            except AttributeError as ae:
                logger.warning("Failed to parse chairman server %" %
                               server)
                raise ParseError(str(ae))

        return server_list

    def _port_chk(self, a_port):
        """
        Check System available ports
        Contract:
            if port available, return True,
            else return False
        """
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

        try:
            s.bind((self._local_address, a_port))
        except socket.error:
            return False
        else:
            s.close()
            return True

    def _parse(self, a_result_dict):
        """
        Parse YML into dictionary type {host_id: PseudoConfig}

        :param a_result_dict: loaded yml dict
        :type a_result_dict: dict

        :raise: ParseError

        :rtype: dict
            Key : Host ID (string type)
            Value : HostConfig (HostConfig type)
        """
        try:
            hosts = a_result_dict["hosts"]
        except KeyError as ke:
            pe = ParseError(
                "Parsering YML error : {} key can not be found".
                format(str(ke)))
            raise pe

        port_set = set()

        if self._discrete_port:
            hosts_count = len(hosts)
            port_count = self._beginning_port

            while (port_count < (port_count + hosts_count)):
                if self._port_chk(port_count):
                    port_set.add(port_count)
                    port_count = port_count + 1
                    hosts_count = hosts_count - 1
                else:
                    port_count = port_count + 1

        result = {}

        chairmain_server_list = self._parse_chairman_list()

        to_str_lambda = \
            lambda l_var: l_var if type(l_var) is str else str(l_var)

        for host in hosts:
            try:
                host_id = to_str_lambda(host['id'])

                datastores = \
                    [to_str_lambda(host["data_stores"][datastore])
                        for datastore in host["data_stores"]]

                availability_zone = to_str_lambda(host["availability_zone"])

                port = port_set.pop() \
                    if self._discrete_port \
                    else self._default_port

            except KeyError as ke:
                pe = ParseError(
                    "Parsering YML error : {}".
                    format(str(ke)))
                raise pe

            result[host_id] = PseudoConfig(
                self._local_address,
                port,
                availability_zone,
                datastores,
                chairmain_server_list)

        return result

    def GetDcHosts(self):
        """
        :raise: ParseError

        :rtype: dict
            Key : Host ID (string type)
            Value : PseudoConfig type
        """
        try:
            with open(self._dc_map_file, 'r') as fd:
                yml_dict = yaml.load(fd)
        except Exception as e:
            logger.error(e)
            pe = ParseError(str(e))
            raise pe

        return self._parse(yml_dict)

if __name__ == '__main__':
    usage = "usage: {} dc_yml_file_name"
    parser = OptionParser(usage=usage.format('%prog'))
    _, dc_yml_file = parser.parse_args()

    if len(dc_yml_file) == 0:
        print(usage.format(basename(__file__)))
        sys.exit()

    dc_parser = PhysicalLayoutYmlParser(
        str(dc_yml_file[0]),
        ("127.0.0.1:9086",),
        a_discrete_port=True)

    result = dc_parser.GetDcHosts()

    for r in result:
        print("host id: {}".format(r))
        print("PseudoConfig: {}".format(result[r]))
        for p in result[r].chairman_list:
            print("chiarman_list: {}".format(p))
        print("\n")
