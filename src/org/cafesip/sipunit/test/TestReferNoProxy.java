/*
 * Created on May 17, 2009
 * 
 * Copyright 2005 CafeSip.org 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *	http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 */
package org.cafesip.sipunit.test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.cafesip.sipunit.ReferNotifySender;
import org.cafesip.sipunit.ReferSubscriber;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTestCase;
import org.cafesip.sipunit.SipTransaction;

/**
 * This class tests SipUnit refer functionality. Currently only the outbound
 * REFER side is supported.
 * 
 * Tests in this class do not require a proxy/registrar server.
 * 
 * @author Becky McElroy
 */
public class TestReferNoProxy extends SipTestCase
{
    private SipStack sipStack;

    private SipPhone ua;

    private int myPort;

    private String testProtocol;

    private static final Properties defaultProperties = new Properties();
    static
    {
        String host = null;
        try
        {
            host = InetAddress.getLocalHost().getHostAddress();
        }
        catch (UnknownHostException e)
        {
            host = "localhost";
        }

        defaultProperties.setProperty("javax.sip.IP_ADDRESS", host);
        defaultProperties.setProperty("javax.sip.STACK_NAME", "testRefer");
        defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testRefer_debug.txt");
        defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testRefer_log.txt");
        defaultProperties
                .setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        defaultProperties.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

        defaultProperties.setProperty("sipunit.trace", "true");
        defaultProperties.setProperty("sipunit.test.port", "5061");
        defaultProperties.setProperty("sipunit.test.protocol", "udp");
    }

    private Properties properties = new Properties(defaultProperties);

    public TestReferNoProxy(String arg0)
    {
        super(arg0);

        properties.putAll(System.getProperties());

        try
        {
            myPort = Integer.parseInt(properties
                    .getProperty("sipunit.test.port"));
        }
        catch (NumberFormatException e)
        {
            myPort = 5061;
        }

        testProtocol = properties.getProperty("sipunit.test.protocol");

    }

    /*
     * @see SipTestCase#setUp()
     */
    public void setUp() throws Exception
    {
        try
        {
            sipStack = new SipStack(testProtocol, myPort, properties);
            SipStack.setTraceEnabled(properties.getProperty("sipunit.trace")
                    .equalsIgnoreCase("true")
                    || properties.getProperty("sipunit.trace")
                            .equalsIgnoreCase("on"));
        }
        catch (Exception ex)
        {
            fail("Exception: " + ex.getClass().getName() + ": "
                    + ex.getMessage());
            throw ex;
        }

        try
        {
            ua = sipStack.createSipPhone("sip:amit@nist.gov");
        }
        catch (Exception ex)
        {
            fail("Exception creating SipPhone: " + ex.getClass().getName()
                    + ": " + ex.getMessage());
            throw ex;
        }
    }

    /*
     * @see SipTestCase#tearDown()
     */
    public void tearDown() throws Exception
    {
        ua.dispose();
        sipStack.dispose();
    }

