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

require "spec_helper"

describe "virtual_network_lifecyle", :nsx => true, :virtual_network => true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance
    @project = @seeder.project!
  end

  let(:spec) {
    EsxCloud::VirtualNetworkCreateSpec.new(random_name("network-"), "virtual network", "ROUTED")
  }

  it "Creates a virtual network, and then delete it" do
    task = create_network(@project.id, spec)
    network_id = task.entity_id.split("/").last

    EsxCloud::VirtualNetwork.create(project_id, spec)
    EsxCloud::VirtualNetwork.get(network_id)
    EsxCloud::VirtualNetwork.delete(network_id)
  end
end
