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

import threading


class DuplicatedValue(Exception):
    pass


class ExclusiveSet(object):
    """ A set in which duplicated value is not accepted. The set will throw
    an exception if client tries to add duplicated value in the set.
    """

    def __init__(self):
        self._lock = threading.Lock()
        self._set = set()

    def add(self, value):
        with self._lock:
            if value in self._set:
                raise DuplicatedValue()

            return self._set.add(value)

    def __dir__(self):
        return dir(self._set)

    def __len__(self):
        with self._lock:
            return len(self._set)

    def __iter__(self):
        with self._lock:
            return iter(self._set)

    def __str__(self):
        with self._lock:
            return str(self._set)

    def __repr__(self):
        with self._lock:
            return repr(self._set)

    def __getattr__(self, name):
        attr = getattr(self._set, name)
        if not callable(attr):
            return attr
        else:
            def f(*args, **kwargs):
                with self._lock:
                    return attr(*args, **kwargs)

            return f
