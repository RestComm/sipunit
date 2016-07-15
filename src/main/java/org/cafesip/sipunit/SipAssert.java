/*
 * Created on Sep 20, 2009
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

import static com.jayway.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Callable;

import javax.sip.header.CSeqHeader;
import javax.sip.header.Header;
import javax.sip.message.Request;

import com.jayway.awaitility.core.ConditionTimeoutException;

/**
 * This class is the static equivalent of SipTestCase. It is intended for use with JUnit 4 or for
 * when the test class must extend something other than SipTestCase.
 * 
 * <p>
 * These methods can be used directly: <code>SipAssert.assertAnswered(...)</code>, or they can be
 * referenced through static import:
 * 
 * <pre>
 * import static org.cafesip.sipunit.SipAssert.assertAnswered;
 *    ...
 *    assertAnswered(...);
 * </pre>
 * 
 * <p>
 * See SipTestCase for further details on writing a SipUnit test class.
 * 
 * @author Becky McElroy
 * 
 */
public class SipAssert {

  /**
   * Asserts that the last SIP operation performed by the given object was successful.
   * 
   * @param op the SipUnit object that executed an operation.
   */
  public static void assertLastOperationSuccess(SipActionObject op) {
    assertLastOperationSuccess(null, op);
  }

  /**
   * Asserts that the last SIP operation performed by the given object was successful. Assertion
   * failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param op the SipUnit object that executed an operation.
   */
  public static void assertLastOperationSuccess(String msg, SipActionObject op) {
    assertNotNull("Null assert object passed in", op);
    assertTrue(msg, op.getErrorMessage().length() == 0);
  }

  /**
   * Asserts that the last SIP operation performed by the given object failed.
   * 
   * @param op the SipUnit object that executed an operation.
   */
  public static void assertLastOperationFail(SipActionObject op) {
    assertLastOperationFail(null, op);
  }

  /**
   * Asserts that the last SIP operation performed by the given object failed. Assertion failure
   * output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param op the SipUnit object that executed an operation.
   */
  public static void assertLastOperationFail(String msg, SipActionObject op) {
    assertNotNull("Null assert object passed in", op);
    assertTrue(msg, op.getErrorMessage().length() > 0);
  }

  /**
   * Asserts that the given SIP message contains at least one occurrence of the specified header.
   * 
   * @param sipMessage the SIP message.
   * @param header the string identifying the header, as specified in RFC-3261.
   * 
   */
  public static void assertHeaderPresent(SipMessage sipMessage, String header) {
    assertHeaderPresent(null, sipMessage, header); // header is case
    // sensitive?
  }

  /**
   * Asserts that the given SIP message contains at least one occurrence of the specified header.
   * Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   */
  public static void assertHeaderPresent(String msg, SipMessage sipMessage, String header) {
    assertNotNull("Null assert object passed in", sipMessage);
    assertTrue(msg, sipMessage.getHeaders(header).hasNext());
  }

  /**
   * Asserts that the given SIP message contains no occurrence of the specified header.
   * 
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   */
  public static void assertHeaderNotPresent(SipMessage sipMessage, String header) {
    assertHeaderNotPresent(null, sipMessage, header);
  }

  /**
   * Asserts that the given SIP message contains no occurrence of the specified header. Assertion
   * failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   */
  public static void assertHeaderNotPresent(String msg, SipMessage sipMessage, String header) {
    assertNotNull("Null assert object passed in", sipMessage);
    assertFalse(msg, sipMessage.getHeaders(header).hasNext());
  }

  /**
   * Asserts that the given SIP message contains at least one occurrence of the specified header and
   * that at least one occurrence of this header contains the given value. The assertion fails if no
   * occurrence of the header contains the value or if the header is not present in the mesage.
   * 
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   * @param value the string value within the header to look for. An exact string match is done
   *        against the entire contents of the header. The assertion will pass if any part of the
   *        header matches the value given.
   */
  public static void assertHeaderContains(SipMessage sipMessage, String header, String value) {
    assertHeaderContains(null, sipMessage, header, value); // value is case
    // sensitive?
  }

  /**
   * Asserts that the given SIP message contains at least one occurrence of the specified header and
   * that at least one occurrence of this header contains the given value. The assertion fails if no
   * occurrence of the header contains the value or if the header is not present in the mesage.
   * Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   * @param value the string value within the header to look for. An exact string match is done
   *        against the entire contents of the header. The assertion will pass if any part of the
   *        header matches the value given.
   */
  public static void assertHeaderContains(String msg, SipMessage sipMessage, String header,
      String value) {
    assertNotNull("Null assert object passed in", sipMessage);
    ListIterator<Header> l = sipMessage.getHeaders(header);
    while (l.hasNext()) {
      String h = ((Header) l.next()).toString();

      if (h.indexOf(value) != -1) {
        assertTrue(true);
        return;
      }
    }

    fail(msg);
  }

