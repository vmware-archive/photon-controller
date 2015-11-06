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

describe "portgroup", management: true, dcp: true do

  describe "#find_portgroup_by_id" do

    it "should fail when portgroup does not exit" do
      error_msg = "Port Group #non-existing-portgroup not found"
      begin
        client.find_portgroup_by_id("non-existing-portgroup")
        fail("find_portgroup_by_id should fail when the portgroup does not exist")
      rescue EsxCloud::ApiError => e
        expect(e.response_code).to eq 404
        expect(e.errors.size).to eq 1
        expect(e.errors.first.code).to eq("PortGroupNotFound")
        expect(e.errors.first.message).to include(error_msg)
      rescue EsxCloud::CliError => e
        expect(e.output).to include(error_msg)
      end
    end

  end

  describe "#find_portgroups" do

    let(:name) { nil }
    let(:usage_tag) { nil }

    context "query params are valid" do

      context "no param is in query" do
        it "finds portgroups" do
          expect(client.find_portgroups(name, usage_tag).items).to be_empty
        end
      end

      context "only name is in query" do
        let(:name) { "p1" }

        it "finds portgroups" do
          expect(client.find_portgroups(name, usage_tag).items).to be_empty
        end
      end

      context "only usage tag is in query" do
        let(:usage_tag) { "MGMT" }

        it "finds portgroups" do
          expect(client.find_portgroups(name, usage_tag).items).to be_empty
        end
      end

      context "both name and usage tag is in query" do
        let(:name) { "p1" }
        let(:usage_tag) { "CLOUD" }

        it "finds portgroups" do
          expect(client.find_portgroups(name, usage_tag).items).to be_empty
        end
      end

    end

    context "query params are invalid" do

      let(:usage_tag) { "INVALID_USAGE_TAG" }

      it "fails to find portgroups" do
        begin
          client.find_portgroups(name, usage_tag)
          fail("find_portgroups should fail when the usage_tag is invalid")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
        rescue EsxCloud::CliError => e
          expect(e.output).to include("404")
        end
      end
    end

  end
end
