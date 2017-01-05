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

require_relative "../spec_helper"

describe EsxCloud::Iso do

  before(:each) do
    @client = double(EsxCloud::ApiClient)
    allow(EsxCloud::Config).to receive(:client).and_return(@client)
  end

  it "can be created from hash or from JSON" do
    hash = {
        "id" => "foo",
        "name" => "iso_name",
        "size" => 1234
    }
    from_hash = EsxCloud::Iso.create_from_hash(hash)
    from_json = EsxCloud::Iso.create_from_json(JSON.generate(hash))

    [from_hash, from_json].each do |image|
      image.id.should == "foo"
      image.name.should == "iso_name"
      image.size.should == 1234
    end
  end

  it "expects hash to have all required keys" do
    expect { EsxCloud::Image.create_from_hash([]) }.to raise_error(EsxCloud::UnexpectedFormat)
  end

end