  /**
   * Asserts that the given SIP message contains no occurrence of the specified header with the
   * value given, or that there is no occurrence of the header in the message. The assertion fails
   * if any occurrence of the header contains the value.
   * 
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   * @param value the string value within the header to look for. An exact string match is done
   *        against the entire contents of the header. The assertion will fail if any part of the
   *        header matches the value given.
   */
  public static void assertHeaderNotContains(SipMessage sipMessage, String header, String value) {
    assertHeaderNotContains(null, sipMessage, header, value);
  }

  /**
   * Asserts that the given SIP message contains no occurrence of the specified header with the
   * value given, or that there is no occurrence of the header in the message. The assertion fails
   * if any occurrence of the header contains the value. Assertion failure output includes the given
   * message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param header the string identifying the header as specified in RFC-3261.
   * @param value the string value within the header to look for. An exact string match is done
   *        against the entire contents of the header. The assertion will fail if any part of the
   *        header matches the value given.
   */
  public static void assertHeaderNotContains(String msg, SipMessage sipMessage, String header,
      String value) {
    assertNotNull("Null assert object passed in", sipMessage);
    ListIterator<Header> l = sipMessage.getHeaders(header);
    while (l.hasNext()) {
      String h = ((Header) l.next()).toString();

      if (h.indexOf(value) != -1) {
        fail(msg);
      }
    }

    assertTrue(true);
  }

  /**
   * Asserts that the given message listener object received a response with the indicated status
   * code.
   * 
   * @param statusCode The response status code to check for (eg, SipResponse.RINGING)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseReceived(int statusCode, MessageListener obj) {
    assertResponseReceived(null, statusCode, obj);
  }

  /**
   * Asserts that the given message listener object received a response with the indicated status
   * code, CSeq method and CSeq sequence number.
   * 
   * @param statusCode The response status code to check for (eg, SipResponse.RINGING)
   * @param method The CSeq method to look for (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to look for
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseReceived(int statusCode, String method, long sequenceNumber,
      MessageListener obj) {
    assertResponseReceived(null, statusCode, method, sequenceNumber, obj);
  }

  /**
   * Asserts that the given message listener object received a response with the indicated status
   * code. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param statusCode The response status code to check for (eg, SipResponse.RINGING)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseReceived(String msg, int statusCode, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertTrue(msg, responseReceived(statusCode, obj));
  }

  /**
   * Await until a the size of {@link SipCall#getAllReceivedResponses()} is equal to count.
   * 
   * @param call the {@link SipCall} under test
   * @param count the expected amount of responses
   * @throws ConditionTimeoutException If condition was not fulfilled within the default time
   *         period.
   */
  public static void awaitReceivedResponses(final SipCall call, final int count) {
    await().until(new Callable<Integer>() {

      @Override
      public Integer call() throws Exception {
        return call.getAllReceivedResponses().size();
      }
    }, is(count));
  }

  /**
   * Check the given message listener object received a response with the indicated status
   * code.
   *
   * @param statusCode the code we want to find
   * @param messageListener the {@link MessageListener} we want to check
   * @return true if a received response matches the given statusCode
   */
  public static boolean responseReceived(int statusCode, MessageListener messageListener) {
    ArrayList<SipResponse> responses = messageListener.getAllReceivedResponses();

    for (SipResponse r : responses) {
      if (statusCode == r.getStatusCode()) {
        return true;
      }
    }

    return false;
  }

  /**
   * Asserts that the given message listener object received a response with the indicated status
   * code, CSeq method and CSeq sequence number. Assertion failure output includes the given message
   * text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param statusCode The response status code to check for (eg, SipResponse.RINGING)
   * @param method The CSeq method to look for (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to look for
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseReceived(String msg, int statusCode, String method,
      long sequenceNumber, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertTrue(msg, responseReceived(statusCode, method, sequenceNumber, obj));
  }

  private static boolean responseReceived(int statusCode, String method, long sequenceNumber,
      MessageListener obj) {
    List<SipResponse> responses = obj.getAllReceivedResponses();

    Iterator<SipResponse> i = responses.iterator();
    while (i.hasNext()) {
      SipResponse resp = i.next();
      if (resp.getStatusCode() == statusCode) {
        CSeqHeader hdr = (CSeqHeader) resp.getMessage().getHeader(CSeqHeader.NAME);
        if (hdr != null) {
          if (hdr.getMethod().equals(method)) {
            if (hdr.getSeqNumber() == sequenceNumber) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  /**
   * Asserts that the given message listener object has not received a response with the indicated
   * status code.
   * 
   * @param statusCode The response status code to verify absent (eg, SipResponse.RINGING)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   * 
   */
  public static void assertResponseNotReceived(int statusCode, MessageListener obj) {
    assertResponseNotReceived(null, statusCode, obj);
  }

