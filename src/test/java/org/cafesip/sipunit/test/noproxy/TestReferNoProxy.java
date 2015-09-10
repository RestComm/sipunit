/*
 * Created on May 17, 2009
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
import static org.cafesip.sipunit.SipAssert.assertBodyContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNoSubscriptionErrors;
import static org.cafesip.sipunit.SipAssert.awaitAnswered;
import static org.cafesip.sipunit.SipAssert.awaitDialogReady;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.cafesip.sipunit.ReferNotifySender;
import org.cafesip.sipunit.ReferSubscriber;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipMessage;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.header.AcceptHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.Header;
import javax.sip.header.OrganizationHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class tests SipUnit refer functionality. Currently only the outbound REFER side is
 * supported.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server.
 * 
 * @author Becky McElroy
 */
public class TestReferNoProxy {

  private SipStack sipStack;

  private SipPhone ua;

  private int myPort;

  private String testProtocol;

  private static final Properties defaultProperties = new Properties();

  static {
    defaultProperties.setProperty("javax.sip.STACK_NAME", "testRefer");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testRefer_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testRefer_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5061");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestReferNoProxy() {
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 5061;
    }

    testProtocol = properties.getProperty("sipunit.test.protocol");
  }

  /**
   * Initialize the sipStack and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack = new SipStack(testProtocol, myPort, properties);

    ua = sipStack.createSipPhone("sip:amit@nist.gov");
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

  @Test(expected = ParseException.class)
  public void testBadInput() throws Exception {
    ua.getUri(null, "sipmaster@192.168.1.11:5060", null, null, null, null, null, null, null);
  }

  @Test(expected = ParseException.class)
  public void testBadInput1() throws Exception {
    ua.getUri("doodah", "sipmaster@192.168.1.11:5060", null, null, null, null, null, null, null);
  }

  @Test(expected = ParseException.class)
  public void testBadInput2() throws Exception {
    ua.getUri("sip:", "sipmaster", null, null, null, null, null, null, null);
  }

  @Test(expected = ParseException.class)
  public void testBadInput3() throws Exception {
    ua.getUri("sip:", "sip:sipmaster@192.168.1.11:5060", null, null, null, null, null, null, null);
  }

  @Test
  public void testGetURI() throws Exception {
    // test scheme and userHostPort
    SipURI uri =
        ua.getUri("sip:", "sipmaster@192.168.1.11:5060", null, null, null, null, null, null, null);
    assertEquals("sip", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(5060, uri.getPort());
    assertTrue("sip:sipmaster@192.168.1.11:5060".equalsIgnoreCase(uri.toString()));

    uri = ua.getUri("sips:", "sipmaster@192.168.1.11", null, null, null, null, null, null, null);
    assertEquals("sips", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(-1, uri.getPort());
    assertTrue("sips:sipmaster@192.168.1.11".equalsIgnoreCase(uri.toString()));

    uri =
        ua.getUri(null, "sip:sipmaster@192.168.1.11:5060", null, null, null, null, null, null, null);
    assertEquals("sip", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(5060, uri.getPort());
    assertTrue("sip:sipmaster@192.168.1.11:5060".equalsIgnoreCase(uri.toString()));

    // test transportUriParameter, methodUriParameter, otherUriParameters
    Map<String, String> paramMap = new HashMap<>();
    paramMap.put("maddr", "abc");
    uri =
        ua.getUri("sip:", "sipmaster@192.168.1.11:5060", "udp", "SUBSCRIBE", paramMap, null, null,
            null, null);
    assertEquals("sip", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(5060, uri.getPort());
    assertEquals("udp", uri.getTransportParam());
    assertEquals("SUBSCRIBE", uri.getMethodParam());
    assertEquals("abc", uri.getMAddrParam());
    assertTrue(uri.toString().startsWith("sip:sipmaster@192.168.1.11"));
    assertTrue(uri.toString().indexOf("transport=udp") > 0);
    assertTrue(uri.toString().indexOf("maddr=abc") > 0);
    assertTrue(uri.toString().indexOf("method=SUBSCRIBE") > 0);
    // test joinUriHeader, Map<String, String> otherUriHeaders
    Map<String, String> headerMap = new HashMap<>();
    headerMap.put("Contact", "sip:abc@192.168.1.12");
    paramMap.clear();
    paramMap.put("maddr", "abc");
    uri =
        ua.getUri("sip:", "sipmaster@192.168.1.11", "tls", null, paramMap,
            "otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef", null, null, headerMap);
    assertEquals("sip", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(-1, uri.getPort());
    assertEquals("tls", uri.getTransportParam());
    assertEquals(null, uri.getMethodParam());
    assertEquals("abc", uri.getMAddrParam());
    assertEquals("otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef", uri.getHeader("Join"));
    assertEquals("sip:abc@192.168.1.12", uri.getHeader("Contact"));
    assertTrue(uri.toString().startsWith("sip:sipmaster@192.168.1.11"));
    assertTrue(uri.toString().indexOf("transport=tls") > 0);
    assertTrue(uri.toString().indexOf("maddr=abc") > 0);
    assertTrue(uri.toString().indexOf("Join=otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef") > 0);
    assertTrue(uri.toString().indexOf("Contact=sip:abc@192.168.1.12") > 0);

    // Test String replacesUriHeader, String bodyUriHeader
    uri =
        ua.getUri("sip:", "sipmaster@192.168.1.11", null, null, null, null,
            "otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz", "This is my body", null);
    assertEquals("sip", uri.getScheme());
    assertEquals("sipmaster", uri.getUser());
    assertEquals("192.168.1.11", uri.getHost());
    assertEquals(-1, uri.getPort());
    assertEquals(null, uri.getTransportParam());
    assertEquals(null, uri.getMethodParam());
    assertEquals(null, uri.getHeader("Join"));
    assertEquals("otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz", uri.getHeader("Replaces"));
    assertEquals("This is my body", uri.getHeader("Body"));
    assertTrue(uri.toString().startsWith("sip:sipmaster@192.168.1.11"));
    assertTrue(uri.toString().indexOf("Replaces=otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz") > 0);
    assertTrue(uri.toString().indexOf("body=This is my body") > 0);
  }

  @Test
  public void testOutboundOutdialogBasicWithUnsubscribe() throws Exception {
    // A sends out-of-dialog REFER to B, gets OK response
    // B sends active-state NOTIFY to A, gets OK response
    // A unsubscribes, gets OK response
    // B sends terminated NOTIFY, gets OK response
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    // create & prepare the referee
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.OK, "OK");

    assertEquals(0, ua.getRefererList().size());

    // A: send REFER
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);

    // check the results - subscription, response, SipPhone referer list
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionTerminated());
    Response resp = subscription.getCurrentResponse().getResponse();
    assertEquals(SipRequest.REFER, ((CSeqHeader) resp.getHeader(CSeqHeader.NAME)).getMethod());
    assertEquals("OK", resp.getReasonPhrase());
    assertNull(resp.getExpires());
    assertEquals(resp.toString(), subscription.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = subscription.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(resp.toString(), receivedResponses.get(0).toString());
    assertEquals(1, ua.getRefererList().size());
    assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(subscription.getDialogId()).get(0));
    assertEquals(subscription, ua.getRefererList().get(0));
    assertNoSubscriptionErrors(subscription);

    // process the received response
    assertTrue(subscription.processResponse(1000));

    // check the response processing results
    assertTrue(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertNull(subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());
    assertNoSubscriptionErrors(subscription);

    // B sends active-state NOTIFY to A, gets OK response
    // B: send NOTIFY
    String notifyBody = "SIP/2.0 200 OK\n";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));

    // A: get the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(500);
    assertNotNull(reqevent);

    // examine the NOTIFY request object, verify subscription getters
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(2400,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    ArrayList<SipRequest> receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest req = subscription.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());
    assertEquals(((SipRequest) receivedRequests.get(0)).getMessage().toString(),
        request.toString());
    assertEquals(receivedRequests.get(0).toString(), req.toString());
    assertBodyContains(req, notifyBody);
    assertNoSubscriptionErrors(subscription);

    // process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);

    // check the processing results
    assertTrue(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertNull(subscription.getTerminationReason());
    assertTrue(subscription.getTimeLeft() <= 2400);
    assertTrue(subscription.getTimeLeft() > 1000);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertNoSubscriptionErrors(subscription);

    // check the response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));

    // A: reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // terminate the subscription from the referrer side
    // A unsubscribes, gets OK response

    // prepare the far end
    ub.processSubscribe(5000, SipResponse.OK, "OK Done");

    // send the un-SUBSCRIBE
    assertTrue(subscription.unsubscribe(100));
    assertFalse(subscription.isRemovalComplete());

    // check the results - subscription, response, SipPhone referer list
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertFalse(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionActive());
    assertTrue(subscription.isSubscriptionTerminated());
    resp = subscription.getCurrentResponse().getResponse();
    assertEquals(SipRequest.SUBSCRIBE, ((CSeqHeader) resp.getHeader(CSeqHeader.NAME)).getMethod());
    assertEquals("OK Done", resp.getReasonPhrase());
    assertEquals(0, resp.getExpires().getExpires());
    assertEquals(resp.toString(), subscription.getLastReceivedResponse().getMessage().toString());
    receivedResponses = subscription.getAllReceivedResponses();
    assertEquals(2, receivedResponses.size());
    assertEquals(resp.toString(), receivedResponses.get(1).toString());
    assertEquals(1, ua.getRefererList().size());
    assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(subscription.getDialogId()).get(0));
    assertEquals(subscription, ua.getRefererList().get(0));
    assertNoSubscriptionErrors(subscription);

    // process the received response
    assertTrue(subscription.processResponse(1000));

    // check the response processing results
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertTrue(subscription.isSubscriptionTerminated());
    assertEquals("Unsubscribe", subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());
    assertNoSubscriptionErrors(subscription);

    // B sends terminated NOTIFY, gets OK response
    // tell far end to send a NOTIFY
    notifyBody = "SIP/2.0 100 Trying\n";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "Unsubscribed", notifyBody, 0,
        false));

    // A: get the NOTIFY
    reqevent = subscription.waitNotify(500);
    assertNotNull(reqevent);

    // examine the NOTIFY request object, verify subscription getters
    request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(-1,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(2, receivedRequests.size());
    req = subscription.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());
    assertEquals(((SipRequest) receivedRequests.get(1)).getMessage().toString(),
        request.toString());
    assertEquals(receivedRequests.get(1).toString(), req.toString());
    assertBodyContains(req, notifyBody);
    assertNoSubscriptionErrors(subscription);

    // process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);

    // check the processing results
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertTrue(subscription.isSubscriptionTerminated());
    assertEquals("Unsubscribed", subscription.getTerminationReason());
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertNoSubscriptionErrors(subscription);

    // check the response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));

    // reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));
    subscription.dispose();
    assertEquals(0, ua.getRefererList().size());
  }

  @Test
  public void testOutboundIndialogBasic() throws Exception {
    // A calls B, call established
    // A sends in-dialog REFER to B, gets 202 Accepted
    // B sends subscription-terminating NOTIFY to A, gets OK in response

    // create and set up the far end
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());

    // make the call from A
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    // B side answer the call
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));

    // A side finish call establishment
    awaitAnswered("Outgoing call leg not answered", callA);
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    // B side - prepare to receive REFER
    ReferNotifySender referHandler = new ReferNotifySender(ub);
    referHandler.setDialog(callB.getDialog());
    referHandler.processRefer(4000, SipResponse.ACCEPTED, "Accepted");

    // A side - send a REFER message
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ReferSubscriber subscription = ua.refer(callA.getDialog(), referTo, null, 4000);
    if (subscription == null) {
      fail(ua.getReturnCode() + ':' + ua.getErrorMessage());
    }

    // B side - verify received REFER contents
    RequestEvent requestEvent = referHandler.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    Request req = requestEvent.getRequest();
    assertEquals(SipRequest.REFER, req.getMethod());
    assertEquals(callB.getDialogId(), requestEvent.getDialog().getDialogId());

    // A side - check the initial results - subscription, response,
    // SipPhone referer list
    assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionTerminated());
    Response resp = subscription.getCurrentResponse().getResponse();
    assertEquals(SipRequest.REFER, ((CSeqHeader) resp.getHeader(CSeqHeader.NAME)).getMethod());
    assertEquals("Accepted", resp.getReasonPhrase());
    assertNull(resp.getExpires());
    assertEquals(resp.toString(), subscription.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = subscription.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(resp.toString(), receivedResponses.get(0).toString());
    assertEquals(1, ua.getRefererList().size());
    assertEquals(subscription, ua.getRefererList().get(0));
    assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(subscription.getDialogId()).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(callA.getDialogId()).get(0));

    // A side - process the received response
    assertTrue(subscription.processResponse(1000));
    assertNoSubscriptionErrors(subscription);

    // A side - check the response processing results
    assertFalse(subscription.isSubscriptionActive());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertNull(subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());

    // B side - send a NOTIFY
    Request notifyRequest = callB.getDialog().createRequest(SipRequest.NOTIFY);
    notifyRequest =
        referHandler.addNotifyHeaders(notifyRequest, null, null,
            SubscriptionStateHeader.TERMINATED, "noresource", "SIP/2.0 100 Trying\n", 0);
    SipTransaction trans = referHandler.sendStatefulNotify(notifyRequest, false);
    assertNotNull(trans);

    // A side - wait for the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    assertNotNull(reqevent);

    // A side - examine the NOTIFY request object, verify subscription
    // message getters
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertTrue(((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
        .getExpires() < 1);
    ArrayList<SipRequest> receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest sipreq = subscription.getLastReceivedRequest();
    assertNotNull(sipreq);
    assertTrue(sipreq.isNotify());
    assertFalse(sipreq.isSubscribe());
    assertEquals(receivedRequests.get(0).getMessage().toString(), request.toString());
    assertEquals(receivedRequests.get(0).toString(), sipreq.toString());
    assertBodyContains(sipreq, "SIP/2.0 100 Trying");

    // A side - process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);
    assertNoSubscriptionErrors(subscription);

    // A side - check the NOTIFY processing results on subscription
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertTrue(subscription.isSubscriptionTerminated());
    assertEquals("noresource", subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());

    // A side - check the NOTIFY response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // A side - reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // B side - verify the NOTIFY response got sent by A
    Object obj = referHandler.waitResponse(trans, 10000);
    assertNotNull(obj);
    assertTrue(obj instanceof ResponseEvent);
    assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse().getStatusCode());

    // cleanup
    callA.disposeNoBye();
    callB.disposeNoBye();
  }

  @Test
  public void testOutboundIndialogBrefersAwithRefresh() throws Exception {
    // A calls B, call established
    // B sends in-dialog REFER to A, gets 202 Accepted
    // A sends state-active NOTIFY to B, gets OK in response
    // A sends another NOTIFY to B, gets OK in response
    // B refreshes the subscription
    // A sends subscription-terminating NOTIFY to B, gets OK in response

    // create and set up the far end
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());

    // make the call from A
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    // B side answer the call
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));

    // A side finish call establishment
    awaitAnswered("Outgoing call leg not answered", callA);
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    // B sends in-dialog REFER to A, gets 202 Accepted
    // A side - prepare to receive REFER
    ReferNotifySender referHandler = new ReferNotifySender(ua);
    referHandler.setDialog(callA.getDialog());
    referHandler.processRefer(4000, SipResponse.ACCEPTED, "Accepted");

    // B side - send a REFER message
    SipURI referTo =
        ub.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferSubscriber subscription = ub.refer(callB.getDialog(), referTo, "myeventid", 4000);
    if (subscription == null) {
      fail(ub.getReturnCode() + ':' + ub.getErrorMessage());
    }

    // A side - verify received REFER contents
    RequestEvent requestEvent = referHandler.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    Request req = requestEvent.getRequest();
    assertEquals(SipRequest.REFER, req.getMethod());
    assertEquals(callA.getDialogId(), requestEvent.getDialog().getDialogId());
    assertEquals("myeventid", ((EventHeader) req.getHeader(EventHeader.NAME)).getEventId());

    // B side - check the initial results - subscription, response,
    // SipPhone referer list
    assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionTerminated());
    Response resp = subscription.getCurrentResponse().getResponse();
    assertEquals(SipRequest.REFER, ((CSeqHeader) resp.getHeader(CSeqHeader.NAME)).getMethod());
    assertEquals("Accepted", resp.getReasonPhrase());
    assertNull(resp.getExpires());
    assertEquals(resp.toString(), subscription.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> received_responses = subscription.getAllReceivedResponses();
    assertEquals(1, received_responses.size());
    assertEquals(resp.toString(), received_responses.get(0).toString());
    assertEquals(1, ub.getRefererList().size());
    assertEquals(subscription, ub.getRefererList().get(0));
    assertEquals(subscription, ub.getRefererInfo(referTo).get(0));
    assertEquals(subscription, ub.getRefererInfoByDialog(subscription.getDialogId()).get(0));
    assertEquals(subscription, ub.getRefererInfoByDialog(callB.getDialogId()).get(0));

    // B side - process the received response
    assertTrue(subscription.processResponse(1000));
    assertNoSubscriptionErrors(subscription);

    // B side - check the response processing results
    assertFalse(subscription.isSubscriptionActive());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertNull(subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());

    // A sends state-active NOTIFY to B, gets OK in response
    // A side - send a NOTIFY
    Request notifyRequest = callA.getDialog().createRequest(SipRequest.NOTIFY);
    notifyRequest =
        referHandler.addNotifyHeaders(notifyRequest, null, null, SubscriptionStateHeader.ACTIVE,
            null, "SIP/2.0 100 Trying\n", 60);
    SipTransaction trans = referHandler.sendStatefulNotify(notifyRequest, false);
    assertNotNull(trans);

    // B side - wait for the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    assertNotNull(reqevent);

    // B side - examine the NOTIFY request object, verify subscription
    // message getters
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(60,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    ArrayList<SipRequest> receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest sipreq = subscription.getLastReceivedRequest();
    assertNotNull(sipreq);
    assertTrue(sipreq.isNotify());
    assertFalse(sipreq.isSubscribe());
    assertEquals(receivedRequests.get(0).getMessage().toString(), request.toString());
    assertEquals(receivedRequests.get(0).toString(), sipreq.toString());
    assertBodyContains(sipreq, "SIP/2.0 100 Trying");
    assertHeaderContains(sipreq, EventHeader.NAME, "myeventid");

    // B side - process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);
    assertNoSubscriptionErrors(subscription);

    // B side - check the NOTIFY processing results on subscription
    assertTrue(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertTrue(subscription.getTimeLeft() <= 60 && subscription.getTimeLeft() > 55);

    // B side - check the NOTIFY response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // B side - reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // A side - verify the NOTIFY response got sent by B
    Object obj = referHandler.waitResponse(trans, 10000);
    assertNotNull(obj);
    assertTrue(obj instanceof ResponseEvent);
    assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse().getStatusCode());

    // A sends another NOTIFY to B, gets OK in response
    notifyRequest = callA.getDialog().createRequest(SipRequest.NOTIFY);
    notifyRequest =
        referHandler.addNotifyHeaders(notifyRequest, null, null, SubscriptionStateHeader.ACTIVE,
            null, "SIP/2.0 180 Ringing\n", 20);
    trans = referHandler.sendStatefulNotify(notifyRequest, false);
    assertNotNull(trans);

    // B side - wait for the NOTIFY
    reqevent = subscription.waitNotify(1000);
    assertNotNull(reqevent);

    // B side - examine the NOTIFY request object
    request = reqevent.getRequest();
    assertEquals(20,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(2, receivedRequests.size());
    sipreq = subscription.getLastReceivedRequest();
    assertNotNull(sipreq);
    assertEquals(receivedRequests.get(1).getMessage().toString(), request.toString());
    assertBodyContains(sipreq, "SIP/2.0 180 Ringing");

    // B side - process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);
    assertNoSubscriptionErrors(subscription);

    // B side - check the NOTIFY processing results on subscription
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getTimeLeft() <= 20 && subscription.getTimeLeft() > 15);

    // B side - check the NOTIFY response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());

    // B side - reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // A side - verify the NOTIFY response got sent by B
    obj = referHandler.waitResponse(trans, 10000);
    assertNotNull(obj);
    assertTrue(obj instanceof ResponseEvent);
    assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse().getStatusCode());

    // B refreshes the subscription
    // prepare A to receive SUBSCRIBE
    referHandler.processSubscribe(2000, SipResponse.OK, "OK");
    // refresh
    assertTrue(subscription.refresh(10, "eventid-x", 500));
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertEquals(subscription.getLastReceivedResponse().getResponseEvent(),
        subscription.getCurrentResponse());
    assertEquals("eventid-x",
        ((EventHeader) subscription.getLastSentRequest().getHeader(EventHeader.NAME)).getEventId());
    assertTrue(subscription.processResponse(200));
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getTimeLeft() <= 10 && subscription.getTimeLeft() > 5);

    // A sends subscription-terminating NOTIFY to B, gets OK in response
    // A side - send a NOTIFY
    notifyRequest = callA.getDialog().createRequest(SipRequest.NOTIFY);
    notifyRequest =
        referHandler.addNotifyHeaders(notifyRequest, null, null,
            SubscriptionStateHeader.TERMINATED, "noresource", "SIP/2.0 100 Trying\n", 0);
    trans = referHandler.sendStatefulNotify(notifyRequest, false);
    assertNotNull(trans);

    // B side - wait for the NOTIFY
    reqevent = subscription.waitNotify(1000);
    assertNotNull(reqevent);

    // B side - examine the NOTIFY request object, verify subscription
    // message getters
    request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertTrue(((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
        .getExpires() < 1);
    receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(3, receivedRequests.size());
    sipreq = subscription.getLastReceivedRequest();
    assertNotNull(sipreq);
    assertTrue(sipreq.isNotify());
    assertFalse(sipreq.isSubscribe());
    assertEquals(receivedRequests.get(2).getMessage().toString(), request.toString());
    assertEquals(receivedRequests.get(2).toString(), sipreq.toString());
    assertBodyContains(sipreq, "SIP/2.0 100 Trying");

    // B side - process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);
    assertNoSubscriptionErrors(subscription);

    // B side - check the NOTIFY processing results on subscription
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertTrue(subscription.isSubscriptionTerminated());
    assertEquals("noresource", subscription.getTerminationReason());

    // B side - check the NOTIFY response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // B side - reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // A side - verify the NOTIFY response got sent by B
    obj = referHandler.waitResponse(trans, 10000);
    assertNotNull(obj);
    assertTrue(obj instanceof ResponseEvent);
    assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse().getStatusCode());

    // cleanup
    callA.disposeNoBye();
    callB.disposeNoBye();
  }

  @Test
  public void testOutboundIndialogNotifyBeforeReferResponse() throws Exception {
    // A calls B, call established
    // A sends in-dialog REFER to B, A gets subscription-terminating NOTIFY
    // A gets 202 Accepted in response to the REFER A sent
    // B receives OK from A in response to the NOTIFY B sent

    // create and set up the far end
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());

    // make the call from A
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    // B side answer the call
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));

    // A side finish call establishment
    awaitAnswered("Outgoing call leg not answered", callA);
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    // B side - set up REFER handler
    ReferNotifySender referHandler = new ReferNotifySender(ub);
    referHandler.setDialog(callB.getDialog());
    referHandler.processReferSendNotifyBeforeResponse(2000, SipResponse.ACCEPTED,
        "Accepted", SubscriptionStateHeader.TERMINATED, "noresource", "SIP/2.0 100 Trying\n", 0);

    // A side - send a REFER message
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ReferSubscriber subscription = ua.refer(callA.getDialog(), referTo, "eventbackward", 1000);
    if (subscription == null) {
      fail(ua.getReturnCode() + ':' + ua.getErrorMessage());
    }

    // B side - verify received REFER contents
    RequestEvent requestEvent = referHandler.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    Request req = requestEvent.getRequest();
    assertEquals(SipRequest.REFER, req.getMethod());
    assertEquals(callB.getDialogId(), requestEvent.getDialog().getDialogId());

    // A side - check the initial results - subscription, response,
    // SipPhone referer list
    assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionTerminated());
    Response resp = subscription.getCurrentResponse().getResponse();
    assertEquals(SipRequest.REFER, ((CSeqHeader) resp.getHeader(CSeqHeader.NAME)).getMethod());
    assertEquals("Accepted", resp.getReasonPhrase());
    assertNull(resp.getExpires());
    assertEquals(resp.toString(), subscription.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = subscription.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(resp.toString(), receivedResponses.get(0).toString());
    assertEquals(1, ua.getRefererList().size());
    assertEquals(subscription, ua.getRefererList().get(0));
    assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(subscription.getDialogId()).get(0));
    assertEquals(subscription, ua.getRefererInfoByDialog(callA.getDialogId()).get(0));

    // A side - process the received response
    assertTrue(subscription.processResponse(2000));
    assertNoSubscriptionErrors(subscription);

    // A side - check the response processing results
    assertFalse(subscription.isSubscriptionActive());
    assertTrue(subscription.isSubscriptionPending());
    assertFalse(subscription.isSubscriptionTerminated());
    assertNull(subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());

    // A side - wait for the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    assertNotNull(reqevent);

    // A side - examine the NOTIFY request object, verify subscription
    // message getters
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertTrue(((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
        .getExpires() < 1);
    ArrayList<SipRequest> receivedRequests = subscription.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest sipreq = subscription.getLastReceivedRequest();
    assertNotNull(sipreq);
    assertTrue(sipreq.isNotify());
    assertFalse(sipreq.isSubscribe());
    assertEquals(receivedRequests.get(0).getMessage().toString(), request.toString());
    assertEquals(receivedRequests.get(0).toString(), sipreq.toString());
    assertBodyContains(sipreq, "SIP/2.0 100 Trying");

    // A side - process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);
    assertNoSubscriptionErrors(subscription);

    // A side - check the NOTIFY processing results on subscription
    assertFalse(subscription.isSubscriptionActive());
    assertFalse(subscription.isSubscriptionPending());
    assertTrue(subscription.isSubscriptionTerminated());
    assertEquals("noresource", subscription.getTerminationReason());
    assertEquals(0, subscription.getTimeLeft());

    // A side - check the NOTIFY response that was created
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // A side - reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // B side - verify the NOTIFY response got sent by A
    SipResponse sipresp = referHandler.getLastReceivedResponse();
    assertNotNull(sipresp);
    CSeqHeader cseq = (CSeqHeader) sipresp.getMessage().getHeader(CSeqHeader.NAME);
    assertEquals(SipRequest.NOTIFY, cseq.getMethod());
    assertEquals(SipResponse.OK, sipresp.getResponseEvent().getResponse().getStatusCode());

    // cleanup
    callA.disposeNoBye();
    callB.disposeNoBye();
  }

  @Test
  public void testErrorReferNoResponse() throws Exception {
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 1000, null);
    assertNull(subscription);
    assertEquals(SipSession.TIMEOUT_OCCURRED, ua.getReturnCode());
  }

  @Test
  public void testErrorReferNoSubsequentResponse() throws Exception {
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.TRYING, "Trying");

    // User A: send REFER
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertEquals(SipResponse.TRYING, subscription.getReturnCode());

    // Process the response & collect remaining responses
    assertFalse(subscription.processResponse(1000));
    assertEquals(SipSession.TIMEOUT_OCCURRED, subscription.getReturnCode());
  }

  @Test
  public void testErrorReferResponseWithExpiry() throws Exception {
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK", 60, null);

    // User A: send REFER out-of-dialog
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 1000, null);
    assertNotNull(subscription);
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // Process the response
    assertFalse(subscription.processResponse(1000));
    assertEquals(SipSession.FAR_END_ERROR, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("expires header was received") != -1);
  }

  @Test
  public void testErrorReferResponseBadEventID() throws Exception {
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    EventHeader badEvent = ua.getParent().getHeaderFactory().createEventHeader("refer");
    badEvent.setEventId("wrong-id");
    ub.processRefer(1000, SipResponse.OK, "OK", -1, badEvent);

    // User A: send REFER out-of-dialog
    ReferSubscriber subscription =
        ua.refer("sip:becky@cafesip.org", referTo, "my-event-id", 1000, null);
    assertNotNull(subscription);
    assertEquals(SipResponse.OK, subscription.getReturnCode());

    // Process the response
    assertFalse(subscription.processResponse(1000));
    assertEquals(SipSession.FAR_END_ERROR, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("incorrect event id") != -1);
  }

  @Test
  public void testErrorSubscribeResponseBadExpiry() throws Exception {
    // prepare referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // receive NOTIFY, send OK
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    RequestEvent notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    Response response = subscription.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.replyToNotify(notifyEvent, response));

    // send refresh SUBSCRIBE, get OK, intercept the response and change its
    // expiry > sent expiry
    ub.processSubscribe(1000, SipResponse.OK, "OK", 20, null);
    assertTrue(subscription.refresh(20, 1000));
    ((ExpiresHeader) subscription.getCurrentResponse().getResponse().getHeader(ExpiresHeader.NAME))
        .setExpires(25);
    assertFalse(subscription.processResponse(200));
    assertEquals(SipSession.FAR_END_ERROR, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("expiry") != -1);
  }

  @Test
  public void testErrorSubscribeResponseNoExpiry() throws Exception {
    // prepare referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // receive NOTIFY, send OK
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    RequestEvent notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    Response response = subscription.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.replyToNotify(notifyEvent, response));

    // send refresh SUBSCRIBE, get OK, intercept the response and remove its
    // expires header
    ub.processSubscribe(1000, SipResponse.OK, "OK", 20, null);
    assertTrue(subscription.refresh(20, 1000));
    subscription.getCurrentResponse().getResponse().removeHeader(ExpiresHeader.NAME);
    assertFalse(subscription.processResponse(200));
    assertEquals(SipSession.FAR_END_ERROR, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("no expires header") != -1);
  }

  @Test
  public void testErrorNotifyBadExpiry() throws Exception {
    // prepare referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // receive NOTIFY, send OK
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    RequestEvent notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    Response response = subscription.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.replyToNotify(notifyEvent, response));
    assertTrue(subscription.getTimeLeft() > 28);

    // send refresh SUBSCRIBE, get OK
    ub.processSubscribe(1000, SipResponse.OK, "OK", 20, null);
    assertTrue(subscription.refresh(20, 1000));
    assertTrue(subscription.processResponse(200));
    assertTrue(subscription.getTimeLeft() < 21);

    // send NOTIFY with expiry too big
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    response = subscription.processNotify(notifyEvent);

    assertEquals(Response.BAD_REQUEST, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("received expiry >") != -1);
  }

  @Test
  public void testErrorNotifyNoExpiry() throws Exception {
    // prepare referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // send NOTIFY then remove expires info upon reception
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    RequestEvent notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    ((SubscriptionStateHeader) notifyEvent.getRequest().getHeader(SubscriptionStateHeader.NAME))
        .setExpires(0);
    Response response = subscription.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().contains("invalid expires"));
    assertTrue(subscription.replyToNotify(notifyEvent, response));
  }

  @Test
  public void testErrorReferFatalResponseOutOfDialog() throws Exception {
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.NOT_IMPLEMENTED, "NI", 60, null);

    // User A: send REFER out-of-dialog
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNull(subscription);
    assertEquals(SipResponse.NOT_IMPLEMENTED, ua.getReturnCode());
  }

  @Test
  public void testErrorReferFatalResponseInDialog() throws Exception {
    // create and set up the far end
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());

    // make the call from A
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertLastOperationSuccess(ua.format(), ua);

    // B side answer the call
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));

    // A side finish call establishment
    awaitAnswered("Outgoing call leg not answered", callA);
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    // B side - prepare to receive REFER
    ReferNotifySender referHandler = new ReferNotifySender(ub);
    referHandler.setDialog(callB.getDialog());
    referHandler.processRefer(4000, SipResponse.NOT_ACCEPTABLE_HERE,
        "Not Acceptable Here");

    // A side - send a REFER message in-dialog
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer(callA.getDialog(), referTo, null, 4000);
    assertNull(subscription);
    assertEquals(SipResponse.NOT_ACCEPTABLE_HERE, ua.getReturnCode());
  }

  @Test
  public void testErrorNotifyBadBodyPendingState() throws Exception {
    // prepare far end referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "Accepted");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // tell far end to send a bad NOTIFY body - wrong number of tokens
    assertTrue(ub.sendNotify(SubscriptionStateHeader.PENDING, null, "SIP/2.0 200\n", 30, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - bad SIP version token
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP2.0 200 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - non-numeric status code
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "all done", "SIP/2.0 200-OK OK\n",
        30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // upper
    assertTrue(ub.sendNotify(SubscriptionStateHeader.PENDING, null, "SIP/2.0 700 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // lower
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 99 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyBadBodyActiveState() throws Exception {
    // prepare far end referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // tell far end to send a bad NOTIFY body - wrong number of tokens
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200\n", 30, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - bad SIP version token
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP2.0 200 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - non-numeric status code
    assertTrue(ub
        .sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200-OK OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // upper
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 700 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // lower
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 99 OK\n", 30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyBadBodyTerminatedState() throws Exception {
    // prepare far end referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // tell far end to send a bad NOTIFY body - wrong number of tokens
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "no-resource", "SIP/2.0 200\n",
        30, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - bad SIP version token
    // start a new subscription
    ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");
    referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "no-resource", "SIP2.0 200 OK\n",
        30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - non-numeric status code
    // start a new subscription
    ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");
    referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "all done", "SIP/2.0 200-OK OK\n",
        30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // upper
    // start a new subscription
    ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");
    referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "no-resource", "SIP/2.0 700 OK\n",
        30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY body - status code out of range,
    // lower
    // start a new subscription
    ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");
    referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "no-resource", "SIP/2.0 99 OK\n",
        30, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

  }

  @Test
  public void testErrorNotifyBadContentTypeSubtypePendingState() throws Exception {
    // establish the subscription
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "Accepted");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // tell far end to send a bad NOTIFY content type
    ContentTypeHeader ct =
        ua.getParent().getHeaderFactory().createContentTypeHeader("bad-content-type", "sipfrag");
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK\n", 30, null,
        null, null, ct, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertHeaderContains(new SipResponse(response), AcceptHeader.NAME, "message");
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY content subtype
    ct =
        ua.getParent().getHeaderFactory().createContentTypeHeader("message", "bad-content-subtype");
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "done", "SIP/2.0 200 OK\n", 30,
        null, null, null, ct, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertHeaderContains(new SipResponse(response), AcceptHeader.NAME, "sipfrag");
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyBadContentTypeSubtypeActiveState() throws Exception {
    // establish the subscription
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // tell far end to send a bad NOTIFY content type
    ContentTypeHeader ct =
        ua.getParent().getHeaderFactory().createContentTypeHeader("bad-content-type", "sipfrag");
    assertTrue(ub.sendNotify(SubscriptionStateHeader.PENDING, null, "SIP/2.0 200 OK\n", 30, null,
        null, null, ct, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertHeaderContains(new SipResponse(response), AcceptHeader.NAME, "message");
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));

    // tell far end to send a bad NOTIFY content subtype
    ct =
        ua.getParent().getHeaderFactory().createContentTypeHeader("message", "bad-content-subtype");
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "done", "SIP/2.0 200 OK\n", 30,
        null, null, null, ct, false));
    // wait for & process the NOTIFY
    reqevent = subscription.waitNotify(1000);
    response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, subscription.getReturnCode());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionActive());
    // check the response that was created & reply
    assertEquals(SipResponse.UNSUPPORTED_MEDIA_TYPE, response.getStatusCode());
    assertHeaderContains(new SipResponse(response), AcceptHeader.NAME, "sipfrag");
    assertFalse(response.getReasonPhrase().equals("OK"));
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyMissingBody() throws Exception {
    // establish the subscription
    final ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "Accepted");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    awaitDialogReady(ub);

    // tell far end to send a bad NOTIFY - missing body
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null, null, 30);
    ContentTypeHeader ct =
        ua.getParent().getHeaderFactory().createContentTypeHeader("message", "sipfrag");
    notifyMsg.setHeader(ct);
    assertTrue(ub.sendNotify(notifyMsg, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("no body") != -1);
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyMissingCtHeader() throws Exception {
    // establish the subscription
    final ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "Accepted");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    awaitDialogReady(ub);

    // tell far end to send a bad NOTIFY - missing CT Header
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null,
            "SIP/2.0 200 OK\n", 30);
    notifyMsg.removeHeader(ContentTypeHeader.NAME);
    assertTrue(ub.sendNotify(notifyMsg, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("no content type header") != -1);
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testNotifyTimeouts() throws Exception {
    // create a refer-To URI
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    // create & prepare the referee simulator (User B) to respond to REFER
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.ACCEPTED, "OK Accepted");

    // User A: send REFER out-of-dialog
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertTrue(subscription.processResponse(1000));
    assertTrue(subscription.isSubscriptionPending());
    assertNoSubscriptionErrors(subscription);

    // TEST NOTIFY TIMEOUT WHILE IN PENDING STATE
    RequestEvent reqevent = subscription.waitNotify(200);
    assertNull(reqevent);
    assertEquals(SipSession.TIMEOUT_OCCURRED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());
    assertTrue(subscription.getEventErrors().size() > 0);
    subscription.clearEventErrors();
    assertFalse(subscription.getEventErrors().size() > 0);

    // TEST NOTIFY TIMEOUT WHILE IN ACTIVE STATE
    // send a NOTFIY to get the subscription into ACTIVE state
    String notifyBody = "SIP/2.0 200 OK\n";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));
    reqevent = subscription.waitNotify(200);
    assertNotNull(reqevent);
    Response resp = subscription.processNotify(reqevent);
    assertTrue(subscription.isSubscriptionActive());
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.replyToNotify(reqevent, resp));
    // now test notify timeout
    reqevent = subscription.waitNotify(200);
    assertNull(reqevent);
    assertEquals(SipSession.TIMEOUT_OCCURRED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getEventErrors().size() > 0);
    subscription.clearEventErrors();

    // TEST NOTIFY TIMEOUT WHILE IN TERMINATED STATE
    // terminate the subscription from the referrer side
    // prepare the far end to respond to unSUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK Done");
    // send the un-SUBSCRIBE
    assertTrue(subscription.unsubscribe(100));
    assertFalse(subscription.isRemovalComplete());
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionTerminated());
    assertTrue(subscription.processResponse(1000));
    assertTrue(subscription.isSubscriptionTerminated());
    assertNoSubscriptionErrors(subscription);
    // now test notify timeout
    reqevent = subscription.waitNotify(200);
    assertNull(reqevent);
    assertEquals(SipSession.TIMEOUT_OCCURRED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionTerminated());
    assertTrue(subscription.getEventErrors().size() > 0);

    subscription.dispose();
  }

  @Test
  public void testErrorNotifyMissingEventHeader() throws Exception {
    // establish the subscription
    final ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "pending");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    awaitDialogReady(ub);

    // tell far end to send a NOTIFY - then remove Event Header before
    // processing it (after reception, else stack won't send it)
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null,
            "SIP/2.0 200 OK\n", 30);
    assertTrue(ub.sendNotify(notifyMsg, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    reqevent.getRequest().removeHeader(EventHeader.NAME);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_EVENT, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("no event header") != -1);
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_EVENT, response.getStatusCode());
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyBadEventType() throws Exception {
    // establish the subscription
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "pending");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    awaitDialogReady(ub);

    // tell far end to send a NOTIFY - then corrupt event type before
    // processing it (after reception, else stack won't send it)
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null,
            "SIP/2.0 200 OK\n", 30);
    assertTrue(ub.sendNotify(notifyMsg, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    ((EventHeader) reqevent.getRequest().getHeader(EventHeader.NAME)).setEventType("doodah");
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_EVENT, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("unknown event") != -1);
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_EVENT, response.getStatusCode());
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyMissingSubsStateHeader() throws Exception {
    // establish the subscription
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "pending");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    awaitDialogReady(ub);

    // tell far end to send a NOTIFY - then remove Subs State Header before
    // processing it (after reception, else stack won't send it)
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null,
            "SIP/2.0 200 OK\n", 30);
    assertTrue(ub.sendNotify(notifyMsg, false));
    // wait for & process the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(1000);
    reqevent.getRequest().removeHeader(SubscriptionStateHeader.NAME);
    Response response = subscription.processNotify(reqevent);
    assertEquals(SipResponse.BAD_REQUEST, subscription.getReturnCode());
    assertTrue(subscription.getErrorMessage().indexOf("no subscription state header") != -1);
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
    // check the response that was created & reply
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertTrue(subscription.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrorNotifyBadEventID() throws Exception {
    // establish the subscription
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.ACCEPTED, "pending");
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    final ReferSubscriber subscription =
        ua.refer("sip:becky@cafesip.org", referTo, "my-event-id", 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    assertEquals(0, subscription.getEventErrors().size());

    awaitDialogReady(ub);

    // tell far end to send a NOTIFY with wrong event ID
    Request req = ub.getDialog().createRequest(Request.NOTIFY);
    Request notifyMsg =
        ub.addNotifyHeaders(req, null, null, SubscriptionStateHeader.ACTIVE, null,
            "SIP/2.0 200 OK\n", 30);
    ((EventHeader) notifyMsg.getHeader(EventHeader.NAME)).setEventId("wrong-event-id");
    assertTrue(ub.sendNotify(notifyMsg, false));

    // wait for the NOTIFY - never received here because
    // SipPhone.processRequestEvent() sees it as orphan and responds with
    // 481, but we'll get that error event
    await().until(new Runnable() {

      @Override
      public void run() {
        assertEquals(1, subscription.getEventErrors().size());
      }
    });
    assertTrue(subscription.getEventErrors().get(0).contains("orphan"));
    RequestEvent reqevent = subscription.waitNotify(100);
    assertNull(reqevent);
    assertEquals(2, subscription.getEventErrors().size());
    assertEquals(0, subscription.getTimeLeft());
    assertTrue(subscription.isSubscriptionPending());
  }

  @Test
  public void testMultipleSubscriptionsPerReferTo() throws Exception {
    // prepare referee side
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(1000, SipResponse.OK, "OK");

    // send REFER 1, get OK - minimal processing
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);
    ReferSubscriber subscription =
        ua.refer("sip:becky@cafesip.org", referTo, "eventid-1", 5000, null);
    assertNotNull(subscription);
    assertTrue(subscription.processResponse(200));

    // receive NOTIFY, send OK
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, "SIP/2.0 200 OK", 30, false));
    RequestEvent notifyEvent = subscription.waitNotify(500);
    assertNotNull(notifyEvent);
    Response response = subscription.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertTrue(subscription.replyToNotify(notifyEvent, response));

    // send REFER 2, sets up a second subscription
    ReferNotifySender ub2 = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub2.processRefer(1000, SipResponse.OK, "OK");
    ReferSubscriber subscription2 =
        ua.refer("sip:becky@cafesip.org", referTo, "eventid-2", 5000, null);
    assertNotNull(subscription2);
    assertTrue(subscription2.processResponse(200));

    // receive NOTIFY, send OK
    assertTrue(ub2.sendNotify(SubscriptionStateHeader.TERMINATED, "no-resource", "SIP/2.0 200 OK",
        -1, false));
    notifyEvent = subscription2.waitNotify(500);
    assertNotNull(notifyEvent);
    response = subscription2.processNotify(notifyEvent);
    assertNotNull(response);
    assertEquals(SipResponse.OK, subscription2.getReturnCode());
    assertTrue(subscription2.replyToNotify(notifyEvent, response));

    // check refer list methods on SipPhone
    assertEquals(2, ua.getRefererList().size());
    assertTrue(ua.getRefererList().contains(subscription));
    assertTrue(ua.getRefererList().contains(subscription2));
    assertEquals(2, ua.getRefererInfo(referTo).size());
    assertTrue(ua.getRefererInfo(referTo).contains(subscription));
    assertTrue(ua.getRefererInfo(referTo).contains(subscription2));
    assertEquals(1, ua.getRefererInfoByDialog(subscription.getDialogId()).size());
    assertTrue(ua.getRefererInfoByDialog(subscription.getDialogId()).contains(subscription));
    assertEquals(1, ua.getRefererInfoByDialog(subscription2.getDialogId()).size());
    assertTrue(ua.getRefererInfoByDialog(subscription2.getDialogId()).contains(subscription2));
    // remove one of them from the list & recheck results
    subscription2.dispose();
    assertEquals(1, ua.getRefererList().size());
    assertTrue(ua.getRefererList().contains(subscription));
    assertEquals(1, ua.getRefererInfo(referTo).size());
    assertTrue(ua.getRefererInfo(referTo).contains(subscription));
    assertEquals(1, ua.getRefererInfoByDialog(subscription.getDialogId()).size());
    assertTrue(ua.getRefererInfoByDialog(subscription.getDialogId()).contains(subscription));
  }

  @Test
  public void testOutboundIndialogAdditionalHeaders() throws Exception {
    // Setup - Establish a call from A to B
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertNotNull(callA);
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitAnswered(callA);
    assertTrue(callA.sendInviteOkAck());

    // B side - prepare to receive REFER
    ReferNotifySender referHandler = new ReferNotifySender(ub);
    referHandler.setDialog(callB.getDialog());
    referHandler.processRefer(2000, SipResponse.ACCEPTED, "Accepted");

    // A side - send a REFER message with additional/replace headers & body

    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ArrayList<Header> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(ua.getParent().getHeaderFactory().createOrganizationHeader("cafesip"));
    additionalHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    Address addr = ua.getParent().getAddressFactory().createAddress("sip:joe@shmoe.net");
    ContactHeader hdr = ua.getParent().getHeaderFactory().createContactHeader(addr);
    replaceHeaders.add(hdr);

    ReferSubscriber subscription =
        ua.refer(callA.getDialog(), referTo, null, 500, additionalHeaders, replaceHeaders,
            "myReferBody");
    assertNotNull(subscription);

    // B side - verify received REFER contents
    RequestEvent requestEvent = referHandler.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    SipMessage msg = new SipRequest(requestEvent);
    assertHeaderContains(msg, OrganizationHeader.NAME, "cafesip");
    assertHeaderContains(msg, ContactHeader.NAME, "joe@shmoe.net");
    ListIterator contactHdrs = requestEvent.getRequest().getHeaders(ContactHeader.NAME);
    contactHdrs.next();
    contactHdrs.remove();
    assertFalse(contactHdrs.hasNext());
    assertHeaderContains(msg, ContentTypeHeader.NAME, "applicationn/texxt");
    assertBodyContains(msg, "myReferBod");

    // we're done
    callA.disposeNoBye();
    callB.disposeNoBye();
  }

  @Test
  public void testOutboundIndialogAdditionalHeadersAsStrings() throws Exception {
    // Setup - Establish a call from A to B
    SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
    ub.setLoopback(true);
    SipCall callB = ub.createSipCall();
    assertTrue(callB.listenForIncomingCall());
    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", ua.getStackAddress() + ':' + myPort + '/'
            + testProtocol);
    assertNotNull(callA);
    assertTrue(callB.waitForIncomingCall(1000));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0));
    awaitAnswered(callA);
    assertTrue(callA.sendInviteOkAck());

    // B side - prepare to receive REFER
    ReferNotifySender referHandler = new ReferNotifySender(ub);
    referHandler.setDialog(callB.getDialog());
    referHandler.processRefer(2000, SipResponse.ACCEPTED, "Accepted");

    // A side - send a REFER message with additional/replace headers & body

    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ArrayList<String> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(ua.getParent().getHeaderFactory().createOrganizationHeader("cafesip")
        .toString());

    ArrayList<String> replaceHeaders = new ArrayList<>();
    Address addr = ua.getParent().getAddressFactory().createAddress("sip:joe@shmoe.net");
    ContactHeader hdr = ua.getParent().getHeaderFactory().createContactHeader(addr);
    replaceHeaders.add(hdr.toString());

    ReferSubscriber subscription =
        ua.refer(callA.getDialog(), referTo, null, 500, "myReferBody", "applicationn", "texxt",
            additionalHeaders, replaceHeaders);
    assertNotNull(subscription);

    // B side - verify received REFER contents
    RequestEvent requestEvent = referHandler.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    SipMessage msg = new SipRequest(requestEvent);
    assertHeaderContains(msg, OrganizationHeader.NAME, "cafesip");
    assertHeaderContains(msg, ContactHeader.NAME, "joe@shmoe.net");
    ListIterator contactHdrs = requestEvent.getRequest().getHeaders(ContactHeader.NAME);
    contactHdrs.next();
    contactHdrs.remove();
    assertFalse(contactHdrs.hasNext());
    assertHeaderContains(msg, ContentTypeHeader.NAME, "applicationn/texxt");
    assertBodyContains(msg, "myReferBod");

    // we're done
    callA.disposeNoBye();
    callB.disposeNoBye();
  }

  @Test
  public void testOutboundOutdialogAdditionalHeaders() throws Exception {
    // create & prepare the referee
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.OK, "OK");

    // A: send REFER with additional/replace headers & body

    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ArrayList<Header> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(ua.getParent().getHeaderFactory().createOrganizationHeader("cafesip"));
    additionalHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    Address addr = ua.getParent().getAddressFactory().createAddress("sip:joe@shmoe.net");
    ContactHeader hdr = ua.getParent().getHeaderFactory().createContactHeader(addr);
    replaceHeaders.add(hdr);

    ReferSubscriber subscription =
        ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null, additionalHeaders,
            replaceHeaders, "myReferBody");

    assertNotNull(subscription);

    // B side - verify received REFER contents
    RequestEvent requestEvent = ub.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    SipMessage msg = new SipRequest(requestEvent);
    assertHeaderContains(msg, OrganizationHeader.NAME, "cafesip");
    assertHeaderContains(msg, ContactHeader.NAME, "joe@shmoe.net");
    ListIterator contactHdrs = requestEvent.getRequest().getHeaders(ContactHeader.NAME);
    contactHdrs.next();
    contactHdrs.remove();
    assertFalse(contactHdrs.hasNext());
    assertHeaderContains(msg, ContentTypeHeader.NAME, "applicationn/texxt");
    assertBodyContains(msg, "myReferBod");
  }

  @Test
  public void testOutboundOutdialogAdditionalHeadersAsStrings() throws Exception {

    // create & prepare the referee
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.OK, "OK");

    // A: send REFER with additional/replace headers & body

    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", null, null, null,
            "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994", null, null);

    ArrayList<String> additionalHeaders = new ArrayList<>();
    additionalHeaders.add(ua.getParent().getHeaderFactory().createOrganizationHeader("cafesip")
        .toString());
    additionalHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt").toString());

    ArrayList<String> replaceHeaders = new ArrayList<>();
    Address addr = ua.getParent().getAddressFactory().createAddress("sip:joe@shmoe.net");
    ContactHeader hdr = ua.getParent().getHeaderFactory().createContactHeader(addr);
    replaceHeaders.add(hdr.toString());

    ReferSubscriber subscription =
        ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null, "myReferBody", "applicationn",
            "texxt", additionalHeaders, replaceHeaders);
    assertNotNull(subscription);

    // B side - verify received REFER contents
    RequestEvent requestEvent = ub.getLastReceivedRequest().getRequestEvent();
    assertNotNull(requestEvent);
    SipMessage msg = new SipRequest(requestEvent);
    assertHeaderContains(msg, OrganizationHeader.NAME, "cafesip");
    assertHeaderContains(msg, ContactHeader.NAME, "joe@shmoe.net");
    ListIterator contactHdrs = requestEvent.getRequest().getHeaders(ContactHeader.NAME);
    contactHdrs.next();
    contactHdrs.remove();
    assertFalse(contactHdrs.hasNext());
    assertHeaderContains(msg, ContentTypeHeader.NAME, "applicationn/texxt");
    assertBodyContains(msg, "myReferBod");
  }

  @Test
  public void testExample() throws Exception {
    // create a refer-To URI
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    // create & prepare the referee simulator (User B) to respond to REFER
    ReferNotifySender ub = new ReferNotifySender(sipStack.createSipPhone("sip:becky@cafesip.org"));
    ub.processRefer(5000, SipResponse.OK, "OK");

    // User A: send REFER out-of-dialog
    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription); // REFER sending successful, received a
    // response
    assertEquals(SipResponse.OK, subscription.getReturnCode()); // status
    // code
    assertTrue(subscription.isSubscriptionPending()); // response not
    // processed yet
    // optionally examine response in full detail using JAIN SIP:
    // ResponseEvent responseEvent = subscription.getCurrentResponse(); Etc.

    // process the received REFER response, check results
    assertTrue(subscription.processResponse(1000));
    assertTrue(subscription.isSubscriptionActive());
    assertNoSubscriptionErrors(subscription);

    // User B send active-state NOTIFY to A
    String notifyBody = "SIP/2.0 200 OK\n";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));

    // User A: get the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(500);
    assertNotNull(reqevent);
    // optionally examine NOTIFY in full detail using JAIN SIP:
    Request request = reqevent.getRequest();
    assertEquals(2400,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    assertBodyContains(new SipRequest(reqevent), "200 OK");

    // process the NOTIFY
    Response resp = subscription.processNotify(reqevent);
    assertNotNull(resp);

    // check the NOTIFY processing results
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getTimeLeft() <= 2400 && subscription.getTimeLeft() > 2300);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertNoSubscriptionErrors(subscription);

    // optionally check or corrupt the response that was created by
    // processNotify()
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));
    // Etc. - modify the resp using JAIN SIP API

    // User A: reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // terminate the subscription from the referrer side
    // prepare the far end to respond to unSUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK Done");

    // send the un-SUBSCRIBE
    assertTrue(subscription.unsubscribe(100));
    if (!subscription.isRemovalComplete()) {
      assertEquals(SipResponse.OK, subscription.getReturnCode());
      assertTrue(subscription.isSubscriptionTerminated());
      assertNoSubscriptionErrors(subscription);
      // optionally examine response in full detail using JAIN SIP:
      resp = subscription.getCurrentResponse().getResponse();
      assertEquals("OK Done", resp.getReasonPhrase()); // Etc.

      // process the received SUBSCRIBE response, check results
      assertTrue(subscription.processResponse(1000));
      assertTrue(subscription.isSubscriptionTerminated());
      assertEquals("Unsubscribe", subscription.getTerminationReason());
      assertEquals(0, subscription.getTimeLeft());
      assertNoSubscriptionErrors(subscription);

      // User B: send terminated NOTIFY
      notifyBody = "SIP/2.0 100 Trying\n";
      assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "Unsubscribed", notifyBody, 0,
          false));

      // User A: get the NOTIFY
      reqevent = subscription.waitNotify(500);
      assertNotNull(reqevent);
      assertNoSubscriptionErrors(subscription);
      // optionally examine NOTIFY in full detail using JAIN SIP:
      request = reqevent.getRequest(); // Etc.

      // process the NOTIFY
      resp = subscription.processNotify(reqevent);
      assertNotNull(resp);

      // check the NOTIFY processing results
      assertTrue(subscription.isSubscriptionTerminated());
      assertEquals(SipResponse.OK, subscription.getReturnCode());
      assertNoSubscriptionErrors(subscription);

      // reply to the NOTIFY
      assertTrue(subscription.replyToNotify(reqevent, resp));
      subscription.dispose();
    }
  }
}
