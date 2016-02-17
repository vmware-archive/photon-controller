#!/usr/bin/env ruby
#
# Generate Go constants for standardized DHCP options from IANA web page.
#

require "open-uri"
require "nokogiri"

def rfc_name(rfc)
  file = open("https://tools.ietf.org/html/rfc%d" % rfc)
  doc = Nokogiri::XML.parse(file.read)
  title = doc / 'title'
  parts = title.inner_text.split(' - ', 2)
  parts[1]
end

file = open("https://www.iana.org/assignments/bootp-dhcp-parameters/bootp-dhcp-parameters.xhtml")
doc = Nokogiri::XML.parse(file.read)
rfcs = {}

(doc / 'table#table-options tbody > tr').each do |tr|
  cells = (tr / 'td').to_a

  name = cells[1].inner_text
  name = name.
    split(/[\-\/ ]/).
    map { |e|
      if e == e.downcase
        e.capitalize
      else
        e
      end
    }.
    join

  next if name =~ /unassigned/i

  number = cells[0].inner_text.to_i

  # Munge the "vendor-specific" part for these options
  if name =~ /^PXEUndefined/
    name = "PXEUndefined%d" % number
  end

  rfc = cells[4].inner_text[/RFC(\d+)/, 1].to_i

  rfcs[rfc] ||= []
  rfcs[rfc] << [name, number]
end

rfcs.keys.sort.each do |rfc|
  # Skip RFC if it only contains >= 128 numbers
  next if rfcs[rfc].all? { |(_, number)| number >= 128 }

  puts "// From RFC%d: %s" % [rfc, rfc_name(rfc)]
  puts "const ("
  rfcs[rfc].each do |(name, number)|
    puts "Option%s = Option(%d)" % [name, number]
  end
  puts ")"
  puts
end
