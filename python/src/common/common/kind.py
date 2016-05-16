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

import enum
from enum import Enum

from common.equality import EqualityMixin
from gen.flavors.ttypes import Flavor as ThriftFlavor
from gen.flavors.ttypes import QuotaUnit
from gen.flavors.ttypes import QuotaLineItem as ThriftQuotaLineItem


class Kind(EqualityMixin):

    def __init__(self, name, flavors=None):
        if flavors is None:
            flavors = {}

        self.name = name
        self.flavors = flavors

    def __repr__(self):
        return "Kind(name=%s, flavors=%s)" % (self.name, self.flavors)


class Flavor(EqualityMixin):

    def __init__(self, name, cost=None):
        self.name = name

        if cost is None:
            self.cost = {}
        elif isinstance(cost, list):
            self.cost = {}
            for item in cost:
                self.cost[item.key] = item
        else:
            self.cost = cost

    def to_thrift(self):
        cost = []
        if isinstance(self.cost, dict):
            for key in self.cost:
                cost.append(self.cost[key].to_thrift())
        return ThriftFlavor(name=self.name, cost=cost)

    @staticmethod
    def from_thrift(thrift_flavor):
        if not thrift_flavor:
            return None

        cost = {}

        for thrift_item in thrift_flavor.cost:
            cost[thrift_item.key] = QuotaLineItem.from_thrift(thrift_item)

        return Flavor(thrift_flavor.name, cost)

    def __repr__(self):
        return "Flavor(name=%s, cost=%s)" % (self.name, self.cost)


@enum.unique
class Unit(Enum):
    COUNT = 1
    B = 2
    KB = 3
    MB = 4
    GB = 5


class QuotaLineItem(EqualityMixin):

    _CONVERSION = {
        Unit.B: 1024 ** 0,
        Unit.KB: 1024 ** 1,
        Unit.MB: 1024 ** 2,
        Unit.GB: 1024 ** 3
    }

    def __init__(self, key, value, unit):
        self.key = key
        self.value = value
        self.unit = unit

    def convert(self, unit):
        if unit == Unit.COUNT and self.unit == unit:
            return float(self.value)

        if (unit not in QuotaLineItem._CONVERSION or
                self.unit not in QuotaLineItem._CONVERSION):
            raise ValueError("invalid unit")

        return float(self.value) * QuotaLineItem._CONVERSION[self.unit] / \
            QuotaLineItem._CONVERSION[unit]

    def __repr__(self):
        return "QuotaLineItem(key=%s, value=%s, unit=%s)" % (
            self.key, self.value, self.unit)

    def to_thrift(self):
        unit_name = self.unit.name
        unit = getattr(QuotaUnit, unit_name)
        return ThriftQuotaLineItem(self.key, self.value, unit)

    @staticmethod
    def from_thrift(thrift_item):
        unit_name = QuotaUnit._VALUES_TO_NAMES[thrift_item.unit]
        unit = getattr(Unit, unit_name)
        return QuotaLineItem(thrift_item.key, thrift_item.value, unit)
