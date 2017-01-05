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

require_relative "spec_helper"

describe EsxCloud::Config do

  it "initializes logger" do
    logger = double(Logger)

    Logger.stub(:new).with(STDOUT).and_return(logger)
    logger.should_receive(:level=).with(Logger::WARN)

    expect { @real_config.client }.to raise_error(EsxCloud::Error)
    @real_config.logger.should be_nil

    @real_config.init
    @real_config.logger.should == logger
  end

  it "initializes logger in debug mode if DEBUG=1" do
    client = double(EsxCloud::ApiClient)
    logger = double(Logger)

    EsxCloud::ApiClient.stub(:new).and_return(client)
    Logger.stub(:new).with(STDOUT).and_return(logger)
    logger.should_receive(:level=).with(Logger::DEBUG)

    with_env("DEBUG" => "1") do
      @real_config.init
    end
  end

  it "looks up debug flag from environment variable" do
    with_env("DEBUG" => "1") do
      @real_config.debug?.should be_true
    end

    @real_config.debug?.should be_false
  end

end
