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

require "net/ssh"
require_relative "../lib/test_helpers"

module EsxCloud
  class HostCleaner

    class << self

      def clean_vms_on_real_host(server, user_name, password)
        puts "cleaning vms on host #{server}"
        Net::SSH.start(server, user_name, password: password) do |ssh|
          dirty_vms = ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1, $2}'`;do echo $id;done")
          ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1}'`;do vim-cmd vmsvc/power.off $id || vim-cmd vmsvc/unregister $id ;done")

          datastore_dir = "/vmfs/volumes/#{EsxCloud::TestHelpers.get_datastore_name}/"
          rm_cmd = "rm -rf #{datastore_dir}"
          puts "cleaning folders under #{datastore_dir}"
          ["vms/*", "disks/*", "images/*", "tmp_uploads/*"].each do |folder_prefix|
            ssh.exec!(rm_cmd + folder_prefix)
          end

          dirty_vms
        end
      end

      def remove_vms(server, user_name, password)
        puts "cleaning vms on host #{server}"
        Net::SSH.start(server, user_name, password: password) do |ssh|
          dirty_vms = ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1, $2}'`;do echo $id;done")
          ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1}'`;do vim-cmd vmsvc/power.off $id && vim-cmd vmsvc/unregister $id ;done")
          dirty_vms
        end
      end

      def reboot_host(server, user_name, password)
        puts "rebooting host #{server}"
        Net::SSH.start(server, user_name, password: password) do |ssh|
          ssh.exec!("reboot")
        end
      end

      def uninstall_vib(server, user_name, password, vib_name)
        puts "deleting vib #{vib_name} from #{server}"
        Net::SSH.start(server, user_name, password: password) do |ssh|
          ssh.exec!("esxcli software vib remove -f -n #{vib_name} | echo #{vib_name}")
        end
      end

      def clean_datastores(server, user_name, password, folders = ["disks", "deleted_images", "images", "tmp_images", "vms"])
        puts "cleaning datastores on #{server}"
        Net::SSH.start(server, user_name, password: password) do |ssh|
          folders.each do |folder|
            ssh.exec!("for ds in `df | awk '{print $6}' | grep -v Mounted`; do rm -rf $ds/#{folder}; done")
          end
          ssh.exec!("rm -rf /opt/vmware/photon/controller/")
          ssh.exec!("rm -rf /opt/vmware/esxcloud")
        end
      end

      def api_clean(host_ip)
        host = EsxCloud::Host.find_all.items.detect { |h| h.address == host_ip }
        fail "Host with [#{host_ip}] not found." if host.nil?
        enter_suspended_mode host
        EsxCloud::Host.get_host_vms(host.id).items.each { |v| delete_vm v.id }
        enter_maintenance_mode host
        EsxCloud::Host.delete host.id
      end

      def delete_vm(vm_id)
        vm = EsxCloud::Vm.find_vm_by_id vm_id
        stop_vm vm
        vm.disks.each { |d| detach_disk vm, d }
        detach_iso vm
        vm.delete
      end

      def detach_disk(vm, disk)
        if ["persistent-disk", "persistent"].include? disk.kind
          vm.detach_disk disk.id
        end
      end

      def detach_iso(vm)
        begin
          vm.detach_iso
        rescue
        end
      end

      def stop_vm(vm)
        begin
          vm.stop!
        rescue
        end
      end

      def enter_suspended_mode(host)
        begin
          EsxCloud::Host.enter_suspended_mode host.id
        rescue
        end
      end

      def enter_maintenance_mode(host)
        begin
          EsxCloud::Host.enter_maintenance_mode host.id
        rescue
        end
      end

    end

  end
end
