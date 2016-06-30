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
import com.vmware.photon.controller.common.xenon.validation.DefaultInteger;
import com.vmware.photon.controller.common.xenon.validation.NotNull;
import com.vmware.photon.controller.common.xenon.validation.Positive;
import com.vmware.xenon.common.FactoryService;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceHost;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import org.apache.commons.math3.random.HaltonSequenceGenerator;

import java.util.Random;
import java.util.logging.Level;

/**
 * This class implements a Xenon micro-service that provides a Halton sequence
 * generator.
 * (see https://en.wikipedia.org/wiki/Halton_sequence)
 */
public class HaltonSequenceService extends StatefulService {
  public static final String FACTORY_LINK = ServiceUriPaths.CLOUDSTORE_ROOT + "/halton-sequences";
  public static final String SINGLETON_LINK = FACTORY_LINK + "/seq0";

  // Maximum number of elements to skip when creating a new instance of this
  // service (see note in handleStart). This value is arbitrary.
  private static final int MAX_SKIP = 1000;

  public static FactoryService createFactory() {
    return FactoryService.createIdempotent(HaltonSequenceService.class);
  }

  /**
   * Generates a CompletionHandler that starts a single HaltonSequenceService.
   * Call ServiceHost.registerForServiceAvailability with the result of this
   * function in order to start the singleton instance of HaltonSequenceService.
   *
   * (patterned after SampleBootstrapService)
   */
  public static Operation.CompletionHandler startSingletonService(ServiceHost host) {
    return (o, e) -> {
      if (e != null) {
        host.log(Level.SEVERE, Utils.toString(e));
        return;
      }

      HaltonSequenceService.State newState = new HaltonSequenceService.State();
      newState.documentSelfLink = HaltonSequenceService.SINGLETON_LINK;

      Operation post = Operation
          .createPost(host, HaltonSequenceService.FACTORY_LINK)
          .setBody(newState)
          .setReferer(ServiceUriPaths.CLOUDSTORE_ROOT)
          .setCompletion((oo, ee) -> {
            if (ee != null) {
              host.log(Level.SEVERE, Utils.toString(ee));
              return;
            }
            host.log(Level.INFO, "Singleton HaltonSequenceService started");
          });
      host.sendRequest(post);
    };
  }

  public HaltonSequenceService() {
    super(State.class);

    // As of this writing (using Xenon 0.9.0), enabling OWNER_SELECTION,
    // PERSISTENCE, and REPLICATION on this service triggers a Xenon bug that
    // causes conflicts during replication/sync when starting multiple Xenon
    // hosts. As a result, for now, each Xenon host will have its own
    // quasi-singleton instance of this service.
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
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

      // Because each Xenon host has its own instance of this service, skip to
      // a random start point, to create independence between the sequences
      // generated on each Xenon host. This improves the uniformity of the
      // generated sequence values.
      startState.index = new Random().nextInt(MAX_SKIP);

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
  public void handlePut(Operation putOperation) {
    if (putOperation.hasPragmaDirective(Operation.PRAGMA_DIRECTIVE_POST_TO_PUT)) {
      // converted PUT due to IDEMPOTENT_POST option
      logInfo("HaltonSequenceService instance is already started. Ignoring converted PUT.");
      putOperation.complete();
      return;
    }

    // normal PUT is not supported
    putOperation.fail(Operation.STATUS_CODE_BAD_METHOD);
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
     * Index of the next value to be emitted from the Halton sequence.
     */
    @NotNull
    @Positive
    @DefaultInteger(0)
    public Integer index;

    /**
     * Last value returned from the sequence (the value at [index-1]).
     */
    @NotNull
    public Double lastValue;
  }
}
