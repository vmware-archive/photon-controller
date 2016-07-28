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

package com.vmware.photon.controller.api.frontend.entities;

import com.vmware.photon.controller.api.model.QuotaUnit;

import java.util.Objects;

/**
 * This is the internal representation, which differs from external in that .unit is an Enum, not a String.
 */
public class QuotaLineItemEntity {

  private static final double BYTES_PER_KB = 1024.0;
  private static final double BYTES_PER_MB = BYTES_PER_KB * 1024.0;
  private static final double BYTES_PER_GB = BYTES_PER_MB * 1024.0;
  private String key;
  private double value;
  private QuotaUnit unit;

  public QuotaLineItemEntity() {
  }

  public QuotaLineItemEntity(String key, double value, QuotaUnit unit) {
    this.key = key;
    this.value = value;
    this.unit = unit;
  }

  public String toString() {
    return key + ", " + value + ", " + unit;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public double getValue() {
    return value;
  }

  public void setValue(double value) {
    this.value = value;
  }

  public QuotaUnit getUnit() {
    return unit;
  }

  public void setUnit(QuotaUnit unit) {
    this.unit = unit;
  }

  /**
   * Returns a QuotaLineItemEntity whose value is this + val. The unit in the result are the same as
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
   * @param val - the value to be added to this
   * @return this + val, unit of the response is identical to this.getUnit();
   */
  public QuotaLineItemEntity add(QuotaLineItemEntity val) {
    return operate(OpCode.ADD, val);
  }

  /**
   * Returns a QuotaLineItemEntity whose value is this - val. The unit in the result are the same as
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
   * @param val - the value to be subtracted from this
   * @return this - val, unit of the response is identical to this.getUnit();
   */
  public QuotaLineItemEntity subtract(QuotaLineItemEntity val) {
    return operate(OpCode.SUB, val);
  }

  /**
   * Compare this QuotaLineItemEntity with the specified one. Return -1, 0, 1 if this' value is
   * less than, equal to, or greater than val.  This method is provided in preference to
   * individual methods for each of the six boolean comparison operators (<, ==, >, >=, !=, <=).
   * The suggested idiom for performing these comparisons is: (x.compareTo(y) <op> 0),
   * where <op> is one of the six comparison operators.
   * <p>
   * Note: it is expected that callers make sensible calls and ensure that if appropriate,
   * the key's are identical.
   * </p>
   *
   * @param val - the QuotaLineItemEntity to be compared
   * @return -1 this is less than val, 0 if equal, +1 if this is greater than val
   */
  public int compareTo(QuotaLineItemEntity val) {
    double normalizedResult;
    double normalizedVal;
    double normalizedThis;
    int rv;

    // normalize the values based on the specified unit
    normalizedVal = normalize(val);
    normalizedThis = normalize(this);

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

  private QuotaLineItemEntity operate(OpCode op, QuotaLineItemEntity val) {
    double normalizedResult;
    double normalizedVal;
    double normalizedThis;

    // normalize the values based on the specified unit
    normalizedVal = normalize(val);
    normalizedThis = normalize(this);
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
    return convert(normalizedResult, this.getUnit());
  }

  private double normalize(QuotaLineItemEntity val) {
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

  private QuotaLineItemEntity convert(double val, QuotaUnit unit) {
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
    return new QuotaLineItemEntity(key, converted, unit);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    QuotaLineItemEntity other = (QuotaLineItemEntity) o;

    return Objects.equals(key, other.key) &&
        Objects.equals(value, other.value) &&
        Objects.equals(unit, other.unit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(key, value, unit);
  }

  private enum OpCode {ADD, SUB}
}
