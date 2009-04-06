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
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Properties;

import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.PriorityHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Response;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipMessage;
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
 * authentication turned on. The Authentication scheme is Digest. Defaults:
 * proxy host = 192.168.1.103, port = 5060, protocol = udp; user amit password
 * a1b2c3d4 and user becky password a1b2c3d4 defined at the proxy for domain
 * cafesip.org. The sipunit test stack uses port 9091.
 * 
 * Example open-source Proxy/registrars include SipExchange (cafesip.org) and
 * nist.gov's JAIN-SIP Proxy for the People
 * (http://snad.ncsl.nist.gov/proj/iptel/).
 * 
 * @author Becky McElroy
 * 
 */
public class TestWithProxyAuthentication extends SipTestCase
{
    private SipStack sipStack;

    private SipPhone ua;

    private int proxyPort;

    private int myPort;

    private String testProtocol;

    private String myUrl;

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
        defaultProperties.setProperty("sipunit.test.port", "9091");
        defaultProperties.setProperty("sipunit.test.protocol", "udp");

        defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
        defaultProperties.setProperty("sipunit.proxy.host", "192.168.1.101");
        defaultProperties.setProperty("sipunit.proxy.port", "5060");
    }

    private Properties properties = new Properties(defaultProperties);

    public TestWithProxyAuthentication(String arg0)
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
            myPort = 9091;
        }

        try
        {
            proxyPort = Integer.parseInt(properties
                    .getProperty("sipunit.proxy.port"));
        }
        catch (NumberFormatException e)
        {
            proxyPort = 5060;
        }

        testProtocol = properties.getProperty("sipunit.test.protocol");
        myUrl = "sip:amit@" + properties.getProperty("sipunit.test.domain");

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
            ua = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, myUrl);
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

    public void testAuthRegistration()
    {
        SipStack.trace("testAuthRegistration");

        ua.register("amit", "a1b2c3d4", null, 4890, 10000);
        assertLastOperationSuccess("user a registration - " + ua.format(), ua);

        // check that the proper default contact address was registered
        try
        {
            SipURI default_contact = ua.getParent().getAddressFactory()
                    .createSipURI(
                            ((SipURI) ua.getAddress().getURI()).getUser(),
                            ua.getStackAddress());
            default_contact.setPort(ua.getParent().getSipProvider()
                    .getListeningPoints()[0].getPort());
            default_contact.setTransportParam(ua.getParent().getSipProvider()
                    .getListeningPoints()[0].getTransport());
            default_contact.setSecure(((SipURI) ua.getAddress().getURI())
                    .isSecure());
            default_contact.setLrParam();

            assertEquals("The default contact is wrong", default_contact
                    .toString(), ua.getContactInfo().getURI());

            assertEquals("The default contact is wrong", "sip:amit@"
                    + ua.getStackAddress() + ':' + myPort + ";lr;transport="
                    + testProtocol, ua.getContactInfo().getURI());
        }
        catch (Exception ex)
        {
            fail("Exception while forming default contact URI: "
                    + ex.getClass().getName() + ": " + ex.getMessage());
        }

        // check contact expiry
        // assertEquals("wrong contact expiry", 4890,
        // getContactInfo().getExpiry());
        // why proxy/registrar doesn't take it?

        // wait 1 sec then unregister
        try
        {
            Thread.sleep(1000);
        }
        catch (Exception ex)
        {
        }

        ua.unregister("a@" + properties.getProperty("sipunit.proxy.host"),
                10000); // unregister the wrong contact
        assertLastOperationFail("unregistering wrong user - " + ua.format(), ua);

        ua.unregister(ua.getContactInfo().getURI(), 10000);
        assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
    }

    public void testCustomContactRegistration()
    {
        SipStack.trace("testCustomContactRegistration");

        ua.register("amit", "a1b2c3d4",
                "sip:billy@bob.land:9090;transport=tcp", 4890, 10000);
        assertLastOperationSuccess("user a registration - " + ua.format(), ua);

        // check that the proper default contact address was registered
        try
        {
            assertEquals("The registered contact is wrong",
                    "sip:billy@bob.land:9090;transport=tcp", ua
                            .getContactInfo().getURI().toString());
        }
        catch (Exception ex)
        {
            fail("Exception while checking contact info: "
                    + ex.getClass().getName() + ": " + ex.getMessage());
        }

        // check contact expiry
        // assertEquals("wrong contact expiry", 4890,
        // getContactInfo().getExpiry());
        // why proxy/registrar doesn't take it?

        // wait 1 sec then unregister
        try
        {
            Thread.sleep(1000);
        }
        catch (Exception ex)
        {
        }

        ua.unregister("a@" + properties.getProperty("sipunit.proxy.host"),
                10000); // unregister the wrong contact
        assertLastOperationFail("unregistering wrong user - " + ua.format(), ua);

        ua.unregister(ua.getContactInfo().getURI().toString(), 10000);
        assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
    }

    public void testBadPasswdRegistration() // authentication must be turned
    // on
    // at server
    {
        SipStack.trace("testBadPasswdRegistration");

        ua.register("amit", "a1b2c3d44", ua.getAddress().toString(), 0, 10000);
        assertLastOperationFail("Registration should have failed", ua);
    }

    /**
     * Test: asynchronous SipPhone.makeCall() with authentication, callee disc
     * SipPhone.register() using pre-set credentials
     */
    public void testBothSidesAsynchMakeCall() // test the nonblocking version
    // of
    // SipPhone.makeCall()
    {
        SipStack.trace("testBothSidesAsynchMakeCall");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null);
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));
            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            Thread.sleep(300);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
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
            Thread.sleep(100);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            a.respondToDisconnect();

            Thread.sleep(500);
            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: asynchronous SipPhone.makeCall() with authentication, caller disc
     * SipPhone.register() using pre-set credentials
     */
    public void testAsynchMakeCallCallerDisc() // test the nonblocking version
    // of
    // SipPhone.makeCall()
    {
        SipStack.trace("testAsynchMakeCallCallerDisc");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null);
            assertLastOperationSuccess(ua.format(), ua);

            boolean status = b.waitForIncomingCall(4000);
            assertTrue(b.format(), status);
            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            Thread.sleep(300);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
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
            Thread.sleep(2000);
            b.listenForDisconnect();
            Thread.sleep(1000);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(5000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);

            b.respondToDisconnect();

            Thread.sleep(1000);
            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: callTimeoutOrError() indicates failure properly
     * 
     * Uncomment to test.
     */
    public void xtestMakeCallFailureCheck()
    {
        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 1800);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            String bad_route = properties.getProperty("sipunit.proxy.host")
                    + ":" + (proxyPort + 1);

            // test asynchronous makeCall() failure

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), bad_route
                    + "/udp");
            assertLastOperationSuccess(ua.format(), ua);
            assertNotAnswered("Call leg shouldn't be answered", a);
            Thread.sleep(120000);

            assertTrue("Outgoing call leg error status incorrect", a
                    .callTimeoutOrError()); // should get a timeout

            a.dispose();

            // synchronous makeCall() test not applicable, SipCall null
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: SipPhone.makeCall() with authentication; send authorization on ACK,
     * BYE SipPhone.register() using pre-set credentials
     * 
     */
    public void testBothSidesCallerDisc() // test the blocking version of
    // SipPhone.makeCall()
    {
        SipStack.trace("testBothSidesCallerDisc");

        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack
                            .createSipPhone(
                                    properties
                                            .getProperty("sipunit.proxy.host"),
                                    testProtocol,
                                    proxyPort,
                                    "sip:becky@"
                                            + properties
                                                    .getProperty("sipunit.test.domain"));

                    ub.register("becky", "a1b2c3d4", null, 600, 5000);

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
                    b.waitForDisconnect(3000);
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

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 1800);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // give user b time to register
            Thread.sleep(2000);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"),
                    SipResponse.OK, 5000, null);
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() >= 2);

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

    public void testMakeCallExtraJainsipParms()
    {
        SipStack.trace("testBothSidesCallerDisc");

        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack
                            .createSipPhone(
                                    properties
                                            .getProperty("sipunit.proxy.host"),
                                    testProtocol,
                                    proxyPort,
                                    "sip:becky@"
                                            + properties
                                                    .getProperty("sipunit.test.domain"));

                    ub.register("becky", "a1b2c3d4", null, 600, 5000);

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
                            MaxForwardsHeader.NAME, "61");
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
                    b.waitForDisconnect(3000);
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

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 1800);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // give user b time to register
            Thread.sleep(2000);

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

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"),
                    SipResponse.OK, 5000, null, addnl_hdrs, replace_hdrs,
                    "my body");
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() >= 2);

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
        SipStack.trace("testBothSidesCallerDisc");

        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack
                            .createSipPhone(
                                    properties
                                            .getProperty("sipunit.proxy.host"),
                                    testProtocol,
                                    proxyPort,
                                    "sip:becky@"
                                            + properties
                                                    .getProperty("sipunit.test.domain"));

                    ub.register("becky", "a1b2c3d4", null, 600, 5000);

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
                            MaxForwardsHeader.NAME, "61");
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
                    b.waitForDisconnect(3000);
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

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 1800);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // give user b time to register
            Thread.sleep(2000);

            // set up outbound INVITE contents

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(new String("Priority: 5"));

            ArrayList replace_hdrs = new ArrayList();
            replace_hdrs.add(new String("Contact: <sip:doodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 62"));

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"),
                    SipResponse.OK, 5000, null, "my body", "applicationn",
                    "texxt", addnl_hdrs, replace_hdrs);
            assertLastOperationSuccess(ua.format(), ua);

            assertAnswered("Outgoing call leg not answered", a);
            assertFalse("Outgoing call leg error status wrong", a
                    .callTimeoutOrError());

            assertTrue("Wrong number of responses received", a
                    .getAllReceivedResponses().size() >= 2);

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
        SipStack.trace("testNonblockingMakeCallExtraJainsipParms");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
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

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null,
                    addnl_hdrs, replace_hdrs, "my body");
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            assertHeaderContains(b.getLastReceivedRequest(),
                    PriorityHeader.NAME, "5");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContentTypeHeader.NAME, "applicationn/texxt");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContactHeader.NAME, "doodah");
            assertHeaderContains(b.getLastReceivedRequest(),
                    MaxForwardsHeader.NAME, "61");
            assertBodyContains(b.getLastReceivedRequest(), "my body");

            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            Thread.sleep(300);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
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
            Thread.sleep(100);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            a.respondToDisconnect();

            Thread.sleep(500);
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
    // version
    // of SipPhone.makeCall() with extra String parameters
    {
        SipStack.trace("testNonblockingMakeCallExtraStringParms");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
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

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null,
                    "my body", "applicationn", "texxt", addnl_hdrs,
                    replace_hdrs);

            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            assertHeaderContains(b.getLastReceivedRequest(),
                    PriorityHeader.NAME, "5");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContentTypeHeader.NAME, "applicationn/texxt");
            assertHeaderContains(b.getLastReceivedRequest(),
                    ContactHeader.NAME, "doodah");
            assertHeaderContains(b.getLastReceivedRequest(),
                    MaxForwardsHeader.NAME, "61");
            assertBodyContains(b.getLastReceivedRequest(), "my body");

            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            Thread.sleep(300);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
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
            Thread.sleep(100);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(5000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            a.respondToDisconnect();

            Thread.sleep(500);
            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    public void testMiscExtraParms() // test the remaining SipCall methods
    // that take extra parameters
    {
        SipStack.trace("testMiscExtraParms");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(50);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null);

            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));

            // create extra parameters for sendIncomingCallResponse()

            ArrayList addnl_hdrs = new ArrayList();
            addnl_hdrs.add(new String("Priority: 5"));

            ArrayList replace_hdrs = new ArrayList();
            replace_hdrs.add(new String("Contact: <sip:doodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 62"));

            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600,
                    "my body", "applicationn", "texxt", addnl_hdrs,
                    replace_hdrs);
            Thread.sleep(300);
            assertNotAnswered("Call leg shouldn't be answered yet", a);
            assertNotAnswered(b);

            // verify extra parameters were received in the message

            assertResponseReceived("Should have gotten RINGING response",
                    SipResponse.RINGING, a);

            SipResponse response = a
                    .findMostRecentResponse(SipResponse.RINGING);
            assertHeaderContains(response, PriorityHeader.NAME, "5");
            assertHeaderContains(response, ContentTypeHeader.NAME,
                    "applicationn/texxt");
            assertHeaderContains(response, ContactHeader.NAME, "doodah");
            assertHeaderContains(response, MaxForwardsHeader.NAME, "62");
            assertBodyContains(response, "my body");

            b
                    .sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 600);
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

            // verify OK was received
            assertResponseReceived(SipResponse.OK, a);
            // check negative
            assertResponseNotReceived("Unexpected response",
                    SipResponse.NOT_FOUND, a);
            assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, a);

            // verify getLastReceivedResponse() method
            assertEquals("Last response received wasn't answer",
                    SipResponse.OK, a.getLastReceivedResponse().getStatusCode());

            // create extra parameters for sendInviteOkAck()

            addnl_hdrs.clear();
            addnl_hdrs.add(new String("Priority: 6"));
            addnl_hdrs
                    .add(new String("Route: " + ub.getContactInfo().getURI()));

            replace_hdrs.clear();
            replace_hdrs.add(new String("Contact: <sip:dooodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 63"));

            a.sendInviteOkAck("my boddy", "application", "text", addnl_hdrs,
                    replace_hdrs);
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            // verify extra parameters were received in the message

            assertTrue(b.waitForAck(1000));
            SipRequest request = b.getLastReceivedRequest();
            assertHeaderContains(request, PriorityHeader.NAME, "6");
            assertHeaderContains(request, ContentTypeHeader.NAME,
                    "application/text");
            assertHeaderContains(request, ContactHeader.NAME, "dooodah");
            assertHeaderContains(request, MaxForwardsHeader.NAME, "63");
            assertBodyContains(request, "my boddy");

            a.listenForDisconnect();
            Thread.sleep(100);

            // create extra parameters for disconnect()

            addnl_hdrs.clear();
            addnl_hdrs.add(new String("Priority: 7"));

            replace_hdrs.clear();
            replace_hdrs.add(new String("Contact: <sip:doooodah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 64"));

            b.disconnect("my bodddy", "appl", "txt", addnl_hdrs, replace_hdrs);
            assertLastOperationSuccess("b disc - " + b.format(), b);

            // verify extra parameters were received in the message

            a.waitForDisconnect(1000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            request = a.getLastReceivedRequest();
            assertHeaderContains(request, PriorityHeader.NAME, "7");
            assertHeaderContains(request, ContentTypeHeader.NAME, "appl/txt");
            assertHeaderContains(request, ContactHeader.NAME, "doooodah");
            assertHeaderContains(request, MaxForwardsHeader.NAME, "64");
            assertBodyContains(request, "my bodddy");

            // create extra parameters for respondToDisconnect()

            addnl_hdrs.clear();
            addnl_hdrs.add(new String("Priority: 8"));
            addnl_hdrs
                    .add(new String("Route: " + ub.getContactInfo().getURI()));

            replace_hdrs.clear();
            replace_hdrs.add(new String("Contact: <sip:ddah@"
                    + properties.getProperty("javax.sip.IP_ADDRESS") + ':'
                    + myPort + '>'));
            replace_hdrs.add(new String("Max-Forwards: 65"));

            a.respondToDisconnect(SipResponse.ACCEPTED, "OK", "my bdy", "app",
                    "xt", addnl_hdrs, replace_hdrs);

            // verify extra parameters were received in the message

            /*
             * TODO - BYE response gets to proxy but not to the far end - is it
             * a proxy problem, or something wrong we're doing here? Resolve and
             * uncomment the following:
             * 
             * Thread.sleep(500); response = b.getLastReceivedResponse();
             * assertEquals(202, response.getStatusCode());
             * assertHeaderContains(response, PriorityHeader.NAME, "8");
             * assertHeaderContains(response, ContentTypeHeader.NAME, "app/xt");
             * assertHeaderContains(response, ContactHeader.NAME, "ddah");
             * assertHeaderContains(response, MaxForwardsHeader.NAME, "65");
             * assertBodyContains(response, "my bdy");
             */

            ub.dispose();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: SipPhone.makeCall() with authentication, callee disc
     * SipPhone.register() using pre-set credentials
     */
    public void testBothSidesCalleeDisc() // test the blocking version of
    // SipPhone.makeCall()
    {
        SipStack.trace("testBothSidesCalleeDisc");

        final class PhoneB extends Thread
        {
            public void run()
            {
                try
                {
                    SipPhone ub = sipStack
                            .createSipPhone(
                                    properties
                                            .getProperty("sipunit.proxy.host"),
                                    testProtocol,
                                    proxyPort,
                                    "sip:becky@"
                                            + properties
                                                    .getProperty("sipunit.test.domain"));
                    ub.addUpdateCredential(new Credential(properties
                            .getProperty("sipunit.test.domain"), "becky",
                            "a1b2c3d4"));
                    ub.register(null, 9600);

                    SipCall b = ub.createSipCall();

                    b.listenForIncomingCall();
                    b.waitForIncomingCall(5000);
                    b.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
                    Thread.sleep(600);
                    b.sendIncomingCallResponse(Response.OK,
                            "Answer - Hello world", 0);

                    Thread.sleep(2000);
                    b.disconnect();
                    assertLastOperationSuccess("b disc - " + b.format(), b);
                    Thread.sleep(2000);

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

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 1800);

        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            PhoneB b = new PhoneB();
            b.start();

            // give user b time to register
            Thread.sleep(2000);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"),
                    SipResponse.OK, 5000, null);
            assertLastOperationSuccess(ua.format(), ua);

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            a.listenForDisconnect();
            a.waitForDisconnect(10000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);
            a.respondToDisconnect();

            b.join();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    /**
     * Test: SipCall send and receive RE-INVITE methods. This method tests
     * re-invite from a to b, TestNoProxy does the other direction
     */
    public void testReinvite()
    {
        SipStack.trace("testReinvite");
        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);
        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            // establish a call
            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(20);

            SipCall a = ua.makeCall("sip:becky@cafesip.org", properties
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

            b.listenForReinvite();
            SipTransaction siptrans_a = a.sendReinvite(null, null,
                    (ArrayList) null, null, null);
            assertNotNull(siptrans_a);
            SipTransaction siptrans_b = b.waitForReinvite(1000);
            assertNotNull(siptrans_b);

            SipMessage req = b.getLastReceivedRequest();
            String a_orig_contact_uri = ((ContactHeader) req.getMessage()
                    .getHeader(ContactHeader.NAME)).getAddress().getURI()
                    .toString();

            // check contact info
            assertEquals(ua.getContactInfo().getURI(), a_orig_contact_uri);
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

            String b_orig_contact_uri = ub.getContactInfo().getURI();
            String b_contact_no_lr = b_orig_contact_uri.substring(0,
                    b_orig_contact_uri.lastIndexOf("lr") - 1);
            assertTrue(b.respondToReinvite(siptrans_b, SipResponse.OK,
                    "ok reinvite response", -1, b_contact_no_lr, null, null,
                    (String) null, null));

            assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            while (a.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            }

            // check response code
            SipResponse response = a.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite response", response.getReasonPhrase());

            // check contact info
            assertEquals(ub.getContactInfo().getURI(), b_contact_no_lr); // changed
            assertFalse(b_orig_contact_uri.equals(b_contact_no_lr));
            assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME, b_contact_no_lr);
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
            assertTrue(a.sendReinviteOkAck(siptrans_a));
            assertTrue(b.waitForAck(1000));
            Thread.sleep(100); //

            // send request - test new contact and display name
            // _____________________________________________

            b.listenForReinvite();
            String a_contact_no_lr = a_orig_contact_uri.substring(0,
                    a_orig_contact_uri.lastIndexOf("lr") - 1);
            siptrans_a = a.sendReinvite(a_contact_no_lr, "My DisplayName",
                    (ArrayList) null, null, null);
            assertNotNull(siptrans_a);
            siptrans_b = b.waitForReinvite(1000);
            assertNotNull(siptrans_b);

            req = b.getLastReceivedRequest();

            // check contact info
            assertEquals(ua.getContactInfo().getURI(), a_contact_no_lr); // changed
            assertFalse(a_orig_contact_uri.equals(a_contact_no_lr));
            assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
            assertHeaderContains(req, ContactHeader.NAME, a_contact_no_lr);
            assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

            // check body
            assertHeaderNotPresent(req, ContentTypeHeader.NAME);
            assertBodyNotPresent(req);

            // check additional headers
            assertHeaderNotPresent(req, PriorityHeader.NAME);
            assertHeaderNotPresent(req, ReasonHeader.NAME);

            // check override headers
            assertHeaderContains(req, MaxForwardsHeader.NAME, "10");

            // send response - test body only
            // _____________________________________________

            assertTrue(b.respondToReinvite(siptrans_b, SipResponse.OK,
                    "ok reinvite response", -1, null, null, "DooDah",
                    "application", "text"));

            assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            while (a.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            }

            // check response code
            response = a.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite response", response.getReasonPhrase());

            // check contact info
            assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME, b_contact_no_lr);
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
            assertTrue(a.sendReinviteOkAck(siptrans_a));
            assertTrue(b.waitForAck(1000));
            Thread.sleep(100);

            // send request - test additional & replace headers (String)
            // _____________________________________________

            b.listenForReinvite();

            ArrayList addnl_hdrs = new ArrayList(2);
            addnl_hdrs.add("Priority: Urgent");
            addnl_hdrs.add("Reason: SIP; cause=41; text=\"I made it up\"");

            ArrayList replace_hdrs = new ArrayList(1);
            MaxForwardsHeader hdr = ua.getParent().getHeaderFactory()
                    .createMaxForwardsHeader(22);
            replace_hdrs.add(hdr.toString());

            siptrans_a = a.sendReinvite(null, null, "mybody", "myapp",
                    "mysubapp", addnl_hdrs, replace_hdrs);
            assertNotNull(siptrans_a);
            siptrans_b = b.waitForReinvite(1000);
            assertNotNull(siptrans_b);

            req = b.getLastReceivedRequest();

            // check contact info
            assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
            assertHeaderContains(req, ContactHeader.NAME, a_contact_no_lr);
            assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

            // check body
            assertHeaderContains(req, ContentTypeHeader.NAME, "myapp/mysubapp");
            assertBodyContains(req, "mybo");

            // check additional headers
            assertHeaderContains(req, PriorityHeader.NAME,
                    PriorityHeader.URGENT);
            assertHeaderContains(req, ReasonHeader.NAME, "41");
            assertHeaderContains(req, ReasonHeader.NAME, "I made it up");

            // check override headers
            assertHeaderContains(req, MaxForwardsHeader.NAME, "22");

            // test everything
            // _____________________________________________

            addnl_hdrs.clear();
            PriorityHeader pri_hdr = ub.getParent().getHeaderFactory()
                    .createPriorityHeader(PriorityHeader.NORMAL);
            ReasonHeader reason_hdr = ub.getParent().getHeaderFactory()
                    .createReasonHeader("SIP", 42, "I made it up");
            ct_hdr = ub.getParent().getHeaderFactory().createContentTypeHeader(
                    "applicationn", "sdp");
            addnl_hdrs.add(pri_hdr);
            addnl_hdrs.add(reason_hdr);
            addnl_hdrs.add(ct_hdr);

            replace_hdrs = new ArrayList();
            replace_hdrs.add(ub.getParent().getHeaderFactory()
                    .createContentTypeHeader("mycontenttype",
                            "mycontentsubtype"));

            assertTrue(b.respondToReinvite(siptrans_b, SipResponse.OK,
                    "ok reinvite last response", -1, b_orig_contact_uri,
                    "Original info", addnl_hdrs, replace_hdrs, "DooDahDay"));

            assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            while (a.getLastReceivedResponse().getStatusCode() == Response.TRYING)
            {
                assertTrue(a.waitReinviteResponse(siptrans_a, 2000));
            }

            // check response code
            response = a.getLastReceivedResponse();
            assertEquals(Response.OK, response.getStatusCode());
            assertEquals("ok reinvite last response", response
                    .getReasonPhrase());

            // check contact info
            assertEquals(ub.getContactInfo().getURI(), b_orig_contact_uri); // changed
            assertFalse(b_orig_contact_uri.equals(b_contact_no_lr));
            assertHeaderContains(response, ContactHeader.NAME, ";lr");
            assertHeaderContains(response, ContactHeader.NAME,
                    b_orig_contact_uri);
            assertHeaderContains(response, ContactHeader.NAME, "Original info");

            // check body
            assertHeaderPresent(response, ContentTypeHeader.NAME);
            ct_hdr = (ContentTypeHeader) response.getMessage().getHeader(
                    ContentTypeHeader.NAME);
            assertEquals("mycontenttype", ct_hdr.getContentType());
            assertEquals("mycontentsubtype", ct_hdr.getContentSubType());
            assertBodyContains(response, "DooD");

            // check additional headers
            assertHeaderContains(response, PriorityHeader.NAME,
                    PriorityHeader.NORMAL);
            assertHeaderContains(response, ReasonHeader.NAME, "42");

            // check override headers
            assertHeaderContains(response, ContentTypeHeader.NAME,
                    "mycontenttype/mycontentsubtype");

            // send ACK
            // with additional, replacement String headers, and body
            addnl_hdrs = new ArrayList(1);
            addnl_hdrs.add("Event: presence");

            replace_hdrs = new ArrayList(3);
            replace_hdrs.add("Max-Forwards: 29");
            replace_hdrs.add("Priority: Urgent");
            replace_hdrs.add("Reason: SIP; cause=44; text=\"dummy\"");

            assertTrue(a.sendReinviteOkAck(siptrans_a, "ack body", "mytype",
                    "mysubtype", addnl_hdrs, replace_hdrs));
            assertTrue(b.waitForAck(1000));
            SipRequest req_ack = b.getLastReceivedRequest();

            assertHeaderContains(req_ack, ReasonHeader.NAME, "dummy");
            assertHeaderContains(req_ack, MaxForwardsHeader.NAME, "29");
            assertHeaderContains(req_ack, PriorityHeader.NAME, "gent");
            assertHeaderContains(req_ack, EventHeader.NAME, "presence");
            assertHeaderContains(req_ack, ContentTypeHeader.NAME, "mysubtype");
            assertBodyContains(req_ack, "ack body");

            Thread.sleep(100); //

            // done, finish up
            b.listenForDisconnect();
            Thread.sleep(100);

            a.disconnect();
            assertLastOperationSuccess("a disc - " + a.format(), a);

            b.waitForDisconnect(5000);
            assertLastOperationSuccess("b wait disc - " + b.format(), b);

            b.respondToDisconnect();

            Thread.sleep(100);
            ub.dispose();

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }

    }

    // this method tests cancel from a to b
    public void testCancel()
    {
        SipStack.trace("testCancel");

        ua.addUpdateCredential(new Credential(properties
                .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
        ua.register(null, 3600);
        assertLastOperationSuccess(
                "Caller registration using pre-set credentials failed - "
                        + ua.format(), ua);

        try
        {
            SipPhone ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, "sip:becky@"
                            + properties.getProperty("sipunit.test.domain"));
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 9600);
            assertLastOperationSuccess(
                    "Callee registration using pre-set credentials failed - "
                            + ub.format(), ub);

            SipCall b = ub.createSipCall();
            b.listenForIncomingCall();
            Thread.sleep(100);

            SipCall a = ua.makeCall("sip:becky@"
                    + properties.getProperty("sipunit.test.domain"), null);
            assertLastOperationSuccess(ua.format(), ua);

            assertTrue(b.waitForIncomingCall(5000));
            b.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
            assertLastOperationSuccess("b send RINGING - " + b.format(), b);
            Thread.sleep(200);
            assertResponseReceived(SipResponse.RINGING, a);
            Thread.sleep(300);

            // Initiate the Cancel
            b.listenForCancel();
            Thread.sleep(500);
            SipTransaction cancel = a.sendCancel();
            assertNotNull(cancel);

            // check b
            SipTransaction trans1 = b.waitForCancel(5000);
            assertNotNull(trans1);
            assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, b);
            assertTrue(b.respondToCancel(trans1, 200, "0K", -1));

            // check a - TODO debug
            // a.waitForCancelResponse(cancel, 5000);
            // assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, a);
        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

}