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

package com.vmware.photon.controller.common.xenon;

import com.vmware.photon.controller.common.thrift.StaticServerSet;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.net.InetSocketAddress;
import java.net.URI;

/**
 * This class implements tests for the {@link CloudStoreHelper} class.
 */
public class CloudStoreHelperTest {

  private static final String TEST_URI_PATH = "/path";

  /**
   * This dummy test enables IntelliJ to recognize this class as a test class.
   */
  @Test(enabled = false)
  public void dummy() {
  }

  /**
   * This class implements tests for the constructor.
   */
  public class ConstructorTest {

    @Test
    public void testConstructor() {
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper();
      assertThat(cloudStoreHelper.getCloudStoreServerSet(), nullValue());
      assertThat(cloudStoreHelper.getRefererUri(), is(OperationUtils.getLocalHostUri()));
    }

    @Test
    public void testConstructorWithServerSet() {
      InetSocketAddress inetSocketAddress = new InetSocketAddress(80);
      StaticServerSet serverSet = new StaticServerSet(inetSocketAddress);
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(serverSet);
      assertThat(cloudStoreHelper.getCloudStoreServerSet(), is(serverSet));
      assertThat(cloudStoreHelper.getRefererUri(), is(OperationUtils.getLocalHostUri()));
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testConstructorWithNullServerSet() {
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(null);
    }
  }

  /**
   * This class implements tests for the getCloudStoreURI method.
   */
  public class GetCloudStoreURITest {

    @Test
    public void testGetCloudStoreURI() throws Throwable {
      InetSocketAddress inetSocketAddress = new InetSocketAddress(80);
      StaticServerSet serverSet = new StaticServerSet(inetSocketAddress);
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(serverSet);

      String hostString = inetSocketAddress.getHostString();
      int port = inetSocketAddress.getPort();
      URI expectedUri = new URI("http", null, hostString, port, TEST_URI_PATH, null, null);

      assertThat(cloudStoreHelper.getCloudStoreURI(TEST_URI_PATH), is(expectedUri));
    }
  }

  /**
   * This class implements tests for the setRefererUri method.
   */
  public class SetRefererURITest {

    @Test
    public void testSetRefererURI() throws Throwable {
      InetSocketAddress inetSocketAddress = new InetSocketAddress(80);
      StaticServerSet serverSet = new StaticServerSet(inetSocketAddress);
      CloudStoreHelper cloudStoreHelper = new CloudStoreHelper(serverSet);

      String hostString = inetSocketAddress.getHostString();
      int port = inetSocketAddress.getPort();
      URI refererUri = new URI("http", null, hostString, port, TEST_URI_PATH, null, null);
      cloudStoreHelper.setRefererUri(refererUri);

      assertThat(cloudStoreHelper.getRefererUri(), is(refererUri));
    }
  }

  /**
   * This class implements tests for the various methods which create {@link Operation} objects.
   */
  public class OperationTest {

    private CloudStoreHelper cloudStoreHelper;
    private URI expectedUri;

    @BeforeClass
    public void setUpClass() throws Throwable {
      InetSocketAddress inetSocketAddress = new InetSocketAddress(80);
      StaticServerSet serverSet = new StaticServerSet(inetSocketAddress);
      cloudStoreHelper = new CloudStoreHelper(serverSet);

      String hostString = inetSocketAddress.getHostString();
      int port = inetSocketAddress.getPort();
      expectedUri = new URI("http", null, hostString, port, TEST_URI_PATH, null, null);
    }

    @Test
    public void testCreateDelete() {
      Operation deleteOp = cloudStoreHelper.createDelete(TEST_URI_PATH);
      assertThat(deleteOp.getAction(), is(Service.Action.DELETE));
      assertThat(deleteOp.getUri(), is(expectedUri));
    }

    @Test
    public void testCreateGet() {
      Operation getOp = cloudStoreHelper.createGet(TEST_URI_PATH);
      assertThat(getOp.getAction(), is(Service.Action.GET));
      assertThat(getOp.getUri(), is(expectedUri));
    }

    @Test
    public void testCreatePost() {
      Operation postOp = cloudStoreHelper.createPost(TEST_URI_PATH);
      assertThat(postOp.getAction(), is(Service.Action.POST));
      assertThat(postOp.getUri(), is(expectedUri));
    }

    @Test
    public void testCreatePatch() {
      Operation patchOp = cloudStoreHelper.createPatch(TEST_URI_PATH);
      assertThat(patchOp.getAction(), is(Service.Action.PATCH));
      assertThat(patchOp.getUri(), is(expectedUri));
    }
  }
}
