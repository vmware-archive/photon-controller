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

import common
import logging
import sys
import time
import uuid

from functools import wraps

from common.lock_vm import ConcurrentVmOperation
from common.service_name import ServiceName
from common.photon_thrift.validation import deep_validate


def log_request(func=None, log_level=logging.INFO):
    """Log thrift requests decorator.

    :type func: func
    :type log_level: int
    :rtype: func
    """

    # support calls with and without arguments @log_request and @log_request()
    if func is None:
        def f(func):
            return log_request(func, log_level)
        return f
    else:
        @wraps(func)
        def f(self, request):
            start = time.time()

            request_id = common.services.get(ServiceName.REQUEST_ID)
            no_request_id = False

            if not hasattr(request_id, "value"):
                request_id.value = []

            try:
                request_id_value = request.tracing_info.request_id
            except AttributeError:
                no_request_id = True

            if no_request_id or request_id.value is None:
                request_id_value = uuid.uuid4()

            request_id.value.append(request_id_value)

            try:
                if no_request_id:
                    self._logger.log(log_level, "%s no tracing", str(request))
                else:
                    self._logger.log(log_level, "%s", str(request))
                response = func(self, request)
                end = time.time()
                self._logger.log(log_level, "result:%d [Duration:%f] %s",
                                 response.result, end - start, str(response))
                return response
            finally:
                request_id.value.pop()

        return f


def error_handler(response_class, result_code_class):
    """Thrift response error handler.

    :type response_class: class
    :type result_code_class: class
    :rtype: func
    """

    def decorator(func):
        @wraps(func)
        def f(self, request):
            try:
                agent_config = common.services.get(ServiceName.AGENT_CONFIG)
                if (agent_config.reboot_required):
                    response = response_class()
                    response.result = result_code_class.SYSTEM_ERROR
                    response.error = "Agent rebooting"
                    return response
            except:
                # Just ignore
                pass
            try:
                deep_validate(request)
                response = func(self, request)
                deep_validate(response)
                return response
            except ConcurrentVmOperation:
                self._logger.info("Concurrent Vm operation on %s" %
                                  request.vm_id)
                response = response_class()
                response.result = result_code_class.CONCURRENT_VM_OPERATION
                response.error = str(sys.exc_info())
                return response
            except:
                exception = sys.exc_info()
                self._logger.warning("Error calling %s" % str(request),
                                     exc_info=exception)
                response = response_class()
                response.result = result_code_class.SYSTEM_ERROR
                response.error = str(exception[1])
                if not response.error:
                    response.error = exception[0].__name__
                return response
        return f
    return decorator
