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

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;
import net.sourceforge.argparse4j.inf.Subparsers;
import org.apache.commons.lang3.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Holds the sub-command of the auth tool and the passed-in arguments.
 */
public class AuthToolCmdLine {
  private static final String COMMAND_ATTR_NAME = "command";
  private static final String AUTH_SERVER_ADDRESS_ATTR_NAME = "authServerAddress";
  private static final String AUTH_SERVER_PORT_ATTR_NAME = "authServerPort";
  private static final String USERNAME_ATTR_NAME = "username";
  private static final String PASSWORD_ATTR_NAME = "password";
  private static final String TENANT_ATTR_NAME = "tenant";
  private static final String REFRESH_TOKEN_ATTR_NAME = "refreshToken";
  private static final String LOGIN_REDIRECT_ENDPOINT_ATTR_NAME = "loginRedirectEndpoint";
  private static final String LOGOUT_REDIRECT_ENDPOINT_ATTR_NAME = "logoutRedirectEndpoint";

  private Command command;
  private Object arguments;

  public static void usage() {
    buildArgumentParser().printHelp();
  }

  public static AuthToolCmdLine parseCmdLineArguments(String[] args) throws ArgumentParserException,
      MalformedURLException {

    ArgumentParser argumentParser = buildArgumentParser();

    Namespace res = argumentParser.parseArgs(args);
    Command command = (Command) res.get(COMMAND_ATTR_NAME);

    AuthToolCmdLine authToolCmdLine = new AuthToolCmdLine();
    authToolCmdLine.command = command;

    switch (command) {
      case GET_ACCESS_TOKEN: {
        authToolCmdLine.arguments = parseGetAccessTokenArguments(argumentParser, res);
        break;
      }
      case GET_REFRESH_TOKEN: {
        String authServerAddress = res.getString(AUTH_SERVER_ADDRESS_ATTR_NAME);
        int authServerPort = res.getInt(AUTH_SERVER_PORT_ATTR_NAME);
        String tenant = res.getString(TENANT_ATTR_NAME);
        String username = res.getString(USERNAME_ATTR_NAME);
        String password = res.getString(PASSWORD_ATTR_NAME);

        authToolCmdLine.arguments = new AuthTokenArguments(authServerAddress, authServerPort, tenant, username,
            password);

        break;
      }
      case REGISTER_CLIENT: {
        String authServerAddress = res.getString(AUTH_SERVER_ADDRESS_ATTR_NAME);
        int authServerPort = res.getInt(AUTH_SERVER_PORT_ATTR_NAME);
        String loginRedirectEndpoint = res.getString(LOGIN_REDIRECT_ENDPOINT_ATTR_NAME);
        String logoutRedirectEndpoint = res.getString(LOGOUT_REDIRECT_ENDPOINT_ATTR_NAME);
        String tenant = res.getString(TENANT_ATTR_NAME);
        String username = res.getString(USERNAME_ATTR_NAME);
        String password = res.getString(PASSWORD_ATTR_NAME);

        new URL(loginRedirectEndpoint);
        new URL(logoutRedirectEndpoint);

        authToolCmdLine.arguments = new ClientRegistrationArguments(authServerAddress, authServerPort, tenant,
            username, password, loginRedirectEndpoint, logoutRedirectEndpoint);

        break;
      }
      default:
        throw new RuntimeException("Not supported auth tool command.");
    }

    return authToolCmdLine;
  }

  private static ArgumentParser buildArgumentParser() {
    ArgumentParser parser = ArgumentParsers.newArgumentParser("auth-tool")
        .defaultHelp(true)
        .description("auth related tools");

    Subparsers subparsers = parser.addSubparsers().help("sub-command help");

    Subparser accessTokenParser = subparsers.addParser("get-access-token")
        .aliases("gat")
        .setDefault(COMMAND_ATTR_NAME, Command.GET_ACCESS_TOKEN)
        .help("Retrieves ESX Cloud authorization access token");
    setupAccessTokenParser(accessTokenParser);

    Subparser refreshTokenParser = subparsers.addParser("get-refresh-token")
        .aliases("grt")
        .setDefault(COMMAND_ATTR_NAME, Command.GET_REFRESH_TOKEN)
        .help("Retrieves ESX Cloud authorization refresh token");
    setupRefreshTokenParser(refreshTokenParser);

    Subparser registerClientParser = subparsers.addParser("register-client")
        .aliases("rc")
        .setDefault(COMMAND_ATTR_NAME, Command.REGISTER_CLIENT)
        .help("Registers Auth Client");
    setupRegisterClientParser(registerClientParser);

    return parser;
  }

  private static void setupAccessTokenParser(Subparser accessTokenParser) {
    accessTokenParser.addArgument("--username", "-u")
        .dest(USERNAME_ATTR_NAME)
        .required(false)
        .help("username of the user to authorize");
    accessTokenParser.addArgument("--password", "-p")
        .dest(PASSWORD_ATTR_NAME)
        .required(false)
        .help("password of the user to authorize");
    accessTokenParser.addArgument("--tenant", "-t")
        .dest(TENANT_ATTR_NAME)
        .required(false)
        .help("tenant name");
    accessTokenParser.addArgument("--refreshToken", "-r")
        .dest(REFRESH_TOKEN_ATTR_NAME)
        .required(false)
        .help("refresh token");
    accessTokenParser.addArgument("--authServerAddress", "-a")
        .dest(AUTH_SERVER_ADDRESS_ATTR_NAME)
        .required(true)
        .help("Auth server address");
    accessTokenParser.addArgument("--authServerPort", "-n")
        .dest(AUTH_SERVER_PORT_ATTR_NAME)
        .required(false)
        .type(Integer.class)
        .setDefault(443)
        .help("Auth server port");
  }

