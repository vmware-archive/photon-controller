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

package com.vmware.transfer.nfc;

import com.vmware.transfer.streamVmdk.StreamVmdkReader;

import org.mockito.InOrder;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.annotations.Test;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tests {@link com.vmware.transfer.nfc.NfcClient}.
 */
public class NfcClientTest extends PowerMockTestCase {

  @Test
  public void testPutStreamOptimizedDisk() throws Exception {
    NfcClient nfcClient = spy(new NfcClient());
    StreamVmdkReader disk = mock(StreamVmdkReader.class);
    DiskWriter writer = mock(DiskWriter.class);

    InputStream inputStream = new ByteArrayInputStream("test content".getBytes());
    doReturn(disk).when(nfcClient).getStreamVmdkReader(inputStream);
    when(disk.getAdapterType()).thenReturn("buslogic");
    when(disk.getCapacityInSectors()).thenReturn(65536L);
    Map<String, String> ddb = new LinkedHashMap<>();
    when(disk.getDdb()).thenReturn(ddb);
    doReturn(writer).when(nfcClient).putDisk("image destination path", "buslogic", 65536L);

    byte[] grain = new byte[disk.getGrainSize() * SparseUtil.DISKLIB_SECTOR_SIZE];
    when(disk.getNextGrain(grain)).thenReturn(65536, -1);
    when(disk.getCurrentLba()).thenReturn(0L);

    long bytes = nfcClient.putStreamOptimizedDisk("image destination path", inputStream);
    assertThat(bytes, is(33554432L));

    InOrder inOrder = inOrder(writer, nfcClient);
    inOrder.verify(writer).writeDdb(ddb);
    inOrder.verify(writer, times(128)).writeGrain(anyLong(), any(byte[].class), anyInt());
    inOrder.verify(writer).finalizeWrite();
    inOrder.verify(nfcClient).close();
    inOrder.verify(writer).close();
    inOrder.verify(nfcClient).abort();
  }
}
