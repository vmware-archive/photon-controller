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

import os
import signal
import sys
import traceback


def dump_threads():
    result = []
    for thread_id, stack in sys._current_frames().items():
        result.append("\n* Thread: %s" % thread_id)
        for file_name, line_number, function_name, text in \
                traceback.extract_stack(stack):
            text = text.strip() if text else ""
            result.append("  - File: %s, line %d, in %s  %s" % (
                file_name, line_number, function_name, text))
    sys.stderr.write("Thread dump: %s" % "\n".join(result))


def suicide(dump=True):
    if dump:
        dump_threads()
    os.kill(os.getpid(), signal.SIGINT)
