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

import gzip
import logging
import os
import sys
import time
import traceback
from functools import wraps
from logging.handlers import RotatingFileHandler
from logging.handlers import SysLogHandler

import common.excepthook
from common import services
from common.service_name import ServiceName
from common.thread import ThreadIdFilter
from common.thread import WorkerThreadNameFilter

SYSLOG_FORMAT = (
    "photon-controller-agent: %(levelname)s "
    "[%(system_thread_id)s:%(process)d:%(thread_name)s] "
    "[%(filename)s:%(funcName)s:%(lineno)d]%(request_id)s %(name)s: "
    "%(message)s")
FILE_LOG_FORMAT = (
    "%(levelname)-8s [%(asctime)s] "
    "[%(system_thread_id)s:%(process)d:%(thread_name)s] "
    "[%(filename)s:%(funcName)s:%(lineno)d]%(request_id)s %(name)s: "
    "%(message)s")
PLAIN_LOG_FORMAT = (
    "%(levelname)s: %(message)s"
)


class GzipRotatingFileHandler(RotatingFileHandler):
    """A rotating file handler that gzips rotated files."""

    def doRollover(self):
        self.stream.close()

        if self.backupCount > 0:
            # shift rotated log files.
            for i in range(self.backupCount - 1, 0, -1):
                src = "%s.%d.gz" % (self.baseFilename, i)
                dest = "%s.%d.gz" % (self.baseFilename, i + 1)
                if os.path.exists(src):
                    os.rename(src, dest)

            # compress the current log file.
            dest = self.baseFilename + ".1.gz"
            with open(self.baseFilename, 'rb') as source_file:
                dest_file = gzip.open(dest, 'wb')
                dest_file.writelines(source_file)
                dest_file.close()
            os.remove(self.baseFilename)

        self.mode = 'w'
        self.stream = self._open()


class RequestIdFilter(object):
    """Request id context log filter."""

    def __init__(self):
        self._request_id = services.get(ServiceName.REQUEST_ID)

    def filter(self, record):
        if ((hasattr(self._request_id, "value") and
             len(self._request_id.value) > 0)):
            record.request_id = " [Request:%s]" % self._request_id.value[-1]
        else:
            record.request_id = ""
        return True


# TODO(vspivak): replace with logging.config

def setup_logging(log_level=logging.INFO, logging_file=None,
                  logging_file_size=10 * 1000 * 1000,
                  logging_file_backup_count=0, console=False, syslog=True):
    """ Create a logger object with the given ident and make sure
        that the logging format matches ISO8601"""

    common.excepthook.install_hook()

    if console:
        handler = logging.StreamHandler()
        formatter = logging.Formatter(FILE_LOG_FORMAT)
        _add_handler(handler, formatter, log_level)
        # console log disables file and syslog
        return

    if logging_file:
        handler, formatter = _file_handler(logging_file, logging_file_size,
                                           logging_file_backup_count)
        _add_handler(handler, formatter, log_level)

    if syslog:
        handler, formatter = _syslog_handler(SysLogHandler.LOG_LOCAL0)
        _add_handler(handler, formatter, log_level)


def setup_hypervisor_logging(logging_file=None,
                             logging_file_size=10 * 1000 * 1000,
                             logging_file_backup_count=0, syslog=True):
    logger = logging.getLogger("__hypervisor__")
    logger.propagate = 0

    if logger.getEffectiveLevel() != logging.DEBUG:
        return

    if logging_file:
        handler, formatter = _file_handler(logging_file, logging_file_size,
                                           logging_file_backup_count)
        _add_handler(handler, formatter, logging.DEBUG, logger)

    if syslog:
        handler, formatter = _syslog_handler(SysLogHandler.LOG_LOCAL1)
        _add_handler(handler, formatter, logging.DEBUG, logger)


def _add_handler(handler, formatter, log_level, logger=None):
    if logger is None:
        logger = logging.getLogger()
    formatter.converter = time.gmtime
    handler.setFormatter(formatter)
    handler.addFilter(RequestIdFilter())
    handler.addFilter(ThreadIdFilter())
    handler.addFilter(WorkerThreadNameFilter())
    logger.addHandler(handler)
    logger.setLevel(log_level)


