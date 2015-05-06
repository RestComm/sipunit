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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.Properties;
import javax.sip.message.Response;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * This class tests multiple SipStacks on the same machine.
 * 
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

  @Before
  public void setUp() throws Exception {
    try {
      sipStack1 = new SipStack(PROXY_PROTO, 5061, properties1);
      sipStack2 = new SipStack(PROXY_PROTO, 5090, properties2);
    } catch (Exception ex) {
      fail("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      throw ex;
    }

    try {
      ua = sipStack1.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:amit@cafesip.org");
    } catch (Exception ex) {
      fail("Exception creating SipPhone: " + ex.getClass().getName() + ": " + ex.getMessage());
      throw ex;
    }
  }

  @After
  public void tearDown() throws Exception {
    ua.dispose();
    sipStack1.dispose();
    sipStack2.dispose();
  }

  @Test
  public void testBothSides() {
    try {
      assertTrue(ua.register("amit", "a1b2c3d4", null, 600, 5000));

      SipPhone ub =
          sipStack2.createSipPhone(PROXY_HOST, PROXY_PROTO, PROXY_PORT, "sip:becky@cafesip.org");
      assertTrue(ub.register("becky", "a1b2c3d4", null, 600, 5000));

      SipCall a = ua.createSipCall();
      SipCall b = ub.createSipCall();

      b.listenForIncomingCall();
      Thread.sleep(100);

      a.initiateOutgoingCall("sip:becky@cafesip.org", null); // "127.0.0.1:4000/UDP"
      assertLastOperationSuccess("a initiate call - " + a.format(), a);

      b.waitForIncomingCall(3000);
      assertLastOperationSuccess("b wait incoming call - " + b.format(), b);

      b.sendIncomingCallResponse(Response.RINGING, null, -1);
      assertLastOperationSuccess("b send RINGING - " + b.format(), b);

      Thread.sleep(1000);

      b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
      assertLastOperationSuccess("b send OK - " + b.format(), b);

      a.waitOutgoingCallResponse(10000);
      assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
      assertTrue("Unexpected 1st response received",
          (a.getReturnCode() == Response.RINGING || a.getReturnCode() == Response.TRYING));
      if (a.getLastReceivedResponse().getStatusCode() == Response.RINGING) {
        assertNotNull("Default response reason not sent", a.getLastReceivedResponse()
            .getReasonPhrase());
        assertEquals("Unexpected default reason", "Ringing", a.getLastReceivedResponse()
            .getReasonPhrase());
      }

      a.waitOutgoingCallResponse(5000);
      assertLastOperationSuccess("a wait 2nd response - " + a.format(), a);
      a.waitOutgoingCallResponse(2000);

      assertEquals("Unexpected final response received", Response.OK, a.getLastReceivedResponse()
          .getStatusCode());

      a.sendInviteOkAck();
      assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

      Thread.sleep(1000);

      a.listenForDisconnect();
      assertLastOperationSuccess("a listen disc - " + a.format(), a);

      b.disconnect();
      assertLastOperationSuccess("b disc - " + b.format(), b);

      a.waitForDisconnect(5000);
      assertLastOperationSuccess("a wait disc - " + a.format(), a);

      a.respondToDisconnect();
      assertLastOperationSuccess("a respond to disc - " + a.format(), a);

      Thread.sleep(1000);
      ub.dispose();
    } catch (Exception e) {
      fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }
  }
}
