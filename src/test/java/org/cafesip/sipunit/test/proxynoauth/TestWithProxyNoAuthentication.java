/*
 * Created on Mar 29, 2005
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

package org.cafesip.sipunit.test.proxynoauth;

import static org.cafesip.sipunit.SipAssert.assertAnswered;
import static org.cafesip.sipunit.SipAssert.assertBodyContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderPresent;
import static org.cafesip.sipunit.SipAssert.assertLastOperationFail;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNotAnswered;
import static org.cafesip.sipunit.SipAssert.assertResponseNotReceived;
import static org.cafesip.sipunit.SipAssert.assertResponseReceived;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.EventObject;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class tests SipUnit API methods.
 * 
 * <p>
 * Tests in this class require that a Proxy/registrar server be running with authentication turned
 * off. Defaults: proxy host = 192.168.112.1, port = 5060, protocol = udp; user amit@cafesip.org
 * password a1b2c3d4 and user becky@cafesip.org password a1b2c3d4 defined at the proxy.
 * 
 * <p>
 * For the Proxy/registrar, I used cafesip.org's SipExchange server.
 * 
 * @author Becky McElroy
 * 
 */
public class TestWithProxyNoAuthentication {

  private SipStack sipStack;

  private SipPhone ua;

  private String thisHostAddr;

  private static String PROXY_HOST = "192.168.112.1";

  private static int PROXY_PORT = 5060;

  private static String PROXY_PROTO = "udp";

  public TestWithProxyNoAuthentication() {}

  /**
   * Initialize the sipStack and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack = new SipStack(null, 5061);
    thisHostAddr = InetAddress.getLocalHost().getHostAddress();

    ua = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:amit@cafesip.org");
  }

  /**
   * Release the sipStack and a user agent for the test.
   */
  @After
  public void tearDown() throws Exception {
    ua.dispose();
    awaitStackDispose(sipStack);
  }

  /**
   * This test illustrates usage of SipTestCase.
   */
  @Test
  public void testBothSidesCallerDisc() throws Exception {
    // invoke the Sip operation, then separately check positive result;
    // include all error details in output (via ua.format()) if the test
    // fails:

    ua.register(null, 1800);
    assertLastOperationSuccess("Caller registration failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    // invoke the Sip operation, then separately check positive result;
    // no failure/error details, just the standard JUnit fail output:

    ub.register(null, 600); // ub.register("sip:becky@" + thisHostAddr,
    // 600);
    assertLastOperationSuccess(ub);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();
    Thread.sleep(10);

    // another way to invoke the operation and check the result
    // separately:

    boolean statusOk = callA.initiateOutgoingCall("sip:becky@cafesip.org", null);
    assertTrue("Initiate outgoing call failed - " + callA.format(), statusOk);

    // invoke the Sip operation and check positive result in one step,
    // no operation error details if the test fails:

    assertTrue("Wait incoming call error or timeout - " + callB.format(), callB.waitForIncomingCall(5000));

    // invoke the Sip operation and result check in one step,
    // only standard JUnit output if the test fails:

    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));

    Thread.sleep(1000);

    // although the 2-step method is not as compact, it's easier
    // to follow what a test is doing since the Sip operations are not
    // buried as parameters in assert statements:

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("Sending answer response failed - " + callB.format(), callB);

    // note with the single step method, you cannot include operation
    // error details for when the test fails: ' + a.format()' wouldn't
    // work in the first parameter here:

    assertTrue("Wait response error", callA.waitOutgoingCallResponse(10000));

    SipResponse resp = callA.getLastReceivedResponse(); // watch for TRYING
    int statusCode = resp.getStatusCode();
    while (statusCode != Response.RINGING) {
      assertFalse("Unexpected final response, status = " + statusCode, statusCode > 200);

      assertFalse("Got OK but no RINGING", statusCode == Response.OK);

      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
      resp = callA.getLastReceivedResponse();
      statusCode = resp.getStatusCode();
    }

    // if you want operation error details in your test fail output,
    // you have to invoke and complete the operation first:

    callA.waitOutgoingCallResponse(10000); // get next response
    assertLastOperationSuccess("Wait response error - " + callA.format(), callA);

