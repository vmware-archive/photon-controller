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

import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUriPaths;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.host.PhotonControllerXenonHost;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.*;

import org.apache.commons.math3.random.HaltonSequenceGenerator;

import java.net.URI;
import java.util.Random;

/**
 * This class implements a Xenon micro-service that provides a Halton sequence
 * generator.
 * (see https://en.wikipedia.org/wiki/Halton_sequence)
 */
public class HaltonSequenceService extends StatefulService {
  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/halton-sequences";
  public static final String SINGLETON_LINK = FACTORY_LINK + "/seq0";

  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(HaltonSequenceService.class);
  }

  /**
   * Starts a single HaltonSequenceService.
   */
  public static void startSingletonService(PhotonControllerXenonHost host) {
    HaltonSequenceService.State newState = new HaltonSequenceService.State();
    newState.documentSelfLink = HaltonSequenceService.SINGLETON_LINK;

    Operation post = Operation
        .createPost(UriUtils.buildFactoryUri(host, HaltonSequenceService.class))
        .setBody(newState)
        .setReferer(UriUtils.buildUri(host, ServiceUriPaths.CLOUDSTORE_ROOT));
    host.sendRequest(post);
  }

  // random is used to fast-forward the sequence to a random start point
  private final Random random = new Random();

  // maximum number of elements to skip at the beginning
  private static final int MAX_SKIP = 300;

  public HaltonSequenceService() {
    super(State.class);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);

      // Skip some random number of starting elements (that is, start getting
      // numbers from the sequence at some random point)
      startState.index = random.nextInt(MAX_SKIP);

      nextValue(startState);
      validateState(startState);
      startOperation.complete();
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, startOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      startOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  @Override
  public void handlePatch(Operation patchOperation) {
    // The body of the patch is ignored. Generate the next value of the
    // sequence, store it, and return it in the output so that the client
    // that invoked PATCH can use this result.
    State state = getState(patchOperation);
    nextValue(state);
    validateState(state);
    patchOperation.setBody(state);
    patchOperation.complete();
  }

  @Override
  public void handlePut(Operation put) {

    if (put.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
      // converted PUT due to IDEMPOTENT_POST option
      logInfo("Task has already started. Ignoring converted PUT.");
      put.complete();
      return;
    }

    // normal PUT is not supported
    put.fail(Operation.STATUS_CODE_BAD_METHOD);
  }

  private void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  /**
   * Computes the next value of the sequence and updates state accordingly.
   */
  private void nextValue(State state) {
    HaltonSequenceGenerator hsg = new HaltonSequenceGenerator(1);
    double[] nextValues = hsg.skipTo(state.index);

    state.index++;
    state.lastValue = nextValues[0];
  }

  /**
   * Defines the document state for a HaltonSequenceService instance.
   */
  public static class State extends ServiceDocument {
    /**
     * This value represents the index of the next value to be emitted from
     * the Halton sequence.
     */
    @NotNull
    @Positive
    public Integer index;

    /**
     * Last value returned from the sequence (the value at [index-1]).
     */
    @NotNull
    public Double lastValue;
  }
}
