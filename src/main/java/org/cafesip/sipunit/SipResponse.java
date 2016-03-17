/*
 * Created on Mar 28, 2005
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

import java.util.HashMap;
import java.util.Map;
import javax.sip.ResponseEvent;
import javax.sip.message.Response;

/**
 * The SipResponse class provides static response status code definitions and text, and it also
 * provides high level getter methods for a response received from the network. With regard to the
 * latter, this class is primarily used for SipTestCase/SipAssert assertions dealing with SIP
 * message body and headers. The test program passes this object to these assert methods. The test
 * program can obtain this object by calling the getLastReceivedResponse() or
 * getAllReceivedResponses() on the MessageListener object (such as SipCall or Subscription) when
 * using the high-level API or it can create this object using the ResponseEvent/Response object
 * returned by a waitXyz() method (such as SipSession.waitResponse()) when using the low-level
 * SipUnit API.
 * 
 * <p>
 * A test program may call this object's getMessage() method to get the underlying
 * javax.sip.message.Message object or call getResponseEvent() to get the associated
 * javax.sip.ResponseEvent which provides access to related JAIN-SIP objects such ClientTransaction,
 * Dialog, etc. Knowledge of JAIN SIP API is required at this level.
 * 
 * @author Becky McElroy
 * 
 */
public class SipResponse extends SipMessage {

  // ******* Response status codes ********** */

  // PROVISIONAL (1xx)
  public static final int TRYING = 100;

  public static final int RINGING = 180;

  public static final int CALL_IS_BEING_FORWARDED = 181;

  public static final int QUEUED = 182;

  public static final int SESSION_PROGRESS = 183;

  // SUCCESS (2xx)
  public static final int OK = 200;

  public static final int ACCEPTED = 202; // (Extension RFC3265)

  // REDIRECTION (3xx)
  public static final int MULTIPLE_CHOICES = 300;

  public static final int MOVED_PERMANENTLY = 301;

  public static final int MOVED_TEMPORARILY = 302;

  public static final int USE_PROXY = 305;

  public static final int ALTERNATIVE_SERVICE = 380;

  // CLIENT_ERROR (4xx)
  public static final int BAD_REQUEST = 400;

  public static final int UNAUTHORIZED = 401;

  public static final int PAYMENT_REQUIRED = 402;

  public static final int FORBIDDEN = 403;

  public static final int NOT_FOUND = 404;

  public static final int METHOD_NOT_ALLOWED = 405;

  public static final int NOT_ACCEPTABLE = 406;

  public static final int PROXY_AUTHENTICATION_REQUIRED = 407;

  public static final int REQUEST_TIMEOUT = 408;

  public static final int GONE = 410;

  public static final int REQUEST_ENTITY_TOO_LARGE = 413;

  public static final int REQUEST_URI_TOO_LONG = 414;

  public static final int UNSUPPORTED_MEDIA_TYPE = 415;

  public static final int UNSUPPORTED_URI_SCHEME = 416;

  public static final int BAD_EXTENSION = 420;

  public static final int EXTENSION_REQUIRED = 421;

  public static final int INTERVAL_TOO_BRIEF = 423;

  public static final int TEMPORARILY_UNAVAILABLE = 480;

  public static final int CALL_OR_TRANSACTION_DOES_NOT_EXIST = 481;

  public static final int LOOP_DETECTED = 482;

  public static final int TOO_MANY_HOPS = 483;

  public static final int ADDRESS_INCOMPLETE = 484;

  public static final int AMBIGUOUS = 485;

  public static final int BUSY_HERE = 486;

  public static final int REQUEST_TERMINATED = 487;

  public static final int NOT_ACCEPTABLE_HERE = 488;

  public static final int BAD_EVENT = 489; // (Extension RFC3265)

  public static final int REQUEST_PENDING = 491;

  public static final int UNDECIPHERABLE = 493;

  // SERVER_ERROR (5xx)
  public static final int SERVER_INTERNAL_ERROR = 500;

  public static final int NOT_IMPLEMENTED = 501;

  public static final int BAD_GATEWAY = 502;

