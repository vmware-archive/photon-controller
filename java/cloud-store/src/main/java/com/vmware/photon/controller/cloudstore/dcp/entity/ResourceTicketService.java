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

package com.vmware.photon.controller.cloudstore.dcp.entity;

import com.vmware.photon.controller.api.QuotaLineItem;
import com.vmware.photon.controller.api.QuotaUnit;
import com.vmware.photon.controller.cloudstore.CloudStoreModule;
import com.vmware.photon.controller.common.xenon.InitializationUtils;
import com.vmware.photon.controller.common.xenon.ServiceUtils;
import com.vmware.photon.controller.common.xenon.ValidationUtils;
import com.vmware.photon.controller.common.xenon.upgrade.NoMigrationDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.MigrateDuringUpgrade;
import com.vmware.photon.controller.common.xenon.upgrade.UpgradeUtils;
import com.vmware.photon.controller.common.xenon.validation.Immutable;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.ServiceErrorResponse;
import com.vmware.xenon.common.StatefulService;
import com.vmware.xenon.common.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Class ResourceTicketService is used for data persistence of Resource Ticket information.
 */
public class ResourceTicketService extends StatefulService {

  private static final double BYTES_PER_KB = 1024.0;
  private static final double BYTES_PER_MB = BYTES_PER_KB * 1024.0;
  private static final double BYTES_PER_GB = BYTES_PER_MB * 1024.0;

  public ResourceTicketService() {
    super(State.class);
    super.toggleOption(ServiceOption.PERSISTENCE, true);
    super.toggleOption(ServiceOption.REPLICATION, true);
    super.toggleOption(ServiceOption.OWNER_SELECTION, true);
    super.toggleOption(ServiceOption.INSTRUMENTATION, true);
  }

  @Override
  public void handleStart(Operation startOperation) {
    ServiceUtils.logInfo(this, "Starting service %s", getSelfLink());
    try {
      State startState = startOperation.getBody(State.class);
      InitializationUtils.initialize(startState);
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
  public void handlePatch(Operation patchOperation) {
    try {
      ServiceUtils.logInfo(this, "Patching service %s", getSelfLink());
      State currentState = getState(patchOperation);
      Patch patch = patchOperation.getBody(Patch.class);

      switch (patch.patchtype) {
        case USAGE_CONSUME:
          consumeQuota(patch, currentState);
          break;
        case USAGE_RETURN:
          returnUsage(patch, currentState);
          break;
        default:
          String message =
              String.format("PatchType {%s} in patchOperation {%s}", patch.patchtype, patchOperation);
          throw new UnsupportedOperationException(message);
      }

      validateState(currentState);
      patchOperation.complete();
      ServiceUtils.logInfo(this, "Patch of type {%s} successfully applied", patch.patchtype);
    } catch (QuotaException quotaException) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, quotaException, quotaException.quotaErrorResponse);
    } catch (IllegalStateException t) {
      ServiceUtils.failOperationAsBadRequest(this, patchOperation, t);
    } catch (Throwable t) {
      ServiceUtils.logSevere(this, t);
      patchOperation.fail(t);
    }
  }

  @Override
  public void handleDelete(Operation deleteOperation) {
    ServiceUtils.expireDocumentOnDelete(this, State.class, deleteOperation);
  }

  private void consumeQuota(Patch patch, State currentState)
      throws QuotaException {
    // first, whip through the cost's actualCostKeys and
    // compute the new usage. then, if usage is ok, commit
    // the new usage values and then update rawUsage
    List<QuotaLineItem> newUsage = new ArrayList<>();
    for (String key : patch.cost.keySet()) {
      if (!currentState.usageMap.containsKey(key)) {
        // make sure usage map has appropriate entries, its only initialized
        // with keys from the limit set
        currentState.usageMap.put(key,
            new QuotaLineItem(key, 0.0, patch.cost.get(key).getUnit()));
      }

      // capture current usage into a new object
      QuotaLineItem qli = new QuotaLineItem(key,
          currentState.usageMap.get(key).getValue(),
          currentState.usageMap.get(key).getUnit());
      QuotaLineItem computedUsage = add(qli, patch.cost.get(key));
      newUsage.add(computedUsage);
    }

    // now compare newUsage against limits. if usage > limit, then return false with no
    // side effects. otherwise, apply the new usage values, then blindly update rawUsage
    for (QuotaLineItem qli : newUsage) {
      if (!currentState.limitMap.containsKey(qli.getKey())) {
        // only enforce limits is the usage entry is covered by
        // limits
        continue;
      }

      // test to see if the limit is less than the computed
      // new usage. if it is, then abort
      if (compare(currentState.limitMap.get(qli.getKey()), qli) < 0) {
        throw new QuotaException(new QuotaErrorResponse(
            currentState.limitMap.get(qli.getKey()),
            currentState.usageMap.get(qli.getKey()), qli));
      }
    }

    // if we made it this far, commit the new usage
    for (QuotaLineItem qli : newUsage) {
      currentState.usageMap.put(qli.getKey(), qli);
    }
  }

