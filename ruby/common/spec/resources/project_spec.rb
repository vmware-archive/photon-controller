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

describe EsxCloud::Project do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "delegates create to API client" do
    spec = EsxCloud::ProjectCreateSpec.new(
        "p1",
        "dev",
        [
            EsxCloud::QuotaLineItem.new("a", "b", "c"),
            EsxCloud::QuotaLineItem.new("d", "e", "f")
        ]
    )

    spec_hash = {
        :name => "p1",
        :resourceTicket => {
            :name => "dev",
            :limits => [
                {:key => "a", :value => "b", :unit => "c"},
                {:key => "d", :value => "e", :unit => "f"}
            ]
        },
        :securityGroups => nil
    }

    expect(@client).to receive(:create_project).with("foo", spec_hash)

    EsxCloud::Project.create("foo", spec)
  end

  it "delegates delete to API client" do
    expect(@client).to receive(:delete_project).with("foo")
    EsxCloud::Project.delete("foo")
  end

  it "delegates find_by_id to API client" do
    expect(@client).to receive(:find_project_by_id).with("foo")
    EsxCloud::Project.find_by_id("foo")
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "p_name",
        "resourceTicket" => {
            "tenantTicketId" => "some-tenant-ticket-id",
            "tenantTicketName" => "some-tenant-ticket-name",
            "usage" => [],
            "limits" => []}
    }
    from_hash = EsxCloud::Project.create_from_hash(hash)
    from_json = EsxCloud::Project.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |project|
      project.id.should == "foo"
      project.name.should == "p_name"
      project.resource_ticket.limits.should == []
      project.resource_ticket.tenant_ticket_name == "some-tenant-ticket-name"
      project.resource_ticket.tenant_ticket_id == "some-tenant-ticket-id"
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Project.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

  context "when a project is valid" do
    it "can create VMs inside the project" do
      vm_options = {name: "vm_name", flavor: "core-100", image_id: "image-id", disks: []}
      ticket = double(EsxCloud::ResourceTicket)
      vm_spec = double(EsxCloud::VmCreateSpec)

      EsxCloud::VmCreateSpec.stub(:new).with("vm_name", "core-100", "image-id", [], nil, nil, nil).and_return(vm_spec)
      expect(EsxCloud::Vm).to receive(:create).with("foo", vm_spec)

      EsxCloud::Project.new("foo", "name", ticket).create_vm(vm_options)
    end

    it "can create Disks inside the project" do
      disk_options = {:name => "disk_name", :kind => "ephemeral", :flavor => "core-100", :capacity_gb => 2, :affinities => [{:id => "vm1", :kind => "vm"}], :tags => nil}
      ticket = double(EsxCloud::ResourceTicket)
      disk_spec = double(EsxCloud::DiskCreateSpec)

      EsxCloud::DiskCreateSpec.stub(:new).with("disk_name", "ephemeral", "core-100", 2, [{:id => "vm1", :kind => "vm"}], nil).and_return(disk_spec)
      expect(EsxCloud::Disk).to receive(:create).with("foo", disk_spec)

      EsxCloud::Project.new("foo", "name", ticket).create_disk(disk_options)
    end
  end

  it "can be deleted" do
    ticket = double(EsxCloud::ResourceTicket)
    expect(@client).to receive(:delete_project).with("foo")

    EsxCloud::Project.new("foo", "name", ticket).delete
  end

end
