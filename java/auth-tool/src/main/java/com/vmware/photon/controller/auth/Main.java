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

package com.vmware.photon.controller.auth;

import com.vmware.identity.openidconnect.client.OIDCTokens;
import com.vmware.identity.openidconnect.common.AccessToken;
import com.vmware.identity.openidconnect.common.ParseException;
import com.vmware.identity.openidconnect.common.RefreshToken;
import com.vmware.photon.controller.common.auth.AuthClientHandler;
import com.vmware.photon.controller.common.auth.AuthException;
import com.vmware.photon.controller.common.auth.AuthOIDCClient;
import com.vmware.photon.controller.common.auth.AuthTokenHandler;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.FileAppender;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Main class for auth related tool.
 */
public class Main {
  private static final String AUTH_TOKEN_LOG_FILE_NAME = "out/AuthToken.log";
  private static final String CLIENT_REGISTRATION_LOG_FILE_NAME = "out/ClientRegistration.log";

  /**
   * Bootstrap the executor.
   * Usage: auth-tool [-h] {get-token,gt,register-client,rc} ...
   */
  public static void main(String[] args) {

    try {
      AuthToolCmdLine authToolCmdLine = AuthToolCmdLine.parseCmdLineArguments(args);

      switch (authToolCmdLine.getCommand()) {
        case GET_ACCESS_TOKEN: {
          initializeLogging(AUTH_TOKEN_LOG_FILE_NAME);

          OIDCTokens tokens = getAuthTokens(authToolCmdLine);
          printAccessToken(tokens.getAccessToken());

          break;
        }
        case GET_REFRESH_TOKEN: {
          initializeLogging(AUTH_TOKEN_LOG_FILE_NAME);

          OIDCTokens tokens = getAuthTokens(authToolCmdLine);
          printRefreshToken(tokens.getRefreshToken());

          break;
        }
        case REGISTER_CLIENT: {
          initializeLogging(CLIENT_REGISTRATION_LOG_FILE_NAME);

          ClientRegistrationArguments arguments = (ClientRegistrationArguments) authToolCmdLine.getArguments();

          AuthOIDCClient oidcClient = new AuthOIDCClient(arguments.getAuthServerAddress(),
              arguments.getAuthServerPort(), arguments.getTenant());
          AuthClientHandler authClientHandler = oidcClient.getClientHandler(arguments.getUsername(),
              arguments.getPassword());

          AuthClientHandler.ImplicitClient implicitClient = authClientHandler.registerImplicitClient(
              new URI(arguments.getLoginRedirectEndpoint()), new URI(arguments.getLogoutRedirectEndpoint()));

          printClientRegistrationInfo(implicitClient);

          break;
        }
        default:
          throw new RuntimeException("Not supported auth tool command.");
      }
    } catch (ArgumentParserException e) {
      System.err.println("Error: " + e.getMessage());
      System.err.println();
      AuthToolCmdLine.usage();
      System.exit(1);

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println(e.getCause().getMessage());
      }
      System.exit(1);
    }
  }


  /**
   * Print user access token.
   */
  static void printAccessToken(AccessToken token) throws Exception {
    System.out.println(token.serialize());
  }

  /**
   * Print refresh token.
   */
  static void printRefreshToken(RefreshToken token) {
    System.out.println(token.serialize());
  }

  /**
   * Print the information after a successful client registration.
   *
   * @param implicitClient
   */
  static void printClientRegistrationInfo(AuthClientHandler.ImplicitClient implicitClient) {
    System.out.printf("Client ID: %s\n\n", implicitClient.clientID);
    System.out.printf("Login URL: %s\n\n", implicitClient.loginURI);
    System.out.printf("Logout URL: %s\n", implicitClient.logoutURI);
  }

  /**
   * Initializes the Logging library.
   */
  static void initializeLogging(String logFileName) {
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.getLoggerContext().reset();

    FileAppender<ILoggingEvent> appender = new FileAppender<ILoggingEvent>();
    appender.setAppend(true);
    appender.setContext(root.getLoggerContext());
    appender.setFile(logFileName);
    appender.setPrudent(false);

    root.addAppender(appender);
  }

  /**
   * Get the auth tokens.
   *
   * @return The OIDC tokens, including ID Token, Access Token, Refresh Token.
   */
  static OIDCTokens getAuthTokens(AuthToolCmdLine authToolCmdLine) throws AuthException, URISyntaxException,
      ParseException {
    AuthTokenArguments arguments = (AuthTokenArguments) authToolCmdLine.getArguments();
    AuthToolCmdLine.Command command = authToolCmdLine.getCommand();

    AuthOIDCClient oidcClient = new AuthOIDCClient(arguments.getAuthServerAddress(),
        arguments.getAuthServerPort(), arguments.getTenant());
    AuthTokenHandler handler = oidcClient.getTokenHandler();

    switch (command) {
      case GET_REFRESH_TOKEN:
        return handler.getAuthTokensByPassword(arguments.getUsername(), arguments.getPassword());

      case GET_ACCESS_TOKEN:
        if (!StringUtils.isBlank(arguments.getRefreshToken())) {
          return handler.getAuthTokensByRefreshToken(RefreshToken.parse(arguments.getRefreshToken()));
        } else {
          return handler.getAuthTokensByPassword(arguments.getUsername(), arguments.getPassword());
        }

      default:
        throw new RuntimeException(String.format("Not supported command '%s'", command));
    }
  }
}