  private void returnUsage(Patch patch, State currentState) {
    // return the cost usage. this undoes the
    // quota consumption that occurs during consumeQuota

    for (String key : patch.cost.keySet()) {
      currentState.usageMap.put(key, subtract(currentState.usageMap.get(key), patch.cost.get(key)));
    }
  }

  /**
   * Returns a QuotaLineItem whose value is this + val. The unit in the result are the same as
   * the unit in "this". That is to say if "this" is in KB and "val" is in MB, the result is in KB.
   * <p>
   * The unit for B and COUNT represent the normalized value. This means that 1 KB is
   * the same as 1024 B or COUNT.
   * </p>
   * <p>
   * Note: it is expected that callers make sensible calls and ensure that if appropriate,
   * the key's are identical.
   * </p>
   *
   * @param val1 - the value to be added to
   * @param val2 - the value to be added
   * @return this + val, unit of the response is identical to this.getUnit();
   */
  private QuotaLineItem add(QuotaLineItem val1, QuotaLineItem val2) {
    return operate(OpCode.ADD, val1, val2);
  }

  /**
   * Returns a QuotaLineItem whose value is this - val. The unit in the result are the same as
   * the unit in "this". That is to say if "this" is in KB and "val" is in MB, the result is in KB.
   * <p>
   * The unit for B and COUNT represent the normalized value. This means that 1 KB is
   * the same as 1024 B or COUNT.
   * </p>
   * <p>
   * Note: it is expected that callers make sensible calls and ensure that if appropriate,
   * the key's are identical.
   * </p>
   *
   * @param val1 - the value to be subtracted from
   * @param val2 - the value to be subtracted
   * @return this - val, unit of the response is identical to this.getUnit();
   */
  private QuotaLineItem subtract(QuotaLineItem val1, QuotaLineItem val2) {
    return operate(OpCode.SUB, val1, val2);
  }

  private QuotaLineItem operate(OpCode op, QuotaLineItem val1, QuotaLineItem val2) {
    double normalizedResult;
    double normalizedVal;
    double normalizedThis;

    // normalize the values based on the specified unit
    normalizedVal = normalize(val2);
    normalizedThis = normalize(val1);
    normalizedResult = 0.0;

    switch (op) {
      case ADD:
        normalizedResult = normalizedThis + normalizedVal;
        break;

      case SUB:
        normalizedResult = normalizedThis - normalizedVal;
        break;

    }
    // convert unit to the unit specified by this. the precise
    // behavior is to return this <op> val converted into the unit
    // specified by this.getUnit()
    return convert(val1.getKey(), normalizedResult, val1.getUnit());
  }

  private QuotaLineItem convert(String key, double val, QuotaUnit unit) {
    double converted = val;

    // todo(markl): determine rounding, maybe 4 decimal digits? maybe a function of the unit?
    // todo(markl): possibly violate left hand side if B magnitude is less than target unit.
    switch (unit) {
      case GB:
        converted = val / BYTES_PER_GB;
        break;
      case MB:
        converted = val / BYTES_PER_MB;
        break;
      case KB:
        converted = val / BYTES_PER_KB;
        break;
      case B:
        converted = val;
        break;
      case COUNT:
        converted = val;
        break;
    }
    return new QuotaLineItem(key, converted, unit);
  }

