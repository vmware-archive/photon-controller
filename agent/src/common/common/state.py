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

from common.file_util import atomic_write_file
from common.lock import locked


class State(object):

    MODE_KEY = "mode"

    """
    Persist host state into json file.
    """
    def __init__(self, file_location):
        """
        :param file_location [str]:
        """
        self.file_location = file_location
        self.lock = threading.RLock()

        try:
            self.cache = self._read_json_file()
        except:
            self.cache = {}

    def _read_json_file(self):
        """read the json file

        :rtype [dict]:
        """
        with open(self.file_location) as fd:
            data = json.load(fd)
            return data

    def _write_json_file(self, state):
        """write to the json file

        :param state [dict]: key/value pair
        """
        with atomic_write_file(self.file_location) as outfile:
            json.dump(state, outfile, sort_keys=True, indent=4,
                      separators=(',', ': '))

    @locked
    def get(self, key):
        """retrieve key value state from json file.

        :param key [str]: key
        :rtype [str] or None: value
        """
        if key in self.cache:
            return self.cache[key]
        else:
            return None

    @locked
    def set(self, key, value):
        """persist key value state into json file.

        :param key [str]: key
        :param value [str]: value
        """
        self.cache[key] = value
        self._write_json_file(self.cache)
