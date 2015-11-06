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

require 'vagrant-guests-photon/cap/change_host_name'
require 'spec_helper'

describe VagrantPlugins::Photon::Cap::ChangeHostName do
  include_context 'machine'

  it "should change hostname when hostname is differ from current" do
    hostname = 'vagrant-photon'
    expect(communicate).to receive(:test).with("sudo hostname --fqdn | grep 'vagrant-photon'")
    communicate.should_receive(:sudo).with("hostname #{hostname.split('.')[0]}")
    described_class.change_host_name(machine, hostname)
  end

  it "should not change hostname when hostname equals current" do
    hostname = 'vagrant-photon'
    communicate.stub(:test).and_return(true)
    communicate.should_not_receive(:sudo)
    described_class.change_host_name(machine, hostname)
  end
end
