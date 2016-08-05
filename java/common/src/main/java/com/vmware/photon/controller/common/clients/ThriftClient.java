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

package com.vmware.photon.controller.common.clients;

import com.vmware.photon.controller.common.thrift.ClientPoolOptions;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.concurrent.TimeUnit;

/**
 * Host Client Facade that hides the zookeeper/async interactions.
 * Note that this class is not thread safe.
 */
@RpcClient
public class ThriftClient {

  protected static final ClientPoolOptions CLIENT_POOL_OPTIONS = new ClientPoolOptions()
      .setMaxClients(1)
      .setMaxWaiters(100)
      .setTimeout(30, TimeUnit.SECONDS)
      .setServiceName("Host");
  protected static final int DEFAULT_PORT_NUMBER = 8835;
  protected static final int MAX_RESERVED_PORT_NUMBER = 1023;

  private String hostIp;
  private int port;
  private String keyStorePath = "/keystore.jks";
  private String keyStorePassword;

  public String getHostIp() {
    return hostIp;
  }

  public void setHostIp(String hostIp) {
    setIpAndPort(hostIp, DEFAULT_PORT_NUMBER);
  }

  public int getPort() {
    return port;
  }

  public void setKeyStorePath(String keyStorePath) {
    this.keyStorePath = keyStorePath;
  }

  protected String getKeyStorePath() {
    return this.keyStorePath;
  }

  public void setKeyStorePassword(String keyStorePassword) {
    this.keyStorePassword = keyStorePassword;
  }

  protected String getKeyStorePassword() {
    return this.keyStorePassword;
  }

  public void setIpAndPort(String ip, int port) {
    checkNotNull(ip, "IP can not be null");
    checkArgument(port > MAX_RESERVED_PORT_NUMBER,
        "Please set port above %s", MAX_RESERVED_PORT_NUMBER);

    if (ip.equals(this.hostIp) && port == this.port) {
      return;
    }

    this.close();
    this.hostIp = ip;
    this.port = port;
  }

  public void close() {

  }
}
