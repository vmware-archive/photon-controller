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
module EsxCloud
  class HostsImporter

    class << self

      # @param [String] file
      # @return [Array<HostCreateSpec>]
      def import_file(file)
        puts "VERBOSE: Importing file #{file}" if $VERBOSE
        fail EsxCloud::NotFound, "file #{file} does not exist." unless File.exist?(file)

        import_config(YAML.load_file(file))
      end

      # @param [Hash] config
      # @return [Array<HostCreateSpec>]
      def import_config(config)
        puts "VERBOSE: Importing config #{config}" if $VERBOSE
        fail UnexpectedFormat, "No host defined." unless config.is_a?(Hash)

        hosts = config['hosts']
        fail UnexpectedFormat, "No host defined." if hosts.nil? || hosts.empty?

        hosts.inject([]) do |acc, host|
          acc << host_specs(host)
        end.flatten 1
      end

      private

      def host_specs(host)
        puts "VERBOSE: Prepare host spec for #{host}" if $VERBOSE
        unless host["metadata"].nil? || host["metadata"].empty?
          management_network_ips = IPRange.parse(host["metadata"]["MANAGEMENT_VM_IPS"])
        end

        fail UnexpectedFormat, "address_ranges is not defined in host #{host}" unless host["address_ranges"]
        host_ip_range = IPRange.parse(host["address_ranges"])
        fail UnexpectedFormat, "Insufficient management VM addresses. [#{host}]" unless
            management_network_ips.nil? || management_network_ips.empty? ||
            host_ip_range.size <= management_network_ips.size

        host_ip_range.map do |ip_address|
          unless host["metadata"].nil?
            metadata = host["metadata"].clone
            metadata.delete "MANAGEMENT_VM_IPS"
            metadata["MANAGEMENT_NETWORK_IP"] = management_network_ips.shift unless management_network_ips.empty?
          end

          host_create_spec host, ip_address, metadata
        end
      end

      def host_create_spec(host, ip_address, metadata)
        puts "VERBOSE: Create host spec for #{host}" if $VERBOSE
        EsxCloud::HostCreateSpec.new(
            host["username"],
            host["password"],
            host["usage_tags"],
            ip_address,
            metadata)
      end

    end
  end
end
