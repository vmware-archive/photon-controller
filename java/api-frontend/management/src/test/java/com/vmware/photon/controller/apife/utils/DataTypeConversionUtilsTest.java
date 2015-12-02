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

package com.vmware.photon.controller.apife.utils;

import com.vmware.photon.controller.api.ResourceList;
import com.vmware.xenon.common.ServiceDocumentQueryResult;
import com.vmware.xenon.common.Utils;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

/**
 * Tests for {@link DataTypeConversionUtils}.
 */
public class DataTypeConversionUtilsTest {

  @Test(enabled = false)
  private void dummy() {
  }

  /**
   * Tests for xenonQueryResultToResourceList.
   */
  public static class TestXenonQueryResultToResourceList {

    private class ExampleData {
      public String name;
    }

    @Test
    public void testXenonQueryResultToResourceListEmptyDataSet() {
      ResourceList<ExampleData> resourceList = DataTypeConversionUtils.xenonQueryResultToResourceList(ExampleData.class,
          new ServiceDocumentQueryResult());

      assertThat(resourceList.getItems().size(), is(0));
      assertThat(resourceList.getNextPageLink(), nullValue());
      assertThat(resourceList.getPreviousPageLink(), nullValue());
    }

    @Test
    public void testXenonQueryResultToResourceList() {
      ServiceDocumentQueryResult queryResult = generateQueryResultData();
      ResourceList<ExampleData> resourceList = DataTypeConversionUtils.xenonQueryResultToResourceList(
          ExampleData.class, queryResult);

      assertThat(resourceList.getItems().size(), is(queryResult.documentLinks.size()));
      for (int i = 0; i < queryResult.documentCount; i++) {
        ExampleData document = Utils.fromJson(queryResult.documents.get(queryResult.documentLinks.get(i)),
            ExampleData.class);

        assertThat(resourceList.getItems().get(i).name, is(document.name));
      }
    }

    private ServiceDocumentQueryResult generateQueryResultData() {
      ServiceDocumentQueryResult queryResult = new ServiceDocumentQueryResult();

      final long documentCount = 100;
      queryResult.documentCount = documentCount;
      queryResult.documents = new HashMap<>();

      for (int i = 0; i < documentCount; i++) {
        String documentLink = UUID.randomUUID().toString();

        ExampleData exampleData = new ExampleData();
        exampleData.name = "document" + i;

        queryResult.documentLinks.add(documentLink);
        queryResult.documents.put(documentLink, Utils.toJson(exampleData));
      }

      queryResult.nextPageLink = UUID.randomUUID().toString();
      queryResult.prevPageLink = UUID.randomUUID().toString();

      return queryResult;
    }
  }

  /**
   * Tests for formalizePageLinks method.
   */
  public static class TestFormalizePageLinks {

    @Test
    public void testPageLinksNull() {
      String route = "/tasks";
      ResourceList<Object> resourceList = DataTypeConversionUtils.formalizePageLinks(
          new ResourceList<>(Collections.emptyList(), null, null), route);

      assertThat(resourceList.getNextPageLink(), nullValue());
      assertThat(resourceList.getPreviousPageLink(), nullValue());
    }
    @Test
    public void testNextPageLinkWithValue() {
      String route = "/tasks";
      String pageLink = UUID.randomUUID().toString();
      ResourceList<Object> resourceList = DataTypeConversionUtils.formalizePageLinks(
          new ResourceList<>(Collections.emptyList(), pageLink, null), route);

      assertThat(resourceList.getNextPageLink().startsWith(route + "?pageLink="), is(true));
      assertThat(resourceList.getPreviousPageLink(), nullValue());
    }
    @Test
    public void testPrevPageLinkWithValue() {
      String route = "/tasks";
      String pageLink = UUID.randomUUID().toString();
      ResourceList<Object> resourceList = DataTypeConversionUtils.formalizePageLinks(
          new ResourceList<>(Collections.emptyList(), null, pageLink), route);

      assertThat(resourceList.getNextPageLink(), nullValue());
      assertThat(resourceList.getPreviousPageLink().startsWith(route + "?pageLink="), is(true));
    }
    @Test
    public void testBothPageLinksWithValues() {
      String route = "/tasks";
      String pageLink1 = UUID.randomUUID().toString();
      String pageLink2 = UUID.randomUUID().toString();
      ResourceList<Object> resourceList = DataTypeConversionUtils.formalizePageLinks(
          new ResourceList<>(Collections.emptyList(), pageLink1, pageLink2), route);

      assertThat(resourceList.getNextPageLink().startsWith(route + "?pageLink="), is(true));
      assertThat(resourceList.getPreviousPageLink().startsWith(route + "?pageLink="), is(true));
    }
  }
}
