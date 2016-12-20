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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

/**
 * Authd opens a connection to the NFC endpoint on a host.
 */
public class Authd {
  private static final Logger logger = LoggerFactory.getLogger(Authd.class);

  public static Socket connect(HostServiceTicket ticket, int timeoutMs) throws IOException {
    return connect(ticket.getHost(), ticket.getPort(), ticket.getSslThumbprint(),
        ticket.getService(), ticket.getSessionId(), timeoutMs);
  }

  // TODO(jandersen): Quick and dirty hack. Needs cleanup.
  public static Socket connect(String host, int port, String sslThumbprint,
                               String service, String session, int timeoutMs) throws IOException {
    // Open socket to the remote host
    Socket socket = new Socket();
    socket.setSoTimeout(timeoutMs);
    socket.setTcpNoDelay(true);
    socket.connect(new InetSocketAddress(host, port));

    try {
      DataInput in = new DataInputStream(socket.getInputStream());
      DataOutput out = new DataOutputStream(socket.getOutputStream());

      // Read greeting - should be 220 Hello
      String reply = in.readLine();
      int code = replyCode(reply);
      if (code != 220) {
        logger.error("Invalid Authd greeting '{}' from server {}:{}", reply, host, port);
        throw new IOException("Authd: Expected 220 Hello");
      }

            /*
             * If the remote server is SSL capable, we need to start a handshake now.
             * If we had known up front, we could have used a SSLSocket instead, but that's
             * not how Authd rolls. Instead, set up an SSL engine, and wrap the already open
             * socket.
             */
      boolean sslCapable = reply.contains("SSL");
      if (sslCapable) {
        try {
          SSLEngine engine = createSslEngine(host, port, sslThumbprint);
          engine.setUseClientMode(true);
          engine.beginHandshake();
          SSLClientWrapper wrap = new SSLClientWrapper(engine, socket);
          wrap.processHandshake();
          in = new DataInputStream(wrap.getInputStream());
          out = new DataOutputStream(wrap.getOutputStream());
        } catch (Exception e) {
          logger.error("SSL handshake error", e);
          throw new IOException("SSL error", e);
        }
      }

      // Write session and service IDs. If using SSL, this is encrypted.
      out.writeBytes("SESSION " + session + "\r\n");
      out.writeBytes("PROXY " + service + "\r\n");

      // Read reply. Should be 200 Connected
      reply = in.readLine();
      code = replyCode(reply);
      if (code != 200) {
        logger.error("Invalid Authd PROXY reply '{}' from server {}:{}", reply, host, port);
        throw new IOException("Authd: Expected 200 Connected");
      }
    } catch (IOException e) {
      try {
        socket.close(); // don't leak file descriptor for socket
      } catch (IOException s) {
        // ignore it
      }
      throw e;
    }

        /*
         * We're now connected to the service. Note that we're returning the raw,
         * unencrypted. The SSL handshake and encryption above was only for the
         * Authd connection. If the client is connecting to an encrypted service
         * (nfc-ssl, for example), it will have to perform its own handshake with
         * the service it's connected to.
         */
    return socket;
  }

  private static int replyCode(String reply) {
    if (reply == null) {
      return -1; // non valid http reply code
    } else {
      try {
        return Integer.parseInt(reply.substring(0, 3));
      } catch (NumberFormatException e) {
        return -1; // non valid http reply code
      }
    }
  }

  private static byte[] convertThumbprint(String thumbprint) {
    if (thumbprint.length() > 1024) {
      throw new RuntimeException("SSL thumbprint too large (" + thumbprint.length() + " characters)");
    }
    String[] bytes = thumbprint.split(":");
    byte[] result = new byte[bytes.length];
    for (int i = 0; i < result.length; ++i) {
      result[i] = (byte) Integer.parseInt(bytes[i], 16);
    }
    return result;
  }

  private static SSLEngine createSslEngine(String host, int port, String sslThumbprint)
      throws NoSuchAlgorithmException, KeyManagementException {
    SSLContext sc = SSLContext.getInstance("SSL");
    TrustManager tm = new ThumbprintTrustManager(convertThumbprint(sslThumbprint));
    TrustManager[] tms = new TrustManager[]{tm};
    sc.init(null, tms, new java.security.SecureRandom());
    SSLEngine engine = sc.createSSLEngine(host, port);
    return engine;
  }
}
