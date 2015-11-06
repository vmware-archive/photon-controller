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

package com.vmware.transfer.nfc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.X509TrustManager;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;

/**
 * Implementation of {@link X509TrustManager} that checks certificates
 * against a known thumbprint (SHA-1 hash).
 *
 * @author jmcclain
 * @version 1.0.0
 * @since 1.0
 */
public class ThumbprintTrustManager implements X509TrustManager {
  private static final Logger logger = LoggerFactory.getLogger(ThumbprintTrustManager.class);
  private final byte[] expectedThumbprint;

  /**
   * Constructor.
   *
   * @param expectedThumbprint SHA-1 certificate thumbprint we expect
   *                           to receive from the host
   */
  public ThumbprintTrustManager(byte[] expectedThumbprint) {
    this.expectedThumbprint = expectedThumbprint;
  }

  @Override
  public void checkClientTrusted(X509Certificate[] arg0, String arg1)
      throws CertificateException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void checkServerTrusted(X509Certificate[] chain, String authType)
      throws CertificateException {
    if (expectedThumbprint == null) {
      // We do not have a thumbprint for the host so we allow
      // connection w/o verification.
      logger.debug("thumbprint not available, skipping verification");
      return;
    }

    // Extract thumbprint from certificate
    MessageDigest md;
    try {
      // ESX only supports SHA-1 currently
      md = MessageDigest.getInstance("SHA1");
    } catch (NoSuchAlgorithmException e) {
      logger.error("can't find SHA1 digest algorithm");
      throw new AssertionError(e);
    }

    md.update(chain[0].getEncoded());
    byte[] certThumb = md.digest();

    // Check match
    if (Arrays.equals(certThumb, expectedThumbprint)) {
      return;
    }

    throw new RuntimeException("Host certificate thumbprint did not match expected value.");
  }

  @Override
  public X509Certificate[] getAcceptedIssuers() {
    return null;
  }
}
