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

package com.vmware.photon.controller.apife.lib;

import com.vmware.photon.controller.api.model.QuotaUnit;
import com.vmware.photon.controller.apife.entities.QuotaLineItemEntity;

import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests {@link QuotaLineItemEntity}.
 */
public class QuotaLineItemTest {

  /**
   * The add test tests all of the unit combinations to ensure
   * that that result unit is correct in all scenarios. The other
   * math tests (Subtract for instance) does not test this breadth
   * since the code for add and subtract is nearly identical).
   */
  @Test
  public void testSimpleAdd() {
    QuotaLineItemEntity x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    QuotaLineItemEntity y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    QuotaLineItemEntity sum = x.add(y);
    assertThat("1 + 1 = 2",
        sum.getValue(),
        is(2.0));
    assertThat("B + B = B",
        sum.getUnit(),
        is(QuotaUnit.B));

    // KB + KB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    sum = x.add(y);
    assertThat("1 KB + 1 KB = 2 KB",
        sum.getValue(),
        is(2.0));
    assertThat("KB + KB = KB",
        sum.getUnit(),
        is(QuotaUnit.KB));

    // B + KB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    sum = x.add(y);
    assertThat("1 B + 1 KB = 1025 B",
        sum.getValue(),
        is(1025.0));
    assertThat("B + KB = B",
        sum.getUnit(),
        is(QuotaUnit.B));

    // KB + B
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 512.0, QuotaUnit.B);
    sum = x.add(y);
    assertThat("1 KB + 512 B = 1.5 KB",
        sum.getValue(),
        is(1.5));
    assertThat("KB + B = KB",
        sum.getUnit(),
        is(QuotaUnit.KB));

