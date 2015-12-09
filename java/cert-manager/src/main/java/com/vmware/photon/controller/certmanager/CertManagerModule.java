/*
 * Copyright (c) 2015 VMware, Inc. All Rights Reserved.
 */
package com.vmware.photon.controller.certmanager;

import com.vmware.photon.controller.certmanager.dcp.CertManagerServiceHost;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;

public class CertManagerModule extends AbstractModule {

  private final Config config;

  public CertManagerModule(Config config) {
    this.config = config;
  }

  @Override
  protected void configure() {
    bindConstant().annotatedWith(Config.Bind.class).to(config.getBind());
    bindConstant().annotatedWith(Config.Port.class).to(config.getPort());
  }

  @Provides
  @Singleton
  public CertManagerServiceHost getCertManagerServiceHost(
      @Config.Bind String bind,
      @Config.Port int port
  ) throws Throwable {
    return new CertManagerServiceHost(bind, port);
  }
}
