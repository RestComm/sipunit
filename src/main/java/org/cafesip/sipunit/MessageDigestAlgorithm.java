/*
 * Created on Feb 27, 2005
 * 
 * Copyright 2005 CafeSip.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 * 
 * This class was copied from the Sip Communicator project (package
 * net.java.sip.communicator.sip.security, its author is Emil Ivov) and slightly modified here (to
 * remove console/log messages). Thanks for making it publicly available. It is licensed under the
 * Apache Software License, Version 1.1 Copyright (c) 2000.
 */

package org.cafesip.sipunit;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * FOR INTERNAL USE, but available to test programs if needed. The class takes standard Http
 * Authentication details and returns a response according to the MD5 algorithm.
 * 
 * <p>
 * This class was copied from the Sip Communicator project (package
 * net.java.sip.communicator.sip.security, its author is Emil Ivov) and slightly modified here (to
 * remove console/log messages). Thanks for making it publicly available. It is licensed under the
 * Apache Software License, Version 1.1 Copyright (c) 2000.
 * 
 * @author Emil Ivov &lt; emcho@dev.java.net &gt;
 * @version 1.0
 */
public class MessageDigestAlgorithm {

  /**
   * Calculates a response an http authentication response in accordance with rfc2617.
   * 
   * <p>
   * This method was copied from the Sip Communicator project (package
   * net.java.sip.communicator.sip.security, its author is Emil Ivov) and slightly modified here (to
   * remove console/log messages). Thanks for making it publicly available. It is licensed under the
   * Apache Software License, Version 1.1 Copyright (c) 2000.
   * 
   * @param algorithm MD5 or MD5-sess)
   * @param username_value username_value (see rfc2617)
   * @param realm_value realm_value
   * @param passwd passwd
   * @param nonce_value nonce_value
   * @param cnonce_value cnonce_value
   * @param Method method
   * @param digest_uri_value uri_value
   * @param entity_body entity_body
   * @param qop_value qop
   * @return a digest response as defined in rfc2617
   * @throws NullPointerException in case of incorrectly null parameters.
   * 
   * 
   */
  public static String calculateResponse(String algorithm, String username_value,
      String realm_value, String passwd, String nonce_value, String nc_value, String cnonce_value,
      String Method, String digest_uri_value, String entity_body, String qop_value) {
    if (username_value == null || realm_value == null || passwd == null || Method == null
        || digest_uri_value == null || nonce_value == null)
      throw new NullPointerException("Null parameter to MessageDigestAlgorithm.calculateResponse()");

    // The following follows closely the algorithm for generating a response
    // digest as specified by rfc2617
    String A1 = null;

    if (algorithm == null || algorithm.trim().length() == 0
        || algorithm.trim().equalsIgnoreCase("MD5")) {
      A1 = username_value + ":" + realm_value + ":" + passwd;
    } else {
      if (cnonce_value == null || cnonce_value.length() == 0)
        throw new NullPointerException("cnonce_value may not be absent for MD5-Sess algorithm.");

      A1 =
          H(username_value + ":" + realm_value + ":" + passwd) + ":" + nonce_value + ":"
              + cnonce_value;
    }

    String A2 = null;
    if (qop_value == null || qop_value.trim().length() == 0
        || qop_value.trim().equalsIgnoreCase("auth")) {
      A2 = Method + ":" + digest_uri_value;
    } else {
      if (entity_body == null)
        entity_body = "";
      A2 = Method + ":" + digest_uri_value + ":" + H(entity_body);
    }

    String request_digest = null;
    if (cnonce_value != null && qop_value != null
        && (qop_value.equals("auth") || (qop_value.equals("auth-int")))) {
      request_digest =
          KD(H(A1), nonce_value + ":" + nc_value + ":" + cnonce_value + ":" + qop_value + ":"
              + H(A2));

    } else {
      request_digest = KD(H(A1), nonce_value + ":" + H(A2));
    }

    return request_digest;
  }

  /**
   * Defined in rfc 2617 as H(data) = MD5(data); .
   * 
   * @param data data
   * @return MD5(data)
   */
  private static String H(String data) {
    try {
      MessageDigest digest = MessageDigest.getInstance("MD5");

      return toHexString(digest.digest(data.getBytes()));
    } catch (NoSuchAlgorithmException ex) {
      // shouldn't happen
      return null;
    }
  }

  /**
   * Defined in rfc 2617 as KD(secret, data) = H(concat(secret, ":", data)) .
   * 
   * @param data data
   * @param secret secret
   * @return H(concat(secret, ":", data));
   */
  private static String KD(String secret, String data) {
    return H(secret + ":" + data);
  }

  // the following code was copied from the NIST-SIP instant
  // messenger (its author is Olivier Deruelle). Thanks for making it public!
  private static final char[] toHex = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b',
      'c', 'd', 'e', 'f'};

  /**
   * Converts b[] to hex string.
   * 
   * @param b the bte array to convert
   * @return a Hex representation of b.
   */
  private static String toHexString(byte b[]) {
    int pos = 0;
    char[] c = new char[b.length * 2];
    for (int i = 0; i < b.length; i++) {
      c[pos++] = toHex[(b[i] >> 4) & 0x0F];
      c[pos++] = toHex[b[i] & 0x0f];
    }
    return new String(c);
  }
}
