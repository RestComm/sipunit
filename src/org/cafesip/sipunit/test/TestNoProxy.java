/*
 * Created on April 21, 2005
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
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.PriorityHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipMessage;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTestCase;
import org.cafesip.sipunit.SipTransaction;

/**
 * This class tests SipUnit API methods.
 * 
 * Tests in this class do not require a proxy/registrar server. Messaging
 * between UACs is direct.
 * 
 * @author Becky McElroy
 * 
 */
public class TestNoProxy extends SipTestCase
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
        defaultProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
        defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
        defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testAgent_debug.txt");
        defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testAgent_log.txt");
        defaultProperties
                .setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        defaultProperties.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

        defaultProperties.setProperty("sipunit.trace", "true");
        defaultProperties.setProperty("sipunit.test.port", "5060");
        defaultProperties.setProperty("sipunit.test.protocol", "udp");
    }

    private Properties properties = new Properties(defaultProperties);

    public TestNoProxy(String arg0)
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
            myPort = 5060;
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

    public void testSendInviteWithRouteHeader() // add a Route Header to the
    // INVITE myself
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            ub.listenRequestMessage();
            Thread.sleep(100);

            AddressFactory addr_factory = ua.getParent().getAddressFactory();
            HeaderFactory hdr_factory = ua.getParent().getHeaderFactory();

            Request invite = ua.getParent().getMessageFactory().createRequest(
                    "INVITE sip:becky@nist.gov SIP/2.0 ");

            invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
            invite.addHeader(hdr_factory.createCSeqHeader((long)1, Request.INVITE));
            invite.addHeader(hdr_factory.createFromHeader(ua.getAddress(), ua
                    .generateNewTag()));

            Address to_address = addr_factory.createAddress(addr_factory
                    .createURI("sip:becky@nist.gov"));
            invite.addHeader(hdr_factory.createToHeader(to_address, null));

            Address contact_address = addr_factory.createAddress("sip:amit@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort);
            invite.addHeader(hdr_factory.createContactHeader(contact_address));

            invite.addHeader(hdr_factory.createMaxForwardsHeader(5));
            ArrayList via_headers = ua.getViaHeaders();
            invite.addHeader((ViaHeader) via_headers.get(0));

            // create and add the Route Header
            Address route_address = addr_factory.createAddress("sip:becky@"
                    + ub.getStackAddress() + ':'
                    + myPort + '/' + testProtocol);
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
                    "sip:becky@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
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
    
    public void testBothSides() // test initiateOugoingCall(), passing routing
    // string
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            SipCall a = ua.createSipCall();
            SipCall b = ub.createSipCall();

            b.listenForIncomingCall();
            Thread.sleep(10);

            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess("a initiate call - " + a.format(), a);

            b.waitForIncomingCall(10000);
            assertLastOperationSuccess("b wait incoming call - " + b.format(),
                    b);

            b.sendIncomingCallResponse(Response.RINGING, null, -1);
            assertLastOperationSuccess("b send RINGING - " + b.format(), b);

            Thread.sleep(1000);

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            assertLastOperationSuccess("b send OK - " + b.format(), b);

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
            assertEquals("Unexpected 1st response received", Response.RINGING,
                    a.getReturnCode());
            assertNotNull("Default response reason not sent", a
                    .getLastReceivedResponse().getReasonPhrase());
            assertEquals("Unexpected default reason", "Ringing", a
                    .getLastReceivedResponse().getReasonPhrase());

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 2nd response - " + a.format(), a);

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

            a.respondToDisconnect();
            assertLastOperationSuccess("a respond to disc - " + a.format(), a);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testSipTestCaseMisc()
    // in this test, user a is handled at the SipCall level and user b at the
    // SipSession level (we send a body in the response)
    {
        try
        {
            SipCall a = ua.createSipCall();
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            ub.listenRequestMessage();
            Thread.sleep(50);

            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess("a initiate call - " + a.format(), a);

            RequestEvent inc_req = ub.waitRequest(10000);
            assertNotNull(ub.format(), inc_req);
            // call received

            SipRequest req = new SipRequest(inc_req.getRequest());

            /*******************************************************************
             * Incoming request - header/body asserts
             ******************************************************************/

            assertHeaderPresent(req, "Max-Forwards");
            assertHeaderNotPresent(req, "Max-Forwardss");
            assertHeaderNotContains(req, "Max-Forwards", "71");
            assertHeaderContains(req, "Max-Forwards", "70");
            assertBodyNotPresent(req);
            assertBodyNotContains(req, "e");

            /** ************************************************************* */

            // send TRYING
            Response response = ub.getParent().getMessageFactory()
                    .createResponse(Response.TRYING, inc_req.getRequest());
            SipTransaction transb = ub.sendReply(inc_req, response);
            assertNotNull(ub.format(), transb);
            // trying response sent

            Thread.sleep(100);

            // send message with a body
            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);
            String to_tag = ub.generateNewTag();
            response = ub.getParent().getMessageFactory().createResponse(
                    Response.RINGING, inc_req.getRequest()); // why OK
            // doesn't
            // work here?
            response.setReasonPhrase("Hello World");
            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(to_tag);
            response.addHeader(ub.getParent().getHeaderFactory()
                    .createContactHeader(contact));
            ContentTypeHeader ct = ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("application", "sdp");
            response.setContent("This is a test body", ct);

            ub.sendReply(transb, response);
            assertLastOperationSuccess(ub.format(), ub);
            // message with body sent

            Thread.sleep(100);

            a.waitOutgoingCallResponse(4000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
            while (a.getReturnCode() == Response.TRYING)
            {
                a.waitOutgoingCallResponse(4000);
                assertLastOperationSuccess("a wait nth response - "
                        + a.format(), a);
            }

            /*******************************************************************
             * Incoming response - header/body asserts
             ******************************************************************/

            assertBodyPresent(a.getLastReceivedResponse());
            assertBodyContains(a.getLastReceivedResponse(),
                    "his is a test body");

            SipResponse resp = a.getLastReceivedResponse();
            assertHeaderPresent(resp, "Contact");
            assertHeaderNotPresent(resp, "Contacts");
            assertHeaderNotContains(resp, "Contact", "amit");
            assertHeaderContains(resp, "Contact", "becky");

            /** **************************************************************** */

            // ub needs to send BYE, so SipCall gets a request and can verify
            // higher
            // level request/response asserts
            Request invite = inc_req.getRequest();
            ContactHeader caller_contact = (ContactHeader) invite
                    .getHeader(ContactHeader.NAME);
            FromHeader a_party = (FromHeader) invite.getHeader(FromHeader.NAME);
            ToHeader b_party = (ToHeader) response.getHeader(ToHeader.NAME);
            CSeqHeader cseq = ub.getParent().getHeaderFactory()
                    .createCSeqHeader(
                            ((CSeqHeader) invite.getHeader(CSeqHeader.NAME))
                                    .getSeqNumber(), Request.BYE);

            Request bye = ub.getParent().getMessageFactory().createRequest(
                    caller_contact.getAddress().getURI(),
                    Request.BYE,
                    (CallIdHeader) invite.getHeader(CallIdHeader.NAME),
                    cseq,
                    ub.getParent().getHeaderFactory().createFromHeader(
                            b_party.getAddress(), b_party.getTag()),
                    ub.getParent().getHeaderFactory().createToHeader(
                            a_party.getAddress(), a_party.getTag()),
                    ub.getViaHeaders(),
                    ub.getParent().getHeaderFactory()
                            .createMaxForwardsHeader(5));

            bye.addHeader(ub.getParent().getHeaderFactory().createRouteHeader(
                    caller_contact.getAddress()));

            assertTrue(a.listenForDisconnect());
            assertTrue(ub.sendUnidirectionalRequest(bye, false));
            assertTrue(a.waitForDisconnect(2000));

            /*******************************************************************
             * MessageListener level - methods, request/response assertions
             ******************************************************************/

            SipRequest received_bye = a.getLastReceivedRequest();
            assertNotNull(received_bye);

            ArrayList received_requests = a.getAllReceivedRequests();
            assertEquals(1, received_requests.size());
            assertEquals(received_bye, received_requests.get(0));

            SipResponse received_response = a.getLastReceivedResponse();
            assertNotNull(received_response);

            ArrayList received_responses = a.getAllReceivedResponses();
            int num_responses = received_responses.size();
            assertTrue(num_responses >= 2);
            assertEquals(received_response, received_responses
                    .get(num_responses - 1));

            assertResponseReceived(SipResponse.TRYING, a);
            assertResponseReceived("Expected RINGING", SipResponse.RINGING, a);
            assertResponseNotReceived(SipResponse.OK, a);
            assertResponseNotReceived("Didn't expect OK", SipResponse.OK, a);

            assertResponseReceived(SipResponse.RINGING, SipRequest.INVITE,
                    ((CSeqHeader) invite.getHeader(CSeqHeader.NAME))
                            .getSeqNumber(), a);
            assertResponseReceived("Expected RINGING", SipResponse.RINGING,
                    SipRequest.INVITE, ((CSeqHeader) invite
                            .getHeader(CSeqHeader.NAME))
                            .getSeqNumber(), a);
            assertResponseNotReceived(SipResponse.RINGING, SipRequest.INVITE,
                    ((CSeqHeader) invite.getHeader(CSeqHeader.NAME))
                            .getSeqNumber() + 1, a);
            assertResponseNotReceived("Didn't expect this",
                    SipResponse.RINGING, SipRequest.ACK, ((CSeqHeader) invite
                            .getHeader(CSeqHeader.NAME))
                            .getSeqNumber(), a);

            long received_cseq_seqnum = ((CSeqHeader) invite
                    .getHeader(CSeqHeader.NAME)).getSeqNumber();

            assertRequestReceived(SipRequest.BYE, a);
            assertRequestReceived(SipRequest.BYE, received_cseq_seqnum, a);
            assertRequestReceived("Expected a BYE", SipRequest.BYE, a);
            assertRequestReceived("Wrong CSEQ sequence number", SipRequest.BYE,
                    received_cseq_seqnum, a);
            assertRequestNotReceived(SipRequest.INVITE, a);
            assertRequestNotReceived("Didn't expect a NOTIFY",
                    SipRequest.NOTIFY, a);
            assertRequestNotReceived(SipRequest.BYE, received_cseq_seqnum + 1,
                    a);
            assertRequestNotReceived("Didn't expect a SUBSCRIBE",
                    SipRequest.SUBSCRIBE, received_cseq_seqnum, a);

            /** ************************************************************* */

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testMakeCallFail()
    {
        try
        {
            ua.makeCall("sip:becky@nist.gov", SipResponse.RINGING, 1000,
                    properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                            + myPort + '/' + testProtocol);
            assertLastOperationFail(ua.format(), ua);
            assertEquals(ua.getReturnCode(), SipSession.TIMEOUT_OCCURRED);
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: asynchronous SipPhone.makeCall() callee disc
     */
    public void testMakeCallCalleeDisconnect() // test the nonblocking version
    // of
    // SipPhone.makeCall() - A CALLS B
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            SipCall b = ub.createSipCall(); // incoming call
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(ua.format(), ua);
            // or assertNotNull(a)

            assertTrue(b.waitForIncomingCall(5000));
            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
            Thread.sleep(400);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            Thread.sleep(500);

            assertAnswered("Outgoing call leg not answered", a);
            assertAnswered(b);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() == 2);
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
            Thread.sleep(100);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(3000);
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

    /**
     * Test: asynchronous SipPhone.makeCall() caller disc
     */
    public void testMakeCallCallerDisconnect() // test the nonblocking version
    // of
    // SipPhone.makeCall() - A CALLS B
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            SipCall b = ub.createSipCall(); // incoming call
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));
            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));
            Thread.sleep(100);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            assertTrue(b.sendIncomingCallResponse(Response.OK,
                    "Answer - Hello world", 0));
            Thread.sleep(100);

            assertAnswered("Outgoing call leg not answered", a);
            assertAnswered(b);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() == 2);
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
            b.listenForDisconnect();
            Thread.sleep(100);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(3000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);
            b.respondToDisconnect();

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testBothSidesCallerDisc() // test the blocking version of
    // SipPhone.makeCall()
    {
        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

                    SipCall b = ub.createSipCall();

                    b.listenForIncomingCall();
                    b.waitForIncomingCall(5000);
                    b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
                    Thread.sleep(600);
                    b.sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 0);

                    assertAnswered(b);
                    assertTrue(
                            "Shouldn't have received anything at the called party side",
                            b.getAllReceivedResponses().size() == 0);

                    b.listenForDisconnect();
                    b.waitForDisconnect(30000);
                    assertLastOperationSuccess("b wait disc - " + b.format(), b);
                    b.respondToDisconnect();

                    Thread.sleep(1000);
                    ub.dispose();

                    return;
                }
                catch (Exception e)
                {
                    fail("Exception: " + e.getClass().getName() + ": "
                            + e.getMessage());
                }
            }
        }

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            SipCall a = ua.makeCall("sip:becky@nist.gov", SipResponse.OK, 5000,
                    properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                            + myPort + '/' + testProtocol);
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(2000);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.join();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testMakeCallExtraJainsipParms() // test the blocking version of
    // SipPhone.makeCall() with extra JAIN SIP parameters
    {
        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

                    SipCall b = ub.createSipCall();

                    b.listenForIncomingCall();
                    b.waitForIncomingCall(5000);

                    assertHeaderContains(b.getLastReceivedRequest(),
                            PriorityHeader.NAME, "5");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            ContentTypeHeader.NAME, "applicationn/texxt");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            ContactHeader.NAME, "doodah");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            MaxForwardsHeader.NAME, "62");
                    assertBodyContains(b.getLastReceivedRequest(), "my body");

                    b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
                    Thread.sleep(600);
                    b.sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 0);

                    assertAnswered(b);
                    assertTrue(
                            "Shouldn't have received anything at the called party side",
                            b.getAllReceivedResponses().size() == 0);

                    b.listenForDisconnect();
                    b.waitForDisconnect(30000);
                    assertLastOperationSuccess("b wait disc - " + b.format(), b);
                    b.respondToDisconnect();

                    Thread.sleep(1000);
                    ub.dispose();

                    return;
                }
                catch (Exception e)
                {
                    fail("Exception: " + e.getClass().getName() + ": "
                            + e.getMessage());
                }
            }
        }

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // set up outbound INVITE contents

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(ua.getParent().getHeaderFactory()
                    .createPriorityHeader("5"));
            addnl_hdrs.add(ua.getParent().getHeaderFactory()
                    .createContentTypeHeader("applicationn", "texxt"));

            ArrayList replace_hdrs = new ArrayList();
            URI bogus_contact = ua.getParent().getAddressFactory().createURI(
                    "sip:doodah@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address bogus_addr = ua.getParent().getAddressFactory()
                    .createAddress(bogus_contact);
            replace_hdrs.add(ua.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr)); // verify replacement
            replace_hdrs.add(ua.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(62));

            SipCall a = ua.makeCall("sip:becky@nist.gov", SipResponse.OK, 5000,
                    properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                            + myPort + '/' + testProtocol, addnl_hdrs,
                    replace_hdrs, "my body");
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(2000);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.join();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testMakeCallExtraStringParms() // test the blocking version of
    // SipPhone.makeCall() with extra String parameters
    {
        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

                    SipCall b = ub.createSipCall();

                    b.listenForIncomingCall();
                    b.waitForIncomingCall(5000);

                    assertHeaderContains(b.getLastReceivedRequest(),
                            PriorityHeader.NAME, "5");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            ContentTypeHeader.NAME, "applicationn/texxt");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            ContactHeader.NAME, "doodah");
                    assertHeaderContains(b.getLastReceivedRequest(),
                            MaxForwardsHeader.NAME, "62");
                    assertBodyContains(b.getLastReceivedRequest(), "my body");

                    b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
                    Thread.sleep(600);
                    b.sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 0);

                    assertAnswered(b);
                    assertTrue(
                            "Shouldn't have received anything at the called party side",
                            b.getAllReceivedResponses().size() == 0);

                    b.listenForDisconnect();
                    b.waitForDisconnect(30000);
                    assertLastOperationSuccess("b wait disc - " + b.format(), b);
                    b.respondToDisconnect();

                    Thread.sleep(1000);
                    ub.dispose();

                    return;
                }
                catch (Exception e)
                {
                    fail("Exception: " + e.getClass().getName() + ": "
                            + e.getMessage());
                }
            }
        }

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // set up outbound INVITE contents

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(new String("Priority: 5"));

            ArrayList replace_hdrs = new ArrayList();
            replace_hdrs.add(new String("Contact: <sip:doodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 62"));

            SipCall a = ua.makeCall("sip:becky@nist.gov", SipResponse.OK, 5000,
                    properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                            + myPort + '/' + testProtocol, "my body",
                    "applicationn", "texxt", addnl_hdrs, replace_hdrs);
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(2000);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.join();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testNonblockingMakeCallExtraJainsipParms() // test the
    // nonblocking
    // SipPhone.makeCall() with extra JAIN SIP parameters
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            SipCall b = ub.createSipCall(); // incoming call
            b.listenForIncomingCall();
            Thread.sleep(50);

            // set up outbound INVITE contents

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(ua.getParent().getHeaderFactory()
                    .createPriorityHeader("5"));
            addnl_hdrs.add(ua.getParent().getHeaderFactory()
                    .createContentTypeHeader("applicationn", "texxt"));

            ArrayList replace_hdrs = new ArrayList();
            URI bogus_contact = ua.getParent().getAddressFactory().createURI(
                    "sip:doodah@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address bogus_addr = ua.getParent().getAddressFactory()
                    .createAddress(bogus_contact);
            replace_hdrs.add(ua.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr)); // verify replacement
            replace_hdrs.add(ua.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(62));

            SipCall a = ua.makeCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol, addnl_hdrs,
                    replace_hdrs, "my body");
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            assertHeaderContains(b.getLastReceivedRequest(),
                    PriorityHeader.NAME, "5");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContentTypeHeader.NAME, "applicationn/texxt");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContactHeader.NAME, "doodah");
            assertHeaderContains(b.getLastReceivedRequest(),
                    MaxForwardsHeader.NAME, "62");
            assertBodyContains(b.getLastReceivedRequest(), "my body");

            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));
            Thread.sleep(100);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            assertTrue(b.sendIncomingCallResponse(Response.OK,
                    "Answer - Hello world", 0));
            Thread.sleep(100);

            assertAnswered("Outgoing call leg not answered", a);
            assertAnswered(b);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() == 2);
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
            b.listenForDisconnect();
            Thread.sleep(100);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(3000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);
            b.respondToDisconnect();

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testNonblockingMakeCallExtraStringParms() // test the
    // nonblocking
    // version of SipPhone.makeCall() with extra String parameters
    {
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            SipCall b = ub.createSipCall(); // incoming call
            b.listenForIncomingCall();
            Thread.sleep(50);

            // set up outbound INVITE contents

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(new String("Priority: 5"));

            ArrayList replace_hdrs = new ArrayList();
            replace_hdrs.add(new String("Contact: <sip:doodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 62"));

            SipCall a = ua.makeCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol, "my body",
                    "applicationn", "texxt", addnl_hdrs, replace_hdrs);

            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            assertHeaderContains(b.getLastReceivedRequest(),
                    PriorityHeader.NAME, "5");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContentTypeHeader.NAME, "applicationn/texxt");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContactHeader.NAME, "doodah");
            assertHeaderContains(b.getLastReceivedRequest(),
                    MaxForwardsHeader.NAME, "62");
            assertBodyContains(b.getLastReceivedRequest(), "my body");

            assertTrue(b.sendIncomingCallResponse(Response.RINGING, "Ringing",
                    0));
            Thread.sleep(100);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            assertTrue(b.sendIncomingCallResponse(Response.OK,
                    "Answer - Hello world", 0));
            Thread.sleep(100);

            assertAnswered("Outgoing call leg not answered", a);
            assertAnswered(b);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() == 2);
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
            b.listenForDisconnect();
            Thread.sleep(100);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(3000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);
            b.respondToDisconnect();

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testMultipleReplies()
    {
        // test: sendRequestWithTrans(Request invite),
        // sendReply(Response Trying, no toTag, no contact, ...),
        // sendReply(statusCode Ringing, toTag, contact, ...),
        // sendReply(Response OK, no toTag, contact, ...),
        // waitResponse()

        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            ub.listenRequestMessage();
            Thread.sleep(100);

            AddressFactory addr_factory = ua.getParent().getAddressFactory();
            HeaderFactory hdr_factory = ua.getParent().getHeaderFactory();

            Request invite = ua.getParent().getMessageFactory().createRequest(
                    "INVITE sip:becky@nist.gov SIP/2.0 ");

            invite.addHeader(ua.getParent().getSipProvider().getNewCallId());
            invite.addHeader(hdr_factory.createCSeqHeader((long)1, Request.INVITE));
            invite.addHeader(hdr_factory.createFromHeader(ua.getAddress(), ua
                    .generateNewTag()));

            Address to_address = addr_factory.createAddress(addr_factory
                    .createURI("sip:becky@nist.gov"));
            invite.addHeader(hdr_factory.createToHeader(to_address, null));

            Address contact_address = addr_factory.createAddress("sip:amit@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort);
            invite.addHeader(hdr_factory.createContactHeader(contact_address));

            invite.addHeader(hdr_factory.createMaxForwardsHeader(5));
            ArrayList via_headers = ua.getViaHeaders();
            invite.addHeader((ViaHeader) via_headers.get(0));

            Address route_address = addr_factory.createAddress("sip:becky@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '/' + testProtocol);
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
                    "sip:becky@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
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

    // this method tests re-invite from b to a,
    // TestWithProxyAuthentication does the other direction
    public void testReinvite()
    {
        SipStack.trace("testAdditionalMessageParms"); // using reinvite

        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            // establish a call
            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(20);

            SipCall a = ua.makeCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));
            assertTrue(b.sendIncomingCallResponse(Response.OK,
                    "Answer - Hello world", 600));
            Thread.sleep(200);
            assertResponseReceived(SipResponse.OK, a);
            assertTrue(a.sendInviteOkAck());
            Thread.sleep(300);

            // send request - test reinvite with no specific parameters
            // _____________________________________________

            a.listenForReinvite();
            SipTransaction siptrans_b = b.sendReinvite(null, null,
                    (String) null, null, null);
            assertNotNull(siptrans_b);
            SipTransaction siptrans_a = a.waitForReinvite(1000);
            assertNotNull(siptrans_a);

            SipMessage req = a.getLastReceivedRequest();
            String b_orig_contact_uri = ((ContactHeader) req.getMessage()
                    .getHeader(ContactHeader.NAME)).getAddress().getURI()
                    .toString();

            // check contact info
            assertEquals(ub.getContactInfo().getURI(), b_orig_contact_uri);
            assertHeaderNotContains(req, ContactHeader.NAME, "My DisplayName");

            // check body
            assertHeaderNotPresent(req, ContentTypeHeader.NAME);
            assertBodyNotPresent(req);

            // check additional headers
            assertHeaderNotPresent(req, PriorityHeader.NAME);
            assertHeaderNotPresent(req, ReasonHeader.NAME);

            // check override headers
            assertHeaderContains(req, MaxForwardsHeader.NAME, "10");

            // send response - test new contact only
            // _____________________________________________

            String a_orig_contact_uri = ua.getContactInfo().getURI();
            String a_contact_no_lr = a_orig_contact_uri.substring(0,
                    a_orig_contact_uri.lastIndexOf("lr") - 1);
            assertTrue(a.respondToReinvite(siptrans_a, SipResponse.OK,
                    "ok reinvite response", -1, a_contact_no_lr, null, null,
                    (String) null, null));

            assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            while (b.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            }

            // check response code
            SipResponse response = b.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite response", response.getReasonPhrase());

            // check contact info
            assertEquals(ua.getContactInfo().getURI(), a_contact_no_lr); // changed
            assertFalse(a_orig_contact_uri.equals(a_contact_no_lr));
            assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME, a_contact_no_lr);
            assertHeaderNotContains(response, ContactHeader.NAME,
                    "My DisplayName");

            // check body
            assertHeaderNotPresent(response, ContentTypeHeader.NAME);
            assertBodyNotPresent(response);

            // check additional headers
            assertHeaderNotPresent(response, PriorityHeader.NAME);
            assertHeaderNotPresent(response, ReasonHeader.NAME);

            // check override headers
            assertHeaderContains(response, ContentLengthHeader.NAME, "0");

            // send ACK
            assertTrue(b.sendReinviteOkAck(siptrans_b));
            assertTrue(a.waitForAck(1000));
            Thread.sleep(100); //

            // send request - test contact and body
            // _____________________________________________

            a.listenForReinvite();
            String b_contact_no_lr = b_orig_contact_uri.substring(0,
                    b_orig_contact_uri.lastIndexOf("lr") - 1);
            siptrans_b = b.sendReinvite(b_contact_no_lr, "My DisplayName",
                    "my reinvite", "app", "subapp");
            assertNotNull(siptrans_b);
            siptrans_a = a.waitForReinvite(1000);
            assertNotNull(siptrans_a);

            req = a.getLastReceivedRequest();

            // check contact info
            assertEquals(ub.getContactInfo().getURI(), b_contact_no_lr); // changed
            assertFalse(b_orig_contact_uri.equals(b_contact_no_lr));
            assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
            assertHeaderContains(req, ContactHeader.NAME, b_contact_no_lr);
            assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

            // check body
            assertHeaderContains(req, ContentTypeHeader.NAME, "subapp");
            assertBodyContains(req, "my reinvite");

            // check additional headers
            assertHeaderNotPresent(req, PriorityHeader.NAME);
            assertHeaderNotPresent(req, ReasonHeader.NAME);

            // check override headers
            assertHeaderContains(req, MaxForwardsHeader.NAME, "10");

            // send response - test body only
            // _____________________________________________

            assertTrue(a.respondToReinvite(siptrans_a, SipResponse.OK,
                    "ok reinvite response", -1, null, null, "DooDah",
                    "application", "text"));

            assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            while (b.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            }

            // check response code
            response = b.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite response", response.getReasonPhrase());

            // check contact info
            assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME, a_contact_no_lr);
            assertHeaderNotContains(response, ContactHeader.NAME,
                    "My DisplayName");

            // check body
            assertHeaderPresent(response, ContentTypeHeader.NAME);
            ContentTypeHeader ct_hdr = (ContentTypeHeader) response
                    .getMessage().getHeader(ContentTypeHeader.NAME);
            assertEquals("application", ct_hdr.getContentType());
            assertEquals("text", ct_hdr.getContentSubType());
            assertBodyContains(response, "DooDah");

            // check additional headers
            assertHeaderNotPresent(response, PriorityHeader.NAME);
            assertHeaderNotPresent(response, ReasonHeader.NAME);

            // check override headers
            // done, content sub type not overidden

            // send ACK
            // with JSIP additional, replacement headers, and body
            ArrayList addnl_hdrs = new ArrayList(2);
            ReasonHeader reason_hdr = ub.getParent().getHeaderFactory()
                    .createReasonHeader("SIP", 44, "dummy");
            addnl_hdrs.add(reason_hdr);
            ct_hdr = ub.getParent().getHeaderFactory().createContentTypeHeader(
                    "mytype", "mysubtype");
            addnl_hdrs.add(ct_hdr);

            ArrayList replace_hdrs = new ArrayList(2);
            MaxForwardsHeader hdr = ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(29);
            replace_hdrs.add(hdr);
            PriorityHeader pri_hdr = ub.getParent().getHeaderFactory()
                    .createPriorityHeader(PriorityHeader.URGENT);
            replace_hdrs.add(pri_hdr);

            assertTrue(b.sendReinviteOkAck(siptrans_b, addnl_hdrs,
                    replace_hdrs, "ack body"));
            assertTrue(a.waitForAck(1000));
            SipRequest req_ack = a.getLastReceivedRequest();
            assertHeaderContains(req_ack, ReasonHeader.NAME, "dummy");
            assertHeaderContains(req_ack, MaxForwardsHeader.NAME, "29");
            assertHeaderContains(req_ack, PriorityHeader.NAME, "gent");
            assertHeaderContains(req_ack, ContentTypeHeader.NAME, "mysubtype");
            assertBodyContains(req_ack, "ack body");

            Thread.sleep(100);

            // send request - test additional and replace headers (JAIN SIP)
            // _____________________________________________

            a.listenForReinvite();

            addnl_hdrs = new ArrayList(2);
            pri_hdr = ub.getParent().getHeaderFactory().createPriorityHeader(
                    PriorityHeader.URGENT);
            reason_hdr = ub.getParent().getHeaderFactory().createReasonHeader(
                    "SIP", 41, "I made it up");
            addnl_hdrs.add(pri_hdr);
            addnl_hdrs.add(reason_hdr);

            replace_hdrs = new ArrayList(1);
            hdr = ub.getParent().getHeaderFactory().createMaxForwardsHeader(21);
            replace_hdrs.add(hdr);

            siptrans_b = b.sendReinvite(null, null, addnl_hdrs, replace_hdrs,
                    "no body");
            assertNotNull(siptrans_b);
            siptrans_a = a.waitForReinvite(1000);
            assertNotNull(siptrans_a);

            req = a.getLastReceivedRequest();

            // check contact info
            assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
            assertHeaderContains(req, ContactHeader.NAME, b_contact_no_lr);
            assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

            // check body
            assertHeaderNotPresent(req, ContentTypeHeader.NAME);
            assertBodyNotPresent(req);

            // check additional headers
            assertHeaderContains(req, PriorityHeader.NAME,
                    PriorityHeader.URGENT);
            assertHeaderContains(req, ReasonHeader.NAME, "41");

            // check override headers
            assertHeaderContains(req, MaxForwardsHeader.NAME, "21");

            // test everything
            // _____________________________________________

            addnl_hdrs.clear();
            replace_hdrs.clear();
            addnl_hdrs.add("Priority: Normal");
            addnl_hdrs.add("Reason: SIP; cause=42; text=\"I made it up\"");

            replace_hdrs.add("Content-Length: 4");

            assertTrue(a.respondToReinvite(siptrans_a, SipResponse.OK,
                    "ok reinvite last response", -1, a_orig_contact_uri,
                    "Original info", "DooDahDay", "applicationn", "sdp",
                    addnl_hdrs, replace_hdrs));

            assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            while (b.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(b.waitReinviteResponse(siptrans_b, 2000));
            }

            // check response code
            response = b.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite last response", response
                    .getReasonPhrase());

            // check contact info
            assertEquals(ua.getContactInfo().getURI(), a_orig_contact_uri); // changed
            assertFalse(a_orig_contact_uri.equals(a_contact_no_lr));
            assertHeaderContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME,
                    a_orig_contact_uri);
            assertHeaderContains(response, ContactHeader.NAME, "Original info");

            // check body
            assertHeaderPresent(response, ContentTypeHeader.NAME);
            ct_hdr = (ContentTypeHeader) response.getMessage().getHeader(
                    ContentTypeHeader.NAME);
            assertEquals("applicationn", ct_hdr.getContentType());
            assertEquals("sdp", ct_hdr.getContentSubType());
            assertBodyContains(response, "DooD");

            // check additional headers
            assertHeaderContains(response, PriorityHeader.NAME,
                    PriorityHeader.NORMAL);
            assertHeaderContains(response, ReasonHeader.NAME, "42");

            // check override headers
            assertHeaderContains(response, ContentLengthHeader.NAME, "4");

            // send ACK
            assertTrue(b.sendReinviteOkAck(siptrans_b));
            assertTrue(a.waitForAck(1000));
            Thread.sleep(100); //

            // done, finish up
            a.listenForDisconnect();
            Thread.sleep(100);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            a.respondToDisconnect();

            Thread.sleep(100);
            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

    }

    public void testSendReplySipTransactionExtraInfo()
    {
        // test sendReply(SipTransaction, ....) options
        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            ub.listenRequestMessage();
            Thread.sleep(100);

            SipCall a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            RequestEvent inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            Response response = ub.getParent().getMessageFactory()
                    .createResponse(Response.TRYING, inc_req.getRequest());
            SipTransaction transb = ub.sendReply(inc_req, response); // sendReply(RequestEvent)
            assertNotNull(ub.format(), transb);
            // initial trying response sent

            Thread.sleep(100);

            // receive it on the 'a' side

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
            assertEquals("Unexpected 1st response received", Response.TRYING, a
                    .getReturnCode());

            // (a) send reply with additional JSIP Headers but no body

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);
            String to_tag = ub.generateNewTag();
            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(12));
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("app", "subtype"));
            // no body - should receive msg with body length 0 and with content
            // type header
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            SipMessage resp = a.getLastReceivedResponse();
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

            // (b) send reply with additional JSIP Header (ContentTypeHeader)
            // and body
            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("bapp", "subtype"));
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyContains(resp, "my body");

            // (c) send reply with other additional JSIP Header (not
            // ContentTypeHeader) and body
            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(11));
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
            assertBodyNotPresent(resp);

            // (d) send reply with replace JSIP Header (test replacement),
            // ignored body
            ArrayList replace_hdrs = new ArrayList();
            URI bogus_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:doodah@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address bogus_addr = ub.getParent().getAddressFactory()
                    .createAddress(bogus_contact);
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr));
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, replace_hdrs, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
            assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

            // (e) send reply with replace JSIP Header (test addition)
            replace_hdrs.clear();
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(50));
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, replace_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "becky");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

            // (f) send reply with all - additional,replace JSIP Headers & body
            addnl_hdrs.clear();
            replace_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory().createToHeader(
                    bogus_addr, "mytag")); // verify ignored
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("application", "text"));// for
            // body
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr)); // verify replacement
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(60)); // verify addition
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, replace_hdrs, "my new body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
            assertBodyContains(resp, "my new body");
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");
            ;

            // now for the String header version:

            // (a') send reply with additional String Headers & content type
            // info but no body

            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(12).toString());
            // no body - should receive msg with body length 0 and with content
            // type header
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, "app", "subtype", addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

            // (b') send reply with ContentTypeHeader info
            // and body
            addnl_hdrs.clear();
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my body", "bapp", "subtype", null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyContains(resp, "my body");

            // (c') send reply with other additional String Header (not
            // ContentType info) and body
            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(11).toString());
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, null, addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
            assertBodyNotPresent(resp);

            // (d') send reply with replace String Header (test replacement),
            // ignored body
            replace_hdrs.clear();
            replace_hdrs.add("Contact: <sip:doodah@192.168.1.101:5060>");
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, null, null, replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
            assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

            // (e') send reply with replace String Header (test addition)
            replace_hdrs.clear();
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(50).toString());
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "becky");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

            // (f') send reply with all - additional,replace String Headers,
            // CTinfo & body
            addnl_hdrs.clear();
            replace_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory().createToHeader(
                    bogus_addr, "mytag").toString()); // verify ignored
            replace_hdrs.add("Contact: <sip:doodah@192.168.1.101:5060>"); // verify
            // replacement
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(60).toString()); // verify
            // addition
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my new body", "application", "text", addnl_hdrs,
                    replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
            assertBodyContains(resp, "my new body");
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");

            // (g') send reply with bad String headers

            replace_hdrs.clear();
            replace_hdrs.add("Max-Forwards");
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, replace_hdrs);
            assertLastOperationFail(ub);
            assertTrue(ub.format().indexOf("no HCOLON") != -1);

            // (h') send reply with partial content type parms and body, no
            // addnl hdrs

            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, "subtype", null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);

            // (i') send reply with partial content type parms and body, other
            // addnl hdrs

            addnl_hdrs.clear();
            addnl_hdrs.add("Max-Forwards: 66");
            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    "my body", "app", null, addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "66");

            // (j') send reply with nothing

            ub.sendReply(transb, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ToHeader.NAME, to_tag);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return;
    }

    public void testSendReplyRequestEventExtraInfo()
    {
        // test sendReply(RequestEvent, ....) options

        try
        {
            SipPhone ub = sipStack.createSipPhone("sip:becky@nist.gov");

            ub.listenRequestMessage();
            Thread.sleep(100);

            SipCall a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            RequestEvent inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            Response response = ub.getParent().getMessageFactory()
                    .createResponse(Response.TRYING, inc_req.getRequest());
            SipTransaction transb = ub.sendReply(inc_req, response); // sendReply(RequestEvent)
            assertNotNull(ub.format(), transb);

            Thread.sleep(100);

            // receive it on the 'a' side

            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
            assertEquals("Unexpected 1st response received", Response.TRYING, a
                    .getReturnCode());

            // (a) send reply with additional JSIP Headers but no body

            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            URI callee_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:becky@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address contact = ub.getParent().getAddressFactory().createAddress(
                    callee_contact);
            String to_tag = ub.generateNewTag();
            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(12));
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("app", "subtype"));
            // no body - should receive msg with body length 0 and with content
            // type header
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            SipMessage resp = a.getLastReceivedResponse();
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

            // (b) send reply with additional JSIP Header (ContentTypeHeader)
            // and body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("bapp", "subtype"));
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyContains(resp, "my body");

            // (c) send reply with other additional JSIP Header (not
            // ContentTypeHeader) and body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(11));
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, null, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
            assertBodyNotPresent(resp);

            // (d) send reply with replace JSIP Header (test replacement),
            // ignored body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            ArrayList replace_hdrs = new ArrayList();
            URI bogus_contact = ub.getParent().getAddressFactory().createURI(
                    "sip:doodah@"
                            + properties.getProperty("javax.sip.IP_ADDRESS")
                            + ':' + myPort);
            Address bogus_addr = ub.getParent().getAddressFactory()
                    .createAddress(bogus_contact);
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr));
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, replace_hdrs, "my body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
            assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

            // (e) send reply with replace JSIP Header (test addition)
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            replace_hdrs.clear();
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(50));
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, replace_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "becky");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

            // (f) send reply with all - additional,replace JSIP Headers & body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            replace_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory().createToHeader(
                    bogus_addr, "mytag")); // verify ignored
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("application", "text"));// for
            // body
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContactHeader(bogus_addr)); // verify replacement
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(60)); // verify addition
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    addnl_hdrs, replace_hdrs, "my new body");
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
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
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(12).toString());
            // no body - should receive msg with body length 0 and with content
            // type header
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, "app", "subtype", addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "app");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContentLengthHeader.NAME, "0");

            // (b') send reply with ContentTypeHeader info
            // and body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my body", "bapp", "subtype", null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, MaxForwardsHeader.NAME, "12");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "bapp");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "subtype");
            assertBodyContains(resp, "my body");

            // (c') send reply with other additional String Header (not
            // ContentType info) and body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(11).toString());
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, null, addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "11");
            assertBodyNotPresent(resp);

            // (d') send reply with replace String Header (test replacement),
            // ignored body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            replace_hdrs.clear();
            replace_hdrs.add("Contact: <sip:doodah@192.168.1.101:5060>");
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, null, null, replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ContactHeader.NAME, "becky");
            assertHeaderNotPresent(resp, MaxForwardsHeader.NAME);

            // (e') send reply with replace String Header (test addition)
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            replace_hdrs.clear();
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(50).toString());
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ContactHeader.NAME, "becky");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "50");

            // (f') send reply with all - additional,replace String Headers,
            // CTinfo & body
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            replace_hdrs.clear();
            addnl_hdrs.add(ub.getParent().getHeaderFactory().createToHeader(
                    bogus_addr, "mytag").toString()); // verify ignored
            replace_hdrs.add("Contact: <sip:doodah@192.168.1.101:5060>"); // verify
            // replacement
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(60).toString()); // verify
            // addition
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my new body", "application", "text", addnl_hdrs,
                    replace_hdrs);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotContains(resp, ToHeader.NAME, "doodah");
            assertHeaderNotContains(resp, ToHeader.NAME, "mytag");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "application");
            assertHeaderContains(resp, ContentTypeHeader.NAME, "text");
            assertBodyContains(resp, "my new body");
            assertHeaderContains(resp, ContactHeader.NAME, "doodah");
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "60");

            // (g') send reply with bad String headers
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            replace_hdrs.clear();
            replace_hdrs.add("Max-Forwards");
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, replace_hdrs);
            assertLastOperationFail(ub);
            assertTrue(ub.format().indexOf("no HCOLON") != -1);

            // (h') send reply with partial content type parms and body, no
            // addnl hdrs
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my body", null, "subtype", null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);

            // (i') send reply with partial content type parms and body, other
            // addnl hdrs
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            addnl_hdrs.clear();
            addnl_hdrs.add("Max-Forwards: 66");
            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    "my body", "app", null, addnl_hdrs, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, MaxForwardsHeader.NAME, "66");

            // (j') send reply with nothing
            a.dispose(); // recreate the call, can only call
            // sendReply(RequestEvent,..) once for the same request.
            a = ua.createSipCall();
            a.initiateOutgoingCall("sip:becky@nist.gov", properties
                    .getProperty("javax.sip.IP_ADDRESS")
                    + ':' + myPort + '/' + testProtocol);
            assertLastOperationSuccess(a.format(), a);
            // call sent

            inc_req = ub.waitRequest(30000);
            assertNotNull(ub.format(), inc_req);
            // call received

            ub.sendReply(inc_req, Response.RINGING, null, to_tag, contact, -1,
                    null, null, null, null, null);
            assertLastOperationSuccess(ub.format(), ub);

            Thread.sleep(100);

            // receive it on the 'a' side
            a.waitOutgoingCallResponse(10000);
            assertLastOperationSuccess("a wait response - " + a.format(), a);
            assertEquals("Unexpected response received", Response.RINGING, a
                    .getReturnCode());
            // check parms in reply
            resp = a.getLastReceivedResponse();
            assertHeaderNotPresent(resp, ContentTypeHeader.NAME);
            assertBodyNotPresent(resp);
            assertHeaderContains(resp, ToHeader.NAME, to_tag);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return;
    }

}