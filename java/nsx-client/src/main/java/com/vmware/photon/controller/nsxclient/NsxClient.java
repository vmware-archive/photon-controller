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

import com.vmware.photon.controller.nsxclient.apis.FabricApi;
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

  private final RestClient restClient;

  private final FabricApi fabricApi;
  private final LogicalSwitchApi logicalSwitchApi;

  public NsxClient(String target,
                   String username,
                   String password) {
    if (!target.startsWith("https")) {
      target = "https://" + target;
    }

    this.restClient = new RestClient(target, username, password);

    this.fabricApi = new FabricApi(restClient);
    this.logicalSwitchApi = new LogicalSwitchApi(restClient);
  }

  public FabricApi getFabricApi() {
    return this.fabricApi;
  }

  public LogicalSwitchApi getLogicalSwitchApi() {
    return this.logicalSwitchApi;
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