  /**
   * Asserts that the given message listener object has not received a response with the indicated
   * status code. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param statusCode The response status code to verify absent (eg, SipResponse.RINGING)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseNotReceived(String msg, int statusCode, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertFalse(msg, responseReceived(statusCode, obj));
  }

  /**
   * Asserts that the given message listener object has not received a response with the indicated
   * status code, CSeq method and sequence number.
   * 
   * @param statusCode The response status code to verify absent (eg, SipResponse.RINGING)
   * @param method The CSeq method to verify absent (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to verify absent
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   * 
   */
  public static void assertResponseNotReceived(int statusCode, String method, long sequenceNumber,
      MessageListener obj) {
    assertResponseNotReceived(null, statusCode, method, sequenceNumber, obj);
  }

  /**
   * Asserts that the given message listener object has not received a response with the indicated
   * status code, CSeq method and sequence number. Assertion failure output includes the given
   * message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param statusCode The response status code to verify absent (eg, SipResponse.RINGING)
   * @param method The CSeq method to verify absent (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to verify absent
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertResponseNotReceived(String msg, int statusCode, String method,
      long sequenceNumber, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertFalse(msg, responseReceived(statusCode, method, sequenceNumber, obj));
  }

  /**
   * Asserts that the given message listener object received a request with the indicated request
   * method.
   * 
   * @param method The request method to check for (eg, SipRequest.BYE)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestReceived(String method, MessageListener obj) {
    assertRequestReceived(null, method, obj);
  }

  /**
   * Asserts that the given message listener object received a request with the indicated CSeq
   * method and CSeq sequence number.
   * 
   * @param method The CSeq method to look for (SipRequest.REGISTER, etc.)
   * @param sequenceNumber The CSeq sequence number to look for
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestReceived(String method, long sequenceNumber, MessageListener obj) {
    assertRequestReceived(null, method, sequenceNumber, obj);
  }

  /**
   * Asserts that the given message listener object received a request with the indicated request
   * method. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param method The request method to check for (eg, SipRequest.INVITE)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestReceived(String msg, String method, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertTrue(msg, requestReceived(method, obj));
  }

  private static boolean requestReceived(String method, MessageListener obj) {
    List<SipRequest> requests = obj.getAllReceivedRequests();

    Iterator<SipRequest> i = requests.iterator();
    while (i.hasNext()) {
      Request req = (Request) i.next().getMessage();
      if (req != null) {
        if (req.getMethod().equals(method)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Asserts that the given message listener object received a request with the indicated CSeq
   * method and CSeq sequence number. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param method The CSeq method to look for (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to look for
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestReceived(String msg, String method, long sequenceNumber,
      MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertTrue(msg, requestReceived(method, sequenceNumber, obj));
  }

  private static boolean requestReceived(String method, long sequenceNumber, MessageListener obj) {
    List<SipRequest> requests = obj.getAllReceivedRequests();

    Iterator<SipRequest> i = requests.iterator();
    while (i.hasNext()) {
      Request req = (Request) i.next().getMessage();
      if (req != null) {
        CSeqHeader hdr = (CSeqHeader) req.getHeader(CSeqHeader.NAME);
        if (hdr != null) {
          if (hdr.getMethod().equals(method)) {
            if (hdr.getSeqNumber() == sequenceNumber) {
              return true;
            }
          }
        }
      }
    }

    return false;
  }

  /**
   * Asserts that the given message listener object has not received a request with the indicated
   * request method.
   * 
   * @param method The request method to verify absent (eg, SipRequest.BYE)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   * 
   */
  public static void assertRequestNotReceived(String method, MessageListener obj) {
    assertRequestNotReceived(null, method, obj);
  }

  /**
   * Asserts that the given message listener object has not received a request with the indicated
   * request method. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param method The request method to verify absent (eg, SipRequest.BYE)
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestNotReceived(String msg, String method, MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertFalse(msg, requestReceived(method, obj));
  }

  /**
   * Asserts that the given message listener object has not received a request with the indicated
   * CSeq method and sequence number.
   * 
   * @param method The CSeq method to verify absent (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to verify absent
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   * 
   */
  public static void assertRequestNotReceived(String method, long sequenceNumber,
      MessageListener obj) {
    assertRequestNotReceived(null, method, sequenceNumber, obj);
  }

