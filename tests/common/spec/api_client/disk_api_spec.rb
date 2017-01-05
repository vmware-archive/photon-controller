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

describe EsxCloud::ApiClient do

  before(:each) do
    @http_client = double(EsxCloud::HttpClient)
    EsxCloud::HttpClient.stub(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  describe "#create_disk" do
    it "creates a Disk" do
      disk = double(EsxCloud::Disk)

      expect(@http_client).to receive(:post_json)
                              .with("/projects/foo/disks", "payload")
                              .and_return(task_created("aaa"))

      expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "disk-id"))
      expect(@http_client).to receive(:get).with("/disks/disk-id").and_return(ok_response("disk"))
      expect(EsxCloud::Disk).to receive(:create_from_json).with("disk").and_return(disk)

      client.create_disk("foo", "payload").should == disk
    end
  end

  describe "#delete_disk" do
    context "when disk is deleted" do
      it "deletes a Disk" do
        expect(@http_client).to receive(:delete).with("/disks/foo").and_return(task_created("aaa"))
        expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

        client.delete_disk("foo").should be_true
      end
    end
  end

  describe "#find_all_disks" do
    it "finds all Disks" do
      disks = double(EsxCloud::Disk)

      expect(@http_client).to receive(:get).with("/projects/foo/disks").and_return(ok_response("disks"))
      expect(EsxCloud::DiskList).to receive(:create_from_json).with("disks").and_return(disks)

      client.find_all_disks("foo").should == disks
    end
  end

  describe "#find_disk_by_id" do
    it "finds Disk by id" do
      disk = double(EsxCloud::Disk)

      expect(@http_client).to receive(:get).with("/disks/foo")
                              .and_return(ok_response("disk"))
      expect(EsxCloud::Disk).to receive(:create_from_json).with("disk").and_return(disk)

      client.find_disk_by_id("foo").should == disk
    end
  end

  describe "#find_disks_by_name" do
    it "finds Disks by name" do
      disks = double(EsxCloud::DiskList)

      expect(@http_client).to receive(:get).with("/projects/foo/disks?name=bar")
                              .and_return(ok_response("disks"))
      expect(EsxCloud::DiskList).to receive(:create_from_json).with("disks").and_return(disks)

      client.find_disks_by_name("foo", "bar").should == disks
    end
  end

  describe "#get_disks_tasks" do
    it "gets Disks tasks" do
      tasks = double(EsxCloud::TaskList)

      expect(@http_client).to receive(:get).with("/disks/foo/tasks")
                              .and_return(ok_response("tasks"))
      expect(EsxCloud::TaskList).to receive(:create_from_json).with("tasks").and_return(tasks)

      client.get_disk_tasks("foo").should == tasks
    end
  end

end
