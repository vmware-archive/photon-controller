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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;

/**
 * Auth test helper.
 */
public class AuthTestHelper {
  public static final String TENANT = "lotus_tenant";
  public static final String USER = "Administrator@lotus";
  public static final String LOOKUP_SERVICE_URL = "https://1.1.1.1:7444/lookupservice/sdk";
  public static final String PASSWORD = "lotus_password";
  public static RSAPrivateKey privateKey;
  public static RSAPublicKey publicKey;
  public static Date issueTime;
  public static Date expirationTime;

  static {
    KeyPairGenerator keyGenerator;
    try {
      keyGenerator = KeyPairGenerator.getInstance("RSA");
      keyGenerator.initialize(1024, new SecureRandom());
      KeyPair keyPair = keyGenerator.genKeyPair();
      privateKey = (RSAPrivateKey) keyPair.getPrivate();
      publicKey = (RSAPublicKey) keyPair.getPublic();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalArgumentException(e.getMessage());
    }

    long lifetimeSeconds = 300;
    issueTime = new Date();
    expirationTime = new Date(issueTime.getTime() + (lifetimeSeconds * 1000L));
  }

}
