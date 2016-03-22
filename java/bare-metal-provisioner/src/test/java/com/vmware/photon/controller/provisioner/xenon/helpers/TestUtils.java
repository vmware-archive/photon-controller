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

package com.vmware.photon.controller.provisioner.xenon.helpers;

import com.vmware.photon.controller.provisioner.xenon.entity.DhcpConfigurationServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpLeaseServiceFactory;
import com.vmware.photon.controller.provisioner.xenon.entity.DhcpSubnetServiceFactory;
import com.vmware.xenon.common.Operation;
import com.vmware.xenon.common.Service;
import com.vmware.xenon.common.ServiceDocument;
import com.vmware.xenon.common.UriUtils;
import com.vmware.xenon.common.test.VerificationHost;

import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Test helper methods.
 */
public class TestUtils {
  public static boolean isNull(String... options) {
    for (String option : options) {
      if (option == null) {
        return false;
      }
    }
    return true;
  }

  /**
   * Add a class to register for availability.
   *
   * @param host VerificationHost
   * @param type Class to register
   * @return URI for registered class
   * @throws Exception
   */
  public static URI register(VerificationHost host, Class<? extends Service> type)
      throws Exception {
    try {
      host.testStart(1);
      Field f = type.getField("SELF_LINK");
      String path = (String) f.get(null);
      host.registerForServiceAvailability(host.getCompletion(), path);
      host.testWait();
    } catch (Throwable e) {
      throw new Exception(e);
    }

    return UriUtils.buildUri(host, type);
  }

  /**
   * Generic doPost.
   *
   * @param host    VerificationHost
   * @param inState Body to POST
   * @param type    Body type to return
   * @param uri     URI to post to
   * @param <T>     type
   * @return State of service after POST
   * @throws Throwable
   */
  public static <T extends ServiceDocument, B extends ServiceDocument> B doPost(
      VerificationHost host, T inState, Class<B> type, URI uri) throws Throwable {
    final ServiceDocument[] doc = {null};
    host.testStart(1);
    Operation post = Operation
        .createPost(uri)
        .setBody(inState).setCompletion(
            (o, e) -> {
              if (e != null) {
                host.failIteration(e);
                return;
              }
              doc[0] = o.getBody(ServiceDocument.class);
              host.completeIteration();
            });
    host.send(post);
    host.testWait();
    host.logThroughput();

    B outState = host.getServiceState(null,
        type,
        UriUtils.buildUri(uri.getHost(), uri.getPort(), doc[0].documentSelfLink, null));

    return outState;
  }

  /**
   * Post a list of bodies to a given URI and return the current state.
   *
   * @param host    VerificationHost
   * @param inState Bodies to post
   * @param type    Type to return
   * @param uri     URI to post to
   * @param <T>     type
   * @return List of return bodies keyed by URI.
   * @throws Throwable
   */
  public static <T extends ServiceDocument, B extends ServiceDocument> Map<URI, B> doParallelPost(
      VerificationHost host, List<T> inState, Class<B> type, URI uri) throws Throwable {
    Operation post = Operation.createPost(uri).setCompletion(host.getCompletion());
    List<URI> uris = Collections.synchronizedList(new ArrayList<>());

    host.testStart(inState.size());
    for (T s : inState) {
      post.setBody(s).setCompletion((o, e) -> {
        if (e != null) {
          o.fail(e);
          return;
        }
        B out = o.getBody(type);
        uris.add(UriUtils.buildUri(uri, out.documentSelfLink));
        o.complete();
        host.completeIteration();
      });
      host.send(post);
    }
    host.testWait();
    host.logThroughput();

    Map<URI, B> outState = host.getServiceState(null,
        type, uris.toArray(new URI[inState.size()]));

    return outState;
  }

  public static void startDhcpServices(VerificationHost host) throws Throwable {
    List<String> serviceSelfLinks = new ArrayList<String>();
    host.startService(
        Operation.createPost(UriUtils.buildUri(host,
            DhcpSubnetServiceFactory.class)),
        new DhcpSubnetServiceFactory());
    serviceSelfLinks.add(DhcpSubnetServiceFactory.SELF_LINK);
    host.startService(
        Operation.createPost(UriUtils.buildUri(host,
            DhcpLeaseServiceFactory.class)),
        new DhcpLeaseServiceFactory());
    serviceSelfLinks.add(DhcpLeaseServiceFactory.SELF_LINK);
    host.startService(
        Operation.createPost(UriUtils.buildUri(host,
            DhcpConfigurationServiceFactory.class)),
        new DhcpConfigurationServiceFactory());
    serviceSelfLinks.add(DhcpConfigurationServiceFactory.SELF_LINK);
    host.testStart(serviceSelfLinks.size());
    host.registerForServiceAvailability(host.getCompletion(),
        serviceSelfLinks.toArray(new String[]{}));
    host.testWait();
  }
}
