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

describe EsxCloud::DeploymentImporter do

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
        it "should return correct DeploymentCreateSpec" do
          expect(EsxCloud::DeploymentImporter.import_file(file)).to eq spec
        end
      end

      context "when every property of deployment is defined" do
        let(:content) do
<<CONTENT
---
deployment:
  image_datastores: image_datastore
  auth_enabled: true
  oauth_endpoint: 0.0.0.0
  oauth_port: '8080'
  oauth_tenant: 't'
  oauth_username: 'u'
  oauth_password: 'p'
  oauth_security_groups: ["sg1", "sg2"]
  syslog_endpoint: 0.0.0.1
  ntp_endpoint: 0.0.0.2
  stats_enabled: true
  stats_store_endpoint: 0.1.2.3
  stats_store_port: '2004'
  stats_store_type: 'GRAPHITE'
  use_image_datastore_for_vms: true
CONTENT
        end

        it_behaves_like "import configuration" do
          let(:file) { yml_file }
          let(:spec) do
            EsxCloud::DeploymentCreateSpec.new(["image_datastore"],
                                               EsxCloud::AuthConfigurationSpec.new(true, 't', 'p', ['sg1', 'sg2']),
                                               EsxCloud::NetworkConfigurationCreateSpec.new(false),
                                               EsxCloud::StatsInfo.new(true, '0.1.2.3', '2004', 'GRAPHITE'),
                                               "0.0.0.1",
                                               "0.0.0.2",
                                               true)
          end
        end
      end

      context "when optional properties of deployment are not defined" do
        let(:content) do
<<CONTENT
---
deployment:
  image_datastores: image_datastore
  auth_enabled: false
  stats_enabled : false
CONTENT
        end

        it_behaves_like "import configuration" do
          let(:file) { yml_file }
          let(:spec) do
            EsxCloud::DeploymentCreateSpec.new(
                ["image_datastore"],
                EsxCloud::AuthConfigurationSpec.new(false),
                EsxCloud::NetworkConfigurationCreateSpec.new(false),
                EsxCloud::StatsInfo.new(false))
          end
        end

        context "when 'image_datastores' is specified as an array" do
          let(:content) do
<<CONTENT
---
deployment:
  image_datastores:
  - image_ds1
  - image_ds2
  auth_enabled: false
  stats_enabled: false
CONTENT
          end

          it_behaves_like "import configuration" do
            let(:file) { yml_file }
            let(:spec) do
              EsxCloud::DeploymentCreateSpec.new(
                  ["image_ds1", "image_ds2"],
                  EsxCloud::AuthConfigurationSpec.new(false),
                  EsxCloud::NetworkConfigurationCreateSpec.new(false),
                  EsxCloud::StatsInfo.new(false))
            end
          end
        end

        context "when 'image_datastores' is specified as a comma delimited list" do
          let(:content) do
<<CONTENT
---
deployment:
  image_datastores: image_ds1,image_ds2
  auth_enabled: false
  stats_enabled: false
CONTENT
          end

          it_behaves_like "import configuration" do
            let(:file) { yml_file }
            let(:spec) do
              EsxCloud::DeploymentCreateSpec.new(
                  ["image_ds1", "image_ds2"],
                  EsxCloud::AuthConfigurationSpec.new(false),
                  EsxCloud::NetworkConfigurationCreateSpec.new(false),
                  EsxCloud::StatsInfo.new(false))
            end
          end
        end
      end
    end

    context "configuration is invalid" do
      shared_examples "fail importing incorrect configuration" do
        it "should throw UnexpectedFormat error" do
          begin
            EsxCloud::DeploymentImporter.import_file(file)
          rescue EsxCloud::UnexpectedFormat => e
            expect(e.message).to eq error_msg
          end
        end
      end

      context "when file is empty" do
        let(:content) { "" }
        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "No deployment defined" }
        end
      end

      context "when deployment is not defined" do
        let(:content) do
<<CONTENT
---
deployments:
CONTENT
        end

        it_behaves_like "fail importing incorrect configuration" do
          let(:file) { yml_file }
          let(:error_msg) { "No deployment defined" }
        end
      end
    end
  end
end
