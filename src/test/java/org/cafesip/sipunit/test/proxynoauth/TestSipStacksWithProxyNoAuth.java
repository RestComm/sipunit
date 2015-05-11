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

package org.cafesip.sipunit.test.proxynoauth;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import javax.sip.message.Response;

/**
 * This class tests multiple SipStacks on the same machine.
 * 
 * <p>
 * Tests in this class require that a Proxy/registrar server be running with authentication turned
 * off. Defaults: proxy host = 192.168.112.1, port = 5060, protocol = udp.
 * 
 * @author Becky McElroy
 * 
 *         sample ant script to run it outside of IDE: <?xml version="1.0"?> <project name="siptest"
 *         basedir="c:/sw/eclipse-workspace/sipunit" default="test">
 * 
 *         <target name="test" description="Run test test"> <ant dir="c:/jain-sip-presence-proxy"
 *         target="run-text-proxy" inheritAll="false"/> <antcall target="runtest"/> </target>
 * 
 *         <target name="runtest" description="Execute JUNit test"> <path id="test.classpath">
 *         <fileset dir="lib"> <include name="*.jar" /> </fileset> <pathelement location="src"/>
 *         </path> <junit fork="false"> <test
 *         name="org.cafesip.sipunit.test.TestSipStacksWithProxyNoAuth"/> <formatter type="brief"
 *         usefile="false"/> <classpath refid="test.classpath" /> </junit> </target> </project>
 * 
 */
public class TestSipStacksWithProxyNoAuth {

  private SipStack sipStack1;

  private SipStack sipStack2;

  private static String PROXY_HOST = "192.168.112.1";

  private static int PROXY_PORT = 5060;

  private static String PROXY_PROTO = "udp";

  private SipPhone ua;

  private Properties properties1 = new Properties();

  private Properties properties2 = new Properties();

  public TestSipStacksWithProxyNoAuth() {
    properties1.setProperty("javax.sip.RETRANSMISSION_FILTER", "true");
    properties1.setProperty("javax.sip.STACK_NAME", "testAgent");
    properties1.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    properties1.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
    properties1.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
    properties1.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");

    properties2.setProperty("javax.sip.RETRANSMISSION_FILTER", "true");
    properties2.setProperty("javax.sip.STACK_NAME", "testAgent2");
    properties2.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    properties2.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent2_debug.txt");
    properties2.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent2_log.txt");
    properties2.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
  }

  /**
   * Initialize the sipStack 1 & 2 and a user agent for the test.
   */
  @Before
  public void setUp() throws Exception {
    sipStack1 = new SipStack(PROXY_PROTO, 5061, properties1);
    sipStack2 = new SipStack(PROXY_PROTO, 5090, properties2);

    ua = sipStack1.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:amit@cafesip.org");
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
    assertTrue(ua.register("amit", "a1b2c3d4", null, 600, 5000));

    SipPhone ub =
        sipStack2.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");
    assertTrue(ub.register("becky", "a1b2c3d4", null, 600, 5000));

    SipCall callA = ua.createSipCall();
    SipCall callB = ub.createSipCall();

    callB.listenForIncomingCall();
    Thread.sleep(100);

    callA.initiateOutgoingCall("sip:becky@cafesip.org", null); // "127.0.0.1:4000/UDP"
    assertLastOperationSuccess("a initiate call - " + callA.format(), callA);

    callB.waitForIncomingCall(3000);
    assertLastOperationSuccess("b wait incoming call - " + callB.format(), callB);

    callB.sendIncomingCallResponse(Response.RINGING, null, -1);
    assertLastOperationSuccess("b send RINGING - " + callB.format(), callB);

    Thread.sleep(1000);

    callB.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
    assertLastOperationSuccess("b send OK - " + callB.format(), callB);

    callA.waitOutgoingCallResponse(10000);
    assertLastOperationSuccess("a wait 1st response - " + callA.format(), callA);
    assertTrue("Unexpected 1st response received",
        (callA.getReturnCode() == Response.RINGING || callA.getReturnCode() == Response.TRYING));
    if (callA.getLastReceivedResponse().getStatusCode() == Response.RINGING) {
      assertNotNull("Default response reason not sent", callA.getLastReceivedResponse()
          .getReasonPhrase());
      assertEquals("Unexpected default reason", "Ringing", callA.getLastReceivedResponse()
          .getReasonPhrase());
    }

    callA.waitOutgoingCallResponse(5000);
    assertLastOperationSuccess("a wait 2nd response - " + callA.format(), callA);
    callA.waitOutgoingCallResponse(2000);

    assertEquals("Unexpected final response received", Response.OK, callA.getLastReceivedResponse()
        .getStatusCode());

    callA.sendInviteOkAck();
    assertLastOperationSuccess("Failure sending ACK - " + callA.format(), callA);

    Thread.sleep(1000);

    callA.listenForDisconnect();
    assertLastOperationSuccess("a listen disc - " + callA.format(), callA);

    callB.disconnect();
    assertLastOperationSuccess("b disc - " + callB.format(), callB);

    callA.waitForDisconnect(5000);
    assertLastOperationSuccess("a wait disc - " + callA.format(), callA);

    callA.respondToDisconnect();
    assertLastOperationSuccess("a respond to disc - " + callA.format(), callA);

    Thread.sleep(1000);
    ub.dispose();
  }
}
