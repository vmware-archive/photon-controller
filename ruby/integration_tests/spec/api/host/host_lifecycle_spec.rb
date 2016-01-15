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
  end

  describe "provisioning", order: :defined do
    describe "success" do
      context "when using a provisioned host" do
        context "with vms" do
          before(:all) do
            @host = client.mgmt_find_all_hosts.items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
            @deployment = client.find_all_api_deployments.items.first
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
            host = EsxCloud::Host.create(@deployment.id, to_spec(@host))

            expect(host.state).to eq("READY")
            expect(host_is_reachable host, @seeder).to be(true)
            #end
          end
        end

        context "without vms" do
          before(:all) do
            @host = client.mgmt_find_all_hosts.items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
            @deployment = client.find_all_api_deployments.items.first
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
            host = EsxCloud::Host.create(@deployment.id, to_spec(@host))

            expect(host.state).to eq("READY")
            expect(host_is_reachable host, @seeder).to be(true)
            #end
          end
        end
      end
    end
  end

  describe "maintenance mode transitions", order: :defined do
    context "when using a host with vms" do
      before(:all) do
        @host = client.mgmt_find_all_hosts.items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
        2.times do
          @seeder.create_vm @seeder.project!, affinities: [{id: @host.address, kind: "host"}]
        end
      end

      after(:all) do
        vms = EsxCloud::Host.get_host_vms(@host.id)
        vms.items.each{ |vm| vm.delete }
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
        @host = client.mgmt_find_all_hosts.items.select { |host| host.usage_tags == ["CLOUD"] and host.state == "READY" }.first
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

def to_spec(host)
  EsxCloud::HostCreateSpec.new(
      host.username,
      host.password,
      host.usage_tags,
      host.address,
      host.metadata,
      host.availability_zone)
end
