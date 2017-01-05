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

require_relative "../spec_helper"

describe EsxCloud::Host do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:host) { double(EsxCloud::Host) }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  it "delegates create to client" do
    spec = EsxCloud::HostCreateSpec.new("u1", "p1", ["CLOUD"], "10.146.36.34", {})
    expect(client).to receive(:create_host).with("foo", spec.to_hash).and_return(host)
    expect(EsxCloud::Host.create("foo", spec)).to eq host
  end

  it "delegates find by id to client" do
    expect(client).to receive(:mgmt_find_host_by_id).with("foo").and_return(host)
    expect(EsxCloud::Host.find_host_by_id("foo")).to eq host
  end

  it "delegates enter maintenance mode to client" do
    expect(client).to receive(:host_enter_maintenance_mode).with("foo").and_return(host)
    expect(EsxCloud::Host.enter_maintenance_mode("foo")).to eq host
  end

  it "delegates enter maintenance mode to client" do
    expect(client).to receive(:host_enter_suspended_mode).with("foo").and_return(host)
    expect(EsxCloud::Host.enter_suspended_mode("foo")).to eq host
  end

end