  public static final int SERVICE_UNAVAILABLE = 503;

  public static final int SERVER_TIMEOUT = 504;

  public static final int VERSION_NOT_SUPPORTED = 505;

  public static final int MESSAGE_TOO_LARGE = 513;

  // GLOBAL_ERROR (6xx)
  public static final int BUSY_EVERYWHERE = 600;

  public static final int DECLINE = 603;

  public static final int DOES_NOT_EXIST_ANYWHERE = 604;

  public static final int SESSION_NOT_ACCEPTABLE = 606;

  /**
   * Comment for <code>statusCodeDescription</code> This map yields a reason phrase, given a SIP
   * network response message status code.
   */
  public static Map<Integer, String> statusCodeDescription = new HashMap<>();

  static {
    // PROVISIONAL (1xx)
    statusCodeDescription.put(new Integer(TRYING), "Trying");
    statusCodeDescription.put(new Integer(RINGING), "Ringing");
    statusCodeDescription.put(new Integer(CALL_IS_BEING_FORWARDED), "Call is Being Forwarded");
    statusCodeDescription.put(new Integer(QUEUED), "Queued");
    statusCodeDescription.put(new Integer(SESSION_PROGRESS), "Session Progress");

    // SUCCESS (2xx)
    statusCodeDescription.put(new Integer(OK), "OK");
    statusCodeDescription.put(new Integer(ACCEPTED), "Accepted");

    // REDIRECTION (3xx)
    statusCodeDescription.put(new Integer(MULTIPLE_CHOICES), "Multiple Choices");
    statusCodeDescription.put(new Integer(MOVED_PERMANENTLY), "Moved Permanently");
    statusCodeDescription.put(new Integer(MOVED_TEMPORARILY), "Moved Temporarily");
    statusCodeDescription.put(new Integer(USE_PROXY), "Use Proxy");
    statusCodeDescription.put(new Integer(ALTERNATIVE_SERVICE), "Alternative Service");

    // CLIENT_ERROR (4xx)
    statusCodeDescription.put(new Integer(BAD_REQUEST), "Bad Request");
    statusCodeDescription.put(new Integer(UNAUTHORIZED), "Unauthorized");
    statusCodeDescription.put(new Integer(PAYMENT_REQUIRED), "Payment Required");
    statusCodeDescription.put(new Integer(FORBIDDEN), "Forbidden");
    statusCodeDescription.put(new Integer(NOT_FOUND), "Not Found");
    statusCodeDescription.put(new Integer(METHOD_NOT_ALLOWED), "Method Not Allowed");
    statusCodeDescription.put(new Integer(NOT_ACCEPTABLE), "Not Acceptable");
    statusCodeDescription.put(new Integer(PROXY_AUTHENTICATION_REQUIRED),
        "Proxy Authentication Required");
    statusCodeDescription.put(new Integer(REQUEST_TIMEOUT), "Request Timeout");
    statusCodeDescription.put(new Integer(GONE), "Gone");
    statusCodeDescription.put(new Integer(REQUEST_ENTITY_TOO_LARGE), "Request Entity Too Large");
    statusCodeDescription.put(new Integer(REQUEST_URI_TOO_LONG), "Request URI Too Long");
    statusCodeDescription.put(new Integer(UNSUPPORTED_MEDIA_TYPE), "Unsupported Media Type");
    statusCodeDescription.put(new Integer(UNSUPPORTED_URI_SCHEME), "Unsupported URI Scheme");
    statusCodeDescription.put(new Integer(BAD_EXTENSION), "Bad Extension");
    statusCodeDescription.put(new Integer(EXTENSION_REQUIRED), "Extension Required");
    statusCodeDescription.put(new Integer(INTERVAL_TOO_BRIEF), "Interval Too Brief");
    statusCodeDescription.put(new Integer(TEMPORARILY_UNAVAILABLE), "Temporarily Unavailable");
    statusCodeDescription.put(new Integer(CALL_OR_TRANSACTION_DOES_NOT_EXIST),
        "Call or Transaction Does Not Exist");
    statusCodeDescription.put(new Integer(LOOP_DETECTED), "Loop Detected");
    statusCodeDescription.put(new Integer(TOO_MANY_HOPS), "Too Many Hops");
    statusCodeDescription.put(new Integer(ADDRESS_INCOMPLETE), "Address Incomplete");
    statusCodeDescription.put(new Integer(AMBIGUOUS), "Ambiguous");
    statusCodeDescription.put(new Integer(BUSY_HERE), "Busy Here");
    statusCodeDescription.put(new Integer(REQUEST_TERMINATED), "Request Terminated");
    statusCodeDescription.put(new Integer(NOT_ACCEPTABLE_HERE), "Not Acceptable Here");
    statusCodeDescription.put(new Integer(BAD_EVENT), "Bad Event");
    statusCodeDescription.put(new Integer(REQUEST_PENDING), "Request Pending");
    statusCodeDescription.put(new Integer(UNDECIPHERABLE), "Undecipherable");

    // SERVER_ERROR (5xx)
    statusCodeDescription.put(new Integer(SERVER_INTERNAL_ERROR), "Server Internal Error");
    statusCodeDescription.put(new Integer(NOT_IMPLEMENTED), "Not Implemented");
    statusCodeDescription.put(new Integer(BAD_GATEWAY), "Bad Gateway");
    statusCodeDescription.put(new Integer(SERVICE_UNAVAILABLE), "Service Unavailable");
    statusCodeDescription.put(new Integer(SERVER_TIMEOUT), "Server Timeout");
    statusCodeDescription.put(new Integer(VERSION_NOT_SUPPORTED), "Version Not Supported");
    statusCodeDescription.put(new Integer(MESSAGE_TOO_LARGE), "Message Too Large");

    // GLOBAL_ERROR (6xx)
    statusCodeDescription.put(new Integer(BUSY_EVERYWHERE), "Busy Everywhere");
    statusCodeDescription.put(new Integer(DECLINE), "Decline");
    statusCodeDescription.put(new Integer(DOES_NOT_EXIST_ANYWHERE), "Does Not Exist Anywhere");
    statusCodeDescription.put(new Integer(SESSION_NOT_ACCEPTABLE), "Session Not Acceptable");
  }

