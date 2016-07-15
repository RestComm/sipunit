/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.cafesip.sipunit.test.noproxy;

import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.assertNotNull;

import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Properties;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.SipFactory;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

public class TestConferenceNoProxy {
  private SipStack sipStack;

  private SipPhone uc;

  private int myPort;

  private String testProtocol;

  private MessageFactory messageFactory;
  private HeaderFactory headerFactory;
  private AddressFactory addressFactory;

  private static Properties getDefaultProperties() {
    Properties defaultProperties = new Properties();

    defaultProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");
    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testConference_debug.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testConference_log.txt");
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");

    defaultProperties.setProperty("sipunit.test.port", "5061");
    defaultProperties.setProperty("sipunit.test.protocol", "udp");
    defaultProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
    return defaultProperties;
  }

  protected Properties properties;

  static String DEFAULT_USER = "alice@example.com";
  static String DEFAULT_CONFERENCE_USER = "conference@example.com";

  protected String getProtocol() {
    return "sip";
  }

  protected String getSipUser() {
    return getProtocol() + ":" + DEFAULT_USER;
  }

  protected String getSipConferenceUser() {
    return getProtocol() + ":" + DEFAULT_CONFERENCE_USER;
  }

  protected String getSipUserAddress(SipPhone phone) {
    return getProtocol() + ":alice@" + phone.getStackAddress() + ':' + myPort;
  }

  protected String getSipConferenceUserAddress(SipPhone phone) {
    return getProtocol() + ":conference@" + phone.getStackAddress() + ':' + myPort;
  }

  @Before
  public void setUp() throws Exception {
    properties = getDefaultProperties();
    properties.putAll(System.getProperties());

    try {
      myPort = Integer.parseInt(properties.getProperty("sipunit.test.port"));
    } catch (NumberFormatException e) {
      myPort = 5061;
    }

    testProtocol = properties.getProperty("sipunit.test.protocol");

    sipStack = new SipStack(testProtocol, myPort, properties);

    uc = sipStack.createSipPhone(getSipConferenceUser());
    uc.setLoopback(true);

    messageFactory = SipFactory.getInstance().createMessageFactory();
    headerFactory = SipFactory.getInstance().createHeaderFactory();
    addressFactory = SipFactory.getInstance().createAddressFactory();
  }

  @After
  public void tearDown() throws Exception {
    uc.dispose();
    awaitStackDispose(sipStack);
  }

  @Test
  public void testNotifyConferenceEvent() throws Exception {
    // create a user to receive the conference notify request
    SipPhone u = sipStack.createSipPhone(getSipUser());
    u.setLoopback(true);

    // first subscribe
    Request subscribe = messageFactory.createRequest("SUBSCRIBE " + getSipConferenceUser() + " SIP/2.0\r\n\r\n");

    CallIdHeader callIdHeader = u.getParent().getSipProvider().getNewCallId();
    MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(5);

    String uTag = u.generateNewTag();
    subscribe.addHeader(callIdHeader);
    subscribe.addHeader(maxForwardsHeader);
    subscribe.addHeader(headerFactory.createCSeqHeader((long) 1, Request.SUBSCRIBE));
    subscribe.addHeader(headerFactory.createFromHeader(u.getAddress(), uTag));
    subscribe.addHeader(headerFactory.createToHeader(uc.getAddress(), null));
    subscribe.addHeader(headerFactory.createContactHeader(u.getAddress()));
    subscribe.addHeader(headerFactory.createEventHeader("conference"));
    subscribe.addHeader(headerFactory.createAcceptHeader("application", "conference-info+xml"));

    for (Header header : u.getViaHeaders()) {
      subscribe.addHeader(header);
    }

    Address routeAddress = addressFactory.createAddress(getSipConferenceUserAddress(uc) + '/' + testProtocol);
    subscribe.addHeader(headerFactory.createRouteHeader(routeAddress));

    uc.listenRequestMessage();
    SipTransaction trans = u.sendRequestWithTransaction(subscribe, false, null);
    assertNotNull(u.format(), trans);

    RequestEvent incReq = uc.waitRequest(5000);
    assertNotNull(uc.format(), incReq);

    // create SUBSCRIBE response
    Response response = messageFactory.createResponse(Response.OK, incReq.getRequest());

    // set subscription expiration
    response.setExpires(headerFactory.createExpiresHeader(3600));

    // set isfocus parameter on Contact: header
    ContactHeader ucContactHeader = headerFactory.createContactHeader(uc.getAddress());
    ucContactHeader.setParameter("isfocus", null);
    response.setHeader(ucContactHeader);

    // set To: header tag
    String ucTag = uc.generateNewTag();
    response.setHeader(headerFactory.createToHeader(uc.getAddress(), ucTag));

    // send the reply
    trans = uc.sendReply(incReq, response);
    assertNotNull(u.format(), trans);

    // save dialog for sending the notify request later
    Dialog ucDialog = trans.getServerTransaction().getDialog();

    // configure user phone for waitRequest()
    u.listenRequestMessage();

    // create the notify request
    Request notify = messageFactory.createRequest("NOTIFY " + getSipUser() + " SIP/2.0\r\n\r\n");

    notify.addHeader(callIdHeader);
    notify.addHeader(maxForwardsHeader);
    notify.addHeader(headerFactory.createCSeqHeader((long) 1, Request.NOTIFY));
    notify.addHeader(headerFactory.createFromHeader(uc.getAddress(), ucTag));
    notify.addHeader(headerFactory.createToHeader(u.getAddress(), uTag));
    notify.addHeader(headerFactory.createSubscriptionStateHeader("active"));
    notify.addHeader(headerFactory.createEventHeader("conference"));
    notify.addHeader(ucContactHeader);

    // create and add the Route Header
    routeAddress = addressFactory.createAddress(getSipUserAddress(u) + '/' + testProtocol);
    notify.addHeader(headerFactory.createRouteHeader(routeAddress));

    notify.setContent(getResourceAsString("/conference-info.xml"),
        headerFactory.createContentTypeHeader("application", "conference-info+xml"));

    trans = uc.sendRequestWithTransaction(notify, false, ucDialog);
    assertNotNull(uc.format(), trans);
    // call sent

    incReq = u.waitRequest(5000);
    assertNotNull(u.format(), incReq);
    // call received

    // send 200 OK response
    response = u.getParent().getMessageFactory().createResponse(Response.OK, incReq.getRequest());
    trans = u.sendReply(incReq, response);
    assertNotNull(u.format(), trans);
  }

  private String getResourceAsString(String resource)
    throws java.net.URISyntaxException,
           java.io.IOException,
           java.io.UnsupportedEncodingException {
    java.net.URL url = this.getClass().getResource(resource);
    java.nio.file.Path resPath = java.nio.file.Paths.get(url.toURI());
    return new String(java.nio.file.Files.readAllBytes(resPath), "UTF-8"); 
  }
}
