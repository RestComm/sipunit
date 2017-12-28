package org.cafesip.sipunit.test.misc;

import gov.nist.javax.sip.ResponseEventExt;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.cafesip.sipunit.test.noproxy.TestMessageNoProxy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.address.Address;
import javax.sip.address.URI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.EventObject;
import java.util.List;
import java.util.Properties;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TestRegisterWithOptions {

    private static final Logger LOG = LoggerFactory.getLogger(TestMessageNoProxy.class);

    static String host = "127.0.0.1";

    private SipStack sipStack;
    private SipPhone ua;
    private int uaPort = 5081;
    private String uaProtocol = "UDP";
    private String uaContact = "sip:client@127.0.0.1:5081";


    private SipStack sipStackProxy;
    private SipPhone proxy;
    private int proxyPort = 5080;
    private String proxyProtocol = "UDP";
    private String proxyContact = "sip:proxy@127.0.0.1:5080";


    private Properties uaProperties = new Properties();
    private final Properties proxyProperties = new Properties();

    @Before
    public void setup() throws Exception {

        uaProperties.setProperty("javax.sip.IP_ADDRESS", host);
        uaProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
        uaProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        uaProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
        uaProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
        uaProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        uaProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
        uaProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

        sipStack = new SipStack(uaProtocol, uaPort, uaProperties);
        ua = sipStack.createSipPhone(host, proxyProtocol, proxyPort, uaContact);

        proxyProperties.setProperty("javax.sip.STACK_NAME", "testProxy");
        proxyProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
        proxyProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testProxy_debug.txt");
        proxyProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testProxy_log.txt");
        proxyProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
        proxyProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
        proxyProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
        proxyProperties.setProperty("javax.sip.IP_ADDRESS", host);

        sipStackProxy = new SipStack(proxyProtocol, proxyPort, proxyProperties);
        proxy = sipStackProxy.createSipPhone(proxyContact);
    }


    /**
     * Release the sipStack and a user agent for the test.
     */
    @After
    public void tearDown() throws Exception {
        ua.unregister(uaContact, 1000);
        ua.dispose();
        awaitStackDispose(sipStack);

        proxy.dispose();
        awaitStackDispose(sipStackProxy);
    }

    @Test
    public void testOptionsWithRegister() throws Exception {
        proxy.setSupportRegisterRequests(true);
        assertTrue(proxy.listenRequestMessage());

        new Thread() {
            @Override
            public void run () {
                RequestEvent requestEvent = proxy.waitRequest(10000);
                assertNotNull(requestEvent);
                Response response = null;
                try {
                    response = proxy.getParent().getMessageFactory().createResponse(200, requestEvent.getRequest());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                proxy.sendReply(requestEvent, response);
            }
        }.start();

        assertTrue(ua.register("amit", "a1b2c3d4", uaContact, 4890, 1000000));
        assertLastOperationSuccess("user a registration - " + ua.format(), ua);


        CSeqHeader cseq = null;

        URI request_uri = proxy.getParent().getAddressFactory().createURI(uaContact);
        CallIdHeader callId = proxy.getParent().getSipProvider().getNewCallId();
        cseq = proxy.getParent().getHeaderFactory().createCSeqHeader(cseq == null ? 1 : (cseq.getSeqNumber() + 1), Request.OPTIONS);

        Address to_address = proxy.getParent().getAddressFactory().createAddress(request_uri);
        ToHeader to_header = proxy.getParent().getHeaderFactory().createToHeader(to_address, null);

        String myTag = proxy.generateNewTag();
        FromHeader from_header = proxy.getParent().getHeaderFactory().createFromHeader(proxy.getAddress(), myTag);

        MaxForwardsHeader max_forwards =
                proxy.getParent().getHeaderFactory().createMaxForwardsHeader(SipPhone.MAX_FORWARDS_DEFAULT);

        List<ViaHeader> via_headers = proxy.getViaHeaders();

        Request options = proxy.getParent().getMessageFactory().createRequest(request_uri, Request.OPTIONS, callId, cseq,
                from_header, to_header, via_headers, max_forwards);

        options.addHeader((ContactHeader) proxy.getContactInfo().getContactHeader().clone());

        SipTransaction transaction = proxy.sendRequestWithTransaction(options, false, null);

        assertNotNull(transaction);

        EventObject eventObject = proxy.waitResponse(transaction, 5000);

        assertNotNull(eventObject);

        assertEquals(Response.OK,((ResponseEventExt)eventObject).getResponse().getStatusCode());

        assertNotNull(ua.getLastReceivedOptionsRequest());
    }


    @Test
    public void testOptionsWithRegisterDisableOptions() throws Exception {
        proxy.setSupportRegisterRequests(true);
        ua.setAutoResponseOptionsRequests(false);

        assertTrue(proxy.listenRequestMessage());

        new Thread() {
            @Override
            public void run () {
                RequestEvent requestEvent = proxy.waitRequest(10000);
                assertNotNull(requestEvent);
                Response response = null;
                try {
                    response = proxy.getParent().getMessageFactory().createResponse(200, requestEvent.getRequest());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                proxy.sendReply(requestEvent, response);
            }
        }.start();

        assertTrue(ua.register("amit", "a1b2c3d4", uaContact, 4890, 1000000));
        assertLastOperationSuccess("user a registration - " + ua.format(), ua);


        CSeqHeader cseq = null;

        URI request_uri = proxy.getParent().getAddressFactory().createURI(uaContact);
        CallIdHeader callId = proxy.getParent().getSipProvider().getNewCallId();
        cseq = proxy.getParent().getHeaderFactory().createCSeqHeader(cseq == null ? 1 : (cseq.getSeqNumber() + 1), Request.OPTIONS);

        Address to_address = proxy.getParent().getAddressFactory().createAddress(request_uri);
        ToHeader to_header = proxy.getParent().getHeaderFactory().createToHeader(to_address, null);

        String myTag = proxy.generateNewTag();
        FromHeader from_header = proxy.getParent().getHeaderFactory().createFromHeader(proxy.getAddress(), myTag);

        MaxForwardsHeader max_forwards =
                proxy.getParent().getHeaderFactory().createMaxForwardsHeader(SipPhone.MAX_FORWARDS_DEFAULT);

        List<ViaHeader> via_headers = proxy.getViaHeaders();

        Request options = proxy.getParent().getMessageFactory().createRequest(request_uri, Request.OPTIONS, callId, cseq,
                from_header, to_header, via_headers, max_forwards);

        options.addHeader((ContactHeader) proxy.getContactInfo().getContactHeader().clone());

        SipTransaction transaction = proxy.sendRequestWithTransaction(options, false, null);

        assertNotNull(transaction);

        EventObject eventObject = proxy.waitResponse(transaction, 5000);

        assertNull(eventObject);
    }

}
