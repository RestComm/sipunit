/*
 * Created on November 22, 2005
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
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.PresenceDeviceInfo;
import org.cafesip.sipunit.PresenceNote;
import org.cafesip.sipunit.PresenceNotifySender;
import org.cafesip.sipunit.PresenceSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.header.EventHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class tests SipUnit presence functionality when a proxy server is between the subscriber and
 * the notifier. Focus is on the "subscriber" side using PresenceNotifySender (test UA driver) as
 * the far end. All endpoint UAs can run on the localhost. This class contains a subset of tests
 * from TestPresenceNoProxy test class.
 * 
 * <p>
 * ****** THIS CLASS IS ON HOLD ******** Please run the TestPresenceNoProxy or the
 * TestPresenceWithSipexProxy class to see the SUBSCRIBE/NOTIFY handling for the time being.
 * 
 * <p>
 * Tests in this class require a proxy server that will authenticate using DIGEST and that supports
 * Type I presence aware clients - IE, one that will pass SUBSCRIBE, NOTIFY messages through to the
 * User Agents. You can use nist.gov's JAIN-SIP proxy for this test. I used the old one with the
 * original stack version - I had to make it set the lr param in the recordroute header it sends
 * out. Also, I had to make it do more than 20 server transactions. It didn't work with the latest
 * 1.2 sipstack so need to wait for the new version to be incorporated into the
 * jain-sip-presence-proxy before trying to test with it using the latest stack. Start up the proxy
 * before running this test, and have the URIs used here in the list of users at the proxy, all with
 * password a1b2c3d4 - these URIs include: sip:becky@nist.gov, sip:amit@nist.gov, sip:tom@nist.gov,
 * sip:vidya@nist.gov. By default, the proxy host is 127.0.0.1 and its listening port is 5060.
 * 
 * <p>
 * AFter a few test runs of this class, the JAIN-SIP proxy starts throwing TransactionUnavailable
 * exceptions (TODO, investigate further) - restarting the proxy clears things up.
 * 
 * @author Becky McElroy
 * 
 */
public class TestPresenceWithProxy {

  private static final Logger LOG = LoggerFactory.getLogger(TestPresenceWithProxy.class);

  private SipStack sipStack;

