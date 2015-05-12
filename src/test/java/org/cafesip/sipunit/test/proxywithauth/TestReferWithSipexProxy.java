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

import static org.cafesip.sipunit.SipAssert.assertBodyContains;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNoSubscriptionErrors;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.ReferNotifySender;
import org.cafesip.sipunit.ReferSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.sip.RequestEvent;
import javax.sip.address.SipURI;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class tests authentication challenges for SipUnit REFER subscriptions. Tests in this class
 * require a proxy that will authenticate using DIGEST and that can pass received REFER
 * requests/responses on to the target registered User Agent endpoints, and for refresh/unsubscribe
 * (via SUBSCRIBE) do the same.
 * 
 * When using CafeSip SipExchange for this test, you need to modify a couple of its default config
 * settings to achieve the above. Open the sipexchange.ear in your JBoss deploy directory and within
 * that, open sipex-jiplets.spr (it is a normal zip archive and can be opened with something like
 * WinZip). Edit the JIP-INF/jip.xml file in the sipex-jiplets.spr. Make these two changes:
 * 
 * <pre>
 * a) to make the refer subscription SUBSCRIBE messages get passed on to the 
 * User Agent endpoint provided in the test below rather than getting passed
 * to the Presence handling jiplet, remove the 'SUBSCRIBE' element from the
 * jiplet-mapping for SipExchangeProxyJiplet (including the 'not', '/not', 'equals' 
 * and '/equals' lines enclosing the 'SUBSCRIBE' value), and remove the whole 
 * jiplet-mapping for SipExchangeBuddyListJiplet.
 * 
 * b) in the security-constraint element, add a jiplet-name element inside the
 * jiplet-names element for the SipExchangeProxyJiplet, like the ones already
 * there for SipExchangeRegistrarJiplet and SipExchangeBuddyListJiplet.
 * 
 * </pre>
 * 
 * Start up the SipExchange server before running this test, and have the URIs used here in the list
 * of users/subscribers at the proxy, all with password a1b2c3d4 - these URIs include:
 * sip:becky@<property "sipunit.test.domain" below>, sip:amit@<property "sipunit.test.domain"
 * below>.
 * 
 * 
 * @author Becky McElroy
 * 
 */
public class TestReferWithSipexProxy {

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

    // the following is required to make the stack deliver an unsolicited
    // NOTIFY to this test
    defaultProperties.setProperty("gov.nist.javax.sip.DELIVER_UNSOLICITED_NOTIFY", "true");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestReferWithSipexProxy() {
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
  public void testBasicReferOutOfDialog() throws Exception {
    // create any refer-To URI
    SipURI referTo =
        ua.getUri("sip:", "dave@denver.example.org", "udp", "INVITE", null, null, null, null, null);

    // create & prepare the referee simulator (User B) to respond to REFER
    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 3600);
    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ub.format(), ub);
    ReferNotifySender referee = new ReferNotifySender(ub);

    // prepare referee to receive REFER and respond with OK
    referee.processRefer(5000, SipResponse.OK, "OK");
    Thread.sleep(50);

    // ********* I. Send REFER out-of-dialog, initiate subscription

    ReferSubscriber subscription = ua.refer("sip:becky@cafesip.org", referTo, null, 5000, null);
    assertNotNull(subscription); // REFER sending successful, received a
    // response
    assertEquals(SipResponse.PROXY_AUTHENTICATION_REQUIRED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionPending());

    // process the received REFER response, check results
    assertTrue(subscription.processResponse(1000));
    assertTrue(subscription.isSubscriptionActive());
    assertNoSubscriptionErrors(subscription);

