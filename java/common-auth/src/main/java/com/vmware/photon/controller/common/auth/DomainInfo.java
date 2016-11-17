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


package com.vmware.photon.controller.common.auth;

import com.vmware.af.PasswordCredential;
import com.vmware.af.VmAfClient;

/**
 *
 * This class represents the Lightwave domain info.
 *
 */
public class DomainInfo {
  private static final int DEFAULT_STS_PORT = 443;

  private final String domainController;
  private final String domain;
  private final String username;
  private final String password;
  private final int port;

  protected DomainInfo(String domain, String domainController, PasswordCredential creds, int port) {
    this.domain = domain;
    this.domainController = domainController;
    this.username = creds.getUserName();
    this.password = creds.getPassword();
    this.port = port;
  }

  public String getDomain() {
    return domain;
  }

  public String getDomainController() {
    return domainController;
  }

  public String getUsername() {
    return username;
  }

  public String getPassword() {
    return password;
  }

  public int getPort() {
    return port;
  }

  public static DomainInfo build() {
    VmAfClient afd = new VmAfClient("localhost");

    String domain = afd.getDomainName();
    String domainController = afd.getDomainController();

    PasswordCredential creds = afd.getMachineAccountCredentials();

    return new DomainInfo(domain, domainController, creds, DEFAULT_STS_PORT);
  }
}
