/*
 * Copyright 2016 VMware, Inc. All Rights Reserved.
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

package com.vmware.photon.controller.common.xenon.serializer;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.util.BitSet;

/**
 * Tests {@link com.vmware.photon.controller.common.xenon.serializer.KryoSerializerCustomization}.
 */
public class KryoSerializerCustomizationTest {

  private TestHost host;

  @BeforeClass
  public void setUp() throws Throwable {
    host = new TestHost();
    host.start();
  }

  @AfterClass
  public void tearDown() throws Throwable {
    if (host != null) {
      host.destroy();
    }
  }

  @Test
  public void testSuccessfulSerialization() throws Throwable {
    TestService.State startState = new TestService.State();
    startState.bitSet = null;

    // Tests that the initial document gets serialized.
    TestService.State finalState = host.startServiceSynchronously(
        new TestService(),
        startState).getBody(TestService.State.class);

    assertThat(finalState.bitSet, is(notNullValue()));
    assertThat(finalState.bitSet.get(0), is(true));
    assertThat(finalState.bitSet.get(1), is(true));
    assertThat(finalState.bitSet.get(4), is(true));
    assertThat(finalState.bitSet.get(7), is(true));
    assertThat(finalState.bitSet.get(2), is(false));
    assertThat(finalState.bitSet.get(3), is(false));
    assertThat(finalState.bitSet.get(5), is(false));
    assertThat(finalState.bitSet.get(6), is(false));

    // Tests that the patched document gets serialized.
    TestService.State patchState = new TestService.State();
    patchState.bitSet = new BitSet();
    patchState.bitSet.set(2, true);
    patchState.bitSet.set(3, true);
    patchState.bitSet.set(5, true);
    patchState.bitSet.set(6, true);

    finalState = host.sendRequestAndWait(
        Operation.createPatch(UriUtils.buildUri(host, finalState.documentSelfLink)).setBody(patchState))
          .getBody(TestService.State.class);

    assertThat(finalState.bitSet, is(notNullValue()));
    assertThat(finalState.bitSet.get(0), is(false));
    assertThat(finalState.bitSet.get(1), is(false));
    assertThat(finalState.bitSet.get(4), is(false));
    assertThat(finalState.bitSet.get(7), is(false));
    assertThat(finalState.bitSet.get(2), is(true));
    assertThat(finalState.bitSet.get(3), is(true));
    assertThat(finalState.bitSet.get(5), is(true));
    assertThat(finalState.bitSet.get(6), is(true));
  }

  /**
   * A test host that starts the test service.
   */
  public static class TestHost extends BasicServiceHost {

    @Override
    public ServiceHost start() throws Throwable {
      super.initialize();
      super.start();

      this.startWithCoreServices();

      KryoSerializerCustomization kryoSerializerCustomization = new KryoSerializerCustomization();
      Utils.registerCustomKryoSerializer(kryoSerializerCustomization, true);
      Utils.registerCustomKryoSerializer(kryoSerializerCustomization, false);

      return this;
    }
  }

  /**
   * A test service that has a service document that contains complex data types.
   */
  public static class TestService extends StatefulService {

    public TestService() {
      super(State.class);
      super.toggleOption(ServiceOption.PERSISTENCE, true);
      super.toggleOption(ServiceOption.REPLICATION, true);
      super.toggleOption(ServiceOption.OWNER_SELECTION, true);
      super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    @Override
    public void handleStart(Operation op) {
      State startState = op.getBody(State.class);
      startState.bitSet = new BitSet(Integer.SIZE);
      startState.bitSet.set(0, true);
      startState.bitSet.set(1, true);
      startState.bitSet.set(4, true);
      startState.bitSet.set(7, true);

      op.complete();
    }

    @Override
    public void handlePatch(Operation op) {
      State startState = getState(op);
      State patchState = op.getBody(State.class);

      startState.bitSet = patchState.bitSet;
      op.complete();
    }

    /**
     * A test service document that contains complex data types.
     */
    public static class State extends ServiceDocument {
      public BitSet bitSet;
    }
  }
}
