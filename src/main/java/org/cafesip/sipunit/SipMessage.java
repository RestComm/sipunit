/*
 * Created on Apr 8, 2005
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
 * These methods are based on/borrowed from the JAIN SIP API.
 */

package org.cafesip.sipunit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ListIterator;

import javax.sip.header.Header;
import javax.sip.message.Message;

/**
 * SipMessage represents a SipRequest or a SipResponse. SipMessage provides a uniform way of getting
 * information about a SIP message (request or reponse), such as content length.
 * 
 * @author Becky McElroy
 * 
 */
public class SipMessage {

  protected Message message;

  protected SipMessage(Message message) {
    this.message = message;
  }

  /**
   * This method is FOR SIPUNIT INTERNAL USE ONLY. It requires knowledge of JAIN SIP API. Used by
   * SipTestCase/SipAssert.
   * 
   * <p>
   * Obtains a ListIterator over all the headers with the specified name in this message. The order
   * of the items returned is the same as the order in which they appeared in the message.
   * 
   * @param header the string identifying the header as specified in RFC-3261.
   * @return the ListIterator over all the Headers of the specified name in the Message, this method
   *         returns an empty ListIterator if no Headers exist of this header type.
   * 
   */
  protected ListIterator<Header> getHeaders(String header) {
    if (message == null) {
      return Collections.<Header>emptyList().listIterator();
    }

    return message.getHeaders(header);
  }

  /**
   * Obtains the underlying javax.sip.message.Message object. Knowledge of JAIN-SIP API is required.
   * 
   * @return the JAIN-SIP API javax.sip.message.Message object.
   */
  public Message getMessage() {
    return message;
  }

  /**
   * Obtains the body of the message as an Object.
   * 
   * @return the body of the message or null if a body isn't present.
   */
  public java.lang.Object getContent() {
    if (message == null) {
      return null;
    }

    return message.getContent();
  }

  /**
   * Gets the length of the message body. The returned length does not include the CRLF separating
   * header fields and body. If no body is present, then zero is returned.
   * 
   * @return the size of the message body in octets.
   */
  public int getContentLength() {
    if ((message == null) || (message.getContentLength() == null)) {
      return 0;
    }

    return message.getContentLength().getContentLength();
  }

  /**
   * Gets the body content of the message as a byte array.
   * 
   * @return the body content of the message as a byte array or null if a body isn't present.
   */
  public byte[] getRawContent() {
    if (message == null) {
      return null;
    }

    return message.getRawContent();
  }

  /**
   * Gets the duration after which the message (or content) expires, relative to message receipt.
   * The value of this field is an integral number of seconds (in decimal) between 0 and (2**32)-1
   * 
   * @return the number of seconds that the message (or content) is valid, from the time of receipt.
   */
  public int getExpiry() {
    if ((message == null) || (message.getExpires() == null)) {
      return 0;
    }

    return message.getExpires().getExpires();
  }

  /**
   * Converts this message object to its String equivalent.
   * 
   * @return a string containing the contents of this message
   */
  public String toString() {
    if (message == null) {
      return "";
    }

    return message.toString();
  }

  // Later, SdpUnit: ContentDispositionHeader, ContentEncodingHeader,
  // ContentLanguageHeader,
  // ContentLengthHeader, ContentTypeHeader, MimeVersionHeader
}
