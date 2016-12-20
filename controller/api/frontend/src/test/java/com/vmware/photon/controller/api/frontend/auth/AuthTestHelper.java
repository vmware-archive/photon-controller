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

package com.vmware.photon.controller.api.frontend.auth;

import com.vmware.identity.openidconnect.client.JWTBuilder;
import com.vmware.identity.openidconnect.client.ResourceServerAccessToken;
import com.vmware.identity.openidconnect.client.TokenValidationException;
import com.vmware.identity.openidconnect.common.ClientID;
import com.vmware.identity.openidconnect.common.Issuer;
import com.vmware.identity.openidconnect.common.JWTID;
import com.vmware.identity.openidconnect.common.Nonce;
import com.vmware.identity.openidconnect.common.Scope;
import com.vmware.identity.openidconnect.common.SessionID;
import com.vmware.identity.openidconnect.common.Subject;
import com.vmware.identity.openidconnect.common.TokenType;

import com.nimbusds.jose.JOSEException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;


/**
 * Test helper.
 */

public class AuthTestHelper {

  private static final RSAPrivateKey privateKey;
  private static final RSAPublicKey publicKey;
  private static long lifetimeSeconds = 300;
  private static Date issueTime = new Date();
  private static Date expirationTime = new Date(issueTime.getTime() + (lifetimeSeconds * 1000L));

  static {
    try {
      KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
      keyGenerator.initialize(1024, new SecureRandom());
      KeyPair keyPair = keyGenerator.genKeyPair();
      privateKey = (RSAPrivateKey) keyPair.getPrivate();
      publicKey = (RSAPublicKey) keyPair.getPublic();

    } catch (Exception e) {
      throw new IllegalStateException(e);
    }

  }

  public static ResourceServerAccessToken generateResourceServerAccessToken(Collection<String> group) throws
      TokenValidationException, JOSEException {

    String accessToken = JWTBuilder.accessTokenBuilder(privateKey).
            tokenType(TokenType.BEARER).
            jwtId(new JWTID()).
            issuer(new Issuer("iss")).
            subject(new Subject("sub")).
            audience(Collections.<String>singletonList("rs_esxcloud")).
            issueTime(issueTime).
            expirationTime(expirationTime).
            scope(Scope.OPENID).
            tenant("tenant").
            clientId((ClientID) null).
            sessionId((SessionID) null).
            holderOfKey(null).
            actAs((Subject) null).
            nonce((Nonce) null).
            groups(group).build();

    return ResourceServerAccessToken.build(
        accessToken,
        publicKey,
        new Issuer("iss"),
        "rs_esxcloud",
        0L
    );
  }

  public static String generateExpiredResourceServerAccessToken() throws
          TokenValidationException, JOSEException {
    String accessToken = JWTBuilder.accessTokenBuilder(privateKey).
            tokenType(TokenType.BEARER).
            jwtId(new JWTID()).
            issuer(new Issuer("iss")).
            subject(new Subject("sub")).
            audience(Collections.<String>singletonList("rs_esxcloud")).
            issueTime(new Date(issueTime.getTime() - (lifetimeSeconds * 1000L))).
            expirationTime(issueTime).
            scope(Scope.OPENID).
            tenant("tenant").
            clientId((ClientID) null).
            sessionId((SessionID) null).
            holderOfKey(null).
            actAs((Subject) null).
            nonce((Nonce) null).
            groups(null).build();

    return accessToken;
  }

}