    // User B send active-state NOTIFY to A
    Thread.sleep(50);
    String notifyBody = "SIP/2.0 200 OK\n";
    assertTrue(referee.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2400, false));

    // User A: get the NOTIFY
    RequestEvent reqevent = subscription.waitNotify(500);
    assertNotNull(reqevent);
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
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));

    // User A: reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // ********** II. Refresh the subscription

    // prepare the far end to respond to SUBSCRIBE
    referee.processSubscribe(5000, SipResponse.OK, "OK Refreshed");
    Thread.sleep(100);

    // send the SUBSCRIBE
    assertTrue(subscription.refresh(3600, 500));
    assertEquals(SipResponse.PROXY_AUTHENTICATION_REQUIRED, subscription.getReturnCode());
    assertTrue(subscription.isSubscriptionActive());

    // process the received response, check results
    assertTrue(subscription.processResponse(1000));
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getTimeLeft() <= 3600 && subscription.getTimeLeft() > 3500);
    assertNoSubscriptionErrors(subscription);

    // User B send active-state NOTIFY to A
    Thread.sleep(50);
    notifyBody = "SIP/2.0 200 Okey Dokey\n";
    assertTrue(referee.sendNotify(SubscriptionStateHeader.ACTIVE, null, notifyBody, 2200, false));

    // User A: get the NOTIFY
    reqevent = subscription.waitNotify(500);
    assertNotNull(reqevent);
    request = reqevent.getRequest();
    assertEquals(2200,
        ((SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME)).getExpires());
    assertBodyContains(new SipRequest(reqevent), "200 Okey Dokey");

    // process the NOTIFY
    resp = subscription.processNotify(reqevent);
    assertNotNull(resp);

    // check the NOTIFY processing results
    assertTrue(subscription.isSubscriptionActive());
    assertTrue(subscription.getTimeLeft() <= 2200 && subscription.getTimeLeft() > 2100);
    assertEquals(SipResponse.OK, subscription.getReturnCode());
    assertNoSubscriptionErrors(subscription);
    assertEquals(SipResponse.OK, resp.getStatusCode());
    assertTrue(resp.getReasonPhrase().equals("OK"));

    // User A: reply to the NOTIFY
    assertTrue(subscription.replyToNotify(reqevent, resp));

    // ********** III. Terminate the subscription from the referrer side

    // prepare the far end to respond to unSUBSCRIBE
    referee.processSubscribe(5000, SipResponse.OK, "OK Done");
    Thread.sleep(100);

    // send the un-SUBSCRIBE
    assertTrue(subscription.unsubscribe(500));
    if (!subscription.isRemovalComplete()) {
      assertTrue(subscription.processResponse(1000));
      assertTrue(subscription.isSubscriptionTerminated());
      assertEquals("Unsubscribe", subscription.getTerminationReason());
      assertEquals(0, subscription.getTimeLeft());
      assertEquals(SipResponse.OK, subscription.getReturnCode());
      resp = subscription.getCurrentResponse().getResponse();
      assertEquals("OK Done", resp.getReasonPhrase());
      assertNoSubscriptionErrors(subscription);

      // User B: send terminated NOTIFY
      Thread.sleep(500);
      notifyBody = "SIP/2.0 100 Trying\n";
      assertTrue(referee.sendNotify(SubscriptionStateHeader.TERMINATED, "timeout", notifyBody, 0,
          false));
      Thread.sleep(10);
      // TODO - why if send Notify via proxy (last parm above = true), it
      // cannot propagate the Notify response, tho it does fine for REFER
      // and SUBSCRIBE?

      // User A: get the NOTIFY
      assertEquals(0, subscription.getEventErrors().size());
      reqevent = subscription.waitNotify(200);

      assertNotNull(reqevent);
      assertNoSubscriptionErrors(subscription);
      request = reqevent.getRequest();

      // process the NOTIFY
      resp = subscription.processNotify(reqevent);
      assertNotNull(resp);

      // check the NOTIFY processing results
      assertTrue(subscription.isSubscriptionTerminated());
      assertEquals(SipResponse.OK, subscription.getReturnCode());
      assertNoSubscriptionErrors(subscription);

      // reply to the NOTIFY
      assertTrue(subscription.replyToNotify(reqevent, resp));
    }

    referee.dispose();
  }
}
