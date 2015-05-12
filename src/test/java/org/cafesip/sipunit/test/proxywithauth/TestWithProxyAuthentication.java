/*
 * Created on Mar 29, 2005
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

import static org.cafesip.sipunit.SipAssert.assertAnswered;
import static org.cafesip.sipunit.SipAssert.assertBodyContains;
import static org.cafesip.sipunit.SipAssert.assertBodyNotPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderPresent;
import static org.cafesip.sipunit.SipAssert.assertLastOperationFail;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.assertNotAnswered;
import static org.cafesip.sipunit.SipAssert.assertRequestReceived;
import static org.cafesip.sipunit.SipAssert.assertResponseNotReceived;
import static org.cafesip.sipunit.SipAssert.assertResponseReceived;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipMessage;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.cafesip.sipunit.test.noproxy.TestMessageNoProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Properties;

import javax.sip.ResponseEvent;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentLengthHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.Header;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.PriorityHeader;
import javax.sip.header.ReasonHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;


/**
 * This class tests SipUnit API methods.
 * 
 * Tests in this class require that a Proxy/registrar server be running with authentication turned
 * on. The Authentication scheme is Digest. Defaults: proxy host = 192.168.112.1, port = 5060,
 * protocol = udp; user amit password a1b2c3d4 and user becky password a1b2c3d4 defined at the proxy
 * for domain cafesip.org. The sipunit test stack uses port 9091.
 * 
 * Example open-source Proxy/registrars include SipExchange (cafesip.org) and nist.gov's JAIN-SIP
 * Proxy for the People (http://snad.ncsl.nist.gov/proj/iptel/).
 * 
 * When using CafeSip SipExchange for this test, you need to modify a default config setting to make
 * the proxy challenge all requests. Open the sipexchange.ear in your JBoss deploy directory and
 * within that, open sipex-jiplets.spr (it is a normal zip archive and can be opened with something
 * like WinZip). Edit the JIP-INF/jip.xml file in the sipex-jiplets.spr. In the security-constraint
 * element toward the bottom, add a jiplet-name element inside the jiplet-names element for the
 * SipExchangeProxyJiplet, like the ones already there for SipExchangeRegistrarJiplet and
 * SipExchangeBuddyListJiplet. Start up the SipExchange server before running this test, and have
 * the URIs used here provisioned at the proxy, all with password a1b2c3d4 - these URIs include:
 * sip:becky@<property "sipunit.test.domain" below>, sip:amit@<property "sipunit.test.domain"
 * below>.
 * 
 * @author Becky McElroy
 * 
 */
