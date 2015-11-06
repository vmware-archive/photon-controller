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

package com.vmware.photon.controller.apife.auth;

/**
 * Class storing information about the resource that is the target of the transaction.
 */
public class TransactionAuthorizationObject {
  private Kind kind;
  private Strategy strategy;
  private String id;

  public TransactionAuthorizationObject(Kind kind) {
    this(kind, Strategy.SELF, null);
  }

  public TransactionAuthorizationObject(Kind kind, Strategy strategy, String id) {
    this.kind = kind;
    this.strategy = strategy;
    this.id = id;
  }

  public Kind getKind() {
    return this.kind;
  }

  public void setKind(Kind kind) {
    this.kind = kind;
  }

  public Strategy getStrategy() {
    return this.strategy;
  }

  public void setStrategy(Strategy strategy) {
    this.strategy = strategy;
  }

  public String getId() {
    return this.id;
  }

  public void setId(String id) {
    this.id = id;
  }

  /**
   * Enum of authorizations objects that need to be explicitly
   * handled.
   */
  public enum Kind {
    NONE,
    CLUSTER,
    DEPLOYMENT,
    DISK,
    PROJECT,
    RESOURCE_TICKET,
    TENANT,
    VM
  }

  /**
   * Enum of possible authorization strategies that control where
   * the security groups are retrieved from.
   */
  public enum Strategy {
    SELF,
    PARENT
  }
}
