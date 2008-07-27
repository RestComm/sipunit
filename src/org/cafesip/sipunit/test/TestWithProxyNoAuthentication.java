/*
 * Created on Mar 29, 2005
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

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTestCase;
import org.cafesip.sipunit.SipTransaction;

/**
 * This class tests SipUnit API methods.
 * 
 * Tests in this class require that a Proxy/registrar server be running with
 * authentication turned off. Defaults: proxy host = 192.168.1.102, port = 5060,
 * protocol = udp; user amit@cafesip.org password a1b2c3d4 and user
 * becky@cafesip.org password a1b2c3d4 defined at the proxy.
 * 
 * For the Proxy/registrar, I used cafesip.org's SipExchange server.
 * 
 * @author Becky McElroy
 * 
 */
public class TestWithProxyNoAuthentication extends SipTestCase
{
    private SipStack sipStack;

    private SipPhone ua;

    private String thisHostAddr;

    private static String PROXY_HOST = "192.168.1.102";

    private static int PROXY_PORT = 5060;

    private static String PROXY_PROTO = "udp";

    public TestWithProxyNoAuthentication(String arg0)
    {
        super(arg0);
    }

    /*
     * @see SipTestCase#setUp()
     */
    public void setUp() throws Exception
    {
        try
        {
            sipStack = new SipStack(null, 5061);
            thisHostAddr = InetAddress.getLocalHost().getHostAddress();
        }
        catch (Exception ex)
        {
            fail("Exception: " + ex.getClass().getName() + ": "
                    + ex.getMessage());
            throw ex;
        }

        try
        {
            ua = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT,
                    "sip:amit@cafesip.org");
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

    /**
     * This test illustrates usage of SipTestCase.
     */
    public void testBothSidesCallerDisc()
    {
        // invoke the Sip operation, then separately check positive result;
        // include all error details in output (via ua.format()) if the test
        // fails:

        ua.register(null, 1800);
        assertLastOperationSuccess("Caller registration failed - "
                + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            // invoke the Sip operation, then separately check positive result;
            // no failure/error details, just the standard JUnit fail output:

            ub.register(null, 600); // ub.register("sip:becky@" + thisHostAddr,
            // 600);
            assertLastOperationSuccess(ub);

            SipCall a = ua.createSipCall();
            SipCall b = ub.createSipCall();

            b.listenForIncomingCall();
            Thread.sleep(10);

            // another way to invoke the operation and check the result
            // separately:

            boolean status_ok = a.initiateOutgoingCall("sip:becky@cafesip.org",
                    null);
            assertTrue("Initiate outgoing call failed - " + a.format(),
                    status_ok);

            // invoke the Sip operation and check positive result in one step,
            // no operation error details if the test fails:

            assertTrue("Wait incoming call error or timeout - " + b.format(), b
                    .waitForIncomingCall(5000));

            // invoke the Sip operation and result check in one step,
            // only standard JUnit output if the test fails:

            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));

            Thread.sleep(1000);

            // although the 2-step method is not as compact, it's easier
            // to follow what a test is doing since the Sip operations are not
            // buried as parameters in assert statements:

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            assertLastOperationSuccess("Sending answer response failed - "
                    + b.format(), b);

            // note with the single step method, you cannot include operation
            // error details for when the test fails: ' + a.format()' wouldn't
            // work in the first parameter here:

            assertTrue("Wait response error", a.waitOutgoingCallResponse(10000));

            SipResponse resp = a.getLastReceivedResponse(); // watch for TRYING
            int status_code = resp.getStatusCode();
            while (status_code != Response.RINGING)
            {
                assertFalse("Unexpected final response, status = "
                        + status_code, status_code > 200);

                assertFalse("Got OK but no RINGING", status_code == Response.OK);

                a.waitOutgoingCallResponse(10000);
                assertLastOperationSuccess(
                        "Subsequent response never received - " + a.format(), a);
                resp = a.getLastReceivedResponse();
                status_code = resp.getStatusCode();
            }

            // if you want operation error details in your test fail output,
            // you have to invoke and complete the operation first:

            a.waitOutgoingCallResponse(10000); // get next response
            assertLastOperationSuccess("Wait response error - " + a.format(), a);

            // throw out any 'TRYING' responses
            // Note, you can also get the response status code from the SipCall
            // class itself (in addition to getting it from the response as
            // above)
            while (a.getReturnCode() == Response.TRYING)
            {
                a.waitOutgoingCallResponse(10000);
                assertLastOperationSuccess(
                        "Subsequent response never received - " + a.format(), a);
            }
            resp = a.getLastReceivedResponse();

            // check for OK response.
            assertEquals("Unexpected response received", Response.OK, a
                    .getReturnCode());

            // check out some header asserts
            assertHeaderContains(resp, "From", "sip:amit@cafesip.org");
            assertHeaderNotContains(resp, "From", "sip:ammit@cafesip.org");
            assertHeaderPresent(resp, "CSeq");
            assertHeaderNotPresent(resp, "Content-Type");

            // continue with the test call
            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(1000);

            b.listenForDisconnect();
            assertLastOperationSuccess("b listen disc - " + b.format(), b);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(5000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);

            // TODO investigate - null pointer from stack or my msg is bad
            // b.respondToDisconnect();
            // assertLastOperationSuccess("b disc - " + b.format(), b);

            ub.unregister(null, 10000);
            assertLastOperationSuccess("unregistering user b - " + ub.format(),
                    ub);
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testBothSidesCalleeDisc()
    {
        ua.register(null, 1800);
        assertLastOperationSuccess("a registration - " + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            ub.register(null, 600);
            assertLastOperationSuccess("b registration - " + ub.format(), ub);

            SipCall a = ua.createSipCall();
            SipCall b = ub.createSipCall();

            b.listenForIncomingCall();
            Thread.sleep(10);

            a.initiateOutgoingCall("sip:becky@cafesip.org", null);
            assertLastOperationSuccess("a initiate call - " + a.format(), a);

            b.waitForIncomingCall(10000);
            assertLastOperationSuccess("b wait incoming call - " + b.format(),
                    b);

            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
            assertLastOperationSuccess("b send RINGING - " + b.format(), b);

            Thread.sleep(1000);

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            assertLastOperationSuccess("b send OK - " + b.format(), b);

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);

            while (a.getReturnCode() == Response.TRYING)
            {
                a.waitOutgoingCallResponse(10000);
                assertLastOperationSuccess(
                        "Subsequent response never received - " + a.format(), a);
            }

            assertEquals("Unexpected 1st response received", Response.RINGING,
                    a.getReturnCode());

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 2nd response - " + a.format(), a);

            while (a.getReturnCode() == Response.TRYING)
            {
                a.waitOutgoingCallResponse(10000);
                assertLastOperationSuccess(
                        "Subsequent response never received - " + a.format(), a);
            }

            assertEquals("Unexpected 2nd response received", Response.OK, a
                    .getReturnCode());

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(1000);

            a.listenForDisconnect();
            assertLastOperationSuccess("a listen disc - " + a.format(), a);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            // TODO - investigate - null pointer from stack or bad msg?
            // a.respondToDisconnect();
            // assertLastOperationSuccess("a respond to disc - " + a.format(),
            // a);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testBasicRegistration() // no authentication
    {
        ua.register("sip:amit@209.42.0.1", 695);
        assertLastOperationSuccess("user a registration - " + ua.format(), ua);

        assertEquals("check contact expiry", 695, ua.getContactInfo()
                .getExpiry());
        assertEquals("check contact URI", "sip:amit@209.42.0.1", ua
                .getContactInfo().getURI());

        // wait 2 sec then unregister
        try
        {
            Thread.sleep(2000);
        }
        catch (Exception ex)
        {
        }

        ua.unregister("sip:amit@209.42.0.1", 10000);
        assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
    }

    public void testSingleReply()
    {
        // test: sendRequestWithTrans(String invite, viaProxy),
        // sendReply(Response OK, toTag, contact),
        // waitResponse()

        SipStack.setTraceEnabled(true);

        ua.register("amit", "a1b2c3d4", null, 0, 10000);
        assertLastOperationSuccess(ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            ub.register("becky", "a1b2c3d4", null, 0, 10000);
            assertLastOperationSuccess(ub.format(), ub);

            ub.listenRequestMessage();
            Thread.sleep(100);

            StringBuffer invite = new StringBuffer("INVITE sip:becky@"
                    + PROXY_HOST + ':' + PROXY_PORT + ";transport="
                    + PROXY_PROTO + " SIP/2.0\n");
            invite.append("Call-ID: " + System.currentTimeMillis() + "@"
                    + thisHostAddr + "\n");
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

            SipTransaction trans = ua.sendRequestWithTransaction(invite
                    .toString(), true, null);
            assertNotNull(ua.format(), trans);
            // call sent

            RequestEvent inc_req = ub.waitRequest(10000);
            assertNotNull(ub.format(), inc_req);
            // call received

            assertHeaderContains(new SipRequest(inc_req.getRequest()),
                    EventHeader.NAME, "presence");
            assertBodyContains(new SipRequest(inc_req.getRequest()), "12345");

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@" + thisHostAddr + ":5061");
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);

            String to_tag = ub.generateNewTag();

            SipTransaction transb = ub.sendReply(inc_req, Response.OK, null,
                    to_tag, contact, -1);
            assertNotNull(ub.format(), transb);
            // answer response sent

            Thread.sleep(1000);

            EventObject response_event;
            int status;
            do
            {
                response_event = ua.waitResponse(trans, 10000);
                assertNotNull(ua.format(), response_event);
                assertFalse(ua.format(), response_event instanceof TimeoutEvent);
                // got a response

                status = ((ResponseEvent) response_event).getResponse()
                        .getStatusCode();
            }
            while (status == Response.TRYING);

            assertEquals(ua.format(), Response.OK, status);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return;
    }

    public void testMultipleReplies()
    {
        // test: sendRequestWithTrans(Request invite),
        // sendReply(Response Trying, no toTag, no contact),
        // sendReply(statusCode Ringing, toTag, contact, ...),
        // sendReply(Response OK, no toTag, contact, ...),
        // waitResponse()

        ua.register("amit", "a1b2c3d4", null, 0, 10000);
        assertLastOperationSuccess(ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            ub.register("becky", "a1b2c3d4", null, 0, 10000);
            assertLastOperationSuccess(ub.format(), ub);

            ub.listenRequestMessage();
            Thread.sleep(100);

            AddressFactory addr_factory = ua.getParent().getAddressFactory();
            HeaderFactory hdr_factory = ua.getParent().getHeaderFactory();

            Request invite = ua.getParent().getMessageFactory().createRequest(
                    "INVITE sip:becky@" + PROXY_HOST + ':' + PROXY_PORT
                            + ";transport=" + PROXY_PROTO + " SIP/2.0\n");

            invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
            invite.addHeader(hdr_factory.createCSeqHeader((long) 1,
                    Request.INVITE));
            invite.addHeader(hdr_factory.createFromHeader(ua.getAddress(), ua
                    .generateNewTag()));

            Address to_address = addr_factory.createAddress(addr_factory
                    .createURI("sip:becky@cafesip.org"));
            invite.addHeader(hdr_factory.createToHeader(to_address, null));

            Address contact_address = addr_factory.createAddress("sip:amit@"
                    + thisHostAddr + ":5061");
            invite.addHeader(hdr_factory.createContactHeader(contact_address));

            invite.addHeader(hdr_factory.createMaxForwardsHeader(5));
            ArrayList via_headers = ua.getViaHeaders();
            invite.addHeader((ViaHeader) via_headers.get(0));

            SipTransaction trans = ua.sendRequestWithTransaction(invite, false,
                    null);
            assertNotNull(ua.format(), trans);
            // call sent

            RequestEvent inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            Response response = ub.getParent().getMessageFactory()
                    .createResponse(Response.TRYING, inc_req.getRequest());
            SipTransaction transb = ub.sendReply(inc_req, response);
            assertNotNull(ub.format(), transb);
            // trying response sent

            Thread.sleep(500);

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@" + thisHostAddr + ":5061");
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);

            String to_tag = ub.generateNewTag();

            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1);
            assertLastOperationSuccess(ub.format(), ub);
            // ringing response sent

            Thread.sleep(500);

            response = ub.getParent().getMessageFactory().createResponse(
                    Response.OK, inc_req.getRequest());
            response.addHeader(ub.getParent().getHeaderFactory()
                    .createContactHeader(contact));

            ub.sendReply(transb, response);
            assertLastOperationSuccess(ub.format(), ub);
            // answer response sent

            Thread.sleep(500);

            EventObject response_event = ua.waitResponse(trans, 10000);
            // wait for trying

            assertNotNull(ua.format(), response_event);
            assertFalse("Operation timed out",
                    response_event instanceof TimeoutEvent);

            assertEquals("Should have received TRYING", Response.TRYING,
                    ((ResponseEvent) response_event).getResponse()
                            .getStatusCode());
            // trying received

            response_event = ua.waitResponse(trans, 10000);
            // wait for ringing

            assertNotNull(ua.format(), response_event);
            assertFalse("Operation timed out",
                    response_event instanceof TimeoutEvent);

            assertEquals("Should have received RINGING", Response.RINGING,
                    ((ResponseEvent) response_event).getResponse()
                            .getStatusCode());
            // ringing received

            response_event = ua.waitResponse(trans, 10000);
            // wait for answer

            assertNotNull(ua.format(), response_event);
            assertFalse("Operation timed out",
                    response_event instanceof TimeoutEvent);

            assertEquals("Should have received OK", Response.OK,
                    ((ResponseEvent) response_event).getResponse()
                            .getStatusCode());
            // answer received

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return;
    }

    public void testStatelessRequestStatefulResponse()
    {
        // test: sendUnidirectionalReq(String invite, not viaProxy),
        // sendReply(statusCode Ringing, no toTag, no contact),
        // sendSubsequentReply(statusCode OK, toTag, contact),
        // sendUnidirectionalReq(Request ack),
        // ack received

        // SipStack.setTraceEnabled(true);
        // SipStack.trace("testStatelessRequestStatefulResponse");
        ua.register("amit", "a1b2c3d4", null, 0, 10000);
        assertLastOperationSuccess(ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            ub.register("becky", "a1b2c3d4", null, 0, 10000);
            assertLastOperationSuccess(ub.format(), ub);

            ub.listenRequestMessage();
            Thread.sleep(100);

            StringBuffer invite = new StringBuffer("INVITE sip:becky@"
                    + PROXY_HOST + ':' + PROXY_PORT + ";transport="
                    + PROXY_PROTO + " SIP/2.0\n");
            String myuniquecallID = String.valueOf(System.currentTimeMillis());
            invite.append("Call-ID: " + myuniquecallID + "@" + thisHostAddr
                    + "\n");
            invite.append("CSeq: 1 INVITE\n");
            invite.append("From: <sip:amit@cafesip.org>;tag=1181356482\n");
            invite.append("To: <sip:becky@cafesip.org>\n");
            invite.append("Contact: <sip:amit@" + thisHostAddr + ":5061>\n");
            invite.append("Max-Forwards: 5\n");
            invite.append("Via: SIP/2.0/" + PROXY_PROTO + " " + thisHostAddr
                    + ":5061;branch=322e3136382e312e3130303a3530363\n");
            invite.append("Content-Length: 0\n");
            invite.append("\n");

            /*
             * template for invite request object:
             * 
             * AddressFactory addr_factory = ua.getParent().getAddressFactory();
             * HeaderFactory hdr_factory = ua.getParent().getHeaderFactory();
             * 
             * Request invite =
             * ua.getParent().getMessageFactory().createRequest( "INVITE
             * sip:becky@cafesip.org;transport=" + PROXY_PROTO + " SIP/2.0 ");
             * 
             * invite
             * .addHeader(ua.getParent().getSipProvider().getNewCallId());
             * invite.addHeader(hdr_factory.createCSeqHeader(1,
             * Request.INVITE));
             * invite.addHeader(hdr_factory.createFromHeader(ua.getAddress(), ua
             * .generateNewTag()));
             * 
             * Address to_address = addr_factory.createAddress(addr_factory
             * .createURI("sip:becky@cafesip.org"));
             * invite.addHeader(hdr_factory.createToHeader(to_address, null));
             * 
             * Address contact_address = addr_factory.createAddress("sip:amit@" +
             * thisHostAddr + ":5061");
             * invite.addHeader(hdr_factory.createContactHeader(contact_address));
             * 
             * invite.addHeader(hdr_factory.createMaxForwardsHeader(5));
             * ArrayList via_headers = ua.getMyViaHeaders();
             * invite.addHeader((ViaHeader) via_headers.get(0));
             * 
             */

            ua.sendUnidirectionalRequest(invite.toString(), false);
            assertLastOperationSuccess(ua.format(), ua);
            // call sent

            RequestEvent inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            assertHeaderContains(new SipRequest(inc_req.getRequest()),
                    CallIdHeader.NAME, myuniquecallID);

            SipTransaction trans = ub.sendReply(inc_req, Response.RINGING,
                    null, null, null, -1);
            assertNotNull(ub.format(), trans);
            // first reply sent

            Thread.sleep(1000);

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@" + thisHostAddr + ":5061");
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);

            String to_tag = ub.generateNewTag();
            ub.sendReply(trans, Response.OK, null, to_tag, contact, 0);
            assertLastOperationSuccess(ub.format(), ub);
            // OK response sent

            Thread.sleep(1000);
            ub.listenRequestMessage();

            // build the ack to send; pass a Request object to
            // sendUnidirectionalRequest(()
            // this time; use headers from the invite request to create the ack
            // request
            Request ack = ua.getParent().getMessageFactory().createRequest(
                    invite.toString());

            ack.setMethod(Request.ACK);
            ack.setHeader(ua.getParent().getHeaderFactory().createCSeqHeader(
                    (long) 1, Request.ACK));
            ((ToHeader) ack.getHeader(ToHeader.NAME)).setTag(to_tag);
            ack.setRequestURI(callee_contact);
            ack.setHeader(ua.getParent().getHeaderFactory().createRouteHeader(
                    contact));

            ua.sendUnidirectionalRequest(ack, false);
            assertLastOperationSuccess(ua.format(), ua);
            // ack sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            assertEquals("Received request other than ACK", Request.ACK,
                    inc_req.getRequest().getMethod());
            // ack received

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return;
    }

