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

describe "service configuration", zookeeper: true  do
  let(:zkClient) { ApiClientHelper.zookeeper }
  let(:deployment) {client.find_all_api_deployments.items.first}

  after(:each) do
    client.resume_system(deployment.id)
  end

  it "apife configuration status is set to PAUSE successfully" do
    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to be_nil

    client.pause_system(deployment.id)

    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to eq "PAUSED"
  end

  it "apife configuration status is set to PAUSE_BACKGROUND successfully" do
    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to be_nil

    client.pause_background_tasks(deployment.id)

    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to eq "PAUSED_BACKGROUND"
  end

  it "apife configuration status is set to PAUSE/PAUSE_BACKGROUND/RESUME successfully" do
    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to be_nil

    client.pause_system(deployment.id)

    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to eq "PAUSED"

    client.pause_background_tasks(deployment.id)

    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to eq "PAUSED_BACKGROUND"

    client.resume_system(deployment.id)

    value = zkClient.get(path: "/config/apife/status")
    expect(value).to_not be_nil
    expect(value[:data]).to be_nil
  end
end
