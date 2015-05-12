/*
 * Created on Aug 17, 2005
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

package org.cafesip.sipunit.test.proxynoauth;

import static org.cafesip.sipunit.SipAssert.assertHeaderContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotContains;
import static org.cafesip.sipunit.SipAssert.assertHeaderNotPresent;
import static org.cafesip.sipunit.SipAssert.assertHeaderPresent;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

import javax.sip.message.Response;

/**
 * This class tests some SipUnit API methods.
 * 
 * <p>
 * Tests in this class require that a Proxy/registrar server be running with authentication turned
 * off. Defaults: proxy host = 192.168.112.1, port = 5060, protocol = udp; user amit password
 * a1b2c3d4 and user becky password a1b2c3d4 defined at the proxy.
 * 
 * <p>
 * For the Proxy/registrar, I used cafesip.org's SipExchange.
 */

public class ExampleTestWithProxyNoAuth {

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
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5061");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");

    defaultProperties.setProperty("sipunit.test.domain", "cafesip.org");
    defaultProperties.setProperty("sipunit.proxy.host", "192.168.112.1");
    defaultProperties.setProperty("sipunit.proxy.port", "5060");
  }

  private Properties properties = new Properties(defaultProperties);

  public ExampleTestWithProxyNoAuth() {
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 5061;
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

  /**
   * This test illustrates usage of SipTestCase. In it, user a calls user b, user b sends RINGING
   * and OK, the test verifies these are received by user a, then the call proceeds through
   * disconnect (BYE).
   */
  @Test
  public void testBothSidesCallerDisc() throws Exception {
    // invoke the Sip operation, then separately check positive result;
    // include all error details in output (via ua.format()) if the test
    // fails:

    ua.register(null, 1800);
    assertLastOperationSuccess("Caller registration failed - " + ua.format(), ua);

    String userB = "sip:becky@" + properties.getProperty("sipunit.test.domain");
    SipPhone ub =
        sipStack.createSipPhone(properties.getProperty("sipunit.proxy.host"), testProtocol,
            proxyPort, userB);

    // invoke the Sip operation, then separately check positive result;
    // no failure/error details, just the standard JUnit fail output:

    ub.register(null, 600);
    assertLastOperationSuccess(ub);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();
    Thread.sleep(10);

    // another way to invoke the operation and check the result
    // separately:

    boolean statusOk = callA.initiateOutgoingCall(userB, null);
    assertTrue("Initiate outgoing call failed - " + callA.format(), statusOk);

    // invoke the Sip operation and check positive result in one step,
    // no operation error details if the test fails:

    assertTrue("Wait incoming call error or timeout", callB.waitForIncomingCall(5000));

    // invoke the Sip operation and result check in one step,
    // only standard JUnit output if the test fails:

    assertTrue(callB.sendIncomingCallResponse(Response.RINGING, "Ringing", 0));

    Thread.sleep(1000);

    // although the 2-step method is not as compact, it's easier
    // to follow what a test is doing since the Sip operations are not
    // buried as parameters in assert statements:

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("Sending answer response failed - " + callB.format(), callB);

    // note with the single step method, you cannot include operation
    // error details for when the test fails: ' + a.format()' wouldn't
    // work in the first parameter here:

    assertTrue("Wait response error", callA.waitOutgoingCallResponse(10000));

    SipResponse resp = callA.getLastReceivedResponse(); // watch for TRYING
    int statusCode = resp.getStatusCode();
    while (statusCode != Response.RINGING) {
      assertFalse("Unexpected final response, status = " + statusCode, statusCode > 200);

      assertFalse("Got OK but no RINGING", statusCode == Response.OK);

      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
      resp = callA.getLastReceivedResponse();
      statusCode = resp.getStatusCode();
    }

    // if you want operation error details in your test fail output,
    // you have to invoke and complete the operation first:

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("Wait response error - " + callA.format(), callA);

    // throw out any 'TRYING' responses
    // Note, you can also get the response status code from the SipCall
    // class itself (in addition to getting it from the response as
    // above)
    while (callA.getReturnCode() == Response.TRYING) {
      callA.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("Subsequent response never received - " + callA.format(), callA);
    }
    resp = callA.getLastReceivedResponse();

    // check for OK response.
    assertEquals("Unexpected response received", Response.OK, callA.getReturnCode());

    // check out some header asserts
    assertHeaderContains(resp, "From", myUrl);
    assertHeaderNotContains(resp, "From", myUrl + 'm');
    assertHeaderPresent(resp, "CSeq");
    assertHeaderNotPresent(resp, "Content-Type");

    // continue with the test call
    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    callB.listenForDisconnect();
    assertLastOperationSuccess("b listen disc - " + callB.format(), callB);

    callA.disconnect();
    assertLastOperationSuccess("a disc - " + callA.format(), callA);

    callB.waitForDisconnect(10000);
    assertLastOperationSuccess("b wait disc - " + callB.format(), callB);

    callB.respondToDisconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    ub.unregister(null, 10000);
    assertLastOperationSuccess("unregistering user b - " + ub.format(), ub);
  }
}
