/*
 * Created on July 29, 2009
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

package org.cafesip.sipunit.test.proxywithauth;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNoSubscriptionErrors;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

/**
 * This class tests authentication challenges for a SipUnit presence subscriber. Tests in this class
 * require a proxy that will authenticate using DIGEST and that can receive and respond to SUBSCRIBE
 * messages and send NOTIFY messages. That is, a proxy server that supports Type II presence. The
 * focus of these tests are on the subscriber side.
 * 
 * Start up the SipExchange server before running this test, and have the URIs used here provisioned
 * at the server, all with password a1b2c3d4 - these URIs include: sip:becky@<property
 * "sipunit.test.domain" below>, sip:amit@<property "sipunit.test.domain" below>.
 * 
 * *** IMPORTANT *** Make sure the users have accepted each other as contacts before running these
 * tests (IE, sipex 'buddies' table has a record for each subs with the other as the Contact and
 * with Status=AUTHORIZED(=1)). Also, first clear out any registrations at the server for these
 * users.
 * 
 * @author Becky McElroy
 * 
 */
public class TestPresenceWithSipexProxy {

  private SipStack sipStack;

  private SipPhone ua;

  private SipPhone ub;

  private int proxyPort;

  private int myPort;

  private String testProtocol;

  private String myUrl;

  private static final Properties defaultProperties = new Properties();

  static {
    String host = null;
    try {
      host = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      host = "localhost";
    }

    defaultProperties.setProperty("javax.sip.IP_ADDRESS", host);
    defaultProperties.setProperty("javax.sip.STACK_NAME", "testProxySubscriptions");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG",
        "testProxySubscriptions_debug.txt");
    defaultProperties
        .setProperty("gov.nist.javax.sip.SERVER_LOG", "testProxySubscriptions_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5093");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");

    defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
    defaultProperties.setProperty("sipunit.proxy.host", "192.168.112.1");
    defaultProperties.setProperty("sipunit.proxy.port", "5060");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestPresenceWithSipexProxy() {
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 5093;
    }

    try {
      proxyPort = Integer.parseInt(properties.getProperty("sipunit.proxy.port"));
    } catch (NumberFormatException e) {
      proxyPort = 5060;
    }

