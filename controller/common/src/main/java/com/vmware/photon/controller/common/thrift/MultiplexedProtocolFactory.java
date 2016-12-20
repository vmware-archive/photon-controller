/*
 * Copyright 2015 VMware, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License.  You may obtain a copy of
 * the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, without warranties or
 * conditions of any kind, EITHER EXPRESS OR IMPLIED.  See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmware.photon.controller.common.thrift;

import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import org.apache.thrift.protocol.TMultiplexedProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

/**
 * Creates Thrift multiplexed protocols for a given Thrift service.
 */
public class MultiplexedProtocolFactory implements TProtocolFactory {

  private final TProtocolFactory factory;
  private final String serviceName;

  @Inject
  public MultiplexedProtocolFactory(TProtocolFactory factory, @Assisted String serviceName) {
    this.factory = factory;
    this.serviceName = serviceName;
  }

  @Override
  public TProtocol getProtocol(TTransport transport) {
    return new TMultiplexedProtocol(factory.getProtocol(transport), serviceName);
  }
}
