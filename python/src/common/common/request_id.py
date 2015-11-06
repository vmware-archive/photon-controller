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

import common
from common.service_name import ServiceName


class RequestIdFilter(object):
    """Request id context log filter."""

    def __init__(self):
        self._request_id = common.services.get(ServiceName.REQUEST_ID)

    def filter(self, record):
        if ((hasattr(self._request_id, "value") and
             len(self._request_id.value) > 0)):
            record.request_id = " [Request:%s]" % self._request_id.value[-1]
        else:
            record.request_id = ""
        return True


class RequestIdExecutor(object):
    """Executor wrapper for propagating the request id."""

    def __init__(self, executor):
        self._executor = executor
        self._request_id_store = common.services.get(ServiceName.REQUEST_ID)
        self._logger = logging.getLogger(__name__)

    def submit(self, fn, *args, **kwargs):
        if ((hasattr(self._request_id_store, "value") and
             len(self._request_id_store.value) > 0)):
            request_id = self._request_id_store.value[-1]
        else:
            request_id = None
        return self._executor.submit(_wrapper, fn, self._request_id_store,
                                     request_id, *args, **kwargs)

    def __getattr__(self, name):
        return getattr(self._executor, name)


def _wrapper(fn, request_id_store, request_id, *args, **kwargs):
    try:
        if request_id:
            if not hasattr(request_id_store, "value"):
                request_id_store.value = []
            request_id_store.value.append(request_id)
        return fn(*args, **kwargs)
    finally:
        if request_id:
            request_id_store.value.pop()
