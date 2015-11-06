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

from pkg_resources import iter_entry_points

# All loaded plugins
loaded_plugins = []


class Plugin(object):

    def __init__(self, name, service, handler, num_threads, max_entries=0):
        """
        :param name: plugin name
        :param service: thrift service class
        :param handler: thrift handler
        :param num_threads: number of dedicated worker threads
        :param max_entries: max number of queued entries. 0 as unbounded.
        """
        self.name = name
        self.service = service
        self.handler = handler
        self.num_threads = num_threads
        self.max_entries = max_entries

    def __repr__(self):
        return "<name: %s, service: %s, handler: %s, num_threads: %d," \
               "max_entries: %d>" % (self.name, self.service, self.handler,
                                     self.num_threads, self.max_entries)


def load_plugins():
    plugins = []
    for entries in iter_entry_points(group="photon.controller.plugin"):
        plugins.append(entries.load())
    global loaded_plugins
    loaded_plugins = plugins
    return plugins
