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

import abc

from six import with_metaclass


class Collector(with_metaclass(abc.ABCMeta, object)):
    """The base class for any stats collector."""

    @abc.abstractmethod
    def collect(self, since=None):
        """Collect new metrics.

        If since is a valid timestamp, returns metrics' new values available
        since the timestamp. Otherwise, returns the latest value of each metric
        associated with collector.

        The method is expected to be called periodically.

        :type since: int, seconds since epoch
        """
        pass
