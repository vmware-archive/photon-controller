/*
 * Copyright (c) 2016 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.common.xenon.serializer;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.ServiceHostUtils;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.BitSet;
import java.util.Random;
import java.util.function.Supplier;

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
  public void test() throws Throwable {
    TestService.State state = new TestService.State();
    state.len = 12;

    TestService.State finalState = host.startServiceSynchronously(
        new TestService(),
        state).getBody(TestService.State.class);
  }

  public static class TestHost extends BasicServiceHost {

    @Override
    public ServiceHost start() throws Throwable {
      super.initialize();
      super.start();

      KryoSerializerCustomization kryoSerializerCustomization = new KryoSerializerCustomization();
      Utils.registerCustomKryoSerializer(kryoSerializerCustomization, true);
      Utils.registerCustomKryoSerializer(kryoSerializerCustomization, false);


      ServiceHostUtils.startFactoryServices(
          this, ImmutableMap.<Class<? extends Service>, Supplier<FactoryService>>builder().put(TestService.class, TestService::createFactory).build());

      return this;
    }

    @Override
    public Operation sendRequestAndWait(Operation op) throws Throwable {
      Operation operation = super.sendRequestAndWait(op);
      // For tests we check status code 200 to see if the response is OK
      // If nothing is changed in patch, it returns 304 which means not modified.
      // We will treat 304 as 200
      if (operation.getStatusCode() == 304) {
        operation.setStatusCode(200);
      }
      return operation;
    }
  }

  public static class TestService extends StatefulService {

    public static final String FACTORY_LINK = "/my-test-service";

    public TestService() {
      super(State.class);
      super.toggleOption(ServiceOption.PERSISTENCE, true);
      super.toggleOption(ServiceOption.REPLICATION, true);
      super.toggleOption(ServiceOption.OWNER_SELECTION, true);
      super.toggleOption(ServiceOption.INSTRUMENTATION, true);
    }

    public static FactoryService createFactory() {
      return FactoryService.create(
          TestService.class,
          TestService.State.class,
          ServiceOption.IDEMPOTENT_POST);
    }

    @Override
    public void handleCreate(Operation op) {
      State startState = op.getBody(State.class);
      startState.bitSet = new BitSet(startState.len);

      Random rand = new Random();
      for (int j = 0; j < startState.len; ++j) {
        startState.bitSet.set(j, rand.nextBoolean());
      }

      setState(op, startState);
      op.complete();
    }

    public static class State extends ServiceDocument {
      public BitSet bitSet;

      public int len;
    }
  }
}
