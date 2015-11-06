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

module EsxCloud::Cli
  module Commands
    module Input

      # @param [String] tenant_name
      # @param [String] project_name
      # @param [OptionParser] opts_parser
      # @return Tenant, Project
      def ensure_project(tenant_name, project_name, opts_parser)
        # If tenant has been provided via command line we need to make sure
        # that project is not looked up in config file.
        if tenant_name && project_name.nil?
          usage_error("Please provide --project flag along with --tenant flag", opts_parser)
        end

        tenant = tenant_name ? find_tenant_by_name(tenant_name) : tenant_required
        project = project_name ? find_project_by_name(tenant, project_name) : project_required

        return project
      end

      # @param [Hash] opts
      # @param [OptionParser] opts_parser
      # @return [Hash]
      def ensure_vm_options(opts, opts_parser)
        if interactive?
          opts[:name] ||= ask("VM name: ")
          opts[:flavor] ||= ask("VM flavor: ")
          opts[:image_id] ||= ask("Image id: ")
          opts[:disks] = opts[:disks] ? parse_vm_disks(opts[:disks]) : ask_for_vm_disks
        else
          opts[:disks] = parse_vm_disks(opts[:disks])
        end

        opts[:affinities] = parse_affinities(opts[:affinities])
        opts[:environment] = parse_vm_environment opts[:environment]

        if opts[:name].blank? || opts[:flavor].blank? || opts[:image_id].blank? || opts[:disks].blank?
          usage_error("Please provide name, flavor, image and disks", opts_parser)
        end

        if interactive?
          puts
          puts "Creating VM: #{opts[:name]} (#{opts[:flavor]})"
          puts "Source image id: #{opts[:image_id]}"
          puts "Disks:"

          opts[:disks].each_with_index do |disk, i|
            disk_info = []
            disk_info << disk.name
            disk_info << disk.flavor
            disk_info << "#{disk.capacity_gb} GB" unless disk.capacity_gb.nil?
            disk_info << (disk.boot_disk ? "boot" : "non-boot")

            puts "  #{i + 1}: " + disk_info.join(", ")
          end
          puts
        end

        unless confirmed?
          puts yellow "OK, canceled"
          exit 0
        end

        opts
      end

      private

      def ask_for_vm_disks
        disks, index, kind = [], 1, "ephemeral-disk"

        while true
          puts
          puts "Disk #{index} (ENTER to finish): "
          name = ask("Name: ")
          break if name.empty?

          flavor = ask("Flavor: ")
          boot_disk = agree("Boot disk? [y/n]: ")
          capacity_gb = boot_disk ? nil : ask("Capacity (in GB): ")

          disks << EsxCloud::VmDisk.new(name, kind, flavor, capacity_gb, boot_disk)
          index += 1
        end

        disks
      end

      def parse_vm_disks(disks)
        return [] unless disks.is_a?(Enumerable)

        disks.map do |disk|
          if disk.is_a?(EsxCloud::VmDisk)
            disk
          else
            fields = disk.strip.split(/\s+/, 3)
            err("Invalid disk #{disk}") unless fields.size == 3
            name = fields[0]
            flavor = fields[1]
            kind = "ephemeral-disk"

            if fields[2] == "boot=true"
              boot_disk = true
            else
              boot_disk = false
              begin
                capacity_gb = Integer(fields[2])
              rescue ArgumentError => e
                err("Invalid disk #{disk}: " + e.to_s)
              end
            end

            EsxCloud::VmDisk.new(name, kind, flavor, capacity_gb, boot_disk)
          end
        end
      end

      def parse_vm_environment(env)
        return {} unless env.is_a?(Enumerable)

        env.inject({}) do |acc, value|
          k, v = value.split(/\s*=\s*/, 2)
          if k && v
            acc[k] = v
          end
          acc
        end
      end
    end
  end
end
