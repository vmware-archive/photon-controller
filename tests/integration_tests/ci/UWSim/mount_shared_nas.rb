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

require 'yaml'
require 'net/ssh'

# Sample usage: ruby photon-controller/tests/integration_tests/ci/UWSim/mount_shared_nas.rb 10.146.58.19
abort('Please provide Shared NAS ip address') if ARGV.empty?

shared_nas_ip = ARGV.first

def mount(ip_address, username, password, shared_nas_ip)
  puts("Mounting Shared NAS on host #{ip_address}")

  Net::SSH.start(ip_address, username, password: password) do |session|
    cmd = 'esxcfg-nas -l'
    output = session.exec!(cmd)
    if output
      puts output

      # output is, for example, "SharedNas is /root/nfs/share1 from 10.146.58.19 mounted available "
      if output.include?(shared_nas_ip + ' mounted')
        cmd = 'esxcfg-nas -d SharedNas'
        puts cmd
        output = session.exec!(cmd)
        puts output
      end
    else
      puts "No mounted NAS file systems"
    end

    cmd = "esxcfg-nas -a -o #{shared_nas_ip} -s /root/nfs/share1 SharedNas"
    puts cmd
    output = session.exec!(cmd)
    puts output

    cmd = 'esxcfg-nas -l'
    puts cmd
    output = session.exec!(cmd)
    puts output
    success = false
    success = true if output.include?(shared_nas_ip + ' mounted')
    puts("Mounting Shared NAS on host #{ip_address} failed") unless success
    success
  end
end

config_file = File.join(File.dirname(__FILE__),
                        '../../../common/test_files/deployment/inventory-configuration-uwsim-chassis4to7.yml')
config = YAML.load_file(config_file)
hosts = config['hosts'].map { |h| h['address'] }
puts "Targeted hosts: #{hosts.inspect}"

for host in hosts
  cmd = "ssh-keygen -f \"#{ENV['HOME']}/.ssh/known_hosts\" -R #{host}"
  puts cmd
  `#{cmd}`
end

username = 'root'
password = 'ca$hc0w'
mount_success = hosts.inject(true) do |result, element|
  puts "\n---------------------------------------------"
  mount(element, username, password, shared_nas_ip) && result
end

abort('Mount Shared NAS failed') unless mount_success
