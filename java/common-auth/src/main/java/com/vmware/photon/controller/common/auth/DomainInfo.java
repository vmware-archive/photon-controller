/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.common.auth;

import com.vmware.af.PasswordCredential;
import com.vmware.af.VmAfClient;

public class DomainInfo
{
  private static final int DEFAULT_STS_PORT = 443;

  private final String _domainController;
  private final String _domain;
  private final String _username;
  private final String _password;
  private final int _port;

  protected DomainInfo(String domain, String domainController, PasswordCredential creds, int port)
  {
    _domain = domain;
    _domainController = domainController;
    _username = creds.getUserName();
    _password = creds.getPassword();
    _port = port;
  }

  public String getDomain()
  {
    return _domain;
  }

  public String getDomainController()
  {
    return _domainController;
  }

  public String getUsername()
  {
    return _username;
  }

  public String getPassword() { return _password; }

  public int getPort() { return _port; }

  public static DomainInfo build()
  {
    VmAfClient afd = new VmAfClient("localhost");

    String domain = afd.getDomainName();
    String domainController = afd.getDomainController();

    PasswordCredential creds = afd.getMachineAccountCredentials();

    return new DomainInfo(domain, domainController, creds, DEFAULT_STS_PORT);
  }
}
