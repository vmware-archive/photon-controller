/*
 * Copyright 2017 VMware, Inc. All Rights Reserved.
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

package io.dropwizard.jetty;

import com.vmware.provider.VecsLoadStoreParameter;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.collect.Iterables;
import io.dropwizard.validation.ValidationMethod;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

import java.security.KeyStore;

/**
 * This class overrides the HttpsConnectorFactory of Dropwizard by supplying a custom SSLContext. This SSL context
 * uses VMware Key Store (VKS) instead of Java Key Store (JKS). This connector factory gets called by supplying "vks"
 * as the application connector type in apife config in the photon controller yml file.
 *
 * Note: NOT all of dropwizard's https configuration options are implemented for this connector factory.
 */
@JsonTypeName("vks")
public class VKSConnectorFactory extends HttpsConnectorFactory {

  private static final Logger logger = LoggerFactory.getLogger(VKSConnectorFactory.class);

  // We do not need a KeyStore password for VKS. So allow the option to not supply a keyStorePassword by overriding
  // HttpsConnectorFactory's validate method (which blocks null and empty values).
  @Override
  @ValidationMethod
  public boolean isValidKeyStorePassword() {
    return true;
  }

  // We do not need a KeyStore path for VKS. So allow the option to not supply a keyStorePath by overriding
  // HttpsConnectorFactory's validate method (which blocks null and empty values).
  @Override
  @ValidationMethod
  public boolean isValidKeyStorePath() {
    return true;
  }

  @Override
  protected SslContextFactory buildSslContextFactory() {
    SslContextFactory sslContextFactory = new SslContextFactory();
    try {
      SSLContext sslContext = SSLContext.getInstance("TLS");
      KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      KeyStore keyStore = KeyStore.getInstance("VKS");
      keyStore.load(new VecsLoadStoreParameter("MACHINE_SSL_CERT"));
      keyManagerFactory.init(keyStore, null);
      sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
      sslContextFactory.setSslContext(sslContext);

      if (getNeedClientAuth() != null) {
        sslContextFactory.setNeedClientAuth(getNeedClientAuth());
      }

      if (getWantClientAuth() != null) {
        sslContextFactory.setWantClientAuth(getWantClientAuth());
      }

      sslContextFactory.setRenegotiationAllowed(getAllowRenegotiation());
      sslContextFactory.setEndpointIdentificationAlgorithm(getEndpointIdentificationAlgorithm());

      sslContextFactory.setValidateCerts(isValidateCerts());
      sslContextFactory.setValidatePeerCerts(getValidatePeers());

      if (getSupportedProtocols() != null) {
        sslContextFactory.setIncludeProtocols(Iterables.toArray(getSupportedProtocols(), String.class));
      }

      if (getExcludedProtocols() != null) {
        sslContextFactory.setExcludeProtocols(Iterables.toArray(getExcludedProtocols(), String.class));
      }

      if (getSupportedCipherSuites() != null) {
        sslContextFactory.setIncludeCipherSuites(Iterables.toArray(getSupportedCipherSuites(), String.class));
      }

      if (getExcludedCipherSuites() != null) {
        sslContextFactory.setExcludeCipherSuites(Iterables.toArray(getExcludedCipherSuites(), String.class));
      }
    } catch (Exception e) {
      logger.error("Failed to create SSL context correctly: {}", e.getMessage());
    }
    return sslContextFactory;
  }
}
