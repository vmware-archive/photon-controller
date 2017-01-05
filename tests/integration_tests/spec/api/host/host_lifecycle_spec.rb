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
require "test_helpers"

describe "provisioning scenarios", promote: true, life_cycle: true do

  before(:all) do
    @seeder = EsxCloud::SystemSeeder.instance

    # seed the image on all image datastores
    @seeder.image!
    wait_for_image_seeding_progress_is_done
  end

  describe "provisioning", order: :defined do
    describe "success" do
      context "when using a provisioned host" do
        context "with vms" do
          before(:all) do
            @deployment = client.find_all_api_deployments.items.first
            @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
            2.times do
              @seeder.create_vm @seeder.project!, affinities: [{id: @host.address, kind: "host"}]
            end
          end

          after(:all) do
            ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
            ignoring_all_errors { EsxCloud::Host.resume @host.id }
          end

          it "de-provisions and re-provisions" do
            #it "pauses a host" do
            expect(EsxCloud::Host.enter_suspended_mode(@host.id).state).to eq("SUSPENDED")
            #end

            #it "removes all vms on host" do
            vms = EsxCloud::Host.get_host_vms(@host.id)

            vms.items.each{ |vm| vm.delete }

            expect(EsxCloud::Host.get_host_vms(@host.id).items.size).to eq(0)
            #end

            #it "puts a host into maintenance mode" do
            expect(EsxCloud::Host.enter_maintenance_mode(@host.id).state).to eq("MAINTENANCE")
            #end

            #it "de-provisions a host" do
            expect(EsxCloud::Host.delete(@host.id)).to eq(true)
            begin
              EsxCloud::Host.find_host_by_id(@host.id)
              fail("should not be able to revtieve deleted host")
            rescue
            end

            expect(host_is_reachable @host, @seeder).to be(false)
            #end

            #it "provisions a host" do
            @host = EsxCloud::Host.create(@deployment.id, @host.to_spec())
            expect(@host.state).to eq("READY")
            expect(host_is_reachable @host, @seeder).to be(true)
            #end
          end
        end

        context "without vms" do
          before(:all) do
            @deployment = client.find_all_api_deployments.items.first
            @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
          end

          it "host has valid fields" do
            expect(@host.state).to eq("READY")
            expect(@host.esx_version).not_to be_nil
            expect(@host.esx_version).not_to be_empty
            expect(host_is_reachable @host, @seeder).to be(true)
          end

          it "de-provisions and re-provisions" do
            #it "pauses a host" do
            expect(EsxCloud::Host.enter_suspended_mode(@host.id).state).to eq("SUSPENDED")
            #end

            #it "puts a host into maintenance mode" do
            expect(EsxCloud::Host.enter_maintenance_mode(@host.id).state).to eq("MAINTENANCE")
            #end

            #it "de-provisions a host" do
            expect(EsxCloud::Host.delete(@host.id)).to eq(true)
            begin
              EsxCloud::Host.find_host_by_id(@host.id)
              fail("should not be able to revtieve deleted host")
            rescue
            end

            expect(host_is_reachable @host, @seeder).to be(false)
            #end

            #it "provisions a host" do
            @host = EsxCloud::Host.create(@deployment.id, @host.to_spec())
            expect(@host.state).to eq("READY")
            expect(host_is_reachable @host, @seeder).to be(true)
            #end
          end
        end
      end
    end
  end

  describe "maintenance mode transitions", order: :defined do
    context "when using a host with vms" do
      before(:all) do
        @deployment = client.find_all_api_deployments.items.first
        @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
        2.times do
          @seeder.create_vm @seeder.project!, affinities: [{id: @host.address, kind: "host"}]
        end
      end

      after(:all) do
        vms = EsxCloud::Host.get_host_vms(@host.id)
        vms.items.each{ |vm| vm.delete }
        ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
        ignoring_all_errors { EsxCloud::Host.resume @host.id }
      end

      before(:each) do
        ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
        ignoring_all_errors { EsxCloud::Host.resume @host.id }
      end

      describe "resume" do
        it "is a no-op when resuming a READY host" do
          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "should transition back to READY from SUSPEND" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("READY")
        end
      end

      describe "suspend" do
        it "is transitioning to suspended mode" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end

        it "is a no-op when suspending a SUSPENDED host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end
      end

      describe "enter maintenance" do
        it "fails to transition to MAINTENANCE from READY" do
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "fails to transition to MAINTENANCE from SUSPENDED" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end
      end

      describe "exit maintenance" do
        it "is a no-op when exiting from READY host" do
          ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "is a no-op when exiting from SUSPENDED host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end
      end
    end

    context "when using a host without vms" do
      before(:all) do
        @deployment = client.find_all_api_deployments.items.first
        @host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
      end

      before(:each) do
        ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
        ignoring_all_errors { EsxCloud::Host.resume @host.id }
      end

      after(:all) do
        ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
        ignoring_all_errors { EsxCloud::Host.resume @host.id }
      end

      describe "resume" do
        it "is a no-op when resuming a READY host" do
          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "should transition back to READY from SUSPEND" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "is a no-op when resuming a MAINTENANCE host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")

          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("MAINTENANCE")
        end
      end

      describe "suspend" do
        it "is transitioning to suspended mode" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end

        it "is a no-op when suspending a SUSPENDED host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end

        it "is a no-op when suspending a MAINTENANCE host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")

          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")
        end
      end

      describe "enter maintenance" do
        it "fails to transition to MAINTENANCE from READY" do
          expect(get_state @host).to eq("READY")
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "transitions to MAINTENANCE from SUSPENDED" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")
        end

        it "is a no-op when entering into maintenance from MAINTENANCE" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")

          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")
        end
      end

      describe "exit maintenance" do
        it "is a no-op when exiting from READY host" do
          ignoring_all_errors { EsxCloud::Host.resume @host.id }
          expect(get_state @host).to eq("READY")

          ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
          expect(get_state @host).to eq("READY")
        end

        it "is a no-op when exiting from SUSPENDED host" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")

          ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
          expect(get_state @host).to eq("SUSPENDED")
        end

        it "exits maintenance mode" do
          ignoring_all_errors { EsxCloud::Host.enter_suspended_mode @host.id }
          ignoring_all_errors { EsxCloud::Host.enter_maintenance_mode @host.id }
          expect(get_state @host).to eq("MAINTENANCE")

          ignoring_all_errors { EsxCloud::Host.exit_maintenance_mode @host.id }
          expect(get_state @host).to eq("READY")
        end
      end

      describe "add mgmt host", disable_for_uptime_tests: true do
        it "add cloud host as management host" do
          mgmt_host_ip = EsxCloud::TestHelpers.get_mgmt_vm_ip_for_add_mgmt_host
          unless mgmt_host_ip.nil? || mgmt_host_ip.empty?
            mgmt_host = client.get_deployment_hosts(@deployment.id).items.select { |host| host.usage_tags.include? "MGMT"}.first

            mgmt_host_metadata = mgmt_host.metadata.clone
            mgmt_host_metadata["MANAGEMENT_NETWORK_IP"] = mgmt_host_ip

            expect(EsxCloud::Host.enter_suspended_mode(@host.id).state).to eq("SUSPENDED")
            expect(EsxCloud::Host.enter_maintenance_mode(@host.id).state).to eq("MAINTENANCE")
            expect(EsxCloud::Host.delete(@host.id)).to eq(true)

            add_mgmt_host_spec = EsxCloud::HostCreateSpec.new(
                @host.username,
                @host.password,
                ["MGMT"],
                @host.address,
                mgmt_host_metadata)

            host = EsxCloud::Host.create @deployment.id, add_mgmt_host_spec
            expect(host.state).to eq("READY")
            puts "Added mgmt host"

            task_list = api_client.find_tasks(host.id, "host", "COMPLETED")
            tasks = task_list.items
            expect(tasks.size).to eq(1)
            task = tasks.first
            expect(task.errors).to be_empty
            expect(task.warnings).to be_empty
          end
        end
      end
    end
 end
end

def get_state(host)
  EsxCloud::Host.find_host_by_id(host.id).state
end

def host_is_reachable(host, seeder)
  # we testing the reachability by trying to create a vm on the specific host
  begin
    vm = seeder.create_vm seeder.project!, affinities: [{id: host.address, kind: "host"}]
    vm.delete
  rescue
    return false
  end
  return true
end
