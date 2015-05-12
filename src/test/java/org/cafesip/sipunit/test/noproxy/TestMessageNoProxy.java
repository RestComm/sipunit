/*
 * Created on Feb 7, 2012
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Properties;

import javax.sip.DialogState;
import javax.sip.message.Response;

/**
 * This class tests SipUnit API MESSAGE methods.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server. Messaging between UACs is direct.
 * 
 * <p>
 * Also tests that MESSAGE request goes through an established dialog.
 * 
 * @author <a href="mailto:gvagenas@gmail.com">George Vagenas</a>
 *
 */
public class TestMessageNoProxy {
  private static final Logger LOG = LoggerFactory.getLogger(TestMessageNoProxy.class);

  private SipStack sipStack1;

  private SipStack sipStack2;

  private SipPhone ua;

  private int port1 = 5061;

  private int port2 = 5090;

  private String testProtocol = "udp";

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

  public TestMessageNoProxy() {
    Properties inputProps = new Properties();
    inputProps.putAll(System.getProperties());

    String prop = inputProps.getProperty("sipunit.testport.1");
    if (prop != null) {
      try {
        port1 = Integer.parseInt(prop);
      } catch (NumberFormatException e) {
        LOG.error("Number format exception for input port: " + prop
            + " - defaulting port1 to 5061");
        port1 = 5061;
      }
    }

    prop = inputProps.getProperty("sipunit.testport.2");
    if (prop != null) {
      try {
        port2 = Integer.parseInt(prop);
      } catch (NumberFormatException e) {
        LOG.error("Number format exception for input port: " + prop
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
   * Initialize the sipStack and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack1 = new SipStack(testProtocol, port1, properties1);
    sipStack2 = new SipStack(testProtocol, port2, properties2);

    ua = sipStack1.createSipPhone("sip:amit@nist.gov");
    ua.setLoopback(true);
  }

  /**
   * Release the sipStack and a user agent for the test.
   */
  @After
  public void tearDown() throws Exception {
    ua.dispose();
    awaitStackDispose(sipStack1);
    awaitStackDispose(sipStack2);
  }

  @Test
  public void testMessageBothSides() throws Exception {
    SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    /*
     * callA send MESSAGE to callB
     */
    callB.listenForMessage();

    callA.initiateOutgoingMessage("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2
        + ";lr/" + testProtocol, "Hello Becky");

    assertLastOperationSuccess("a initiate MESSAGE - " + callA.format(), callA);


    assertTrue(callB.waitForMessage(4000));
    List<String> msgsFromA = callB.getAllReceivedMessagesContent();

    assertTrue(msgsFromA.size() > 0);
    assertTrue(msgsFromA.get(0).equals("Hello Becky"));

    callB.sendMessageResponse(200, "OK", -1);

    // The dialog should be null
    assertNull(callB.getDialog());

    assertTrue(callA.waitOutgoingMessageResponse(4000));
    assertEquals(Response.OK, callA.getLastReceivedResponse().getStatusCode());

    /*
     * callB send MESSAGE to callB
     */
    callA.listenForMessage();

    callB.initiateOutgoingMessage("sip:amit@nist.gov", ua.getStackAddress() + ":" + port1
        + ";lr/" + testProtocol, "Hello Amit");

    assertLastOperationSuccess("b initiate MESSAGE - " + callB.format(), callB);

    assertTrue(callA.waitForMessage(4000));
    List<String> msgsFromB = callA.getAllReceivedMessagesContent();

    assertTrue(msgsFromB.size() > 0);
    assertTrue(msgsFromB.get(0).equals("Hello Amit"));

    callA.sendMessageResponse(200, "OK", -1);

    // The dialog should be null
    assertNull(callA.getDialog());

    assertTrue(callB.waitOutgoingMessageResponse(4000));
    assertEquals(Response.OK, callB.getLastReceivedResponse().getStatusCode());

    ub.dispose();
  }

  // Send MESSAGE replies with body
  @Test
  public void testMessageBothSidesWithResponseBody() throws Exception {
    SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    /*
     * callA send MESSAGE to callB
     */
    callB.listenForMessage();

    callA.initiateOutgoingMessage("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2
        + ";lr/" + testProtocol, "Hello Becky");

    assertLastOperationSuccess("a initiate MESSAGE - " + callA.format(), callA);


    assertTrue(callB.waitForMessage(4000));
    List<String> msgsFromA = callB.getAllReceivedMessagesContent();

    assertTrue(msgsFromA.size() > 0);
    assertTrue(msgsFromA.get(0).equals("Hello Becky"));

    // Send reply with a MESSAGE BODY
    callB.sendMessageResponse(200, "OK", -1, "Hello Amit");

    // The dialog should be null
    assertNull(callB.getDialog());

    assertTrue(callA.waitOutgoingMessageResponse(4000));
    SipResponse responseA = callA.getLastReceivedResponse();
    assertEquals(Response.OK, responseA.getStatusCode());
    assertEquals("Hello Amit", new String(responseA.getRawContent()));
    /*
     * callB send MESSAGE to callB
     */
    callA.listenForMessage();

    callB.initiateOutgoingMessage("sip:amit@nist.gov", ua.getStackAddress() + ":" + port1
        + ";lr/" + testProtocol, "Hello Amit");

    assertLastOperationSuccess("b initiate MESSAGE - " + callB.format(), callB);

    assertTrue(callA.waitForMessage(4000));
    List<String> msgsFromB = callA.getAllReceivedMessagesContent();

    assertTrue(msgsFromB.size() > 0);
    assertTrue(msgsFromB.get(0).equals("Hello Amit"));

    // Send reply with a MESSAGE BODY
    callA.sendMessageResponse(200, "OK", -1, "Hello Again Becky");

    // The dialog should be null
    assertNull(callA.getDialog());

    assertTrue(callB.waitOutgoingMessageResponse(4000));
    SipResponse responseB = callB.getLastReceivedResponse();
    assertEquals(Response.OK, responseB.getStatusCode());
    assertEquals("Hello Again Becky", new String(responseB.getRawContent()));

    ub.dispose();
  }

  /*
   * Initiate an outgoing call so we have an established dialog and send messages using it
   */
  @Test
  public void testMessageBothSidesWithinDialog() throws Exception {
    SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    // Initiate a call from A to B and check that we have a CONFIRMED dialog

    callB.listenForIncomingCall();

    callA.initiateOutgoingCall("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2 + ";lr/"
        + testProtocol);
    assertLastOperationSuccess("a initiate OutgoingCall - " + callA.format(), callA);

    assertTrue(callB.waitForIncomingCall(1000));

    assertTrue(callB.sendIncomingCallResponse(100, "TRYING", -1));
    assertTrue(callB.sendIncomingCallResponse(180, "RINGING", -1));

    assertTrue(callA.waitOutgoingCallResponse());

    assertTrue(callB.sendIncomingCallResponse(200, "OK", -1));

    assertTrue(callA.sendInviteOkAck());

    assertEquals(DialogState.CONFIRMED, callB.getDialog().getState());
    assertEquals(DialogState.CONFIRMED, callA.getDialog().getState());

    /*
     * callA send MESSAGE to callB
     */
    callB.listenForMessage();

    callA.initiateOutgoingMessage("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2
        + ";lr/" + testProtocol, "Hello Becky");

    assertLastOperationSuccess("a initiate MESSAGE - " + callA.format(), callA);


    assertTrue(callB.waitForMessage(4000));
    List<String> msgsFromA = callB.getAllReceivedMessagesContent();

    assertTrue(msgsFromA.size() > 0);
    assertTrue(msgsFromA.get(0).equals("Hello Becky"));

    callB.sendMessageResponse(200, "OK", -1);

    // Check we are inside a dialog
    assertNotNull(callB.getDialog());
    assertEquals(DialogState.CONFIRMED, callB.getDialog().getState());

    assertTrue(callA.waitOutgoingMessageResponse(4000));
    assertEquals(Response.OK, callA.getLastReceivedResponse().getStatusCode());

    /*
     * callB send MESSAGE to callB
     */
    callA.listenForMessage();

    callB.initiateOutgoingMessage("sip:amit@nist.gov", ua.getStackAddress() + ":" + port1
        + ";lr/" + testProtocol, "Hello Amit");

    assertLastOperationSuccess("b initiate MESSAGE - " + callB.format(), callB);

    assertTrue(callA.waitForMessage(4000));
    List<String> msgsFromB = callA.getAllReceivedMessagesContent();

    assertTrue(msgsFromB.size() > 0);
    assertTrue(msgsFromB.get(0).equals("Hello Amit"));

    callA.sendMessageResponse(200, "OK", -1);

    // Check we are inside a dialog
    assertNotNull(callA.getDialog());
    assertEquals(DialogState.CONFIRMED, callA.getDialog().getState());

    assertTrue(callB.waitOutgoingMessageResponse(4000));
    assertEquals(Response.OK, callB.getLastReceivedResponse().getStatusCode());

    // Should be able to disconnect the previously created call
    assertTrue(callA.disconnect());

    ub.dispose();
  }

  /*
   * Initiate an outgoing call so we have an established dialog and send messages using it MESSAGE
   * replies contain BODY
   */
  @Test
  public void testMessageBothSidesWithinDialogWithResponseBody() throws Exception {
    SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
    ub.setLoopback(true);

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    // Initiate a call from A to B and check that we have a CONFIRMED dialog

    callB.listenForIncomingCall();

    callA.initiateOutgoingCall("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2 + ";lr/"
        + testProtocol);
    assertLastOperationSuccess("a initiate OutgoingCall - " + callA.format(), callA);

    assertTrue(callB.waitForIncomingCall(1000));

    assertTrue(callB.sendIncomingCallResponse(100, "TRYING", -1));
    assertTrue(callB.sendIncomingCallResponse(180, "RINGING", -1));

    assertTrue(callA.waitOutgoingCallResponse());

    assertTrue(callB.sendIncomingCallResponse(200, "OK", -1));

    assertTrue(callA.sendInviteOkAck());

    assertEquals(DialogState.CONFIRMED, callB.getDialog().getState());
    assertEquals(DialogState.CONFIRMED, callA.getDialog().getState());

    /*
     * callA send MESSAGE to callB
     */
    callB.listenForMessage();

    callA.initiateOutgoingMessage("sip:becky@nist.gov", ub.getStackAddress() + ":" + port2
        + ";lr/" + testProtocol, "Hello Becky");

    assertLastOperationSuccess("a initiate MESSAGE - " + callA.format(), callA);


    assertTrue(callB.waitForMessage(4000));
    List<String> msgsFromA = callB.getAllReceivedMessagesContent();

    assertTrue(msgsFromA.size() > 0);
    assertTrue(msgsFromA.get(0).equals("Hello Becky"));

    // Send reply with a MESSAGE BODY
    callB.sendMessageResponse(200, "OK", -1, "Hello Amit");

    // Check we are inside a dialog
    assertNotNull(callB.getDialog());
    assertEquals(DialogState.CONFIRMED, callB.getDialog().getState());

    assertTrue(callA.waitOutgoingMessageResponse(4000));
    SipResponse responseA = callA.getLastReceivedResponse();
    assertEquals(Response.OK, responseA.getStatusCode());
    assertEquals("Hello Amit", new String(responseA.getRawContent()));

    /*
     * callB send MESSAGE to callB
     */
    callA.listenForMessage();

    callB.initiateOutgoingMessage("sip:amit@nist.gov", ua.getStackAddress() + ":" + port1
        + ";lr/" + testProtocol, "Hello Amit");

    assertLastOperationSuccess("b initiate MESSAGE - " + callB.format(), callB);

    assertTrue(callA.waitForMessage(4000));
    List<String> msgsFromB = callA.getAllReceivedMessagesContent();

    assertTrue(msgsFromB.size() > 0);
    assertTrue(msgsFromB.get(0).equals("Hello Amit"));

    callA.sendMessageResponse(200, "OK", -1, "Hello Again Becky");

    // Check we are inside a dialog
    assertNotNull(callA.getDialog());
    assertEquals(DialogState.CONFIRMED, callA.getDialog().getState());

    assertTrue(callB.waitOutgoingMessageResponse(4000));
    SipResponse responseB = callB.getLastReceivedResponse();
    assertEquals(Response.OK, responseB.getStatusCode());
    assertEquals("Hello Again Becky", new String(responseB.getRawContent()));

    // Should be able to disconnect the previously created call
    assertTrue(callA.disconnect());

    ub.dispose();
  }
}
