/*
 * Created on July 29, 2009
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
package org.cafesip.sipunit.test.proxywithauth;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNoSubscriptionErrors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.address.SipURI;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.PresenceDeviceInfo;
import org.cafesip.sipunit.PresenceSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This class tests authentication challenges for a SipUnit presence subscriber.
 * Tests in this class require a proxy that will authenticate using DIGEST and
 * that can receive and respond to SUBSCRIBE messages and send NOTIFY messages.
 * That is, a proxy server that supports Type II presence. The focus of these
 * tests are on the subscriber side.
 * 
 * Start up the SipExchange server before running this test, and have the URIs
 * used here provisioned at the server, all with password a1b2c3d4 - these URIs
 * include: sip:becky@<property "sipunit.test.domain" below>, sip:amit@<property
 * "sipunit.test.domain" below>.
 * 
 * *** IMPORTANT *** Make sure the users have accepted each other as contacts
 * before running these tests (IE, sipex 'buddies' table has a record for each
 * subs with the other as the Contact and with Status=AUTHORIZED(=1)). Also,
 * first clear out any registrations at the server for these users.
 * 
 * @author Becky McElroy
 * 
 */
public class TestPresenceWithSipexProxy
{
    private SipStack sipStack;

    private SipPhone ua;

    private SipPhone ub;

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
        defaultProperties.setProperty("javax.sip.STACK_NAME",
                "testProxySubscriptions");
        defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testProxySubscriptions_debug.txt");
        defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testProxySubscriptions_log.txt");
        defaultProperties
                .setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        defaultProperties.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

        defaultProperties.setProperty("sipunit.trace", "true");
        defaultProperties.setProperty("sipunit.test.port", "5093");
        defaultProperties.setProperty("sipunit.test.protocol", "udp");

        defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
        defaultProperties.setProperty("sipunit.proxy.host", "192.168.112.1");
        defaultProperties.setProperty("sipunit.proxy.port", "5060");
    }

    private Properties properties = new Properties(defaultProperties);

    public TestPresenceWithSipexProxy()
    {
        properties.putAll(System.getProperties());

        try
        {
            myPort = Integer.parseInt(properties
                    .getProperty("sipunit.test.port"));
        }
        catch (NumberFormatException e)
        {
            myPort = 5093;
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

    @Before
    public void setUp() throws Exception
    {
        try
        {
            sipStack = new SipStack(testProtocol, myPort, properties);
            SipStack.setTraceEnabled(properties.getProperty("sipunit.trace")
                    .equalsIgnoreCase("true")
                    || properties.getProperty("sipunit.trace")
                            .equalsIgnoreCase("on"));
            SipStack.trace("Properties: " + properties.toString());
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

            // register with the server
            ua.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "amit", "a1b2c3d4"));
            ua.register(null, 3600);
            assertLastOperationSuccess(
                    "Caller registration using pre-set credentials failed - "
                            + ua.format(), ua);
        }
        catch (Exception ex)
        {
            fail("Exception creating SipPhone: " + ex.getClass().getName()
                    + ": " + ex.getMessage());
            throw ex;
        }

        ub = null;
    }

    @After
    public void tearDown() throws Exception
    {
        ua.dispose();

        if (ub != null)
        {
            ub.unregister(ub.getContactInfo().getURI(), 200);
            ub.dispose();
        }

        sipStack.dispose();
    }

    @Test
    public void testBasicPresence()
    {
        String buddy = "sip:becky@"
                + properties.getProperty("sipunit.test.domain"); // I am amit

        try
        {
            assertEquals(0, ua.getBuddyList().size()); // my list empty

            // ********** I. Add the buddy to the buddy list - start
            // subscription

            PresenceSubscriber s = ua.addBuddy(buddy, 1000);

            // check the return info
            assertNotNull(s);
            assertEquals(1, ua.getBuddyList().size());
            assertEquals(0, ua.getRetiredBuddies().size());
            assertEquals(buddy, s.getTargetUri());
            assertNotNull(ua.getBuddyInfo(buddy)); // call anytime to get
            // Subscription
            assertEquals(s.getTargetUri(), ua.getBuddyInfo(buddy)
                    .getTargetUri());
            // assertFalse(s.isSubscriptionPending());
            // assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionTerminated()); // call anytime
            assertEquals(SipResponse.PROXY_AUTHENTICATION_REQUIRED, s
                    .getReturnCode());
            ResponseEvent resp_event = s.getCurrentResponse();
            Response response = resp_event.getResponse();
            assertEquals(response.toString(), s.getLastReceivedResponse() // call
                    // anytime
                    .getMessage().toString());
            ArrayList<SipResponse> received_responses = s
                    .getAllReceivedResponses(); // call
            // anytime
            assertEquals(1, received_responses.size());
            assertEquals(response.toString(), received_responses.get(0)
                    .toString());

            // process the received response
            boolean status = s.processResponse(1000);
            assertTrue(s.format(), status);

            // check the response processing results
            assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertFalse(s.isSubscriptionTerminated());
            assertNull(s.getTerminationReason());
            assertTrue(s.getTimeLeft() <= 3600);
            response = (Response) s.getLastReceivedResponse().getMessage();
            assertEquals(3600, response.getExpires().getExpires());

            // wait for a NOTIFY
            RequestEvent reqevent = s.waitNotify(10000);
            assertNotNull(reqevent);
            assertNoSubscriptionErrors(s);

            // examine the request object
            Request request = reqevent.getRequest();
            assertEquals(Request.NOTIFY, request.getMethod());
            assertTrue(((SubscriptionStateHeader) request
                    .getHeader(SubscriptionStateHeader.NAME)).getExpires() <= 3600
                    && ((SubscriptionStateHeader) request
                            .getHeader(SubscriptionStateHeader.NAME))
                            .getExpires() >= 3595);
            ArrayList<SipRequest> received_requests = s
                    .getAllReceivedRequests();
            assertEquals(1, received_requests.size());
            SipRequest req = s.getLastReceivedRequest();
            assertNotNull(req);
            assertTrue(req.isNotify());
            assertFalse(req.isSubscribe());
            assertEquals((received_requests.get(0)).getMessage().toString(),
                    request.toString());
            assertEquals(received_requests.get(0).toString(), req.toString());

            // process the NOTIFY
            response = s.processNotify(reqevent);
            assertNotNull(response);

            // check the processing results
            assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertFalse(s.isSubscriptionTerminated());
            assertNull(s.getTerminationReason());
            assertTrue(s.getTimeLeft() <= 3600);
            assertEquals(SipResponse.OK, s.getReturnCode());

            // check the response that was created
            assertEquals(SipResponse.OK, response.getStatusCode());
            assertTrue(response.getReasonPhrase().equals("OK"));

            // check PRESENCE info - devices/tuples
            // -----------------------------------------------
            HashMap<String, PresenceDeviceInfo> devices = s
                    .getPresenceDevices();
            assertEquals(1, devices.size());
            PresenceDeviceInfo dev = devices.get("1");
            assertNotNull(dev);
            assertEquals("closed", dev.getBasicStatus());
            assertEquals(-1.0, dev.getContactPriority(), 0.001);
            assertNull(dev.getContactURI());
            assertEquals(0, dev.getDeviceExtensions().size());
            assertEquals(0, dev.getDeviceNotes().size());
            assertEquals("1", dev.getId());
            assertEquals(0, dev.getStatusExtensions().size());
            assertNull(dev.getTimestamp());

            // check PRESENCE info - top-level extensions
            // -----------------------------------------------
            assertEquals(0, s.getPresenceExtensions().size());

            // check PRESENCE info - top-level notes
            // -----------------------------------------------
            assertEquals(0, s.getPresenceNotes().size());

            // reply to the NOTIFY
            assertTrue(s.replyToNotify(reqevent, response));
            Thread.sleep(200);

            // ******* II. Log the buddy in, check status change

            ub = sipStack.createSipPhone(properties
                    .getProperty("sipunit.proxy.host"), testProtocol,
                    proxyPort, buddy);
            ub.addUpdateCredential(new Credential(properties
                    .getProperty("sipunit.test.domain"), "becky", "a1b2c3d4"));
            ub.register(null, 3600);
            assertLastOperationSuccess(
                    "Caller registration using pre-set credentials failed - "
                            + ub.format(), ub);

            // get the NOTIFY
            reqevent = s.waitNotify(10000);
            assertNotNull(s.format(), reqevent);
            assertNoSubscriptionErrors(s);

            // examine the request object
            request = reqevent.getRequest();
            assertEquals(Request.NOTIFY, request.getMethod());
            assertTrue(((SubscriptionStateHeader) request
                    .getHeader(SubscriptionStateHeader.NAME)).getExpires() > 0);
            received_requests = s.getAllReceivedRequests();
            assertEquals(2, received_requests.size());
            req = s.getLastReceivedRequest();
            assertNotNull(req);
            assertTrue(req.isNotify());
            assertFalse(req.isSubscribe());

            // process the NOTIFY
            response = s.processNotify(reqevent);
            assertNotNull(response);

            // check the processing results
            assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertFalse(s.isSubscriptionTerminated());
            assertNull(s.getTerminationReason());

            // check the response that was created
            assertEquals(SipResponse.OK, response.getStatusCode());
            assertTrue(response.getReasonPhrase().equals("OK"));

            // check PRESENCE info - devices/tuples
            // -----------------------------------------------
            devices = s.getPresenceDevices();
            assertEquals(1, devices.size());
            dev = devices.get("1");
            assertNotNull(dev);
            assertEquals("open", dev.getBasicStatus());
            assertEquals(-1.0, dev.getContactPriority(), 0.001);
            assertNotNull(dev.getContactURI());
            SipURI ubURI = (SipURI) ub.getContactInfo().getContactHeader()
                    .getAddress().getURI();
            String devURI = dev.getContactURI();
            assertTrue(devURI.indexOf(ubURI.getScheme()) != -1);
            assertTrue(devURI.indexOf(ubURI.getHost()) != -1);
            assertTrue(devURI.indexOf(String.valueOf(ubURI.getPort())) != -1);
            assertTrue(devURI.indexOf(ubURI.getTransportParam()) != -1);
            assertTrue(devURI.indexOf(ubURI.getUser()) != -1);
            assertTrue(devURI.indexOf("lr") != -1);
            assertEquals(0, dev.getDeviceExtensions().size());
            assertEquals(0, dev.getDeviceNotes().size());
            assertEquals("1", dev.getId());
            assertEquals(0, dev.getStatusExtensions().size());
            assertNull(dev.getTimestamp());

            // check PRESENCE info - top-level extensions
            // -----------------------------------------------
            assertEquals(0, s.getPresenceExtensions().size());

            // check PRESENCE info - top-level notes
            // -----------------------------------------------
            assertEquals(0, s.getPresenceNotes().size());

            // reply to the NOTIFY
            assertTrue(s.replyToNotify(reqevent, response));

            assertEquals(1, ua.getBuddyList().size());
            assertEquals(0, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);

            // ******** III. Refresh subscription

            s = ua.getBuddyInfo(buddy);
            assertTrue(s.refreshBuddy(1790, 2000));

            // check the return info
            assertEquals(1, ua.getBuddyList().size());
            assertEquals(0, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);
            assertNotNull(ua.getBuddyInfo(buddy));
            assertEquals(s.getTargetUri(), ua.getBuddyInfo(buddy)
                    .getTargetUri());
            assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertFalse(s.isSubscriptionTerminated());
            assertEquals(SipResponse.OK, s.getReturnCode());
            resp_event = s.getCurrentResponse();
            response = resp_event.getResponse();
            assertEquals(1790, response.getExpires().getExpires());
            assertEquals(response.toString(), s.getLastReceivedResponse()
                    .getMessage().toString());
            received_responses = s.getAllReceivedResponses();
            assertEquals(3, received_responses.size());
            assertEquals(response.toString(), received_responses.get(2)
                    .toString());

            // process the received response
            assertTrue(s.processResponse(1000));

            // check the response processing results
            assertTrue(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertFalse(s.isSubscriptionTerminated());
            assertNull(s.getTerminationReason());
            int timeleft = s.getTimeLeft();
            assertTrue("Expected time left to be close to 1790, it was "
                    + timeleft, timeleft <= 1790 && timeleft >= 1700);
            assertEquals(1, ua.getBuddyList().size());
            assertEquals(0, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);

            // ********* IV. Log the buddy out, check status change

            ub.unregister(ub.getContactInfo().getURI(), 5000);
            Thread.sleep(500);

            // get the resulting NOTIFY
            reqevent = s.waitNotify(1000); // TODO - why this NOTIFY w/status
            // open? reply to it
            response = s.processNotify(reqevent);
            assertTrue(s.replyToNotify(reqevent, response));

            reqevent = s.waitNotify(1000);
            assertNotNull(reqevent);
            assertNoSubscriptionErrors(s);

            // process the NOTIFY
            response = s.processNotify(reqevent);
            assertNotNull(response);
            assertEquals(1, ua.getBuddyList().size());
            assertEquals(0, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);

            // check the processing results
            assertTrue(s.isSubscriptionActive());
            assertNull(s.getTerminationReason());
            assertTrue(s.getTimeLeft() <= timeleft);
            assertEquals(SipResponse.OK, s.getReturnCode()); // response code

            // check PRESENCE info - devices/tuples
            // -----------------------------------------------
            devices = s.getPresenceDevices();
            assertEquals(1, devices.size());
            dev = devices.get("1");
            assertNotNull(dev);
            assertEquals("closed", dev.getBasicStatus());
            assertEquals(-1.0, dev.getContactPriority(), 0.001);
            assertNull(dev.getContactURI());
            assertEquals(0, dev.getDeviceExtensions().size());
            assertEquals(0, dev.getDeviceNotes().size());
            assertEquals("1", dev.getId());
            assertEquals(0, dev.getStatusExtensions().size());
            assertNull(dev.getTimestamp());

            // check PRESENCE info - top-level extensions
            // -----------------------------------------------
            assertEquals(0, s.getPresenceExtensions().size());

            // check PRESENCE info - top-level notes
            // -----------------------------------------------
            assertEquals(0, s.getPresenceNotes().size());

            // reply to the NOTIFY
            assertTrue(s.replyToNotify(reqevent, response));

            // *********** V. Finally, unsubscribe (end subscription)

            // remove buddy from contacts list, terminating SUBSCRIBE sequence
            s = ua.getBuddyInfo(buddy);
            assertTrue(s.removeBuddy(2000));

            // check immediate impacts - buddy lists, subscription state
            assertEquals(0, ua.getBuddyList().size());
            assertEquals(1, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);
            assertNotNull(ua.getBuddyInfo(buddy)); // check buddy can still be
            // found
            assertEquals(s.getTargetUri(), ua.getBuddyInfo(buddy)
                    .getTargetUri());
            assertFalse(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertTrue(s.isSubscriptionTerminated());
            String reason = s.getTerminationReason();
            assertNotNull(reason);

            // check the SUBSCRIBE response code, process the response
            assertEquals(SipResponse.OK, s.getReturnCode());

            resp_event = s.getCurrentResponse();
            response = resp_event.getResponse(); // check out the response
            // details
            assertEquals("OK", response.getReasonPhrase());
            assertEquals(0, response.getExpires().getExpires());
            assertEquals(response.toString(), s.getLastReceivedResponse()
                    .getMessage().toString());
            received_responses = s.getAllReceivedResponses();
            assertEquals(4, received_responses.size());
            assertEquals(response.toString(), received_responses.get(3)
                    .toString());

            // process the received response
            assertTrue(s.processResponse(300));

            // check the response processing results
            assertFalse(s.isSubscriptionActive());
            assertFalse(s.isSubscriptionPending());
            assertTrue(s.isSubscriptionTerminated());
            assertEquals(reason, s.getTerminationReason());
            assertEquals(0, s.getTimeLeft());
            assertEquals(0, ua.getBuddyList().size());
            assertEquals(1, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);

            // get the NOTIFY
            reqevent = s.waitNotify(1000);
            assertNotNull(reqevent);
            assertNoSubscriptionErrors(s);

            // process the NOTIFY
            response = s.processNotify(reqevent);
            assertNotNull(response);

            assertEquals(0, ua.getBuddyList().size());
            assertEquals(1, ua.getRetiredBuddies().size());
            assertNoSubscriptionErrors(s);

            // check the processing results
            assertTrue(s.isSubscriptionTerminated());
            assertNotNull(s.getTerminationReason());
            assertEquals(0, s.getTimeLeft());
            assertEquals(SipResponse.OK, s.getReturnCode()); // response code

            // check PRESENCE info got updated w/last NOTIFY - devices/tuples
            // -----------------------------------------------
            devices = s.getPresenceDevices();
            assertEquals(1, devices.size());
            dev = devices.get("1");
            assertNotNull(dev);
            assertEquals("closed", dev.getBasicStatus());
            assertEquals(-1.0, dev.getContactPriority(), 0.001);
            assertNull(dev.getContactURI());
            assertEquals(0, dev.getDeviceExtensions().size());
            assertEquals(0, dev.getDeviceNotes().size());
            assertEquals("1", dev.getId());
            assertEquals(0, dev.getStatusExtensions().size());
            assertNull(dev.getTimestamp());

            // check PRESENCE info - top-level extensions
            // -----------------------------------------------
            assertEquals(0, s.getPresenceExtensions().size());

            // check PRESENCE info - top-level notes
            // -----------------------------------------------
            assertEquals(0, s.getPresenceNotes().size());

            // reply to the NOTIFY
            assertTrue(s.replyToNotify(reqevent, response));

        }
        catch (Exception e)
        {
            e.printStackTrace();
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }
}