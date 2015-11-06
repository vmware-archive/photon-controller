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

import net.sourceforge.argparse4j.inf.ArgumentParserException;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.MalformedURLException;

/**
 * Test {@link AuthToolCmdLine}.
 */
public class AuthToolCmdLineTest {
  @Test(expectedExceptions = ArgumentParserException.class, expectedExceptionsMessageRegExp = "^too few arguments$")
  public void failOnNoArguments() throws Exception {
    AuthToolCmdLine.parseCmdLineArguments(new String[0]);
  }

  /**
   * Tests for parsing the arguments of get-access-token with password command.
   */
  public class ParseGetAccessTokenByPasswordCommandTests {
    @Test
    public void getArgumentsSuccess() throws Exception {
      AuthToolCmdLine authToolCmdLine = AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD});

      AuthToolCmdLine.Command command = authToolCmdLine.getCommand();
      AuthTokenArguments arguments = (AuthTokenArguments) authToolCmdLine.getArguments();

      assertThat(command, is(AuthToolCmdLine.Command.GET_ACCESS_TOKEN));
      assertThat(arguments.getUsername(), is(MainTest.USER));
      assertThat(arguments.getPassword(), is(MainTest.PASSWORD));
      assertThat(arguments.getAuthServerAddress(), is(MainTest.AUTH_SERVER_ADDRESS));
      assertThat(arguments.getRefreshToken(), nullValue());
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --authServerAddress/-a is required$")
    public void getArgumentsWithMissingLookupService() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"get-access-token", "-u", MainTest.USER, "-p",
          MainTest.PASSWORD});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --username/-u is required$")
    public void getArgumentsWithMissingUsername() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-p", MainTest.PASSWORD});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --password/-p is required$")
    public void getArgumentsWithMissingPassword() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^unrecognized arguments: '-x'$")
    public void getArgumentsWithUnrecognizedOption() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p",
              MainTest.PASSWORD, "-x"});
    }
  }

  /**
   * Tests for parsing the arguments of get-access-token with refresh token command.
   */
  public class ParseGetAccessTokenByRefreshCommandTests {
    @Test
    public void getArgumentsSuccess() throws Exception {
      AuthToolCmdLine authToolCmdLine = AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-r", MainTest.REFRESH_TOKEN});

      AuthToolCmdLine.Command command = authToolCmdLine.getCommand();
      AuthTokenArguments arguments = (AuthTokenArguments) authToolCmdLine.getArguments();

      assertThat(command, is(AuthToolCmdLine.Command.GET_ACCESS_TOKEN));
      assertThat(arguments.getAuthServerAddress(), is(MainTest.AUTH_SERVER_ADDRESS));
      assertThat(arguments.getRefreshToken(), is(MainTest.REFRESH_TOKEN));
      assertThat(arguments.getUsername(), nullValue());
      assertThat(arguments.getPassword(), nullValue());
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --authServerAddress/-a is required$")
    public void getArgumentsWithMissingLookupService() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"get-access-token", "-r", MainTest.REFRESH_TOKEN});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^unrecognized arguments: '-x'$")
    public void getArgumentsWithUnrecognizedOption() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-r", MainTest.REFRESH_TOKEN, "-x"});
    }
  }

  /**
   * Tests for the two mutually exclusive groups when parsing access token
   * (by username/password and by refresh token).
   */
  public class ParseGetAccessTokenGroupsTests {

    @Test(expectedExceptions = ArgumentParserException.class, expectedExceptionsMessageRegExp = "^Either " +
        "--username/-u/--password/-p/ or --refreshToken/-r must be provided in order to get access token.$")
    public void testNeitherGroupWasGiven() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[] {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS});
    }

    @Test(expectedExceptions = ArgumentParserException.class, expectedExceptionsMessageRegExp = "^Either " +
        "--username/-u/--password/-p/ or --refreshToken/-, but not both$")
    public void testBothGroupsWereGiven() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[] {"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS,
          "-u", MainTest.USER, "-r", MainTest.REFRESH_TOKEN});
    }
  }

  /**
   * Tests for parsing the arguments of get-refresh-token command.
   */
  public class ParseGetRefreshTokenCommandTests {
    @Test
    public void getArgumentsSuccess() throws Exception {
      AuthToolCmdLine authToolCmdLine = AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-refresh-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD});

      AuthToolCmdLine.Command command = authToolCmdLine.getCommand();
      AuthTokenArguments arguments = (AuthTokenArguments) authToolCmdLine.getArguments();

      assertThat(command, is(AuthToolCmdLine.Command.GET_REFRESH_TOKEN));
      assertThat(arguments.getUsername(), is(MainTest.USER));
      assertThat(arguments.getPassword(), is(MainTest.PASSWORD));
      assertThat(arguments.getAuthServerAddress(), is(MainTest.AUTH_SERVER_ADDRESS));
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --authServerAddress/-a is required$")
    public void getArgumentsWithMissingLookupService() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"get-refresh-token", "-u", MainTest.USER, "-p",
          MainTest.PASSWORD});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --username/-u is required$")
    public void getArgumentsWithMissingUsername() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-refresh-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-p", MainTest.PASSWORD});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --password/-p is required$")
    public void getArgumentsWithMissingPassword() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-refresh-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^unrecognized arguments: '-x'$")
    public void getArgumentsWithUnrecognizedOption() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"get-refresh-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u",
              MainTest.USER, "-p", MainTest.PASSWORD, "-x"});
    }
  }

  /**
   * Tests for parsing the arguments of register-client command.
   */
  public class ParseRegisterClientCommandTests {
    @Test
    public void getArgumentsSuccess() throws Exception {
      AuthToolCmdLine authToolCmdLine = AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD, "-t",
              MainTest.TENANT, "-r", MainTest.LOGIN_REDIRECT_URL, "-o", MainTest.LOGOUT_REDIRECT_URL});

      AuthToolCmdLine.Command command = authToolCmdLine.getCommand();
      ClientRegistrationArguments arguments = (ClientRegistrationArguments) authToolCmdLine.getArguments();

      assertThat(command, is(AuthToolCmdLine.Command.REGISTER_CLIENT));
      assertThat(arguments.getUsername(), is(MainTest.USER));
      assertThat(arguments.getPassword(), is(MainTest.PASSWORD));
      assertThat(arguments.getTenant(), is(MainTest.TENANT));
      assertThat(arguments.getAuthServerAddress(), is(MainTest.AUTH_SERVER_ADDRESS));
      assertThat(arguments.getLoginRedirectEndpoint(), is(MainTest.LOGIN_REDIRECT_URL));
      assertThat(arguments.getLogoutRedirectEndpoint(), is(MainTest.LOGOUT_REDIRECT_URL));
    }

    @Test(expectedExceptions = MalformedURLException.class, expectedExceptionsMessageRegExp = "^no protocol: foo$")
    public void getArgumentsWithBadUrl() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD, "-t",
              MainTest.TENANT, "-r", "foo", "-o", "bar"});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --authServerAddress/-a is required$")
    public void getArgumentsWithMissingLookupservice() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"rc", "-u", MainTest.USER, "-p", MainTest.PASSWORD, "-t", MainTest.TENANT,
              "-r", MainTest.LOGIN_REDIRECT_URL, "-o", MainTest.LOGOUT_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --loginRedirectEndpoint/-r is required$")
    public void getArgumentsWithMissingLoginRedirectUrl() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD,
              "-t", MainTest.TENANT, "-o", MainTest.LOGOUT_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --logoutRedirectEndpoint/-o is required$")
    public void getArgumentsWithMissingLogoutRedirectUrl() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]
          {"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p", MainTest.PASSWORD,
              "-t", MainTest.TENANT, "-r", MainTest.LOGIN_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --username/-u is required$")
    public void getArgumentsWithMissingUsername() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-p",
          MainTest.PASSWORD, "-t", MainTest.TENANT,
          "-r", MainTest.LOGIN_REDIRECT_URL, "-o", MainTest.LOGOUT_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --password/-p is required$")
    public void getArgumentsWithMissingPassword() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u",
          MainTest.USER, "-t", MainTest.TENANT, "-r", MainTest.LOGIN_REDIRECT_URL, "-o", MainTest.LOGOUT_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^argument --tenant/-t is required$")
    public void getArgumentsWithMissingTenant() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u",
          MainTest.USER, "-p", MainTest.PASSWORD, "-r", MainTest.LOGIN_REDIRECT_URL,
          "-o", MainTest.LOGOUT_REDIRECT_URL});
    }

    @Test(expectedExceptions = ArgumentParserException.class,
        expectedExceptionsMessageRegExp = "^unrecognized arguments: '-x'$")
    public void getArgumentsWithUnrecognizedOption() throws Exception {
      AuthToolCmdLine.parseCmdLineArguments(new String[]{"rc", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u",
          MainTest.USER, "-p", MainTest.PASSWORD, "-t", MainTest.TENANT,
          "-r", MainTest.LOGIN_REDIRECT_URL, "-o", MainTest.LOGOUT_REDIRECT_URL, "-x"});
    }
  }
}
