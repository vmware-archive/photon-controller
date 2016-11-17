/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.common.auth;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class AuthOIDCRegistrar {

  private static final String PROGRAM_NAME = "lightwave-oidc-registrar";
  private static final String USERNAME_ARG = "username";
  private static final String PASSWORD_ARG = "password";
  private static final String TARGET_ARG = "target";
  private static final String MANAGEMENT_UI_REG_FILE_ARG = "mgmt_ui_reg_path";
  private static final String SWAGGER_UI_REG_FILE_ARG = "swagger_ui_reg_path";
  private static final String HELP_ARG = "help";

  private static final int ERROR_PARSE_EXCEPTION = 1;
  private static final int ERROR_USAGE_EXCEPTION = 2;
  private static final int ERROR_AUTH_EXCEPTION = 3;

  private static final String MGMT_UI_LOGIN_REDIRECT_URL_TEMPLATE = "https://%s:4343/oauth_callback.html";
  private static final String MGMT_UI_LOGOUT_REDIRECT_URL_TEMPLATE = "https://%s:4343/logout_callback";

  private static final String SWAGGER_UI_LOGIN_REDIRECT_URL_TEMPLATE = "https://%s/api/login-redirect.html";
  private static final String SWAGGER_UI_LOGOUT_REDIRECT_URL_TEMPLATE = "https://%s/api/login-redirect.html";

  private static final String MGMT_UI_REG_PATH_DEFAULT = "/etc/esxcloud/management_ui_auth_reg.json";
  private static final String SWAGGER_UI_REG_PATH_DEFAULT = "/etc/esxcloud/swagger_ui_auth_reg.json";

  private final DomainInfo _domainInfo;

  public AuthOIDCRegistrar(DomainInfo domainInfo)
  {
    _domainInfo = domainInfo;
  }

  public void
  register(
      String registrationAddress,
      String username,
      String password,
      String mgmt_ui_reg_path,
      String swagger_ui_reg_path
      ) throws AuthException
  {
    try {
      AuthOIDCClient client = new AuthOIDCClient(
                                      _domainInfo.getDomainController(),
                                      _domainInfo.getPort(),
                                      _domainInfo.getDomain()
                                      );
      AuthClientHandler handler = client.getClientHandler(username, password);

      String hostname = registrationAddress;

      if (hostname == null || hostname.isEmpty()) {
        hostname = InetAddress.getLocalHost().getCanonicalHostName();
      }

      AuthClientHandler.ImplicitClient managementUI = handler.registerImplicitClient(
                                                                    new URI(
                                                                        String.format(
                                                                            MGMT_UI_LOGIN_REDIRECT_URL_TEMPLATE,
                                                                            hostname
                                                                        )
                                                                    ),
                                                                    new URI(
                                                                        String.format(
                                                                            MGMT_UI_LOGOUT_REDIRECT_URL_TEMPLATE,
                                                                            hostname
                                                                        )
                                                                    )
                                                                );

      AuthClientHandler.ImplicitClient swaggerUI = handler.registerImplicitClient(
                                                                    new URI(
                                                                        String.format(
                                                                            SWAGGER_UI_LOGIN_REDIRECT_URL_TEMPLATE,
                                                                            hostname
                                                                        )
                                                                    ),
                                                                    new URI(
                                                                        String.format(
                                                                            SWAGGER_UI_LOGOUT_REDIRECT_URL_TEMPLATE,
                                                                            hostname
                                                                        )
                                                                    )
                                                                );

      if (mgmt_ui_reg_path == null || mgmt_ui_reg_path.isEmpty()) {
        mgmt_ui_reg_path = MGMT_UI_REG_PATH_DEFAULT;
      }

      writeToFile(managementUI, mgmt_ui_reg_path);

      if (swagger_ui_reg_path == null || swagger_ui_reg_path.isEmpty()) {
        swagger_ui_reg_path = SWAGGER_UI_REG_PATH_DEFAULT;
      }

      writeToFile(swaggerUI, swagger_ui_reg_path);
    }
    catch (UnknownHostException e)
    {
        throw new AuthException(e);
    }
    catch(URISyntaxException e)
    {
        throw new AuthException(e);
    }
    catch (IOException e)
    {
        throw new AuthException(e);
    }
  }

  private void writeToFile(AuthClientHandler.ImplicitClient client, String path)
          throws
          IOException
  {
    Map<String,Object> client_json = new HashMap<String,Object>();

    client_json.put("ClientID", client.clientID);
    client_json.put("LoginURI", client.loginURI);
    client_json.put("LogoutURI", client.logoutURI);

    ObjectMapper mapper = new ObjectMapper();

    mapper.writeValue(new File(path), client_json);
  }

  public static int main(String[] args)
  {
    Options options = new Options();
    options.addOption(USERNAME_ARG, true, "Lightwave user name");
    options.addOption(PASSWORD_ARG, true, "Password");
    options.addOption(TARGET_ARG, true, "Registration Hostname or IPAddress"); // Possible load-balancer address
    options.addOption(MANAGEMENT_UI_REG_FILE_ARG, true, "Management UI Registration Path");
    options.addOption(SWAGGER_UI_REG_FILE_ARG, true, "Swagger UI Registration Path");
    options.addOption(HELP_ARG, false, "Help");

    try
    {
      String username = null;
      String password = null;
      String registration_address = null;
      String mgmt_ui_reg_path = null;
      String swagger_ui_reg_path = null;

      CommandLineParser parser = new DefaultParser();
      CommandLine cmd = null;
      cmd = parser.parse(options, args);

      if (cmd.hasOption(HELP_ARG))
      {
        ShowUsage(options);
        return 0;
      }

      if (cmd.hasOption(USERNAME_ARG))
      {
        username = cmd.getOptionValue(USERNAME_ARG);
      }

      if (cmd.hasOption(PASSWORD_ARG))
      {
        password = cmd.getOptionValue(PASSWORD_ARG);
      }

      if (cmd.hasOption(TARGET_ARG))
      {
        registration_address = cmd.getOptionValue(TARGET_ARG);
      }

      if (cmd.hasOption(MANAGEMENT_UI_REG_FILE_ARG))
      {
        mgmt_ui_reg_path = cmd.getOptionValue(MANAGEMENT_UI_REG_FILE_ARG);
      }

      if (cmd.hasOption(SWAGGER_UI_REG_FILE_ARG))
      {
        swagger_ui_reg_path = cmd.getOptionValue(SWAGGER_UI_REG_FILE_ARG);
      }

      if (username == null || username.trim().isEmpty())
      {
        throw new UsageException("Error: username is not specified");
      }

      if (password == null)
      {
        char[] passwd = System.console().readPassword("Password:");
        password = new String(passwd);
      }

      DomainInfo domainInfo = DomainInfo.build();

      AuthOIDCRegistrar registrar = new AuthOIDCRegistrar(domainInfo);

      registrar.register(registration_address, username, password, mgmt_ui_reg_path, swagger_ui_reg_path);

      return 0;
    }
    catch( ParseException e)
    {
      System.err.println(e.getMessage());
      return ERROR_PARSE_EXCEPTION;
    }
    catch (UsageException e)
    {
      System.err.println(e.getMessage());
      ShowUsage(options);
      return ERROR_USAGE_EXCEPTION;
    }
    catch (AuthException e)
    {
      System.err.println(e.getMessage());
      return ERROR_AUTH_EXCEPTION;
    }
  }

  private static void ShowUsage(Options options)
  {
    HelpFormatter formatter = new HelpFormatter();

    formatter.printHelp(PROGRAM_NAME, options);
  }

  private static class UsageException extends Exception
  {
    public UsageException(String message) {
      super(message);
    }
  }
}
