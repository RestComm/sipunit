/*
 * Created on April 21, 2005
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

package org.cafesip.sipunit.test.noproxy;

import static com.jayway.awaitility.Awaitility.await;
import static org.cafesip.sipunit.SipAssert.assertAnswered;
import static org.cafesip.sipunit.SipAssert.assertBodyContains;
import static org.cafesip.sipunit.SipAssert.assertBodyNotContains;
import static org.cafesip.sipunit.SipAssert.assertBodyNotPresent;
import static org.cafesip.sipunit.SipAssert.assertBodyPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderPresent;
import static org.cafesip.sipunit.SipAssert.assertLastOperationFail;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNotAnswered;
import static org.cafesip.sipunit.SipAssert.assertRequestNotReceived;
import static org.cafesip.sipunit.SipAssert.assertRequestReceived;
import static org.cafesip.sipunit.SipAssert.assertResponseNotReceived;
import static org.cafesip.sipunit.SipAssert.assertResponseReceived;
import static org.cafesip.sipunit.SipAssert.awaitReceivedResponses;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipMessage;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.cafesip.sipunit.test.util.AuthUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.Properties;
import java.util.concurrent.Callable;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionState;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.PriorityHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class tests SipUnit API methods.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server. Messaging between UACs is direct.
 * 
 * @author Becky McElroy
 * 
 */
public class TestNoProxy {
  private static final Logger LOG = LoggerFactory.getLogger(TestNoProxy.class);

  private SipStack sipStack;

  private SipPhone ua;

  private int myPort;

  private String testProtocol;

