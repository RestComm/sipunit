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

package org.cafesip.sipunit.test.noproxy;

import static org.cafesip.sipunit.SipAssert.assertNoSubscriptionErrors;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.cafesip.sipunit.PresenceDeviceInfo;
import org.cafesip.sipunit.PresenceNote;
import org.cafesip.sipunit.PresenceNotifySender;
import org.cafesip.sipunit.PresenceSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

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
 * This class tests SipUnit presence functionality. Focus is on the "subscriber" side using
 * PresenceNotifySender (test UA) as the far end.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server. Messaging between UACs is direct
 * (within the localhost).
 * 
 * @author Becky McElroy
 * 
 */
public class TestPresenceNoProxy {

  private SipStack sipStack;

  private SipPhone ua;

  private String host;

  private int myPort;

  private String testProtocol;

  private static final Properties defaultProperties = new Properties();

  static {
    defaultProperties.setProperty("javax.sip.STACK_NAME", "testNotify");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testNotify_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testNotify_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5061");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestPresenceNoProxy() {
    try {
      host = InetAddress.getLocalHost().getHostAddress();
    } catch (UnknownHostException e) {
      host = "localhost";
    }

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

  @Test
  public void testBasicSubscription() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub = new PresenceNotifySender(sipStack.createSipPhone(buddy));

    // tell far end to wait for a SUBSCRIBE, and when it
    // comes in, respond with OK
    ub.processSubscribe(5000, SipResponse.OK, "OK");

    assertEquals(0, ua.getBuddyList().size()); // my list empty

    // add the buddy to the buddy list - sends SUBSCRIBE, gets response
    PresenceSubscriber sub = ua.addBuddy(buddy, 2000);

    // check the return info
    assertNotNull(sub);
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertEquals(buddy, sub.getTargetUri());
    assertNotNull(ua.getBuddyInfo(buddy)); // call anytime to get
    // Subscription
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertTrue(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionTerminated()); // call anytime
    assertEquals(SipResponse.OK, sub.getReturnCode());
    ResponseEvent respEvent = sub.getCurrentResponse();
    Response response = respEvent.getResponse();
    assertEquals("OK", response.getReasonPhrase());
    assertEquals(3600, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse() // call
        // anytime
        .getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses(); // call
    // anytime
    assertEquals(1, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(0).toString());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= 3600);

    // tell far end to send a NOTIFY
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));

    // get the NOTIFY
    RequestEvent reqevent = sub.waitNotify(500);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // examine the request object
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(2400,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    ArrayList<SipRequest> receivedRequests = sub.getAllReceivedRequests();
    assertEquals(1, receivedRequests.size());
    SipRequest req = sub.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());
    assertEquals(((SipRequest) receivedRequests.get(0)).getMessage().toString(),
        request.toString());
    assertEquals(receivedRequests.get(0).toString(), req.toString());

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= 2400);
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

    // Next: send a NOTIFY w/basic status, id, and expiry change

    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"2\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 1800, false));

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // examine the request object
    request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(1800,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    receivedRequests = sub.getAllReceivedRequests();
    assertEquals(2, receivedRequests.size());
    req = sub.getLastReceivedRequest();
    assertNotNull(req);
    assertTrue(req.isNotify());
    assertFalse(req.isSubscribe());
    assertEquals(((SipRequest) receivedRequests.get(1)).getMessage().toString(),
        request.toString());
    assertEquals(receivedRequests.get(1).toString(), req.toString());

    // process the NOTIFY
    response = sub.processNotify(reqevent);
    assertNotNull(response);

    // check the processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    int timeleft = sub.getTimeLeft();
    assertTrue("Expected time left to be close to 1800, it was " + timeleft, timeleft <= 1800
        && timeleft >= 1795);
    assertEquals(SipResponse.OK, sub.getReturnCode());

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

    // Next, send another SUBSCRIBE via refreshBuddy()

    // tell far end to wait for a SUBSCRIBE, and when it
    // comes in, respond with OK
    ub.processSubscribe(5000, SipResponse.OK, "Okey dokey");

    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // refresh buddy status - sends SUBSCRIBE, gets response
    assertTrue(sub.refreshBuddy(3600, 1000));

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
    assertEquals("Okey dokey", response.getReasonPhrase());
    assertEquals(3600, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    receivedResponses = sub.getAllReceivedResponses();
    assertEquals(2, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(1).toString());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    timeleft = sub.getTimeLeft();
    assertTrue("Expected time left to be close to 3600, it was " + timeleft, timeleft <= 3600
        && timeleft >= 3595);
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // tell far end to send a NOTIFY
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"> <tuple id=\"3\"> <status> <basic>closed</basic> </status> </tuple> </presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, timeleft, false));

    // get the NOTIFY
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

    // Next, terminate the subscription from the far end

    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"3\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, SubscriptionStateHeader.TIMEOUT,
        notifyBody, 0, false));

    // get the NOTIFY
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
    assertFalse(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(SubscriptionStateHeader.TIMEOUT, sub.getTerminationReason());
    timeleft = sub.getTimeLeft(); // should reflect the time that was left
    assertTrue("Expected time left to be close to 3600, it was " + timeleft, timeleft <= 3600
        && timeleft >= 3590);
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info - devices/tuples
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

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
  }

  @Test
  public void testEndSubscription() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddy));

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK");

    // do the SUBSCRIBE sequence
    PresenceSubscriber sub = ua.addBuddy(buddy, 2000);
    assertNotNull(sub);
    assertTrue(sub.processResponse(1000));
    assertTrue(sub.isSubscriptionActive());

    // do the NOTIFY sequence
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));
    RequestEvent reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);
    Response response = sub.processNotify(reqevent);
    assertNotNull(response);
    assertTrue(sub.isSubscriptionActive());

    // check PRESENCE info
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    PresenceDeviceInfo dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertEquals(0, sub.getPresenceExtensions().size());
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    // check buddy lists
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // Now, end the subscription from our side

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK Ended");

    // remove buddy from contacts, do the SUBSCRIBE sequence
    assertTrue(sub.removeBuddy(300));
    assertFalse(sub.isRemovalComplete());

    // check immediate impacts - buddy lists, subscription state
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);
    assertNotNull(ua.getBuddyInfo(buddy)); // can still be found
    assertEquals(sub.getTargetUri(), ua.getBuddyInfo(buddy).getTargetUri());
    assertFalse(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionPending());
    assertTrue(sub.isSubscriptionTerminated());
    String reason = sub.getTerminationReason();
    assertNotNull(reason);

    // check the return info
    assertEquals(SipResponse.OK, sub.getReturnCode());
    ResponseEvent respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse();
    assertEquals("OK Ended", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses();
    assertEquals(2, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(1).toString());

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

    // tell far end to send a NOTIFY
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"> <tuple id=\"3\"> <status> <basic>open</basic> </status> </tuple> </presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "done", notifyBody, 0, false));

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
    assertFalse(reason.equals(sub.getTerminationReason())); // updated
    assertEquals(0, sub.getTimeLeft());
    assertEquals(SipResponse.OK, sub.getReturnCode()); // response code

    // check PRESENCE info - devices/tuples
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

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
  }

  @Test
  public void testFetch() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ns =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddy));

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
    ns.processSubscribe(5000, SipResponse.OK, "OK");

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
    assertEquals(SipResponse.OK, sub.getReturnCode());
    ResponseEvent respEvent = sub.getCurrentResponse();
    Response response = respEvent.getResponse();
    assertEquals("OK", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(0).toString());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // verify results still correct
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());

    // tell far end to send a NOTIFY
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ns.sendNotify(SubscriptionStateHeader.TERMINATED, "fetch", notifyBody, 0, false));

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

    ns.processSubscribe(5000, SipResponse.OK, "OKay");

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
    assertEquals(SipResponse.OK, sub.getReturnCode());
    respEvent = sub.getCurrentResponse();
    response = respEvent.getResponse();
    assertEquals("OKay", response.getReasonPhrase());
    assertEquals(0, response.getExpires().getExpires());
    assertEquals(response.toString(), sub.getLastReceivedResponse().getMessage().toString());
    receivedResponses = sub.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(response.toString(), receivedResponses.get(0).toString());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // verify results still correct
    assertTrue(sub.isSubscriptionTerminated());
    assertEquals(0, sub.getTimeLeft());
    assertEquals("Fetch", sub.getTerminationReason());

    // tell far end to send a NOTIFY
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>open</basic></status></tuple></presence>";
    boolean notifySent =
        ns.sendNotify(SubscriptionStateHeader.TERMINATED, "refetch", notifyBody, 0, false);
    assertTrue(ns.getErrorMessage(), notifySent);

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
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
  }

  @Test
  public void testResponseRequestEvent() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddy));

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OK");

    // do something with a buddy - sends SUBSCRIBE, gets response
    PresenceSubscriber sub = ua.fetchPresenceInfo(buddy, 2000);

    // check the return info
    assertNotNull(sub);
    ResponseEvent respEvent = sub.getCurrentResponse();
    ArrayList<SipResponse> receivedResponses = sub.getAllReceivedResponses();
    assertEquals(1, receivedResponses.size());
    assertEquals(respEvent.getClientTransaction(), ((SipResponse) receivedResponses.get(0))
        .getResponseEvent().getClientTransaction());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // tell far end to send a NOTIFY
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.TERMINATED, "fetch", notifyBody, 0, false));

    // get the NOTIFY
    RequestEvent reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertEquals(reqevent, sub.getLastReceivedRequest().getRequestEvent());
    assertEquals(respEvent.getDialog(), reqevent.getDialog());

    // process the NOTIFY
    Response response = sub.processNotify(reqevent);
    assertTrue(sub.replyToNotify(reqevent, response));
  }

  @Test
  public void testBadNotify() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub = new PresenceNotifySender(sipStack.createSipPhone(buddy));

    // tell far end to wait for a SUBSCRIBE, and when it
    // comes in, respond with OK
    ub.processSubscribe(5000, SipResponse.OK, "OK");

    // add the buddy to the buddy list - sends SUBSCRIBE, gets response
    PresenceSubscriber sub = ua.addBuddy(buddy, 2000);
    assertNotNull(sub);
    assertTrue(sub.processResponse(1000));
    assertTrue(sub.isSubscriptionActive());

    // tell far end to send a bad NOTIFY
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.PENDING, null, notifyBody, 3800, false)); // expiry
                                                                                                // too
                                                                                                // big

    // wait for the NOTIFY
    RequestEvent reqevent = sub.waitNotify(2000);
    assertNotNull(reqevent);

    // examine the request object - sequence diagram - start here
    // confirm detail
    Request request = reqevent.getRequest();
    assertEquals(Request.NOTIFY, request.getMethod());
    assertEquals(3800,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    ArrayList<SipRequest> receivedRequests = sub.getAllReceivedRequests();
    assertTrue(receivedRequests.size() > 0);
    assertEquals(((SipRequest) receivedRequests.get(0)).getMessage().toString(),
        request.toString());

    // process the NOTIFY
    Response response = sub.processNotify(reqevent);

    // check the notify processing results
    assertFalse(sub.isSubscriptionPending());
    assertTrue(sub.isSubscriptionActive());
    assertFalse(sub.isSubscriptionTerminated());
    assertNull(sub.getTerminationReason());
    assertTrue(sub.getTimeLeft() <= 3600); // expiry > what we sent
    assertEquals(SipResponse.BAD_REQUEST, sub.getReturnCode());

    // check the response that was created
    assertEquals(SipResponse.BAD_REQUEST, response.getStatusCode());
    assertFalse(response.getReasonPhrase().equals("OK"));

    // check presence info - shouldn't have been processed
    assertEquals(0, sub.getPresenceDevices().size());
    assertEquals(0, sub.getPresenceExtensions().size());
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));
  }

  @Test
  public void testErrors() throws Exception {
    PresenceSubscriber sub = new PresenceSubscriber("sip:ddd@aaa.bbb", ua);
    assertFalse(sub.refreshBuddy(100));
    assertEquals(SipSession.INVALID_ARGUMENT, sub.getReturnCode());

    assertFalse(sub.removeBuddy(100));
    assertEquals(SipSession.INVALID_ARGUMENT, sub.getReturnCode());

    // test wrong event ID -- TODO add more correct event ID tests

    String buddy = "sip:becky@cafesip.org"; // I am amit

    // create far end (presence server simulator, fictitious buddy)
    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddy));

    // prepare far end to receive SUBSCRIBE - TODO non-OK responses
    ub.processSubscribe(5000, SipResponse.OK, "OK");

    // do the SUBSCRIBE sequence
    sub = ua.addBuddy(buddy, 2300, "myevent-id", 2000);
    assertNotNull(sub);
    ResponseEvent resp = sub.getCurrentResponse();
    assertEquals("myevent-id",
        ((EventHeader) (resp.getResponse().getHeader(EventHeader.NAME))).getEventId());
    assertTrue(sub.processResponse(1000));
    assertTrue(sub.isSubscriptionActive());
    assertTrue(sub.getTimeLeft() <= 2300);

    // do the NOTIFY sequence, send the right event ID
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 1200, false));

    RequestEvent reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);
    Response response = sub.processNotify(reqevent);
    assertNotNull(response);
    assertTrue(sub.isSubscriptionActive());
    assertTrue(sub.getTimeLeft() <= 1200);

    // check PRESENCE info
    HashMap<String, PresenceDeviceInfo> devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    PresenceDeviceInfo dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertEquals(0, sub.getPresenceExtensions().size());
    assertEquals(0, sub.getPresenceNotes().size());

    // reply to the NOTIFY
    assertTrue(sub.replyToNotify(reqevent, response));

    // check buddy lists
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNoSubscriptionErrors(sub);

    // do the NOTIFY sequence, send the wrong event ID
    EventHeader eventHdr = sipStack.getHeaderFactory().createEventHeader("presence");
    eventHdr.setEventId("bad-id");
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"11\"><status><basic>open</basic></status></tuple></presence>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 1000, eventHdr,
        null, null, null, false));

    reqevent = sub.waitNotify(1000);
    assertNull(reqevent); // event id used to find the Subscription, s
    // won't get it. should have sent 481
    assertTrue(sub.getEventErrors().size() > 0);
    assertTrue(((String) sub.getEventErrors().get(0)).indexOf("orphan") != -1);

    // check PRESENCE info unchanged
    devices = sub.getPresenceDevices();
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertEquals(0, sub.getPresenceExtensions().size());
    assertEquals(0, sub.getPresenceNotes().size());

    // check buddy lists
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    sub.clearEventErrors();
    assertNoSubscriptionErrors(sub);
  }

  @Test
  public void testNotifyPresenceDataDetail() throws Exception {
    String buddy = "sip:becky@cafesip.org"; // I am amit

    PresenceNotifySender ub =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddy));

    // prepare far end to receive SUBSCRIBE
    ub.processSubscribe(5000, SipResponse.OK, "OKee");

    // do something with a buddy - sends SUBSCRIBE, gets response
    PresenceSubscriber sub = ua.addBuddy(buddy, 2000);

    // check the return info
    assertNotNull(sub);
    assertEquals(SipResponse.OK, sub.getReturnCode());

    // process the received response
    assertTrue(sub.processResponse(1000));

    // check the response processing results
    assertTrue(sub.isSubscriptionActive());
    assertTrue(sub.getTimeLeft() <= 3600);

    // (1) send notify with everything possible

    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\" xmlns:xs=\"http://www.w3.org/2001/XMLSchema\" entity=\"sip:becky@cafesip.org\">"
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
            + " <timestamp>2001-10-27T16:49:29Z</timestamp>" + " </tuple>"
            + " <tuple id=\"doodah\">" + " <status><basic>closed</basic>"
            + " <xs:string>Status extension 1</xs:string>" + " </status>"
            + " <contact priority=\"1.0\">me@mobilecarrier.net</contact>"
            + " <note xml:lang=\"fr\">Ne derangez pas, s'il vous plait</note>"
            + " <timestamp>2002-10-27T16:48:29Z</timestamp>" + " </tuple>"
            + " <tuple id=\"eg92n8\">" + " <status>" + " </status>" + " </tuple>"
            + " <note>I'll be in Tokyo next week</note>"
            + " <note xml:lang=\"en\">I'll be in Tahiti after that</note>"
            // here + " top level extension 1"
            // here + " top level extension 2"
            // here + mustUnderstand
            + " </presence>";
    // TODO figure out why can't make jaxb create objects for "xs:any"
    // extension items ("here" above - and add assert statements for
    // each)
    // see comment in conf/presence-pidf.xsd
    // also check out "Mapping to DOM" section of:
    // http://java.sun.com/webservices/docs/1.4/jaxb/vendorCustomizations.html#dom

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, false));

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
    assertEquals("Don't Disturb Please!", (notes.get(0)).getValue());
    assertEquals("en", (notes.get(0)).getLanguage());
    assertEquals("Ne derangez pas, s'il vous plait", (notes.get(1)).getValue());
    assertEquals("fr", (notes.get(1)).getLanguage());
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
    assertEquals("Ne derangez pas, s'il vous plait", (notes.get(0)).getValue());
    assertEquals("fr", (notes.get(0)).getLanguage());
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
    PresenceNote note = sub.getPresenceNotes().get(0);
    assertEquals("I'll be in Tokyo next week", note.getValue());
    assertNull(note.getLanguage());
    note = sub.getPresenceNotes().get(1);
    assertEquals("I'll be in Tahiti after that", note.getValue());
    assertEquals("en", note.getLanguage());

    // check PRESENCE info - top-level extensions
    assertEquals(0, sub.getPresenceExtensions().size());
    // check mustUnderstand // TODO

    // (2) send notify with minimal possible

    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<presence xmlns=\"urn:ietf:params:xml:ns:pidf\""
            + " entity=\"sip:becky@cafesip.org\">" + " </presence>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, false));

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
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n "
            + " <presence entity=\"sip:becky@cafesip.org\""
            + " xmlns=\"urn:ietf:params:xml:ns:pidf\"> " + " <tuple id=\"1\"> "
            + " <status><basic>open</basic></status>" + " </tuple> " + " </presencee>";

    assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, false));

    // get the NOTIFY
    reqevent = sub.waitNotify(1000);
    assertNotNull(reqevent);
    assertNoSubscriptionErrors(sub);

    // process the NOTIFY
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
  }

  // Test reception of NOTIFY message

  /*
   * String buddy = "sip:becky@cafesip.org"; // I am amit
   * 
   * try { // create far end (presence server simulator, fictitious buddy) PresenceNotifySender ub =
   * new PresenceNotifySender(sipStack, buddy); // add the buddy to the buddy list - sends SUBSCRIBE
   * to buddy assertTrue(ua.addBuddy(buddy, "my-event-id-1"));
   * 
   * Subscription s = ua.getBuddyInfo(buddy); assertNotNull(s); assertEquals(0,
   * s.getEventErrors().size()); assertTrue(s.isSubscriptionPending()); // tell far end to wait for
   * & process SUBSCRIBE, respond with OK assertTrue(ub.processSubscribe(5000, SipResponse.OK,
   * "OK")); // tell far end to send a correct NOTIFY String notify_body = " <?xml version=\"1.0\"
   * encoding=\"UTF-8\"?>\n <presence entity=\"sip:becky@cafesip.org\"
   * xmlns=\"urn:ietf:params:xml:ns:pidf\"> <tuple id=\"1\"> <status> <basic>closed </basic>
   * </status> </tuple> </presence>"; assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null,
   * notify_body, 3600));
   * 
   * assertNotNull(s.waitNotification(2000)); SipRequest req = s.getLastReceivedRequest();
   * assertNotNull(req); assertTrue(req.isNotify()); assertEquals(0, s.getEventErrors().size());
   * assertTrue(s.isSubscriptionActive()); assertEquals("closed", ((PresenceDeviceInfo)
   * s.getPresenceDevices() .get("1")).getBasicStatus()); Request good_notify =
   * ub.getLastSentNotify(); Request req2 = (Request) s.getLastReceivedRequest().getMessage();
   * assertTrue(req2.equals(good_notify)); // now send a NOTIFY with a different event ID, callID,
   * or totag - // verify not processed notify_body = " <?xml version=\"1.0\" encoding=\"UTF-8\"?>\n
   * <presence entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"> <tuple
   * id=\"1\"> <status> <basic>open </basic> </status> </tuple> </presence>"; EventHeader event_hdr
   * = (EventHeader) good_notify.getHeader( EventHeader.NAME).clone();
   * event_hdr.setEventId("my-event-id-2");
   * assertTrue(ub.sendNotify(SubscriptionStateHeader.PENDING, null, notify_body, 3600, event_hdr,
   * null, null, null)); // assertNull(s.waitNotification(500));
   * //assertTrue(s.isSubscriptionActive()); TODO, uncomment w/SipPHone HERE // NOTIFY
   * //assertEquals("closed", ((PresenceDeviceInfo) // s.getPresenceDevices() //
   * .get("1")).getBasicStatus()); s.clearEventErrors(); // send NOTIFY with bad event header
   * Request message = (Request) good_notify.clone(); ((EventHeader)
   * message.getHeader(EventHeader.NAME)) .setEventType("preesence");
   * assertTrue(ub.sendNotify(message)); // assertNotNull1000); TODO HERE - resume here
   * assertTrue(s.getEventErrors().size() > 0); String err = (String) s.getEventErrors().get(
   * s.getEventErrors().size() - 1); assertTrue(err.indexOf("bad event") != -1); } catch (Exception
   * e) { e.printStackTrace(); fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
   * }
   */

  // TODO next: 2 SipPhone, 2 buddies each; 1 SipPHone
  // that
  // has 1 buddy and 1 SipCall; amit and becky on separate boxes.
  @Test
  public void testStrayNotify() throws Exception {
    // with no matching Subscription
    String buddy = "sip:becky@cafesip.org";
    String buddy2 = "sip:doodah@day.com";

    // create object to send a NOTIFY
    PresenceNotifySender sender =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort,
            "sip:huey@duey.com"));

    // create and send NOTIFY out of the blue
    Request request =
        sipStack.getMessageFactory().createRequest(
            "NOTIFY sip:amit@" + host + ':' + myPort + ";transport=" + testProtocol
                + " SIP/2.0\r\n\r\n");
    String notifyBody =
        "<?xml version='1.0' encoding='UTF-8'?> " + " <presence entity='sip:anyone@cafesip.org' "
            + "xmlns='urn:ietf:params:xml:ns:pidf'>" + "<tuple id='1'>"
            + "<status><basic>closed</basic>" + "</status>" + "</tuple>" + "</presence>";

    sender.addNotifyHeaders(request, "amit", "nist.gov", SubscriptionStateHeader.TERMINATED,
        "late", notifyBody, 0);

    SipTransaction trans = sender.sendStatefulNotify(request, true);
    assertNotNull(sender.getErrorMessage(), trans);
    // get the response
    EventObject event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);

    if (event instanceof TimeoutEvent) {
      fail("Event Timeout received by far end while waiting for NOTIFY response");
    }

    Response response = ((ResponseEvent) event).getResponse();
    assertEquals("Should have gotten 481 response for stray NOTIFY",
        SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST, response.getStatusCode());

    // repeat w/2 buddies using wrong presentity. Verify
    // presence event on both
    setUpTwoBuddies(ua, buddy, "open", buddy2, "open");
    PresenceSubscriber s1 = ua.getBuddyInfo(buddy);
    assertNotNull(s1);
    assertEquals(0, s1.getEventErrors().size());
    PresenceSubscriber s2 = ua.getBuddyInfo(buddy2);
    assertNotNull(s2);
    assertEquals(0, s2.getEventErrors().size());

    request = (Request) request.clone();
    trans = sender.sendStatefulNotify(request, true); // resend
    // last
    // notify
    assertNotNull(sender.getErrorMessage(), trans);

    // get the response
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);

    if (event instanceof TimeoutEvent) {
      fail("Event Timeout received by far end while waiting for NOTIFY response");
    }

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

    // repeat w/2 buddies using correct presentity but wrong event ID.
    // Verify
    // presence event on both

    request =
        sipStack.getMessageFactory().createRequest(
            "NOTIFY sip:amit@" + host + ':' + myPort + ";transport=" + testProtocol
                + " SIP/2.0\r\n\r\n");
    notifyBody =
        "<?xml version='1.0' encoding='UTF-8'?> <presence entity='"
            + buddy
            + "'xmlns='urn:ietf:params:xml:ns:pidf'><tuple id='1'><status><basic>closed</basic></status></tuple></presence>";

    sender.addNotifyHeaders(request, "amit", "nist.gov", SubscriptionStateHeader.ACTIVE, "late",
        notifyBody, 1000);

    EventHeader ehdr = (EventHeader) request.getHeader(EventHeader.NAME);
    ehdr.setEventId("unmatched-eventid");
    trans = sender.sendStatefulNotify(request, true);
    assertNotNull(sender.getErrorMessage(), trans); // get the response
    event = sender.waitResponse(trans, 2000);
    assertNotNull(sender.getErrorMessage(), event);

    if (event instanceof TimeoutEvent) {
      fail("Event Timeout received by far end while waiting for NOTIFY response");
    }

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
  }

  @Test
  public void testOnePhoneThreeBuddies() throws Exception {
    String buddyone = "sip:becky@cafesip.org";
    String buddytwo = "sip:vidya@cafesip.org";
    String buddythree = "sip:tom@cafesip.org";

    // ///////////////// create the test buddies ////////////////////

    PresenceNotifySender buddy1 =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddyone));
    PresenceNotifySender buddy2 =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddytwo));
    PresenceNotifySender buddy3 =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, buddythree));

    // ////////// add buddy1 to the buddy list ///////////////////

    buddy1.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    PresenceSubscriber s1 = ua.addBuddy(buddyone, 2000); // send
    // SUBSCRIBE,
    // get response
    assertNotNull(s1);
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.processResponse(1000));
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddyone
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(buddy1.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));
    RequestEvent reqevent = s1.waitNotify(1000); // wait for notify
    assertNotNull(reqevent);
    Response response = s1.processNotify(reqevent); // process it
    assertNotNull(response);
    assertNoSubscriptionErrors(s1);
    assertTrue(s1.isSubscriptionActive());
    assertTrue(s1.getTimeLeft() <= 2400);
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.replyToNotify(reqevent, response)); // send reply
    HashMap<String, PresenceDeviceInfo> devices = s1.getPresenceDevices(); // check presence info
    assertEquals(1, devices.size());
    PresenceDeviceInfo dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    assertEquals(1, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());

    // /////////// add buddys 2 and 3 to the buddy list ////////////////

    buddy2.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    buddy3.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    PresenceSubscriber s2 = ua.addBuddy(buddytwo, 2000);
    assertEquals(2, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    PresenceSubscriber s3 = ua.addBuddy(buddythree, 3700, 2000);
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertEquals(SipResponse.OK, s3.getReturnCode());
    assertEquals(3, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertNotNull(s2);
    assertNotNull(s3);
    assertTrue(s2.processResponse(1000));
    assertTrue(s3.processResponse(1000));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddytwo
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(buddy2.sendNotify(SubscriptionStateHeader.PENDING, null, notifyBody, 2400, false));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddythree
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"3\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(buddy3.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3700, false));

    RequestEvent reqevent2 = s2.waitNotify(1000); // wait for notify
    // on 2
    assertNotNull(reqevent2);
    RequestEvent reqevent3 = s3.waitNotify(1000); // wait for notify
    // on 3
    assertNotNull(reqevent3);

    response = s2.processNotify(reqevent2); // process notify on 2
    assertNotNull(response);
    assertNoSubscriptionErrors(s2);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s2.isSubscriptionPending());
    assertTrue(s2.getTimeLeft() <= 2400);
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertTrue(s2.replyToNotify(reqevent2, response)); // send reply on
    // 2

    response = s3.processNotify(reqevent3); // process notify on 3
    assertNotNull(response);
    assertNoSubscriptionErrors(s3);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s3.isSubscriptionActive());
    assertTrue(s3.getTimeLeft() <= 3700);
    assertTrue(s3.getTimeLeft() > 3500);
    assertTrue(s2.getTimeLeft() <= 2400);
    assertTrue(s3.replyToNotify(reqevent3, response)); // send reply on
    // 3

    devices = s2.getPresenceDevices(); // check presence info on 2
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    devices = s3.getPresenceDevices(); // check presence info on 3
    assertEquals(1, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s1.getPresenceDevices(); // re-check presence info on 1
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    assertEquals(3, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());

    // ////////// refresh one guy, make his dev data same as another
    // ///////// another guy gets notify out of the blue, 2 tuples

    buddy2.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    assertTrue(s2.refreshBuddy(2000));
    assertEquals(3, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertTrue(s2.processResponse(1000));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddytwo
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"3\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(buddy2.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2000, false));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddythree
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"3\"><status><basic>open</basic></status></tuple><tuple id=\"4\"><status><basic>closed</basic></status></tuple></presence>";
    assertTrue(buddy3.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3000, false));

    reqevent3 = s3.waitNotify(1000); // wait for notify on 3
    assertNotNull(reqevent3);
    reqevent2 = s2.waitNotify(1000); // wait for notify on 2
    assertNotNull(reqevent2);

    response = s2.processNotify(reqevent2); // process notify on 2
    assertNotNull(response);
    assertNoSubscriptionErrors(s2);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s2.isSubscriptionActive());
    assertTrue(s2.getTimeLeft() <= 2000);
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertTrue(s2.replyToNotify(reqevent2, response)); // send reply on
    // 2

    response = s3.processNotify(reqevent3); // process notify on 3
    assertNotNull(response);
    assertNoSubscriptionErrors(s3);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s3.isSubscriptionActive());
    assertTrue(s3.getTimeLeft() <= 3000);
    assertTrue(s3.replyToNotify(reqevent3, response)); // send reply on
    // 3

    devices = s2.getPresenceDevices(); // check presence info on 2
    assertEquals(1, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s3.getPresenceDevices(); // check presence info on 3
    assertEquals(2, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    dev = devices.get("4");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());
    assertNull(devices.get("5"));

    devices = s1.getPresenceDevices(); // re-check presence info on 1
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    assertEquals(3, ua.getBuddyList().size());
    assertEquals(0, ua.getRetiredBuddies().size());

    // //////// remove a buddy from the list ///////////////////

    buddy1.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    assertTrue(s1.removeBuddy(2000)); // send unSUBSCRIBE,
    // get response
    assertEquals(2, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());

    // confirm subscription objects obtainable and the same
    assertEquals(s1.toString(), ua.getBuddyInfo(buddyone).toString());
    assertEquals(s2.toString(), ua.getBuddyInfo(buddytwo).toString());
    assertEquals(s3.toString(), ua.getBuddyInfo(buddythree).toString());

    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.processResponse(1000));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddyone
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"2\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(buddy1.sendNotify(SubscriptionStateHeader.TERMINATED, "removed", notifyBody, 0,
        false));
    reqevent = s1.waitNotify(1000); // wait for notify
    assertNotNull(reqevent);
    response = s1.processNotify(reqevent); // process it
    assertNotNull(response);
    assertNoSubscriptionErrors(s1);
    assertTrue(s1.isSubscriptionTerminated());
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.replyToNotify(reqevent, response)); // send reply
    devices = s1.getPresenceDevices(); // check presence info
    assertEquals(1, devices.size());
    dev = devices.get("2");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    assertEquals(2, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());

    devices = s2.getPresenceDevices(); // re-check presence info on 2
    assertEquals(1, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s3.getPresenceDevices(); // re-check presence info on 3
    assertEquals(2, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    dev = devices.get("4");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    // ///////// do a fetch on removed buddy ///////////////////

    // like the remove above
    buddy1.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    s1 = ua.fetchPresenceInfo(buddyone, 2000); // send unSUBSCRIBE,
    // get response
    assertNotNull(s1);
    assertEquals(2, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());

    // confirm subscription objects obtainable and the same
    assertEquals(s1.toString(), ua.getBuddyInfo(buddyone).toString());
    assertEquals(s2.toString(), ua.getBuddyInfo(buddytwo).toString());
    assertEquals(s3.toString(), ua.getBuddyInfo(buddythree).toString());

    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.processResponse(1000));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"" + buddyone
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"></presence>";
    assertTrue(buddy1.sendNotify(SubscriptionStateHeader.TERMINATED, "removed", notifyBody, 0,
        false));
    reqevent = s1.waitNotify(1000); // wait for notify
    assertNotNull(reqevent);
    response = s1.processNotify(reqevent); // process it
    assertNotNull(response);
    assertNoSubscriptionErrors(s1);
    assertTrue(s1.isSubscriptionTerminated());
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertTrue(s1.replyToNotify(reqevent, response)); // send reply
    devices = s1.getPresenceDevices(); // check presence info
    assertEquals(0, devices.size());

    assertEquals(2, ua.getBuddyList().size());
    assertEquals(1, ua.getRetiredBuddies().size());

    devices = s2.getPresenceDevices(); // re-check presence info on 2
    assertEquals(1, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s3.getPresenceDevices(); // re-check presence info on 3
    assertEquals(2, devices.size());
    dev = devices.get("3");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());
    dev = devices.get("4");
    assertNotNull(dev);
    assertEquals("closed", dev.getBasicStatus());

    // ///////// remove the other two buddies also /////////

    buddy2.processSubscribe(15000, SipResponse.OK, "OK"); // prepare
    buddy3.processSubscribe(25000, SipResponse.OK, "OK"); // prepare
    assertTrue(s2.removeBuddy(22000));
    assertEquals(1, ua.getBuddyList().size());
    assertEquals(2, ua.getRetiredBuddies().size());
    assertTrue(s3.removeBuddy(2000));
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertEquals(SipResponse.OK, s3.getReturnCode());
    assertEquals(0, ua.getBuddyList().size());
    assertEquals(3, ua.getRetiredBuddies().size());
    // confirm subscription objects obtainable and the same
    assertEquals(s1.toString(), ua.getBuddyInfo(buddyone).toString());
    assertEquals(s2.toString(), ua.getBuddyInfo(buddytwo).toString());
    assertEquals(s3.toString(), ua.getBuddyInfo(buddythree).toString());
    assertTrue(s2.processResponse(1000));
    assertTrue(s3.processResponse(1000));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddytwo
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(buddy2.sendNotify(SubscriptionStateHeader.TERMINATED, "done", notifyBody, 2400,
        false));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\""
            + buddythree
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"7\"><status><basic>open</basic></status></tuple></presence>";
    assertTrue(buddy3.sendNotify(SubscriptionStateHeader.TERMINATED, "all-done", notifyBody,
        3700, false));

    reqevent2 = s2.waitNotify(1000); // wait for notify on 2
    assertNotNull(reqevent2);
    reqevent3 = s3.waitNotify(1000); // wait for notify on 3
    assertNotNull(reqevent3);

    response = s2.processNotify(reqevent2); // process notify on 2
    assertNotNull(response);
    assertNoSubscriptionErrors(s2);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s2.isSubscriptionTerminated());
    assertEquals("done", s2.getTerminationReason());
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertTrue(s2.replyToNotify(reqevent2, response)); // send reply on
    // 2

    response = s3.processNotify(reqevent3); // process notify on 3
    assertNotNull(response);
    assertNoSubscriptionErrors(s3);
    assertEquals(SipResponse.OK, response.getStatusCode());
    assertTrue(s3.isSubscriptionTerminated());
    assertEquals("all-done", s3.getTerminationReason());
    assertTrue(s3.replyToNotify(reqevent3, response)); // send reply on
    // 3

    devices = s2.getPresenceDevices(); // check presence info on 2
    assertEquals(1, devices.size());
    dev = devices.get("1");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s3.getPresenceDevices(); // check presence info on 3
    assertEquals(1, devices.size());
    dev = devices.get("7");
    assertNotNull(dev);
    assertEquals("open", dev.getBasicStatus());

    devices = s1.getPresenceDevices(); // re-check presence info on 1
    assertEquals(0, devices.size());

    assertEquals(0, ua.getBuddyList().size());
    assertEquals(3, ua.getRetiredBuddies().size());
  }

  private void setUpTwoBuddies(SipPhone phone, String b1, String b1status, String b2,
      String b2status) throws Exception {
    PresenceNotifySender buddy1 =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, b1));
    PresenceNotifySender buddy2 =
        new PresenceNotifySender(sipStack.createSipPhone(host, testProtocol, myPort, b2));

    buddy1.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    buddy2.processSubscribe(5000, SipResponse.OK, "OK"); // prepare
    PresenceSubscriber s1 = ua.addBuddy(b1, 2000);
    PresenceSubscriber s2 = ua.addBuddy(b2, 2000);
    assertNotNull(s1);
    assertNotNull(s2);
    assertEquals(SipResponse.OK, s1.getReturnCode());
    assertEquals(SipResponse.OK, s2.getReturnCode());
    assertTrue(s1.processResponse(1000));
    assertTrue(s2.processResponse(1000));
    String notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"" + b1
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"1\"><status><basic>"
            + b1status + "</basic></status></tuple></presence>";
    assertTrue(buddy1.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, false));
    notifyBody =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence entity=\"" + b2
            + "\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple id=\"2\"><status><basic>"
            + b2status + "</basic></status></tuple></presence>";
    assertTrue(buddy2.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 3600, false));

    RequestEvent reqevent1 = s1.waitNotify(1000); // wait for notify
    // on 1
    RequestEvent reqevent2 = s2.waitNotify(1000); // wait for notify
    // on 2

    Response response = s1.processNotify(reqevent1); // process
    // notify
    // on 1
    assertTrue(s1.replyToNotify(reqevent1, response)); // send reply

    response = s2.processNotify(reqevent2); // process notify on 2
    assertTrue(s2.replyToNotify(reqevent2, response)); // send reply

    assertNoSubscriptionErrors(s1);
    assertNoSubscriptionErrors(s2);
  }

  /*
   * private void template() { String buddy = "sip:becky@cafesip.org"; // I am amit
   * 
   * try { // create far end (presence server simulator, fictitious buddy) PresenceNotifySender ub =
   * new PresenceNotifySender(sipStack .createSipPhone(host, testProtocol, myPort, buddy)); //
   * SEQUENCE OF EVENTS // prepare far end to receive SUBSCRIBE // do something with a buddy - sends
   * SUBSCRIBE, gets response // check the return info // process the received response // check the
   * response processing results // tell far end to send a NOTIFY // wait for the NOTIFY // process
   * the NOTIFY // check the processing results // check PRESENCE info - devices/tuples // check
   * PRESENCE info - top-level extensions // check PRESENCE info - top-level notes // reply to the
   * NOTIFY // prepare far end to receive SUBSCRIBE assertTrue(ub.processSubscribe(5000,
   * SipResponse.OK, "OK")); // do something with a buddy - sends SUBSCRIBE, gets response
   * Subscription s = ua.addBuddy(buddy, 2000); // check the return info assertNotNull(s);
   * assertEquals(SipResponse.OK, s.getReturnCode()); // process the received response
   * assertTrue(s.processResponse(1000)); // check the response processing results
   * assertTrue(s.isSubscriptionActive()); assertTrue(s.getTimeLeft() <= 3600); // tell far end to
   * send a NOTIFY String notify_body = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<presence
   * entity=\"sip:becky@cafesip.org\" xmlns=\"urn:ietf:params:xml:ns:pidf\"><tuple
   * id=\"1\"><status><basic>closed</basic></status></tuple></presence>";
   * assertTrue(ub.sendNotify(SubscriptionStateHeader.ACTIVE, null, notify_body, 2400, false)); //
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
