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

require 'spec_helper'
require 'vagrant-guests-photon/plugin'
require 'vagrant-guests-photon/cap/change_host_name'
require 'vagrant-guests-photon/cap/configure_networks'
require 'vagrant-guests-photon/cap/docker'

describe VagrantPlugins::Photon::Plugin do
    it "should be loaded with photon" do
      expect(described_class.components.guests[:photon].first).to eq(VagrantPlugins::Photon::Guest)
    end

    {
      :docker_daemon_running => VagrantPlugins::Photon::Cap::Docker,
      :change_host_name      => VagrantPlugins::Photon::Cap::ChangeHostName,
      :configure_networks    => VagrantPlugins::Photon::Cap::ConfigureNetworks,
    }.each do |cap, cls|
      it "should be capable of #{cap} with photon" do
        expect(described_class.components.guest_capabilities[:photon][cap])
          .to eq(cls)
      end
    end
end