  /**
   * Compare this QuotaLineItem with the specified one. Return -1, 0, 1 if this' value is
   * less than, equal to, or greater than val.  This method is provided in preference to
   * individual methods for each of the six boolean comparison operators (<, ==, >, >=, !=, <=).
   * The suggested idiom for performing these comparisons is: (x.compareTo(y) <op> 0),
   * where <op> is one of the six comparison operators.
   * <p>
   * Note: it is expected that callers make sensible calls and ensure that if appropriate,
   * the key's are identical.
   * </p>
   *
   * @param val1 - the first QuotaLineItem to be compared
   * @param val2 - the second QuotaLineItem to be compared
   * @return -1 this is less than val, 0 if equal, +1 if this is greater than val
   */
  public int compare(QuotaLineItem val1, QuotaLineItem val2) {
    double normalizedResult;
    double normalizedVal;
    double normalizedThis;
    int rv;

    // normalize the values based on the specified unit
    normalizedVal = normalize(val2);
    normalizedThis = normalize(val1);

    // compare this to val
    normalizedResult = normalizedThis - normalizedVal;

    if (normalizedResult < 0) {
      rv = -1;
    } else if (normalizedResult == 0) {
      rv = 0;
    } else {
      rv = 1;
    }

    return rv;
  }

  private double normalize(QuotaLineItem val) {
    double normalized = val.getValue();

    switch (val.getUnit()) {
      case GB:
        normalized = val.getValue() * BYTES_PER_GB;
        break;
      case MB:
        normalized = val.getValue() * BYTES_PER_MB;
        break;
      case KB:
        normalized = val.getValue() * BYTES_PER_KB;
        break;
      case B:
        normalized = val.getValue();
        break;
      case COUNT:
        normalized = val.getValue();
        break;
    }
    return normalized;
  }

  /**
   * Validate the service state for coherence.
   *
   * @param currentState
   */
  protected void validateState(State currentState) {
    ValidationUtils.validateState(currentState);
  }

  private enum OpCode {ADD, SUB}

  /**
   * Gets thrown when resource creation cannot proceed because there was not enough quota allocated to accommodate it.
   */
  public static class QuotaException extends IllegalArgumentException {

    private final QuotaErrorResponse quotaErrorResponse;

    public QuotaException(QuotaErrorResponse quotaErrorResponse) {
      this.quotaErrorResponse = quotaErrorResponse;
    }

    @Override
    public String getMessage() {
      return quotaErrorResponse.getMessage();
    }
  }

  /**
   * Captures error details when resource creation cannot proceed because there was not enough quota allocated to
   * accommodate it.
   */
  public static class QuotaErrorResponse extends ServiceErrorResponse {

    public static final String KIND = Utils.buildKind(QuotaErrorResponse.class);

    public final QuotaLineItem limit;
    public final QuotaLineItem usage;
    public final QuotaLineItem newUsage;

    public QuotaErrorResponse(QuotaLineItem limit, QuotaLineItem usage, QuotaLineItem newUsage) {
      this.limit = limit;
      this.usage = usage;
      this.newUsage = newUsage;
      this.documentKind = KIND;
      this.message = getMessage();
    }

    public String getMessage() {
      return String.format("Not enough quota: Current Limit: %s, desiredUsage %s",
          this.limit.toString(),
          this.newUsage.toString());
    }
  }

  /**
   * Class encapsulating patch data for ResourceTicket.
   */
  @NoMigrationDuringUpgrade
  public static class Patch extends ServiceDocument {

    public PatchType patchtype = PatchType.NONE;

    public Map<String, QuotaLineItem> cost = new HashMap<>();

    /**
     * Defines the purpose of the patch.
     */
    public enum PatchType {
      NONE,
      USAGE_CONSUME,
      USAGE_RETURN
    }
  }

  /**
   * Durable service state data. Class encapsulating the data for ResourceTicket.
   */
  @MigrateDuringUpgrade(transformationServicePath = UpgradeUtils.REFLECTION_TRANSFORMATION_SERVICE_LINK,
      sourceFactoryServicePath = ResourceTicketServiceFactory.SELF_LINK,
      destinationFactoryServicePath = ResourceTicketServiceFactory.SELF_LINK,
      serviceName = CloudStoreModule.CLOUDSTORE_SERVICE_NAME)
  public static class State extends ServiceDocument {

    @Immutable
    public String name;

    public Set<String> tags = new HashSet<>();

    //needed for tenant level tickets
    @Immutable
    public String tenantId;

    // the parent is used in project level resource tickets
    // and points to the tenant level resource ticket that this
    // ticket is based on. when a project ticket is destroyed,
    // its current usage needs to be returned to the parent tenant ticket
    @Immutable
    public String parentId;

    // maps to track usage/limits and provide direct lookup
    public Map<String, QuotaLineItem> limitMap = new HashMap<>();

    public Map<String, QuotaLineItem> usageMap = new HashMap<>();
  }
}
