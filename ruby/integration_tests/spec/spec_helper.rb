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

require "thread"
require "rspec"
require "yaml"
require "socket"

gemfile = File.expand_path("../../Gemfile", __FILE__)

if File.exists?(gemfile)
  ENV["BUNDLE_GEMFILE"] = gemfile
  require "rubygems"
  require "bundler/setup"
end

require_relative "../lib/integration"
require_relative "../lib/test_helpers"
require_relative "../../common/lib/common/errors"
require_relative "support/api_client_helper"
require_relative "support/api_routes_helper"
require_relative "support/system_cleaner"
require_relative "support/system_seeder"
require_relative "support/cluster_helper"
require_relative "support/log_helper"
require_relative "support/housekeeper_helper"

EsxCloud::Config.init
EsxCloud::Config.client = ApiClientHelper.management

# JUnit formatter uses test filename as a classname for JUnit report line item.
# We open up and monkey-patch the formatter instead of extending it it b/c RSpec
# requires formatters to be in LOAD_PATH before spec helper is evaluated, and this
# approach seems less involved than creating a new gem for this formatter.
if defined?(RSpec::Core::Formatters::JUnitFormatter)
  class RSpec::Core::Formatters::JUnitFormatter
    def driver_aware_example_classname(example)
      ENV["DRIVER"] + "." + old_example_classname(example)
    end

    alias_method :old_example_classname, :example_classname
    alias_method :example_classname, :driver_aware_example_classname
  end
end

def get_system_status(instances = 1)
  raise "Number of instances cannot be nil" if instances.nil?

  puts "Verifying system status..."
  120.times do
    begin
      system_status = EsxCloud::Config.client.get_status
      expect(system_status.status).to eq "READY"
      expect(system_status.components.size).to eq 4

      system_status.components.each do |component|
        expect(component.name).to_not be_nil
        expect(component.status).to eq "READY"
        expect(component.instances.size).to eq instances.to_i
      end
      return
    rescue
      sleep 5
    end
  end

  raise "System is not ready after 10 minutes"
end

def ignoring_all_errors(&block)
  block.call
rescue ApiError => e
  STDERR.puts "  ignoring API error: #{e.message}"
rescue Exception => e
  STDERR.puts "  ignoring error: #{e}"
end

RSpec.configure do |config|
  config.include EsxCloud::TestHelpers
  config.color = true
  config.formatter = :documentation
  config.around(:each) do |example|
    metadata = example.metadata[:description_args]
    metadata.each_with_index do |arg, i|
      metadata[i] = "[#{ApiClientHelper.driver}] #{arg}"
    end
    example.run
  end

  install_logging_hooks(config)

  # auth filtering
  config.filter_run_excluding auth_enabled: true unless ENV["ENABLE_AUTH"]
  config.filter_run_excluding auth_disabled: true if ENV["ENABLE_AUTH"]
  config.filter_run_excluding auth_admin: true unless ENV["PHOTON_USERNAME_ADMIN"] && ENV["PHOTON_PASSWORD_ADMIN"]
  config.filter_run_excluding auth_admin2: true unless ENV["PHOTON_USERNAME_ADMIN2"] && ENV["PHOTON_PASSWORD_ADMIN2"]
  config.filter_run_excluding auth_tenant_admin: true unless ENV["PHOTON_USERNAME_TENANT_ADMIN"] && ENV["PHOTON_PASSWORD_TENANT_ADMIN"]
  config.filter_run_excluding auth_project_user: true unless ENV["PHOTON_USERNAME_PROJECT_USER"] && ENV["PHOTON_PASSWORD_PROJECT_USER"]
  config.filter_run_excluding auth_non_admin: true unless ENV["PHOTON_USERNAME_NON_ADMIN"] && ENV["PHOTON_PASSWORD_NON_ADMIN"]

  config.filter_run_excluding disable_for_cli_test: true if ENV["DRIVER"] == "gocli"

  config.filter_run_excluding image: true unless ENV["ESXCLOUD_DISK_IMAGE"] && ENV["ESXCLOUD_BAD_DISK_IMAGE"] &&
    ENV["ESXCLOUD_DISK_OVA_IMAGE"] && ENV["ESXCLOUD_DISK_BOOTABLE_OVA_IMAGE"]

  config.filter_run_excluding iso_file: true unless ENV["ESXCLOUD_ISO_FILE"]

  config.filter_run_excluding datastore_id: true unless ENV["ESX_DATASTORE_ID"]

  config.filter_run_excluding real_agent: true unless ENV["REAL_AGENT"]

  config.filter_run_excluding deployer: true unless ENV["DEPLOYER"]

  config.filter_run_excluding devbox: true unless ENV["DEVBOX"]
  config.filter_run_excluding promote: true if ENV["DEVBOX"]

  config.filter_run_excluding dcp: true unless ENV["DCP"]

  config.filter_run_excluding upgrade: true unless ENV["UPGRADE"]

  config.filter_run_excluding disable_for_uptime_tests: true if ENV["UPTIME"]

  config.filter_run_excluding single_vm_port_group: true if EsxCloud::TestHelpers.get_vm_port_groups.length == 1

  config.filter_run_excluding go_cli: true unless ENV["DRIVER"] == "gocli"

  config.before(:suite) do
    unless ENV["DEPLOYER_TEST"]
      EsxCloud::SystemSeeder.instance.network!
    end

    if ENV["UPTIME"]
      get_system_status(ENV["MANAGEMENT_VM_COUNT"])
      HousekeeperHelper.clean_unreachable_datastores
    end
  end

  config.after(:suite) do
    cleaner = EsxCloud::SystemCleaner.new(ApiClientHelper.management)
    ignoring_all_errors { cleaner.clean_images(EsxCloud::SystemSeeder.instance) }
    ignoring_all_errors {
      cleaner.delete_network(EsxCloud::SystemSeeder.instance.network) if EsxCloud::SystemSeeder.instance.network
    }
    ignoring_all_errors {
      cleaner.delete_tenant(EsxCloud::SystemSeeder.instance.tenant) if EsxCloud::SystemSeeder.instance.tenant
    }
  end
end