public class TestWithProxyAuthentication {
  private static final Logger LOG = LoggerFactory.getLogger(TestMessageNoProxy.class);

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
    defaultProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "9091");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");

    defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
    defaultProperties.setProperty("sipunit.proxy.host", "192.168.112.1");
    defaultProperties.setProperty("sipunit.proxy.port", "5060");
  }

  private Properties properties = new Properties(defaultProperties);

  public TestWithProxyAuthentication() {
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 9091;
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
  public void testAuthRegistration() throws Exception {
    LOG.trace("testAuthRegistration");

    ua.register("amit", "a1b2c3d4", null, 4890, 10000);
    assertLastOperationSuccess("user a registration - " + ua.format(), ua);

    // check that the proper default contact address was registered
    SipURI defaultContact =
        ua.getParent().getAddressFactory()
            .createSipURI(((SipURI) ua.getAddress().getURI()).getUser(), ua.getStackAddress());
    defaultContact.setPort(ua.getParent().getSipProvider().getListeningPoints()[0].getPort());
    defaultContact.setTransportParam(ua.getParent().getSipProvider().getListeningPoints()[0]
        .getTransport());
    defaultContact.setSecure(((SipURI) ua.getAddress().getURI()).isSecure());
    defaultContact.setLrParam();

    assertEquals("The default contact is wrong", defaultContact.toString(), ua.getContactInfo()
        .getURI());

    assertEquals("The default contact is wrong", "sip:amit@" + ua.getStackAddress() + ':'
        + myPort + ";transport=" + testProtocol + ";lr", ua.getContactInfo().getURI());

    // check contact expiry
    // assertEquals("wrong contact expiry", 4890,
    // getContactInfo().getExpiry());
    // why proxy/registrar doesn't take it?

    // wait 1 sec then unregister
    Thread.sleep(1000);

    ua.unregister("a@" + properties.getProperty("sipunit.proxy.host"), 10000); // unregister the
                                                                               // wrong contact
    assertLastOperationFail("unregistering wrong user - " + ua.format(), ua);

    ua.unregister(ua.getContactInfo().getURI(), 10000);
    assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
  }

  @Test
  public void testCustomContactRegistration() throws Exception {
    LOG.trace("testCustomContactRegistration");

    ua.register("amit", "a1b2c3d4", "sip:billy@bob.land:9090;transport=tcp", 4890, 10000);
    assertLastOperationSuccess("user a registration - " + ua.format(), ua);

    // check that the proper default contact address was registered
    assertEquals("The registered contact is wrong", "sip:billy@bob.land:9090;transport=tcp", ua
        .getContactInfo().getURI().toString());

    // check contact expiry
    // assertEquals("wrong contact expiry", 4890,
    // getContactInfo().getExpiry());
    // why proxy/registrar doesn't take it?

    // wait 1 sec then unregister
    Thread.sleep(1000);

    ua.unregister("a@" + properties.getProperty("sipunit.proxy.host"), 10000); // unregister the
                                                                               // wrong contact
    assertLastOperationFail("unregistering wrong user - " + ua.format(), ua);

    ua.unregister(ua.getContactInfo().getURI().toString(), 10000);
    assertLastOperationSuccess("unregistering user a - " + ua.format(), ua);
  }

  @Test
  public void testBadPasswdRegistration() {
    // authentication must be turned
    // on
    // at server
    LOG.trace("testBadPasswdRegistration");

    ua.register("amit", "a1b2c3d44", ua.getAddress().toString(), 0, 10000);
    assertLastOperationFail("Registration should have failed", ua);
  }

  /**
   * Test: asynchronous SipPhone.makeCall() with authentication, callee disc SipPhone.register()
   * using pre-set credentials
   */
  @Test
  public void testBothSidesAsynchMakeCall() throws Exception {
    // test the nonblocking version
    // of
    // SipPhone.makeCall()
    LOG.trace("testBothSidesAsynchMakeCall");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(50);

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
    Thread.sleep(300);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callA.listenForDisconnect();
    Thread.sleep(100);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();

    Thread.sleep(500);
    ub.dispose();
  }

  /**
   * Test: asynchronous SipPhone.makeCall() with authentication, caller disc SipPhone.register()
   * using pre-set credentials
   */
  @Test
  @Ignore
  // TODO fix - why BYE failing?
  public void testAsynchMakeCallCallerDisc() throws Exception {
    // test the nonblocking version
    // of
    // SipPhone.makeCall()
    LOG.trace("testAsynchMakeCallCallerDisc");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(50);

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null);
    assertLastOperationSuccess(ua.format(), ua);

    boolean status = callB.waitForIncomingCall(4000);
    assertTrue(callB.format(), status);
    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
    Thread.sleep(300);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    Thread.sleep(2000);
    callB.listenForDisconnect();
    Thread.sleep(1000);

    // a.disconnect();
    // instead, send the BYE myself without credentials due to
    // SipExchange rejecting cached credentials (bug 2845998)
    // and handle the challenge myself
    Request bye = callA.getDialog().createRequest(Request.BYE);
    SipTransaction trans = ua.sendRequestWithTransaction(bye, false, callA.getDialog());
    assertNotNull(trans);
    EventObject respEvent = ua.waitResponse(trans, 1000);
    assertNotNull(respEvent);
    assertTrue(respEvent instanceof ResponseEvent);
    Response resp = ((ResponseEvent) respEvent).getResponse();
    assertEquals(Response.PROXY_AUTHENTICATION_REQUIRED, resp.getStatusCode());
    Request newBye = ua.processAuthChallenge(resp, bye, null, null);
    assertNotNull(newBye);
    assertNotNull(ua.sendRequestWithTransaction(newBye, true, callA.getDialog()));

    callB.waitForDisconnect(5000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);

    callB.respondToDisconnect();

    Thread.sleep(1000);
    ub.dispose();
  }

  /**
   * Test: callTimeoutOrError() indicates failure properly
   * 
   * <p>
   * Uncomment to test.
   */
  public void xtestMakeCallFailureCheck() throws Exception {
    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 1800);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    String badRoute = properties.getProperty("sipunit.proxy.host") + ":" + (proxyPort + 1);

    // test asynchronous makeCall() failure

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), badRoute
            + "/udp");
    assertLastOperationSuccess(ua.format(), ua);
    assertNotAnswered("Call leg shouldn't be answered", callA);
    Thread.sleep(120000);

    assertTrue("Outgoing call leg error status incorrect", callA.callTimeoutOrError()); // should
                                                                                        // get
    // a timeout

    callA.dispose();

    // synchronous makeCall() test not applicable, SipCall null
  }

  /**
   * Test: SipPhone.makeCall() with authentication; send authorization on ACK, BYE
   * SipPhone.register() using pre-set credentials
   * 
   */
  @Test
  public void testBothSidesCallerDisc() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall()
    LOG.trace("testBothSidesCallerDisc");

    final class PhoneB extends Thread {

      public void run() {
        try {
          SipPhone ub =
              sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
                  proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));

          ub.register("becky", "a1b2c3d4", null, 600, 5000);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);
          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(3000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          Thread.sleep(1000);
          ub.dispose();

          return;
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 1800);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    // give user b time to register
    Thread.sleep(2000);

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), SipResponse.OK,
            5000, null);
    assertLastOperationSuccess(ua.format(), ua);

    assertAnswered("Outgoing call leg not answered", callA);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(2000);

    // a.disconnect();
    // assertLastOperationSuccess("a disc - " + a.format(), a);
    // instead, send the BYE myself without credentials due to
    // SipExchange rejecting cached credentials (bug 2845998)
    // and handle the challenge myself
    Request bye = callA.getDialog().createRequest(Request.BYE);
    SipTransaction trans = ua.sendRequestWithTransaction(bye, false, callA.getDialog());
    assertNotNull(trans);
    EventObject respEvent = ua.waitResponse(trans, 1000);
    assertNotNull(respEvent);
    assertTrue(respEvent instanceof ResponseEvent);
    Response resp = ((ResponseEvent) respEvent).getResponse();
    assertEquals(Response.PROXY_AUTHENTICATION_REQUIRED, resp.getStatusCode());
    Request newBye = ua.processAuthChallenge(resp, bye, null, null);
    assertNotNull(newBye);
    assertNotNull(ua.sendRequestWithTransaction(newBye, false, callA.getDialog()));

    phoneB.join();
  }

  @Test
  public void testMakeCallExtraJainsipParms() {
    LOG.trace("testBothSidesCallerDisc");

    final class PhoneB extends Thread {

      public void run() {
        try {
          SipPhone ub =
              sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
                  proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));

          ub.register("becky", "a1b2c3d4", null, 600, 5000);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);

          assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
          assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
              "applicationn/texxt");
          assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
          assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "61");
          assertBodyContains(callB.getLastReceivedRequest(), "my body");

          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(3000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          Thread.sleep(1000);
          ub.dispose();

          return;
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 1800);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    try {
      PhoneB callB = new PhoneB();
      callB.start();

      // give user b time to register
      Thread.sleep(2000);

      // set up outbound INVITE contents

      ArrayList<Header> addnlHeaders = new ArrayList<>();
      addnlHeaders.add(ua.getParent().getHeaderFactory().createPriorityHeader("5"));
      addnlHeaders.add(ua.getParent().getHeaderFactory()
          .createContentTypeHeader("applicationn", "texxt"));

      ArrayList<Header> replaceHeaders = new ArrayList<>();
      URI bogusContact =
          ua.getParent()
              .getAddressFactory()
              .createURI(
                  "sip:doodah@" + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort);
      Address bogusAddr = ua.getParent().getAddressFactory().createAddress(bogusContact);
      replaceHeaders.add(ua.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                            // replacement
      replaceHeaders.add(ua.getParent().getHeaderFactory().createMaxForwardsHeader(62));

      SipCall callA =
          ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), SipResponse.OK,
              5000, null, addnlHeaders, replaceHeaders, "my body");
      assertLastOperationSuccess(ua.format(), ua);

      assertAnswered("Outgoing call leg not answered", callA);
      assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

      assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);

      // verify RINGING was received
      assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
      // verify OK was received
      assertResponseReceived(SipResponse.OK, callA);
      // check negative
      assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
      assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

      // verify getLastReceivedResponse() method
      assertEquals("Last response received wasn't answer", SipResponse.OK, callA
          .getLastReceivedResponse().getStatusCode());

      callA.sendInviteOkAck();
      assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

      Thread.sleep(2000);

      // a.disconnect();
      // assertLastOperationSuccess("a disc - " + a.format(), a);
      // instead, send the BYE myself without credentials due to
      // SipExchange rejecting cached credentials (bug 2845998)
      // and handle the challenge myself
      Request bye = callA.getDialog().createRequest(Request.BYE);
      SipTransaction trans = ua.sendRequestWithTransaction(bye, false, callA.getDialog());
      assertNotNull(trans);
      EventObject respEvent = ua.waitResponse(trans, 1000);
      assertNotNull(respEvent);
      assertTrue(respEvent instanceof ResponseEvent);
      Response resp = ((ResponseEvent) respEvent).getResponse();
      assertEquals(Response.PROXY_AUTHENTICATION_REQUIRED, resp.getStatusCode());
      Request newBye = ua.processAuthChallenge(resp, bye, null, null);
      assertNotNull(newBye);
      assertNotNull(ua.sendRequestWithTransaction(newBye, false, callA.getDialog()));

      callB.join();
    } catch (Exception e) {
      fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }
  }

  @Test
  public void testMakeCallExtraStringParms() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall() with extra String parameters
    LOG.trace("testBothSidesCallerDisc");

    final class PhoneB extends Thread {

      public void run() {
        try {
          SipPhone ub =
              sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
                  proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));

          ub.register("becky", "a1b2c3d4", null, 600, 5000);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);

          assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
          assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
              "applicationn/texxt");
          assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
          assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "61");
          assertBodyContains(callB.getLastReceivedRequest(), "my body");

          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          assertAnswered(callB);
          assertTrue("Shouldn't have received anything at the called party side", callB
              .getAllReceivedResponses().size() == 0);

          callB.listenForDisconnect();
          callB.waitForDisconnect(3000);
          assertLastOperationSuccess("b wait disc - " + callB.format(), callB);
          callB.respondToDisconnect();

          Thread.sleep(1000);
          ub.dispose();

          return;
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 1800);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    // give user b time to register
    Thread.sleep(2000);

    // set up outbound INVITE contents

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <sip:doodah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), SipResponse.OK,
            5000, null, "my body", "applicationn", "texxt", addnlHeaders, replaceHeaders);
    assertLastOperationSuccess(ua.format(), ua);

    assertAnswered("Outgoing call leg not answered", callA);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(2000);

    // a.disconnect();
    // assertLastOperationSuccess("a disc - " + a.format(), a);
    // instead, send the BYE myself without credentials due to
    // SipExchange rejecting cached credentials (bug 2845998)
    // and handle the challenge myself
    Request bye = callA.getDialog().createRequest(Request.BYE);
    SipTransaction trans = ua.sendRequestWithTransaction(bye, false, callA.getDialog());
    assertNotNull(trans);
    EventObject respEvent = ua.waitResponse(trans, 1000);
    assertNotNull(respEvent);
    assertTrue(respEvent instanceof ResponseEvent);
    Response resp = ((ResponseEvent) respEvent).getResponse();
    assertEquals(Response.PROXY_AUTHENTICATION_REQUIRED, resp.getStatusCode());
    Request newBye = ua.processAuthChallenge(resp, bye, null, null);
    assertNotNull(newBye);
    assertNotNull(ua.sendRequestWithTransaction(newBye, false, callA.getDialog()));

    phoneB.join();
  }

  @Test
  public void testNonblockingMakeCallExtraJainsipParms() throws Exception {
    // test the
    // nonblocking
    // SipPhone.makeCall() with extra JAIN SIP parameters
    LOG.trace("testNonblockingMakeCallExtraJainsipParms");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);
    ua.setLoopback(true);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(50);

    // set up outbound INVITE contents

    ArrayList<Header> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(ua.getParent().getHeaderFactory().createPriorityHeader("5"));
    addnlHeaders.add(ua.getParent().getHeaderFactory()
        .createContentTypeHeader("applicationn", "texxt"));

    ArrayList<Header> replaceHeaders = new ArrayList<>();
    URI bogusContact =
        ua.getParent()
            .getAddressFactory()
            .createURI(
                "sip:doodah@" + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort);
    Address bogusAddr = ua.getParent().getAddressFactory().createAddress(bogusContact);
    replaceHeaders.add(ua.getParent().getHeaderFactory().createContactHeader(bogusAddr)); // verify
                                                                                          // replacement
    replaceHeaders.add(ua.getParent().getHeaderFactory().createMaxForwardsHeader(62));

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null,
            addnlHeaders, replaceHeaders, "my body");
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));

    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "61");
    assertBodyContains(callB.getLastReceivedRequest(), "my body");

    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
    Thread.sleep(300);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callA.listenForDisconnect();
    Thread.sleep(100);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();

    Thread.sleep(500);
    ub.dispose();
  }

  @Test
  public void testNonblockingMakeCallExtraStringParms() throws Exception {
    // test the
    // nonblocking
    // version
    // of SipPhone.makeCall() with extra String parameters
    LOG.trace("testNonblockingMakeCallExtraStringParms");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);
    ua.setLoopback(true);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(50);

    // set up outbound INVITE contents

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <sip:doodah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null,
            "my body", "applicationn", "texxt", addnlHeaders, replaceHeaders);

    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));

    assertHeaderContains(callB.getLastReceivedRequest(), PriorityHeader.NAME, "5");
    assertHeaderContains(callB.getLastReceivedRequest(), ContentTypeHeader.NAME,
        "applicationn/texxt");
    assertHeaderContains(callB.getLastReceivedRequest(), ContactHeader.NAME, "doodah");
    assertHeaderContains(callB.getLastReceivedRequest(), MaxForwardsHeader.NAME, "61");
    assertBodyContains(callB.getLastReceivedRequest(), "my body");

    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
    Thread.sleep(300);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(callB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
    assertTrue("Shouldn't have received anything at the called party side", callB
        .getAllReceivedResponses().size() == 0);

    // verify RINGING was received
    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);
    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);
    callA.listenForDisconnect();
    Thread.sleep(100);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();

    Thread.sleep(500);
    ub.dispose();
  }

  @Test
  public void testMiscExtraParms() throws Exception {
    // test the remaining SipCall methods
    // that take extra parameters
    LOG.trace("testMiscExtraParms");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall phoneB = ub.createSipCall();
    phoneB.listenForIncomingCall();
    Thread.sleep(50);

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null);

    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(phoneB.waitForIncomingCall(5000));

    // create extra parameters for sendIncomingCallResponse()

    ArrayList<String> addnlHeaders = new ArrayList<>();
    addnlHeaders.add(new String("Priority: 5"));

    ArrayList<String> replaceHeaders = new ArrayList<>();
    replaceHeaders.add(new String("Contact: <sip:doodah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 62"));

    phoneB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600, "my body", "applicationn",
        "texxt", addnlHeaders, replaceHeaders);
    Thread.sleep(300);
    assertNotAnswered("Call leg shouldn't be answered yet", callA);
    assertNotAnswered(phoneB);

    // verify extra parameters were received in the message

    assertResponseReceived("Should have gotten RINGING response", SipResponse.RINGING, callA);

    SipResponse response = callA.findMostRecentResponse(SipResponse.RINGING);
    assertHeaderContains(response, PriorityHeader.NAME, "5");
    assertHeaderContains(response, ContentTypeHeader.NAME, "applicationn/texxt");
    assertHeaderContains(response, ContactHeader.NAME, "doodah");
    assertHeaderContains(response, MaxForwardsHeader.NAME, "62");
    assertBodyContains(response, "my body");

    phoneB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600);
    Thread.sleep(500);

    assertAnswered("Outgoing call leg not answered", callA);
    assertAnswered(phoneB);
    assertFalse("Outgoing call leg error status wrong", callA.callTimeoutOrError());

    assertTrue("Wrong number of responses received", callA.getAllReceivedResponses().size() >= 2);
    assertTrue("Shouldn't have received anything at the called party side", phoneB
        .getAllReceivedResponses().size() == 0);

    // verify OK was received
    assertResponseReceived(SipResponse.OK, callA);
    // check negative
    assertResponseNotReceived("Unexpected response", SipResponse.NOT_FOUND, callA);
    assertResponseNotReceived(SipResponse.ADDRESS_INCOMPLETE, callA);

    // verify getLastReceivedResponse() method
    assertEquals("Last response received wasn't answer", SipResponse.OK, callA
        .getLastReceivedResponse().getStatusCode());

    // create extra parameters for sendInviteOkAck()

    addnlHeaders.clear();
    addnlHeaders.add(new String("Priority: 6"));
    addnlHeaders.add(new String("Route: " + ub.getContactInfo().getURI()));

    replaceHeaders.clear();
    replaceHeaders.add(new String("Contact: <sip:dooodah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 63"));

    callA.sendInviteOkAck("my boddy", "application", "text", addnlHeaders, replaceHeaders);
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    // verify extra parameters were received in the message

    assertTrue(phoneB.waitForAck(1000));
    SipRequest request = phoneB.getLastReceivedRequest();
    assertHeaderContains(request, PriorityHeader.NAME, "6");
    assertHeaderContains(request, ContentTypeHeader.NAME, "application/text");
    assertHeaderContains(request, ContactHeader.NAME, "dooodah");
    assertHeaderContains(request, MaxForwardsHeader.NAME, "63");
    assertBodyContains(request, "my boddy");

    callA.listenForDisconnect();
    Thread.sleep(100);

    // create extra parameters for disconnect()

    addnlHeaders.clear();
    addnlHeaders.add(new String("Priority: 7"));

    replaceHeaders.clear();
    replaceHeaders.add(new String("Contact: <sip:doooodah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 64"));

    phoneB.disconnect("my bodddy", "appl", "txt", addnlHeaders, replaceHeaders);
    assertLastOperationSuccess("b disc - " + phoneB.format(), phoneB);

    // verify extra parameters were received in the message

    callA.waitForDisconnect(1000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    request = callA.getLastReceivedRequest();
    assertHeaderContains(request, PriorityHeader.NAME, "7");
    assertHeaderContains(request, ContentTypeHeader.NAME, "appl/txt");
    assertHeaderContains(request, ContactHeader.NAME, "doooodah");
    assertHeaderContains(request, MaxForwardsHeader.NAME, "64");
    assertBodyContains(request, "my bodddy");

    // create extra parameters for respondToDisconnect()

    addnlHeaders.clear();
    addnlHeaders.add(new String("Priority: 8"));
    addnlHeaders.add(new String("Route: " + ub.getContactInfo().getURI()));

    replaceHeaders.clear();
    replaceHeaders.add(new String("Contact: <sip:ddah@"
        + properties.getProperty("javax.sip.IP_ADDRESS") + ':' + myPort + '>'));
    replaceHeaders.add(new String("Max-Forwards: 65"));

    callA.respondToDisconnect(SipResponse.ACCEPTED, "OK", "my bdy", "app", "xt", addnlHeaders,
        replaceHeaders);

    // verify extra parameters were received in the message

    /*
     * TODO - BYE response gets to proxy but not to the far end - is it a proxy problem, or
     * something wrong we're doing here? Resolve and uncomment the following:
     * 
     * Thread.sleep(500); response = b.getLastReceivedResponse(); assertEquals(202,
     * response.getStatusCode()); assertHeaderContains(response, PriorityHeader.NAME, "8");
     * assertHeaderContains(response, ContentTypeHeader.NAME, "app/xt");
     * assertHeaderContains(response, ContactHeader.NAME, "ddah"); assertHeaderContains(response,
     * MaxForwardsHeader.NAME, "65"); assertBodyContains(response, "my bdy");
     */

    ub.dispose();
  }

  /**
   * Test: SipPhone.makeCall() with authentication, callee disc SipPhone.register() using pre-set
   * credentials
   */
  @Test
  public void testBothSidesCalleeDisc() throws Exception {
    // test the blocking version of
    // SipPhone.makeCall()
    LOG.trace("testBothSidesCalleeDisc");

    final class PhoneB extends Thread {

      public void run() {
        try {
          SipPhone ub =
              sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
                  proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
          ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"),
              "becky", "a1b2c3d4"));
          ub.register(null, 9600);

          SipCall callB = ub.createSipCall();

          callB.listenForIncomingCall();
          callB.waitForIncomingCall(5000);
          callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0);
          Thread.sleep(600);
          callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);

          Thread.sleep(2000);
          callB.disconnect();
          assertLastOperationSuccess("b disc - " + callB.format(), callB);
          Thread.sleep(2000);

          ub.dispose();

          return;
        } catch (Exception e) {
          fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
      }
    }

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 1800);

    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    PhoneB phoneB = new PhoneB();
    phoneB.start();

    // give user b time to register
    Thread.sleep(2000);

    SipCall callA =
        ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), SipResponse.OK,
            5000, null);
    assertLastOperationSuccess(ua.format(), ua);

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    callA.listenForDisconnect();
    callA.waitForDisconnect(10000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);
    callA.respondToDisconnect();

    phoneB.join();
  }

  /**
   * Test: SipCall send and receive RE-INVITE methods. This method tests re-invite from a to b,
   * TestNoProxy does the other direction
   */
  @Test
  public void testReinvite() throws Exception {
    LOG.trace("testReinvite");
    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);
    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.setLoopback(true);
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    // establish a call
    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(20);

    SipCall callA =
        ua.makeCall("sip:becky@cafesip.org", properties.getProperty("javax.sip.IP_ADDRESS") + ':'
            + myPort + '/' + testProtocol);
    // TODO - this is direct to ub for now - sipex discards reinvite
    // (bug 2847529)

    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    assertTrue(callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 600));
    Thread.sleep(200);
    assertResponseReceived(SipResponse.OK, callA);
    assertTrue(callA.sendInviteOkAck());
    Thread.sleep(300);

    // send request - test reinvite with no specific parameters

    callB.listenForReinvite();
    SipTransaction siptransA =
        callA.sendReinvite(null, null, (ArrayList<Header>) null, null, null);
    assertNotNull(siptransA);
    SipTransaction siptransB = callB.waitForReinvite(1000);
    assertNotNull(siptransB);

    SipMessage req = callB.getLastReceivedRequest();
    String origContactUriA =
        ((ContactHeader) req.getMessage().getHeader(ContactHeader.NAME)).getAddress().getURI()
            .toString();

    // check contact info
    assertEquals(ua.getContactInfo().getURI(), origContactUriA);
    assertHeaderNotContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(req, ContentTypeHeader.NAME);
    assertBodyNotPresent(req);

    // check additional headers
    assertHeaderNotPresent(req, PriorityHeader.NAME);
    assertHeaderNotPresent(req, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "70");

    // send response - test new contact only

    String origContactUriB = ub.getContactInfo().getURI();
    String contactNoLrB = origContactUriB.substring(0, origContactUriB.lastIndexOf("lr") - 1);
    assertTrue(callB.respondToReinvite(siptransB, SipResponse.OK, "ok reinvite response", -1,
        contactNoLrB, null, null, (String) null, null));

    assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    while (callA.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    }

    // check response code
    SipResponse response = callA.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite response", response.getReasonPhrase());

    // check contact info
    assertEquals(ub.getContactInfo().getURI(), contactNoLrB); // changed
    assertFalse(origContactUriB.equals(contactNoLrB));
    assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, contactNoLrB);
    assertHeaderNotContains(response, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(response, ContentTypeHeader.NAME);
    assertBodyNotPresent(response);

    // check additional headers
    assertHeaderNotPresent(response, PriorityHeader.NAME);
    assertHeaderNotPresent(response, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(response, ContentLengthHeader.NAME, "0");

    // send ACK
    assertTrue(callA.sendReinviteOkAck(siptransA));
    assertTrue(callB.waitForAck(1000));
    Thread.sleep(100); //

    // send request - test new contact and display name

    callB.listenForReinvite();
    String contactNoLrA = origContactUriA.substring(0, origContactUriA.lastIndexOf("lr") - 1);
    siptransA =
        callA.sendReinvite(contactNoLrA, "My DisplayName", (ArrayList<Header>) null, null, null);
    assertNotNull(siptransA);
    siptransB = callB.waitForReinvite(1000);
    assertNotNull(siptransB);

    req = callB.getLastReceivedRequest();

    // check contact info
    assertEquals(ua.getContactInfo().getURI(), contactNoLrA); // changed
    assertFalse(origContactUriA.equals(contactNoLrA));
    assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
    assertHeaderContains(req, ContactHeader.NAME, contactNoLrA);
    assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderNotPresent(req, ContentTypeHeader.NAME);
    assertBodyNotPresent(req);

    // check additional headers
    assertHeaderNotPresent(req, PriorityHeader.NAME);
    assertHeaderNotPresent(req, ReasonHeader.NAME);

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "70");

    // send response - test body only

    assertTrue(callB.respondToReinvite(siptransB, SipResponse.OK, "ok reinvite response", -1,
        null, null, "DooDah", "application", "text"));

    assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    while (callA.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    }

    // check response code
    response = callA.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite response", response.getReasonPhrase());

    // check contact info
    assertHeaderNotContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, contactNoLrB);
    assertHeaderNotContains(response, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderPresent(response, ContentTypeHeader.NAME);
    ContentTypeHeader ctHeader =
        (ContentTypeHeader) response.getMessage().getHeader(ContentTypeHeader.NAME);
    assertEquals("application", ctHeader.getContentType());
    assertEquals("text", ctHeader.getContentSubType());
    assertBodyContains(response, "DooDah");

    // check additional headers
    assertHeaderNotPresent(response, PriorityHeader.NAME);
    assertHeaderNotPresent(response, ReasonHeader.NAME);

    // check override headers
    // done, content sub type not overidden

    // send ACK
    assertTrue(callA.sendReinviteOkAck(siptransA));
    assertTrue(callB.waitForAck(1000));
    Thread.sleep(100);

    // send request - test additional & replace headers (String)

    callB.listenForReinvite();

    ArrayList<String> addnlHeaders = new ArrayList<>(2);
    addnlHeaders.add("Priority: Urgent");
    addnlHeaders.add("Reason: SIP; cause=41; text=\"I made it up\"");

    ArrayList<String> replaceHeaders = new ArrayList<>(1);
    MaxForwardsHeader hdr = ua.getParent().getHeaderFactory().createMaxForwardsHeader(22);
    replaceHeaders.add(hdr.toString());

    siptransA =
        callA.sendReinvite(null, null, "mybody", "myapp", "mysubapp", addnlHeaders, replaceHeaders);
    assertNotNull(siptransA);
    siptransB = callB.waitForReinvite(1000);
    assertNotNull(siptransB);

    req = callB.getLastReceivedRequest();

    // check contact info
    assertHeaderNotContains(req, ContactHeader.NAME, ";lr");
    assertHeaderContains(req, ContactHeader.NAME, contactNoLrA);
    assertHeaderContains(req, ContactHeader.NAME, "My DisplayName");

    // check body
    assertHeaderContains(req, ContentTypeHeader.NAME, "myapp/mysubapp");
    assertBodyContains(req, "mybo");

    // check additional headers
    assertHeaderContains(req, PriorityHeader.NAME, PriorityHeader.URGENT);
    assertHeaderContains(req, ReasonHeader.NAME, "41");
    assertHeaderContains(req, ReasonHeader.NAME, "I made it up");

    // check override headers
    assertHeaderContains(req, MaxForwardsHeader.NAME, "22");

    // test everything

    ArrayList<Header> addnlHdrHeaders = new ArrayList<>();
    PriorityHeader priHeaders =
        ub.getParent().getHeaderFactory().createPriorityHeader(PriorityHeader.NORMAL);
    ReasonHeader reasonHeaders =
        ub.getParent().getHeaderFactory().createReasonHeader("SIP", 42, "I made it up");
    ctHeader = ub.getParent().getHeaderFactory().createContentTypeHeader("applicationn", "sdp");
    addnlHdrHeaders.add(priHeaders);
    addnlHdrHeaders.add(reasonHeaders);
    addnlHdrHeaders.add(ctHeader);

    ArrayList<Header> replaceHdrHeaders = new ArrayList<>();
    replaceHdrHeaders.add(ub.getParent().getHeaderFactory()
        .createContentTypeHeader("mycontenttype", "mycontentsubtype"));

    assertTrue(callB.respondToReinvite(siptransB, SipResponse.OK, "ok reinvite last response",
        -1, origContactUriB, "Original info", addnlHdrHeaders, replaceHdrHeaders, "DooDahDay"));

    assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    while (callA.getLastReceivedResponse().getStatusCode() == Response.TRYING) {
      assertTrue(callA.waitReinviteResponse(siptransA, 2000));
    }

    // check response code
    response = callA.getLastReceivedResponse();
    assertEquals(Response.OK, response.getStatusCode());
    assertEquals("ok reinvite last response", response.getReasonPhrase());

    // check contact info
    assertEquals(ub.getContactInfo().getURI(), origContactUriB); // changed
    assertFalse(origContactUriB.equals(contactNoLrB));
    assertHeaderContains(response, ContactHeader.NAME, ";lr");
    assertHeaderContains(response, ContactHeader.NAME, origContactUriB);
    assertHeaderContains(response, ContactHeader.NAME, "Original info");

    // check body
    assertHeaderPresent(response, ContentTypeHeader.NAME);
    ctHeader = (ContentTypeHeader) response.getMessage().getHeader(ContentTypeHeader.NAME);
    assertEquals("mycontenttype", ctHeader.getContentType());
    assertEquals("mycontentsubtype", ctHeader.getContentSubType());
    assertBodyContains(response, "DooD");

    // check additional headers
    assertHeaderContains(response, PriorityHeader.NAME, PriorityHeader.NORMAL);
    assertHeaderContains(response, ReasonHeader.NAME, "42");

    // check override headers
    assertHeaderContains(response, ContentTypeHeader.NAME, "mycontenttype/mycontentsubtype");

    // send ACK
    // with additional, replacement String headers, and body
    addnlHeaders = new ArrayList<>(1);
    addnlHeaders.add("Event: presence");

    replaceHeaders = new ArrayList<>(3);
    replaceHeaders.add("Max-Forwards: 29");
    replaceHeaders.add("Priority: Urgent");
    replaceHeaders.add("Reason: SIP; cause=44; text=\"dummy\"");

    assertTrue(callA.sendReinviteOkAck(siptransA, "ack body", "mytype", "mysubtype", addnlHeaders,
        replaceHeaders));
    assertTrue(callB.waitForAck(1000));
    SipRequest reqAck = callB.getLastReceivedRequest();

    assertHeaderContains(reqAck, ReasonHeader.NAME, "dummy");
    assertHeaderContains(reqAck, MaxForwardsHeader.NAME, "29");
    assertHeaderContains(reqAck, PriorityHeader.NAME, "gent");
    assertHeaderContains(reqAck, EventHeader.NAME, "presence");
    assertHeaderContains(reqAck, ContentTypeHeader.NAME, "mysubtype");
    assertBodyContains(reqAck, "ack body");

    Thread.sleep(100); //

    // done, finish up
    callB.listenForDisconnect();
    Thread.sleep(100);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(5000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);

    callB.respondToDisconnect();

    Thread.sleep(100);
    ub.dispose();
  }

  // this method tests cancel from a to b
  @Test
  public void testCancel() throws Exception {
    LOG.trace("testCancel");

    ua.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "amit",
        "a1b2c3d4"));
    ua.register(null, 3600);
    assertLastOperationSuccess(
        "Caller registration using pre-set credentials failed - " + ua.format(), ua);

    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, "sip:becky@" + properties.getProperty("sipunit.test.domain"));
    ub.addUpdateCredential(new Credential(properties.getProperty("sipunit.test.domain"), "becky",
        "a1b2c3d4"));
    ub.register(null, 9600);
    assertLastOperationSuccess(
        "Callee registration using pre-set credentials failed - " + ub.format(), ub);

    SipCall callB = ub.createSipCall();
    callB.listenForIncomingCall();
    Thread.sleep(100);

    SipCall callA = ua.makeCall("sip:becky@" + properties.getProperty("sipunit.test.domain"), null);
    assertLastOperationSuccess(ua.format(), ua);

    assertTrue(callB.waitForIncomingCall(5000));
    callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 600);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);
    Thread.sleep(200);
    assertResponseReceived(SipResponse.RINGING, callA);
    Thread.sleep(300);

    // Initiate the Cancel
    callB.listenForCancel();
    Thread.sleep(500);
    SipTransaction cancel = callA.sendCancel();
    assertNotNull(cancel);

    // check b
    SipTransaction trans1 = callB.waitForCancel(5000);
    assertNotNull(trans1);
    assertRequestReceived("CANCEL NOT RECEIVED", SipRequest.CANCEL, callB);
    assertTrue(callB.respondToCancel(trans1, 200, "0K", -1));

    // check a
    assertTrue(callA.waitForCancelResponse(cancel, 5000));
    Thread.sleep(500);
    assertResponseReceived("200 OK NOT RECEIVED", SipResponse.OK, callA);

    // check that the original INVITE transaction got responded to
    Thread.sleep(100);
    assertResponseReceived("487 Request Not Terminated NOT RECEIVED",
        SipResponse.REQUEST_TERMINATED, callA);

    // close the INVITE transaction on the called leg
    assertTrue("487 NOT SENT",
        callB.sendIncomingCallResponse(SipResponse.REQUEST_TERMINATED, "Request Terminated", 0));
  }
}
