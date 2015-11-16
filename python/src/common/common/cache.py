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

import random
import time


CACHE_LIMIT = 100


def cached(ttl=0):
    """ A simple implementation for caching results of methods. Results are
    saved in dict based on method and args of the method (kwargs is also
    expected). The expiration of cache will not be triggered automatically,
    unless a new value is requested, then the outdated value in cache will be
    replaced. The cache can only hold 100 items, to avoid memory leak due to
    unexpected use of this cache. If the cache has more than 100 items,
    a random item is dropped no matter it's expired or not.

    The cache is shared across all objects of the same class. In future,
    anyone who wants per object cache, rewrite _get_cache_dict. But be
    careful about the possible race condition, though I think for cache,
    it's not harmful not to lock _get_cache_dict.

    class Sample:

        @cached(ttl=10)
        def expensive_operation():
            sleep(1)  # simulate operation which takes a second to finish
            return some_value

    s = Sample()
    s.expensive_operation()  # take a second
    s.expensive_operation()  # get value from cache
    sleep(10)
    s.expensive_operation()  # take a second, because cache is expired.

    :param ttl: cache time of seconds to live. ttl<=0 mean cache never expires.
    """
    # TODO(agui): LRU expire cache
    def decorated(func):
        def nested(self, *args, **kwargs):
            cache = _get_cache_dict(self, nested)
            now = time.time()

            key = (self, args + tuple(sorted(kwargs.items())))

            # Cache miss, get result and update cache
            if key not in cache:
                v = func(self, *args, **kwargs)
                # If length of cache exceeds limit, drop one random item.
                if len(cache) >= CACHE_LIMIT:
                    cache.pop(random.choice(cache.keys()))
                cache[key] = (v, now)
                return v

            # Cache hit
            value, last_updated = cache[key]

            # Cache hit and it's not expired yet
            if ttl <= 0 or last_updated + ttl > now:
                return value

            # Cache hit but expired, get result and renew the cache
            v = func(self, *args, **kwargs)
            cache[key] = (v, now)
            return v

        return nested

    return decorated


def _get_cache_dict(self, func):
    return func.__dict__
