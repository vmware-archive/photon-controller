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

describe "service registration", zookeeper: true  do
  let(:client) { ApiClientHelper.zookeeper }

  ["housekeeper", "apife", "cloudstore", "root-scheduler"].each do |service|
    it "has at least one '#{service}' node registered" do
      service_nodes = client.get_children(path: "/services/#{service}")[:children]
      expect(service_nodes).to_not be_nil
      expect(service_nodes).to have_at_least(1).item
    end
  end
end
