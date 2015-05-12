/*
 * Created on April 21, 2005
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

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import javax.sip.message.Response;

/**
 * This class tests two SipStacks on the same machine. This test makes use of the fact that with
 * JAIN-SIP 1.2, if you don't provide IP_ADDRESS in the properties when you create a stack, it goes
 * by STACK_NAME only and you can create as many stacks as you want as long as the name is different
 * (and IP_ADDRESS property is null). You can use system properties to override settings in this
 * test except for the IP_ADDRESS property used on the SipStack(s) - it will be ignored by this
 * particular test.
 * 
 * <p>
 * Thanks to Venkita S. for contributing the changes to SipSession and SipStack needed to make this
 * work.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server. Messaging between UACs is direct.
 * 
 * @author Becky McElroy
 * 
 */
public class TestTwoOrMoreSipStacksNoProxy {

  private SipStack sipStack1;

  private SipStack sipStack2;

  private SipPhone ua;

  private int port1 = 5061;

  private int port2 = 5090;

  private String testProtocol = "udp";

  private boolean sipunitTrace = true;

  private static final Properties defaultProperties1 = new Properties();

  private static final Properties defaultProperties2 = new Properties();

  static {
    defaultProperties1.setProperty("javax.sip.STACK_NAME", "testAgent");
    defaultProperties1.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties1.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
    defaultProperties1.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
    defaultProperties1.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties1.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties2.setProperty("javax.sip.STACK_NAME", "testAgent2");
    defaultProperties2.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
    defaultProperties2.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    defaultProperties2.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties2.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  private Properties properties1 = new Properties(defaultProperties1);

  private Properties properties2 = new Properties(defaultProperties2);

  public TestTwoOrMoreSipStacksNoProxy() {
    Properties inputProps = new Properties();
    inputProps.putAll(System.getProperties());

    String prop = inputProps.getProperty("sipunit.testport.1");
    if (prop != null) {
      try {
        port1 = Integer.parseInt(prop);
      } catch (NumberFormatException e) {
        System.err.println("Number format exception for input port: " + prop
            + " - defaulting port1 to 5061");
        port1 = 5061;
      }
    }

    prop = inputProps.getProperty("sipunit.testport.2");
    if (prop != null) {
      try {
        port2 = Integer.parseInt(prop);
      } catch (NumberFormatException e) {
        System.err.println("Number format exception for input port: " + prop
            + " - defaulting port2 to 5091");
        port2 = 5091;
      }
    }

    prop = inputProps.getProperty("sipunit.test.protocol");
    if (prop != null) {
      testProtocol = prop;
    }
  }

  /**
   * Initialize the sipStack 1 & 2 and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack1 = new SipStack(testProtocol, port1, properties1);
    sipStack2 = new SipStack(testProtocol, port2, properties2);

    ua = sipStack1.createSipPhone("sip:amit@nist.gov");
    ua.setLoopback(true);
  }

  /**
   * Release the sipStack 1 & 2 and a user agent for the test.
   */
  @After
  public void tearDown() throws Exception {
    ua.dispose();
    awaitStackDispose(sipStack1);
    awaitStackDispose(sipStack2);
  }

  @Test
  public void testBothSides() throws Exception {
    SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();

    callA.initiateOutgoingCall("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2 + ";lr/"
        + testProtocol);
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);

    callB.waitForIncomingCall(4000);
    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.RINGING, null, -1);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("b send OK - " + callB.format(), callB);

    callA.waitOutgoingCallResponse(5000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    assertEquals("Unexpected 1st response received", Response.RINGING, callA.getReturnCode());
    assertNotNull("Default response reason not sent", callA.getLastReceivedResponse()
        .getReasonPhrase());
    assertEquals("Unexpected default reason", "Ringing", callA.getLastReceivedResponse()
        .getReasonPhrase());

    callA.waitOutgoingCallResponse(5000);
    assertLastOperationSuccess("a wait 2nd response - " + callA.format(), callA);

    assertEquals("Unexpected 2nd response received", Response.OK, callA.getReturnCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    callA.listenForDisconnect();
    assertLastOperationSuccess("a listen disc - " + callA.format(), callA);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(3000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();
    assertLastOperationSuccess("a respond to disc - " + callA.format(), callA);

    ub.dispose();
  }
}
