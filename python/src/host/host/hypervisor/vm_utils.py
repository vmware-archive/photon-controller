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

import csv


def parse_vmdk(pathname):
    """
    :param pathname: the pathname of the vmdk file
    :returns dictionary: a dictionary containing
                         the key, values
    """
    dictionary = dict()
    with open(pathname, "r") as csvfile:
        reader = csv.reader(csvfile, delimiter='=',
                            escapechar='\\',
                            quoting=csv.QUOTE_ALL)
        for row in reader:
            if len(row) != 2:
                continue
            dictionary[row[0]] = row[1]
    return dictionary