  /**
   * Asserts that the given message listener object has not received a request with the indicated
   * CSeq method and sequence number. Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param method The CSeq method to verify absent (SipRequest.INVITE, etc.)
   * @param sequenceNumber The CSeq sequence number to verify absent
   * @param obj The MessageListener object (ie, SipCall, Subscription, etc.).
   */
  public static void assertRequestNotReceived(String msg, String method, long sequenceNumber,
      MessageListener obj) {
    assertNotNull("Null assert object passed in", obj);
    assertFalse(msg, requestReceived(method, sequenceNumber, obj));
  }

  /**
   * Asserts that the given incoming or outgoing call leg was answered.
   * 
   * @param call The incoming or outgoing call leg.
   */
  public static void assertAnswered(SipCall call) {
    assertAnswered(null, call);
  }

  /**
   * Asserts that the given incoming or outgoing call leg was answered. Assertion failure output
   * includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param call The incoming or outgoing call leg.
   */
  public static void assertAnswered(String msg, SipCall call) {
    assertNotNull("Null assert object passed in", call);
    assertTrue(msg, call.isCallAnswered());
  }

  /**
   * Awaits that the given incoming or outgoing call leg was answered. Assertion failure output
   * includes the given message text.
   * 
   * @param call The incoming or outgoing call leg.
   */
  public static void awaitAnswered(SipCall call) {
    awaitAnswered(null, call);
  }

  /**
   * Awaits that the given incoming or outgoing call leg was answered. Assertion failure output
   * includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param call The incoming or outgoing call leg.
   */
  public static void awaitAnswered(final String msg, final SipCall call) {
    await().until(new Runnable() {

      @Override
      public void run() {
        assertAnswered(msg, call);
      }
    });
  }

  public static void awaitDialogReady(final ReferNotifySender ub) {
    await().until(new Runnable() {

      @Override
      public void run() {
        assertNotNull(ub.getDialog());
      }
    });
  }

  /**
   * Asserts that the given incoming or outgoing call leg has not been answered.
   * 
   * @param call The incoming or outgoing call leg.
   */
  public static void assertNotAnswered(SipCall call) {
    assertNotAnswered(null, call);
  }

  /**
   * Asserts that the given incoming or outgoing call leg has not been answered. Assertion failure
   * output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param call The incoming or outgoing call leg.
   */
  public static void assertNotAnswered(String msg, SipCall call) {
    assertNotNull("Null assert object passed in", call);
    assertFalse(msg, call.isCallAnswered());
  }

  /**
   * Asserts that the given SIP message contains a body.
   * 
   * @param sipMessage the SIP message.
   */
  public static void assertBodyPresent(SipMessage sipMessage) {
    assertBodyPresent(null, sipMessage);
  }

  /**
   * Asserts that the given SIP message contains a body. Assertion failure output includes the given
   * message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   */
  public static void assertBodyPresent(String msg, SipMessage sipMessage) {
    assertNotNull("Null assert object passed in", sipMessage);
    assertTrue(msg, sipMessage.getContentLength() > 0);
  }

  /**
   * Asserts that the given SIP message contains no body.
   * 
   * @param sipMessage the SIP message.
   */
  public static void assertBodyNotPresent(SipMessage sipMessage) {
    assertBodyNotPresent(null, sipMessage);
  }

  /**
   * Asserts that the given SIP message contains no body. Assertion failure output includes the
   * given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   */
  public static void assertBodyNotPresent(String msg, SipMessage sipMessage) {
    assertNotNull("Null assert object passed in", sipMessage);
    assertFalse(msg, sipMessage.getContentLength() > 0);
  }

  /**
   * Asserts that the given SIP message contains a body that includes the given value. The assertion
   * fails if a body is not present in the message or is present but doesn't include the value.
   * 
   * @param sipMessage the SIP message.
   * @param value the string value to look for in the body. An exact string match is done against
   *        the entire contents of the body. The assertion will pass if any part of the body matches
   *        the value given. ??case sensitive?
   */
  public static void assertBodyContains(SipMessage sipMessage, String value) {
    assertBodyContains(null, sipMessage, value);
  }

