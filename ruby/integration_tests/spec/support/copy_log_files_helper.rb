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

require "net/sftp"
require "net/ping"

# @param [String] host_ip
def remove_host_from_known_hosts_file(host_ip)
  known_hosts_file_path = File.join(Dir.home, ".ssh", "known_hosts")
  temp_known_hosts_file_name = "temp_known_hosts"

  File.open(temp_known_hosts_file_name, 'w') do |output_file|
    File.open(known_hosts_file_path, 'r').each do |line|
      output_file.print line unless line.start_with? host_ip
    end
  end
  FileUtils.move(temp_known_hosts_file_name, known_hosts_file_path)
end

# @param [String] server
# @param [String] user_name
# @param [String] password
# @param [String] source_folder
# @param [String] source_file_name
# @param [String] dest_folder
# @param [String] dest_file_name
# @return [Boolean] true if download is successful an false if source file does not exist
def download_file(server, user_name, password, source_folder, source_file_name, dest_folder, dest_file_name = nil)
  source_file = File.join(source_folder, source_file_name)

  Net::SSH.start(server, user_name, password: password) do |ssh|
    output = ssh.exec!("[ -e #{source_file} ] && echo Y || echo N").strip
    return false if output == "N"
  end

  Net::SFTP.start(server, user_name, password: password) do |sftp|
    dest_file_name = source_file_name unless dest_file_name
    sftp.download!(source_file, File.join(dest_folder, dest_file_name))
    true
  end
end

# @param [String] server
# @param [String] user_name
# @param [String] password
# @param [String] source_folder
# @param [String] dest_folder
def download_folder(server, user_name, password, source_folder, dest_folder)
  Net::SFTP.start(server, user_name, password: password) do |sftp|
    sftp.dir.foreach(source_folder) do |file|
      if file.name.end_with? ".log"
        sftp.download!(File.join(source_folder, file.name), File.join(dest_folder, file.name))
      end
    end
  end
end

# @Param [String] host_ip
def server_up?(host_ip)
  check = Net::Ping::External.new(host_ip)
  check.ping
end