    // KB + COUNT
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 512.0, QuotaUnit.COUNT);
    sum = x.add(y);
    assertThat("1 KB + 512 COUNT = 1.5 KB",
        sum.getValue(),
        is(1.5));
    assertThat("KB + COUNT = KB",
        sum.getUnit(),
        is(QuotaUnit.KB));

    // COUNT + KB
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.KB);
    sum = x.add(y);
    assertThat("1024 COUNT + 2 KB = 3072 COUNT",
        sum.getValue(),
        is(3072.0));
    assertThat("COUNT + KB = COUNT",
        sum.getUnit(),
        is(QuotaUnit.COUNT));

    // MB + MB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    sum = x.add(y);
    assertThat("1 MB + 1 MB = 2 MB",
        sum.getValue(),
        is(2.0));
    assertThat("MB + MB = MB",
        sum.getUnit(),
        is(QuotaUnit.MB));

    // B + MB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    sum = x.add(y);
    assertThat("1 B + 1 MB = 1048577 B",
        sum.getValue(),
        is(1048577.0));
    assertThat("B + MB = B",
        sum.getUnit(),
        is(QuotaUnit.B));

    // MB + B
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 524288.0, QuotaUnit.B);
    sum = x.add(y);
    assertThat("1 MB + 524288 B = 1.5 MB",
        sum.getValue(),
        is(1.5));
    assertThat("MB + B = MB",
        sum.getUnit(),
        is(QuotaUnit.MB));

    // MB + COUNT
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 524288.0, QuotaUnit.COUNT);
    sum = x.add(y);
    assertThat("1 MB + 524288 COUNT = 1.5 MB",
        sum.getValue(),
        is(1.5));
    assertThat("MB + COUNT = MB",
        sum.getUnit(),
        is(QuotaUnit.MB));

    // COUNT + MB
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.MB);
    sum = x.add(y);
    assertThat("1024 COUNT + 2 MB = 2098176 COUNT",
        sum.getValue(),
        is(2098176.0));
    assertThat("COUNT + MB = COUNT",
        sum.getUnit(),
        is(QuotaUnit.COUNT));

    // KB + MB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    sum = x.add(y);
    assertThat("1 KB + 1 MB = 1049600 B",
        sum.getValue(),
        is(1025.0));
    assertThat("KB + MB = KB",
        sum.getUnit(),
        is(QuotaUnit.KB));

    // MB + KB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 512.0, QuotaUnit.KB);
    sum = x.add(y);
    assertThat("1 MB + 512 KB = 1.5 MB",
        sum.getValue(),
        is(1.5));
    assertThat("MB + B = MB",
        sum.getUnit(),
        is(QuotaUnit.MB));

    // GB + GB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    sum = x.add(y);
    assertThat("1 GB + 1 GB = 2 GB",
        sum.getValue(),
        is(2.0));
    assertThat("GB + GB = GB",
        sum.getUnit(),
        is(QuotaUnit.GB));

    // GB + MB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 512.0, QuotaUnit.MB);
    sum = x.add(y);
    assertThat("1 GB + 512 MB = 1.5 GB",
        sum.getValue(),
        is(1.5));
    assertThat("GB + MB = GB",
        sum.getUnit(),
        is(QuotaUnit.GB));

    // GB + KB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 131072.0, QuotaUnit.KB);
    sum = x.add(y);
    assertThat("1 GB + 131072 KB = 1.125 GB",
        sum.getValue(),
        is(1.125));
    assertThat("GB + MB = GB",
        sum.getUnit(),
        is(QuotaUnit.GB));

    // GB + B
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 134217728.0, QuotaUnit.B);
    sum = x.add(y);
    assertThat("1 GB + 134217728 B = 1.125 GB",
        sum.getValue(),
        is(1.125));
    assertThat("GB + B = GB",
        sum.getUnit(),
        is(QuotaUnit.GB));

    // GB + COUNT
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 134217728.0, QuotaUnit.COUNT);
    sum = x.add(y);
    assertThat("1 GB + 134217728 COUNT = 1.125 GB",
        sum.getValue(),
        is(1.125));
    assertThat("GB + COUNT = GB",
        sum.getUnit(),
        is(QuotaUnit.GB));

    // MB + GB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    sum = x.add(y);
    assertThat("1 MB + 1 GB = 1025 MB",
        sum.getValue(),
        is(1025.0));
    assertThat("MB + GB = MB",
        sum.getUnit(),
        is(QuotaUnit.MB));

    // KB + GB
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    sum = x.add(y);
    assertThat("1024 KB + 1 GB = 1025 KB",
        sum.getValue(),
        is(1049600.0));
    assertThat("KB + GB = KB",
        sum.getUnit(),
        is(QuotaUnit.KB));

    // B + GB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    sum = x.add(y);
    assertThat("1 B + 1 GB = 1073741825 B",
        sum.getValue(),
        is(1073741825.0));
    assertThat("B + GB = B",
        sum.getUnit(),
        is(QuotaUnit.B));

    // COUNT + GB
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    sum = x.add(y);
    assertThat("1 COUNT + 1 GB = 1073741825 COUNT",
        sum.getValue(),
        is(1073741825.0));
    assertThat("B + GB = COUNT",
        sum.getUnit(),
        is(QuotaUnit.COUNT));
  }

  @Test
  public void testSimpleSubtract() {

    // same units 0
    QuotaLineItemEntity x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    QuotaLineItemEntity y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    QuotaLineItemEntity diff = x.subtract(y);
    assertThat("1 - 1 = 0",
        diff.getValue(),
        is(0.0));

    // same units, positive
    x = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    diff = x.subtract(y);
    assertThat("2 KB - 1 KB = 1 KB",
        diff.getValue(),
        is(1.0));

    // same units, negative
    x = new QuotaLineItemEntity("foo", 12.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 14.0, QuotaUnit.GB);
    diff = x.subtract(y);
    assertThat("12 GB - 14 GB = -2 GB",
        diff.getValue(),
        is(-2.0));

    // mismatched units 0
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.KB);
    diff = x.subtract(y);
    assertThat("1024 B - 1 KB = 0 B",
        diff.getValue(),
        is(0.0));

    // mismatched units positive
    x = new QuotaLineItemEntity("foo", 3.0, QuotaUnit.GB);
    y = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.MB);
    diff = x.subtract(y);
    assertThat("3 GB - 1024 MB = 2.0 GB",
        diff.getValue(),
        is(2.0));

    // mismatched units negative
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.KB);
    diff = x.subtract(y);
    assertThat("1024 B - 2 KB = -1024 B",
        diff.getValue(),
        is(-1024.0));
  }

  @Test
  public void testCompare() {

    // same units: x == y
    QuotaLineItemEntity x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    QuotaLineItemEntity y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    assertThat("x == y",
        x.compareTo(y),
        is(0));

    // same units: x < y
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.B);
    assertThat("x < y",
        x.compareTo(y),
        is(-1));

    // same units: x > y
    x = new QuotaLineItemEntity("foo", 3.0, QuotaUnit.B);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.B);
    assertThat("x > y",
        x.compareTo(y),
        is(1));

    // using (x.compareTo(y) <op> 0) idom
    // for: <, ==, >, >=, !=, <=

    // x < y
    x = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 101.0, QuotaUnit.COUNT);
    assertThat("x < y",
        (x.compareTo(y) < 0),
        is(true));

    // x == y
    x = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    assertThat("x == y",
        (x.compareTo(y) == 0),
        is(true));

    // x > y
    x = new QuotaLineItemEntity("foo", 100.9, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    assertThat("x > y",
        (x.compareTo(y) > 0),
        is(true));

    // x >= y
    x = new QuotaLineItemEntity("foo", 100.9, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    assertThat("x >= y (p0)",
        (x.compareTo(y) >= 0),
        is(true));
    x = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    assertThat("x >= y (p1)",
        (x.compareTo(y) >= 0),
        is(true));

    // x != y
    x = new QuotaLineItemEntity("foo", 100.00125, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 100.0, QuotaUnit.COUNT);
    assertThat("x != y",
        (x.compareTo(y) != 0),
        is(true));

    // x <= y
    x = new QuotaLineItemEntity("foo", 39.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 327.0, QuotaUnit.COUNT);
    assertThat("x <= y (p0)",
        (x.compareTo(y) <= 0),
        is(true));
    x = new QuotaLineItemEntity("foo", 327.0, QuotaUnit.COUNT);
    y = new QuotaLineItemEntity("foo", 327.0, QuotaUnit.COUNT);
    assertThat("x <= y (p1)",
        (x.compareTo(y) <= 0),
        is(true));

    // mixed unit variants
    // m: x < y
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1025.0, QuotaUnit.KB);
    assertThat("m: x < y",
        (x.compareTo(y) < 0),
        is(true));

    // m: x == y
    x = new QuotaLineItemEntity("foo", 2048.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 2.0, QuotaUnit.GB);
    assertThat("m: x == y",
        (x.compareTo(y) == 0),
        is(true));

    // m: x > y
    x = new QuotaLineItemEntity("foo", 1024.1, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.GB);
    assertThat("m: x > y",
        (x.compareTo(y) > 0),
        is(true));

    // m: x >= y
    x = new QuotaLineItemEntity("foo", 1024.1, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    assertThat("m: x >= y (p0)",
        (x.compareTo(y) >= 0),
        is(true));
    x = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.KB);
    y = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    assertThat("m: x >= y (p1)",
        (x.compareTo(y) >= 0),
        is(true));

    // m: x != y
    x = new QuotaLineItemEntity("foo", 1024.1, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1024.1, QuotaUnit.KB);
    assertThat("m: x != y",
        (x.compareTo(y) != 0),
        is(true));

    // m: x <= y
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1025.0, QuotaUnit.KB);
    assertThat("m: x <= y (p0)",
        (x.compareTo(y) <= 0),
        is(true));
    x = new QuotaLineItemEntity("foo", 1.0, QuotaUnit.MB);
    y = new QuotaLineItemEntity("foo", 1024.0, QuotaUnit.KB);
    assertThat("m: x <= y (p1)",
        (x.compareTo(y) <= 0),
        is(true));
  }
}
