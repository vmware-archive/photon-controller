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

describe EsxCloud::IPRange do

  describe ".parse" do

    shared_examples "parse string" do
      it "should return correct array" do
        range = EsxCloud::IPRange.parse(range_string)
        expect(range).to eq expected_range
      end
    end

    context "when range is nil" do
      it_behaves_like "parse string" do
        let(:range_string) { nil }
        let(:expected_range) { [] }
      end
    end

    context "when range is empty" do
      it_behaves_like "parse string" do
        let(:range_string) { "" }
        let(:expected_range) { [] }
      end
    end

    context "when range is single ip address" do
      it_behaves_like "parse string" do
        let(:range_string) { "127.0.0.1" }
        let(:expected_range) { ["127.0.0.1"] }
      end
    end

    context "when range is multiple ip addresses" do
      it_behaves_like "parse string" do
        let(:range_string) { "127.0.0.1 - 127.0.1.10" }
        let(:expected_range) do
          1.upto(255).map { |i| "127.0.0.#{i}" } +
          0.upto(10).map { |i| "127.0.1.#{i}" }
        end
      end
    end

    context "when ip address is invalid" do

      context "when range is single ip address" do
        it "should throw ValidationError" do
          begin
            EsxCloud::IPRange.parse("127.0.1.s")
            fail("parse should fail when ip address is invalid")
          rescue NetAddr::ValidationError => e
            expect(e.message).to eq "127.0.1.s is invalid (contains invalid characters)."
          end
        end
      end

      context "when range is multiple ip addresses" do
        it "should throw ValidationError" do
          begin
            EsxCloud::IPRange.parse("127.0.0.1 - 127.0.1.300")
            fail("parse should fail when ip address is invalid")
          rescue NetAddr::ValidationError => e
            expect(e.message).to eq "127.0.1.300 is invalid (IPv4 octets should be between 0 and 255)."
          end
        end
      end

    end
  end
end
