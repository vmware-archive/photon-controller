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

describe EsxCloud::Step do
  let(:step_hash) do
    {
      "state" => "RUNNING",
      "sequence" => 0,
      "operation" => "DeleteVm",
      "errors" => [
        {"code" => "100", "message" => "foo", "data" => {"a" => "b"}},
        {"code" => "200", "message" => "bar", "data" => {"c" => "d"}}
      ],
      "warnings" => [
        {"code" => "300", "message" => "foo", "data" => {"a" => "b"}},
        {"code" => "400", "message" => "bar", "data" => {"c" => "d"}}
      ],
      "startedTime" => 100,
      "endTime" => 150,
    }
  end

  describe "::create_from_json" do
    it "instantiates object" do
      step = EsxCloud::Step.create_from_json(JSON.generate(step_hash))
      expect(step.state).to eq "RUNNING"
      expect(step.sequence).to eq 0
      expect(step.operation).to eq "DeleteVm"
      expect(step.errors.size).to eq 1
      expect(step.errors[0][0]).to be_a EsxCloud::TaskError
      expect(step.warnings.size).to eq 1
      expect(step.warnings[0][0]).to be_a EsxCloud::TaskError
      expect(step.start_time).to eq 100
      expect(step.end_time).to eq 150
    end
  end

  describe "::create_from_hash" do
    it "instantiates object" do
      step = EsxCloud::Step.create_from_hash(step_hash)
      expect(step.state).to eq "RUNNING"
      expect(step.sequence).to eq 0
      expect(step.operation).to eq "DeleteVm"
      expect(step.errors.size).to eq 1
      expect(step.errors[0][0]).to be_a EsxCloud::TaskError
      expect(step.warnings.size).to eq 1
      expect(step.warnings[0][0]).to be_a EsxCloud::TaskError
      expect(step.start_time).to eq 100
      expect(step.end_time).to eq 150
    end
  end

  describe "#duration" do
    context "when start_time or end_time is missing" do
      it "returns nil" do
        step = EsxCloud::Step.create_from_hash(step_hash.merge("startedTime" => nil))
        expect(step.duration).to be_nil
      end
    end

    context "when start_time and end_time is set" do
      it "returns correct value" do
        step = EsxCloud::Step.create_from_hash(step_hash)
        expect(step.duration).to eq 50.0
      end
    end
  end
end
