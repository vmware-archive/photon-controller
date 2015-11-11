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
""" common types """
import threading

from thrift.TSerialization import deserialize
from kazoo.protocol.states import EventType


class Watcher(object):
    def __init__(self, event_type, path, event):
        self.event_type = event_type
        self.path = path
        self.event = event

    def watch(self, event):
        if event.type == self.event_type and event.path == self.path:
            self.event.set()


def async_wait_for(event_type, prefix, node, zk_client, mem_fun="exists"):
    path = "%s/%s" % (prefix, node)
    event = threading.Event()
    watcher = Watcher(event_type, path, event)
    getattr(zk_client, mem_fun)(path, watch=watcher.watch)
    return event


def extract_node_data(client, path, thrift_type):
    (value, stat) = client.get(path)
    t_type_instance = thrift_type()
    deserialize(t_type_instance, value)
    return t_type_instance


def check_event(event_type, prefix,
                node, zk_client, mem_fun="exists"):
    """
    :param timeout [int]: seconds
    """
    path = "%s/%s" % (prefix, node)
    res = getattr(zk_client, mem_fun)(path)

    if event_type == EventType.CREATED and res:
        return True
    elif event_type == EventType.DELETED and not res:
        return True

    return False


def wait_for(event_type, prefix,
             node, zk_client, mem_fun="exists", timeout=10):
    """
    :param timeout [int]: seconds
    """
    path = "%s/%s" % (prefix, node)
    event = threading.Event()
    watcher = Watcher(event_type, path, event)
    res = getattr(zk_client, mem_fun)(path, watch=watcher.watch)

    if event_type == EventType.CREATED and res:
        return True
    elif event_type == EventType.DELETED and not res:
        return True
    else:
        event.wait(timeout)

    return event.isSet()
