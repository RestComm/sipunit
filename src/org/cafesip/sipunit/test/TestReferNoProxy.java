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
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.cafesip.sipunit.ReferNotifySender;
import org.cafesip.sipunit.ReferSubscriber;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
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

    private String host;

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
        host = properties.getProperty("javax.sip.IP_ADDRESS");

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

    public void testOutboundOutdialogBasic() throws Exception // TODO
                                                              // unsubscribe
                                                              // part
    {
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

        // send REFER
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

        // process the received response
        assertTrue(subscription.processResponse(1000));

        // check the response processing results
        assertTrue(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionTerminated());
        assertNull(subscription.getTerminationReason());
        assertEquals(0, subscription.getTimeLeft());

        // tell far end to send a NOTIFY
        Thread.sleep(500);
        String notify_body = "CSeq: 1 INVITE";
        assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null,
                notify_body, 2400, false));
        Thread.sleep(10);

        // get the NOTIFY
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

        // process the NOTIFY
        resp = subscription.processNotify(reqevent);
        assertNotNull(resp);

        // check the processing results
        assertTrue(subscription.isSubscriptionActive());
        assertFalse(subscription.isSubscriptionPending());
        assertFalse(subscription.isSubscriptionTerminated());
        assertNull(subscription.getTerminationReason());
        assertTrue(subscription.getTimeLeft() <= 2400);
        assertEquals(SipResponse.OK, subscription.getReturnCode());

        // check the response that was created
        assertEquals(SipResponse.OK, resp.getStatusCode());
        assertTrue(resp.getReasonPhrase().equals("OK"));

        // reply to the NOTIFY
        assertTrue(subscription.replyToNotify(reqevent, resp));

        // terminate the subscription from the referrer side

        // prepare the far end
        assertTrue(ub.processSubscribe(5000, SipResponse.OK, "OK"));
        Thread.sleep(500);

        // send the un-SUBSCRIBE
        assertTrue(subscription.unsubscribe(100));
        assertFalse(subscription.isRemovalComplete());

        // check everything

        // process the received response

        // check the response processing results

        // tell far end to send a NOTIFY

        // receive the NOTIFY

        // examine the request object

        // process the NOTIFY
        // resp = subscription.processNotify(reqevent);
        // assertNotNull(resp);

        // check the processing results

        // check the response that was created

        // reply to the NOTIFY

    }

    public void testOutboundIndialogBasic() throws Exception // this test still
    // in progress
    {
        // A calls B, call established
        // A sends in-dialog REFER to B (TODO, test B sending REFER)
        try
        {
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
            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));
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
            assertTrue(referHandler.processRefer(4000, SipResponse.OK, "OK"));

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
                    "myeventid", 4000);
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
            assertEquals(b.getDialogId(), requestEvent.getDialog()
                    .getDialogId());

            // A side - check the initial results - subscription, response,
            // SipPhone referer list
            assertEquals(SipResponse.OK, subscription.getReturnCode());
            assertTrue(subscription.isSubscriptionPending());
            assertFalse(subscription.isSubscriptionActive());
            assertFalse(subscription.isSubscriptionTerminated());
            Response resp = subscription.getCurrentResponse().getResponse();
            assertEquals(SipRequest.REFER, ((CSeqHeader) resp
                    .getHeader(CSeqHeader.NAME)).getMethod());
            assertEquals("OK", resp.getReasonPhrase());
            assertNull(resp.getExpires());
            assertEquals(resp.toString(), subscription
                    .getLastReceivedResponse().getMessage().toString());
            ArrayList<SipResponse> received_responses = subscription
                    .getAllReceivedResponses();
            assertEquals(1, received_responses.size());
            assertEquals(resp.toString(), received_responses.get(0).toString());
            assertEquals(1, ua.getRefererList().size());
            assertEquals(subscription, ua.getRefererList().get(0));
            assertEquals(subscription, ua.getRefererInfo(referTo).get(0));
            assertEquals(subscription, ua.getRefererInfoByDialog(
                    subscription.getDialogId()).get(0));
            assertEquals(subscription, ua.getRefererInfoByDialog(
                    a.getDialogId()).get(0));

            // A side - process the received response
            assertTrue(subscription.processResponse(1000));

            // A side - check the response processing results
            assertTrue(subscription.isSubscriptionActive());
            assertFalse(subscription.isSubscriptionPending());
            assertFalse(subscription.isSubscriptionTerminated());
            assertNull(subscription.getTerminationReason());
            assertEquals(0, subscription.getTimeLeft());

            // B side - send a NOTIFY
            Thread.sleep(20); // TODO test NOTIFY reception before REFER
            // response
            Request notifyRequest = b.getDialog().createRequest(
                    SipRequest.NOTIFY);
            String notify_body = "CSeq: 1 INVITE";
            notifyRequest = referHandler.addNotifyHeaders(notifyRequest, null,
                    null, SubscriptionStateHeader.ACTIVE, null, notify_body,
                    2400);
            SipTransaction trans = referHandler.sendStatefulNotify(
                    notifyRequest, false);
            assertNotNull(trans);

            // A side - wait for the NOTIFY
            RequestEvent reqevent = subscription.waitNotify(1000);
            assertNotNull(reqevent);

            // A side - examine the NOTIFY request object, verify subscription
            // getters
            Request request = reqevent.getRequest();
            assertEquals(Request.NOTIFY, request.getMethod());
            assertEquals(2400, ((SubscriptionStateHeader) request
                    .getHeader(SubscriptionStateHeader.NAME)).getExpires());
            ArrayList<SipRequest> received_requests = subscription
                    .getAllReceivedRequests();
            assertEquals(1, received_requests.size());
            SipRequest sipreq = subscription.getLastReceivedRequest();
            assertNotNull(sipreq);
            assertTrue(sipreq.isNotify());
            assertFalse(sipreq.isSubscribe());
            assertEquals(((SipRequest) received_requests.get(0)).getMessage()
                    .toString(), request.toString());
            assertEquals(received_requests.get(0).toString(), sipreq.toString());

            // A side - process the NOTIFY
            resp = subscription.processNotify(reqevent);
            assertNotNull(resp);

            // A side - check the NOTIFY processing results
            assertTrue(subscription.isSubscriptionActive());
            assertFalse(subscription.isSubscriptionPending());
            assertFalse(subscription.isSubscriptionTerminated());
            assertNull(subscription.getTerminationReason());
            assertTrue(subscription.getTimeLeft() <= 2400);
            assertEquals(SipResponse.OK, subscription.getReturnCode());

            // A side - check the NOTIFY response that was created
            assertEquals(SipResponse.OK, resp.getStatusCode());
            assertTrue(resp.getReasonPhrase().equals("OK"));

            // A side - reply to the NOTIFY
            assertTrue(subscription.replyToNotify(reqevent, resp));

            // B side - get the NOTIFY response
            Object obj = referHandler.waitResponse(trans, 10000);
            assertNotNull(obj);
            assertTrue(obj instanceof ResponseEvent);

            // terminate the subscription from the referrer side
            // (TODO retest with subscription termination from referee side)

            // prepare the far end
            // assertTrue(ub.processSubscribe(5000, SipResponse.OK, "OK"));
            // Thread.sleep(500);

            // send un-SUBSCRIBE

            // check everything, ETC

            // finish up
            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

}
