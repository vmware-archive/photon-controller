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

import json
import threading

from common.lock import locked


class SetEncoder(json.JSONEncoder):
    """json encoder for set"""
    def default(self, obj):
        if isinstance(obj, set):
            return list(obj)
        return json.JSONEncoder.default(self, obj)


class DatastoreTags(object):
    """Datastore tags Persistence class
    """

    # TODO(agui): Generalize the class to support other resources
    KEY = "datastore_tags"

    def __init__(self, state):
        """Constructor
        :param state: common.state.State. It helps to read/write in file (
        e.g. state.json).
        """
        self._state = state
        self.lock = threading.RLock()
        self._change_callbacks = []

    @locked
    def set(self, datastore_tags):
        """Set datastore tags
        :param datastore_tags: dict[str=>set(str)], map from datastore id to
                               set of tags
        """
        self._state.set(self.KEY, json.dumps(datastore_tags, cls=SetEncoder))
        for callback in self._change_callbacks:
            callback()

    @locked
    def get(self):
        """Get datastore tags
        :return: dict[str=>set(str)], map from datastore id to set of tags
        """
        s = self._state.get(self.KEY)
        if not s:
            return {}

        d = json.loads(s)
        if not d:
            return {}

        for key in d.keys():
            d[key] = set(d[key])
        return d

    def on_change(self, callback):
        """Register callback which will be triggered after tags changed
        :param callback: [callable] with no parameter
        """
        self._change_callbacks.append(callback)
