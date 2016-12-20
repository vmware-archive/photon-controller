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
import com.vmware.transfer.streamVmdk.VmdkFormatException;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static com.google.common.base.Preconditions.checkArgument;

import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/**
 * NFC Client. Used for transferring files and disks to/from datastores using NFC.
 */
@SuppressWarnings("unused") // Not all messages are implemented yet
public class NfcClient implements AutoCloseable {
  // NFC FILE_DATA max size (including 256 byte header)
  public static final int MAX_XFER_SIZE = 256 * 1024;

  // Constants below are lifted from nfclib on vmkernel-main
  static final int NFC_MESSAGE_SIZE = 264;
  static final int MAX_PAYLOAD_SIZE = MAX_XFER_SIZE - NFC_MESSAGE_SIZE;
  // NFC messages
  static final int NFC_HANDSHAKE = 0;
  static final int NFC_FILE_PUT = 1;
  static final int NFC_FILE_GET = 2;
  static final int NFC_FILE_COMPLETE = 3;
  static final int NFC_SESSION_COMPLETE = 4;
  static final int NFC_ERROR_OLD = 5; // Not used
  static final int NFC_RATE_CONTROL = 6;
  static final int NFC_FILE_DATA = 7;
  static final int NFC_PING = 8;
  static final int NFC_PUTFILE_DONE = 27;
  static final int SECTOR_SIZE = 512;
  static final int FILE_DSK_HDR_MAGIC = 0x87654321;
  static final int FILE_DSK_RLE_HDR_MAGIC = 0x12345678;
  static final int FILE_DSK_DDB_ENTRY_MAGIC = 0xdeadbeef;
  static final int FILE_DATA_HDR_MAGIC = 0xabcdefab;
  static final int RLE_NON_ZERO_FLAG = 1 << 15;
  static final int RLE_MAX_COUNT = RLE_NON_ZERO_FLAG - 1;
  static final int NFC_RAW = 0;
  static final int NFC_TEXT = 1;
  static final int NFC_DISK = 2;
  static final int NFC_DELTA_DISK = 3;
  static final int NFC_DIGEST = 4;
  static final int NFC_DELTA_DIGEST = 5;
  static final int NFC_RDM_DISK = 6;
  static final int NFC_OBJECT = 7;
  // Flags for file management
  static final int NFC_FILE_FORCE = 1 << 0;
  static final int NFC_FILE_CREATEDIRHIER = 1 << 1;
  private static final Logger logger = LoggerFactory.getLogger(NfcClient.class);
  // Newer nfc message types
  private static final int NFC_ERROR = 20;
  private static final int NFC_FSSRVR_OPEN = 21;
  private static final int NFC_FSSRVR_DISKGEO = 22;
  private static final int NFC_FSSRVR_IO = 23;
  private static final int NFC_FSSRVR_CLOSE = 24;
  private static final int NFC_PUTFILESINFO = 25;
  private static final int NFC_GETFILESINFO = 26;
  private static final int NFC_FSSRVR_DDBENUM = 28;
  private static final int NFC_FSSRVR_DDBGET = 29;
  private static final int NFC_FSSRVR_DDBSET = 30;
  private static final int NFC_FILE_DELETE = 31;
  private static final int NFC_FILE_RENAME = 32;
  private static final int NFC_FILE_COPY = 33;
  private static final int NFC_FILE_CREATEDIR = 34;
  private static final int NFC_FILEOP_STATUS = 36;   /* sent in response to prior 4 msgs */
  private static final int NFC_ENUM_DISKEXTS = 37;
  private static final int NFC_FILENAME_LIST = 38;
  private static final int NFC_FSSRVR_MULTIIO = 39;
  private static final int NFC_FSSRVR_ASM_MAP = 40;
  private static final int NFC_FSSRVR_CHM_MAP = 41;
  private static final int NFC_FSSRVR_DDBREMOVE = 42;
  private static final int NFC_CLIENTRANDOM = 43;
  private static final int NFC_FSSRVR_UNMAP = 44;
  private static final int NFC_FSSRVR_CKSM_XTNT = 45;
  private static final int NFC_FSSRVR_IO_EX = 46;
  private static final int NFC_FSSRVR_MULTIIO_EX = 47;
  private static final int NFC_FSSRVR_SYNC = 48;
  // Conversion flags
  private static final int CONV_DISK_VMFS = 1 << 3;
  private static final int CONV_CREATE_OVERWRITE = 1 << 4;
  private static final int CONV_DISK_THIN = 1 << 7;
  private static final int CONV_DISK_LSILOGIC = 1 << 8;
  private static final int CONV_DISK_IDE = 1 << 13;
  private Socket socket;
  private ReadableByteChannel input;
  private WritableByteChannel output;

