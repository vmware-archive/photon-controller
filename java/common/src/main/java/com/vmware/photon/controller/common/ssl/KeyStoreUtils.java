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

package com.vmware.photon.controller.common.ssl;

import com.google.common.io.Files;
import sun.security.tools.keytool.CertAndKeyGen;
import sun.security.x509.X500Name;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import java.io.File;
import java.io.FileOutputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * This class generates a self signed certificate.
 * We use self signed certificates in the non-auth mode.
 */
public class KeyStoreUtils {
  private static final int keysize = 1024;
  private static final String commonName = "photon-controller.vmware.com";
  private static final String organizationalUnit = "BU";
  private static final String organization = "CNA";
  private static final String city = "Bellevue";
  private static final String state = "Washington";
  private static final String country = "WA";
  private static final long validity = TimeUnit.DAYS.toSeconds(365) * 9; // 9 years
  private static final String alias = "__MACHINE";

  public static final String KEY_STORE_NAME = "keystore.jks";
  public static final String KEY_PASS = "photon-controller";

  public static final String THRIFT_PROTOCOL = "TLS";

  public static void generateKeys(String folder) {
    try {
      File f = new File(folder + "/" + KEY_STORE_NAME);
      Files.createParentDirs(f);
      f.createNewFile();

      KeyStore keyStore = KeyStore.getInstance("JKS");
      keyStore.load(null, null);

      CertAndKeyGen keypair = new CertAndKeyGen("RSA", "SHA1WithRSA", null);

      X500Name x500Name = new X500Name(commonName, organizationalUnit, organization, city, state, country);

      keypair.generate(keysize);
      PrivateKey privKey = keypair.getPrivateKey();

      X509Certificate[] chain = new X509Certificate[1];
      chain[0] = keypair.getSelfCertificate(x500Name, new Date(), validity);

      keyStore.setKeyEntry(alias, privKey, KEY_PASS.toCharArray(), chain);

      keyStore.store(new FileOutputStream(f.getAbsolutePath()), KEY_PASS.toCharArray());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static SSLContext acceptAllCerts(String protocol) {
    TrustManager tm = new X509TrustManager() {
      @Override
      public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      }

      @Override
      public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      }

      @Override
      public X509Certificate[] getAcceptedIssuers() {
        return null;
      }
    };

    try {
      SSLContext instance = SSLContext.getInstance(protocol);
      instance.init(null, new TrustManager[] {tm}, null);
      SSLContext.setDefault(instance);
      return instance;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
}