    // throw out any 'TRYING' responses
    // Note, you can also get the response status code from the SipCall
    // class itself (in addition to getting it from the response as
    // above)
    while (callA.getReturnCode() == Response.TRYING) {
      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
    }
    resp = callA.getLastReceivedResponse();

    // check for OK response.
    assertEquals("Unexpected response received", Response.OK, callA.getReturnCode());

    // check out some header asserts
    assertHeaderContains(resp, "From", "sip:amit@cafesip.org");
    assertHeaderNotContains(resp, "From", "sip:ammit@cafesip.org");
    assertHeaderPresent(resp, "CSeq");
    assertHeaderNotPresent(resp, "Content-Type");

    // continue with the test call
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    callB.listenForDisconnect();
    assertLastOperationSuccess("b listen disc - " + callB.format(), callB);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(5000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);

    // TODO investigate - null pointer from stack or my msg is bad
    // b.respondToDisconnect();
    // assertLastOperationSuccess("b disc - " + b.format(), b);

    ub.unregister(null, 10000);
    assertLastOperationSuccess("unregistering user b - " + ub.format(), ub);
  }

  @Test
  public void testBothSidesCalleeDisc() throws Exception {
    ua.register(null, 1800);
    assertLastOperationSuccess("a registration - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    ub.register(null, 600);
    assertLastOperationSuccess("b registration - " + ub.format(), ub);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();
    Thread.sleep(10);

    callA.initiateOutgoingCall("sip:becky@cafesip.org", null);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);

    callB.waitForIncomingCall(10000);
    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);

    Thread.sleep(1000);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("b send OK - " + callB.format(), callB);

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);

    while (callA.getReturnCode() == Response.TRYING) {
      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
    }

    assertEquals("Unexpected 1st response received", Response.RINGING, callA.getReturnCode());

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 2nd response - " + callA.format(), callA);

    while (callA.getReturnCode() == Response.TRYING) {
      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
    }

    assertEquals("Unexpected 2nd response received", Response.OK, callA.getReturnCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    callA.listenForDisconnect();
    assertLastOperationSuccess("a listen disc - " + callA.format(), callA);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    // TODO - investigate - null pointer from stack or bad msg?
    // a.respondToDisconnect();
    // assertLastOperationSuccess("a respond to disc - " + a.format(),
    // a);

    ub.dispose();
  }

  @Test
  public void testBasicRegistration() throws Exception {
    // no authentication
    ua.register("sip:amit@209.42.0.1", 695);
    assertLastOperationSuccess("user a registration - " + ua.format(), ua);

    assertEquals("check contact expiry", 695, ua.getContactInfo().getExpiry());
    assertEquals("check contact URI", "sip:amit@209.42.0.1", ua.getContactInfo().getURI());

    // wait 2 sec then unregister
    Thread.sleep(2000);

    ua.unregister("sip:amit@209.42.0.1", 10000);
    assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
  }

  @Test
  public void testSingleReply() throws Exception {
    // test: sendRequestWithTrans(String invite, viaProxy),
    // sendReply(Response OK, toTag, contact),
    // waitResponse()

    ua.register("amit", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    ub.register("becky", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ub.format(), ub);

    ub.listenRequestMessage();
    Thread.sleep(100);

    StringBuffer invite =
        new StringBuffer("INVITE sip:becky@" + PROXY_HOST + ':' + PROXY_PORT + ";transport="
            + PROXY_PROTO + " SIP/2.0\n");
    invite.append("Call-ID: " + System.currentTimeMillis() + "@" + thisHostAddr + "\n");
    invite.append("CSeq: 1 INVITE\n");
    invite.append("From: <sip:amit@cafesip.org>;tag=1181356482\n");
    invite.append("To: <sip:becky@cafesip.org>\n");
    invite.append("Contact: <sip:amit@" + thisHostAddr + ":5061>\n");
    invite.append("Max-Forwards: 5\n");
    invite.append("Via: SIP/2.0/" + PROXY_PROTO + " " + thisHostAddr
        + ":5061;branch=322e3136382e312e3130303a3530363\n");
    invite.append("Event: presence\n");
    invite.append("Content-Length: 5\n");
    invite.append("\n");
    invite.append("12345");

    SipTransaction trans = ua.sendRequestWithTransaction(invite.toString(), true, null);
    assertNotNull(ua.format(), trans);
    // call sent

    RequestEvent incReq = ub.waitRequest(10000);
    assertNotNull(ub.format(), incReq);
    // call received

    assertHeaderContains(new SipRequest(incReq.getRequest()), EventHeader.NAME, "presence");
    assertBodyContains(new SipRequest(incReq.getRequest()), "12345");

    URI calleeContact =
        ub.getParent().getAddressFactory().createURI("sip:becky@" + thisHostAddr + ":5061");
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    SipTransaction transb = ub.sendReply(incReq, Response.OK, null, toTag, contact, -1);
    assertNotNull(ub.format(), transb);
    // answer response sent

    Thread.sleep(1000);

    EventObject responseEvent;
    int status;
    do {
      responseEvent = ua.waitResponse(trans, 10000);
      assertNotNull(ua.format(), responseEvent);
      assertFalse(ua.format(), responseEvent instanceof TimeoutEvent);
      // got a response

      status = ((ResponseEvent) responseEvent).getResponse().getStatusCode();
    } while (status == Response.TRYING);

    assertEquals(ua.format(), Response.OK, status);

    ub.dispose();
  }

  @Test
  public void testMultipleReplies() throws Exception {
    // test: sendRequestWithTrans(Request invite),
    // sendReply(Response Trying, no toTag, no contact),
    // sendReply(statusCode Ringing, toTag, contact, ...),
    // sendReply(Response OK, no toTag, contact, ...),
    // waitResponse()

    ua.register("amit", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    ub.register("becky", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ub.format(), ub);

    ub.listenRequestMessage();
    Thread.sleep(100);

    AddressFactory addrFactory = ua.getParent().getAddressFactory();
    HeaderFactory hdrFactory = ua.getParent().getHeaderFactory();

    Request invite =
        ua.getParent()
            .getMessageFactory()
            .createRequest(
                "INVITE sip:becky@" + PROXY_HOST + ':' + PROXY_PORT + ";transport=" + PROXY_PROTO
                    + " SIP/2.0\r\n\r\n");

    invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
    invite.addHeader(hdrFactory.createCSeqHeader((long) 1, Request.INVITE));
    invite.addHeader(hdrFactory.createFromHeader(ua.getAddress(), ua.generateNewTag()));

    Address toAddress =
        addrFactory.createAddress(addrFactory.createURI("sip:becky@cafesip.org"));
    invite.addHeader(hdrFactory.createToHeader(toAddress, null));

    Address contactAddress = addrFactory.createAddress("sip:amit@" + thisHostAddr + ":5061");
    invite.addHeader(hdrFactory.createContactHeader(contactAddress));

    invite.addHeader(hdrFactory.createMaxForwardsHeader(5));
    ArrayList<ViaHeader> viaHeaders = ua.getViaHeaders();
    invite.addHeader(viaHeaders.get(0));

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

    Thread.sleep(500);

    URI calleeContact =
        ub.getParent().getAddressFactory().createURI("sip:becky@" + thisHostAddr + ":5061");
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1);
    assertLastOperationSuccess(ub.format(), ub);
    // ringing response sent

    Thread.sleep(500);

    response =
        ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response.addHeader(ub.getParent().getHeaderFactory().createContactHeader(contact));

    ub.sendReply(transb, response);
    assertLastOperationSuccess(ub.format(), ub);
    // answer response sent

    Thread.sleep(500);

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

    assertEquals("Should have received RINGING", Response.RINGING,
        ((ResponseEvent) responseEvent).getResponse().getStatusCode());
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

  @Test
  public void testStatelessRequestStatefulResponse() throws Exception {
    // test: sendUnidirectionalReq(String invite, not viaProxy),
    // sendReply(statusCode Ringing, no toTag, no contact),
    // sendSubsequentReply(statusCode OK, toTag, contact),
    // sendUnidirectionalReq(Request ack),
    // ack received

    // SipStack.setTraceEnabled(true);
    // SipStack.trace("testStatelessRequestStatefulResponse");
    ua.register("amit", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    ub.register("becky", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ub.format(), ub);

    ub.listenRequestMessage();
    Thread.sleep(100);

    StringBuffer invite =
        new StringBuffer("INVITE sip:becky@" + PROXY_HOST + ':' + PROXY_PORT + ";transport="
            + PROXY_PROTO + " SIP/2.0\r\n");
    String myuniquecallId = String.valueOf(System.currentTimeMillis());
    invite.append("Call-ID: " + myuniquecallId + "@" + thisHostAddr + "\r\n");
    invite.append("CSeq: 1 INVITE\r\n");
    invite.append("From: <sip:amit@cafesip.org>;tag=1181356482\r\n");
    invite.append("To: <sip:becky@cafesip.org>\r\n");
    invite.append("Contact: <sip:amit@" + thisHostAddr + ":5061>\r\n");
    invite.append("Max-Forwards: 5\r\n");
    invite.append("Via: SIP/2.0/" + PROXY_PROTO + " " + thisHostAddr
        + ":5061;branch=322e3136382e312e3130303a3530363\r\n");
    invite.append("Content-Length: 0\r\n");
    invite.append("\r\n");

    ua.sendUnidirectionalRequest(invite.toString(), false);
    assertLastOperationSuccess(ua.format(), ua);
    // call sent

    RequestEvent incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    // call received

    assertHeaderContains(new SipRequest(incReq.getRequest()), CallIdHeader.NAME, myuniquecallId);

    SipTransaction trans = ub.sendReply(incReq, Response.RINGING, null, null, null, -1);
    assertNotNull(ub.format(), trans);
    // first reply sent

    Thread.sleep(1000);

    URI calleeContact =
        ub.getParent().getAddressFactory().createURI("sip:becky@" + thisHostAddr + ":5061");
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();
    ub.sendReply(trans, Response.OK, null, toTag, contact, 0);
    assertLastOperationSuccess(ub.format(), ub);
    // OK response sent

    Thread.sleep(1000);
    ub.listenRequestMessage();

    // build the ack to send; pass a Request object to
    // sendUnidirectionalRequest(()
    // this time; use headers from the invite request to create the ack
    // request
    Request ack = ua.getParent().getMessageFactory().createRequest(invite.toString());

    ack.setMethod(Request.ACK);
    ack.setHeader(ua.getParent().getHeaderFactory().createCSeqHeader((long) 1, Request.ACK));
    ((ToHeader) ack.getHeader(ToHeader.NAME)).setTag(toTag);
    ack.setRequestURI(calleeContact);
    ack.setHeader(ua.getParent().getHeaderFactory().createRouteHeader(contact));

    ua.sendUnidirectionalRequest(ack, false);
    assertLastOperationSuccess(ua.format(), ua);
    // ack sent

    incReq = ub.waitRequest(30000);
    assertNotNull(ub.format(), incReq);
    assertEquals("Received request other than ACK", Request.ACK, incReq.getRequest().getMethod());
    // ack received

    ub.dispose();
  }

  @Test
  public void testSendInviteNoProxy() throws Exception {
    ua.dispose(); // re-create ua with no proxy
    ua = sipStack.createSipPhone("sip:amit@cafesip.org");

    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");

    ub.listenRequestMessage();
    Thread.sleep(100);

    AddressFactory addrFactory = ua.getParent().getAddressFactory();
    HeaderFactory hdrFactory = ua.getParent().getHeaderFactory();

    Request invite =
        ua.getParent().getMessageFactory()
            .createRequest("INVITE sip:becky@cafesip.org SIP/2.0\r\n\r\n");

    invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
    invite.addHeader(hdrFactory.createCSeqHeader((long) 1, Request.INVITE));
    invite.addHeader(hdrFactory.createFromHeader(ua.getAddress(), ua.generateNewTag()));

    Address toAddress =
        addrFactory.createAddress(addrFactory.createURI("sip:becky@cafesip.org"));
    invite.addHeader(hdrFactory.createToHeader(toAddress, null));

    Address contactAddress = addrFactory.createAddress("sip:amit@" + thisHostAddr + ":5061");
    invite.addHeader(hdrFactory.createContactHeader(contactAddress));

    invite.addHeader(hdrFactory.createMaxForwardsHeader(5));
    ArrayList<ViaHeader> viaHeaders = ua.getViaHeaders();
    invite.addHeader(viaHeaders.get(0));

    Address routeAddress = addrFactory.createAddress("sip:becky@" + thisHostAddr + ":5061");
    invite.addHeader(hdrFactory.createRouteHeader(routeAddress));

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

    Thread.sleep(500);

    URI calleeContact =
        ub.getParent().getAddressFactory().createURI("sip:becky@" + thisHostAddr + ":5061");
    Address contact = ub.getParent().getAddressFactory().createAddress(calleeContact);

    String toTag = ub.generateNewTag();

    ub.sendReply(transb, Response.RINGING, null, toTag, contact, -1);
    assertLastOperationSuccess(ub.format(), ub);
    // ringing response sent

    Thread.sleep(500);

    response =
        ub.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    response.addHeader(ub.getParent().getHeaderFactory().createContactHeader(contact));

    ub.sendReply(transb, response);
    assertLastOperationSuccess(ub.format(), ub);
    // answer response sent

    Thread.sleep(500);

    EventObject responseEvent = ua.waitResponse(trans, 10000);
    // wait for trying

    assertNotNull(ua.format(), responseEvent);
    assertFalse("Operation timed out", responseEvent instanceof TimeoutEvent);

    assertEquals("Should have received TRYING", Response.TRYING, ((ResponseEvent) responseEvent)
        .getResponse().getStatusCode());
    // response(s) received, we're done

    ub.dispose();
  }

  @Test
  public void testMakeCall() throws Exception {
    ua.register("amit", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ua.format(), ua);

    // use a called party not registered, verify expected response
    ua.makeCall("sip:doodah@cafesip.org", SipResponse.TEMPORARILY_UNAVAILABLE, 10000, null);
    assertLastOperationSuccess(ua.format(), ua);

    // do it again, look for what we know won't happen
    ua.makeCall("sip:doodah@cafesip.org", SipResponse.OK, 10000, null);
    assertLastOperationFail("Unexpected success, call completed", ua);
  }

  /**
   * Test: asynchronous SipPhone.makeCall(), callee disc
   */
  @Test
  public void testBothSidesAsynchMakeCall() throws Exception {
    ua.register("amit", "a1b2c3d4", null, 0, 10000);
    assertLastOperationSuccess(ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");

    assertTrue(ub.register("becky", "a1b2c3d4", null, 600, 5000));
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());
    Thread.sleep(50);

    SipCall callA = ua.makeCall("sip:becky@cafesip.org", null);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    Thread.sleep(500);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
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
    Thread.sleep(1000);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);
    callA.respondToDisconnect();

    ub.dispose();
  }

  // testStatelessRequestStatelessResponse() TODO
  // testStatefulRequestStatelessResponse() TODO

  /*
   * public void xtestCalltoSipPhone() throws Exception { // First register a Sip phone
   * (becky@cafesip.org) to the proxy before // executing this test. This test doesn't work as is -
   * the Sip phone // doesn't do anything with the INVITE
   * 
   * ua.register("amit", "a1b2c3d4", null, 0); assertLastOperationSuccess(ua.format(), ua);
   * 
   * SipCall call = ua.createSipCall();
   * 
   * call.initiateOutgoingCall("sip:becky@cafesip.org", true);
   * assertLastOperationSuccess(call.format(), call);
   * 
   * call.waitOutgoingCallResponse(10000); assertLastOperationSuccess(call.format(), call);
   * 
   * int status_code = call.getReturnCode(); while (status_code != Response.OK) { if (status_code /
   * 100 == 1) { // provisional
   * 
   * call.waitOutgoingCallResponse(10000); assertLastOperationSuccess(call.format(), call);
   * 
   * status_code = call.getReturnCode(); continue; } else if ((status_code == Response.UNAUTHORIZED)
   * || (status_code == Response.PROXY_AUTHENTICATION_REQUIRED)) { // auth required // use a common
   * method on parent to handle // this for outgoing calls, registration, etc. // see registration()
   * for handling of this case
   * 
   * fail("Need auth handling"); } else { fail("Unknown/unexpected response code - " +
   * call.format()); } }
   * 
   * return; }
   */
}
