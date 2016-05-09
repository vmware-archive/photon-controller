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

import com.vmware.identity.openidconnect.client.ClientConfig;
import com.vmware.identity.openidconnect.client.ConnectionConfig;
import com.vmware.identity.openidconnect.client.MetadataHelper;
import com.vmware.identity.openidconnect.client.OIDCClient;
import com.vmware.identity.openidconnect.client.OIDCClientException;
import com.vmware.identity.openidconnect.client.OIDCServerException;
import com.vmware.identity.openidconnect.client.SSLConnectionException;
import com.vmware.identity.openidconnect.common.ClientID;
import com.vmware.identity.openidconnect.common.ProviderMetadata;
import com.vmware.identity.rest.afd.client.AfdClient;
import com.vmware.identity.rest.core.client.exceptions.ClientException;
import com.vmware.identity.rest.core.data.CertificateDTO;
import com.vmware.identity.rest.idm.client.IdmClient;

import org.apache.http.HttpException;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.TrustStrategy;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * General OIDC client metadata.
 */
public class AuthOIDCClient {
  private String domainControllerFQDN;
  private int domainControllerPort;
  private String tenant;
  private AuthCertificateStore certificateStore;
  private ProviderMetadata providerMetadata;
  private RSAPublicKey providerPublicKey;

  /**
   * Constructor.
   */
  public AuthOIDCClient(String domainControllerFQDN, int domainControllerPort, String tenant)
      throws AuthException {
    try {
      this.domainControllerFQDN = domainControllerFQDN;
      this.domainControllerPort = domainControllerPort;
      this.tenant = tenant;
      this.certificateStore = new AuthCertificateStore();

      AfdClient afdClient = setSSLTrustPolicy(domainControllerFQDN, domainControllerPort);
      populateSSLCertificates(afdClient);

      MetadataHelper metadataHelper = new MetadataHelper.Builder(domainControllerFQDN)
          .domainControllerPort(this.domainControllerPort)
          .tenant(this.tenant)
          .keyStore(certificateStore.getKeyStore()).build();
      this.providerMetadata = metadataHelper.getProviderMetadata();
      this.providerPublicKey = metadataHelper.getProviderRSAPublicKey(providerMetadata);

    } catch (AuthException | SSLConnectionException | OIDCClientException | OIDCServerException e) {
      throw new AuthException("Failed to build client metadata.", e);
    }
  }

  /**
   * Get token handler class instance.
   */
  public AuthTokenHandler getTokenHandler() {
    return new AuthTokenHandler(getOidcClient(), getProviderPublicKey());
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
        createIdmClient(domainControllerFQDN, domainControllerPort, user, password),
        getTokenHandler(),
        user,
        password,
        tenant);
  }

  private AfdClient setSSLTrustPolicy(String domainControllerFQDN, int domainControllerPort)
      throws AuthException {
    try {
      return
          new AfdClient(domainControllerFQDN,
              domainControllerPort,
              new DefaultHostnameVerifier(),
              new SSLContextBuilder()
                  .loadTrustMaterial(null, new TrustStrategy() {
                    @Override
                    public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                      return true;
                    }
                  }).build());

    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
      throw new AuthException("Failed to set SSL policy", e);
    }
  }


  private void populateSSLCertificates(AfdClient afdClient)
      throws AuthException {
    try {
      List<CertificateDTO> certs = afdClient.vecs().getSSLCertificates();
      int index = 1;
      for (CertificateDTO cert : certs) {
        certificateStore.getKeyStore()
            .setCertificateEntry(String.format("VecsSSLCert%d", index), cert.getX509Certificate());
        index++;
      }
    } catch (KeyStoreException | ClientException | HttpException | IOException e) {
      throw new AuthException("Failed to populate SSL Certificates", e);
    }
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

  private IdmClient createIdmClient(
      String domainControllerFQDN,
      int domainControllerPort,
      String user,
      String password)
      throws AuthException {
    try {
      TrustManagerFactory trustManagerFactory =
          TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagerFactory.init(certificateStore.getKeyStore());
      SSLContext sslContext = SSLContext.getInstance("SSL");
      sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
      IdmClient idmClient =
          new IdmClient(domainControllerFQDN, domainControllerPort, new DefaultHostnameVerifier(), sslContext);

      com.vmware.identity.openidconnect.common.AccessToken accessToken = getTokenHandler()
          .getAdminServerAccessToken(user, password)
          .getAccessToken();

      com.vmware.identity.rest.core.client.AccessToken restAccessToken =
          new com.vmware.identity.rest.core.client.AccessToken(accessToken.serialize(),
              com.vmware.identity.rest.core.client.AccessToken.Type.JWT);
      idmClient.setToken(restAccessToken);
      return idmClient;
    } catch (NoSuchAlgorithmException | KeyStoreException | KeyManagementException e) {
      throw new AuthException("Failed to createIdmClient", e);
    }
  }

  /**
   * Resource servers that require access tokens.
   */
  public enum ResourceServer {
    rs_esxcloud,
    rs_admin_server
  }
}
