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

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

/**
 * Test {@link AuthTokenArguments}.
 */
public class AuthTokenArgumentsTest {

  /**
   * Tests to verify the getter methods in using password case.
   */
  @Test
  public void testGettersForUsingPassword() throws Exception {
    AuthTokenArguments args = (AuthTokenArguments) AuthToolCmdLine.parseCmdLineArguments(
        new String[]{"get-access-token", "-a", MainTest.AUTH_SERVER_ADDRESS, "-u", MainTest.USER, "-p",
            MainTest.PASSWORD}).getArguments();

    assertEquals(args.getAuthServerAddress(), MainTest.AUTH_SERVER_ADDRESS);
    assertEquals(args.getUsername(), MainTest.USER);
    assertEquals(args.getPassword(), MainTest.PASSWORD);
    assertNull(args.getRefreshToken());
  }

  /**
   * Tests to verify the getter methods in using refresh token case.
   *
   * @throws Exception
   */
  @Test
  public void testGettersForUsingRefereshToken() throws Exception {
    AuthTokenArguments args = (AuthTokenArguments) AuthToolCmdLine.parseCmdLineArguments(
        new String[]{"get-access-token", "-r", MainTest.REFRESH_TOKEN, "-a",
            MainTest.AUTH_SERVER_ADDRESS}).getArguments();

    assertEquals(args.getAuthServerAddress(), MainTest.AUTH_SERVER_ADDRESS);
    assertEquals(args.getRefreshToken(), MainTest.REFRESH_TOKEN);
    assertNull(args.getUsername());
    assertNull(args.getPassword());
  }
}