  private ResponseEvent responseEvent;

  /**
   * A constructor for this class, applicable when using the low-level SipUnit API. Call this method
   * to create a SipResponse object after calling SipSession.waitResponse(), so that you can use the
   * SipTestCase/SipAssert assert methods pertaining to SIP message body and headers (by passing in
   * this object).
   * 
   * @param response the Response object contained within the ResponseEvent object returned by
   *        SipSession.waitResponse()
   */
  public SipResponse(Response response) {
    super(response);
  }

  /**
   * A constructor for this class used by SipUnit classes such as SipCall and Subscription to save
   * the response event information so that the test program can get JAIN-SIP objects from it, if
   * needed - ClientTransaction, Dialog, etc.
   * 
   * <p>
   * This constructor may also be used by a test program in lieu of the other constructor.
   * 
   * @param event
   */
  public SipResponse(ResponseEvent event) {
    super(event.getResponse());
    this.responseEvent = event;
  }

  /**
   * Obtains the reason phrase of this response message.
   * 
   * @return the reason phrase associated with the status code of this response message
   */
  public String getReasonPhrase() {
    if (message == null) {
      return "";
    }

    return ((Response) message).getReasonPhrase();
  }

  /**
   * Obtains the status code of this response message.
   * 
   * @return the status code
   */
  public int getStatusCode() {
    if (message == null) {
      return 0;
    }

    return ((Response) message).getStatusCode();
  }

  /**
   * Use this method if you need the JAIN-SIP response event associated with a response received by
   * high level SipUnit classes like SipCall and Subscription.
   * 
   * @return Returns the responseEvent.
   */
  public ResponseEvent getResponseEvent() {
    return responseEvent;
  }

  /**
   * A setter for the response event.
   * 
   * @param responseEvent The responseEvent to set.
   */
  public void setResponseEvent(ResponseEvent responseEvent) {
    this.responseEvent = responseEvent;
  }
}
