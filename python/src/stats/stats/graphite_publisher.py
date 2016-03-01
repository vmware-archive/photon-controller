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

import logging
import pickle
import socket
import struct

from .publisher import Publisher

DEFAULT_CARBON_PORT = 2004


class GraphitePublisher(Publisher):

    def __init__(self, host_id, carbon_host, carbon_port=DEFAULT_CARBON_PORT,
                 use_pickle_format=True, host_tags=None):
        self._logger = logging.getLogger(__name__)
        self._carbon_host = carbon_host
        self._carbon_port = carbon_port
        self._use_pickle = use_pickle_format
        tags = self.get_sensitized_tags(host_tags)
        self._host_metric_prefix = "photon." + str(host_id) + tags

        if not use_pickle_format:
            # Not supporting plain text format for now.
            raise NotImplementedError

    @staticmethod
    def get_sensitized_tags(host_tags):
        tags = ""
        if host_tags is not None:
            host_tags = host_tags.strip()
            if host_tags is not "":
                tag_list = host_tags.split(',')
                tag_list = map(lambda tag: tag.strip().replace(' ', '_'), tag_list)
                tag_list = filter(lambda tag: tag is not "", tag_list)
                tag_list.sort()
                tags_joined = ".".join(tag_list)
                if tags_joined != "":
                    tags = "." + tags_joined
        return tags

    def _build_pickled_data_msg(self, stats):
        # pickle-based message is of the form
        # <payload-length><pickle data of stats>
        # where stats is a nested list of tuples of the form
        # [(metric_key, (ts, value)), ... ]

        metric_list = []
        for metric in stats.keys():
            metric_key = "%s.%s" % (self._host_metric_prefix, metric)
            metric_list += [(metric_key, tup) for tup in stats[metric]]

        payload = pickle.dumps(metric_list, protocol=2)
        header = struct.pack("!L", len(payload))
        message = header + payload
        return message

    def publish(self, stats):
        try:
            message = self._build_pickled_data_msg(stats)
            sock = socket.socket()
            sock.connect((self._carbon_host, self._carbon_port))
            sock.sendall(message)
            sock.close()
        except:
            self._logger.critical(
                "could not connect with stats_store_endpoint: %s : %s" % (self._carbon_host, self._carbon_port))
