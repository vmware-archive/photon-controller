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

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.powermock.api.mockito.PowerMockito.spy;

/**
 * Test AuthCertificateStoreTest.
 */
public class AuthCertificateStoreTest {
  AuthCertificateStore authCertificateStore;

  @BeforeClass
  public void setUp() throws AuthException {
    authCertificateStore = spy(new AuthCertificateStore());
  }

  @Test
  public void testGetKeyStore() {
    Assert.assertNotNull(authCertificateStore.getKeyStore());
  }
}