    public void testSendInviteNoProxy()
    {
        try
        {
            ua.dispose(); // re-create ua with no proxy
            ua = sipStack.createSipPhone("sip:amit@cafesip.org");

            SipPhone ub = sipStack.createSipPhone("sip:becky@cafesip.org");

            ub.listenRequestMessage();
            Thread.sleep(100);

            AddressFactory addr_factory = ua.getParent().getAddressFactory();
            HeaderFactory hdr_factory = ua.getParent().getHeaderFactory();

            Request invite = ua.getParent().getMessageFactory().createRequest(
                    "INVITE sip:becky@cafesip.org SIP/2.0\n");

            invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
            invite.addHeader(hdr_factory.createCSeqHeader((long) 1,
                    Request.INVITE));
            invite.addHeader(hdr_factory.createFromHeader(ua.getAddress(), ua
                    .generateNewTag()));

            Address to_address = addr_factory.createAddress(addr_factory
                    .createURI("sip:becky@cafesip.org"));
            invite.addHeader(hdr_factory.createToHeader(to_address, null));

            Address contact_address = addr_factory.createAddress("sip:amit@"
                    + thisHostAddr + ":5061");
            invite.addHeader(hdr_factory.createContactHeader(contact_address));

            invite.addHeader(hdr_factory.createMaxForwardsHeader(5));
            ArrayList via_headers = ua.getViaHeaders();
            invite.addHeader((ViaHeader) via_headers.get(0));

            Address route_address = addr_factory.createAddress("sip:becky@"
                    + thisHostAddr + ":5061");
            invite.addHeader(hdr_factory.createRouteHeader(route_address));

            SipTransaction trans = ua.sendRequestWithTransaction(invite, false,
                    null);
            assertNotNull(ua.format(), trans);
            // call sent

            RequestEvent inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            Response response = ub.getParent().getMessageFactory()
                    .createResponse(Response.TRYING, inc_req.getRequest());
            SipTransaction transb = ub.sendReply(inc_req, response);
            assertNotNull(ub.format(), transb);
            // trying response sent

            Thread.sleep(500);

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@" + thisHostAddr + ":5061");
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);

            String to_tag = ub.generateNewTag();

            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1);
            assertLastOperationSuccess(ub.format(), ub);
            // ringing response sent

