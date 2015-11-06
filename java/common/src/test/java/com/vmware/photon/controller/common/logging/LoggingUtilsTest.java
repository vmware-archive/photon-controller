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

package com.vmware.photon.controller.common.logging;

import org.slf4j.MDC;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.util.UUID;

/**
 * Tests {@link LoggingUtils}.
 */
public class LoggingUtilsTest {

  private String requestId;

  /**
   * Dummy test case to make Intellij recognize this as a test class.
   */
  @Test
  private void dummy() {
  }

  /**
   * Tests the setRequestId method.
   */
  public class SetRequestIdTest {
    @BeforeMethod
    public void setUp() {
      requestId = UUID.randomUUID().toString();
    }

    /**
     * Test that MDC dictionary is populated correctly.
     */
    @Test
    public void testMDCHasExpectedValues() {
      LoggingUtils.setRequestId(requestId);

      assertThat(MDC.get(LoggingUtils.REQUEST_KEY), containsString(requestId));
      assertThat(MDC.get(LoggingUtils.REQUEST_ID_KEY), is(requestId));
    }
  }

  /**
   * Tests the getRequestId method.
   */
  public class GetRequestIdTest {
    @BeforeMethod
    public void setUp() {
      requestId = UUID.randomUUID().toString();

      // populate MDC
      MDC.put(LoggingUtils.REQUEST_ID_KEY, requestId);
    }

    /**
     * Test that we read the MDC values correctly.
     */
    @Test
    public void testItReadsExpectedMDCValues() {
      assertThat(LoggingUtils.getRequestId(), is(requestId));
    }

    /**
     * Test that we read null when MDC does not have requestId set.
     */
    @Test
    public void testItReadsNullIfMDCIsNotSet() {

      // Explicitly clear the requestId in MDC.
      MDC.clear();
      requestId = null;

      assertThat(LoggingUtils.getRequestId(), is(requestId));
    }
  }

  /**
   * Tests the formatRequestIdLogSection method.
   */
  public class FormatRequestIdTest {
    @BeforeMethod
    public void setUp() {
      requestId = UUID.randomUUID().toString();
    }

    /**
     * Test that we do log the RequestId.
     */
    @Test
    public void testFormattedStringContainsRequestId() {
      assertThat(
          LoggingUtils.formatRequestIdLogSection(requestId),
          containsString(requestId));
    }
  }
}
