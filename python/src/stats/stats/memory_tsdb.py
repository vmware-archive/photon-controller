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

""" memory_tsdb.py

    Simple in-memory time-series database.
    It provides a simple interface

    add(<key string>, <timestamp>, <data>)

    to add a new value to the time series database. Timestamp is in seconds
    since the epoch (i.e the return value of time()). The key string has to
    be unique for this instance and serves as a unique identifier for a
    time series across the distributed system. Data can be any serializable
    structure.

    Retention: The memory DB retains values in the database according to the
    set retention policy. However values are not pruned by time but rather by
    their number. The DB calculates a max number of samples from the retention
    policy and retains that amount in the database. As a consequence if a stats
    producer diverts from the frequency set by the policy then the DB may
    contain a larger set of samples than the retention policy specified.
"""

from collections import defaultdict
from collections import deque
import math
import re
import threading

from common.lock import locked


class MemoryTimeSeriesDBPolicyError(Exception):
    pass


class MemoryTimeSeriesDB(object):
    """ Implements the memory DB as described above

    Attributes:
        _db: dict that uses the time series keys as identifiers
        _num_samples: Number of samples the DB will retain

    """
    def __init__(self):
        self._db = None
        self.lock = threading.Lock()

    @locked
    def set_policy(self, frequency="10s", duration="60s"):
        """ Calculate _num_samples based on given retention policy

        Args:
            frequency: A string describing the frequency with which stats will
                       be pushed into the DB. The format is nHnMnS with H
                       multiple of hours, M multiple of minutes and S multiple
                       of seconds. The frequency must be smaller than the
                       duration.
            duration: A string describing how long data should be retained in
                      the DB. The format is nHnMnS with H multiple of hours, M
                      multiple of minutes and S multiple of seconds.

        Raises:
            MemoryTimeSeriesDBPolicyError: One or more of frequency or duration
                                           arguments is invalid.
        """
        frequency_sum = self._parse_time(frequency)
        duration_sum = self._parse_time(duration)
        if duration_sum == 0 or frequency_sum == 0:
            raise MemoryTimeSeriesDBPolicyError(
                "duration=%s, frequency=%s" % (duration_sum, frequency_sum))
        self._num_samples = int(math.ceil(float(duration_sum) / frequency_sum))
        self._db = defaultdict(lambda: deque(maxlen=self._num_samples))

    def _parse_time(self, time_string):
        """ Calculate total number of seconds from time_string.

        Args:
            time_string: A string specifying a time interval in multiples of
                         hours, minutes or seconds.
        """
        seconds = 0
        time_regex = "((?P<hours>[0-9]+)[h|H])?"               \
                     "((?P<minutes>[0-9]+)[m|M])?"             \
                     "((?P<seconds>[0-9]+)[s|S])?"

        m = re.match(time_regex, time_string)
        if m:
            result = m.groupdict()
            for key, value in result.iteritems():
                if not value:
                    continue
                if key == "hours":
                    seconds += int(value) * 3600
                if key == "minutes":
                    seconds += int(value) * 60
                if key == "seconds":
                    seconds += int(value)
        return seconds

    @locked
    def add(self, key, timestamp, data):
        """ Add a new entry to the time series DB.

        Once number of entries prescribed by the retention policy is added to a
        series, the least recently added values will be dropped with new
        additions.

        Args:
            key: A string that acts as an identifier for the time series
            timestamp: Value in seconds since epoch
            data: Serializable data to be added to the series
        """
        self._db[key].append((timestamp, data))

    @locked
    def get_values_since(self, since, key):
        """ Get values added since particular time.

        Returns a list of zero or more (timestamp, value) tuples whose timstamp
        field is greater than since.
        """
        result = []
        for idx in reversed(range(0, len(self._db[key]))):
            value = self._db[key][idx]
            if value[0] > since:
                result.insert(0, value)
            else:
                break
        return result

    @locked
    def get_keys(self):
        """ Get list of metric keys stored in the db. """
        return self._db.keys()