def _syslog_address():
    if sys.platform.lower() == "darwin":
        return "/var/run/syslog"
    else:
        return "/dev/log"


def _syslog_handler(facility):
        handler = SysLogHandler(_syslog_address(), facility)
        # The default SysLogHandler appends a zero-terminator,
        # which vmsyslogd does not consume and puts in the log file.
        handler.log_format_string = "<%d>%s"
        formatter = logging.Formatter(SYSLOG_FORMAT)
        return handler, formatter


def _file_handler(logging_file, size, backup_count):
        handler = GzipRotatingFileHandler(logging_file,
                                          maxBytes=size,
                                          backupCount=backup_count)
        formatter = logging.Formatter(FILE_LOG_FORMAT)
        return handler, formatter


def log_duration(func):
    """Log invocation duration.

    :type func: func
    :rtype: func
    """
    @wraps(func)
    def f(self, *args, **kwargs):
        start = time.time()
        try:
            return func(self, *args, **kwargs)
        finally:
            end = time.time()
            self._logger.info("%s took %fs", func.__name__, end - start)
    return f


def log_duration_with(log_level="info"):
    """Log invocation duration.

    :type log_level: str
    :rtype: func
    """
    def decorator(func):
        @wraps(func)
        def f(self, *args, **kwargs):
            start = time.time()
            try:
                return func(self, *args, **kwargs)
            finally:
                end = time.time()

                def gen_str():
                    def prepare_arg_str(arg, append_str):
                        tmp_args_str = ""
                        for c, i in enumerate(arg):
                            tmp_args_str += append_str(i, arg) + " "
                        return tmp_args_str

                    tmp_ret_str = "{0}: {1}{2}took {3}"

                    return tmp_ret_str.format(
                        func.__name__,
                        prepare_arg_str(args, lambda k, _: str(k)),
                        prepare_arg_str(
                            kwargs,
                            lambda k, d: str(k) + ":" + str(d[k])),
                        end - start)

                getattr(self._logger, log_level)(gen_str())

        return f
    return decorator


class log_wrapper(logging.Logger):
    """ Wrapper to log multi-line/exceptions better.

        This wrapper class is a bit of a hack to avoid a problem with
        multi-line logs including exception logs. With the default log class
        multi-line log messages don't get the log prefixes attached to each
        line but rather they are only attached to the first line and subsequent
        lines are printed as is. This class overwrites the _log function of the
        logging.Logger class and handles multi-line message/exceptions
        differently by prepending a log prefix to each line.
        See https://www.pivotaltracker.com/story/show/59457362 for motivation.
    """
    def __init__(self, name=None, level=logging.NOTSET):
        logging.Logger.__init__(self, name, level)

    def _log(self, level, msg, args, exc_info=None, extra=None):
        if args:
            fmt_msg = msg % args
        else:
            fmt_msg = "%s" % (msg)
        (fn, lno, func) = self.findCaller()
        for l in fmt_msg.splitlines():
            record = self.makeRecord(self.name, level,
                                     fn, lno, l.strip(os.linesep),
                                     None, None, func, extra)
            self.handle(record)
        if exc_info:
            if not isinstance(exc_info, tuple):
                exc_info = sys.exc_info()
            (tp, val, tb) = exc_info
            for entry in traceback.format_exception(tp, val, tb):
                for l in entry.splitlines():
                    record = self.makeRecord(self.name, level,
                                             fn, lno, l.strip(os.linesep),
                                             None, None, func, None)
                    self.handle(record)


# This overwrites globals in the logging module. They will remain the same for
# every logging import once common.log has been included.
logging.setLoggerClass(log_wrapper)
logging.root = log_wrapper("root", logging.WARNING)
logging.Logger.root = logging.root
logging.Logger.manager = logging.Manager(logging.root)
logging.raiseExceptions = False
