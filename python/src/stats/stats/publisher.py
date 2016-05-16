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


class Publisher(with_metaclass(abc.ABCMeta, object)):
    """The base class for any stats publisher."""

    @abc.abstractmethod
    def publish(self, stats):
        """Publish metrics available since a particular time.

        The method is expected to be called periodically.

        :type stats: dict of metric_key to list-of-(ts, value)-tuples
        """
        pass
