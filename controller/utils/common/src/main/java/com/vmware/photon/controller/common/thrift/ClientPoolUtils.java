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
import org.apache.thrift.async.TAsyncSSLClient;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.SSLClientSocketFactory;
import org.apache.thrift.transport.TNonBlockingSSLSocket;
import org.apache.thrift.transport.TNonblockingSSLTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Map;

/**
 * Utility functions for classes {@link ClientPoolImpl} and
 * {@link BasicClientPool}.
 */
public class ClientPoolUtils {

    private static final int DEFAULT_TIMEOUT = 0;
    private static final Logger logger = LoggerFactory.getLogger(ClientPoolUtils.class);

    public static <C extends TAsyncSSLClient> C createNewClient(
        InetSocketAddress address,
        TProtocolFactory protocolFactory,
        ClientPoolOptions options,
        ThriftFactory thriftFactory,
        TAsyncSSLClientFactory<C> clientFactory, Map<C, TNonblockingSSLTransport> clientTransportMap,
        SSLContext sslContext
        ) throws IOException, TTransportException {

        TNonBlockingSSLSocket socket
          = SSLClientSocketFactory.create(address.getHostString(), address.getPort(), DEFAULT_TIMEOUT, sslContext);

        if (StringUtils.isNotBlank(options.getServiceName())) {
            protocolFactory = thriftFactory.create(options.getServiceName());
        }

        C client = clientFactory.create(protocolFactory, socket);
        clientTransportMap.put(client, socket);
        logger.debug("created new client {} for {}", client, address);
        return client;
    }
}
