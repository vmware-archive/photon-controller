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

require "vagrant"

module VagrantPlugins
  module Photon
    class Plugin < Vagrant.plugin("2")
      name "Photon guest"
      description "Photon guest support."

      %w{photon vagrant_photon devbox_photon}.each do |os|
        guest os, "linux" do
          require_relative "guest"
          Guest
        end

        guest_capability os, "change_host_name" do
          require_relative "cap/change_host_name"
          Cap::ChangeHostName
        end

        guest_capability os, "configure_networks" do
          require_relative "cap/configure_networks"
          Cap::ConfigureNetworks
        end

        guest_capability os, "docker_daemon_running" do
          require_relative "cap/docker"
          Cap::Docker
        end
      end
    end
  end
end
