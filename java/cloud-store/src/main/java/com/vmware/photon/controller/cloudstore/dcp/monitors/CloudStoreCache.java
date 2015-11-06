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

package com.vmware.photon.controller.cloudstore.dcp.monitors;

import com.vmware.dcp.common.Operation;
import com.vmware.dcp.common.ServiceDocument;
import com.vmware.photon.controller.common.dcp.DcpRestClient;
import com.vmware.photon.controller.common.dcp.exceptions.BadRequestException;
import com.vmware.photon.controller.common.dcp.exceptions.DocumentNotFoundException;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * The CloudStoreCache implements a client side cache. Essentially, it is a local view of factory
 * service children resources . On add, edit and delete events the CloudStoreCache will propagate
 * the event to either onAdd, onUpdate or onRemove methods. Classes that extend this class should
 * implement the onAdd, onUpdate and onRemove methods to implement more specialized monitors, for
 * example CloudStoreMonitor.
 *
 * Classes extending CloudStoreCache should not update the cache, in other words they should
 * only ready from currentData and not modify it.
 */

public abstract class CloudStoreCache {
    private static final Logger logger = LoggerFactory.getLogger(CloudStoreCache.class);
    protected final Gson gson = new Gson();
    private final DcpRestClient dcpRestClient;
    protected final Map<String, Map<String, CachedDocument>> currentData;
    protected final Map<String, Class> pathTypes;

    public CloudStoreCache(DcpRestClient dcpRestClient, LinkedHashMap<String, Class> paths) {
      this.dcpRestClient = dcpRestClient;

      // The cache will multiple paths according to their order in paths
      // CloudStoreCache will update its cache according to the order of
      // paths supplied. The reason we need to impose order on retrieving
      // the data is because, some resources reference other documents which
      // need to be retrieved prior to triggering the event.

      this.currentData = new LinkedHashMap();

      this.pathTypes = new HashMap();
      for (String typeName : paths.keySet()) {
        this.pathTypes.put(typeName, paths.get(typeName));
      }

      /*
       * Since the CloudStoreCache can cache multiple paths at the same
       * time, a prefix mapping is needed to map a prefix with all its
       * suffixes (i.e. its children). For example, if the paths variable is
       * {A,B}, and A has a1 and a2 as children, and B has b1 as a child, then
       * the prefix map would like like this:
       *    A -> map1
       *    B -> map2
       *
       *    where map1 is :
       *    a1 -> a1 Document
       *    a2 -> a2 Document
       *
       *    and map2 is:
       *    b1 -> b1 Document
       */
      for (String prefixPath : paths.keySet()) {
          this.currentData.put(prefixPath, new HashMap());
      }
    }

    private String expand(String url) {
        return url + "?expand";
    }

    private String getResourceId(String uri) {
        String[] segments = uri.split("/");
        return segments[segments.length - 1];
    }

    private long getVersion(Object obj) {
      JsonObject json = gson.fromJson((String) obj, JsonObject.class);
      return json.get("documentVersion").getAsLong();
    }

    /**
     * Given an expand query response and a path, this method will update the cache's
     * view for that particular path. As a side effect of the update, certain events will
     * be triggered when resources are added, removed and modified.
     *
     * @param queryResponse     The response from expanding the factory service path
     * @param path              A path to the factory service
     */
    private void processQuery(ExpandQueryResponse queryResponse, String path) {
        Map<String, CachedDocument> pathResources = currentData.get(path);
        Set<String> newPaths = new HashSet();
        for (String fullPath : queryResponse.documents.keySet()) {
          newPaths.add(getResourceId(fullPath));
        }

        Class documentType = pathTypes.get(path);

        Set<String> purgePaths = new HashSet(pathResources.keySet());
        purgePaths.removeAll(newPaths);

        for (String uri : queryResponse.documents.keySet()) {
          String id = getResourceId(uri);

          String newJson = (String) queryResponse.documents.get(uri);
          Operation op = new Operation();
          op.setBody(newJson);
          ServiceDocument document = (ServiceDocument) op.getBody(documentType);
          CachedDocument newDocument = new CachedDocument(document, getVersion(newJson));

          CachedDocument currentDocument = pathResources.get(id);
          if (currentDocument == null) {
            // First time seeing this path, add it to the current view and
            // emit a notification
            pathResources.put(id, newDocument);
            onAdd(path, id, newDocument.getDocument());
          } else {
              if (currentDocument.getVersion() < newDocument.getVersion()) {
                // There is a newer version of this path, update
                // the current view
                pathResources.put(id, newDocument);
                onUpdate(path, id, newDocument.getDocument());
              } else if (currentDocument.getVersion() > newDocument.getVersion()) {
                logger.error("Ignoring event, resource {}/{} version decreased from {} to {}",
                        path, id,
                        currentDocument.getVersion(), newDocument.getVersion());
              }
          }
        }

        // Only keep the paths that are in the intersection of
        // current paths and newly seen paths

        for (String oldId : purgePaths) {
          // can delete an existing path from currentData, if we are querying
          // inconsistent replicas between queries
          CachedDocument document = pathResources.get(oldId);
          pathResources.remove(oldId);
          onRemove(path, oldId, document.document);
        }
    }

    /**
     * This method will refresh the cache, as a result some events
     * might be triggered.
     */
    public void refresh() {

        for (String prefixPath : currentData.keySet()) {
            try {
              StopWatch timer = new StopWatch();
              timer.start();
              Operation op = dcpRestClient.getAndWait(expand(prefixPath));
              timer.stop();
              long retrievalTime = timer.getTime() / 1000;
              ExpandQueryResponse resp = op.getBody(ExpandQueryResponse.class);
              logger.info("Took {} seconds to get {}, query time {} us", retrievalTime,
                      prefixPath, resp.queryTimeMicros);
              // Assumes that the expanded query response will return
              // a set of resource ids there should be no duplicate ids
              processQuery(resp, prefixPath);
            } catch (BadRequestException | DocumentNotFoundException |
                    InterruptedException | TimeoutException e) {
              logger.error("Encountered an error while executing the expand query for {}",
                      prefixPath, e);
              // Since the retrieval order matters, we shouldn't continue in the case of an
              // error occurring. Latter queries can have resources referencing prior queries
              // (i.e. Host documents referencing Datastore documents).
              break;
            }
        }
    }

    /*
    * This method is called when a child node is created under a path.
    */
    protected abstract void onAdd(String path, String id, ServiceDocument document);

    /*
     * This method is called when a child node is updated.
     */
    protected abstract void onUpdate(String path, String id, ServiceDocument document);

    /*
     * This method is called when a child node is deleted.
     */
    protected abstract void onRemove(String path, String id, ServiceDocument document);


    /**
     * Body type that is returned by a response query.
     */
    public class ExpandQueryResponse {
      public Map<String, Object> documents;
      public long queryTimeMicros;
    }

    /**
     * CloudStoreCache's internal representation of versioned documents.
     */
    public class CachedDocument {
      private long version;
      private ServiceDocument document;

      public CachedDocument(ServiceDocument document, long version) {
        this.version = version;
        this.document = document;
      }

      public long getVersion() {
        return this.version;
      }

      public ServiceDocument getDocument() {
        return this.document;
      }
    }
}
