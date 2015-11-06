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

package com.vmware.photon.controller.common.auth;

import com.vmware.identity.openidconnect.client.AdminServerException;
import com.vmware.identity.openidconnect.client.AuthenticationFrameworkHelper;
import com.vmware.identity.openidconnect.client.ClientConfig;
import com.vmware.identity.openidconnect.client.ClientID;
import com.vmware.identity.openidconnect.client.ClientRegistrationHelper;
import com.vmware.identity.openidconnect.client.ConnectionConfig;
import com.vmware.identity.openidconnect.client.MetadataHelper;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCServerException;
import com.vmware.identity.openidconnect.client.ProviderMetadata;
import com.vmware.identity.openidconnect.client.SSLConnectionException;

import java.net.URISyntaxException;
import java.security.interfaces.RSAPublicKey;

/**
 * General OIDC client metadata.
 */
public class AuthOIDCClient {
  private String domainControllerFQDN;
  private int domainControllerPort;
  private String tenant;
  private AuthCertificateStore certificateStore;
  private AuthenticationFrameworkHelper authenticationFrameworkHelper;
  private ProviderMetadata providerMetadata;
  private RSAPublicKey providerPublicKey;

  /**
   * Constructor.
   */
  public AuthOIDCClient(String domainControllerFQDN, int domainControllerPort, String tenant) throws URISyntaxException,
      AuthException {
    try {
      this.domainControllerFQDN = domainControllerFQDN;
      this.domainControllerPort = domainControllerPort;
      this.tenant = tenant;
      this.certificateStore = new AuthCertificateStore();

      authenticationFrameworkHelper = new AuthenticationFrameworkHelper(
          domainControllerFQDN,
          domainControllerPort);

      authenticationFrameworkHelper.populateSSLCertificates(certificateStore.getKeyStore());

      MetadataHelper metadataHelper = new MetadataHelper.Builder(domainControllerFQDN)
          .domainControllerPort(this.domainControllerPort)
          .tenant(this.tenant)
          .keyStore(certificateStore.getKeyStore()).build();
      this.providerMetadata = metadataHelper.getProviderMetadata();
      this.providerPublicKey = metadataHelper.getProviderRSAPublicKey(providerMetadata);

    } catch (AuthException | SSLConnectionException | OIDCClientException | AdminServerException | OIDCServerException
    e) {
      throw new AuthException("Failed to build client metadata.", e);
    }
  }

  /**
   * Get token handler class instance.
   */
  public AuthTokenHandler getTokenHandler() {
    return new AuthTokenHandler(getOidcClient(), getProviderPublicKey(), getProviderMetadata());
  }

  /**
   * Get client handler class instance.
   */
  public AuthClientHandler getClientHandler(
      String user,
      String password)
      throws AuthException {
    return new AuthClientHandler(
        this,
        getClientRegistrationHelper(),
        getTokenHandler(),
        user,
        password);
  }

  /**
   * Get provider publick key. Package visibility.
   */
  RSAPublicKey getProviderPublicKey() {
    return providerPublicKey;
  }

  /**
   * Get provider metadata. Package visibility.
   */
  ProviderMetadata getProviderMetadata() {
    return providerMetadata;
  }

  /**
   * Get OIDC client. Package visibility.
   */
  OIDCClient getOidcClient() {
    return getOidcClient(null);
  }

  /**
   * Create OIDCClient object using the retrieved metadata. Package visibility.
   */
  OIDCClient getOidcClient(ClientID clientID) {
    ConnectionConfig connectionConfig = new ConnectionConfig(
        providerMetadata,
        providerPublicKey,
        certificateStore.getKeyStore());
    return new OIDCClient(new ClientConfig(connectionConfig, clientID, null));
  }

  /**
   * Get client registration helper.. Package visibility.
   */
  ClientRegistrationHelper getClientRegistrationHelper() throws AuthException {
    return new ClientRegistrationHelper.Builder(domainControllerFQDN)
        .domainControllerPort(domainControllerPort)
        .tenant(tenant)
        .keyStore(certificateStore.getKeyStore()).build();
  }

  /**
   * Resource servers that require access tokens.
   */
  public enum ResourceServer {
    rs_esxcloud,
    rs_admin_server
  }
}
