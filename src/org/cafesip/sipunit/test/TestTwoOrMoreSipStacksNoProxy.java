/*
 * Created on April 21, 2005
 * 
 * Copyright 2005 CafeSip.org 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *	http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 */
package org.cafesip.sipunit.test;

import java.util.Properties;

import javax.sip.message.Response;

import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTestCase;

/**
 * This class tests two SipStacks on the same machine. This test makes use of
 * the fact that with JAIN-SIP 1.2, if you don't provide IP_ADDRESS in the
 * properties when you create a stack, it goes by STACK_NAME only and you can
 * create as many stacks as you want as long as the name is different (and
 * IP_ADDRESS property is null). You can use system properties to override
 * settings in this test except for the IP_ADDRESS property used on the
 * SipStack(s) - it will be ignored by this particular test.
 * 
 * Thanks to Venkita S. for contributing the changes to SipSession and SipStack
 * needed to make this work.
 * 
 * Tests in this class do not require a proxy/registrar server. Messaging
 * between UACs is direct.
 * 
 * @author Becky McElroy
 * 
 */
public class TestTwoOrMoreSipStacksNoProxy extends SipTestCase
{
    private SipStack sipStack1;

    private SipStack sipStack2;

    private SipPhone ua;

    private int port1 = 5061;

    private int port2 = 5090;

    private String testProtocol = "udp";

    private boolean sipunitTrace = true;

    private static final Properties defaultProperties1 = new Properties();

    private static final Properties defaultProperties2 = new Properties();

    static
    {
        defaultProperties1.setProperty("javax.sip.STACK_NAME", "testAgent");
        defaultProperties1.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        defaultProperties1.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testAgent_debug.txt");
        defaultProperties1.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testAgent_log.txt");
        defaultProperties1.setProperty("gov.nist.javax.sip.READ_TIMEOUT",
                "1000");
        defaultProperties1.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

        defaultProperties2.setProperty("javax.sip.STACK_NAME", "testAgent2");
        defaultProperties2.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        defaultProperties2.setProperty("gov.nist.javax.sip.DEBUG_LOG",
                "testAgent2_debug.txt");
        defaultProperties2.setProperty("gov.nist.javax.sip.SERVER_LOG",
                "testAgent2_log.txt");
        defaultProperties2.setProperty("gov.nist.javax.sip.READ_TIMEOUT",
                "1000");
        defaultProperties2.setProperty(
                "gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    }

    private Properties properties1 = new Properties(defaultProperties1);

    private Properties properties2 = new Properties(defaultProperties2);

    public TestTwoOrMoreSipStacksNoProxy(String arg0)
    {
        super(arg0);
        Properties input_props = new Properties();
        input_props.putAll(System.getProperties());

        String prop = input_props.getProperty("sipunit.testport.1");
        if (prop != null)
        {
            try
            {
                port1 = Integer.parseInt(prop);
            }
            catch (NumberFormatException e)
            {
                System.err.println("Number format exception for input port: "
                        + prop + " - defaulting port1 to 5061");
                port1 = 5061;
            }
        }

        prop = input_props.getProperty("sipunit.testport.2");
        if (prop != null)
        {
            try
            {
                port2 = Integer.parseInt(prop);
            }
            catch (NumberFormatException e)
            {
                System.err.println("Number format exception for input port: "
                        + prop + " - defaulting port2 to 5091");
                port2 = 5091;
            }
        }

        prop = input_props.getProperty("sipunit.test.protocol");
        if (prop != null)
        {
            testProtocol = prop;
        }

        prop = input_props.getProperty("sipunit.trace");
        if (prop != null)
        {
            sipunitTrace = prop.trim().equalsIgnoreCase("true")
                    || prop.trim().equalsIgnoreCase("on");
        }

    }

    /*
     * @see SipTestCase#setUp()
     */
    public void setUp() throws Exception
    {
        try
        {
            sipStack1 = new SipStack(testProtocol, port1, properties1);
            sipStack2 = new SipStack(testProtocol, port2, properties2);

            SipStack.setTraceEnabled(sipunitTrace);
        }
        catch (Exception ex)
        {
            fail("Exception: " + ex.getClass().getName() + ": "
                    + ex.getMessage());
            throw ex;
        }

        try
        {
            ua = sipStack1.createSipPhone("sip:amit@nist.gov");
            ua.setLoopback(true);
        }
        catch (Exception ex)
        {
            fail("Exception creating SipPhone: " + ex.getClass().getName()
                    + ": " + ex.getMessage());
            throw ex;
        }
    }

    /*
     * @see SipTestCase#tearDown()
     */
    public void tearDown() throws Exception
    {
        ua.dispose();
        sipStack1.dispose();
        sipStack2.dispose();
    }

    public void testBothSides()
    {
        try
        {
            SipPhone ub = sipStack2.createSipPhone("sip:becky@nist.gov");
            ub.setLoopback(true);

            SipCall a = ua.createSipCall();
            SipCall b = ub.createSipCall();

            b.listenForIncomingCall();
            Thread.sleep(10);

            a.initiateOutgoingCall("sip:becky@nist.gov", ub.getStackAddress()
                    + ":" + port2 + ";lr/" + testProtocol);
            assertLastOperationSuccess("a initiate call - " + a.format(), a);

            b.waitForIncomingCall(4000);
            assertLastOperationSuccess("b wait incoming call - " + b.format(),
                    b);

            b.sendIncomingCallResponse(Response.RINGING, null, -1);
            assertLastOperationSuccess("b send RINGING - " + b.format(), b);

            Thread.sleep(200);

            b.sendIncomingCallResponse(Response.OK, "Answer - Hello world", 0);
            assertLastOperationSuccess("b send OK - " + b.format(), b);

            a.waitOutgoingCallResponse(5000);
            assertLastOperationSuccess("a wait 1st response - " + a.format(), a);
            assertEquals("Unexpected 1st response received", Response.RINGING,
                    a.getReturnCode());
            assertNotNull("Default response reason not sent", a
                    .getLastReceivedResponse().getReasonPhrase());
            assertEquals("Unexpected default reason", "Ringing", a
                    .getLastReceivedResponse().getReasonPhrase());

            a.waitOutgoingCallResponse(5000);
            assertLastOperationSuccess("a wait 2nd response - " + a.format(), a);

            assertEquals("Unexpected 2nd response received", Response.OK, a
                    .getReturnCode());

            a.sendInviteOkAck();
            assertLastOperationSuccess("Failure sending ACK - " + a.format(), a);

            Thread.sleep(500);

            a.listenForDisconnect();
            assertLastOperationSuccess("a listen disc - " + a.format(), a);

            b.disconnect();
            assertLastOperationSuccess("b disc - " + b.format(), b);

            a.waitForDisconnect(3000);
            assertLastOperationSuccess("a wait disc - " + a.format(), a);

            a.respondToDisconnect();
            assertLastOperationSuccess("a respond to disc - " + a.format(), a);

            ub.dispose();
        }
        catch (Exception e)
        {
            fail("Exception: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

}