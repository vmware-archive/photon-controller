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

package com.vmware.photon.controller.apife.exceptions;

import com.vmware.photon.controller.apife.exceptions.external.ExternalException;
import static com.vmware.photon.controller.apife.exceptions.external.ErrorCode.INTERNAL_ERROR;

import com.google.common.collect.ImmutableMap;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import javax.ws.rs.core.Response;

/**
 * Tests {@link ExternalException}.
 */
public class ExternalExceptionTest extends PowerMockTestCase {

  @Test
  public void testCreateException() throws Exception {
    ImmutableMap<String, String> extraData = ImmutableMap.of("foo", "bar");
    ExternalException e = new ExternalException(INTERNAL_ERROR, "foo", extraData);

    assertThat(e.getData().get("foo"), is("bar"));
    assertThat(e.getErrorCode(), is(INTERNAL_ERROR.getCode()));
    assertThat(e.getHttpStatus(), is(INTERNAL_ERROR.getHttpStatus()));
    assertThat(e.getMessage(), is("foo"));
  }

  @Test
  public void testCreateWithMessage() throws Exception {
    ExternalException e = new ExternalException("foo");

    assertThat(e.getData().isEmpty(), is(true));
    assertThat(e.getErrorCode(), is(INTERNAL_ERROR.getCode()));
    assertThat(e.getHttpStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    assertThat(e.getMessage(), is("foo"));
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Test
  public void testLaunder() throws Exception {
    ExternalException e1 = new ExternalException("foo");
    assertThat(ExternalException.launder(e1), is(e1));

    RuntimeException e2 = new RuntimeException("bar");

    ExternalException e3 = ExternalException.launder(e2);
    assertThat(e3.getErrorCode(), is(INTERNAL_ERROR.getCode()));
    assertThat(e3.getHttpStatus(), is(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode()));
    assertThat(e3.getMessage(), is("Please contact the system administrator about request #null"));
    assertThat(e3.getData().isEmpty(), is(true));
  }
}
