# Copyright 2016 VMware, Inc. All Rights Reserved.
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

describe EsxCloud::GoCliClient do

  before(:each) do
    @api_client = double(EsxCloud::ApiClient)
    EsxCloud::ApiClient.stub(:new).with(any_args).and_return(@api_client)
    allow(File).to receive(:executable?).with("/path/to/cli").and_return(true)
  end

  let(:client) {
    cmd = "/path/to/cli target set --nocertcheck localhost:9000"
    expect(EsxCloud::CmdRunner).to receive(:run).with(cmd)
    EsxCloud::GoCliClient.new("/path/to/cli", "localhost:9000")
  }


  it "creates a Kubernetes Cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster_id ="bar"
    cluster = double(EsxCloud::Cluster, :id => cluster_id)

    spec = {
        :name => "cluster1",
        :type => "KUBERNETES",
        :vmFlavor => "core-100",
        :diskFlavor => "core-100",
        :vmNetworkId => "network-id",
        :workerCount => 2,
        :extendedProperties => {"dns" => "10.0.0.1",
                                 "gateway" => "10.0.0.2",
                                 "netmask" => "255.255.255.128",
                                 "etcd_ip1" => "10.0.0.3",
                                 "etcd_ip2" => "10.0.0.4",
                                 "etcd_ip3" => "10.0.0.5",
                                 "master_ip" => "10.0.0.6",
                                 "container_network" => "10.0.0.0/12"}
    }
    result = "bar
Note: the cluster has been created with minimal resources. You can use the cluster now.
A background task is running to gradually expand the cluster to its target capacity.
You can run 'cluster show bar' to see the state of the cluster."

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
    .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'KUBERNETES' " +
              "-v 'core-100' -d 'core-100' -w 'network-id' -c 2 --dns '10.0.0.1' --gateway '10.0.0.2' " +
              "--netmask '255.255.255.128' --etcd1 '10.0.0.3' --etcd2 '10.0.0.4' --etcd3 '10.0.0.5' " +
              "--master-ip '10.0.0.6' --container-network '10.0.0.0/12'")
    .and_return(result)

    expect(client).to receive(:find_cluster_by_id).with(cluster_id).and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "creates a Mesos cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster_id = "bar"
    cluster = double(EsxCloud::Cluster, :id => cluster_id)

    spec = {
        :name => "cluster1",
        :type => "MESOS",
        :vmFlavor => "core-100",
        :diskFlavor => "core-100",
        :vmNetworkId => "network-id",
        :workerCount => 2,
        :extendedProperties => {"dns" => "10.0.0.1",
                                 "gateway" => "10.0.0.2",
                                 "netmask" => "255.255.255.128",
                                 "zookeeper_ip1" => "10.0.0.3",
                                 "zookeeper_ip2" => "10.0.0.4",
                                 "zookeeper_ip3" => "10.0.0.5"}
    }

   result = "bar
Note: the cluster has been created with minimal resources. You can use the cluster now.
A background task is running to gradually expand the cluster to its target capacity.
You can run 'cluster show bar' to see the state of the cluster."

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
    .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'MESOS' " +
              "-v 'core-100' -d 'core-100' -w 'network-id' -c 2 --dns '10.0.0.1' " +
              "--gateway '10.0.0.2' --netmask '255.255.255.128' --zookeeper1 '10.0.0.3' "+
              "--zookeeper2 '10.0.0.4' --zookeeper3 '10.0.0.5'")
    .and_return(result)

    expect(client).to receive(:find_cluster_by_id).with(cluster_id).and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "creates a Swarm cluster" do
    tenant = double(EsxCloud::Tenant, :name => "t1")
    project = double(EsxCloud::Project, :id => "foo", :name => "p1")
    client.project_to_tenant["foo"] = tenant

    cluster_id = "bar"
    cluster = double(EsxCloud::Cluster, :id => "bar")

    spec = {
        :name => "cluster1",
        :type => "SWARM",
        :vmFlavor => "core-100",
        :diskFlavor => "core-100",
        :vmNetworkId => "network-id",
        :workerCount => 2,
        :extendedProperties => {"dns" => "10.0.0.1",
                                 "gateway" => "10.0.0.2",
                                 "netmask" => "255.255.255.128",
                                 "etcd_ip1" => "10.0.0.3",
                                 "etcd_ip2" => "10.0.0.4",
                                 "etcd_ip3" => "10.0.0.5"}
    }

    result = "bar
Note: the cluster has been created with minimal resources. You can use the cluster now.
A background task is running to gradually expand the cluster to its target capacity.
You can run 'cluster show bar' to see the state of the cluster."

    expect(client).to receive(:find_project_by_id).with("foo").and_return(project)
    expect(client).to receive(:run_cli)
    .with("cluster create -t 't1' -p 'p1' -n 'cluster1' -k 'SWARM' " +
              "-v 'core-100' -d 'core-100' -w 'network-id' -c 2 --dns '10.0.0.1' " +
              "--gateway '10.0.0.2' --netmask '255.255.255.128' --etcd1 '10.0.0.3' "+
              "--etcd2 '10.0.0.4' --etcd3 '10.0.0.5'")
    .and_return(result)

    expect(client).to receive(:find_cluster_by_id).with(cluster_id).and_return(cluster)

    expect(client.create_cluster("foo", spec)).to eq(cluster)
  end

  it "finds Cluster by id" do

    cluster_id = double("bar")
    cluster_hash ={"id" => cluster_id,
                   "name" => "cluster1",
                   "state" => "READY",
                   "type" => "SWARM",
                   "workerCount" => 2,
                   "extendedProperties" =>{"dns" => "10.0.0.1",
                                           "gateway" => "10.0.0.2",
                                           "netmask" => "255.255.255.128",
                                           "etcd_ips"=>"10.0.0.3,10.0.0.4"}
                 }

    cluster = double(EsxCloud::Cluster.create_from_hash(cluster_hash))

    result = "bar cluster1  READY SWARM 2 etcd_ips:10.0.0.3,10.0.0.4 gateway:10.0.0.2 netmask:255.255.255.128 dns:10.0.0.1"
    expect(client).to receive(:run_cli).with("cluster show #{cluster_id}").and_return(result)
    expect(client).to receive(:get_cluster_from_response).with(result).and_return(cluster)

    client.find_cluster_by_id(cluster_id).should == cluster
  end

  it "deletes a Cluster" do
    cluster_id = double("bar")
    expect(client).to receive(:run_cli).with("cluster delete '#{cluster_id}'")

    client.delete_cluster(cluster_id).should be_true
  end

  it "resizes a Cluster" do
    cluster_id = double("bar")
    expect(client).to receive(:run_cli).with("cluster resize '#{cluster_id}' '100'")

    client.resize_cluster(cluster_id, 100).should be_true
  end

  it "gets all vms in the specified Cluster" do
    cluster_id = double("bar")
    vms = double(EsxCloud::VmList)
    result ="vmId1  vm1 READY
             vmId2  vm2 READY"
    expect(client).to receive(:run_cli).with("cluster list_vms '#{cluster_id}'").and_return(result)
    expect(client).to receive(:get_vm_list_from_response).with(result).and_return(vms)

    client.get_cluster_vms(cluster_id).should == vms
  end

end
