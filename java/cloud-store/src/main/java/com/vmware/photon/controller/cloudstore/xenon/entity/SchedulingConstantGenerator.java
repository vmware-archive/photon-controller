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
package com.vmware.photon.controller.cloudstore.xenon.entity;

import com.vmware.photon.controller.common.xenon.BasicServiceHost;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.deployment.NoMigrationDuringDeployment;
import com.vmware.photon.controller.common.xenon.migration.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.DefaultLong;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Range;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import org.apache.commons.math3.random.HaltonSequenceGenerator;

import java.util.logging.Level;

/**
 * Xenon microservice that generates scheduling constants for hosts.
 *
 * This is intended to be used as a singleton service. An instance of this
 * service tracks the state that generates a sequence of scheduling constants.
 *
 * See comments in CloudStoreConstraintChecker for a description of the
 * scheduling algorithm.
 *
 * The evenness of the scheduling algorithm depends on how evenly-spaced the
 * scheduling constants are when they are initially assigned to hosts. Choosing
 * scheduling constants randomly tends to produce unevenly-spaced constants.
 * This service is used to assign more evenly-spaced constants to hosts.
 *
 * Currently, this implementation uses a Halton sequence to generate scheduling
 * constants that are roughly evenly spaced.
 *
 * @see <a href="https://en.wikipedia.org/wiki/Halton_sequence">Halton
 * sequence</a>.
 */
public class SchedulingConstantGenerator extends StatefulService {

  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/sched-const-generators";
  public static final String SINGLETON_LINK = FACTORY_LINK + "/default";

  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(SchedulingConstantGenerator.class);
  }

  /**
   * Creates a CompletionHandler that starts a single
   * SchedulingConstantGenerator.
   *
   * Call ServiceHost.registerForServiceAvailability with the result of this
   * function in order to start the singleton instance of
   * SchedulingConstantGenerator.
   */
  public static Operation.CompletionHandler startSingletonService(ServiceHost host) {
    return (o, e) -> {
      if (e != null) {
        host.log(Level.SEVERE, Utils.toString(e));
        return;
      }

      Operation postOperation = createPostToStartSingleton(host);
      host.sendRequest(postOperation);
    };
  }

  /**
   * Creates a POST request that creates a singleton SchedulingConstantGenerator.
   *
   * Does not send the request.
   */
  public static Operation createPostToStartSingleton(ServiceHost host) {
    SchedulingConstantGenerator.State newState = new SchedulingConstantGenerator.State();
    newState.documentSelfLink = SchedulingConstantGenerator.SINGLETON_LINK;

    Operation postOperation = Operation
        .createPost(host, SchedulingConstantGenerator.FACTORY_LINK)
        .setBody(newState)
        .setReferer(ServiceUriPaths.CLOUDSTORE_ROOT)
        .setCompletion((o, e) -> {
          if (e != null) {
            host.log(Level.SEVERE, Utils.toString(e));
            return;
          }
          host.log(Level.INFO, "Singleton SchedulingConstantGenerator service started");
        });

    return postOperation;
  }

  /**
   * Synchronously create the SchedulingConstantGenerator factory and singleton
   * for unit tests.
   *
   * Starts the SchedulingConstantGenerator factory service and the singleton
   * instance, synchronously. This is provided as a simple way to initialize the
   * singleton instance for unit tests. Do not use this method outside of unit
   * tests.
   *
   * This method uses PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE so that the singleton
   * service can be restarted after being deleted, which can happen in unit test
   * cleanup.
   */
  public static void startSingletonServiceForTest(BasicServiceHost host) throws Throwable {
    host.startFactoryServiceSynchronously(createFactory(), FACTORY_LINK);

    SchedulingConstantGenerator.State newState = new SchedulingConstantGenerator.State();
    newState.documentSelfLink = SINGLETON_LINK;

    Operation postOperation = Operation
        .createPost(host, FACTORY_LINK)
        .setBody(newState)
        .setReferer(ServiceUriPaths.CLOUDSTORE_ROOT)
        .addPragmaDirective(Operation.PRAGMA_DIRECTIVE_FORCE_INDEX_UPDATE)
        .setCompletion((o, e) -> {
          if (e != null) {
            host.log(Level.SEVERE, Utils.toString(e));
            return;
          }
          host.log(Level.INFO, "Singleton SchedulingConstantGenerator service started");
        });

    host.sendRequestAndWait(postOperation);
  }

  public SchedulingConstantGenerator() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());

    if (!ServiceHost.isServiceCreate(startOperation)) {
      startOperation.complete();
      return;
    }

    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
      ValidationUtils.validateState(startState);
      nextValue(startState);
      startOperation.complete();
      ServiceUtils.logInfo(this, "Started service %s", getSelfLink());
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.logInfo(this, "Deleting service %s", getSelfLink());
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    ServiceUtils.logInfo(this, "Handling PATCH to service %s", getSelfLink());
    // The body of the patch is ignored. Generate the next value of the
    // sequence, store it, and return it in the output so that the client that
    // invoked PATCH can use the result.
    try {
      State state = getState(patchOperation);
      nextValue(state);
      patchOperation.setBody(state);
      patchOperation.complete();
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handlePut(Operation putOperation) {
    if (putOperation.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
      // Converted POST to PUT due to IDEMPOTENT_POST; ignore
      logInfo("SchedulingConstantGenerator is alredy started; ignoring converted POST");
      putOperation.complete();
      return;
    }

    // Normal PUT is not supported
    putOperation.fail(Operation.STATUS_CODE_BAD_METHOD);
  }

  private void nextValue(State state) {
    if (state == null) {
      throw new RuntimeException("state == null");
    }
    if (state.nextHaltonSequenceIndex == null) {
      throw new RuntimeException("state.nextHaltonSequenceIndex == null");
    }

    HaltonSequenceGenerator hsg = new HaltonSequenceGenerator(1);
    if (hsg == null) {
      throw new RuntimeException("hsg == null");
    }
    double[] nextValues = hsg.skipTo(state.nextHaltonSequenceIndex);

    state.nextHaltonSequenceIndex++;
    state.lastSchedulingConstant = (long) (nextValues[0] * HostService.MAX_SCHEDULING_CONSTANT);
  }

  /**
   * State associated with a SchedulingConstantGenerator.
   *
   * The members of this class depend on the specific algorithm used to
   * implement the scheduling constant generator.
   */
  @NoMigrationDuringDeployment
  @NoMigrationDuringUpgrade
  public static class State extends ServiceDocument {

    /**
     * Index of the next value to be emitted from the Halton sequence.
     */
    @NotNull
    @Range(min = 0, max = Integer.MAX_VALUE)
    @DefaultInteger(0)
    public Integer nextHaltonSequenceIndex;

    /**
     * Last scheduling constant returned from the sequence.
     *
     * This is the value of the Halton sequence at [nextHaltonSequenceIndex-1]
     * multiplied by HostService.MAX_SCHEDULING_CONSTANT.
     */
    @NotNull
    @Range(min = 0, max = HostService.MAX_SCHEDULING_CONSTANT)
    @DefaultLong(0)
    public Long lastSchedulingConstant;
  }
}
