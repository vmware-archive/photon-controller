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
import sys
import threading


def log_excepthook(tp, val, tb):
    """ Wrapper to print an exception through the logger. """
    logger = logging.getLogger()
    logger.error("Caught fatal exception", exc_info=(tp, val, tb))


def install_hook():
    """ Workaround for sys.excepthook thread bug

        Call once from __main__ before creating any threads.
        See http://bugs.python.org/issue1230540.

        Note: This changes thread behavior with regards to exceptions. Instead
              of killing the whole process on an exception the thread will
              print out the exception info and then return.
    """
    sys.excepthook = log_excepthook
    init_old = threading.Thread.__init__

    def init(self, *args, **kwargs):
        init_old(self, *args, **kwargs)
        run_old = self.run

        def run_with_except_hook(*args, **kw):
            try:
                run_old(*args, **kw)
            except (KeyboardInterrupt, SystemExit):
                raise
            except:
                sys.excepthook(*sys.exc_info())
        self.run = run_with_except_hook
    threading.Thread.__init__ = init
