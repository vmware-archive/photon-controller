/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.nsxclient;

import com.vmware.photon.controller.nsxclient.apis.DhcpServiceApi;
import com.vmware.photon.controller.nsxclient.apis.FabricApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalRouterApi;
import com.vmware.photon.controller.nsxclient.apis.LogicalSwitchApi;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * This class represents the NSX client.
 */
public class NsxClient {

  private final static int CREATE_LOGICAL_SWITCH_POLL_DELAY = 5000;
  private final static int DELETE_LOGICAL_SWITCH_POLL_DELAY = 10;
  private final static int DELETE_LOGICAL_ROUTER_POLL_DELAY = 10;
  private final static int DELETE_LOGICAL_PORT_POLL_DELAY = 1000;

  private final RestClient restClient;

  private final FabricApi fabricApi;
  private final LogicalSwitchApi logicalSwitchApi;
  private final LogicalRouterApi logicalRouterApi;
  private final DhcpServiceApi dhcpServiceApi;

  /**
   * Constructs a NSX client.
   */
  public NsxClient(String target,
                   String username,
                   String password) {
    if (!target.startsWith("https")) {
      target = "https://" + target;
    }

    this.restClient = new RestClient(target, username, password);

    this.fabricApi = new FabricApi(restClient);
    this.logicalSwitchApi = new LogicalSwitchApi(restClient);
    this.logicalRouterApi = new LogicalRouterApi(restClient);
    this.dhcpServiceApi = new DhcpServiceApi(restClient);
  }

  /**
   * Returns NSX fabric API client.
   */
  public FabricApi getFabricApi() {
    return this.fabricApi;
  }

  /**
   * Returns NSX logical switch API client.
   */
  public LogicalSwitchApi getLogicalSwitchApi() {
    return this.logicalSwitchApi;
  }

  /**
   * Return NSX logical router API client.
   */
  public LogicalRouterApi getLogicalRouterApi() {
    return this.logicalRouterApi;
  }

  /**
   * Returns NSX DHCP service API client.
   */
  public DhcpServiceApi getDhcpServiceApi() {
    return this.dhcpServiceApi;
  }

  /**
   * Returns a poll delay value in milliseconds. The delay is used as the interval to poll
   * the status of a created logical switch before we can claim the creation successful.
   */
  public int getCreateLogicalSwitchPollDelay() {
    return CREATE_LOGICAL_SWITCH_POLL_DELAY;
  }

  /**
   * Returns a poll delay value in milliseconds. The delay is used as the interval to poll
   * the status of a deleted logical switch before we can claim deletion successful.
   */
  public int getDeleteLogicalSwitchPollDelay() {
    return DELETE_LOGICAL_SWITCH_POLL_DELAY;
  }

  /**
   * Returns a poll delay value in milliseconds. The delay is used as the interval to poll
   * the status of a deleted logical router before we can claim deletion successful.
   */
  public int getDeleteLogicalRouterPollDelay() {
    return DELETE_LOGICAL_ROUTER_POLL_DELAY;
  }

  /**
   * Returns a poll delay value in milliseconds. The delay is used as the interval to poll
   * the status of a deleted logical port before we can claim deletion successful.
   */
  public int getDeleteLogicalPortPollDelay() {
    return DELETE_LOGICAL_PORT_POLL_DELAY;
  }

  public String getHostThumbprint(String ipAddress, int port) throws Throwable {
    // Create a trust manager that does not validate certificate chains
    TrustManager[] trustAllCerts = new TrustManager[] {new X509TrustManager() {
      public java.security.cert.X509Certificate[] getAcceptedIssuers() {
        return null;
      }
      public void checkClientTrusted(X509Certificate[] certs, String authType) {
      }
      public void checkServerTrusted(X509Certificate[] certs, String authType) {
      }
    }};

    // Install the all-trusting trust manager
    SSLContext sc = SSLContext.getInstance("SSL");
    sc.init(null, trustAllCerts, null);
    SSLSocketFactory factory = sc.getSocketFactory();
    SSLSocket socket = (SSLSocket) factory.createSocket(ipAddress, port);
    socket.startHandshake();
    SSLSession session = socket.getSession();
    Certificate[] certs = session.getPeerCertificates();

    Certificate x509Cert = null;
    for (Certificate cert : certs) {
      if (cert instanceof X509Certificate) {
        x509Cert = cert;
        break;
      }
    }

    if (x509Cert == null) {
      throw new IllegalStateException("Cannot find X509Certificate for host " + ipAddress);
    }

    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    digest.update(x509Cert.getEncoded());

    char[] hexDigits = {'0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    byte[] bytes = digest.digest();
    StringBuffer buf = new StringBuffer(bytes.length * 2);

    for (int i = 0; i < digest.digest().length; ++i) {
      buf.append(hexDigits[(bytes[i] & 0xf0) >> 4]);
      buf.append(hexDigits[bytes[i] & 0x0f]);
    }

    return buf.toString();
  }
}