  private static Properties getDefaultProperties() {
    Properties defaultProperties = new Properties();

    defaultProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5061");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");
    defaultProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
    return defaultProperties;
  }

  protected Properties properties;

  static String DEFAULT_USER_A = "amit@nist.gov";
  static String DEFAULT_USER_B = "becky@nist.gov";

  protected String getProtocol() {
    return "sip";
  }

  protected String getSipUserA() {
    return getProtocol() + ":" + DEFAULT_USER_A;
  }

  protected String getSipUserB() {
    return getProtocol() + ":" + DEFAULT_USER_B;
  }

  protected String getSipUserAAddress() {
    return getProtocol() + ":amit@" + ua.getStackAddress() + ':' + myPort;
  }

  protected String getSipUserBAddress(SipPhone phone) {
    return getProtocol() + ":becky@" + phone.getStackAddress() + ':' + myPort;
  }

  protected String getSipUserCAddress(SipPhone phone) {
    return getProtocol() + ":doodah@" + phone.getStackAddress() + ':' + myPort;
  }

  /**
   * Initialize the sipStack and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    properties = getDefaultProperties();
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 5061;
    }

    testProtocol = properties.getProperty("sipunit.test.protocol");

    sipStack = new SipStack(testProtocol, myPort, properties);

    ua = sipStack.createSipPhone(getSipUserA());
    ua.setLoopback(true);
  }

  /**
   * Release the sipStack and a user agent for the test.
   */
  @After
  public void tearDown() throws Exception {
    ua.dispose();
    awaitStackDispose(sipStack);
  }

  @Test
  public void testSendInviteWithRouteHeader() throws Exception {
    // add a Route Header to the
    // INVITE myself
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ub.listenRequestMessage();

    AddressFactory addrFactory = ua.getParent().getAddressFactory();
    HeaderFactory headerFactory = ua.getParent().getHeaderFactory();

    Request invite =
        ua.getParent().getMessageFactory()
            .createRequest("INVITE " + getSipUserB() + " SIP/2.0\r\n\r\n");

    invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
    invite.addHeader(headerFactory.createCSeqHeader((long) 1, Request.INVITE));
    invite.addHeader(headerFactory.createFromHeader(ua.getAddress(), ua.generateNewTag()));

    Address toAddress = addrFactory.createAddress(addrFactory.createURI(getSipUserB()));
    invite.addHeader(headerFactory.createToHeader(toAddress, null));

    Address contactAddress = addrFactory.createAddress(getSipUserAAddress());
    invite.addHeader(headerFactory.createContactHeader(contactAddress));

    invite.addHeader(headerFactory.createMaxForwardsHeader(5));
    ArrayList<ViaHeader> viaHeaders = ua.getViaHeaders();
    invite.addHeader((ViaHeader) viaHeaders.get(0));

    // create and add the Route Header
    Address routeAddress = addrFactory.createAddress(getSipUserBAddress(ub) + '/' + testProtocol);
    invite.addHeader(headerFactory.createRouteHeader(routeAddress));

    SipTransaction trans = ua.sendRequestWithTransaction(invite, false, null);
    assertNotNull(ua.format(), trans);
    // call sent

    RequestEvent incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response);
    assertNotNull(ub.format(), transb);
    // trying response sent

    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1);
    assertLastOperationSuccess(ub.format(), ub);
    // ringing response sent

    response = ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response.addHeader(ub.getParent().getHeaderFactory().createContactHeader(contact));

    ub.sendReply(transb, response);
    assertLastOperationSuccess(ub.format(), ub);
    // answer response sent

    EventObject responseEvent = ua.waitResponse(trans, 10000);
    // wait for trying

    assertNotNull(ua.format(), responseEvent);
    assertFalse("Operation timed out", responseEvent instanceof TimeoutEvent);

    assertEquals("Should have received TRYING", Response.TRYING, ((ResponseEvent) responseEvent)
        .getResponse().getStatusCode());
    // response(s) received, we're done

    ub.dispose();
  }

  /**
   * Test program sends a request, expects a response followed by a request from the far end and
   * checks for things in that order. The received request beats the received response in actuality.
   * The test program should get both.
   */
  @Test
  public void testRaceConditionRequestBeatsResponse() throws Exception {
    final class PhoneB extends Thread {
      public void run() {
        try {
          SipPhone ub = sipStack.createSipPhone(getSipUserB());
          ub.setLoopback(true);
          ub.listenRequestMessage();
          SipCall callB = ub.createSipCall();

          // wait for session establishment
          callB.waitForIncomingCall(10000);
          assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);
          Dialog dialogB = callB.getLastTransaction().getServerTransaction().getDialog();

          Header allow = ub.getParent().getHeaderFactory().createAllowHeader(Request.INFO);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0, new ArrayList<>(
              Arrays.asList(allow)), null, null);
          assertLastOperationSuccess("b send OK - " + callB.format(), callB);

          // wait for INFO request
          RequestEvent incReq = ub.waitRequest(2000);
          while (incReq != null) {
            if (incReq.getRequest().getMethod().equals(Request.INFO)) {
              break;
            }
            incReq = ub.waitRequest(2000);
          }
          if (incReq == null) {
            fail("didn't get expected INFO message");
          }

          // first - send my INFO request before responding to
          // received INFO
          Request infoRequest = dialogB.createRequest(Request.INFO);

          SipTransaction respTransB = ub.sendRequestWithTransaction(infoRequest, false, dialogB);
          if (null == respTransB) {
            String msg = "B side failed sending INFO: " + ub.getErrorMessage();
            fail(msg);
          }

          // wait for some time, make sure it gets to other side
          Thread.sleep(5000);

          // second - send 200 OK for the received INFO
          Response response =
              ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
          SipTransaction transb = ub.sendReply(incReq, response);
          assertNotNull(ub.format(), transb);

          // wait disconnect
          callB.waitForDisconnect(20000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          assertTrue(callB.respondToDisconnect());

          ub.dispose();
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    ua.listenRequestMessage();

    SipCall callA = ua.createSipCall();

    // first establish the session

    Header allow = ua.getParent().getHeaderFactory().createAllowHeader(Request.INFO);

    callA.initiateOutgoingCall(getSipUserA(), getSipUserB(), ua.getStackAddress() + ':' + myPort
        + '/' + testProtocol, new ArrayList<>(Arrays.asList(allow)), null, null);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);
    Dialog dialogA = callA.getLastTransaction().getClientTransaction().getDialog();

    assertTrue(callA.waitOutgoingCallResponse(10000));
    assertEquals("Unexpected response received", Response.OK, callA.getReturnCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    // do the test

    Request infoRequest = dialogA.createRequest(Request.INFO);

    SipTransaction responseTransaction = ua.sendRequestWithTransaction(infoRequest, false, dialogA);
    if (null == responseTransaction) {
      String msg = "media server failed sending message: " + ua.getErrorMessage();
      fail(msg);
    }

    // first wait for a 200 OK from the app
    EventObject respEvent = ua.waitResponse(responseTransaction, 10000);
    assertNotNull(
        "media server failed waiting for a 200 OK for the INFO message: " + ua.getErrorMessage(),
        respEvent);
    assertTrue(respEvent instanceof ResponseEvent);
    assertEquals(Response.OK, ((ResponseEvent) respEvent).getResponse().getStatusCode());

    // second - verify INFO was received by ua
    RequestEvent incReq = ua.waitRequest(2000);
    assertNotNull(incReq);
    assertEquals(Request.INFO, incReq.getRequest().getMethod());

    // Tear down

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    phoneB.join();
  }

  /**
   * Test program sends a request, expects a response preceded by a request from the far end and
   * checks for things in that order. The received response beats the received request in actuality.
   * The test program should get both.
   */
  @Test
  public void testRaceConditionResponseBeatsRequest() throws Exception {
    final class PhoneB extends Thread {
      public void run() {
        try {
          SipPhone ub = sipStack.createSipPhone(getSipUserB());
          ub.setLoopback(true);
          ub.listenRequestMessage();
          SipCall callB = ub.createSipCall();

          // wait for session establishment
          callB.waitForIncomingCall(10000);
          assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);
          Dialog dialogB = callB.getLastTransaction().getServerTransaction().getDialog();

          Header allow = ub.getParent().getHeaderFactory().createAllowHeader(Request.INFO);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0, new ArrayList<>(
              Arrays.asList(allow)), null, null);
          assertLastOperationSuccess("b send OK - " + callB.format(), callB);

          // wait for INFO request
          RequestEvent incReq = ub.waitRequest(2000);
          while (incReq != null) {
            if (incReq.getRequest().getMethod().equals(Request.INFO)) {
              break;
            }
            incReq = ub.waitRequest(2000);
          }
          if (incReq == null) {
            fail("didn't get expected INFO message");
          }

          // first - send 200 OK for the received INFO
          Response response =
              ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
          SipTransaction transb = ub.sendReply(incReq, response);
          assertNotNull(ub.format(), transb);

          // wait for some time, make sure it gets to other side
          Thread.sleep(5000);

          // second - send my INFO request before responding to
          // received INFO
          Request infoRequest = dialogB.createRequest(Request.INFO);

          SipTransaction respTransB = ub.sendRequestWithTransaction(infoRequest, false, dialogB);
          if (null == respTransB) {
            String msg = "B side failed sending INFO: " + ub.getErrorMessage();
            fail(msg);
          }

          // wait disconnect
          callB.waitForDisconnect(20000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          assertTrue(callB.respondToDisconnect());

          ub.dispose();
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    ua.listenRequestMessage();

    SipCall callA = ua.createSipCall();

    // first establish the session

    Header allow = ua.getParent().getHeaderFactory().createAllowHeader(Request.INFO);

    callA.initiateOutgoingCall(getSipUserA(), getSipUserB(), ua.getStackAddress() + ':' + myPort
        + '/' + testProtocol, new ArrayList<>(Arrays.asList(allow)), null, null);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);
    Dialog dialogA = callA.getLastTransaction().getClientTransaction().getDialog();

    assertTrue(callA.waitOutgoingCallResponse(10000));
    assertEquals("Unexpected response received", Response.OK, callA.getReturnCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    // do the test

    Request infoRequest = dialogA.createRequest(Request.INFO);

    SipTransaction responseTransaction = ua.sendRequestWithTransaction(infoRequest, false, dialogA);
    if (null == responseTransaction) {
      String msg = "media server failed sending message: " + ua.getErrorMessage();
      fail(msg);
    }

    // first - verify INFO was received by ua
    RequestEvent incReq = ua.waitRequest(10000);
    assertNotNull(incReq);
    assertEquals(Request.INFO, incReq.getRequest().getMethod());

    // second - wait for a 200 OK
    EventObject respEvent = ua.waitResponse(responseTransaction, 10000);
    assertNotNull(
        "media server failed waiting for a 200 OK for the INFO message: " + ua.getErrorMessage(),
        respEvent);
    assertTrue(respEvent instanceof ResponseEvent);
    assertEquals(Response.OK, ((ResponseEvent) respEvent).getResponse().getStatusCode());

    // Tear down

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    phoneB.join();
  }

  @Test
  public void testBothSides() throws Exception {
    // test initiateOugoingCall(), passing routing
    // string
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();

    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);

    callB.waitForIncomingCall(10000);
    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.RINGING, null, -1);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("b send OK - " + callB.format(), callB);

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    assertEquals("Unexpected 1st response received", Response.RINGING, callA.getReturnCode());
    assertNotNull("Default response reason not sent", callA.getLastReceivedResponse()
        .getReasonPhrase());
    assertEquals("Unexpected default reason", "Ringing", callA.getLastReceivedResponse()
        .getReasonPhrase());

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 2nd response - " + callA.format(), callA);

    assertEquals("Unexpected 2nd response received", Response.OK, callA.getReturnCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    callA.listenForDisconnect();
    assertLastOperationSuccess("a listen disc - " + callA.format(), callA);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();
    assertLastOperationSuccess("a respond to disc - " + callA.format(), callA);

    ub.dispose();
  }

  @Test
  public void testSipTestCaseMisc() throws Exception {
    // in this test, user a is handled at the SipCall level and user b at the
    // SipSession level (we send a body in the response)
    SipCall callA = ua.createSipCall();
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ub.listenRequestMessage();

    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);

    RequestEvent incReq = ub.waitRequest(10000);
    assertNotNull(ub.format(), incReq);
    // call received

    SipRequest req = new SipRequest(incReq.getRequest());

    // Incoming request - header/body asserts

    assertHeaderPresent(req, "Max-Forwards");
    assertHeaderNotPresent(req, "Max-Forwardss");
    assertHeaderNotContains(req, "Max-Forwards", "71");
    assertHeaderContains(req, "Max-Forwards", "70");
    assertBodyNotPresent(req);
    assertBodyNotContains(req, "e");

    // send TRYING
    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response);
    assertNotNull(ub.format(), transb);
    // trying response sent

    // send message with a body
    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);
    String toTag = ub.generateNewTag();
    response =
        ub.getParent().getMessageFactory().createResponse(Response.RINGING, incReq.getRequest()); // why
                                                                                                  // OK
    // doesn't
    // work here?
    response.setReasonPhrase("Hello World");
    ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
    response.addHeader(ub.getParent().getHeaderFactory().createContactHeader(contact));
    ContentTypeHeader ct =
        ub.getParent().getHeaderFactory().createContentTypeHeader("application", "sdp");
    response.setContent("This is a test body", ct);

    ub.sendReply(transb, response);
    assertLastOperationSuccess(ub.format(), ub);
    // message with body sent

    callA.waitOutgoingCallResponse(4000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    while (callA.getReturnCode() == Response.TRYING) {
      callA.waitOutgoingCallResponse(4000);
      assertLastOperationSuccess("a wait nth response - " + callA.format(), callA);
    }

    // Incoming response - header/body asserts

    assertBodyPresent(callA.getLastReceivedResponse());
    assertBodyContains(callA.getLastReceivedResponse(), "his is a test body");

    SipResponse resp = callA.getLastReceivedResponse();
    assertHeaderPresent(resp, "Contact");
    assertHeaderNotPresent(resp, "Contacts");
    assertHeaderNotContains(resp, "Contact", "amit");
    assertHeaderContains(resp, "Contact", "becky");

    // ub needs to send BYE, so SipCall gets a request and can verify
    // higher
    // level request/response asserts
    Request invite = incReq.getRequest();
    ContactHeader callerContact = (ContactHeader) invite.getHeader(ContactHeader.NAME);
    FromHeader partyA = (FromHeader) invite.getHeader(FromHeader.NAME);
    ToHeader partyB = (ToHeader) response.getHeader(ToHeader.NAME);
    CSeqHeader cseq =
        ub.getParent()
            .getHeaderFactory()
            .createCSeqHeader(((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber(),
                Request.BYE);

    Request bye =
        ub.getParent()
            .getMessageFactory()
            .createRequest(
                callerContact.getAddress().getURI(),
                Request.BYE,
                (CallIdHeader) invite.getHeader(CallIdHeader.NAME),
                cseq,
                ub.getParent().getHeaderFactory()
                    .createFromHeader(partyB.getAddress(), partyB.getTag()),
                ub.getParent().getHeaderFactory()
                    .createToHeader(partyA.getAddress(), partyA.getTag()), ub.getViaHeaders(),
                ub.getParent().getHeaderFactory().createMaxForwardsHeader(5));

    bye.addHeader(ub.getParent().getHeaderFactory().createRouteHeader(callerContact.getAddress()));

    assertTrue(callA.listenForDisconnect());
    assertTrue(ub.sendUnidirectionalRequest(bye, false));
    assertTrue(callA.waitForDisconnect(2000));

    // MessageListener level - methods, request/response assertions

    SipRequest receivedBye = callA.getLastReceivedRequest();
    assertNotNull(receivedBye);

    ArrayList<SipRequest> receivedRequests = callA.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    assertEquals(receivedBye, receivedRequests.get(0));

    SipResponse receivedResponse = callA.getLastReceivedResponse();
    assertNotNull(receivedResponse);

    ArrayList<SipResponse> receivedResponses = callA.getAllReceivedResponses();
    int numResponses = receivedResponses.size();
    assertTrue(numResponses >= 2);
    assertEquals(receivedResponse, receivedResponses.get(numResponses - 1));

    assertResponseReceived(SipResponse.TRYING, callA);
    assertResponseReceived("Expected RINGING", SipResponse.RINGING, callA);
    assertResponseNotReceived(SipResponse.OK, callA);
    assertResponseNotReceived("Didn't expect OK", SipResponse.OK, callA);

    assertResponseReceived(SipResponse.RINGING, SipRequest.INVITE,
        ((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber(), callA);
    assertResponseReceived("Expected RINGING", SipResponse.RINGING, SipRequest.INVITE,
        ((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber(), callA);
    assertResponseNotReceived(SipResponse.RINGING, SipRequest.INVITE,
        ((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber() + 1, callA);
    assertResponseNotReceived("Didn't expect this", SipResponse.RINGING, SipRequest.ACK,
        ((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber(), callA);

    long receivedCseqSeqnum = ((CSeqHeader) invite.getHeader(CSeqHeader.NAME)).getSeqNumber();

    assertRequestReceived(SipRequest.BYE, callA);
    assertRequestReceived(SipRequest.BYE, receivedCseqSeqnum, callA);
    assertRequestReceived("Expected a BYE", SipRequest.BYE, callA);
    assertRequestReceived("Wrong CSEQ sequence number", SipRequest.BYE, receivedCseqSeqnum, callA);
    assertRequestNotReceived(SipRequest.INVITE, callA);
    assertRequestNotReceived("Didn't expect a NOTIFY", SipRequest.NOTIFY, callA);
    assertRequestNotReceived(SipRequest.BYE, receivedCseqSeqnum + 1, callA);
    assertRequestNotReceived("Didn't expect a SUBSCRIBE", SipRequest.SUBSCRIBE, receivedCseqSeqnum,
        callA);

    ub.dispose();
  }

  @Test
  public void testMakeCallFail() {
    ua.makeCall(getSipUserB(), SipResponse.RINGING, 1000, ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationFail(ua.format(), ua);
    assertEquals(ua.getReturnCode(), SipSession.TIMEOUT_OCCURRED);
  }

  /**
   * Test: asynchronous SipPhone.makeCall() callee disc
   */
  @Test
  public void testMakeCallCalleeDisconnect() throws Exception {
    // test the nonblocking version
    // of
    // SipPhone.makeCall() - A CALLS B
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callB = ub.createSipCall(); // incoming call
    callB.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);
    // or assertNotNull(a)

    assertTrue(callB.waitForIncomingCall(5000));
    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
    awaitReceivedResponses(callA, 1);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    awaitReceivedResponses(callA, 2);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(3000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);
    callA.respondToDisconnect();

    ub.dispose();
  }

  /**
   * Test: asynchronous SipPhone.makeCall() caller disc
   */
  @Test
  public void testMakeCallCallerDisconnect() throws Exception {
    // test the nonblocking version
    // of
    // SipPhone.makeCall() - A CALLS B
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callB = ub.createSipCall(); // incoming call
    callB.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    awaitReceivedResponses(callA, 1);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitReceivedResponses(callA, 2);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callB.listenForDisconnect();

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(3000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
    callB.respondToDisconnect();

    ub.dispose();
  }

  @Test
  public void testBothSidesCallerDisc() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall()
    final class PhoneB extends Thread {
      public void run() {
        try {
          SipPhone ub = sipStack.createSipPhone(getSipUserB());
          ub.setLoopback(true);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);
          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(30000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          ub.dispose();
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    SipCall callA =
        ua.makeCall(getSipUserB(), SipResponse.OK, 5000, ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertAnswered("Outgoing call leg not answered", callA);

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(2000);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    phoneB.join();
  }

  @Test
  public void testMakeCallExtraJainsipParms() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall() with extra JAIN SIP parameters
    final class PhoneB extends Thread {
      public void run() {
        try {
          SipPhone ub = sipStack.createSipPhone(getSipUserB());
          ub.setLoopback(true);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);

          assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
          assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
              "applicationn/texxt");
          assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
          assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
          assertBodyContains(callB.getLastReceivedRequest(), "my body");

          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(30000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          ub.dispose();
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    // set up outbound INVITE contents

    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ua.getParent().getHeaderFactory().createPriorityHeader("5"));
    addnlHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact = ua.getParent().getAddressFactory().createURI(getSipUserCAddress(ua));
    Address bogusAddr = ua.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ua.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ua.getParent().getHeaderFactory().createMaxForwardsHeader(62));

    SipCall callA =
        ua.makeCall(getSipUserB(), SipResponse.OK, 5000, ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol, addnlHeaders, replaceHeaders, "my body");
    assertLastOperationSuccess(ua.format(), ua);

    assertAnswered("Outgoing call leg not answered", callA);

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(2000);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    phoneB.join();
  }

  @Test
  public void testMakeCallExtraStringParms() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall() with extra String parameters
    final class PhoneB extends Thread {
      public void run() {
        try {
          SipPhone ub = sipStack.createSipPhone(getSipUserB());
          ub.setLoopback(true);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);

          assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
          assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
              "applicationn/texxt");
          assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
          assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
          assertBodyContains(callB.getLastReceivedRequest(), "my body");

          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(30000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          ub.dispose();
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    // set up outbound INVITE contents

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <" + getSipUserCAddress(ua) + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    SipCall callA =
        ua.makeCall(getSipUserB(), SipResponse.OK, 5000, ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol, "my body", "applicationn", "texxt", addnlHeaders, replaceHeaders);
    assertLastOperationSuccess(ua.format(), ua);

    assertAnswered("Outgoing call leg not answered", callA);

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(2000);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    phoneB.join();
  }

  @Test
  public void testNonblockingMakeCallExtraJainsipParms() throws Exception {
    // test the
    // nonblocking
    // SipPhone.makeCall() with extra JAIN SIP parameters
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callB = ub.createSipCall(); // incoming call
    callB.listenForIncomingCall();

    // set up outbound INVITE contents

    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ua.getParent().getHeaderFactory().createPriorityHeader("5"));
    addnlHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact = ua.getParent().getAddressFactory().createURI(getSipUserCAddress(ua));
    Address bogusAddr = ua.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ua.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ua.getParent().getHeaderFactory().createMaxForwardsHeader(62));

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol,
            addnlHeaders, replaceHeaders, "my body");
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));

    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callB.getLastReceivedRequest(), "my body");

    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    awaitReceivedResponses(callA, 1);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitReceivedResponses(callA, 2);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() == 2);
    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callB.listenForDisconnect();

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(3000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
    callB.respondToDisconnect();

    ub.dispose();
  }

  @Test
  public void testNonblockingMakeCallExtraStringParms() throws Exception {
    // test the
    // nonblocking
    // version of SipPhone.makeCall() with extra String parameters
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callB = ub.createSipCall(); // incoming call
    callB.listenForIncomingCall();

    // set up outbound INVITE contents

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <" + getSipUserCAddress(ua) + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol,
            "my body", "applicationn", "texxt", addnlHeaders, replaceHeaders);

    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));

    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callB.getLastReceivedRequest(), "my body");

    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    awaitReceivedResponses(callA, 1);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitReceivedResponses(callA, 2);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callB.listenForDisconnect();

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(3000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
    callB.respondToDisconnect();

    ub.dispose();
  }

  @Test
  public void testMultipleReplies() throws Exception {
    // test: sendRequestWithTrans(Request invite),
    // sendReply(Response Trying, no toTag, no contact, ...),
    // sendReply(statusCode Ringing, toTag, contact, ...),
    // sendReply(Response OK, no toTag, contact, ...),
    // waitResponse()

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ub.listenRequestMessage();

    AddressFactory addrFactory = ua.getParent().getAddressFactory();
    HeaderFactory headerFactory = ua.getParent().getHeaderFactory();

    Request invite =
        ua.getParent().getMessageFactory()
            .createRequest("INVITE " + getSipUserB() + " SIP/2.0\r\n\r\n");

    invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
    invite.addHeader(headerFactory.createCSeqHeader((long) 1, Request.INVITE));
    invite.addHeader(headerFactory.createFromHeader(ua.getAddress(), ua.generateNewTag()));

    Address toAddress = addrFactory.createAddress(addrFactory.createURI(getSipUserB()));
    invite.addHeader(headerFactory.createToHeader(toAddress, null));

    Address contactAddress = addrFactory.createAddress(getSipUserAAddress());
    invite.addHeader(headerFactory.createContactHeader(contactAddress));

    invite.addHeader(headerFactory.createMaxForwardsHeader(5));
    ArrayList<ViaHeader> viaHeaders = ua.getViaHeaders();
    invite.addHeader((ViaHeader) viaHeaders.get(0));

    Address routeAddress = addrFactory.createAddress(getSipUserBAddress(ub) + '/' + testProtocol);
    invite.addHeader(headerFactory.createRouteHeader(routeAddress));

    SipTransaction trans = ua.sendRequestWithTransaction(invite, false, null);
    assertNotNull(ua.format(), trans);
    // call sent

    RequestEvent incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response);
    assertNotNull(ub.format(), transb);
    // trying response sent

    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1);
    assertLastOperationSuccess(ub.format(), ub);
    // ringing response sent

    response = ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response.addHeader(ub.getParent().getHeaderFactory().createContactHeader(contact));

    ub.sendReply(transb, response);
    assertLastOperationSuccess(ub.format(), ub);
    // answer response sent

    EventObject responseEvent = ua.waitResponse(trans, 10000);
    // wait for trying

    assertNotNull(ua.format(), responseEvent);
    assertFalse("Operation timed out", responseEvent instanceof TimeoutEvent);

    assertEquals("Should have received TRYING", Response.TRYING, ((ResponseEvent) responseEvent)
        .getResponse().getStatusCode());
    // trying received

    responseEvent = ua.waitResponse(trans, 10000);
    // wait for ringing

    assertNotNull(ua.format(), responseEvent);
    assertFalse("Operation timed out", responseEvent instanceof TimeoutEvent);

    assertEquals("Should have received RINGING", Response.RINGING, ((ResponseEvent) responseEvent)
        .getResponse().getStatusCode());
    // ringing received

    responseEvent = ua.waitResponse(trans, 10000);
    // wait for answer

    assertNotNull(ua.format(), responseEvent);
    assertFalse("Operation timed out", responseEvent instanceof TimeoutEvent);

    assertEquals("Should have received OK", Response.OK, ((ResponseEvent) responseEvent)
        .getResponse().getStatusCode());
    // answer received

    ub.dispose();
  }

  // this method tests re-invite from b to a,
  // TestWithProxyAuthentication does the other direction
  @Test
  public void testReinvite() throws Exception {
    LOG.trace("testAdditionalMessageParms"); // using reinvite

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600));
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.OK, callA);
    assertTrue(callA.sendInviteOkAck());

    // send request - test reinvite with no specific parameters

    callA.listenForReinvite();
    SipTransaction siptransB = callB.sendReinvite(null, null, (String) null, null, null);
    assertNotNull(siptransB);
    SipTransaction siptransA = callA.waitForReinvite(1000);
    assertNotNull(siptransA);

    SipMessage req = callA.getLastReceivedRequest();
    String origContactUriB =
        ((ContactHeader) req.getMessage().getHeader(ContactHeader.NAME)).getAddress().getURI()
            .toString();

    // check contact info
    assertEquals(ub.getContactInfo().getURI(), origContactUriB);
    assertHeaderNotContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(req, ContentTypeHeader.NAME);
    assertBodyNotPresent(req);

    // check additional headers
    assertHeaderNotPresent(req, PriorityHeader.NAME);
    assertHeaderNotPresent(req, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "70");

    // send response - test new contact only

    String origContactUriA = ua.getContactInfo().getURI();
    String contactNoLrA = origContactUriA.substring(0, origContactUriA.lastIndexOf("lr") - 1);
    assertTrue(callA.respondToReinvite(siptransA, SipResponse.OK, "ok reinvite response", -1,
        contactNoLrA, null, null, (String) null, null));

    assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    while (callB.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    }

    // check response code
    SipResponse response = callB.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite response", response.getReasonPhrase());

    // check contact info
    assertEquals(ua.getContactInfo().getURI(), contactNoLrA); // changed
    assertFalse(origContactUriA.equals(contactNoLrA));
    assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, contactNoLrA);
    assertHeaderNotContains(response, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(response, ContentTypeHeader.NAME);
    assertBodyNotPresent(response);

    // check additional headers
    assertHeaderNotPresent(response, PriorityHeader.NAME);
    assertHeaderNotPresent(response, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(response, ContentLengthHeader.NAME, "0");

    // send ACK
    assertTrue(callB.sendReinviteOkAck(siptransB));
    assertTrue(callA.waitForAck(1000));

    // send request - test contact and body

    callA.listenForReinvite();
    String contactNoLrB = origContactUriB.substring(0, origContactUriB.lastIndexOf("lr") - 1);
    siptransB = callB.sendReinvite(contactNoLrB, "My DisplayName", "my reinvite", "app", "subapp");
    assertNotNull(siptransB);
    siptransA = callA.waitForReinvite(1000);
    assertNotNull(siptransA);

    req = callA.getLastReceivedRequest();

    // check contact info
    assertEquals(ub.getContactInfo().getURI(), contactNoLrB); // changed
    assertFalse(origContactUriB.equals(contactNoLrB));
    assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
    assertHeaderContains(req, ContactHeader.NAME, contactNoLrB);
    assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderContains(req, ContentTypeHeader.NAME, "subapp");
    assertBodyContains(req, "my reinvite");

    // check additional headers
    assertHeaderNotPresent(req, PriorityHeader.NAME);
    assertHeaderNotPresent(req, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "70");

    // send response - test body only

    assertTrue(callA.respondToReinvite(siptransA, SipResponse.OK, "ok reinvite response", -1, null,
        null, "DooDah", "application", "text"));

    assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    while (callB.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    }

    // check response code
    response = callB.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite response", response.getReasonPhrase());

    // check contact info
    assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, contactNoLrA);
    assertHeaderNotContains(response, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderPresent(response, ContentTypeHeader.NAME);
    ContentTypeHeader ctHeader =
        (ContentTypeHeader) response.getMessage().getHeader(ContentTypeHeader.NAME);
    assertEquals("application", ctHeader.getContentType());
    assertEquals("text", ctHeader.getContentSubType());
    assertBodyContains(response, "DooDah");

    // check additional headers
    assertHeaderNotPresent(response, PriorityHeader.NAME);
    assertHeaderNotPresent(response, ReasonHeader.NAME);

    // check override headers
    // done, content sub type not overidden

    // send ACK
    // with JSIP additional, replacement headers, and body
    ArrayList<Header> addnlHeaders = new ArrayList<>(2);
    ReasonHeader reasonHeader =
        ub.getParent().getHeaderFactory().createReasonHeader("SIP", 44, "dummy");
    addnlHeaders.add(reasonHeader);
    ctHeader = ub.getParent().getHeaderFactory().createContentTypeHeader("mytype", "mysubtype");
    addnlHeaders.add(ctHeader);

    ArrayList<Header> replaceHeaders = new ArrayList<>(2);
    MaxForwardsHeader header = ub.getParent().getHeaderFactory().createMaxForwardsHeader(29);
    replaceHeaders.add(header);
    PriorityHeader priHeader =
        ub.getParent().getHeaderFactory().createPriorityHeader(PriorityHeader.URGENT);
    replaceHeaders.add(priHeader);

    assertTrue(callB.sendReinviteOkAck(siptransB, addnlHeaders, replaceHeaders, "ack body"));
    assertTrue(callA.waitForAck(1000));
    SipRequest reqAck = callA.getLastReceivedRequest();
    assertHeaderContains(reqAck, ReasonHeader.NAME, "dummy");
    assertHeaderContains(reqAck, MaxForwardsHeader.NAME, "29");
    assertHeaderContains(reqAck, PriorityHeader.NAME, "gent");
    assertHeaderContains(reqAck, ContentTypeHeader.NAME, "mysubtype");
    assertBodyContains(reqAck, "ack body");

    // send request - test additional and replace headers (JAIN SIP)

    callA.listenForReinvite();

    addnlHeaders = new ArrayList<>(2);
    priHeader = ub.getParent().getHeaderFactory().createPriorityHeader(PriorityHeader.URGENT);
    reasonHeader = ub.getParent().getHeaderFactory().createReasonHeader("SIP", 41, "I made it up");
    addnlHeaders.add(priHeader);
    addnlHeaders.add(reasonHeader);

    replaceHeaders = new ArrayList<>(1);
    header = ub.getParent().getHeaderFactory().createMaxForwardsHeader(21);
    replaceHeaders.add(header);

    siptransB = callB.sendReinvite(null, null, addnlHeaders, replaceHeaders, "no body");
    assertNotNull(siptransB);
    siptransA = callA.waitForReinvite(1000);
    assertNotNull(siptransA);

    req = callA.getLastReceivedRequest();

    // check contact info
    assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
    assertHeaderContains(req, ContactHeader.NAME, contactNoLrB);
    assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(req, ContentTypeHeader.NAME);
    assertBodyNotPresent(req);

    // check additional headers
    assertHeaderContains(req, PriorityHeader.NAME, PriorityHeader.URGENT);
    assertHeaderContains(req, ReasonHeader.NAME, "41");

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "21");

    // test everything

    ArrayList<String> addnlStrHeaders = new ArrayList<>();
    ArrayList<String> replaceStrHeaders = new ArrayList<>();
    addnlStrHeaders.add("Priority: Normal");
    addnlStrHeaders.add("Reason: SIP; cause=42; text=\"I made it up\"");

    // TODO, find another header to replace, stack corrects this one
    // replaceHeaders.add("Content-Length: 4");

    assertTrue(callA.respondToReinvite(siptransA, SipResponse.OK, "ok reinvite last response", -1,
        origContactUriA, "Original info", "DooDahDay", "applicationn", "sdp", addnlStrHeaders,
        replaceStrHeaders));

    assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    while (callB.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    }

    // check response code
    response = callB.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite last response", response.getReasonPhrase());

    // check contact info
    assertEquals(ua.getContactInfo().getURI(), origContactUriA); // changed
    assertFalse(origContactUriA.equals(contactNoLrA));
    assertHeaderContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, origContactUriA);
    assertHeaderContains(response, ContactHeader.NAME, "Original info");

    // check body
    assertHeaderPresent(response, ContentTypeHeader.NAME);
    ctHeader = (ContentTypeHeader) response.getMessage().getHeader(ContentTypeHeader.NAME);
    assertEquals("applicationn", ctHeader.getContentType());
    assertEquals("sdp", ctHeader.getContentSubType());
    assertBodyContains(response, "DooD");

    // check additional headers
    assertHeaderContains(response, PriorityHeader.NAME, PriorityHeader.NORMAL);
    assertHeaderContains(response, ReasonHeader.NAME, "42");

    // check override headers - see TODO above
    // assertHeaderContains(response, ContentLengthHeader.NAME, "4");

    // send ACK
    assertTrue(callB.sendReinviteOkAck(siptransB));
    assertTrue(callA.waitForAck(1000));

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();

    ub.dispose();
  }

  @Test
  public void testSendReplySipTransactionExtraInfo() throws Exception {
    // test sendReply(SipTransaction, ....) options
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ub.listenRequestMessage();

    SipCall callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    RequestEvent incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response); // sendReply(RequestEvent)
    assertNotNull(ub.format(), transb);
    // initial trying response sent

    // receive it on the 'a' side

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    assertEquals("Unexpected 1st response received", Response.TRYING, callA.getReturnCode());

    // (a) send reply with additional JSIP Headers but no body

    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);
    String toTag = ub.generateNewTag();
    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(12));
    addnlHeaders.add(ub.getParent().getHeaderFactory().createContentTypeHeader("app", "subtype"));
    // no body - should receive msg with body length 0 and with content
    // type header
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    SipMessage resp = callA.getLastReceivedResponse();
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

    // (b) send reply with additional JSIP Header (ContentTypeHeader)
    // and body
    addnlHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createContentTypeHeader("bapp", "subtype"));
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyContains(resp, "my body");

    // (c) send reply with other additional JSIP Header (not
    // ContentTypeHeader) and body
    addnlHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(11));
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
    assertBodyNotPresent(resp);

    // (d) send reply with replace JSIP Header (test replacement),
    // ignored body
    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact = ub.getParent().getAddressFactory().createURI(getSipUserCAddress(ub));
    Address bogusAddr = ub.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ub.getParent().getHeaderFactory().createContactHeader(bogusAddr));
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, replaceHeaders,
        "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
    assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

    // (e) send reply with replace JSIP Header (test addition)
    replaceHeaders.clear();
    replaceHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(50));
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, replaceHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "becky");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

    // (f) send reply with all - additional,replace JSIP Headers & body
    addnlHeaders.clear();
    replaceHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createToHeader(bogusAddr, "mytag")); // verify
                                                                                            // ignored
    addnlHeaders.add(ub.getParent().getHeaderFactory()
        .createContentTypeHeader("application", "text"));// for
    // body
    replaceHeaders.add(ub.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(60)); // verify
    // addition
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, addnlHeaders, replaceHeaders,
        "my new body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
    assertBodyContains(resp, "my new body");
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");;

    // now for the String header version:

    // (a') send reply with additional String Headers & content type
    // info but no body

    ArrayList<String> addnlStrHeaders = new ArrayList<>();
    addnlStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(12).toString());
    // no body - should receive msg with body length 0 and with content
    // type header
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, "app", "subtype",
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

    // (b') send reply with ContentTypeHeader info
    // and body
    addnlStrHeaders.clear();
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my body", "bapp", "subtype",
        null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyContains(resp, "my body");

    // (c') send reply with other additional String Header (not
    // ContentType info) and body
    addnlStrHeaders.clear();
    addnlStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(11).toString());
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my body", null, null,
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
    assertBodyNotPresent(resp);

    // (d') send reply with replace String Header (test replacement),
    // ignored body
    ArrayList<String> replaceStrHeaders = new ArrayList<>();
    replaceStrHeaders.add("Contact: <" + getProtocol() + ":doodah@192.168.1.101:5061>");
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my body", null, null, null,
        replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
    assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

    // (e') send reply with replace String Header (test addition)
    replaceStrHeaders.clear();
    replaceStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(50).toString());
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, null, null, null,
        replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "becky");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

    // (f') send reply with all - additional,replace String Headers,
    // CTinfo & body
    addnlStrHeaders.clear();
    replaceStrHeaders.clear();
    replaceStrHeaders.add("Contact: <" + getProtocol() + ":doodah@192.168.1.101:5061>"); // verify
    // replacement
    replaceStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(60).toString()); // verify
    // addition
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my new body", "application",
        "text", replaceStrHeaders, replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
    assertBodyContains(resp, "my new body");
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");

    // (g') send reply with bad String headers

    replaceStrHeaders.clear();
    replaceStrHeaders.add("Max-Forwards");
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, null, null, null,
        replaceStrHeaders);
    assertLastOperationFail(ub);
    assertTrue(ub.format().indexOf("no HCOLON") != -1);

    // (h') send reply with partial content type parms and body, no
    // addnl headers

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my body", null, "subtype",
        null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);

    // (i') send reply with partial content type parms and body, other
    // addnl headers

    addnlStrHeaders.clear();
    addnlStrHeaders.add("Max-Forwards: 66");
    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, "my body", "app", null,
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "66");

    // (j') send reply with nothing

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1, null, null, null, null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ToHeader.NAME, toTag);

    ub.dispose();
  }

  @Test
  @Ignore
  // TODO - investigate intermittent failure at last or next-to-last test
  // in this method. Seem to get an extra INVITE with a different from tag, b
  // replies to one but a is waiting on a reply for the other. This problem
  // wasn't seen until updated the stack to sipx-stable-420 stack branch
  // (1.2.148). Running this method by itself never failed but when running
  // the whole class the intermittent failure happens in this method.
  public void testSendReplyRequestEventExtraInfo() throws Exception {
    // test sendReply(RequestEvent, ....) options

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ub.listenRequestMessage();

    SipCall callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    RequestEvent incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response); // sendReply(RequestEvent)
    assertNotNull(ub.format(), transb);

    // receive it on the 'a' side

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    assertEquals("Unexpected 1st response received", Response.TRYING, callA.getReturnCode());

    // (a) send reply with additional JSIP Headers but no body

    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);
    String toTag = ub.generateNewTag();
    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(12));
    addnlHeaders.add(ub.getParent().getHeaderFactory().createContentTypeHeader("app", "subtype"));
    // no body - should receive msg with body length 0 and with content
    // type header
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    SipMessage resp = callA.getLastReceivedResponse();
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

    // (b) send reply with additional JSIP Header (ContentTypeHeader)
    // and body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createContentTypeHeader("bapp", "subtype"));
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyContains(resp, "my body");

    // (c) send reply with other additional JSIP Header (not
    // ContentTypeHeader) and body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(11));
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, addnlHeaders, null, "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
    assertBodyNotPresent(resp);

    // (d) send reply with replace JSIP Header (test replacement),
    // ignored body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact = ub.getParent().getAddressFactory().createURI(getSipUserCAddress(ub));
    Address bogusAddr = ub.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ub.getParent().getHeaderFactory().createContactHeader(bogusAddr));
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, replaceHeaders,
        "my body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
    assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

    // (e) send reply with replace JSIP Header (test addition)
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    replaceHeaders.clear();
    replaceHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(50));
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, replaceHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "becky");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

    // (f) send reply with all - additional,replace JSIP Headers & body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlHeaders.clear();
    replaceHeaders.clear();
    addnlHeaders.add(ub.getParent().getHeaderFactory().createToHeader(bogusAddr, "mytag")); // verify
                                                                                            // ignored
    addnlHeaders.add(ub.getParent().getHeaderFactory()
        .createContentTypeHeader("application", "text"));// for
    // body
    replaceHeaders.add(ub.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(60)); // verify
    // addition
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, addnlHeaders, replaceHeaders,
        "my new body");
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
    assertBodyContains(resp, "my new body");
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");

    // now for the String header version:

    // (a') send reply with additional String Headers & content type
    // info but no body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    ArrayList<String> addnlStrHeaders = new ArrayList<>();
    addnlStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(12).toString());
    // no body - should receive msg with body length 0 and with content
    // type header
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, "app", "subtype",
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

    // (b') send reply with ContentTypeHeader info
    // and body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlHeaders.clear();
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my body", "bapp", "subtype",
        null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
    assertBodyContains(resp, "my body");

    // (c') send reply with other additional String Header (not
    // ContentType info) and body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlStrHeaders.clear();
    addnlStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(11).toString());
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my body", null, null,
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
    assertBodyNotPresent(resp);

    // (d') send reply with replace String Header (test replacement),
    // ignored body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    ArrayList<String> replaceStrHeaders = new ArrayList<>();
    replaceStrHeaders.add("Contact: <" + getProtocol() + ":doodah@192.168.1.101:5061>");
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my body", null, null, null,
        replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
    assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

    // (e') send reply with replace String Header (test addition)
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    replaceStrHeaders.clear();
    replaceStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(50).toString());
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, null, null, null,
        replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ContactHeader.NAME, "becky");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

    // (f') send reply with all - additional,replace String Headers,
    // CTinfo & body
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlStrHeaders.clear();
    replaceStrHeaders.clear();
    addnlStrHeaders.add(ub.getParent().getHeaderFactory().createToHeader(bogusAddr, "mytag")
        .toString()); // verify
    // ignored
    replaceStrHeaders.add("Contact: <" + getProtocol() + ":doodah@192.168.1.101:5061>"); // verify
    // replacement
    replaceStrHeaders.add(ub.getParent().getHeaderFactory().createMaxForwardsHeader(60).toString()); // verify
    // addition
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my new body", "application",
        "text", addnlStrHeaders, replaceStrHeaders);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
    assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
    assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
    assertBodyContains(resp, "my new body");
    assertHeaderContains(resp, ContactHeader.NAME, "doodah");
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");

    // (g') send reply with bad String headers
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    replaceStrHeaders.clear();
    replaceStrHeaders.add("Max-Forwards");
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, null, null, null,
        replaceStrHeaders);
    assertLastOperationFail(ub);
    assertTrue(ub.format().indexOf("no HCOLON") != -1);

    // (h') send reply with partial content type parms and body, no
    // addnl Headers
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my body", null, "subtype",
        null, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);

    // (i') send reply with partial content type parms and body, other
    // addnl Headers
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    addnlStrHeaders.clear();
    addnlStrHeaders.add("Max-Forwards: 66");
    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, "my body", "app", null,
        addnlStrHeaders, null);
    assertLastOperationSuccess(ub.format(), ub);

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, MaxForwardsHeader.NAME, "66");

    // (j') send reply with nothing
    callA.dispose(); // recreate the call, can only call
    // sendReply(RequestEvent,..) once for the same request.
    callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);
    // call sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    ub.sendReply(incReq, Response.RINGING, null, toTag, contact, -1, null, null, null, null, null);
    assertLastOperationSuccess(ub.format(), ub);

    LOG.trace("about to wait for RINGING");

    // receive it on the 'a' side
    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait response - " + callA.format(), callA);
    assertEquals("Unexpected response received", Response.RINGING, callA.getReturnCode());
    // check parms in reply
    resp = callA.getLastReceivedResponse();
    assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
    assertBodyNotPresent(resp);
    assertHeaderContains(resp, ToHeader.NAME, toTag);

    ub.dispose();
  }

  // this method tests cancel from a to b
  @Test
  public void testCancel() throws Exception {
    LOG.trace("testCancelWithoutHeader");
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", -1));
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.RINGING, callA);

    // Initiate the Cancel
    callB.listenForCancel();
    SipTransaction cancel = callA.sendCancel();
    assertNotNull(cancel);

    // Respond to the Cancel
    SipTransaction trans1 = callB.waitForCancel(2000);
    callB.stopListeningForRequests();
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1));
    callA.waitForCancelResponse(cancel, 5000);
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);

    // close the INVITE transaction
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
    awaitReceivedResponses(callA, 3);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    callA.respondToDisconnect();
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    ub.dispose();
  }

  @Test
  public void testCancelAfter100Trying() throws Exception {
    LOG.trace("testCancelAfter100Trying");

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.TRYING, "Trying", -1));
    assertLastOperationSuccess("b send TRYING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.TRYING, callA);

    // Initiate the Cancel
    callB.listenForCancel();
    SipTransaction cancel = callA.sendCancel();
    assertNotNull(cancel);

    // Respond to the Cancel
    SipTransaction trans1 = callB.waitForCancel(1000);
    callB.stopListeningForRequests();
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1));
    callA.waitForCancelResponse(cancel, 2000);
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);

    // close the INVITE transaction
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
    awaitReceivedResponses(callA, 3);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);

    // done, finish up
    ub.dispose();
  }

  @Test
  public void testCancelBeforeInvite() throws Exception {
    LOG.trace("testCancelBeforeInvite");

    SipCall callA = ua.createSipCall();
    SipTransaction cancel = callA.sendCancel();
    assertNull(cancel);
    assertEquals(SipSession.INVALID_OPERATION, callA.getReturnCode());
  }

  @Test
  public void testCancelBeforeAnyResponse() throws Exception {
    LOG.trace("testCancelBeforeAnyResponse");

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // send INVITE
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(1000));

    // Initiate the Cancel
    SipTransaction cancel = callA.sendCancel();
    assertNull(cancel);
    assertEquals(SipSession.INVALID_OPERATION, callA.getReturnCode());

    // done, finish up
    ub.dispose();
  }

  @Test
  public void testCancelAfterFinalResponse() throws Exception {
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitReceivedResponses(callA, 1);

    assertAnswered("Outgoing call leg not answered", callA);

    // Initiate the Cancel
    SipTransaction cancel = callA.sendCancel();
    assertNull(cancel);
    assertEquals(SipSession.INVALID_OPERATION, callA.getReturnCode());

    // done, finish up
    ub.dispose();
  }

  // this method tests cancel from a to b
  @Test
  public void testCancelWith481() throws Exception {
    LOG.trace("testCancelWithoutHeaderWith481");
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);
    assertTrue(callB.waitForIncomingCall(5000));
    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", -1);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.RINGING, callA);

    // Initiate the Cancel
    callB.listenForCancel();
    SipTransaction cancel = callA.sendCancel();

    // Take the call and assert Cancel received
    SipTransaction trans1 = callB.waitForCancel(2000);
    callA.waitOutgoingCallResponse(500);
    assertTrue("200 OK FOR INVITE NOT SENT", callB.sendIncomingCallResponse(Response.OK, "OK", -1));
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    awaitReceivedResponses(callA, 2);
    assertResponseReceived("200 OK FOR INVITE NOT RECEIVED", Response.OK, callA);

    // Respond to the 200 OK INVITE and Verify ACK
    callB.listenForAck();
    assertTrue("OK ACK NOT SENT", callA.sendInviteOkAck());
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    assertTrue("ACK NOT RECEIVED", callB.waitForAck(5000));
    assertRequestReceived("ACK NOT RECEIVED", Request.ACK, callB);

    // Respond To Cancel ( Send 481 CALL_OR_TRANSACTION_DOES_NOT_EXIST
    // TO THE CANCEL )
    callA.waitForCancelResponse(cancel, 2000);
    assertTrue("481 NOT SENT", callB.respondToCancel(trans1,
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, "Request Terminated", -1));
    assertTrue(callA.waitForCancelResponse(cancel, 500));
    assertResponseReceived("481 Call/Transaction Does Not Exist NOT RECEIVED",
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, callA);

    // Disconnect AND Verify BYE
    // Send 200 OK TO THE BYE
    callB.listenForDisconnect();
    assertTrue("BYE NOT SENT", callA.disconnect());
    callB.waitForDisconnect(1000);
    assertRequestReceived("BYE NOT RECEIVED", Request.BYE, callB);
    assertTrue("DISCONNECT OK", callB.respondToDisconnect());
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    ub.dispose();
  }

  @Test
  public void testCancelExtraJainsipParms() throws Exception {
    LOG.trace("testCancelExtraJainsipParms");
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", -1));
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.RINGING, callA);

    // Initiate a Cancel with extra Jain SIP parameters
    callB.listenForCancel();

    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ua.getParent().getHeaderFactory().createPriorityHeader("5"));
    addnlHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact = ua.getParent().getAddressFactory().createURI(getSipUserCAddress(ua));
    Address bogusAddr = ua.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ua.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ua.getParent().getHeaderFactory().createMaxForwardsHeader(62));
    SipTransaction cancel = callA.sendCancel(addnlHeaders, replaceHeaders, "my cancel body");
    assertNotNull(cancel);

    // Verify the received Cancel
    SipTransaction trans1 = callB.waitForCancel(2000);
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callB.getLastReceivedRequest(), "my cancel body");

    // finish off the sequence
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1, addnlHeaders, replaceHeaders,
        "my cancel response body"));
    callA.waitForCancelResponse(cancel, 5000);
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);
    assertHeaderContains(callA.getLastReceivedResponse(), PriorityHeader.NAME, "5");
    assertHeaderContains(callA.getLastReceivedResponse(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callA.getLastReceivedResponse(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callA.getLastReceivedResponse(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callA.getLastReceivedResponse(), "my cancel response body");

    // close the INVITE transaction
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
    awaitReceivedResponses(callA, 3);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    callA.respondToDisconnect();
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    ub.dispose();
  }

  @Test
  public void testCancelExtraStringParms() throws Exception {
    LOG.trace("testCancelExtraStringParms");
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", -1));
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.RINGING, callA);

    // Initiate a Cancel with extra Jain SIP headers as strings
    callB.listenForCancel();

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <" + getSipUserCAddress(ua) + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    SipTransaction cancel =
        callA.sendCancel("my other cancel body", "applicationn", "texxt", addnlHeaders,
            replaceHeaders);
    assertNotNull(cancel);

    // Verify the received Cancel
    SipTransaction trans1 = callB.waitForCancel(2000);
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);

    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callB.getLastReceivedRequest(), "my other cancel body");

    // finish off the sequence
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1, "my other cancel response body",
        "applicationn", "texxt", addnlHeaders, replaceHeaders));
    callA.waitForCancelResponse(cancel, 5000);
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);

    assertHeaderContains(callA.getLastReceivedResponse(), PriorityHeader.NAME, "5");
    assertHeaderContains(callA.getLastReceivedResponse(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callA.getLastReceivedResponse(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callA.getLastReceivedResponse(), MaxForwardsHeader.NAME, "62");
    assertBodyContains(callA.getLastReceivedResponse(), "my other cancel response body");

    // close the INVITE transaction
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
    awaitReceivedResponses(callA, 3);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    callA.respondToDisconnect();
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    ub.dispose();
  }

  @Test
  public void testReceivedRequestResponseEvents() throws Exception {
    LOG.trace("testReceivedRequestResponseEvents");

    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    ClientTransaction transactionA = callA.getLastTransaction().getClientTransaction();
    Dialog dialogA = transactionA.getDialog();

    assertTrue(callB.waitForIncomingCall(5000));
    // verify RequestEvent is accessible
    SipRequest request = callB.getLastReceivedRequest();
    assertNotNull(request);
    assertNotNull(request.getRequestEvent());
    assertEquals(request.getRequestEvent().getRequest(), request.getMessage());
    assertTrue(request.isInvite());
    ServerTransaction transactionB = callB.getLastTransaction().getServerTransaction();
    Dialog dialogB = transactionB.getDialog();

    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600));
    awaitReceivedResponses(callA, 1);

    assertTrue(callA.waitForAnswer(200));
    // verify ResponseEvent is accessible
    SipResponse response = callA.getLastReceivedResponse();
    assertNotNull(response);
    assertNotNull(response.getResponseEvent());
    assertEquals(response.getResponseEvent().getResponse(), response.getMessage());
    assertAnswered(callA);
    assertEquals(transactionA, response.getResponseEvent().getClientTransaction());
    assertEquals(dialogA, response.getResponseEvent().getDialog());

    assertTrue(callA.sendInviteOkAck());
    assertTrue(callB.waitForAck(1000));
    // check RequestEvent
    request = callB.getLastReceivedRequest();
    assertNotNull(request);
    assertNotNull(request.getRequestEvent());
    assertEquals(request.getRequestEvent().getRequest(), request.getMessage());
    assertTrue(request.isAck());
    assertEquals(dialogB, request.getRequestEvent().getServerTransaction().getDialog());

    callA.listenForReinvite();
    SipTransaction siptransB = callB.sendReinvite(null, null, "my reinvite", "app", "subapp");
    assertNotNull(siptransB);
    assertEquals(dialogB, siptransB.getClientTransaction().getDialog());
    SipTransaction siptransA = callA.waitForReinvite(1000);
    assertNotNull(siptransA);
    // verify RequestEvent
    request = callA.getLastReceivedRequest();
    assertNotNull(request);
    assertNotNull(request.getRequestEvent());
    assertEquals(request.getRequestEvent().getRequest(), request.getMessage());
    assertTrue(request.isInvite());
    assertEquals(dialogA, siptransA.getServerTransaction().getDialog());
    assertEquals(dialogA, request.getRequestEvent().getDialog());

    // spot check request message received
    URI receivedContactUri =
        ((ContactHeader) request.getMessage().getHeader(ContactHeader.NAME)).getAddress().getURI();
    assertEquals(ub.getContactInfo().getURI(), receivedContactUri.toString());
    assertTrue(ub.getContactInfo().getURIasURI().equals(receivedContactUri));
    assertHeaderContains(request, ContentTypeHeader.NAME, "subapp");
    assertBodyContains(request, "my reinvite");

    // send response
    assertTrue(callA.respondToReinvite(siptransA, SipResponse.OK, "ok reinvite response", -1, null,
        null, null, (String) null, null));

    assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    while (callB.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callB.waitReinviteResponse(siptransB, 2000));
    }
    // verify ResponseEvent
    response = callB.getLastReceivedResponse();
    assertNotNull(response);
    assertNotNull(response.getResponseEvent());
    assertEquals(response.getResponseEvent().getResponse(), response.getMessage());
    assertEquals("ok reinvite response", response.getReasonPhrase());
    assertEquals(siptransB.getClientTransaction(), response.getResponseEvent()
        .getClientTransaction());

    // send ACK
    assertTrue(callB.sendReinviteOkAck(siptransB));
    assertTrue(callA.waitForAck(1000));

    // verify RequestEvent
    request = callA.getLastReceivedRequest();
    assertNotNull(request);
    assertNotNull(request.getRequestEvent());
    assertEquals(request.getRequestEvent().getRequest(), request.getMessage());
    assertTrue(request.isAck());

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);
    assertEquals(dialogB, callB.getLastTransaction().getClientTransaction().getDialog());

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    // verify RequestEvent
    request = callA.getLastReceivedRequest();
    assertNotNull(request);
    assertNotNull(request.getRequestEvent());
    assertEquals(request.getRequestEvent().getRequest(), request.getMessage());
    assertTrue(request.isBye());

    callA.respondToDisconnect();

    ub.dispose();
  }

  @Test
  public void testCancelRequestResponseEvents() throws Exception {
    LOG.trace("testCancelRequestResponseEvents");
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);
    Dialog dialogA = callA.getLastTransaction().getClientTransaction().getDialog();
    ClientTransaction transactionA = callA.getLastTransaction().getClientTransaction();

    assertTrue(callB.waitForIncomingCall(5000));
    Dialog dialogB = callB.getLastTransaction().getServerTransaction().getDialog();
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", -1));
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    awaitReceivedResponses(callA, 1);
    assertResponseReceived(SipResponse.RINGING, callA);

    // Initiate the Cancel
    callB.listenForCancel();
    SipTransaction cancel = callA.sendCancel();
    assertNotNull(cancel);
    assertEquals(dialogA, cancel.getClientTransaction().getDialog());

    // Receive/Respond to the Cancel
    SipTransaction trans1 = callB.waitForCancel(2000);
    callB.stopListeningForRequests();
    assertNotNull(trans1);
    assertEquals(dialogB, trans1.getServerTransaction().getDialog());
    assertEquals(trans1.getRequest(), callB.getLastReceivedRequest().getMessage());
    assertEquals(trans1.getServerTransaction(), callB.getLastReceivedRequest().getRequestEvent()
        .getServerTransaction());
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1));

    assertTrue(callA.waitForCancelResponse(cancel, 5000));
    awaitReceivedResponses(callA, 2);
    SipResponse response = callA.getLastReceivedResponse();
    assertNotNull(response);
    assertNotNull(response.getResponseEvent());
    assertEquals(response.getResponseEvent().getResponse(), response.getMessage());
    assertEquals(cancel.getClientTransaction(), callA.getLastReceivedResponse().getResponseEvent()
        .getClientTransaction());
    assertEquals(dialogA, cancel.getClientTransaction().getDialog());
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);
    Thread.sleep(500);

    // close the INVITE transaction
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
    awaitReceivedResponses(callA, 3);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);
    response = callA.getLastReceivedResponse();
    assertNotNull(response);
    assertNotNull(response.getResponseEvent());
    assertEquals(response.getResponseEvent().getResponse(), response.getMessage());
    assertEquals(transactionA, response.getResponseEvent().getClientTransaction());

    // done, finish up
    callA.listenForDisconnect();

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    callA.respondToDisconnect();
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    ub.dispose();
  }

  /**
   * This test has to be done manually. For this test, you must introduce a delay
   * (Thread.sleep(500);) in org.cafesip.sipunit.SipStack.processResponse() before it loops through
   * the listeners and calls their processResponse() method.
   */
  @Test
  public void ManualTestTransTerminationRaceCondition() throws Exception {
    // test OK reception terminating transaction before TRYING gets
    // processed by SipSession
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);
    ub.listenRequestMessage();

    SipCall callA = ua.createSipCall();
    callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol);
    assertLastOperationSuccess(callA.format(), callA);

    RequestEvent incReq = ub.waitRequest(5000);
    assertNotNull(ub.format(), incReq);

    Response response =
        ub.getParent().getMessageFactory().createResponse(Response.TRYING, incReq.getRequest());
    SipTransaction transb = ub.sendReply(incReq, response);
    assertNotNull(ub.format(), transb);
    // trying response sent

    URI calleeContact = ub.getParent().getAddressFactory().createURI(getSipUserBAddress(ub));
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    ub.sendReply(transb, Response.OK, null, toTag, contact, -1);
    assertLastOperationSuccess(ub.format(), ub);
    // OK sent

    // receive it on the 'a' side
    assertTrue(callA.waitOutgoingCallResponse(1000));
    ResponseEvent event = callA.getLastReceivedResponse().getResponseEvent();
    assertEquals("Unexpected 1st response received", Response.TRYING, event.getResponse()
        .getStatusCode());
    final ClientTransaction ct = event.getClientTransaction();

    await().until(new Callable<Integer>() {

      @Override
      public Integer call() throws Exception {
        return ct.getState().getValue();
      }
    }, is(TransactionState._TERMINATED));

    assertTrue(callA.waitOutgoingCallResponse(2000));
    ResponseEvent event2  = callA.getLastReceivedResponse().getResponseEvent();
    assertEquals("Unexpected 2nd response received", Response.OK, event2.getResponse()
        .getStatusCode());
    ClientTransaction ct2 = event2.getClientTransaction();
    assertEquals(TransactionState._TERMINATED, ct2.getState().getValue());
  }

  @Test
  public void testReceivingACKAfterCancel() throws Exception {
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    // establish a call
    SipCall callee = ub.createSipCall();
    callee.listenForIncomingCall();

    SipCall callA =
        ua.makeCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/' + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    callee.waitForIncomingCall(1000);
    SipRequest req1 = callee.getLastReceivedRequest();
    String lastRequest = (req1 == null) ? "no request received" : req1.getMessage().toString();
    assertRequestReceived("SIP: INVITE not received<br>(last received request is " + lastRequest
        + ")", SipRequest.INVITE, callee);

    callee.sendIncomingCallResponse(SipResponse.SESSION_PROGRESS, "Session Progress", 0, null,
        "application", "sdp", null, null);
    awaitReceivedResponses(callA, 1);
    assertLastOperationSuccess("SIP: 183 Session Progress is not correcly sent", callee);

    callee.listenForCancel();

    SipTransaction callingCancelTrans = callA.sendCancel();
    assertNotNull(callingCancelTrans);

    SipTransaction calleeCancelTrans = callee.waitForCancel(1000);
    SipRequest req2 = callee.getLastReceivedRequest();
    String lastRequest2 = (req2 == null) ? "no request received" : req2.getMessage().toString();
    assertRequestReceived("SIP: CANCEL is not received<br>(last received request is "
        + lastRequest2 + ")", SipRequest.CANCEL, callee);

    callee.respondToCancel(calleeCancelTrans, SipResponse.OK, "OK", 0);
    assertLastOperationSuccess("SIP: could not send 200 OK for the CANCEL", callee);
    awaitReceivedResponses(callA, 1);

    // see if caller got the cancel OK
    assertTrue(callA.waitForCancelResponse(callingCancelTrans, 1000));
    assertEquals(200, callA.getLastReceivedResponse().getStatusCode());
    assertHeaderContains(callA.getLastReceivedResponse(), CSeqHeader.NAME, "CANCEL");

    // let callee respond to original INVITE transaction
    callee.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0, null,
        "application", "sdp", null, null);
    awaitReceivedResponses(callA, 3);
    assertLastOperationSuccess(getProtocol()
        + ": could not send 487 Request Terminated send for the CANCEL", callee);

    // see if caller got the 487
    assertEquals(SipResponse.REQUEST_TERMINATED, callA.getLastReceivedResponse().getStatusCode());

    // stack sends ACK, give it some time

    callee.waitForAck(3000);
    SipRequest req3 = callee.getLastReceivedRequest();
    lastRequest = (req3 == null) ? "no request received" : req3.getMessage().toString();
    assertRequestReceived(getProtocol() + ": ACK not received<br>(last received request is "
        + lastRequest + ")", SipRequest.ACK, callee);
  }

  @Test
  public void testWaitForAuthPositive() throws Exception {
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ua.addUpdateCredential(new Credential("nist.gov", "amit", "a1b2c3d4"));

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();

    assertTrue(callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol));
    assertTrue(callB.waitForIncomingCall(10000));

    WWWAuthenticateHeader auth_header =
        AuthUtil.getAuthenticationHeader(callB.getLastReceivedRequest(), callB.getHeaderFactory(),
            "nist.gov");
    ArrayList<Header> addnl = new ArrayList<>();
    addnl.add(auth_header);

    assertTrue(callB.sendIncomingCallResponse(Response.PROXY_AUTHENTICATION_REQUIRED, null, -1,
        addnl, null, null));

    assertTrue(callA.waitForAuthorisation(5000));
    assertTrue(callB.waitForIncomingCall(5000));
    assertHeaderPresent(callB.getLastReceivedRequest(), ProxyAuthorizationHeader.NAME);

    ub.dispose();
  }

  @Test
  public void testWaitForAuthNegative() throws Exception {
    SipPhone ub = sipStack.createSipPhone(getSipUserB());
    ub.setLoopback(true);

    ua.addUpdateCredential(new Credential("nist.gov", "amit", "a1b2c3d4"));

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();

    assertTrue(callA.initiateOutgoingCall(getSipUserB(), ua.getStackAddress() + ':' + myPort + '/'
        + testProtocol));
    assertTrue(callB.waitForIncomingCall(10000));

    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "RINGING", -1));

    assertFalse(callA.waitForAuthorisation(5000));
    assertResponseReceived(Response.RINGING, callA);

    ub.dispose();
  }
}
