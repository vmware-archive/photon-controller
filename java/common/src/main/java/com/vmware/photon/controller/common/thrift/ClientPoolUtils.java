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

import org.apache.commons.lang3.StringUtils;
import org.apache.thrift.async.TAsyncClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Utility functions for classes {@link ClientPoolImpl} and {@link BasicClientPool}.
 */
public class ClientPoolUtils {

  private static final Logger logger = LoggerFactory.getLogger(ClientPoolUtils.class);

  public static <C extends TAsyncClient> C createNewClient(
      InetSocketAddress address, TProtocolFactory protocolFactory,
      ClientPoolOptions options, ThriftFactory thriftFactory,
      TAsyncClientFactory<C> clientFactory, Map<C, TTransport> clientTransportMap)
      throws IOException, TTransportException {
    TTransport socket = null;

    if (!isKeyStoreUsed(options.getKeyStorePath())) {
      // Auth is not enabled
      socket = new TNonblockingSocket(address.getHostString(), address.getPort());
    } else {
      TSSLTransportFactory.TSSLTransportParameters params =
          new TSSLTransportFactory.TSSLTransportParameters();
      params.setTrustStore(options.getKeyStorePath(), options.getKeyStorePassword());

      socket = TSSLTransportFactory.getClientSocket(address.getHostString(), address.getPort(), (options.getTimeoutMs
          () == 0L) ? 10000 : (int) options.getTimeoutMs(), params);
    }
    if (StringUtils.isNotBlank(options.getServiceName())) {
      protocolFactory = thriftFactory.create(options.getServiceName());
    }

    C client = clientFactory.create(protocolFactory, socket);
    clientTransportMap.put(client, socket);
    logger.debug("created new client {} for {}", client, address);
    return client;
  }

  private static boolean isKeyStoreUsed(String keyStorePath) {
    if (keyStorePath == null) {
      return false;
    }

    File file = new File(keyStorePath);
    return file.exists();
  }
}
