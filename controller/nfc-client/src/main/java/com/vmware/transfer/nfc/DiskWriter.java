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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

/**
 * DiskWriter writes a disk to an NFC server using PUT_FILE. This implementation
 * is lifted from the C# VI client, and could do with a little refactoring. It
 * performs well in tests, though.
 */
public class DiskWriter implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(DiskWriter.class);

  private final NfcClient nfcClient;
  private ByteBuffer fileData;
  private long zeroSectors = 0;
  private long nextLba = 0;
  private boolean isDirty = false;
  private long capacity;

  public DiskWriter(NfcClient nfcClient, long capacity) {
    this.nfcClient = nfcClient;
    this.capacity = capacity;
    fileData = ByteBuffer.allocate(NfcClient.MAX_PAYLOAD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    resetFileData();
  }

  /**
   * Write disk DB.
   */
  public void writeDdb(Map<String, String> ddb) throws IOException {
    if (ddb.isEmpty()) {
      return;
    }
    flush(); // In case we have buffered file data
    writeNfcFileDskHdr(true);
    for (Map.Entry<String, String> entry : ddb.entrySet()) {
      writeNfcFileDskDDBEntry(entry.getKey(), entry.getValue());
    }
    writeNfcFileDskDDBEntry(null, null); // List terminator
    flush(); // Send DDB entries, prepare for file data
  }

  /**
   * Write a data grain. Will be buffered until we have a full message of grains.
   */
  public void writeGrain(long lba, byte[] data, int offset) throws IOException {
    assert data.length - offset >= NfcClient.SECTOR_SIZE;
    if (lba < nextLba) {
      throw new RuntimeException("Sectors are out of order");
    }

    zeroSectors += lba - nextLba;
    nextLba = lba + 1;
    if (isZero(data, offset, NfcClient.SECTOR_SIZE)) {
      zeroSectors++;
    } else {
      outputDataSector(data, offset);
    }
  }

  private void writeNfcFileDskDDBEntry(String key, String value) {
    assert isDirty;
    fileData.putInt(NfcClient.FILE_DSK_DDB_ENTRY_MAGIC);
    if (key == null) {
      // End of list marker
      assert value == null;
      fileData.putInt(0);
      fileData.putInt(0);
      return;
    }
    ByteBuffer keyBytes = this.nfcClient.stringToCString(key);
    ByteBuffer valueBytes = this.nfcClient.stringToCString(value);
    fileData.putInt(keyBytes.remaining());
    fileData.putInt(valueBytes.remaining());
    fileData.put(keyBytes);
    fileData.put(valueBytes);
  }

  private void outputBufferedZeroSectors() throws IOException {
    if (zeroSectors == 0) {
      return;
    }
    while (zeroSectors > NfcClient.RLE_MAX_COUNT) {
      writeNfcFileDskRLEHdr(NfcClient.RLE_MAX_COUNT, true);
      zeroSectors -= NfcClient.RLE_MAX_COUNT;
      flushIfAtLimit();
    }
    if (zeroSectors > 0) {
      writeNfcFileDskRLEHdr((int) zeroSectors, true);
      zeroSectors = 0;
      flushIfAtLimit();
    }
  }

  private void outputDataSector(byte[] data, int offset) throws IOException {
    outputBufferedZeroSectors();
    writeNfcFileDskRLEHdr(1, false);
    fileData.put(data, offset, NfcClient.SECTOR_SIZE);
    flushIfAtLimit();
  }

  private void flushIfAtLimit() throws IOException {
    // Flush if there isn't room for another data section
    if (fileData.remaining() < NfcClient.SECTOR_SIZE + 2 * 4) {
      flush();
    }
  }

  private void flush() throws IOException {
    if (isDirty) {
      writeFileData();
      resetFileData();
    }
  }

  private void resetFileData() {
    fileData.clear();
    fileData.putInt(NfcClient.FILE_DATA_HDR_MAGIC);
    fileData.putInt(0); // Will be overwritten with correct size later
    isDirty = false;
  }

  private boolean isZero(byte[] data, int offset, int length) {
    for (int i = offset; i < offset + length; ++i) {
      if (data[i] != 0) {
        return false;
      }
    }
    return true;
  }

  private void writeNfcFileDskHdr(boolean isMetadata) {
    // This is the first section in a FILE_DATA message
    assert !isDirty;
    assert fileData.position() == 8; // FILE_DATA message header
    fileData.putInt(NfcClient.FILE_DSK_HDR_MAGIC);
    fileData.putInt(isMetadata ? 1 : 0);
    isDirty = true;
  }

  private void writeNfcFileDskRLEHdr(int sectors, boolean areZeroBlocks) {
    if (!isDirty) {
      writeNfcFileDskHdr(false);
    }
    fileData.putInt(NfcClient.FILE_DSK_RLE_HDR_MAGIC);
    assert sectors <= NfcClient.RLE_MAX_COUNT;
    // MSB: 1 = data, 0 = RLE
    if (!areZeroBlocks) {
      sectors |= NfcClient.RLE_NON_ZERO_FLAG;
    }
    fileData.putInt(sectors);
  }

  private void writeFileData() throws IOException {
    ByteBuffer msg = this.nfcClient.newNfcMsg(NfcClient.NFC_FILE_DATA);
    this.nfcClient.sendNfcMsg(msg);
    // Patch in size of file data
    fileData.putInt(4, fileData.position() - 2 * 4);
    fileData.flip();
    this.nfcClient.writeFully(fileData);
  }

  public void finalizeWrite() throws IOException {
    if (fileData == null) {
      // Already closed
      return;
    }
    try {
      assert nextLba <= capacity;
      zeroSectors += capacity - nextLba;
      outputBufferedZeroSectors();
      flush(); // This is a no-op if no pending data
      // Send zero-length FILE_DATA to mark end of stream
      writeFileData();
      fileData = null;
      // Receive completion message from server
      ByteBuffer reply = this.nfcClient.readNfcResponse();
      this.nfcClient.validateReplyCode(reply, NfcClient.NFC_PUTFILE_DONE);
    } catch (IOException e) {
      logger.debug("Error closing PUTFILE session: {}", e.getMessage(), e);
      throw e;
    }
  }

  @Override
  public void close() throws IOException {
    if (fileData != null) {
      // Not closed gracefully, abort NFC client
      nfcClient.abort();
    }
  }
}