    public void testGetURI() throws Exception
    {
        // test scheme and userHostPort
        SipURI uri = ua.getUri("sip:", "sipmaster@192.168.1.11:5060", null,
                null, null, null, null, null, null);
        assertEquals("sip", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(5060, uri.getPort());
        assertTrue("sip:sipmaster@192.168.1.11:5060".equalsIgnoreCase(uri
                .toString()));

        uri = ua.getUri("sips:", "sipmaster@192.168.1.11", null, null, null,
                null, null, null, null);
        assertEquals("sips", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertTrue("sips:sipmaster@192.168.1.11".equalsIgnoreCase(uri
                .toString()));

        uri = ua.getUri(null, "sip:sipmaster@192.168.1.11:5060", null, null,
                null, null, null, null, null);
        assertEquals("sip", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(5060, uri.getPort());
        assertTrue("sip:sipmaster@192.168.1.11:5060".equalsIgnoreCase(uri
                .toString()));

        try
        {
            uri = ua.getUri(null, "sipmaster@192.168.1.11:5060", null, null,
                    null, null, null, null, null);
            fail("getUri() accepted bad input");
            uri = ua.getUri("doodah", "sipmaster@192.168.1.11:5060", null,
                    null, null, null, null, null, null);
            fail("getUri() accepted bad input");
            uri = ua.getUri("sip:", "sipmaster", null, null, null, null, null,
                    null, null);
            fail("getUri() accepted bad input");
            uri = ua.getUri("sip:", "sip:sipmaster@192.168.1.11:5060", null,
                    null, null, null, null, null, null);
            fail("getUri() accepted bad input");
        }
        catch (ParseException e)
        {
        }

        // test transportUriParameter, methodUriParameter, otherUriParameters
        Map<String, String> paramMap = new HashMap<String, String>();
        paramMap.put("maddr", "abc");
        uri = ua.getUri("sip:", "sipmaster@192.168.1.11:5060", "udp",
                "SUBSCRIBE", paramMap, null, null, null, null);
        assertEquals("sip", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(5060, uri.getPort());
        assertEquals("udp", uri.getTransportParam());
        assertEquals("SUBSCRIBE", uri.getMethodParam());
        assertEquals("abc", uri.getMAddrParam());
        assertTrue("sip:sipmaster@192.168.1.11:5060;transport=udp;method=SUBSCRIBE;maddr=abc"
                .equalsIgnoreCase(uri.toString()));

        // test joinUriHeader, Map<String, String> otherUriHeaders
        Map<String, String> headerMap = new HashMap<String, String>();
        headerMap.put("Contact", "sip:abc@192.168.1.12");
        paramMap.clear();
        paramMap.put("maddr", "abc");
        uri = ua.getUri("sip:", "sipmaster@192.168.1.11", "tls", null,
                paramMap, "otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef", null,
                null, headerMap);
        assertEquals("sip", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertEquals("tls", uri.getTransportParam());
        assertEquals(null, uri.getMethodParam());
        assertEquals("abc", uri.getMAddrParam());
        assertEquals("otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef", uri
                .getHeader("Join"));
        assertEquals("sip:abc@192.168.1.12", uri.getHeader("Contact"));
        assertTrue(uri.toString().startsWith(
                "sip:sipmaster@192.168.1.11;transport=tls;maddr=abc"));
        assertTrue(uri.toString().indexOf(
                "Join=otherDialog%3Bto-tag%abc%3Bfrom-tag%3Ddef") > 0);
        assertTrue(uri.toString().indexOf("Contact=sip:abc@192.168.1.12") > 0);

        // Test String replacesUriHeader, String bodyUriHeader
        uri = ua.getUri("sip:", "sipmaster@192.168.1.11", null, null, null,
                null, "otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz",
                "This is my body", null);
        assertEquals("sip", uri.getScheme());
        assertEquals("sipmaster", uri.getUser());
        assertEquals("192.168.1.11", uri.getHost());
        assertEquals(-1, uri.getPort());
        assertEquals(null, uri.getTransportParam());
        assertEquals(null, uri.getMethodParam());
        assertEquals(null, uri.getHeader("Join"));
        assertEquals("otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz", uri
                .getHeader("Replaces"));
        assertEquals("This is my body", uri.getHeader("Body"));
        assertTrue(uri.toString().startsWith("sip:sipmaster@192.168.1.11"));
        assertTrue(uri.toString().indexOf(
                "Replaces=otherDialog%3Bto-tag%wx%3Bfrom-tag%3Dyz") > 0);
        assertTrue(uri.toString().indexOf("body=This is my body") > 0);
    }

    public void testOutboundOutdialogBasicWithUnsubscribe() throws Exception
    {
        // A sends out-of-dialog REFER to B, gets OK response
        // B sends active-state NOTIFY to A, gets OK response
        // A unsubscribes, gets OK response
        // B sends terminated NOTIFY, gets OK response
        SipURI referTo = ua
                .getUri(
                        "sip:",
                        "dave@denver.example.org",
                        "udp",
                        null,
                        null,
                        null,
                        "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994",
                        null, null);

        // create & prepare the referee
        ReferNotifySender ub = new ReferNotifySender(sipStack
                .createSipPhone("sip:becky@cafesip.org"));
        assertTrue(ub.processRefer(5000, SipResponse.OK, "OK"));
        Thread.sleep(500);

        assertEquals(0, ua.getRefererList().size());

        // A: send REFER
        ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org",
                referTo, null, 5000, null);
        assertNotNull(subscription);

        // check the results - subscription, response, SipPhone referer list
        assertEquals(SipResponse.OK, subscription.getReturnCode());
        assertTrue(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionTerminated());
        Response resp = subscription.getCurrentResponse().getResponse();
        assertEquals(SipRequest.REFER, ((CSeqHeader) resp
                .getHeader(CSeqHeader.NAME)).getMethod());
        assertEquals("OK", resp.getReasonPhrase());
        assertNull(resp.getExpires());
        assertEquals(resp.toString(), subscription.getLastReceivedResponse()
                .getMessage().toString());
        ArrayList<SipResponse> received_responses = subscription
                .getAllReceivedResponses();
        assertEquals(1, received_responses.size());
        assertEquals(resp.toString(), received_responses.get(0).toString());
        assertEquals(1, ua.getRefererList().size());
        assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(
                subscription.getDialogId()).get(0));
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
        // **********************************************************
        // B: send NOTIFY
        Thread.sleep(500);
        String notifyBody = "SIP/2.0 200 OK";
        assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null,
                notifyBody, 2400, false));
        Thread.sleep(10);

        // A: get the NOTIFY
        RequestEvent reqevent = subscription.waitNotify(500);
        assertNotNull(reqevent);

        // examine the NOTIFY request object, verify subscription getters
        Request request = reqevent.getRequest();
        assertEquals(Request.NOTIFY, request.getMethod());
        assertEquals(2400, ((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires());
        ArrayList<SipRequest> received_requests = subscription
                .getAllReceivedRequests();
        assertEquals(1, received_requests.size());
        SipRequest req = subscription.getLastReceivedRequest();
        assertNotNull(req);
        assertTrue(req.isNotify());
        assertFalse(req.isSubscribe());
        assertEquals(((SipRequest) received_requests.get(0)).getMessage()
                .toString(), request.toString());
        assertEquals(received_requests.get(0).toString(), req.toString());
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
        // **************************************************
        // prepare the far end
        assertTrue(ub.processSubscribe(5000, SipResponse.OK, "OK Done"));
        Thread.sleep(100);

        // send the un-SUBSCRIBE
        assertTrue(subscription.unsubscribe(100));
        assertFalse(subscription.isRemovalComplete());

        // check the results - subscription, response, SipPhone referer list
        assertEquals(SipResponse.OK, subscription.getReturnCode());
        assertFalse(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionActive());
        assertTrue(subscription.isSubscriptionTerminated());
        resp = subscription.getCurrentResponse().getResponse();
        assertEquals(SipRequest.SUBSCRIBE, ((CSeqHeader) resp
                .getHeader(CSeqHeader.NAME)).getMethod());
        assertEquals("OK Done", resp.getReasonPhrase());
        assertEquals(0, resp.getExpires().getExpires());
        assertEquals(resp.toString(), subscription.getLastReceivedResponse()
                .getMessage().toString());
        received_responses = subscription.getAllReceivedResponses();
        assertEquals(2, received_responses.size());
        assertEquals(resp.toString(), received_responses.get(1).toString());
        assertEquals(1, ua.getRefererList().size());
        assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(
                subscription.getDialogId()).get(0));
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
        // *********************************************************
        // tell far end to send a NOTIFY
        Thread.sleep(500);
        notifyBody = "SIP/2.0 100 Trying";
        assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED,
                "Unsubscribed", notifyBody, 0, false));
        Thread.sleep(10);

        // A: get the NOTIFY
        reqevent = subscription.waitNotify(500);
        assertNotNull(reqevent);

        // examine the NOTIFY request object, verify subscription getters
        request = reqevent.getRequest();
        assertEquals(Request.NOTIFY, request.getMethod());
        assertEquals(-1, ((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires());
        received_requests = subscription.getAllReceivedRequests();
        assertEquals(2, received_requests.size());
        req = subscription.getLastReceivedRequest();
        assertNotNull(req);
        assertTrue(req.isNotify());
        assertFalse(req.isSubscribe());
        assertEquals(((SipRequest) received_requests.get(1)).getMessage()
                .toString(), request.toString());
        assertEquals(received_requests.get(1).toString(), req.toString());
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

    public void testOutboundIndialogBasic() throws Exception
    {
        // A calls B, call established
        // A sends in-dialog REFER to B, gets 202 Accepted
        // B sends subscription-terminating NOTIFY to A, gets OK in response

        // create and set up the far end
        SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
        SipCall b = ub.createSipCall();
        assertTrue(b.listenForIncomingCall());

        // make the call from A
        SipCall a = ua.makeCall("sip:becky@cafesip.org", properties
                .getProperty("javax.sip.IP_ADDRESS")
                + ':' + myPort + '/' + testProtocol);
        assertLastOperationSuccess(ua.format(), ua);

        // B side answer the call
        assertTrue(b.waitForIncomingCall(1000));
        assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
        Thread.sleep(20);
        assertTrue(b.sendIncomingCallResponse(Response.OK,
                "Answer - Hello world", 0));
        Thread.sleep(20);

        // A side finish call establishment
        assertAnswered("Outgoing call leg not answered", a);
        a.sendInviteOkAck();
        assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);
        Thread.sleep(1000);

        // B side - prepare to receive REFER
        ReferNotifySender referHandler = new ReferNotifySender(ub);
        referHandler.setDialog(b.getDialog());
        assertTrue(referHandler.processRefer(4000, SipResponse.ACCEPTED,
                "Accepted"));

        // A side - send a REFER message
        SipURI referTo = ua
                .getUri(
                        "sip:",
                        "dave@denver.example.org",
                        "udp",
                        null,
                        null,
                        null,
                        "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994",
                        null, null);

        ReferSubscriber subscription = ua.refer(a.getDialog(), referTo, null,
                4000);
        if (subscription == null)
        {
            fail(ua.getReturnCode() + ':' + ua.getErrorMessage());
        }

        // B side - verify received REFER contents
        RequestEvent requestEvent = referHandler.getLastReceivedRequest()
                .getRequestEvent();
        assertNotNull(requestEvent);
        Request req = requestEvent.getRequest();
        assertEquals(SipRequest.REFER, req.getMethod());
        assertEquals(b.getDialogId(), requestEvent.getDialog().getDialogId());

        // A side - check the initial results - subscription, response,
        // SipPhone referer list
        assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
        assertTrue(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionTerminated());
        Response resp = subscription.getCurrentResponse().getResponse();
        assertEquals(SipRequest.REFER, ((CSeqHeader) resp
                .getHeader(CSeqHeader.NAME)).getMethod());
        assertEquals("Accepted", resp.getReasonPhrase());
        assertNull(resp.getExpires());
        assertEquals(resp.toString(), subscription.getLastReceivedResponse()
                .getMessage().toString());
        ArrayList<SipResponse> received_responses = subscription
                .getAllReceivedResponses();
        assertEquals(1, received_responses.size());
        assertEquals(resp.toString(), received_responses.get(0).toString());
        assertEquals(1, ua.getRefererList().size());
        assertEquals(subscription, ua.getRefererList().get(0));
        assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(
                subscription.getDialogId()).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(a.getDialogId())
                .get(0));

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
        Thread.sleep(20);
        Request notifyRequest = b.getDialog().createRequest(SipRequest.NOTIFY);
        notifyRequest = referHandler.addNotifyHeaders(notifyRequest, null,
                null, SubscriptionStateHeader.TERMINATED, "noresource",
                "SIP/2.0 100 Trying", 0);
        SipTransaction trans = referHandler.sendStatefulNotify(notifyRequest,
                false);
        assertNotNull(trans);

        // A side - wait for the NOTIFY
        RequestEvent reqevent = subscription.waitNotify(1000);
        assertNotNull(reqevent);

        // A side - examine the NOTIFY request object, verify subscription
        // message getters
        Request request = reqevent.getRequest();
        assertEquals(Request.NOTIFY, request.getMethod());
        assertTrue(((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires() < 1);
        ArrayList<SipRequest> received_requests = subscription
                .getAllReceivedRequests();
        assertEquals(1, received_requests.size());
        SipRequest sipreq = subscription.getLastReceivedRequest();
        assertNotNull(sipreq);
        assertTrue(sipreq.isNotify());
        assertFalse(sipreq.isSubscribe());
        assertEquals(received_requests.get(0).getMessage().toString(), request
                .toString());
        assertEquals(received_requests.get(0).toString(), sipreq.toString());
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
        assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse()
                .getStatusCode());

        // cleanup
        a.disposeNoBye();
        b.disposeNoBye();
    }

    public void testOutboundIndialogBrefersAwithRefresh() throws Exception
    {
        // A calls B, call established
        // B sends in-dialog REFER to A, gets 202 Accepted
        // A sends state-active NOTIFY to B, gets OK in response
        // A sends another NOTIFY to B, gets OK in response
        // B refreshes the subscription
        // A sends subscription-terminating NOTIFY to B, gets OK in response

        // create and set up the far end
        SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
        SipCall b = ub.createSipCall();
        assertTrue(b.listenForIncomingCall());

        // make the call from A
        SipCall a = ua.makeCall("sip:becky@cafesip.org", properties
                .getProperty("javax.sip.IP_ADDRESS")
                + ':' + myPort + '/' + testProtocol);
        assertLastOperationSuccess(ua.format(), ua);

        // B side answer the call
        assertTrue(b.waitForIncomingCall(1000));
        assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
        Thread.sleep(20);
        assertTrue(b.sendIncomingCallResponse(Response.OK,
                "Answer - Hello world", 0));
        Thread.sleep(20);

        // A side finish call establishment
        assertAnswered("Outgoing call leg not answered", a);
        a.sendInviteOkAck();
        assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);
        Thread.sleep(1000);

        // B sends in-dialog REFER to A, gets 202 Accepted
        // ****************************************************************
        // A side - prepare to receive REFER
        ReferNotifySender referHandler = new ReferNotifySender(ua);
        referHandler.setDialog(a.getDialog());
        assertTrue(referHandler.processRefer(4000, SipResponse.ACCEPTED,
                "Accepted"));

        // B side - send a REFER message
        SipURI referTo = ub.getUri("sip:", "dave@denver.example.org", "udp",
                "INVITE", null, null, null, null, null);

        ReferSubscriber subscription = ub.refer(b.getDialog(), referTo,
                "myeventid", 4000);
        if (subscription == null)
        {
            fail(ub.getReturnCode() + ':' + ub.getErrorMessage());
        }

        // A side - verify received REFER contents
        RequestEvent requestEvent = referHandler.getLastReceivedRequest()
                .getRequestEvent();
        assertNotNull(requestEvent);
        Request req = requestEvent.getRequest();
        assertEquals(SipRequest.REFER, req.getMethod());
        assertEquals(a.getDialogId(), requestEvent.getDialog().getDialogId());
        assertEquals("myeventid", ((EventHeader) req
                .getHeader(EventHeader.NAME)).getEventId());

        // B side - check the initial results - subscription, response,
        // SipPhone referer list
        assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
        assertTrue(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionTerminated());
        Response resp = subscription.getCurrentResponse().getResponse();
        assertEquals(SipRequest.REFER, ((CSeqHeader) resp
                .getHeader(CSeqHeader.NAME)).getMethod());
        assertEquals("Accepted", resp.getReasonPhrase());
        assertNull(resp.getExpires());
        assertEquals(resp.toString(), subscription.getLastReceivedResponse()
                .getMessage().toString());
        ArrayList<SipResponse> received_responses = subscription
                .getAllReceivedResponses();
        assertEquals(1, received_responses.size());
        assertEquals(resp.toString(), received_responses.get(0).toString());
        assertEquals(1, ub.getRefererList().size());
        assertEquals(subscription, ub.getRefererList().get(0));
        assertEquals(subscription, ub.getRefererInfo(referTo).get(0));
        assertEquals(subscription, ub.getRefererInfoByDialog(
                subscription.getDialogId()).get(0));
        assertEquals(subscription, ub.getRefererInfoByDialog(b.getDialogId())
                .get(0));

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
        // **************************************************************
        // A side - send a NOTIFY
        Thread.sleep(20);
        Request notifyRequest = a.getDialog().createRequest(SipRequest.NOTIFY);
        notifyRequest = referHandler.addNotifyHeaders(notifyRequest, null,
                null, SubscriptionStateHeader.ACTIVE, null,
                "SIP/2.0 100 Trying", 60);
        SipTransaction trans = referHandler.sendStatefulNotify(notifyRequest,
                false);
        assertNotNull(trans);

        // B side - wait for the NOTIFY
        RequestEvent reqevent = subscription.waitNotify(1000);
        assertNotNull(reqevent);

        // B side - examine the NOTIFY request object, verify subscription
        // message getters
        Request request = reqevent.getRequest();
        assertEquals(Request.NOTIFY, request.getMethod());
        assertEquals(60, ((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires());
        ArrayList<SipRequest> received_requests = subscription
                .getAllReceivedRequests();
        assertEquals(1, received_requests.size());
        SipRequest sipreq = subscription.getLastReceivedRequest();
        assertNotNull(sipreq);
        assertTrue(sipreq.isNotify());
        assertFalse(sipreq.isSubscribe());
        assertEquals(received_requests.get(0).getMessage().toString(), request
                .toString());
        assertEquals(received_requests.get(0).toString(), sipreq.toString());
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
        assertTrue(subscription.getTimeLeft() <= 60
                && subscription.getTimeLeft() > 55);

        // B side - check the NOTIFY response that was created
        assertEquals(SipResponse.OK, resp.getStatusCode());
        assertEquals(SipResponse.OK, subscription.getReturnCode());

        // B side - reply to the NOTIFY
        assertTrue(subscription.replyToNotify(reqevent, resp));

        // A side - verify the NOTIFY response got sent by B
        Object obj = referHandler.waitResponse(trans, 10000);
        assertNotNull(obj);
        assertTrue(obj instanceof ResponseEvent);
        assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse()
                .getStatusCode());

        // A sends another NOTIFY to B, gets OK in response
        // **************************************************************
        Thread.sleep(800);
        notifyRequest = a.getDialog().createRequest(SipRequest.NOTIFY);
        notifyRequest = referHandler.addNotifyHeaders(notifyRequest, null,
                null, SubscriptionStateHeader.ACTIVE, null,
                "SIP/2.0 180 Ringing", 20);
        trans = referHandler.sendStatefulNotify(notifyRequest, false);
        assertNotNull(trans);

        // B side - wait for the NOTIFY
        reqevent = subscription.waitNotify(1000);
        assertNotNull(reqevent);

        // B side - examine the NOTIFY request object
        request = reqevent.getRequest();
        assertEquals(20, ((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires());
        received_requests = subscription.getAllReceivedRequests();
        assertEquals(2, received_requests.size());
        sipreq = subscription.getLastReceivedRequest();
        assertNotNull(sipreq);
        assertEquals(received_requests.get(1).getMessage().toString(), request
                .toString());
        assertBodyContains(sipreq, "SIP/2.0 180 Ringing");

        // B side - process the NOTIFY
        resp = subscription.processNotify(reqevent);
        assertNotNull(resp);
        assertNoSubscriptionErrors(subscription);

        // B side - check the NOTIFY processing results on subscription
        assertTrue(subscription.isSubscriptionActive());
        assertTrue(subscription.getTimeLeft() <= 20
                && subscription.getTimeLeft() > 15);

        // B side - check the NOTIFY response that was created
        assertEquals(SipResponse.OK, resp.getStatusCode());

        // B side - reply to the NOTIFY
        assertTrue(subscription.replyToNotify(reqevent, resp));

        // A side - verify the NOTIFY response got sent by B
        obj = referHandler.waitResponse(trans, 10000);
        assertNotNull(obj);
        assertTrue(obj instanceof ResponseEvent);
        assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse()
                .getStatusCode());

        // B refreshes the subscription
        // **************************************************************
        // prepare A to receive SUBSCRIBE
        assertTrue(referHandler.processSubscribe(2000, SipResponse.OK, "OK"));
        // refresh
        assertTrue(subscription.refresh(10, "eventid-x", 500));
        assertEquals(SipResponse.OK, subscription.getReturnCode());
        assertEquals(subscription.getLastReceivedResponse().getResponseEvent(),
                subscription.getCurrentResponse());
        assertEquals("eventid-x", ((EventHeader) subscription
                .getLastSentRequest().getHeader(EventHeader.NAME)).getEventId());
        assertTrue(subscription.processResponse(200));
        assertTrue(subscription.isSubscriptionActive());
        assertTrue(subscription.getTimeLeft() <= 10
                && subscription.getTimeLeft() > 5);

        // A sends subscription-terminating NOTIFY to B, gets OK in response
        // **************************************************************
        // A side - send a NOTIFY
        Thread.sleep(20);
        notifyRequest = a.getDialog().createRequest(SipRequest.NOTIFY);
        notifyRequest = referHandler.addNotifyHeaders(notifyRequest, null,
                null, SubscriptionStateHeader.TERMINATED, "noresource",
                "SIP/2.0 100 Trying", 0);
        trans = referHandler.sendStatefulNotify(notifyRequest, false);
        assertNotNull(trans);

        // B side - wait for the NOTIFY
        reqevent = subscription.waitNotify(1000);
        assertNotNull(reqevent);

        // B side - examine the NOTIFY request object, verify subscription
        // message getters
        request = reqevent.getRequest();
        assertEquals(Request.NOTIFY, request.getMethod());
        assertTrue(((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires() < 1);
        received_requests = subscription.getAllReceivedRequests();
        assertEquals(3, received_requests.size());
        sipreq = subscription.getLastReceivedRequest();
        assertNotNull(sipreq);
        assertTrue(sipreq.isNotify());
        assertFalse(sipreq.isSubscribe());
        assertEquals(received_requests.get(2).getMessage().toString(), request
                .toString());
        assertEquals(received_requests.get(2).toString(), sipreq.toString());
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
        assertEquals(SipResponse.OK, ((ResponseEvent) obj).getResponse()
                .getStatusCode());

        // cleanup
        a.disposeNoBye();
        b.disposeNoBye();
    }

    public void testOutboundIndialogNotifyBeforeReferResponse()
            throws Exception
    {
        // A calls B, call established
        // A sends in-dialog REFER to B, A gets subscription-terminating NOTIFY
        // A gets 202 Accepted in response to the REFER A sent
        // B receives OK from A in response to the NOTIFY B sent

        // create and set up the far end
        SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");
        SipCall b = ub.createSipCall();
        assertTrue(b.listenForIncomingCall());

        // make the call from A
        SipCall a = ua.makeCall("sip:becky@cafesip.org", properties
                .getProperty("javax.sip.IP_ADDRESS")
                + ':' + myPort + '/' + testProtocol);
        assertLastOperationSuccess(ua.format(), ua);

        // B side answer the call
        assertTrue(b.waitForIncomingCall(1000));
        assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));
        Thread.sleep(20);
        assertTrue(b.sendIncomingCallResponse(Response.OK,
                "Answer - Hello world", 0));
        Thread.sleep(20);

        // A side finish call establishment
        assertAnswered("Outgoing call leg not answered", a);
        a.sendInviteOkAck();
        assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);
        Thread.sleep(1000);

        // B side - set up REFER handler
        ReferNotifySender referHandler = new ReferNotifySender(ub);
        referHandler.setDialog(b.getDialog());
        assertTrue(referHandler.processReferSendNotifyBeforeResponse(2000,
                SipResponse.ACCEPTED, "Accepted",
                SubscriptionStateHeader.TERMINATED, "noresource",
                "SIP/2.0 100 Trying", 0));

        // A side - send a REFER message
        SipURI referTo = ua
                .getUri(
                        "sip:",
                        "dave@denver.example.org",
                        "udp",
                        null,
                        null,
                        null,
                        "12345%40192.168.118.3%3Bto-tag%3D12345%3Bfrom-tag%3D5FFE-3994",
                        null, null);

        ReferSubscriber subscription = ua.refer(a.getDialog(), referTo,
                "eventbackward", 1000);
        if (subscription == null)
        {
            fail(ua.getReturnCode() + ':' + ua.getErrorMessage());
        }

        // B side - verify received REFER contents
        RequestEvent requestEvent = referHandler.getLastReceivedRequest()
                .getRequestEvent();
        assertNotNull(requestEvent);
        Request req = requestEvent.getRequest();
        assertEquals(SipRequest.REFER, req.getMethod());
        assertEquals(b.getDialogId(), requestEvent.getDialog().getDialogId());

        // A side - check the initial results - subscription, response,
        // SipPhone referer list
        assertEquals(SipResponse.ACCEPTED, subscription.getReturnCode());
        assertTrue(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionTerminated());
        Response resp = subscription.getCurrentResponse().getResponse();
        assertEquals(SipRequest.REFER, ((CSeqHeader) resp
                .getHeader(CSeqHeader.NAME)).getMethod());
        assertEquals("Accepted", resp.getReasonPhrase());
        assertNull(resp.getExpires());
        assertEquals(resp.toString(), subscription.getLastReceivedResponse()
                .getMessage().toString());
        ArrayList<SipResponse> received_responses = subscription
                .getAllReceivedResponses();
        assertEquals(1, received_responses.size());
        assertEquals(resp.toString(), received_responses.get(0).toString());
        assertEquals(1, ua.getRefererList().size());
        assertEquals(subscription, ua.getRefererList().get(0));
        assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(
                subscription.getDialogId()).get(0));
        assertEquals(subscription, ua.getRefererInfoByDialog(a.getDialogId())
                .get(0));

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
        assertTrue(((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires() < 1);
        ArrayList<SipRequest> received_requests = subscription
                .getAllReceivedRequests();
        assertEquals(1, received_requests.size());
        SipRequest sipreq = subscription.getLastReceivedRequest();
        assertNotNull(sipreq);
        assertTrue(sipreq.isNotify());
        assertFalse(sipreq.isSubscribe());
        assertEquals(received_requests.get(0).getMessage().toString(), request
                .toString());
        assertEquals(received_requests.get(0).toString(), sipreq.toString());
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
        Thread.sleep(500);
        SipResponse sipresp = referHandler.getLastReceivedResponse();
        assertNotNull(sipresp);
        CSeqHeader cseq = (CSeqHeader) sipresp.getMessage().getHeader(
                CSeqHeader.NAME);
        assertEquals(SipRequest.NOTIFY, cseq.getMethod());
        assertEquals(SipResponse.OK, sipresp.getResponseEvent().getResponse()
                .getStatusCode());

        // cleanup
        a.disposeNoBye();
        b.disposeNoBye();
    }

    public void testErrorNoResponse() throws Exception
    {
        SipURI referTo = ua.getUri("sip:", "dave@denver.example.org", "udp",
                "INVITE", null, null, null, null, null);

        ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org",
                referTo, null, 1000, null);
        assertNull(subscription);
    }

    public void testErrorResponseWithExpiry() throws Exception
    {
        SipURI referTo = ua.getUri("sip:", "dave@denver.example.org", "udp",
                "INVITE", null, null, null, null, null);

        ReferNotifySender ub = new ReferNotifySender(sipStack
                .createSipPhone("sip:becky@cafesip.org"));
        assertTrue(ub.processRefer(5000, SipResponse.OK, "OK", 60));
        Thread.sleep(50);

        // User A: send REFER out-of-dialog
        ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org",
                referTo, null, 5000, null);
        assertNotNull(subscription);
        assertEquals(SipResponse.OK, subscription.getReturnCode());

        // Process the response
        assertFalse(subscription.processResponse(1000));
        assertEquals(SipSession.FAR_END_ERROR, subscription.getReturnCode());
        assertTrue(subscription.getErrorMessage().indexOf(
                "expires header was received") != -1);
    }

    public void testExample() throws Exception
    {
        // create a refer-To URI
        SipURI referTo = ua.getUri("sip:", "dave@denver.example.org", "udp",
                "INVITE", null, null, null, null, null);

        // create & prepare the referee simulator (User B) to respond to REFER
        ReferNotifySender ub = new ReferNotifySender(sipStack
                .createSipPhone("sip:becky@cafesip.org"));
        assertTrue(ub.processRefer(5000, SipResponse.OK, "OK"));
        Thread.sleep(50);

        // User A: send REFER out-of-dialog
        ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org",
                referTo, null, 5000, null);
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
        Thread.sleep(50);
        String notifyBody = "SIP/2.0 200 OK";
        assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null,
                notifyBody, 2400, false));

        // User A: get the NOTIFY
        RequestEvent reqevent = subscription.waitNotify(500);
        assertNotNull(reqevent);
        // optionally examine NOTIFY in full detail using JAIN SIP:
        Request request = reqevent.getRequest();
        assertEquals(2400, ((SubscriptionStateHeader) request
                .getHeader(SubscriptionStateHeader.NAME)).getExpires());
        assertBodyContains(new SipRequest(reqevent), "200 OK");

        // process the NOTIFY
        Response resp = subscription.processNotify(reqevent);
        assertNotNull(resp);

        // check the NOTIFY processing results
        assertTrue(subscription.isSubscriptionActive());
        assertTrue(subscription.getTimeLeft() <= 2400
                && subscription.getTimeLeft() > 2300);
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
        assertTrue(ub.processSubscribe(5000, SipResponse.OK, "OK Done"));
        Thread.sleep(100);

        // send the un-SUBSCRIBE
        assertTrue(subscription.unsubscribe(100));
        if (!subscription.isRemovalComplete())
        {
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
            Thread.sleep(500);
            notifyBody = "SIP/2.0 100 Trying";
            assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED,
                    "Unsubscribed", notifyBody, 0, false));
            Thread.sleep(10);

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
