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


class AlreadyLocked(Exception):
    pass


def lock_with(lock_name=None):
    """Decorator which locks the specific lock. If lock is not specified,
       use self.lock."""

    def decorator(func):
        def nested(self, *args, **kwargs):
            lock = getattr(self, lock_name or "lock")

            lock.acquire()
            try:
                return func(self, *args, **kwargs)
            finally:
                lock.release()

        return nested

    return decorator


def locked(func):
    """Decorator which locks self.lock."""

    def nested(self, *args, **kwargs):
        self.lock.acquire()
        try:
            return func(self, *args, **kwargs)
        finally:
            self.lock.release()

    return nested


def lock_non_blocking(func):
    """Decorator which attempt to lock self.lock.

    It throws AlreadyLock if unable to do so.
    """

    def nested(self, *args, **kwargs):
        acquired = self.lock.acquire(False)
        if not acquired:
            raise AlreadyLocked()
        try:
            return func(self, *args, **kwargs)
        finally:
            self.lock.release()

    return nested
