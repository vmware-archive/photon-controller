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

import com.vmware.photon.controller.common.cert.X509CertificateHelper;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Static utility functions for OAuth clients.
 */
public class AuthCertificateStore {
  private final KeyStore keyStore;

  /**
   * Constructor.
   */
  AuthCertificateStore() throws AuthException {
    try {
      keyStore = KeyStore.getInstance("JKS");
      keyStore.load(null, null);
    } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
      throw new AuthException("Failed to get JKS key store.", e);
    }
  }

  /**
   * Retrieve key store.
   *
   * @return
   */
  public KeyStore getKeyStore() {
    return keyStore;
  }

  /**
   * Add a base64 certificate to key store.
   *
   * @param name
   * @param base64Certificate
   * @throws AuthException
   * @throws KeyStoreException
   */
  public void setCertificateEntry(String name, String base64Certificate) throws AuthException {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    X509Certificate x509Certificate = null;

    try {
      x509Certificate = x509CertificateHelper.getX509CertificateFromBase64(base64Certificate);
    } catch (CertificateException e) {
      throw new AuthException("Failed to create X509Certificate for the passed base64 certificate", e);
    }

    setCertificateEntry(name, x509Certificate);
  }

  /**
   * Add a X509 certificate to key store.
   *
   * @param name
   * @param x509Certificate
   * @throws AuthException
   * @throws KeyStoreException
   */
  public void setCertificateEntry(String name, X509Certificate x509Certificate) throws AuthException {
    try {
      keyStore.setCertificateEntry(name, x509Certificate);
    } catch (KeyStoreException e) {
      throw new AuthException("Failed to add cerificate to the store.");
    }
  }
}
