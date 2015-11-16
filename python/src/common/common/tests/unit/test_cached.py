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

import threading
import time
import unittest

from common.cache import cached, _get_cache_dict
from common.lock import locked

from hamcrest import *  # noqa
from matchers import *  # noqa


class Counter:
    def __init__(self):
        self.count = 0
        self.lock = threading.Lock()

    @cached(ttl=0.1)
    def get_count_cached(self):
        self.count += 1
        return self.count

    @cached(ttl=0.1)
    @locked
    def get_count_cache_and_lock(self):
        self.count += 1
        return self.count

    @locked
    @cached(ttl=0.1)
    def get_count_lock_and_cache(self):
        self.count += 1
        return self.count

    @cached()
    def echo(self, num, **kwargs):
        self.count += 1
        return num

    @cached()
    def shout(self, num, **kwargs):
        self.count += 1
        return num


class TestCacheDecorator(unittest.TestCase):

    def setUp(self):
        # Clear all caches
        methods = [m for m in dir(Counter) if not m.startswith('__')]
        for method in methods:
            getattr(Counter, method).__dict__.clear()

    def test_cached(self):
        counter = Counter()
        for i in range(100):
            assert_that(counter.get_count_cached(), is_(1))

        time.sleep(0.1)
        assert_that(counter.get_count_cached(), is_(2))

    def test_cached_with_lock(self):
        counter = Counter()
        for i in range(100):
            assert_that(counter.get_count_cache_and_lock(), is_(1))
        for i in range(100):
            assert_that(counter.get_count_lock_and_cache(), is_(2))

        time.sleep(0.1)
        assert_that(counter.get_count_cache_and_lock(), is_(3))
        assert_that(counter.get_count_lock_and_cache(), is_(4))

    def test_cached_with_kwargs(self):
        counter = Counter()
        counter.echo('hello')
        assert_that(counter.count, is_(1))
        assert_that(self.cache_len(counter, Counter.echo), is_(1))

        counter.echo('hello', a=1, b=2)
        assert_that(counter.count, is_(2))
        assert_that(self.cache_len(counter, Counter.echo), is_(2))
        counter.echo('hello', a=1, b=2)
        assert_that(counter.count, is_(2))
        assert_that(self.cache_len(counter, Counter.echo), is_(2))
        counter.echo('hello', b=2, a=1)
        assert_that(counter.count, is_(2))
        assert_that(self.cache_len(counter, Counter.echo), is_(2))

        counter.echo('hello')
        assert_that(counter.count, is_(2))
        assert_that(self.cache_len(counter, Counter.echo), is_(2))

        counter.echo(1, a=1, b=2)
        assert_that(counter.count, is_(3))
        assert_that(self.cache_len(counter, Counter.echo), is_(3))

    def test_multiple_object_cache(self):
        counter1 = Counter()
        counter2 = Counter()

        counter1.echo(1)
        assert_that(self.cache_len(None, Counter.echo), is_(1))

        counter1.shout(1)
        assert_that(self.cache_len(None, Counter.echo), is_(1))
        assert_that(self.cache_len(None, Counter.shout), is_(1))

        counter2.echo(1)
        assert_that(self.cache_len(None, Counter.echo), is_(2))

    def test_cache_limit(self):
        counter = Counter()
        for i in range(0, 100):
            counter.echo(i)  # called
            counter.echo(i)  # cached
            assert_that(self.cache_len(counter, Counter.echo), is_(i+1))
        assert_that(counter.count, is_(100))
        assert_that(self.cache_len(counter, Counter.echo), is_(100))

        for i in range(100, 200):
            counter.echo(i)  # called
            counter.echo(i)  # cached
            assert_that(len(_get_cache_dict(counter, Counter.echo)), is_(100))
        assert_that(counter.count, is_(100+100))

    def cache_len(self, obj, func):
        return len(_get_cache_dict(obj, func))
