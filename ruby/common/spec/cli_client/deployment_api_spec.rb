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

describe EsxCloud::CliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) do
    EsxCloud::CliClient.new("/path/to/cli", "localhost:9000")
  end

  context "when auth is not enabled" do
    let(:spec) do
      EsxCloud::DeploymentCreateSpec.new(['d'], EsxCloud::AuthConfigurationSpec.new(false),
                                         EsxCloud::NetworkConfigurationCreateSpec.new(false),
                                         EsxCloud::StatsInfo.new(false),
                                         "0.0.0.1",
                                         "0.0.0.2",
                                         true)
    end

    it "creates an api deployment" do
      deployment = double(EsxCloud::Deployment, id: "d1")
      deployments = double(EsxCloud::DeploymentList, items: [deployment])
      expect(client).to receive(:run_cli)
                        .with("deployment create -i 'd' -s '0.0.0.1' -n '0.0.0.2' -v")
      expect(client).to receive(:find_all_api_deployments).and_return(deployments)

      expect(client.create_api_deployment(spec.to_hash)).to eq deployment
    end
  end

  context "when auth is enabled" do
    let(:spec) do
      EsxCloud::DeploymentCreateSpec.new(['d'],
                                         EsxCloud::AuthConfigurationSpec.new(true, 't', 'p', ['sg1', 'sg2']),
                                         EsxCloud::NetworkConfigurationCreateSpec.new(false),
                                         EsxCloud::StatsInfo.new(false),
                                         "0.0.0.1",
                                         "0.0.0.2",
                                         true)
    end

    it "creates an api deployment" do
      deployment = double(EsxCloud::Deployment, id: "d1")
      deployments = double(EsxCloud::DeploymentList, items: [deployment])

      expect(client).to receive(:run_cli)
                        .with("deployment create -i 'd' -s '0.0.0.1' -n '0.0.0.2' -v -a -t 't' -p 'p' -g 'sg1,sg2'")
      expect(client).to receive(:find_all_api_deployments).and_return(deployments)

      expect(client.create_api_deployment(spec.to_hash)).to eq deployment
    end
  end

  context "when stats is not enabled" do
    let(:spec) do
      EsxCloud::DeploymentCreateSpec.new(['d'], EsxCloud::AuthConfigurationSpec.new(false),
                                         EsxCloud::NetworkConfigurationCreateSpec.new(false),
                                         EsxCloud::StatsInfo.new(false),
                                         "0.0.0.1",
                                         "0.0.0.2",
                                         true)
    end

    it "creates an api deployment" do
      deployment = double(EsxCloud::Deployment, id: "d1")
      deployments = double(EsxCloud::DeploymentList, items: [deployment])
      expect(client).to receive(:run_cli)
                            .with("deployment create -i 'd' -s '0.0.0.1' -n '0.0.0.2' -v")
      expect(client).to receive(:find_all_api_deployments).and_return(deployments)

      expect(client.create_api_deployment(spec.to_hash)).to eq deployment
    end
  end

  context "when stats is enabled" do
    let(:spec) do
      EsxCloud::DeploymentCreateSpec.new(['d'],
                                         EsxCloud::AuthConfigurationSpec.new(true, 't', 'p', ['sg1', 'sg2']),
                                         EsxCloud::NetworkConfigurationCreateSpec.new(false),
                                         EsxCloud::StatsInfo.new(true, '0.1.2.3', '2004'),
                                         "0.0.0.1",
                                         "0.0.0.2",
                                         true)
    end

    it "creates an api deployment" do
      deployment = double(EsxCloud::Deployment, id: "d1")
      deployments = double(EsxCloud::DeploymentList, items: [deployment])

      expect(client).to receive(:run_cli)
                            .with("deployment create -i 'd' -s '0.0.0.1' -n '0.0.0.2' -d -e '0.1.2.3' -f '2004' -v -a -t 't' -p 'p' -g 'sg1,sg2'")
      expect(client).to receive(:find_all_api_deployments).and_return(deployments)

      expect(client.create_api_deployment(spec.to_hash)).to eq deployment
    end
  end

  it "pauses a system under deployment" do
    expect(client).to receive(:run_cli).with("deployment pause_system d1")
    expect(client.pause_system("d1")).to eq true
  end

  it "pauses_background_tasks under deployment" do
    expect(client).to receive(:run_cli).with("deployment pause_background_tasks d1")
    expect(client.pause_background_tasks("d1")).to eq true
  end

  it "resumes a system under deployment" do
    expect(client).to receive(:run_cli).with("deployment resume_system d1")
    expect(client.resume_system("d1")).to eq true
  end

  it "finds deployment by id" do
    deployment = double(EsxCloud::Deployment)

    expect(@api_client).to receive(:find_deployment_by_id).with("d1")
                           .and_return(deployment)
    expect(client.find_deployment_by_id("d1")).to eq deployment
  end

  it "finds all api deployments" do
    deployments = double(EsxCloud::DeploymentList)

    expect(@api_client).to receive(:find_all_api_deployments).and_return(deployments)
    expect(client.find_all_api_deployments).to eq deployments
  end

  it "deletes deployment by id" do
    expect(client).to receive(:run_cli).with("deployment delete 'foo'")
    client.delete_api_deployment("foo").should be_true
  end

  it "gets deployment vms" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:get_deployment_vms).with("foo").and_return(vms)
    client.get_deployment_vms("foo").should == vms
  end

  it "gets deployment hosts" do
    hosts = double(EsxCloud::HostList)
    expect(@api_client).to receive(:get_deployment_hosts).with("foo").and_return(hosts)
    client.get_deployment_hosts("foo").should == hosts
  end

  it "updates deployment security groups" do
    security_groups = {items: ["adminGroup1", "adminGroup2"]}
    expect(@api_client).to receive(:update_security_groups).with("foo", security_groups)

    client.update_security_groups("foo", security_groups)
  end

  it "enable_cluster_type for deployment" do
    expect(@api_client).to receive(:enable_cluster_type).with("foo", "payload")

    client.enable_cluster_type("foo", "payload")
  end

  it "enable_cluster_type for deployment" do
    expect(@api_client).to receive(:disable_cluster_type).with("foo", "payload")

    client.disable_cluster_type("foo", "payload")
  end
end