  @VisibleForTesting
  protected NfcClient() {
  }

  public NfcClient(HostServiceTicket ticket, int timeoutMs) throws IOException {
    checkArgument(ticket != null, "Null ticket passed to NfcClient().");
    logger.debug("Connecting to {} on {}:{}", ticket.getService(), ticket.getHost(),
        ticket.getPort());
    socket = Authd.connect(ticket, timeoutMs);
    input = Channels.newChannel(socket.getInputStream());
    output = Channels.newChannel(socket.getOutputStream());
    // TODO(jandersen): Use SSL if session ~= s/ssl/ ("vpxa-nfcssl", for example)
  }


  /**
   * Open a PUT_FILE session to upload a raw file. The difference of this method to putFile is close the OutputStream
   * will automatically close nfc client. This is useful when the nfc client is created for a single operation.
   */
  public NfcFileOutputStream putFileAutoClose(String dsPath, long fileSize) throws IOException {
    return this.putFile(dsPath, fileSize, true);
  }

  /**
   * Open a PUT_FILE session to upload a raw file.
   */
  public NfcFileOutputStream putFile(String dsPath, long fileSize) throws IOException {
    return this.putFile(dsPath, fileSize, false);
  }

  protected NfcFileOutputStream putFile(String dsPath, long fileSize, boolean autoClose) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_PUT);
    msg.putInt(NFC_RAW);
    int conversionFlags = CONV_CREATE_OVERWRITE;
    msg.putInt(conversionFlags);
    msg.putInt(dsPathBytes.remaining());
    msg.putLong(fileSize); // file size
    msg.putLong(fileSize); // space required
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    return new NfcFileOutputStream(this, autoClose);
  }

  /**
   * Open a PUT_FILE session to upload a disk.
   */
  public DiskWriter putDisk(String dsPath, String adapterType, long capacity) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_PUT);
    msg.putInt(NFC_DISK);
    int conversionFlags = CONV_DISK_VMFS | CONV_DISK_THIN | CONV_CREATE_OVERWRITE;
    if ("lsilogic".equalsIgnoreCase(adapterType)) {
      conversionFlags |= CONV_DISK_LSILOGIC;
    } else if ("ide".equalsIgnoreCase(adapterType)) {
      conversionFlags |= CONV_DISK_IDE;
    }
    msg.putInt(conversionFlags);
    msg.putInt(dsPathBytes.remaining());
    msg.putLong(capacity * SECTOR_SIZE); // capacity
    msg.putLong(capacity * SECTOR_SIZE); // space required
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    return new DiskWriter(this, capacity);
  }

  /**
   * Upload image to remote datastore as a disk. If the image name exists in
   * the datastore, it will be overwritten. The only disk type than can be correctly
   * uploaded in this mode is streamOptimized.
   * <p/>
   * adapted from: https://opengrok.eng.vmware.com/source/xref/vdc-2015.perforce-shark.1700
   * /vdc-2015/src/transfer-svc/ts-main/src/main/java/com/vmware/transfer/impl/NfcEndpointImpl.java
   *
   * @param filePath    image datastore path
   * @param inputStream input stream of image
   * @return number of bytes uploaded
   */
  public long putStreamOptimizedDisk(
      String filePath,
      InputStream inputStream)
      throws IOException, VmdkFormatException {
    StreamVmdkReader disk = getStreamVmdkReader(inputStream);

    DiskWriter writer = null;
    try {
      writer = putDisk(filePath, disk.getAdapterType(), disk.getCapacityInSectors());
      writer.writeDdb(disk.getDdb());
      byte[] grain = new byte[disk.getGrainSize() * SparseUtil.DISKLIB_SECTOR_SIZE];
      int grainSize = disk.getNextGrain(grain);
      int grainCount = 0;
      while (grainSize >= 0) {
        grainCount++;
        if (0 == (grainCount % 1000)) {
          logger.debug("NfcClient putStreamOptimizedDisk for file {} preparing to write grain count/size {}/{}",
              filePath,
              grainCount,
              grainSize);
        }
        if (grainSize > 0) {
          long lba = disk.getCurrentLba();
          for (int offset = 0; offset < grainSize; offset += SparseUtil.DISKLIB_SECTOR_SIZE) {
            writer.writeGrain(lba++, grain, offset);
          }
        }
        grainSize = disk.getNextGrain(grain);
      }
      logger.debug("NfcClient putStreamOptimizedDisk for file {} write complete on grain count/size {}/{}", filePath,
          grainCount, grainSize);
      writer.finalizeWrite();
      close();
      return disk.getCapacityInSectors() * SparseUtil.DISKLIB_SECTOR_SIZE;
    } catch (SocketException e) {
      logger.error("Remote host closed connection while uploading to datastore (could be of out of space): {}", e);
      throw e;
    } finally {
      if (writer != null) {
        writer.close();
      }
      abort();
    }
  }

  /**
   * Open a GET_FILE session to download a raw file.
   */
  public NfcFileInputStream getFile(String dsPath) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_GET);
    msg.putInt(NFC_RAW);
    msg.putInt(dsPathBytes.remaining());
    msg.putInt(0); // conversion flags
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    NfcFileInputStream result = new NfcFileInputStream(this);
    result.init(); // read and parse NFC_FILE_PUT response message
    return result;
  }

  /**
   * Open a GET_FILE session to download a disk.
   */
  public DiskReader getDisk(String dsPath) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_GET);
    msg.putInt(NFC_DISK);
    msg.putInt(dsPathBytes.remaining());
    msg.putInt(0); // conversion flags
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    DiskReader result = new DiskReader(this);
    result.init(); // read and parse NFC_FILE_PUT response message
    return result;
  }

  public void mkdir(String dsPath) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_CREATEDIR);
    msg.putInt(dsPathBytes.remaining());
    msg.putInt(NFC_FILE_CREATEDIRHIER);
    msg.putShort((short) 1);
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    ByteBuffer reply = readNfcResponse();
    validateReplyCode(reply, NFC_FILEOP_STATUS);
    int errsize = reply.getInt();
    if (errsize > 0) {
      throw new IOException("Failed to create directory " + dsPath);
    }
  }

  public void delete(String dsPath) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FILE_DELETE);
    msg.putInt(dsPathBytes.remaining());
    msg.putInt(NFC_FILE_CREATEDIRHIER);
    msg.putShort((short) 1);
    sendNfcMsg(msg);
    writeFully(dsPathBytes);
    ByteBuffer reply = readNfcResponse();
    validateReplyCode(reply, NFC_FILEOP_STATUS);
    int errsize = reply.getInt();
    if (errsize > 0) {
      throw new IOException("Failed to delete " + dsPath);
    }
  }

  public void close() throws IOException {
    if (socket != null) {
      ByteBuffer msg = newNfcMsg(NFC_SESSION_COMPLETE);
      sendNfcMsg(msg);
      readNfcResponse(msg);
      socket.close();
      socket = null;
    }
  }

  public void abort() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        logger.debug("Error closing NFC connection: {}", e.getMessage(), e);
      }
      socket = null;
    }
  }

  /**
   * Read from channel until data buffer is full.
   */
  void readFully(ByteBuffer data) throws IOException {
    while (data.hasRemaining()) {
      input.read(data);
    }
  }

  /**
   * Write entire data buffer to channel.
   */
  void writeFully(ByteBuffer data) throws IOException {
    while (data.hasRemaining()) {
      output.write(data);
    }
  }

  ByteBuffer newNfcMsg(int msgType) {
    ByteBuffer msg = ByteBuffer.allocate(NFC_MESSAGE_SIZE);
    msg.order(ByteOrder.LITTLE_ENDIAN);
    msg.putInt(msgType);
    return msg;
  }

  void sendNfcMsg(ByteBuffer msg) throws IOException {
    byte[] b = msg.array();
    assert b.length == NFC_MESSAGE_SIZE;
    msg.rewind();
    writeFully(msg);
  }

  void readNfcResponse(ByteBuffer reply) throws IOException {
    assert reply.capacity() == NFC_MESSAGE_SIZE;
    assert reply.order() == ByteOrder.LITTLE_ENDIAN;
    reply.clear();
    readFully(reply);
    reply.flip();
  }

  ByteBuffer readNfcResponse() throws IOException {
    ByteBuffer reply = ByteBuffer.allocate(NFC_MESSAGE_SIZE).order(ByteOrder.LITTLE_ENDIAN);
    readNfcResponse(reply);
    return reply;
  }

  /**
   * Read NFC error from reply message.
   *
   * @param reply Reply message
   * @return String containing NFC error
   */
  private String readNfcErrorMsg(ByteBuffer reply) throws IOException {
    int errorType = reply.getInt();
    int errorCode = reply.getInt();
    int msgLen = reply.getInt();
    if (msgLen == 0) {
      return "";
    }
    ByteBuffer msgBuffer = ByteBuffer.allocate(msgLen);
    readFully(msgBuffer);
    String errorMsg = new String(msgBuffer.array(), 0, msgLen - 1, Charsets.US_ASCII);
    return String.format("NFC Error %d/%d: %s", errorType, errorCode, errorMsg);
  }

  void validateReplyCode(ByteBuffer reply, int expectedReplyCode) throws IOException {
    int code = reply.getInt();
    if (code == NFC_ERROR) {
      throw new IOException(readNfcErrorMsg(reply));
    }
    if (code != expectedReplyCode) {
      throw new IOException("Invalid NFC response code (" + code + "), expected " + expectedReplyCode);
    }
  }

  String readCString(final int length) throws IOException {
    if (length <= 0) {
      return null;
    }
    ByteBuffer stringBytes = ByteBuffer.allocate(length);
    readFully(stringBytes);
    return stringFromCString(stringBytes);
  }

  String stringFromCString(final ByteBuffer buffer) {
    buffer.flip();
    return new String(buffer.array(), 0, buffer.limit() - 1, Charsets.US_ASCII);
  }

  /**
   * Convert a String to ASCII byte array, including terminating 0.
   */
  ByteBuffer stringToCString(final String string) {
    byte[] stringBytes = string.getBytes(Charsets.US_ASCII);
    ByteBuffer result = ByteBuffer.allocate(stringBytes.length + 1);
    result.put(stringBytes).put((byte) 0);
    result.flip();
    return result;
  }

    /*
     * FSSRVR implementation not used right now, but will be if we implement resuming
     * for NFC transfers.
     */

  public void fssrvrOpenDisk(String dsPath) throws IOException {
    ByteBuffer dsPathBytes = stringToCString(dsPath);
    ByteBuffer msg = newNfcMsg(NFC_FSSRVR_OPEN);
    msg.putInt(dsPathBytes.remaining());
    msg.putInt(0x0A); // openFlags (=OPEN_PARENT | OPEN_LOCK)
    msg.put((byte) 0); // rawFile? (=FALSE)
    sendNfcMsg(msg);
    writeFully(dsPathBytes);

    ByteBuffer reply = readNfcResponse();
    validateReplyCode(reply, NFC_FSSRVR_DISKGEO);
    // TODO(jandersen): Read and validate disk geometry
  }

  public void fssrvrWriteDisk(long lba, byte[] data, int length) throws IOException {
    assert lba % 512 == 0;
    assert length % 512 == 0;
    assert length <= data.length;
    ByteBuffer msg = newNfcMsg(NFC_FSSRVR_IO);
    msg.putInt(1); // write
    msg.putLong(lba);
    msg.putInt(length);
    sendNfcMsg(msg);
    writeFully(ByteBuffer.wrap(data, 0, length));

    ByteBuffer reply = readNfcResponse();
    validateReplyCode(reply, NFC_FSSRVR_IO);
  }

  public void fssrvrCloseDisk() throws IOException {
    ByteBuffer msg = newNfcMsg(NFC_FSSRVR_CLOSE);
    sendNfcMsg(msg);
    ByteBuffer reply = readNfcResponse();
    validateReplyCode(reply, NFC_FSSRVR_CLOSE);
  }

  @VisibleForTesting
  protected StreamVmdkReader getStreamVmdkReader(InputStream inputStream)
      throws IOException, VmdkFormatException {
    try {
      return new StreamVmdkReader(inputStream);
    } catch (VmdkFormatException e) {
      logger.error("Unable to parse disk image: {}", e);
      throw e;
    }
  }
}
