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

require 'rexml/document'
require 'open3'
require 'tmpdir'
include REXML

module EsxCloud::Cli
  module Commands
    class Images < Base
      usage "image upload <path> [<options>]"
      desc "Upload a new image"
      def upload(args = [])
        path = shift_keyword_arg(args)
        name, image_replication = nil, nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-n", "--name NAME", "Image name") { |n| name = n }
          opts.on("-i", "--image replication", "Image replication type") { |replication| image_replication = replication }
        end
        parse_options(args, opts_parser)

        if interactive?
          path ||= ask("Image path: ")
        end

        if path.blank?
          usage_error("Please provide image path")
        end

        path = File.expand_path(path, Dir.pwd)
        puts "Uploading image file #{path}"

        usage_error("No such image file at that path") unless File.exist?(path)

        if interactive?
          default_name = File.basename(path)
          name ||= ask("Image name (default: #{default_name}): ")
          name = default_name if name.empty?

          default_image_replication = "EAGER"
          image_replication ||= ask("Image replication type (default: #{default_image_replication}): ")
          image_replication = default_image_replication if image_replication.empty?
        end

        image = client.create_image(path, name, image_replication)
        puts green("Image '#{image.name}' created (ID=#{image.id})")
      end

      usage "image show <id>"
      desc "Show current image"
      def show_current(args = [])
        id = shift_keyword_arg(args)
        parse_options(args)

        if interactive?
          id ||= ask("Image id: ")
        end

        if id.blank?
          usage_error("Please provide image id")
        end

        image = client.find_image_by_id(id)

        puts "Image #{image.id}:"
        puts "  Name: #{image.name}"
        puts "  State: #{image.state}"
        puts "  Size: #{image.size} Byte(s)"
        puts "  Image Replication Type: #{image.replication}"
        puts "  Image Replication Progress: #{image.replication_progress}"
        puts "  Image Seeding Progress: #{image.seeding_progress}"
        puts "  Settings:"
        image.settings.each do |setting|
          puts "    #{setting['name']} : #{setting['defaultValue']}"
        end
        puts
      end

      usage "image list"
      desc "List images"
      def list(args = [])
        parse_options(args)
        images = client.find_all_images.items

        table = Terminal::Table.new
        table.headings = %w(ID Name State Size(Byte) Replication_type)
        table.rows = images.map do |i|
          [i.id, i.name, i.state, i.size, i.replication]
        end

        puts
        puts table
        puts
        puts "Total: #{images.size}"
      end

      usage "image delete [<id>]"
      desc "Delete image"
      def delete_image(args = [])
        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide image id")
        end

        if confirmed?
          client.delete_image(id)
          puts green("Deleted image '#{id}'")
        else
          puts yellow("OK, canceled")
        end
      end

      usage "image tasks <id> [<options>]"
      desc "Show Image tasks"
      def tasks(args = [])
        state = nil

        opts_parser = OptionParser.new do |opts|
          opts.on("-s", "--state VALUE", "Task state") {|ts| state = ts}
        end

        id = shift_keyword_arg(args)
        if id.blank?
          usage_error("Please provide Image id", opts_parser)
        end

        parse_options(args, opts_parser)

        render_task_list(client.get_image_tasks(id, state).items)
      end
    end
  end
end
