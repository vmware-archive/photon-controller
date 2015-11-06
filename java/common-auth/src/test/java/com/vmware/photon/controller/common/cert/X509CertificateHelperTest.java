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

package com.vmware.photon.controller.common.cert;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Test for {@link X509CertificateHelper}.
 */
public class X509CertificateHelperTest {
  @Test
  public void generateX509CertificateTestSuccess() throws Throwable {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    X509Certificate cert = x509CertificateHelper.generateX509Certificate();

    assertThat(cert, is(notNullValue()));
    assertThat(cert.getSigAlgName(), is("SHA1withRSA"));
  }

  @Test(expectedExceptions = NoSuchAlgorithmException.class)
  public void generateX509CertificateTestFailure() throws Throwable {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    X509Certificate cert = x509CertificateHelper.generateX509Certificate("InvalidAlg", "SHA1withRSA");
  }

  @Test
  public void x509CertificateToBase64Test() throws Throwable {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    X509Certificate cert = x509CertificateHelper.generateX509Certificate();
    String base64Cert = x509CertificateHelper.x509CertificateToBase64(cert);

    assertThat(base64Cert, is(notNullValue()));
    assertThat(base64Cert.length(), greaterThan(0));
  }

  @Test
  public void getX509CertificateFromBase64TestSuccess() throws Throwable {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    X509Certificate cert = x509CertificateHelper.generateX509Certificate();
    String base64Cert = x509CertificateHelper.x509CertificateToBase64(cert);

    X509Certificate convertedCert = x509CertificateHelper.getX509CertificateFromBase64(base64Cert);

    assertThat(convertedCert, is(cert));
  }

  @Test(expectedExceptions = CertificateException.class)
  public void getX509CertificateFromBase64TestFailure() throws Throwable {
    X509CertificateHelper x509CertificateHelper = new X509CertificateHelper();
    x509CertificateHelper.getX509CertificateFromBase64("invalid cert");
  }
}