  private SipPhone ua;

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
    defaultProperties.setProperty("javax.sip.STACK_NAME", "testPresence");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testPresence_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testPresence_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5093");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");

    defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
    defaultProperties.setProperty("sipunit.proxy.host", "192.168.1.101");
    defaultProperties.setProperty("sipunit.proxy.port", "5060");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestPresenceWithProxy() {
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
   * Initialize the sipStack and a user agent for the test.
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
  public void testBasicSubscription() throws Exception {
    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain"); // I am amit

    assertEquals(0, ua.getBuddyList().size()); // my list empty

    // add the buddy to the buddy list - sends SUBSCRIBE, gets response
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
        && ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires() >= 3595);
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

    // log the buddy in
    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, buddy);
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ua.register(null, 3600);
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
    dev = devices.get("2");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    assertEquals(-1.0, dev.getContactPriority(), 0.001);
    assertNull(dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    assertEquals(0, dev.getDeviceNotes().size());
    assertEquals("2", dev.getId());
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

    // refresh buddy status - sends SUBSCRIBE, gets response

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

    // log the buddy out
    ub.unregister(ub.getContactInfo().getURI(), 10000);

    // get the resulting NOTIFY
    reqevent = sub.waitNotify(5000);
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
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertEquals(-1.0, dev.getContactPriority(), 0.001);
    assertNull(dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    assertEquals(0, dev.getDeviceNotes().size());
    assertEquals("3", dev.getId());
    assertEquals(0, dev.getStatusExtensions().size());
    assertNull(dev.getTimestamp());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    Thread.sleep(30);
    ub.dispose();
  }

  @Test
  public void testEndSubscription() throws Exception {
    // This method tests terminating a subscription from the client side
    // (by removing a buddy from the SipPhone buddy list).

    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain"); // URI of
    // buddy

    // test steps SEQUENCE:
    // 1) prepare the far end (I will use the PresenceNotifySender
    // utility class to simulate the Presence Server)
    // 2) establish an active subscription (SUBSCRIBE, NOTIFY)
    // 3) remove buddy from buddy list - sends SUBSCRIBE, gets response
    // 4) check the return code, process the received response
    // 5) tell far end to send a NOTIFY
    // 6) get the NOTIFY
    // 7) process the NOTIFY
    // check the processing results
    // check PRESENCE info - devices/tuples
    // check PRESENCE info - top-level extensions
    // check PRESENCE info - top-level notes
    // 8) reply to the NOTIFY

    // (1) prepare the far end - a presence server and a buddy somewhere
    // create the far end, register buddy w/server
    PresenceNotifySender pserver =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, buddy));
    boolean registered =
        pserver.register(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
            "a1b2c3d4"));
    assertTrue(pserver.getErrorMessage(), registered);

    // prepare far end to receive SUBSCRIBE within 2 sec, respond w/OK
    pserver.processSubscribe(2000, SipResponse.OK, "OK");

    // (2) establish a subscription, get the buddy in the buddy list
    PresenceSubscriber sub = ua.addBuddy(buddy, 1000);
    assertNotNull(sub);
    boolean status = sub.processResponse(1000);
    assertTrue(sub.format(), status);
    assertTrue(sub.isSubscriptionActive());

    // do the initial NOTIFY sequence - still establishing
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain")
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(pserver.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, true));
    Thread.sleep(100);
    RequestEvent reqevent = sub.waitNotify(1000); // client receives
    // notify
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);
    Response response = sub.processNotify(reqevent); // client processes
    // it
    assertNotNull(response); // this is the response that should be
    // sent
    // back
    assertTrue(sub.isSubscriptionActive());

    // verify upcated client PRESENCE info - still part of establishment
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    PresenceDeviceInfo dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertEquals(0, sub.getPresenceExtensions().size());
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
    Thread.sleep(200); // !IF YOU DON't HAVE THIS, THINGS WILL FAIL
    // BELOW.

    // verify the buddy lists look correct
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // (3) Now, end the subscription from our (client) side

    // prepare far end to receive SUBSCRIBE within 2 sec, reply with OK
    pserver.processSubscribe(2000, SipResponse.OK, "OK Ended");

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

    // (4) check the SUBSCRIBE response code, process the response
    assertEquals(SipResponse.OK, sub.getReturnCode());

    ResponseEvent respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse(); // check out the response
    // details
    assertEquals("OK Ended", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses();
    assertEquals(3, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(2).toString());

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

    // (5) tell far end to send a last NOTIFY - use different tuple info
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain")
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"> <tuple id=\"3\"> <status> <basic>open</basic> </status> </tuple> </presence>";
    assertTrue(pserver.sendNotify(SubscriptionStateHeader.TERMINATED, "done", notifyBody, 0, true));

    // (6) get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // (7) process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // check the processing results
    assertTrue(sub.isSubscriptionTerminated());
    assertNotNull(sub.getTerminationReason());
    assertFalse(reason.equals(sub.getTerminationReason())); // updated
    assertEquals(0, sub.getTimeLeft());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info got updated w/last NOTIFY - devices/tuples
    devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    assertEquals(-1.0, dev.getContactPriority(), 0.001);
    assertNull(dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    assertEquals(0, dev.getDeviceNotes().size());
    assertEquals("3", dev.getId());
    assertEquals(0, dev.getStatusExtensions().size());
    assertNull(dev.getTimestamp());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // (8) reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    Thread.sleep(30);
    pserver.dispose();
  }

  @Test
  public void testFetch() throws Exception {
    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain"); // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, buddy));
    boolean registered =
        ub.register(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
            "a1b2c3d4"));
    assertTrue(ub.getErrorMessage(), registered);

    // SEQUENCE OF EVENTS
    // prepare far end to receive SUBSCRIBE
    // do something with a buddy - sends SUBSCRIBE, gets response
    // check the return info
    // process the received response
    // check the response processing results
    // tell far end to send a NOTIFY
    // get the NOTIFY
    // process the NOTIFY
    // check the processing results
    // check PRESENCE info - devices/tuples
    // check PRESENCE info - top-level extensions
    // check PRESENCE info - top-level notes
    // reply to the NOTIFY

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK");
    Thread.sleep(500);

    // do something with a buddy - sends SUBSCRIBE, gets response
    PresenceSubscriber sub = ua.fetchPresenceInfo(buddy, 2000);

    // check the return info
    assertNotNull(sub);
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertEquals(buddy, sub.getTargetUri());
    assertNotNull(ua.getBuddyInfo(buddy));
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionActive());
    assertTrue(sub.isSubscriptionTerminated());

    assertTrue(sub.getReturnCode() == SipResponse.PROXY_AUTHENTICATION_REQUIRED
        || sub.getReturnCode() == SipResponse.UNAUTHORIZED);

    // process the received response and any remaining ones
    assertTrue(sub.processResponse(1000));

    assertEquals(SipResponse.OK, sub.getReturnCode());
    ResponseEvent respEvent = sub.getCurrentResponse();
    Response response = respEvent.getResponse();
    assertEquals("OK", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses();
    assertEquals(2, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(1).toString());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());
    assertTrue(sub.isSubscriptionTerminated());

    // tell far end to send a NOTIFY
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain")
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "fetch", notifyBody, 0, true));

    // get the NOTIFY
    RequestEvent reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
    assertTrue(devices.isEmpty());

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("fetch", sub.getTerminationReason());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info - devices/tuples
    devices = sub.getPresenceDevices();
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

    // check misc again
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNotNull(ua.getBuddyInfo(buddy));
    assertEquals(buddy, ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionActive());
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("fetch", sub.getTerminationReason());

    // do another fetch

    Thread.sleep(100);
    ub.processSubscribe(5000, SipResponse.OK, "OKay");
    Thread.sleep(500);

    sub = ua.fetchPresenceInfo(buddy, 2000);

    // check the return info
    assertNotNull(sub);
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertEquals(buddy, sub.getTargetUri());
    assertNotNull(ua.getBuddyInfo(buddy));
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionActive());
    assertTrue(sub.isSubscriptionTerminated());

    // process the received response(s)
    assertTrue(sub.processResponse(1000));

    assertEquals(SipResponse.OK, sub.getReturnCode());
    respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse();
    assertEquals("OKay", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    receivedResponses = sub.getAllReceivedResponses();
    assertEquals(2, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(1).toString());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());
    assertTrue(sub.isSubscriptionTerminated());

    // tell far end to send a NOTIFY
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain")
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>open</basic></status></tuple></presence>";
    boolean notifySent =
        ub.sendNotify(SubscriptionStateHeader.TERMINATED, "refetch", notifyBody, 0, true);
    assertTrue(ub.getErrorMessage(), notifySent);

    // get the NOTIFY
    reqevent = sub.waitNotify(5000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("refetch", sub.getTerminationReason());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info - devices/tuples
    devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
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

    // check misc again
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNotNull(ua.getBuddyInfo(buddy));
    assertEquals(buddy, ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionActive());
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("refetch", sub.getTerminationReason());

    Thread.sleep(30);
    ub.dispose();
  }

  @Test
  public void testNotifyPresenceDataDetail() throws Exception {
    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain"); // I am amit

    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, buddy));
    boolean registered =
        ub.register(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
            "a1b2c3d4"));
    assertTrue(ub.getErrorMessage(), registered);

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OKee");
    Thread.sleep(500);

    // do something with a buddy - sends SUBSCRIBE, gets fist response
    PresenceSubscriber sub = ua.addBuddy(buddy, 2000);

    // check initial success
    assertNotNull(sub);

    // process the received response and any remaining ones for the
    // transaction
    assertTrue(sub.processResponse(1000));

    // check final result of the SUBSCRIBE operation
    assertEquals(SipResponse.OK, sub.getReturnCode());

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertTrue(sub.getTimeLeft() <= 3600);

    // (1) send notify with everything possible

    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain")
            + "\">"
            + " <tuple id=\"bs35r9\">"
            + " <status><basic>open</basic>"
            // here + " status extention 1"
            // here + " status extension 2"
            + " </status>"
            // here + " device(tuple) extension 1"
            // here + " device extension 2"
            + " <contact priority=\"0.8\">im:someone@mobilecarrier.net</contact>"
            + " <note xml:lang=\"en\">Don't Disturb Please!</note>"
            + " <note xml:lang=\"fr\">Ne derangez pas, s'il vous plait</note>"
            + " <timestamp>2001-10-27T16:49:29Z</timestamp>"
            + " </tuple>"
            + " <tuple id=\"doodah\">"
            + " <status><basic>closed</basic>"
            + " <xs:string>Status extension 1</xs:string>"
            + " </status>"
            + " <contact priority=\"1.0\">me@mobilecarrier.net</contact>"
            + " <note xml:lang=\"fr\">Ne derangez pas, s'il vous plait</note>"
            + " <timestamp>2002-10-27T16:48:29Z</timestamp>"
            + " </tuple>"
            + " <tuple id=\"eg92n8\">"
            + " <status>"
            + " </status>"
            + " </tuple>"
            + " <note>I'll be in Tokyo next week</note>"
            + " <note xml:lang=\"en\">I'll be in Tahiti after that</note>"
            // here + " top level extension 1"
            // here + " top level extension 2"
            // here + mustUnderstand
            + " </presence>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, true));

    // get the NOTIFY
    RequestEvent reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
    Response response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response
    // code

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    // check PRESENCE info - devices
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
    assertEquals(3, devices.size());
    assertNull(devices.get("dummy"));

    PresenceDeviceInfo dev = devices.get("bs35r9");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    List<Object> statusext = dev.getStatusExtensions();
    assertEquals(0, statusext.size());
    assertEquals(0.8, dev.getContactPriority(), 0.001);
    assertEquals("im:someone@mobilecarrier.net", dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    List<PresenceNote> notes = dev.getDeviceNotes();
    assertEquals(2, notes.size());
    assertEquals("Don't Disturb Please!", ((PresenceNote) notes.get(0)).getValue());
    assertEquals("en", ((PresenceNote) notes.get(0)).getLanguage());
    assertEquals("Ne derangez pas, s'il vous plait", ((PresenceNote) notes.get(1)).getValue());
    assertEquals("fr", ((PresenceNote) notes.get(1)).getLanguage());
    assertEquals("bs35r9", dev.getId());
    Calendar timestamp = dev.getTimestamp();
    assertEquals(2001, timestamp.get(Calendar.YEAR));
    assertEquals(49, timestamp.get(Calendar.MINUTE));

    dev = devices.get("doodah");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    statusext = dev.getStatusExtensions();
    assertEquals(0, statusext.size());
    assertEquals(1.0, dev.getContactPriority(), 0.001);
    assertEquals("me@mobilecarrier.net", dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    notes = dev.getDeviceNotes();
    assertEquals(1, notes.size());
    assertEquals("Ne derangez pas, s'il vous plait", ((PresenceNote) notes.get(0)).getValue());
    assertEquals("fr", ((PresenceNote) notes.get(0)).getLanguage());
    assertEquals("doodah", dev.getId());
    timestamp = dev.getTimestamp();
    assertEquals(2002, timestamp.get(Calendar.YEAR));
    assertEquals(48, timestamp.get(Calendar.MINUTE));

    dev = devices.get("eg92n8");
    assertNotNull(dev);
    assertEquals(null, dev.getBasicStatus());
    assertEquals(0, dev.getStatusExtensions().size());
    assertEquals(-1.0, dev.getContactPriority(), 0.001);
    assertEquals(null, dev.getContactURI());
    assertEquals(0, dev.getDeviceExtensions().size());
    assertEquals(0, dev.getDeviceNotes().size());
    assertNotSame("bs35r9", dev.getId());
    assertEquals("eg92n8", dev.getId());
    assertNull(dev.getTimestamp());

    // check PRESENCE info - top-level notes
    assertEquals(2, sub.getPresenceNotes().size());
    PresenceNote note = (PresenceNote) sub.getPresenceNotes().get(0);
    assertEquals("I'll be in Tokyo next week", note.getValue());
    assertNull(note.getLanguage());
    note = (PresenceNote) sub.getPresenceNotes().get(1);
    assertEquals("I'll be in Tahiti after that", note.getValue());
    assertEquals("en", note.getLanguage());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());
    // check mustUnderstand

    // (2) send notify with minimal possible

    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\"" + " entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain") + "\">" + " </presence>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, true));

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    // check PRESENCE info - devices
    devices = sub.getPresenceDevices();
    assertEquals(0, devices.size());

    // check PRESENCE info - top-level notes
    assertEquals(0, sub.getPresenceNotes().size());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());

    // (3) send badly formed data

    assertNoSubscriptionErrors(sub);
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n " + " <presence entity=\"sip:becky@"
            + properties.getProperty("sipunit.test.domain") + "\""
            + " xmlns=\"urn:ietf:params:xml:ns:pidf\"> " + " <tuple id=\"1\"> "
            + " <status><basic>open</basic></status>" + " </tuple> " + " </presencee>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, true));

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY

    LOG.trace("The following validation FATAL_ERROR is SUPPOSED TO HAPPEN");
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertEquals(SipResponse.BAD_REQUEST, sub.getReturnCode());

    String err = (String) sub.getErrorMessage();
    assertTrue(err.indexOf("parsing error") != -1);
    devices = sub.getPresenceDevices();
    assertEquals(0, devices.size());
    assertEquals(0, sub.getEventErrors().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    Thread.sleep(30);
    ub.dispose();
  }

  @Test
  public void testStrayNotify() throws Exception {
    // with no matching Subscription
    String buddy = "sip:becky@" + properties.getProperty("sipunit.test.domain");
    String buddy2 = "sip:vidya@" + properties.getProperty("sipunit.test.domain");

    // create object to send a NOTIFY
    PresenceNotifySender sender =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, "sip:tom@"
                + properties.getProperty("sipunit.test.domain")));
    boolean registered =
        sender.register(new Credential(properties.getProperty("sipunit.test.domain"), "tom",
            "a1b2c3d4"));
    assertTrue(sender.getErrorMessage(), registered);

    // create and send NOTIFY out of the blue
    Request request =
        sipStack.getMessageFactory().createRequest(
            "NOTIFY sip:amit@" + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort
                + ";transport=udp SIP/2.0\n");
    String notifyBody =
        "<?xml version='1.0' encoding='UTF-8'?> " + " <presence entity='sip:anyone@"
            + properties.getProperty("sipunit.test.domain") + "' "
            + "xmlns='urn:ietf:params:xml:ns:pidf'>" + "<tuple id='1'>"
            + "<status><basic>closed</basic>" + "</status>" + "</tuple>" + "</presence>";

    sender.addNotifyHeaders(request, "amit", properties.getProperty("sipunit.test.domain"),
        SubscriptionStateHeader.TERMINATED, "late", notifyBody, 0);

    SipTransaction trans = sender.sendStatefulNotify(request, true);
    assertNotNull(sender.getErrorMessage(), trans);

    // get the response
    EventObject event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);

    if (event instanceof TimeoutEvent) {
      fail("Event Timeout received by far end while waiting for NOTIFY response");
    }

    assertTrue("Expected auth challenge", sender.needAuthorization((ResponseEvent) event));
    trans = sender.resendWithAuthorization((ResponseEvent) event);
    assertNotNull(sender.getErrorMessage(), trans);

    // get the next response
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);

    if (event instanceof TimeoutEvent) {
      fail("Event Timeout received by far end while waiting for NOTIFY response");
    }

    assertFalse("Didn't expect auth challenge", sender.needAuthorization((ResponseEvent) event));
    // should have a do-while loop here, handle multiple challenges

    Response response = ((ResponseEvent) event).getResponse();
    assertEquals("Should have gotten 481 response for stray NOTIFY",
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, response.getStatusCode());

    // repeat w/2 buddies using wrong presentity. Verify
    // presence event on both

    // set up the two buddies

    PresenceNotifySender buddy1sim =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, buddy));
    PresenceNotifySender buddy2sim =
        new PresenceNotifySender(sipStack.createSipPhone(
            properties.getProperty("sipunit.proxy.host"), testProtocol, proxyPort, buddy2));

    // register the buddies with the server
    registered =
        buddy1sim.register(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
            "a1b2c3d4"));
    assertTrue(buddy1sim.getErrorMessage(), registered);
    registered =
        buddy2sim.register(new Credential(properties.getProperty("sipunit.test.domain"), "vidya",
            "a1b2c3d4"));
    assertTrue(buddy2sim.getErrorMessage(), registered);

    buddy1sim.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    buddy2sim.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    Thread.sleep(500);
    PresenceSubscriber s1 = ua.addBuddy(buddy, 2000);
    PresenceSubscriber s2 = ua.addBuddy(buddy2, 2000);
    assertNotNull(s1);
    assertNotNull(s2);
    boolean status = s1.processResponse(1000);
    assertTrue(s1.format(), status);
    status = s2.processResponse(1000);
    assertTrue(s2.format(), status);
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertEquals(SipResponse.OK, s2.getReturnCode());

    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"" + buddy
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>" + "open"
            + "</basic></status></tuple></presence>";
    assertTrue(buddy1sim.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, true));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"" + buddy2
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"2\"><status><basic>" + "open"
            + "</basic></status></tuple></presence>";
    assertTrue(buddy2sim.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, true));

    RequestEvent reqevent1 = s1.waitNotify(1000); // wait for notify
    // on 1
    RequestEvent reqevent2 = s2.waitNotify(1000); // wait for notify
    // on 2

    response = s1.processNotify(reqevent1); // process
    // notify
    // on 1
    assertTrue(s1.replyToNotify(reqevent1, response)); // send reply

    response = s2.processNotify(reqevent2); // process notify on 2
    assertTrue(s2.replyToNotify(reqevent2, response)); // send reply

    assertNoSubscriptionErrors(s1);
    assertNoSubscriptionErrors(s2);

    // end setting up two buddies

    s1 = ua.getBuddyInfo(buddy);
    assertNotNull(s1);
    assertEquals(0, s1.getEventErrors().size());
    s2 = ua.getBuddyInfo(buddy2);
    assertNotNull(s2);
    assertEquals(0, s2.getEventErrors().size());

    request = (Request) request.clone();
    trans = sender.sendStatefulNotify(request, true); // resend last
    // notify
    assertNotNull(sender.getErrorMessage(), trans);

    // get the response
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);
    assertTrue("Expected challenge", sender.needAuthorization((ResponseEvent) event));
    trans = sender.resendWithAuthorization((ResponseEvent) event);
    assertNotNull(sender.getErrorMessage(), trans);
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);
    response = ((ResponseEvent) event).getResponse();
    assertEquals("Should have gotten 481 response for stray NOTIFY",
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, response.getStatusCode());

    // check presence errors on subscriptions - these won't be seen in
    // nist sip 1.2 because
    // the stack now sends the 481 automatically unless the automatic
    // dialog support flag is false
    // assertEquals(1, s1.getEventErrors().size());
    // assertEquals(1, s2.getEventErrors().size());
    // assertEquals(s1.getEventErrors().getFirst(), s2.getEventErrors()
    // .getFirst());
    // s1.clearEventErrors();
    // s2.clearEventErrors();

    // //////////////////////////////////////////////////////////
    // repeat w/2 buddies using correct presentity but wrong event ID.
    // Verify
    // presence event on both

    request =
        sipStack.getMessageFactory().createRequest(
            "NOTIFY sip:amit@" + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort
                + ";transport=udp SIP/2.0");
    notifyBody =
        "<?xml version='1.0' encoding='UTF-8'?> " + " <presence entity='" + buddy
            + "' xmlns='urn:ietf:params:xml:ns:pidf'><tuple id='1'>"
            + "<status><basic>closed</basic></status>" + "</tuple></presence>";

    sender.addNotifyHeaders(request, "amit", properties.getProperty("sipunit.test.domain"),
        SubscriptionStateHeader.ACTIVE, "late", notifyBody, 1000);

    EventHeader ehdr = (EventHeader) request.getHeader(EventHeader.NAME);
    ehdr.setEventId("unmatched-eventid");
    trans = sender.sendStatefulNotify(request, true);
    assertNotNull(sender.getErrorMessage(), trans);

    // get the response
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);
    assertTrue("Expected challenge", sender.needAuthorization((ResponseEvent) event));
    trans = sender.resendWithAuthorization((ResponseEvent) event);
    assertNotNull(sender.getErrorMessage(), trans);
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);
    response = ((ResponseEvent) event).getResponse();
    assertEquals("Should have gotten 481 response for stray NOTIFY",
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, response.getStatusCode());

    // check presence errors on subscriptions - these won't be seen in
    // nist sip 1.2 because
    // the stack now sends the 481 automatically unless the automatic
    // dialog support flag is false
    // assertEquals(1, s1.getEventErrors().size());
    // assertEquals(1, s2.getEventErrors().size());
    // assertEquals(s1.getEventErrors().getFirst(), s2.getEventErrors()
    // .getFirst());
    // s1.clearEventErrors();
    // s2.clearEventErrors();

    sender.dispose();
    buddy1sim.dispose();
    buddy2sim.dispose();
  }

  /*
   * private void template() { String buddy = "sip:becky@" +
   * properties.getProperty("sipunit.test.domain"); // I am amit
   * 
   * try { // create far end (presence server simulator, fictitious buddy) PresenceNotifySender ub =
   * new PresenceNotifySender(sipStack .createSipPhone(properties.getProperty("sipunit.proxy.host"),
   * testProtocol, proxyPort, buddy)); // SEQUENCE OF EVENTS // prepare far end to receive SUBSCRIBE
   * // do something with a buddy - sends SUBSCRIBE, gets response // check the return info //
   * process the received response // check the response processing results // tell far end to send
   * a NOTIFY // get the NOTIFY // process the NOTIFY // check the processing results // check
   * PRESENCE info - devices/tuples // check PRESENCE info - top-level extensions // check PRESENCE
   * info - top-level notes // reply to the NOTIFY // prepare far end to receive SUBSCRIBE
   * assertTrue(ub.processSubscribe(5000, SipResponse.OK, "OK")); // do something with a buddy -
   * sends SUBSCRIBE, gets response Subscription s = ua.addBuddy(buddy, 2000); // check the return
   * info assertNotNull(s); assertEquals(SipResponse.OK, s.getReturnCode()); // process the received
   * response assertTrue(s.processResponse(1000)); // check the response processing results
   * assertTrue(s.isSubscriptionActive()); assertTrue(s.getTimeLeft() <= 3600); // tell far end to
   * send a NOTIFY String notify_body = "<?xml version=\"1.0\"
   * encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@" +
   * properties.getProperty("sipunit.test.domain") +
   * "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>"
   * ; assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notify_body, 2400, true)); //
   * get the NOTIFY RequestEvent reqevent = s.waitNotify(500); assertNotNull(reqevent);
   * assertNoPresenceErrors(s); // process the NOTIFY Response response = s.processNotify(reqevent);
   * assertNotNull(response); // check the processing results assertTrue(s.isSubscriptionActive());
   * assertNull(s.getTerminationReason()); assertTrue(s.getTimeLeft() <= 2400);
   * assertEquals(SipResponse.OK, s.getReturnCode()); // response code // check PRESENCE info -
   * devices/tuples // ----------------------------------------------- HashMap devices =
   * s.getPresenceDevices(); assertEquals(1, devices.size()); PresenceDeviceInfo dev =
   * (PresenceDeviceInfo) devices.get("1"); assertNotNull(dev); assertEquals("closed",
   * dev.getBasicStatus()); assertEquals(-1.0, dev.getContactPriority(), 0.001);
   * assertNull(dev.getContactURI()); assertEquals(0, dev.getDeviceExtensions().size());
   * assertEquals(0, dev.getDeviceNotes().size()); assertEquals("1", dev.getId()); assertEquals(0,
   * dev.getStatusExtensions().size()); assertNull(dev.getTimestamp()); // check PRESENCE info -
   * top-level extensions // ----------------------------------------------- assertEquals(0,
   * s.getPresenceExtensions().size()); // check PRESENCE info - top-level notes //
   * ----------------------------------------------- assertEquals(0, s.getPresenceNotes().size());
   * // reply to the NOTIFY assertTrue(s.replyToNotify(reqevent, response)); } catch (Exception e) {
   * e.printStackTrace(); fail("Exception: " + e.getClass().getName() + ": " + e.getMessage()); } }
   */
}