    testProtocol = properties.getProperty("sipunit.test.protocol");
    myUrl = "sip:amit@" + properties.getProperty("sipunit.test.domain");

  }

  /**
   * Initialize the sipStack and both user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack = new SipStack(testProtocol, myPort, properties);

    ua =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, myUrl);

    // register with the server
    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);
    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    ub = null;
  }

  /**
   * Release the sipStack and both user agent for the test.
   */
  @After
  public void tearDown() throws Exception {
    ua.dispose();

    if (ub != null) {
      ub.unregister(ub.getContactInfo().getURI(), 200);
      ub.dispose();
    }
    awaitStackDispose(sipStack);
  }

  @Test
  public void testBasicPresence() throws Exception {
    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain"); // I am amit

    assertEquals(0, ua.getBuddyList().size()); // my list empty

    // ********** I. Add the buddy to the buddy list - start
    // subscription

    PresenceSubscriber sub = ua.addBuddy(buddy, 1000);

    // check the return info
    assertNotNull(sub);
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertEquals(buddy, sub.getTargetUri());
    assertNotNull(ua.getBuddyInfo(buddy)); // call anytime to get
    // Subscription
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    // assertFalse(s.isSubscriptionPending());
    // assertTrue(s.isSubscriptionActive());
    assertFalse(sub.isSubscriptionTerminated()); // call anytime
    assertEquals(SipResponse.PROXY_AUTHENTICATION_REQUIRED, sub.getReturnCode());
    ResponseEvent respEvent = sub.getCurrentResponse();
    Response response = respEvent.getResponse();
    assertEquals(response.toString(), sub.getLastReceivedResponse() // call
        // anytime
        .getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses(); // call
    // anytime
    assertEquals(1, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(0).toString());

    // process the received response
    boolean status = sub.processResponse(1000);
    assertTrue(sub.format(), status);

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= 3600);
    response = (Response) sub.getLastReceivedResponse().getMessage();
    assertEquals(3600, response.getExpires().getExpires());

    // wait for a NOTIFY
    RequestEvent reqevent = sub.waitNotify(10000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // examine the request object
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertTrue(((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
        .getExpires() <= 3600
        && ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
            .getExpires() >= 3595);
    ArrayList<SipRequest> receivedRequests = sub.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest req = sub.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());
    assertEquals((receivedRequests.get(0)).getMessage().toString(), request.toString());
    assertEquals(receivedRequests.get(0).toString(), req.toString());

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= 3600);
    assertEquals(SipResponse.OK, sub.getReturnCode());

    // check the response that was created
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(response.getReasonPhrase().equals("OK"));

    // check PRESENCE info - devices/tuples
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
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
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
    Thread.sleep(200);

    // ******* II. Log the buddy in, check status change

    ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, buddy);
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 3600);
    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ub.format(), ub);

    // get the NOTIFY
    reqevent = sub.waitNotify(10000);
    assertNotNull(sub.format(), reqevent);
    assertNoSubscriptionErrors(sub);

    // examine the request object
    request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertTrue(((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME))
        .getExpires() > 0);
    receivedRequests = sub.getAllReceivedRequests();
    assertEquals(2, receivedRequests.size());
    req = sub.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());

    // check the response that was created
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(response.getReasonPhrase().equals("OK"));

    // check PRESENCE info - devices/tuples
    devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    assertEquals(-1.0, dev.getContactPriority(), 0.001);
    assertNotNull(dev.getContactURI());
    SipURI ubUri = (SipURI) ub.getContactInfo().getContactHeader().getAddress().getURI();
    String devUri = dev.getContactURI();
    assertTrue(devUri.indexOf(ubUri.getScheme()) != -1);
    assertTrue(devUri.indexOf(ubUri.getHost()) != -1);
    assertTrue(devUri.indexOf(String.valueOf(ubUri.getPort())) != -1);
    assertTrue(devUri.indexOf(ubUri.getTransportParam()) != -1);
    assertTrue(devUri.indexOf(ubUri.getUser()) != -1);
    assertTrue(devUri.indexOf("lr") != -1);
    assertEquals(0, dev.getDeviceExtensions().size());
    assertEquals(0, dev.getDeviceNotes().size());
    assertEquals("1", dev.getId());
    assertEquals(0, dev.getStatusExtensions().size());
    assertNull(dev.getTimestamp());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // ******** III. Refresh subscription

    sub = ua.getBuddyInfo(buddy);
    assertTrue(sub.refreshBuddy(1790, 2000));

    // check the return info
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);
    assertNotNull(ua.getBuddyInfo(buddy));
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertEquals(SipResponse.OK, sub.getReturnCode());
    respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse();
    assertEquals(1790, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    receivedResponses = sub.getAllReceivedResponses();
    assertEquals(3, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(2).toString());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    int timeleft = sub.getTimeLeft();
    assertTrue("Expected time left to be close to 1790, it was " + timeleft, timeleft <= 1790
        && timeleft >= 1700);
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // ********* IV. Log the buddy out, check status change

    ub.unregister(ub.getContactInfo().getURI(), 5000);
    Thread.sleep(500);

    // get the resulting NOTIFY
    reqevent = sub.waitNotify(1000); // TODO - why this NOTIFY w/status
    // open? reply to it
    response = sub.processNotify(reqevent);
    assertTrue(sub.replyToNotify(reqevent, response));

    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= timeleft);
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info - devices/tuples
    devices = sub.getPresenceDevices();
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
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    // *********** V. Finally, unsubscribe (end subscription)

    // remove buddy from contacts list, terminating SUBSCRIBE sequence
    sub = ua.getBuddyInfo(buddy);
    assertTrue(sub.removeBuddy(2000));

    // check immediate impacts - buddy lists, subscription state
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);
    assertNotNull(ua.getBuddyInfo(buddy)); // check buddy can still be
    // found
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertTrue(sub.isSubscriptionTerminated());
    String reason = sub.getTerminationReason();
    assertNotNull(reason);

    // check the SUBSCRIBE response code, process the response
    assertEquals(SipResponse.OK, sub.getReturnCode());

    respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse(); // check out the response
    // details
    assertEquals("OK", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    receivedResponses = sub.getAllReceivedResponses();
    assertEquals(4, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(3).toString());

    // process the received response
    assertTrue(sub.processResponse(300));

    // check the response processing results
    assertFalse(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(reason, sub.getTerminationReason());
    assertEquals(0, sub.getTimeLeft());
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // check the processing results
    assertTrue(sub.isSubscriptionTerminated());
    assertNotNull(sub.getTerminationReason());
    assertEquals(0, sub.getTimeLeft());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info got updated w/last NOTIFY - devices/tuples
    devices = sub.getPresenceDevices();
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
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
  }
}
