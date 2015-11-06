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
    allow(EsxCloud::ApiClient).to receive(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    EsxCloud::CliClient.new("/path/to/cli", "localhost:9000")
  }

  it "creates a Kubernetes Cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster = double(EsxCloud::Cluster, :id => "bar")

    spec = {
      :name => "cluster1",
      :type => "KUBERNETES",
      :vm_flavor => "core-100",
      :disk_flavor => "core-100",
      :network_id => "network-id",
      :slave_count => 2,
      :extended_properties => {"dns" => "10.0.0.1",
                               "gateway" => "10.0.0.2",
                               "netmask" => "255.255.255.128",
                               "etcd_ip1" => "10.0.0.3",
                               "etcd_ip2" => "10.0.0.4",
                               "etcd_ip3" => "10.0.0.5",
                               "master_ip" => "10.0.0.6",
                               "container_network" => "10.0.0.0/12"}
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'KUBERNETES' " +
                            "-v 'core-100' -d 'core-100' -w 'network-id' -s 2 --dns '10.0.0.1' --gateway '10.0.0.2' " +
                            "--netmask '255.255.255.128' --etcd1 '10.0.0.3' --etcd2 '10.0.0.4' --etcd3 '10.0.0.5' " +
                            "--master-ip '10.0.0.6' --container-network '10.0.0.0/12'")
                      .and_return("Cluster 'cluster-id-created' created")

    expect(client).to receive(:find_cluster_by_id).with("cluster-id-created").and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "creates a Mesos cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster = double(EsxCloud::Cluster, :id => "bar")

    spec = {
      :name => "cluster1",
      :type => "MESOS",
      :vm_flavor => "core-100",
      :disk_flavor => "core-100",
      :network_id => "network-id",
      :slave_count => 2,
      :extended_properties => {"dns" => "10.0.0.1",
                               "gateway" => "10.0.0.2",
                               "netmask" => "255.255.255.128",
                               "zookeeper_ip1" => "10.0.0.3",
                               "zookeeper_ip2" => "10.0.0.4",
                               "zookeeper_ip3" => "10.0.0.5"}
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'MESOS' " +
                            "-v 'core-100' -d 'core-100' -w 'network-id' -s 2 --dns '10.0.0.1' " +
                            "--gateway '10.0.0.2' --netmask '255.255.255.128' --zookeeper1 '10.0.0.3' "+
                            "--zookeeper2 '10.0.0.4' --zookeeper3 '10.0.0.5'")
                      .and_return("Cluster 'cluster-id-created' created")

    expect(client).to receive(:find_cluster_by_id).with("cluster-id-created").and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "creates a Swarm cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster = double(EsxCloud::Cluster, :id => "bar")

    spec = {
      :name => "cluster1",
      :type => "SWARM",
      :vm_flavor => "core-100",
      :disk_flavor => "core-100",
      :network_id => "network-id",
      :slave_count => 2,
      :extended_properties => {"dns" => "10.0.0.1",
                               "gateway" => "10.0.0.2",
                               "netmask" => "255.255.255.128",
                               "etcd_ip1" => "10.0.0.3",
                               "etcd_ip2" => "10.0.0.4",
                               "etcd_ip3" => "10.0.0.5"}
    }

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
                      .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'SWARM' " +
                            "-v 'core-100' -d 'core-100' -w 'network-id' -s 2 --dns '10.0.0.1' " +
                            "--gateway '10.0.0.2' --netmask '255.255.255.128' --etcd1 '10.0.0.3' "+
                            "--etcd2 '10.0.0.4' --etcd3 '10.0.0.5'")
                      .and_return("Cluster 'cluster-id-created' created")

    expect(client).to receive(:find_cluster_by_id).with("cluster-id-created").and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "resizes a Cluster" do
    expect(@api_client).to receive(:resize_cluster).with("foo", 100).and_return("Cluster 'foo' resized.")

    client.resize_cluster("foo", 100).should be_true
  end

  it "deletes a Cluster" do
    expect(@api_client).to receive(:delete_cluster).with("foo").and_return("Cluster 'foo' deleted.")

    client.delete_cluster("foo").should be_true
  end

  it "finds Cluster by id" do
    cluster = double(EsxCloud::Cluster)
    expect(@api_client).to receive(:find_cluster_by_id).with("foo").and_return(cluster)
    client.find_cluster_by_id("foo").should == cluster
  end

  it "gets all vms in the specified Cluster" do
    vms = double(EsxCloud::VmList)
    expect(@api_client).to receive(:get_cluster_vms).with("foo").and_return(vms)
    client.get_cluster_vms("foo").should == vms
  end
end
