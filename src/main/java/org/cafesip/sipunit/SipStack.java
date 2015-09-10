/*
 * Created on Feb 19, 2005
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

package org.cafesip.sipunit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.javax.sip.ResponseEventExt;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.ParseException;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Random;

import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.ListeningPoint;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipFactory;
import javax.sip.SipListener;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.AddressFactory;
import javax.sip.header.HeaderFactory;
import javax.sip.header.RecordRouteHeader;
import javax.sip.header.RouteHeader;
import javax.sip.message.MessageFactory;

/**
 * This class is the starting point for a SipUnit test. Before establishing any SIP sessions, the
 * test program must instantiate this class. Each SipStack object establishes the SIP protocol
 * binding on the specified TCP/UDP port. This port is used to communicate with external SIP agents
 * (a SIP proxy server, for example). A SipStack object may contain one or more SipPhone objects and
 * each SipPhone object may contain one (or more, in future) SipCall objects and a buddy list. The
 * getXxxFactory() methods of this class are used in conjunction with JAIN-SIP Request/Response
 * classes for dealing with low level message, address and header content, needed when dealing at
 * the SipSession level.
 * 
 * @author Amit Chatterjee, Becky McElroy
 */
public class SipStack implements SipListener {

  private static final Logger LOG = LoggerFactory.getLogger(SipStack.class);

  private static SipFactory sipFactory = null;

  private javax.sip.SipStack sipStack;

  private AddressFactory addressFactory;

  private MessageFactory messageFactory;

  private HeaderFactory headerFactory;

  private SipProvider sipProvider;

  private LinkedList<SipListener> listeners = new LinkedList<>();

  private Random random = new Random((new Date()).getTime());

  private int retransmissions;

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

    // You need 16 for logging traces. 32 for debug + traces.
    // Your code will limp at 32 but it is best for debugging.
    defaultProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");

    defaultProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");

    defaultProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");

    // Guard against starvation.
    defaultProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");

    // properties.setProperty("gov.nist.javax.sip.MAX_MESSAGE_SIZE",
    // "4096");

    defaultProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
  }

  /**
   * <code>PROTOCOL_TCP</code> Specifies TCP/IP transport.
   */
  public static final String PROTOCOL_TCP = "tcp";

  /**
   * <code>PROTOCOL_UDP</code> Specifies UDP/IP transport.
   */
  public static final String PROTOCOL_UDP = "udp";

  public static final int DEFAULT_PORT = 5060;

  public static final String DEFAULT_PROTOCOL = PROTOCOL_UDP;

  /**
   * A constructor for this class. Before establishing any SIP sessions, instantiate this class. You
   * may provide the parameters for SIP protocol binding on a specific TCP/UDP port, which will be
   * used to communicate with external SIP agents (a SIP proxy server, for example). (TODO - update
   * to take advantage of JAIN-SIP 1.2 architecture, multiple listening points per provider -
   * multiple protocols.)
   * 
   * <p>
   * A test program may contain one or more SipStack objects, each of which may have one or more
   * SipPhones.
   * 
   * @param proto SIP transport protocol, "tcp" or "udp" (default is "udp").
   * @param port port on which this stack listens for messages (default is 5060).
   * @param props properties of the SIP stack. These properties are the same as that defined for
   *        JAIN-SIP SipStack. If this parameter has a null value, we pick default values for you.
   * @throws Exception
   */
  public SipStack(String proto, int port, Properties props) throws Exception {
    if (sipFactory == null) {
      sipFactory = SipFactory.getInstance();
      sipFactory.setPathName("gov.nist");
    }

    if (props == null) {
      props = defaultProperties;
    }

    if (props.getProperty("javax.sip.STACK_NAME") == null) {
      props.setProperty("javax.sip.STACK_NAME", "SipUnitTestAgent");
    }

    /*
     * The user can specify an IP address for the stack but we use it as an IP address for the
     * listener and remove it from the property set so you can run multiple stacks.
     */
    String listenAddr = props.getProperty("javax.sip.IP_ADDRESS");

    if (props.getProperty("javax.sip.IP_ADDRESS") != null) {
      props.remove("javax.sip.IP_ADDRESS");
    }

    sipStack = sipFactory.createSipStack(props);

    headerFactory = sipFactory.createHeaderFactory();
    addressFactory = sipFactory.createAddressFactory();
    messageFactory = sipFactory.createMessageFactory();

    if (proto == null) {
      proto = DEFAULT_PROTOCOL;
    }

    if (port < 0) {
      port = DEFAULT_PORT;
    }

    if (listenAddr == null) {
      listenAddr = InetAddress.getLocalHost().getHostAddress();
    }
    /*
     * The above makes use of the fact that with JAIN-SIP 1.2, if you don't provide IP_ADDRESS in
     * the properties when you create a stack, it goes by STACK_NAME only and you can create as many
     * stacks as you want as long as the name is different (and IP_ADDRESS property is null). Thanks
     * to Venkita S. for contributing the changes to SipSession and SipStack needed to make this
     * work.
     */

    ListeningPoint lp = sipStack.createListeningPoint(listenAddr, port, proto);

    sipProvider = sipStack.createSipProvider(lp);
    sipProvider.addSipListener(this);

    sipStack.start();
  }

  /**
   * Equivalent to the other constructor without any properties specified.
   * 
   * @param proto SIP transport protocol (default is UDP).
   * @param port port on which this stack listens for messages (default is 5060).
   * @throws Exception
   */
  public SipStack(String proto, int port) throws Exception {
    this(proto, port, null);
  }

  /**
   * This method is used to create a SipPhone object. The SipPhone class simulates a SIP User Agent.
   * The SipPhone object is used to communicate with other SIP agents. Using a SipPhone object, the
   * test program can make one (or more, in future) outgoing calls or (and, in future) receive one
   * (or more, in future) incoming calls.
   * 
   * @param proxyHost host name or address of the SIP proxy to use. The proxy is used for
   *        registering and outbound calling on a per-call basis. If this parameter is a null value,
   *        any registration requests will be sent to the "host" part of the "me" parameter (see
   *        below) and any attempt to make an outbound call via proxy will fail. If a host name is
   *        given here, it must resolve to a valid, reachable DNS address.
   * 
   * @param proxyProto used to specify the protocol for communicating with the proxy server - "udp"
   *        or "tcp".
   * @param proxyPort port number into with the proxy server listens to for SIP messages and
   *        connections.
   * @param me "Address of Record" URI of the phone user. Each SipPhone is associated with one user.
   *        This parameter is used in the "from" header field.
   * 
   * @return A new SipPhone object.
   * @throws InvalidArgumentException
   * @throws ParseException
   */
  public SipPhone createSipPhone(String proxyHost, String proxyProto, int proxyPort, String me)
      throws InvalidArgumentException, ParseException {
    return new SipPhone(this, proxyHost, proxyProto, proxyPort, me);
  }

  /**
   * This method is the equivalent to the other createSipPhone() methods but without a proxy server.
   * 
   * @param me "Address of Record" URI of the phone user. Each SipPhone is associated with one user.
   *        This parameter is used in the "from" header field.
   * 
   * @return A new SipPhone object.
   * @throws InvalidArgumentException
   * @throws ParseException
   */
  public SipPhone createSipPhone(String me) throws InvalidArgumentException, ParseException {
    return createSipPhone(null, null, -1, me);
  }

  /**
   * This method is the equivalent to the other createSipPhone() method, but using the default
   * transport (UDP/IP) and the default SIP port number (5060).
   * 
   * @param host host name or address of the SIP proxy to use. The proxy is used for registering and
   *        outbound calling on a per-call basis. If this parameter is a null value, any
   *        registration requests will be sent to the "host" part of the "me" parameter (see below)
   *        and any attempt to make an outbound call via proxy will fail. If a host name is given
   *        here, it must resolve to a valid, reachable DNS address.
   * @param me "Address of Record" URI of the phone user. Each SipPhone is associated with one user.
   *        This parameter is used in the "from" header field.
   * 
   * @return A new SipPhone object.
   * @throws InvalidArgumentException
   * @throws ParseException
   */
  public SipPhone createSipPhone(String host, String me) throws InvalidArgumentException,
      ParseException {
    return createSipPhone(host, PROTOCOL_UDP, DEFAULT_PORT, me);
  }

  /**
   * This method is used to tear down the SipStack object. All resources are freed up. Before
   * calling this method, you should call the dispose() nethod on any SipPhones you've created using
   * this sip stack.
   */
  public void dispose() {
    try {
      sipStack.deleteListeningPoint(sipProvider.getListeningPoints()[0]);
      sipProvider.removeSipListener(this);
      sipStack.deleteSipProvider(sipProvider);
      sipFactory.resetFactory();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processRequest(RequestEvent arg0) {
    LOG.trace("request received !");
    synchronized (listeners) {
      Iterator<SipListener> iter = listeners.iterator();
      while (iter.hasNext() == true) {
        SipListener listener = iter.next();
        LOG.trace("calling listener");
        listener.processRequest(arg0);
      }
    }
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processResponse(ResponseEvent arg0) {
    synchronized (listeners) {
      if (((ResponseEventExt) arg0).isRetransmission())
        retransmissions++;
      Iterator iter = listeners.iterator();
      while (iter.hasNext() == true) {
        SipListener listener = (SipListener) iter.next();
        listener.processResponse(arg0);
      }
    }
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processTimeout(TimeoutEvent arg0) {
    synchronized (listeners) {
      Iterator<SipListener> iter = listeners.iterator();
      while (iter.hasNext() == true) {
        SipListener listener = (SipListener) iter.next();
        listener.processTimeout(arg0);
      }
    }
  }

  protected void registerListener(SipListener listener) {
    synchronized (listeners) {
      listeners.addLast(listener);
    }
  }

  protected void unregisterListener(SipListener listener) {
    synchronized (listeners) {
      listeners.remove(listener);
    }
  }

  /**
   * @return Returns the sipFactory.
   */
  protected static SipFactory getSipFactory() {
    return sipFactory;
  }

  /**
   * Gets the JAIN-SIP AddressFactory associated with the SipStack.
   * 
   * @return the address factory.
   */
  public AddressFactory getAddressFactory() {
    return addressFactory;
  }

  /**
   * Gets the JAIN-SIP HeaderFactory associated with the SipStack.
   * 
   * @return the header factory.
   */
  public HeaderFactory getHeaderFactory() {
    return headerFactory;
  }

  /**
   * Gets the JAIN-SIP MessageFactory associated with the SipStack.
   * 
   * @return the message factory.
   */
  public MessageFactory getMessageFactory() {
    return messageFactory;
  }

  /**
   * Gets the JAIN-SIP SipProvider associated with the SipStack.
   * 
   * @return the sip provider.
   */
  public SipProvider getSipProvider() {
    return sipProvider;
  }

  /**
   * Gets the JAIN-SIP SipStack associated with this JUnit SipStack.
   * 
   * @return the JAIN-SIP SipStack.
   */
  public javax.sip.SipStack getSipStack() {
    return sipStack;
  }

  /**
   * @return Returns the random.
   */
  protected Random getRandom() {
    return random;
  }

  /**
   * @param random The random to set.
   */
  protected void setRandom(Random random) {
    this.random = random;
  }

  /**
   * Outputs to console the provided header string followed by the message.
   * 
   * @param informationalHeader
   * @param msg
   */
  public static void dumpMessage(String informationalHeader, javax.sip.message.Message msg) {
    LOG.trace(informationalHeader + "{}.......... \n {}", informationalHeader, msg);

    ListIterator rhdrs = msg.getHeaders(RouteHeader.NAME);
    while (rhdrs.hasNext()) {
      RouteHeader rhdr = (RouteHeader) rhdrs.next();

      if (rhdr != null) {
        LOG.trace("RouteHeader address: {}", rhdr.getAddress().toString());
        Iterator i = rhdr.getParameterNames();
        while (i.hasNext()) {
          String parm = (String) i.next();
          LOG.trace("RouteHeader parameter {}: {}", parm, rhdr.getParameter(parm));
        }
      }
    }

    ListIterator rrhdrs = msg.getHeaders(RecordRouteHeader.NAME);
    while (rrhdrs.hasNext()) {
      RecordRouteHeader rrhdr = (RecordRouteHeader) rrhdrs.next();

      if (rrhdr != null) {
        LOG.trace("RecordRouteHeader address: {}", rrhdr.getAddress());
        Iterator i = rrhdr.getParameterNames();
        while (i.hasNext()) {
          String parm = (String) i.next();
          LOG.trace("RecordRouteHeader parameter {}: {}", parm, rrhdr.getParameter(parm));
        }
      }
    }
  }

  public void processIOException(IOExceptionEvent arg0) {
    // TODO Auto-generated method stub
  }

  public void processTransactionTerminated(TransactionTerminatedEvent arg0) {
    // TODO Auto-generated method stub
  }

  public void processDialogTerminated(DialogTerminatedEvent arg0) {
    // TODO Auto-generated method stub
  }

  public int getRetransmissions() {
    return retransmissions;
  }
}
