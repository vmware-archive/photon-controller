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

describe EsxCloud::HostsImporter do

  describe ".import" do

    let(:yml_file) do
      tmp_file = Tempfile.new("tempfile")
      tmp_file << content
      tmp_file.flush
      tmp_file.close
      tmp_file.path
    end

    after(:each) do
      File.delete(yml_file) if File.exist?(yml_file)
    end

    context "configuration is valid" do
      shared_examples "import configuration" do
        it "should return correct array of HostCreateSpec" do
          expect(EsxCloud::HostsImporter.import_file(file)).to eq specs
        end
      end

      context "when hosts are defined via range" do
        let(:content) do
<<CONTENT
---
hosts:
- address_ranges: 127.0.0.2 - 127.0.0.5, 127.0.0.6, 127.0.0.7
  username: u
  password: p
  usage_tags:
  - CLOUD
  metadata:
   m1: test
   m2: test
CONTENT
        end

        it_behaves_like "import configuration" do
          let(:file) { yml_file }
          let(:specs) do
            2.upto(7).map do |i|
              EsxCloud::HostCreateSpec.new("u", "p", ["CLOUD"], "127.0.0.#{i}", {"m1" => "test", "m2" => "test"})
            end
          end
        end
      end

      context "when hosts are defined one by one" do
        let(:content) do
<<CONTENT
---
hosts:
- address_ranges: 127.0.0.2
  username: u
  password: p
  usage_tags:
  - MGMT
  - CLOUD
- address_ranges: 127.0.0.3
  username: u
  password: p
  usage_tags:
  - MGMT
  - CLOUD
- address_ranges: 127.0.0.4
  username: u
  password: p
  usage_tags:
  - MGMT
  - CLOUD
CONTENT
        end

        it_behaves_like "import configuration" do
          let(:file) { yml_file }
          let(:specs) do
            2.upto(4).map do |i|
              EsxCloud::HostCreateSpec.new("u", "p", ["MGMT", "CLOUD"], "127.0.0.#{i}", nil)
            end
          end
        end
      end

      context "when MANAGEMENT_VM_IPS are defined via range" do
        let(:content) do
          <<CONTENT
---
hosts:
- address_ranges: 127.0.0.2 - 127.0.0.5, 127.0.0.6, 127.0.0.7
  username: u
  password: p
  usage_tags:
  - CLOUD
  metadata:
   m1: test
   MANAGEMENT_VM_IPS: 127.0.0.102 - 127.0.0.107
CONTENT
        end

        it_behaves_like "import configuration" do
          let(:file) { yml_file }
          let(:specs) do
            2.upto(7).map do |i|
              EsxCloud::HostCreateSpec.new("u", "p", ["CLOUD"], "127.0.0.#{i}",
                                           {"m1" => "test", "MANAGEMENT_NETWORK_IP" => "127.0.0.10#{i}"})
            end
          end
        end
      end
    end

    context "configuration is invalid" do
      shared_examples "fail importing incorrect configuration" do
        it "should throw UnexpectedFormat error" do
          begin
            EsxCloud::HostsImporter.import_file(file)
          rescue EsxCloud::UnexpectedFormat => e
            expect(e.message).to include error_msg
          end
        end
      end

      context "when file is empty" do
        let(:content) { "" }
        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "No host defined" }
        end
      end

      context "when hosts is not defined" do
        let(:content) do
<<CONTENT
---
host:
CONTENT
        end

        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "No host defined" }
        end
      end

      context "when management VM addresses are insufficient" do
        let(:content) do
          <<CONTENT
---
hosts:
- address_ranges: 127.0.0.2 - 127.0.0.5, 127.0.0.6, 127.0.0.7
  username: u
  password: p
  usage_tags:
  - CLOUD
  metadata:
   m1: test
   MANAGEMENT_VM_IPS: 127.0.0.102 - 127.0.0.103
CONTENT
        end

        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "Insufficient management VM addresses" }
        end
      end

      context "when address_ranges is not specified in host" do
        let(:content) do
          <<CONTENT
---
hosts:
- address_range: 127.0.0.2 - 127.0.0.5, 127.0.0.6, 127.0.0.7
CONTENT
        end

        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "address_ranges is not defined in host" }
        end
      end

    end
  end
end