  /**
   * Asserts that the given SIP message contains a body that includes the given value. The assertion
   * fails if a body is not present in the message or is present but doesn't include the value.
   * Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param value the string value to look for in the body. An exact string match is done against
   *        the entire contents of the body. The assertion will pass if any part of the body matches
   *        the value given.
   */
  public static void assertBodyContains(String msg, SipMessage sipMessage, String value) {
    assertNotNull("Null assert object passed in", sipMessage);
    assertBodyPresent(msg, sipMessage);
    String body = new String(sipMessage.getRawContent());

    if (body.indexOf(value) != -1) {
      assertTrue(true);
      return;
    }

    fail(msg);
  }

  /**
   * Asserts that the body in the given SIP message does not contain the value given, or that there
   * is no body in the message. The assertion fails if the body is present and contains the value.
   * 
   * @param sipMessage the SIP message.
   * @param value the string value to look for in the body. An exact string match is done against
   *        the entire contents of the body. The assertion will fail if any part of the body matches
   *        the value given.
   */
  public static void assertBodyNotContains(SipMessage sipMessage, String value) {
    assertBodyNotContains(null, sipMessage, value);
  }

  /**
   * Asserts that the body in the given SIP message does not contain the value given, or that there
   * is no body in the message. The assertion fails if the body is present and contains the value.
   * Assertion failure output includes the given message text.
   * 
   * @param msg message text to output if the assertion fails.
   * @param sipMessage the SIP message.
   * @param value the string value to look for in the body. An exact string match is done against
   *        the entire contents of the body. The assertion will fail if any part of the body matches
   *        the value given.
   */
  public static void assertBodyNotContains(String msg, SipMessage sipMessage, String value) {
    assertNotNull("Null assert object passed in", sipMessage);
    if (sipMessage.getContentLength() > 0) {
      String body = new String(sipMessage.getRawContent());

      if (body.indexOf(value) != -1) {
        fail(msg);
      }
    }

    assertTrue(true);
  }

  /**
   * Asserts that the given Subscription has not encountered any errors while processing received
   * subscription responses and received NOTIFY requests. If the assertion fails, the encountered
   * error(s) are included in the failure output.
   * 
   * @param subscription the Subscription in question.
   */
  public static void assertNoSubscriptionErrors(EventSubscriber subscription) {
    assertNoSubscriptionErrors(null, subscription);
  }

  /**
   * Asserts that the given Subscription has not encountered any errors while processing received
   * subscription responses and received NOTIFY requests. Assertion failure output includes the
   * given message text along with the encountered error(s).
   * 
   * @param msg message text to include if the assertion fails.
   * @param subscription the Subscription in question.
   */
  public static void assertNoSubscriptionErrors(String msg, EventSubscriber subscription) {
    assertNotNull("Null assert object passed in", subscription);

    StringBuffer buf = new StringBuffer(msg == null ? "Subscription error(s)" : msg);
    Iterator<String> i = subscription.getEventErrors().iterator();
    while (i.hasNext()) {
      buf.append(" : ");
      buf.append((String) i.next());
    }

    assertEquals(buf.toString(), 0, subscription.getEventErrors().size());
  }

  /**
   * Awaits the an error free {@link SipStack#dispose()}.
   */
  public static void awaitStackDispose(final SipStack sipStack) {
    await().until(new Runnable() {

      @Override
      public void run() {
        try {
          sipStack.dispose();
        } catch (RuntimeException e) {
          e.printStackTrace();
          fail();
        }
      }
    });
  }

  // Later: ContentDispositionHeader, ContentEncodingHeader,
  // ContentLanguageHeader,
  // ContentLengthHeader, ContentTypeHeader, MimeVersionHeader

  /*
   * From seeing how to remove our stuff from the failure stack:
   * 
   * catch (AssertionFailedError e) // adjust stack trace { ArrayList stack = new ArrayList();
   * StackTraceElement[] t = e.getStackTrace(); String thisclass = this.getClass().getName(); for
   * (int i = 0; i < t.length; i++) { StackTraceElement ele = t[i]; System.out.println("Element = "
   * + ele.toString()); if (thisclass.equals(ele.getClass().getName()) == true) { continue; }
   * stack.add(t[i]); }
   * 
   * StackTraceElement[] new_stack = new StackTraceElement[stack.size()];
   * e.setStackTrace((StackTraceElement[]) stack.toArray(new_stack)); throw e; }
   */

  /*
   * public class Arguments { public static void notNull(Object arg) { if(arg == null) {
   * IllegalArgumentException t = new IllegalArgumentException(); reorient(t); throw t; } } private
   * static void reorient(Throwable t) { StackTraceElement[] elems = t.getStackTrace();
   * StackTraceElement[] subElems = new StackTraceElement[elems.length-1]; System.arrayCopy(elems,
   * 1, subElems, 0, elems.length-1); t.setStackTrace(t); } }
   */

}
