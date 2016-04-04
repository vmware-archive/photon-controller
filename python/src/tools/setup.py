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
from setuptools import setup, find_packages

with open('VERSION', 'r') as f:
    version = f.readline().strip()

setup(name='photon.controller.tools',
      version=version,
      description="Photon Controller Tools",
      author='VMware',
      author_email='support@vmware.com',
      url='http://www.vmware.com',
      packages=find_packages(),
      include_package_data=True,
      zip_safe=False,
      install_requires=[
          'photon.controller.common',
          'thrift==0.9.3',
      ]
      )
