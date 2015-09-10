/*
 * Created on Apr 6, 2005
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
 */

package org.cafesip.sipunit;

import javax.sip.RequestEvent;
import javax.sip.message.Request;

/**
 * The SipRequest class provides high level getter methods for a request received from the network.
 * It is primarily used for SipTestCase/SipAssert assertions dealing with SIP message body and
 * headers. The test program passes this object to these assert methods. The test program can obtain
 * this object by calling the getLastReceivedRequest() or getAllReceivedRequests() on the
 * MessageListener object (such as SipCall or Subscription) when using the high-level API or it can
 * create this object using the RequestEvent/request object returned by a waitXyz() method (such as
 * SipSession.waitRequest()) when using the low-level SipUnit API.
 * 
 * <p>
 * A test program may call this object's getMessage() method to get the underlying
 * javax.sip.message.Message object or call getRequestEvent() to get the associated
 * javax.sip.RequestEvent which provides access to related JAIN-SIP objects such ServerTransaction,
 * Dialog, etc. Knowledge of JAIN SIP API is required at this level.
 * 
 * @author Becky McElroy
 * 
 */
public class SipRequest extends SipMessage {

  // ******* Request methods ********** */

  public static final String INVITE = Request.INVITE;

  public static final String ACK = Request.ACK;

  public static final String BYE = Request.BYE;

  public static final String CANCEL = Request.CANCEL;

  public static final String INFO = Request.INFO;

  public static final String MESSAGE = Request.MESSAGE;

  public static final String NOTIFY = Request.NOTIFY;

  public static final String OPTIONS = Request.OPTIONS;

  public static final String PRACK = Request.PRACK;

  public static final String REFER = Request.REFER;

  public static final String REGISTER = Request.REGISTER;

  public static final String SUBSCRIBE = Request.SUBSCRIBE;

  public static final String UPDATE = Request.UPDATE;

  private RequestEvent requestEvent;

  /**
   * A constructor for this class, applicable when using the low-level SipUnit API. Call this method
   * to create a SipRequest object after calling SipSession.waitRequest(), so that you can use the
   * SipTestCase/SipAssert assert methods pertaining to SIP message body and headers (by passing in
   * this object).
   * 
   * @param request the Request contained within the RequestEvent object returned by
   *        SipSession.waitRequest()
   */
  public SipRequest(Request request) {
    super(request);
  }

  /**
   * A constructor for this class used by SipUnit classes such as SipCall and Subscription to save
   * the request event information so that the test program can get JAIN-SIP objects from it, if
   * needed - ServerTransaction, Dialog, etc.
   * 
   * <p>
   * This constructor may also be used by a test program in lieu of the other constructor.
   * 
   * @param event
   */
  public SipRequest(RequestEvent event) {
    super(event.getRequest());
    this.requestEvent = event;
  }

  /**
   * Returns the request URI line of the request message or an empty string if there isn't one. The
   * request URI indicates the user or service to which this request is addressed.
   * 
   * @return the Request URI line as a string or "" if there isn't one
   */
  public String getRequestURI() {
    if ((message == null) || (((Request) message).getRequestURI() == null)) {
      return "";
    }

    return ((Request) message).getRequestURI().toString();
  }

  /**
   * Indicates if the request URI in the request message is a URI with a scheme of "sip" or "sips".
   * 
   * @return true if the request URI scheme is "sip" or "sips", false otherwise.
   */
  public boolean isSipURI() {
    if ((message == null) || (((Request) message).getRequestURI() == null)) {
      return false;
    }

    return ((Request) message).getRequestURI().isSipURI();
  }

  /**
   * Indicates if the request method is INVITE or not.
   * 
   * @return true if the method is INVITE, false otherwise.
   */
  public boolean isInvite() {
    if (message == null) {
      return false;
    }

    return (((Request) message).getMethod().equals(Request.INVITE));
  }

  /**
   * Indicates if the request method is ACK or not.
   * 
   * @return true if the method is ACK, false otherwise.
   */
  public boolean isAck() {
    if (message == null) {
      return false;
    }

    return (((Request) message).getMethod().equals(Request.ACK));
  }

  /**
   * Indicates if the request method is BYE or not.
   * 
   * @return true if the method is BYE, false otherwise.
   */
  public boolean isBye() {
    if (message == null) {
      return false;
    }

    return (((Request) message).getMethod().equals(Request.BYE));
  }

  /**
   * Indicates if the request method is NOTIFY or not.
   * 
   * @return true if the method is NOTIFY, false otherwise.
   */
  public boolean isNotify() {
    if (message == null) {
      return false;
    }

    return (((Request) message).getMethod().equals(Request.NOTIFY));
  }

  /**
   * Indicates if the request method is SUBSCRIBE or not.
   * 
   * @return true if the method is SUBSCRIBE, false otherwise.
   */
  public boolean isSubscribe() {
    if (message == null) {
      return false;
    }

    return (((Request) message).getMethod().equals(Request.SUBSCRIBE));
  }

  /**
   * Use this method if you need the JAIN-SIP request event associated with a request received by
   * high level SipUnit classes like SipCall and Subscription.
   * 
   * @return Returns the requestEvent.
   */
  public RequestEvent getRequestEvent() {
    return requestEvent;
  }

  /**
   * A setter for the request event.
   * 
   * @param requestEvent The requestEvent to set.
   */
  public void setRequestEvent(RequestEvent requestEvent) {
    this.requestEvent = requestEvent;
  }
}
