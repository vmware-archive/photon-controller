# Copyright 2015 VMware, Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, without warranties or
# conditions of any kind, EITHER EXPRESS OR IMPLIED. See the License for the
# specific language governing permissions and limitations under the License.

require "tempfile"

require "vagrant/util/template_renderer"

module VagrantPlugins
  module Photon
    module Cap
      class ConfigureNetworks
        include Vagrant::Util

        def self.configure_networks(machine, networks)
          machine.communicate.tap do |comm|
            # Read network interface names
            interfaces = []
            comm.sudo("ifconfig | grep 'enp' | cut -f1 -d' '") do |_, result|
              interfaces = result.split("\n")
            end

            # Configure interfaces
            networks.each do |network|
              comm.sudo("ifconfig #{interfaces[network[:interface].to_i]} #{network[:ip]} netmask #{network[:netmask]}")
            end

            primary_machine_config = machine.env.active_machines.first
            primary_machine = machine.env.machine(*primary_machine_config, true)

            get_ip = lambda do |machine|
              ip = nil
              machine.config.vm.networks.each do |type, opts|
                if type == :private_network && opts[:ip]
                  ip = opts[:ip]
                  break
                end
              end

              ip
            end
          end
        end
      end
    end
  end
end
