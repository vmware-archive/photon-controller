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
# This script takes the input and configures the test
# template accordingly. Set the following values as
# required.
#
# PER_HOST_RAM = Memory capacity of each host.
# PER_VM_RAM   = Memory capacity of each VM. Take this from
#                the VM flavor.
# RAM_LOAD_FACTOR = Target Memory utilization of the cloud.
#
import os
import yaml
import sys

PER_HOST_RAM = 132352
PER_VM_RAM = 8192
RAM_LOAD_FACTOR = 0.50
tree_template_content = open("tree_template.yml", "r").read()
tree_template_yaml = yaml.load(tree_template_content)
request_template_content = open("requests_template.yml", "r").read()
request_template_yaml = yaml.load(request_template_content)


def main():
    d = sys.argv[1]
    if not os.path.exists(d):
        os.makedirs(d)
    hosts = int(d.split("_")[0])
    fanout = int(d.split("_")[1])
    fanout_ratio = float(d.split("_")[2])
    req_file = os.path.join(d, "requests.yml")
    tree_file = os.path.join(d, "tree.yml")
    results_file = os.path.join(d, "results.txt")
    print "Deleting files from", d
    if os.path.exists(req_file):
        os.remove(req_file)
    if os.path.exists(tree_file):
        os.remove(tree_file)
    if os.path.exists(results_file):
        os.remove(results_file)
    cur_load_factor = 0.0
    request_count = 0
    total_cloud_ram = hosts * PER_HOST_RAM
    while cur_load_factor < RAM_LOAD_FACTOR:
        request_count += 1
        request_load = request_count * PER_VM_RAM
        cur_load_factor = float(request_load) / total_cloud_ram
    print ("Number of Hosts:%d, Max Fanout:%d, "
           "Number of Requests:%d, Fanout Ratio: %f") % \
          (hosts, fanout, request_count, fanout_ratio)
    requests = request_template_yaml
    tree = tree_template_yaml
    requests["auto"]["count"] = request_count
    tree["num_hosts"] = hosts
    tree["root_config"]["max_fanout"] = fanout
    tree["root_config"]["fanout_ratio"] = fanout_ratio
    with open(tree_file, 'w') as t:
        t.write(yaml.dump(tree, default_flow_style=False))
    with open(req_file, 'w') as r:
        r.write(yaml.dump(requests, default_flow_style=False))
    print "Generated test files for", d

if __name__ == "__main__":
    main()
