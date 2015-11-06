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

describe EsxCloud::DiskList do

  describe "#create_from_jason" do
    context "when created from a valid Json configuration" do
      it "can be created from JSON" do
        disks = (1..3).map do |i|
          {
              "id" => "id-#{i}",
              "name" => "name-#{i}",
              "kind" => "ephemeral",
              "flavor" => "flavor-#{i}",
              "capacityGb" => 2,
              "datastore" => "datastore-1",
              "state" => "DETACHED"
          }
        end

        json = JSON.generate({"items" => disks})
        list = EsxCloud::DiskList.create_from_json(json)

        list.items.each_with_index do |item, i|
          item.should == EsxCloud::Disk.create_from_hash(disks[i])
        end
      end
    end

    context "when created from an empty Json configuration" do
      it "needs a proper payload format" do
        expect { EsxCloud::DiskList.create_from_json("[]") }.to raise_error(EsxCloud::UnexpectedFormat)
      end
    end
  end

end
