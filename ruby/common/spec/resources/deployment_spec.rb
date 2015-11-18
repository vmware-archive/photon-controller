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

describe EsxCloud::Deployment do

  let(:client) { double(EsxCloud::ApiClient) }
  let(:deployment) { double(EsxCloud::Deployment) }

  before(:each) do
    allow(EsxCloud::Config).to receive(:client).and_return(client)
  end

  it "delegates create to client" do
    spec = EsxCloud::DeploymentCreateSpec.new(["deployment_id"], EsxCloud::AuthInfo.new(false), "0.0.0.1", "0.0.0.2", true)
    expect(client).to receive(:create_api_deployment).with(spec.to_hash).and_return(deployment)
    expect(EsxCloud::Deployment.create(spec)).to eq deployment
  end

  it "delegates find_by_id to client" do
    expect(client).to receive(:find_deployment_by_id).with("deployment_id").and_return(deployment)
    expect(EsxCloud::Deployment.find_deployment_by_id("deployment_id")).to eq deployment
  end

  it "delegates delete to client" do
    expect(client).to receive(:delete_api_deployment).with("deployment_id").and_return(true)
    expect(EsxCloud::Deployment.delete("deployment_id")).to eq true
  end

  it "delegates update to client" do
    security_groups = ["adminGroup1", "adminGroup2"]
    security_groups_in_hash = {items: security_groups}
    expect(client).to receive(:update_security_groups)
                      .with("deployment_id", security_groups_in_hash)
                      .and_return(task_done("task1", "entity-id"))

    EsxCloud::Deployment.update_security_groups("deployment_id", security_groups_in_hash)
  end

  it "delegates configure cluster to client" do
    spec = EsxCloud::ClusterConfigurationSpec.new("KUBERNETES", "imageId")
    configuration = EsxCloud::ClusterConfiguration.new("KUBERNETES", "imageId")
    expect(client).to receive(:configure_cluster)
                      .with("deployment_id", spec.to_hash)
                      .and_return(configuration)

    EsxCloud::Deployment.configure_cluster("deployment_id", spec)
  end

end
