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
import uuid


class TimeoutError(Exception):
    pass


class BlockingDict(object):
    """ Blocking version of dict. It supports client to wait on a specific
    key until the value of the key comes to a certain state. This dict
    treats the value as a whole item. It only triggers unblock when the whole
    item changes, which doesn't include the internal state change.

    For example,

    dict = BlockingDict()

    # The following line blocks if dict[task_id].state != 'success'
    dict.wait_until(task_id, lambda t: t.state == 'success', timeout=10)

    # The following lines unblocks the previous statement
    task = TaskInfo(state='success')
    dict[task_id] = task

    # The following line doesn't unblock the previous statement
    dict[task_id].state = 'success'

    """

    DEFAULT_TIMEOUT = 30

    def __init__(self):
        self._dict = {}
        self._waiters = {}
        self._dict_lock = threading.Lock()
        self._waiter_lock = threading.Lock()

    def __getitem__(self, key):
        with self._dict_lock:
            return self._dict[key]

    def __setitem__(self, key, value):
        with self._dict_lock:
            waiters = self._get_waiters(key)
            for waiter in waiters:
                waiter.set_value(value)

            self._dict[key] = value

    def __delitem__(self, key):
        with self._dict_lock:
            waiters = self._get_waiters(key)
            for waiter in waiters:
                waiter.set_value(None)

            del self._dict[key]

    def __contains__(self, key):
        with self._dict_lock:
            return key in self._dict

    def __len__(self):
        with self._dict_lock:
            return len(self._dict)

    def wait_until(self, key, verifier, timeout=DEFAULT_TIMEOUT):
        """ Blocks the caller until the value of the key in the dict passes
        the verifier. If the verifier is a function, call verifier(value) to
        verify if caller can be unblocked. If verifier is not a function,
        compare with value directly.

        If an entry is deleted, it's equivalent to setting it to None. The
        waiters waiting on None will get released when it happens.

        :param key: key of the dict
        :param verifier: used to verify if the new value of the key in dict
        can unblock caller
        :param timeout: number of seconds before timeout
        :return: value of the key in dict
        """
        with self._dict_lock:
            if key in self._dict:
                if _verify(verifier, self._dict[key]):
                    return self._dict[key]
            else:
                if _verify(verifier, None):
                    return None

            waiter = self._add_waiter(key, verifier)

        try:
            return waiter.wait_until(timeout)
        finally:
            self._remove_waiter(waiter)

    @property
    def num_waiters(self):
        """ Number of waiters in the dict

        :return: int
        """
        num = 0
        with self._waiter_lock:
            for waiters in self._waiters.values():
                num += len(waiters)
        return num

    @property
    def num_keys_with_waiters(self):
        """ Number of the keys with waiters waiting

        :return: int
        """
        with self._waiter_lock:
            return len(self._waiters)

    def _add_waiter(self, key, verifier):
        waiter = Waiter(key, verifier)
        with self._waiter_lock:
            if key in self._waiters:
                self._waiters[key].append(waiter)
            else:
                self._waiters[key] = [waiter]

        return waiter

    def _get_waiters(self, key):
        with self._waiter_lock:
            if key not in self._waiters:
                return []

            waiters = self._waiters[key]
            return waiters

    def _remove_waiter(self, waiter):
        with self._waiter_lock:
            assert(waiter.key in self._waiters)

            waiters = self._waiters[waiter.key]
            waiters.remove(waiter)

            if not waiters:
                del self._waiters[waiter.key]


class Waiter(object):
    """ Single waiter to wait for a specific value to be set.
    """

    def __init__(self, key, verifier):
        self._value = None
        self._key = key
        self._verifier = verifier
        self._verified = False
        self._uuid = uuid.uuid4()
        self._cond = threading.Condition()

    def wait_until(self, timeout):
        """ Blocks the caller until the value passes the verifier. If the
         verifier is a function, call verifier(value) to verify if caller
         can be unblocked. If verifier is not a function, compare with value
         directly.

        :param verifier: used to verify if the new value of the key in dict
        can unblock caller
        :param timeout: number of seconds before timeout
        :return: the updated value
        """
        with self._cond:
            # Test set value being verified first in case
            # set_value being called before wait_until, thus
            # we could skip the timeout waiting.
            if self._verified:
                return self._value

            self._cond.wait(timeout)

            if self._verified:
                return self._value
            else:
                raise TimeoutError()

    def set_value(self, value):
        with self._cond:
            if _verify(self._verifier, value):
                self._verified = True
                self._value = value
                self._cond.notify()

    @property
    def uuid(self):
        return self._uuid

    @property
    def key(self):
        return self._key


def _verify(verifier, value):
    """ Use verifier to verify value.

    :param verifier: If function, call verifier(value), otherwise just test
    equality of verifier and value
    :param value: the value to be verified
    :return: boolean, True if passed verifier, Otherwise False
    """
    if hasattr(verifier, '__call__'):
        if verifier(value):
            return True
    else:
        if value == verifier:
            return True
    return False