  private static void setupRefreshTokenParser(Subparser refreshTokenParser) {
    refreshTokenParser.addArgument("--username", "-u")
        .dest(USERNAME_ATTR_NAME)
        .required(true)
        .help("username of the user to authorize");
    refreshTokenParser.addArgument("--password", "-p")
        .dest(PASSWORD_ATTR_NAME)
        .required(true)
        .help("password of the user to authorize");
    refreshTokenParser.addArgument("--tenant", "-t")
        .dest(TENANT_ATTR_NAME)
        .required(false)
        .help("tenant name");
    refreshTokenParser.addArgument("--authServerAddress", "-a")
        .dest(AUTH_SERVER_ADDRESS_ATTR_NAME)
        .required(true)
        .help("Auth server address");
    refreshTokenParser.addArgument("--authServerPort", "-n")
        .dest(AUTH_SERVER_PORT_ATTR_NAME)
        .required(false)
        .type(Integer.class)
        .setDefault(443)
        .help("Auth server port");
  }

  private static void setupRegisterClientParser(Subparser registerClientParser) {
    registerClientParser.addArgument("--tenant", "-t")
        .dest(TENANT_ATTR_NAME)
        .required(true)
        .help("tenant name");
    registerClientParser.addArgument("--username", "-u")
        .dest(USERNAME_ATTR_NAME)
        .required(true)
        .help("username of the user to authorize");
    registerClientParser.addArgument("--password", "-p")
        .dest(PASSWORD_ATTR_NAME)
        .required(true)
        .help("password of the user to authorize");
    registerClientParser.addArgument("--authServerAddress", "-a")
        .dest(AUTH_SERVER_ADDRESS_ATTR_NAME)
        .required(true)
        .help("Auth server address");
    registerClientParser.addArgument("--authServerPort", "-n")
        .dest(AUTH_SERVER_PORT_ATTR_NAME)
        .required(false)
        .type(Integer.class)
        .setDefault(443)
        .help("Auth server port");
    registerClientParser.addArgument(String.format("--%s", LOGIN_REDIRECT_ENDPOINT_ATTR_NAME), "-r")
        .required(true)
        .help("Endpoint to redirect after login");
    registerClientParser.addArgument(String.format("--%s", LOGOUT_REDIRECT_ENDPOINT_ATTR_NAME), "-o")
        .required(true)
        .help("Endpoint to redirect after logout");
  }

  private static AuthTokenArguments parseGetAccessTokenArguments(ArgumentParser argumentParser,
                                            Namespace res) throws ArgumentParserException, MalformedURLException {

    String authServerAddress = res.getString(AUTH_SERVER_ADDRESS_ATTR_NAME);
    int authServerPort = res.getInt(AUTH_SERVER_PORT_ATTR_NAME);

    String username = res.getString(USERNAME_ATTR_NAME);
    String password = res.getString(PASSWORD_ATTR_NAME);
    String tenant = res.getString(TENANT_ATTR_NAME);
    String refreshToken = res.getString(REFRESH_TOKEN_ATTR_NAME);

    if (StringUtils.isBlank(username) && StringUtils.isBlank(password) && StringUtils.isBlank(refreshToken)) {
      throw new ArgumentParserException("Either --username/-u/--password/-p/ or --refreshToken/-r must be provided" +
          " in order to get access token.", argumentParser);
    } else if ((!StringUtils.isBlank(username) || !StringUtils.isBlank(password))
        && !StringUtils.isBlank(refreshToken)) {
      throw new ArgumentParserException("Either --username/-u/--password/-p/ or --refreshToken/-, but not both",
          argumentParser);
    } else if (StringUtils.isBlank(username) && !StringUtils.isBlank(password) && StringUtils.isBlank(refreshToken)) {
      throw new ArgumentParserException("argument --username/-u is required", argumentParser);
    } else if (!StringUtils.isBlank(username) && StringUtils.isBlank(password) && StringUtils.isBlank(refreshToken)) {
      throw new ArgumentParserException("argument --password/-p is required", argumentParser);
    } else if (StringUtils.isBlank(username) && StringUtils.isBlank(password) && !StringUtils.isBlank(refreshToken)) {
      return new AuthTokenArguments(authServerAddress, authServerPort, tenant, refreshToken);
    } else {
      return new AuthTokenArguments(authServerAddress, authServerPort, tenant, username, password);
    }
  }

  public Command getCommand() {
    return command;
  }

  public Object getArguments() {
    return arguments;
  }

  /**
   * Supported auth-tool commands.
   */
  public enum Command {
    GET_ACCESS_TOKEN,
    GET_REFRESH_TOKEN,
    REGISTER_CLIENT
  }
}
