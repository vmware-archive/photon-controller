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

from psim.command import register_cmd
from . import help, quit, load_tree, print_tree, run, log, \
    seed, load_flavors, load_datastores, print_results, \
    check_results, auto_tree

register_cmd(help.HelpCmd)
register_cmd(load_tree.LoadTreeCmd)
register_cmd(print_tree.PrintTreeCmd)
register_cmd(quit.QuitCmd)
register_cmd(run.RunCmd)
register_cmd(log.LogCmd)
register_cmd(seed.SeedCmd)
register_cmd(load_flavors.LoadFlavorCmd)
register_cmd(load_datastores.LoadDatastoresCmd)
register_cmd(print_results.PrintResultsCmd)
register_cmd(check_results.CheckResultsCmd)
register_cmd(auto_tree.AutoTreeCmd)
