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

package com.vmware.photon.controller.common.dcp;

import com.vmware.dcp.common.ServiceDocument;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A generic class to hold a service document page as well as
 * its previous page and its next page links, if available.
 *
 * @param <T> Service document type.
 */
public class ServiceDocumentPage<T extends ServiceDocument> {

  private List<String> documentLinks = new ArrayList<>();
  private Map<String, T> documents = new HashMap<>();
  private String nextPageLink;
  private String prevPageLink;

  public ServiceDocumentPage() {
  }

  public ServiceDocumentPage(List<String> documentLinks,
                             Map<String, T> documents,
                             String nextPageLink,
                             String prevPageLink) {

    this.documentLinks.addAll(documentLinks);
    this.documents.putAll(documents);
    this.nextPageLink = nextPageLink;
    this.prevPageLink = prevPageLink;
  }

  public List<String> getDocumentLinks() {
    return documentLinks;
  }

  public void setDocumentLinks(List<String> documentLinks) {
    this.documentLinks.addAll(documentLinks);
  }

  public Map<String, T> getDocuments() {
    return documents;
  }

  public void setDocuments(Map<String, T> documents) {
    this.documents.putAll(documents);
  }

  public String getNextPageLink() {
    return nextPageLink;
  }

  public void setNextPageLink(String nextPageLink) {
    this.nextPageLink = nextPageLink;
  }

  public String getPrevPageLink() {
    return prevPageLink;
  }

  public void setPrevPageLink(String prevPageLink) {
    this.prevPageLink = prevPageLink;
  }

  @Override
  public String toString() {
    return Objects.toStringHelper(this)
        .add("documentLinks", documentLinks.toString())
        .add("nextPageLink", nextPageLink)
        .add("prevPageLink", prevPageLink)
        .toString();
  }
}
