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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DiskReader reads a disk from an NFC server.
 */
public class DiskReader implements AutoCloseable {
  private static final Logger logger = LoggerFactory.getLogger(NfcClient.class);

  private final NfcClient nfcClient;
  private long capacitySectors;
  private Map<String, String> ddb;
  private ByteBuffer msgBuffer;
  private ByteBuffer hdrBuffer;
  private int frameBytesRemaining;
  private long nextSector = 0;
  private int pendingDataSectors = 0;
  private int pendingZeroSectors = 0;

  public DiskReader(NfcClient nfcClient) {
    this.nfcClient = nfcClient;
    this.ddb = new LinkedHashMap<>();
    this.hdrBuffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
  }

  public void init() throws IOException {
    msgBuffer = nfcClient.readNfcResponse();
    nfcClient.validateReplyCode(msgBuffer, NfcClient.NFC_FILE_PUT);
    int type = msgBuffer.getInt();
        /*int conversionFlags =*/
    msgBuffer.getInt();
    int pathLen = msgBuffer.getInt();
    long fileSize = msgBuffer.getLong();
        /*long spaceRequired =*/
    msgBuffer.getLong();

    capacitySectors = fileSize / NfcClient.SECTOR_SIZE;
    if (type != NfcClient.NFC_DISK && type != NfcClient.NFC_DELTA_DISK) {
      throw new IOException("Invalid file type in NFC_FILE_PUT response");
    }
    if (type == NfcClient.NFC_DELTA_DISK) {
      logger.debug("Server is sending disk in delta mode");
    }

        /*String path =*/
    nfcClient.readCString(pathLen);

    // Read first data message (should be metadata)
    readIncoming();
  }

  public long getCapacitySectors() {
    return capacitySectors;
  }

  public Map<String, String> getDDB() {
    return ddb;
  }

  /**
   * Read next sector from stream.
   *
   * @param expectedSector [in] expected sector number
   * @param data           [in/out] buffer to hold sector data
   * @param dataOffset     [in] offset in data buffer to write sector data
   * @return true if the sector has data, false if it is empty (all 0)
   */
  public boolean getNextSector(long expectedSector, byte[] data, int dataOffset) throws IOException {
    if (expectedSector != nextSector) {
      logger.error("NFC error: Unexpected sector in stream: Expected {}, was {}", expectedSector, nextSector);
      throw new IOException("NFC error: Unexpected sector in stream");
    }

    // If we read a RLE zero-encoded block, return a zero block
    if (pendingZeroSectors > 0) {
      pendingZeroSectors--;
      nextSector++;
      return false;
    }

    if (pendingDataSectors > 0) {
      pendingDataSectors--;
      nextSector++;
      readSector(data, dataOffset);
      return true;
    }

    assert pendingZeroSectors == 0;
    assert pendingDataSectors == 0;

    // Read RLE header
    readRLEHeader();
    // We should now have data buffered, try again
    return getNextSector(expectedSector, data, dataOffset);
  }

  private void readRLEHeader() throws IOException {
    if (frameBytesRemaining == 0) {
      readIncoming();
    }
    hdrBuffer.clear();
    readFromFrame(hdrBuffer);
    if (hdrBuffer.hasRemaining()) {
      logger.error("NFC error: Unexpected end of stream while reading RLE header");
      throw new IOException("Unexpected end of stream");
    }
    hdrBuffer.flip();
    int magic = hdrBuffer.getInt();
    int value = hdrBuffer.getInt();
    if (magic != NfcClient.FILE_DSK_RLE_HDR_MAGIC) {
      logger.error("NFC Error: Invalid magic, expected RLE header, got {}", magic);
      throw new IOException("NFC Error: Invalid RLE header");
    }
    int numSectors = value & NfcClient.RLE_MAX_COUNT;
    boolean isZeroBlocks = !(value > NfcClient.RLE_MAX_COUNT);

    if (isZeroBlocks) {
      pendingZeroSectors = numSectors;
    } else {
      pendingDataSectors = numSectors;
    }
  }

  private void readSector(byte[] data, int offset) throws IOException {
    ByteBuffer buffer = ByteBuffer.wrap(data, offset, SparseUtil.DISKLIB_SECTOR_SIZE);

    readFromFrame(buffer);
    if (buffer.hasRemaining()) {
      logger.error("NFC error: Unexpected end of stream while reading sector");
      throw new IOException("Unexpected end of stream");
    }
  }

  @Override
  public void close() throws IOException {
  }

  private void readIncoming() throws IOException {
    do {
      nfcClient.readNfcResponse(msgBuffer);
      nfcClient.validateReplyCode(msgBuffer, NfcClient.NFC_FILE_DATA);
      hdrBuffer.clear();
      nfcClient.readFully(hdrBuffer);
      hdrBuffer.flip();
      int fileDataHeader = hdrBuffer.getInt();
      if (NfcClient.FILE_DATA_HDR_MAGIC != fileDataHeader) {
        logger.error("Invalid NFC message: Expected FILE_DATA, got {}", fileDataHeader);
        nfcClient.abort();
        throw new IOException("NFC protocol violation");
      }
      frameBytesRemaining = hdrBuffer.getInt();
      // Read file data header, parse all metadata sections
      readDiskHeader();
    } while (frameBytesRemaining == 0);
    // No more metadata. Actual file data present in frame.
  }

  private void readDiskHeader() throws IOException {
    hdrBuffer.clear();
    readFromFrame(hdrBuffer);
    hdrBuffer.flip();
    int fileDskHeader = hdrBuffer.getInt();
    if (NfcClient.FILE_DSK_HDR_MAGIC != fileDskHeader) {
      logger.error("Invalid FILE_DATA message: Expected disk header, got {}", fileDskHeader);
      nfcClient.abort();
      throw new IOException("NFC protocol violation");
    }
    int isMetadata = hdrBuffer.getInt();
    if (isMetadata != 0) {
      while (frameBytesRemaining > 0 && readDdbEntry()) {};
    }
  }

  private boolean readDdbEntry() throws IOException {
    ByteBuffer ddbHeader = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
    readFromFrame(ddbHeader);
    ddbHeader.flip();
    int magic = ddbHeader.getInt();
    int keyBytes = ddbHeader.getInt();
    int valueBytes = ddbHeader.getInt();
    if (keyBytes == 0) {
      return false;
    }
    if (NfcClient.FILE_DSK_DDB_ENTRY_MAGIC != magic) {
      logger.error("NFC error: Expected DDB entry magic, got {}", magic);
      nfcClient.abort();
      throw new IOException("NFC protocol violation");
    }
    String key = readCStringFromFrame(keyBytes);
    String value = readCStringFromFrame(valueBytes);
    logger.trace("DDB entry: {} => {}", key, value);
    ddb.put(key, value);
    return true;
  }

  private void readFromFrame(ByteBuffer buffer) throws IOException {
    int bytesToRead = buffer.remaining();
    if (frameBytesRemaining < bytesToRead) {
      logger.debug("Insufficient bytes remaining in NFC message: {} < {}",
          frameBytesRemaining, bytesToRead);
      throw new IOException("NFC protocol violation: Insufficient bytes in current message");
    }
    nfcClient.readFully(buffer);
    frameBytesRemaining -= bytesToRead;
  }

  private String readCStringFromFrame(int length) throws IOException {
    if (length <= 0) {
      return null;
    }
    ByteBuffer stringBytes = ByteBuffer.allocate(length);
    readFromFrame(stringBytes);
    return nfcClient.stringFromCString(stringBytes);
  }
}
