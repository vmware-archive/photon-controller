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
    allow(EsxCloud::HttpClient).to receive(:new).and_return(@http_client)
  end

  let(:client) {
    EsxCloud::ApiClient.new("localhost:9000")
  }

  describe "#create_cluster" do
    it "creates a Cluster" do
      cluster = double(EsxCloud::Cluster)

      expect(@http_client).to receive(:post_json)
                              .with("/projects/foo/clusters", "payload")
                              .and_return(task_created("aaa"))

      expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "cluster-id"))
      expect(@http_client).to receive(:get).with("/clusters/cluster-id").and_return(ok_response("cluster"))
      expect(EsxCloud::Cluster).to receive(:create_from_json).with("cluster").and_return(cluster)

      expect(client.create_cluster("foo", "payload")).to eq(cluster)
    end
  end

  describe "#resize_cluster" do
    it "resizes a Cluster" do
      cluster = double(EsxCloud::Cluster)

      expect(@http_client).to receive(:post_json)
                              .with("/clusters/foo/resize", {:newWorkerCount=>100})
                              .and_return(task_created("aaa"))
      expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "cluster-id"))

      expect(client.resize_cluster("foo", 100)).to eq(true)
    end
  end


  describe "#delete_cluster" do
    it "deletes a Cluster" do
      cluster = double(EsxCloud::Cluster)

      expect(@http_client).to receive(:delete).with("/clusters/foo").and_return(task_created("aaa"))
      expect(@http_client).to receive(:get).with(URL_HOST + "/tasks/aaa").and_return(task_done("aaa", "foo"))

      client.delete_cluster("foo").should be_true
    end
  end

  describe "#find_cluster_by_id" do
    it "finds Cluster by id" do
      cluster = double(EsxCloud::Cluster)

      expect(@http_client).to receive(:get).with("/clusters/foo")
                              .and_return(ok_response("cluster"))
      expect(EsxCloud::Cluster).to receive(:create_from_json).with("cluster").and_return(cluster)

      expect(client.find_cluster_by_id("foo")).to eq(cluster)
    end
  end

  describe "#get_cluster_vms" do
    it "gets all vms in the specified Cluster" do
      vms = double(EsxCloud::VmList)

      expect(@http_client).to receive(:get).with("/clusters/foo/vms").and_return(ok_response("vms"))
      expect(EsxCloud::VmList).to receive(:create_from_json).with("vms").and_return(vms)

      expect(client.get_cluster_vms("foo")).to eq(vms)
    end
  end

end
