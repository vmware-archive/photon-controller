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
require "net/http"
require_relative "../lib/test_helpers"

module EsxCloud
  class HostCleaner

    class << self

      def clean_vms_on_real_host(server, user_name, password)
        dirty_vms = remove_vms server, user_name, password

        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          clean_datastore ssh, EsxCloud::TestHelpers.get_datastore_name
          dirty_vms
        end
      end

      def remove_vms(server, user_name, password)
        puts "cleaning vms on host #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          dirty_vms = ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1, $2}'`;do echo $id;done")
          ssh.exec!("for id in `vim-cmd vmsvc/getallvms | tail -n+2 | awk '{print $1}'`;do (vim-cmd vmsvc/power.off $id || true) && vim-cmd vmsvc/unregister $id ;done")
          ssh.exec!("tmp=`mktemp` && vim-cmd vmsvc/getallvms 2>$tmp && for id in `awk '{print $4}' $tmp | sed \"s/'//g\"`;do (vim-cmd vmsvc/power.off $id || true) && vim-cmd vmsvc/unregister $id ;done")
          dirty_vms
        end
      end

      def reboot_host(server, user_name, password)
        puts "rebooting host #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          ssh.exec!("reboot -f")
        end
      end

      def wait_for_boot(server, user_name, password, max_wait_time_seconds)
        wait_start = Time.now
        puts "waiting for host #{server} to be reachable within #{max_wait_time_seconds} seconds"
        wait_for_ssh(server, user_name, password, max_wait_time_seconds, wait_start)
        wait_for_http(server, user_name, password, max_wait_time_seconds, wait_start)
        puts "host #{server} became available after #{Time.now - wait_start} seconds"
      end

      def wait_for_ssh(server, user_name, password, max_wait_time_seconds, wait_start)
        while Time.now - wait_start < max_wait_time_seconds
          begin
            # test if we can ssh into the machine
            Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null", timeout: 5}) do |ssh|
            end
            puts "ssh on host #{server} is up"
            return
          rescue
          end
        end
        fail "ssh on host #{server} did not become available after #{max_wait_time_seconds} seconds"
      end

      def wait_for_http(server, user_name, password, max_wait_time_seconds, wait_start)
        # using the data store list uri
        uri = URI("https://#{server}/folder?dcPath=ha-datacenter")
        while Time.now - wait_start < max_wait_time_seconds
          begin
            Net::HTTP.start(uri.host, uri.port, :use_ssl => true, :verify_mode => OpenSSL::SSL::VERIFY_NONE) do |http|
              request = Net::HTTP::Get.new uri.request_uri
              request.basic_auth user_name, password
              http.request request
            end
            puts "http on host #{server} is up"
            return
          rescue
          end
        end
        fail "http on host #{server} did not become available after #{max_wait_time_seconds} seconds"
      end

      def stop_agent(server, user_name, password)
        puts "stopping agent on host #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          ssh.exec!("/etc/init.d/photon-controller-agent stop")
        end
      end

      def start_agent(server, user_name, password)
        puts "starting agent on host #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          ssh.exec!("/etc/init.d/photon-controller-agent start")
        end
      end

      def uninstall_vib(server, user_name, password, vib_name)
        puts "deleting vib #{vib_name} from #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          ssh.exec!("esxcli software vib remove -f -n #{vib_name} | echo #{vib_name}")
        end
      end

      DATASTORE_DIRS_TO_DELETE = ["deleted_image", "disk", "image", "tmp_image", "vm", "tmp_upload"]
      def clean_datastore(ssh, datastore)
        puts "cleaning datastore #{datastore}"

        datastore_dir = "/vmfs/volumes/#{datastore}/"
        DATASTORE_DIRS_TO_DELETE.each do |folder|
          if datastore.start_with?('vsan')
            rm_cmd = "for dir in `/usr/lib/vmware/osfs/bin/osfs-ls #{datastore_dir} | grep -i #{folder}`; do"\
                     " rm -rf #{datastore_dir}$dir/*"\
                     " && (rm -rf #{datastore_dir}$dir/.* || true)"\
                     " && /usr/lib/vmware/osfs/bin/osfs-rmdir #{datastore_dir}$dir;"\
                     " done"
          else
            rm_cmd = "rm -rf #{datastore_dir}#{folder}* && rm -rf *installer*"
          end
          ssh.exec!(rm_cmd)
        end
      end

      def clean_datastores(server, user_name, password)
        puts "cleaning datastores on #{server}"
        Net::SSH.start(server, user_name, {password: password, user_known_hosts_file: "/dev/null"}) do |ssh|
          output = ssh.exec!("for ds in `df | awk '{print $6}' | grep -v Mounted`; do echo $(basename $ds); done")
          datastores = output.split("\n")
          datastores.each do |datastore|
            clean_datastore ssh, datastore
          end
          ssh.exec!("rm -rf /opt/vmware/photon/controller/")
          ssh.exec!("rm -rf /opt/vmware/esxcloud")
        end
      end

      def api_clean(host_ip)
        deployment = EsxCloud::Deployment.find_all.items.first
        host = EsxCloud::Deployment.get_deployment_hosts(deployment.id).items.detect { |h| h.address == host_ip }
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