            Thread.sleep(500);

            response = ub.getParent().getMessageFactory().createResponse(
                    Response.OK, inc_req.getRequest());
            response.addHeader(ub.getParent().getHeaderFactory()
                    .createContactHeader(contact));

            ub.sendReply(transb, response);
            assertLastOperationSuccess(ub.format(), ub);
            // answer response sent

            Thread.sleep(500);

            EventObject response_event = ua.waitResponse(trans, 10000);
            // wait for trying

            assertNotNull(ua.format(), response_event);
            assertFalse("Operation timed out",
                    response_event instanceof TimeoutEvent);

            assertEquals("Should have received TRYING", Response.TRYING,
                    ((ResponseEvent) response_event).getResponse()
                            .getStatusCode());
            // response(s) received, we're done

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

    }

    public void testMakeCall()
    {
        ua.register("amit", "a1b2c3d4", null, 0, 10000);
        assertLastOperationSuccess(ua.format(), ua);

        try
        {
            // use a called party not registered, verify expected response
            ua.makeCall("sip:doodah@cafesip.org",
                    SipResponse.TEMPORARILY_UNAVAILABLE, 10000, null);
            assertLastOperationSuccess(ua.format(), ua);

            // do it again, look for what we know won't happen
            ua.makeCall("sip:doodah@cafesip.org", SipResponse.OK, 10000, null);
            assertLastOperationFail("Unexpected success, call completed", ua);
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

    }

    /**
     * Test: asynchronous SipPhone.makeCall(), callee disc
     */
    public void testBothSidesAsynchMakeCall()
    {
        ua.register("amit", "a1b2c3d4", null, 0, 10000);
        assertLastOperationSuccess(ua.format(), ua);
        SipStack.setTraceEnabled(true);

        try
        {
            SipPhone ub = sipStack.createSipPhone(PROXY_HOST, PROXY_PROTO,
                    PROXY_PORT, "sip:becky@cafesip.org");

            assertTrue(ub.register("becky", "a1b2c3d4", null, 600, 5000));
            SipCall b = ub.createSipCall();
            assertTrue(b.listenForIncomingCall());
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@cafesip.org", null);
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));
            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));
            Thread.sleep(500);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            Thread.sleep(500);

            assertAnswered("Outgoing call leg not answered", a);
            assertAnswered(b);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() >= 2);
            assertTrue(
                    "Shouldn't have received anything at the called party side",
                    b.getAllReceivedResponses().size() == 0);

            // verify RINGING was received
            assertResponseReceived("Should have gotten RINGING response",
                    SipResponse.RINGING, a);
            // verify OK was received
            assertResponseReceived(SipResponse.OK, a);
            // check negative
            assertResponseNotReceived("Unexpected response",
                    SipResponse.NOT_FOUND, a);
            assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, a);

            // verify getLastReceivedResponse() method
            assertEquals("Last response received wasn't answer",
                    SipResponse.OK, a.getLastReceivedResponse().getStatusCode());

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);
            a.listenForDisconnect();
            Thread.sleep(1000);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);
            a.respondToDisconnect();

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    // testStatelessRequestStatelessResponse() TODO
    // testStatefulRequestStatelessResponse() TODO

    /*
     * public void xtestCalltoSipPhone() throws Exception { // First register a
     * Sip phone (becky@cafesip.org) to the proxy before // executing this test.
     * This test doesn't work as is - the Sip phone // doesn't do anything with
     * the INVITE
     * 
     * ua.register("amit", "a1b2c3d4", null, 0);
     * assertLastOperationSuccess(ua.format(), ua);
     * 
     * SipCall call = ua.createSipCall();
     * 
     * call.initiateOutgoingCall("sip:becky@cafesip.org", true);
     * assertLastOperationSuccess(call.format(), call);
     * 
     * call.waitOutgoingCallResponse(10000);
     * assertLastOperationSuccess(call.format(), call);
     * 
     * int status_code = call.getReturnCode(); while (status_code !=
     * Response.OK) { if (status_code / 100 == 1) { // provisional
     * 
     * call.waitOutgoingCallResponse(10000);
     * assertLastOperationSuccess(call.format(), call);
     * 
     * status_code = call.getReturnCode(); continue; } else if ((status_code ==
     * Response.UNAUTHORIZED) || (status_code ==
     * Response.PROXY_AUTHENTICATION_REQUIRED)) { // auth required // use a
     * common method on parent to handle // this for outgoing calls,
     * registration, etc. // see registration() for handling of this case
     * 
     * fail("Need auth handling"); } else { fail("Unknown/unexpected response
     * code - " + call.format()); } }
     * 
     * return; }
     */
}