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

require 'vagrant-guests-photon/cap/configure_networks'
require 'spec_helper'

describe VagrantPlugins::Photon::Cap::ConfigureNetworks do
  include_context 'machine'

  it "should configure networks" do
    networks = [
      {:type => :static, :ip => '192.168.10.10', :netmask => '255.255.255.0', :interface => 1, :name => "eth0"},
      {:type => :dhcp, :interface => 2, :name => "eth1"},
      {:type => :static, :ip => '10.168.10.10', :netmask => '255.255.0.0', :interface => 3, :name => "docker0"},
    ]
    communicate.should_receive(:sudo).with("ifconfig | grep 'enp' | cut -f1 -d' '")
    communicate.should_receive(:sudo).with("ifconfig  192.168.10.10 netmask 255.255.255.0")
    communicate.should_receive(:sudo).with("ifconfig   netmask ")
    communicate.should_receive(:sudo).with("ifconfig  10.168.10.10 netmask 255.255.0.0")

    allow_message_expectations_on_nil
    machine.should_receive(:env).at_least(5).times
    machine.env.should_receive(:active_machines).at_least(:twice)
    machine.env.active_machines.should_receive(:first)
    machine.env.should_receive(:machine)

    described_class.configure_networks(machine, networks)
  end
end
