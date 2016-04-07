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
require "ipaddr"

describe "host", management: true, devbox: true do
  before(:all) do
    @seeder = EsxCloud::SystemSeeder.new(create_small_limits, [5.0])
    @deployment = @seeder.deployment!
  end

  let(:host_ip) { EsxCloud::TestHelpers.get_esx_ip }

  let (:spec) do
    EsxCloud::HostCreateSpec.new(
        EsxCloud::TestHelpers.get_esx_username,
        EsxCloud::TestHelpers.get_esx_password,
        ["CLOUD"],
        host_ip,
        {})
  end

  describe "#create" do
    context "when spec is invalid" do
      context "when usageTags is invalid" do
        let(:host_create_spec) do
          spec.usage_tags = ["INVALID"]
          spec
        end

        it "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one with invalid usageTags")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 400
            expect(e.errors.first.message).to include("The supplied JSON could not be parsed")
            expect(e.errors.first.code).to eq "InvalidJson"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidJson")
          end
        end
      end

      context "when ip address is invalid" do
        let(:host_create_spec) do
          spec.address = "1.2.qb"
          spec
        end

        it "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one with invalid ip")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 400
            expect(e.errors.first.message).to include("is invalid IP or Domain Address")
            expect(e.errors.first.code).to eq "InvalidEntity"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidEntity")
          end
        end
      end

      context "when management vm address is already in use" do
        let(:mgmt_host_create_spec) do
          spec.usage_tags = ["MGMT"]
          spec.metadata = {"MANAGEMENT_DATASTORE"=>"datastore", "MANAGEMENT_NETWORK_DNS_SERVER"=>"1.1.1.1",
                           "MANAGEMENT_NETWORK_GATEWAY"=>"2.2.2.2", "MANAGEMENT_NETWORK_IP"=>host_ip,
                           "MANAGEMENT_NETWORK_NETMASK"=>"4.4.4.4", "MANAGEMENT_PORTGROUP"=>"['p1', 'p2']"}
          spec
        end

        xit "fails to create host" do
          error_msg = "IP Address #{host_ip} is in use"
          begin
            client.create_host(@deployment.id, mgmt_host_create_spec.to_hash)
            fail("There should be an error when creating one whose management vm ip address is already in use")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 200
            expect(e.errors.size).to eq 1
            expect(e.errors.first.size).to eq 1
            expect(e.errors.first.first.code).to eq "IpAddressInUse"
            expect(e.errors.first.first.message).to eq error_msg
          rescue EsxCloud::CliError => e
            expect(e.output).to include("IpAddressInUse")
          end
        end
      end

      context "when password is missing" do
        let(:host_create_spec) do
          spec.password = nil
          spec
        end

        it "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one without password")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 400
            expect(e.errors.first.message).to eq "password may not be null (was null)"
            expect(e.errors.first.code).to eq "InvalidEntity"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidEntity")
          end
        end
      end

      context "when password is wrong" do
        let(:host_create_spec) do
          spec.password = "wrong_pw"
          spec
        end

        xit "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one with wrong password")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 200
            expect(e.errors.size).to eq 1
            expect(e.errors.first.size).to eq 1
            expect(e.errors.first.first.code).to eq "InvalidLoginCredentials"
            expect(e.errors.first.first.message).to eq "Invalid Username or Password"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidLoginCredentials")
          end
        end
      end

      context "when username is missing" do
        let(:host_create_spec) do
          spec.username = nil
          spec
        end

        it "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one without username")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 400
            expect(e.errors.first.message).to eq "username may not be null (was null)"
            expect(e.errors.first.code).to eq "InvalidEntity"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidEntity")
          end
        end
      end

      context "when username is wrong" do
        let(:host_create_spec) do
          spec.username = "wrong_username"
          spec
        end

        xit "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one with wrong username")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 200
            expect(e.errors.size).to eq 1
            expect(e.errors.first.size).to eq 1
            expect(e.errors.first.first.code).to eq "InvalidLoginCredentials"
            expect(e.errors.first.first.message).to eq "Invalid Username or Password"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidLoginCredentials")
          end
        end
      end

      context "when usageTags is missing", disable_for_cli_test: true do
        let(:host_create_spec) do
          spec.usage_tags = nil
          spec
        end

        it "fails to create host" do
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one without usage tag")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 400
            expect(e.errors.first.message).to eq "usageTags may not be null (was null)"
            expect(e.errors.first.code).to eq "InvalidEntity"
          rescue EsxCloud::CliError => e
            expect(e.output).to include("InvalidEntity")
          end
        end
      end

      context "when availability zone does not exist", disable_for_cli_test: true do
        let(:host_create_spec) do
          spec.availability_zone = "availability-zone-not-exist"
          spec
        end

        it "fails to create host" do
          error_msg = "AvailabilityZone availability-zone-not-exist not found"
          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail("There should be an error when creating one with non-existing availability zone")
          rescue EsxCloud::ApiError => e
            expect(e.response_code).to eq 404
            expect(e.errors.size).to eq 1
            expect(e.errors.first.code).to eq "AvailabilityZoneNotFound"
            expect(e.errors.first.message).to eq error_msg
          rescue EsxCloud::CliError => e
            expect(e.output).to include(error_msg)
          end
        end
      end

      context "when availability zone is in PENDING_DELETE state", disable_for_cli_test: true do
        let(:zone) { EsxCloud::SystemSeeder.instance.pending_delete_availability_zone! }
        let(:host_create_spec) do
          spec.availability_zone = zone.id
          spec
        end

        it "should fail" do
          expect(zone.state).to eq "PENDING_DELETE"

          begin
            client.create_host(@deployment.id, host_create_spec.to_hash)
            fail "create host with availability zone in PENDING_DELETE state should fail"
          rescue EsxCloud::ApiError => e
            e.response_code.should == 400
            e.errors.size.should == 1
            e.errors[0].code.should == "InvalidAvailabilityZoneState"
          rescue EsxCloud::CliError => e
            e.message.should match("InvalidAvailabilityZoneState")
          end
        end
      end
    end
  end

  describe "#delete" do
    context "when host does not exist" do
      it "fails to delete the host" do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.mgmt_delete_host(invalid_host_id)
          fail("delete host should fail when the host is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#find_host_by_id" do
    context "when host does not exist" do
      it "fails to find host" do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.mgmt_find_host_by_id(invalid_host_id)
          fail("There should be an error when host does not exist")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq "HostNotFound"
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include("HostNotFound")
        end
      end
    end
  end

  describe "#find_tasks_by_host_id" do
    context "when host does not exist" do
      it "errors out on getting task list"  do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.find_tasks_by_host_id(invalid_host_id)
          fail("get tasks for host should fail when the host does not exist")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#host_enter_maintenance_mode" do
    context "when host does not exist" do
      it "fails to enter maintenance mode" do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.host_enter_maintenance_mode(invalid_host_id)
          fail("enter maintenance should fail when the host is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#host_exit_maintenance_mode" do
    context "when host does not exist" do
      it "fails to exit maintenance mode" do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.host_exit_maintenance_mode(invalid_host_id)
          fail("delete host should fail when the host is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#host_suspend" do
    context "when host does not exist" do
      it "fails to enter suspended mode" do
      invalid_host_id = "invalid-host"
      error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.host_enter_suspended_mode(invalid_host_id)
          fail("enter suspended should fail when the host is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end

  describe "#host_resume" do
    context "when host does not exist" do
      it "fails to enter suspended mode" do
        invalid_host_id = "invalid-host"
        error_msg = "Host ##{invalid_host_id} not found"
        begin
          client.host_resume(invalid_host_id)
          fail("enter suspended should fail when the host is not created")
        rescue EsxCloud::ApiError => e
          expect(e.response_code).to eq 404
          expect(e.errors.size).to eq 1
          expect(e.errors.first.code).to eq("HostNotFound")
          expect(e.errors.first.message).to include(error_msg)
        rescue EsxCloud::CliError => e
          expect(e.output).to include(error_msg)
        end
      end
    end
  end
end
