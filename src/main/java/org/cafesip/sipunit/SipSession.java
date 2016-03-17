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
 *
 */

package org.cafesip.sipunit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gov.nist.javax.sip.header.ParameterNames;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.sip.ClientTransaction;
import javax.sip.Dialog;
import javax.sip.DialogTerminatedEvent;
import javax.sip.IOExceptionEvent;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipListener;
import javax.sip.TimeoutEvent;
import javax.sip.TransactionAlreadyExistsException;
import javax.sip.TransactionState;
import javax.sip.TransactionTerminatedEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.Header;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Message;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * Methods of this class provide the test program with low-level access to a SIP session. Instead of
 * using the SipPhone and SipCall methods to communicate with other SIP agents, the test program can
 * use methods of this class to send and receive SIP messages. Methods of this class can be accessed
 * via the SipPhone object returned by SipStack.createSipPhone().
 * 
 * <p>
 * Many of the methods in this class return an object or true return value if successful. In case of
 * an error or caller-specified timeout, a null object or a false is returned. The
 * getErrorMessage(), getReturnCode() and getException() methods may be used for further
 * diagnostics. The getReturnCode() method returns either the SIP response code received from the
 * network (defined in SipResponse) or a SipUnit internal status/return code (defined in
 * SipSession). SipUnit internal codes are in a specially designated range
 * (SipSession.SIPUNIT_INTERNAL_RETURNCODE_MIN and upward). The information provided by the
 * getException() method is only meaningful when the getReturnCode() method returns internal SipUnit
 * return code EXCEPTION_ENCOUNTERED. The getErrorMessage() method returns a descriptive string
 * indicating the cause of the problem. If an exception was involved, this string will contain the
 * name of the Exception class and the exception message. This class has a method, format(), which
 * can be called to obtain a human-readable string containing all of this error information.
 * 
 * @author Amit Chatterjee, Becky McElroy
 * 
 */
public class SipSession implements SipListener, SipActionObject {

  private static final Logger LOG = LoggerFactory.getLogger(SipSession.class);

  /**
   * Comment for <code>SIPUNIT_INTERNAL_RETURNCODE_MIN</code> A constant marking the lowest possible
   * SipUnit internal return code value. Anything below this is not internal, but a standard RFC3261
   * SIP status code. A test program can refer to this constant or call isInternal() to determine if
   * the value returned by SipSession.getReturnCode() is an internal SipUnit return code or a SIP
   * response code received from the network.
   */
  public static final int SIPUNIT_INTERNAL_RETURNCODE_MIN = 9000;

  // Internal return/status codes

  public static final int NONE_YET = 9000; // initial value

  public static final int EXCEPTION_ENCOUNTERED = 9001;

  public static final int UNSUPPORTED_URI_SCHEME = 9002;

  public static final int INVALID_UNREGISTERED_OPERATION = 9003;

  public static final int TIMEOUT_OCCURRED = 9004;

  public static final int FAR_END_ERROR = 9005;

  public static final int INTERNAL_ERROR = 9006;

  public static final int ERROR_OF_UNKNOWN_ORIGIN = 9007;

  public static final int INVALID_OPERATION = 9008;

  public static final int INVALID_ARGUMENT = 9009;

  public static final int MISSING_CREDENTIAL = 9010;

  /**
   * Comment for <code>statusCodeDescription</code> This map yields a descriptive string, given an
   * internal sipunit return code.
   */
  public static HashMap<Integer, String> statusCodeDescription = new HashMap<>();

  static {
    statusCodeDescription.put(new Integer(NONE_YET), "Not yet determined");
    statusCodeDescription.put(new Integer(EXCEPTION_ENCOUNTERED), "Exception Encountered");
    statusCodeDescription.put(new Integer(UNSUPPORTED_URI_SCHEME), "Unsupported URI Scheme");
    statusCodeDescription.put(new Integer(INVALID_UNREGISTERED_OPERATION),
        "Invalid Unregistered Operation");
    statusCodeDescription.put(new Integer(TIMEOUT_OCCURRED), "Timeout Occurred");
    statusCodeDescription.put(new Integer(FAR_END_ERROR), "Far End Error");
    statusCodeDescription.put(new Integer(INTERNAL_ERROR), "Internal Error");
    statusCodeDescription.put(new Integer(ERROR_OF_UNKNOWN_ORIGIN), "Error of Unknown Origin");
    statusCodeDescription.put(new Integer(INVALID_OPERATION), "Invalid Operation");
    statusCodeDescription.put(new Integer(INVALID_ARGUMENT), "Invalid Argument");
  }

  // Other constants

  public static final int MAX_FORWARDS_DEFAULT = 70;

  // Class attributes

  private int returnCode = -1;

  private String errorMessage = "";

  protected Throwable exception;

  protected SipStack parent;

  protected String myRegistrationId; // registration call ID

  protected String me;

  protected Address myAddress;

  protected String myhost;

  protected SipContact contactInfo;

  protected Object contactLock = new Object();

  protected String myDisplayName;

  protected String proxyHost;

  protected String proxyProto;

  protected int proxyPort;

  private ArrayList<ViaHeader> viaHeaders;

  private HashMap<ClientTransaction, SipTransaction> respTransactions = new HashMap<>();

  private LinkedList<RequestEvent> reqEvents = new LinkedList<>();

  private boolean rcvRequests = false;

  private BlockObject reqBlock = new BlockObject();

  private BlockObject respBlock = new BlockObject();

  private Map<String, ArrayList<RequestListener>> requestListeners = new HashMap<>();

  // key = String request method, value = ArrayList of RequestListener

  private boolean loopback;

  protected SipSession(SipStack stack, String proxyHost, String proxyProto, int proxyPort,
      String me) throws InvalidArgumentException, ParseException {
    this.parent = stack;
    this.proxyHost = proxyHost;
    this.proxyProto = proxyProto;
    this.proxyPort = proxyPort;
    this.me = me;

    this.myhost = parent.getSipProvider().getListeningPoints()[0].getIPAddress();

    // validate given URI and generate unique ID
    StringTokenizer tokens = new StringTokenizer(me, "@");
    if (tokens.countTokens() != 2) {
      throw new InvalidArgumentException("The \'me\' parameter must be in name@host format");
    }

    tokens.nextToken();
    generateMyId(tokens.nextToken());

    // create and store our address
    AddressFactory addr_factory = parent.getAddressFactory();
    URI my_uri = addr_factory.createURI(me);
    if (my_uri.isSipURI() == false) {
      throw new InvalidArgumentException("URI " + me + " is not a Sip URI");
    }

    myAddress = addr_factory.createAddress(my_uri);

    // default our local contact info, in case no Proxy/registration applies
    // (use user@hostname)
    SipURI contact_uri = addr_factory.createSipURI(((SipURI) my_uri).getUser(), this.myhost);

    contact_uri.setPort(parent.getSipProvider().getListeningPoints()[0].getPort());
    contact_uri.setTransportParam(parent.getSipProvider().getListeningPoints()[0].getTransport());
    contact_uri.setSecure(((SipURI) my_uri).isSecure());
    contact_uri.setLrParam();

    Address contact_address = addr_factory.createAddress(contact_uri);
    contact_address.setDisplayName(myAddress.getDisplayName());
    ContactHeader hdr = parent.getHeaderFactory().createContactHeader(contact_address);

    contactInfo = new SipContact();
    contactInfo.setContactHeader(hdr);

    // determine and store my via header(s)
    ViaHeader via_header = parent.getHeaderFactory().createViaHeader(this.myhost,
        parent.getSipProvider().getListeningPoints()[0].getPort(),
        parent.getSipProvider().getListeningPoints()[0].getTransport(), "somebranchvalue");

    viaHeaders = new ArrayList<>(1);
    viaHeaders.add(via_header);

    // finally, register with the sip stack
    parent.registerListener(this);
  }

  private void generateMyId(String host) {
    // This scheme of using random numbers to generate a
    // unique identifier has a small probability of
    // causing duplicate call ids.
    // TODO at some point, we may want to change it.

    long r = parent.getRandom().nextLong();
    r = (r < 0) ? 0 - r : r; // generate a positive number

    myRegistrationId = r + "@" + host;
  }

  /**
   * Generates a newly generated unique tag ID.
   * 
   * @return A String tag ID
   */
  public String generateNewTag() {
    // This scheme of using random numbers to generate a
    // unique tag has a small probability of
    // causing duplicate tags.
    // TODO at some point, we may want to change it.

    int r = parent.getRandom().nextInt();
    r = (r < 0) ? 0 - r : r; // generate a positive number

    // incorporate registration/session ID (myRegistrationId) into all tags
    // from this
    // client? (for jiplet call identification purposes)

    return Integer.toString(r);
  }

  protected SipSession(SipStack stack, String proxyHost, String me)
      throws InvalidArgumentException, ParseException {
    this(stack, proxyHost, SipStack.PROTOCOL_UDP, SipStack.DEFAULT_PORT, me);
  }

  /**
   * This method idles this SipSession. This SipSession object must not be used again after calling
   * the dispose() method.
   * 
   */
  public void dispose() {
    parent.unregisterListener(this);
  }

  /**
   * The getParent() method returns the org.cafesip.sipunit.SipStack object that is associated with
   * this SipSession object. A test program can use the SipStack object to get the header, address,
   * and message factories for building requests and responses, before passing those to methods of
   * this class.
   * 
   * @return Returns the parent.
   */
  public SipStack getParent() {
    return parent;
  }

  /**
   * @param parent The parent to set.
   */
  protected void setParent(SipStack parent) {
    this.parent = parent;
  }

  /**
   * Gets the IP address and port currently being used in this Sip agent's contact
   * address, via, and listening point 'sentby' components. Example: 66.32.44.114:5066
   * 
   * @return A String containing address + ':' + port.
   */
  public String getPublicAddress() {
    return parent.getSipProvider().getListeningPoints()[0].getSentBy();
  }

  /**
   * This method replaces the host/port values currently being used in this Sip agent's contact
   * address, via, and listening point 'sentby' components with the given host and port parameters.
   * 
   * <p>
   * Call this method when you are running a SipUnit testcase behind a NAT and need to register with
   * a proxy on the public internet. Before creating the SipStack and SipPhone, you'll need to first
   * obtain the public IP address/port using a mechanism such as the Stun4j API. See the
   * TestWithStun.java file in the sipunit test/examples directory for an example of how to do it.
   * 
   * @param host The publicly accessible IP address for this Sip client (ex: 66.32.44.112).
   * @param port The port to be used with the publicly accessible IP address.
   * @return true if the parameters are successfully parsed and this client's information is
   *         updated, false otherwise.
   */
  public boolean setPublicAddress(String host, int port) {
    try {
      // set 'sentBy' in the listening point for outbound messages
      parent.getSipProvider().getListeningPoints()[0].setSentBy(host + ":" + port);

      // update my contact info
      SipURI my_uri = (SipURI) contactInfo.getContactHeader().getAddress().getURI();
      my_uri.setHost(host);
      my_uri.setPort(port);

      // update my via header
      ViaHeader my_via = (ViaHeader) viaHeaders.get(0);
      my_via.setHost(host);
      my_via.setPort(port);

      // update my host
      myhost = host;
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return false;
    }

    // LOG.info("my public IP {}", host);
    // LOG.info("my public port = {}", port);
    // LOG.info("my sentby = {}",
    //  parent.getSipProvider().getListeningPoints()[0].getSentBy());

    return true;
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processRequest(RequestEvent request) {
    Request req_msg = request.getRequest();
    ToHeader to = (ToHeader) req_msg.getHeader(ToHeader.NAME);
    SipContact my_contact_info = new SipContact();

    synchronized (contactLock) {
      my_contact_info.setContactHeader((ContactHeader) (contactInfo.getContactHeader().clone()));
    }

    // Is it for me? Check: Request-URI = my contact address (I may not be
    // the original 'To' party, also there may be multiple devices for one
    // "me" address of record)
    // If no match, check 'To' = me
    // (so that local messaging without proxy still works) - but ONLY IF
    // setLoopback() has been called

    LOG.trace("request received !");
    LOG.trace("     me ('To' check) = {}", me);
    LOG.trace("     my local contact info ('Request URI' check) = {}", my_contact_info.getURI());
    LOG.trace("     {}" , req_msg.toString());

    if (destMatch((SipURI) my_contact_info.getContactHeader().getAddress().getURI(),
        (SipURI) req_msg.getRequestURI()) == false) {
      if (!loopback) {
        LOG.trace("     skipping 'To' check, we're not loopback (see setLoopback())");
        return;
      }

      // check 'To' for a match
      if (to.getAddress().getURI().toString().equals(me) == false) {
        return;
      }
    }

    // check for listener handling
    synchronized (requestListeners) {
      List<RequestListener> listeners = requestListeners.get(req_msg.getMethod());
      if (listeners != null) {
        Iterator<RequestListener> i = listeners.iterator();
        while (i.hasNext()) {
          RequestListener listener = (RequestListener) i.next();
          listener.processEvent(request);
        }
      }
    }

    synchronized (reqBlock) {
      if (rcvRequests == false) {
        LOG.trace("not interested in blocking requests");
        return;
      }

      reqEvents.addLast(request);

      LOG.trace("notifying block object");
      reqBlock.notifyEvent();
    }
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processResponse(ResponseEvent response) {
    ClientTransaction trans = response.getClientTransaction();
    if (trans == null) {
      return;
    }

    SipTransaction sip_trans = null;

    synchronized (respTransactions) {
      sip_trans = (SipTransaction) respTransactions.get(trans);
    }

    if (sip_trans == null) {
      return;
    }

    if (response.getResponse().getStatusCode() > 199) {
      synchronized (respTransactions) {
        respTransactions.remove(trans);
      }
    }

    // check for listener handling
    MessageListener listener = sip_trans.getClientListener();
    if (listener != null) {
      listener.processEvent(response);
      return;
    }

    // if no listener, use the default blocking mechanism
    synchronized (sip_trans.getBlock()) {
      sip_trans.getEvents().addLast(response);
      sip_trans.getBlock().notifyEvent();
    }
  }

  /**
   * FOR INTERNAL USE ONLY. Not to be used by a test program.
   */
  public void processTimeout(TimeoutEvent timeout) {
    ClientTransaction trans = timeout.getClientTransaction();
    if (trans == null) {
      return;
    }

    SipTransaction sip_trans = null;
    synchronized (respTransactions) {
      sip_trans = (SipTransaction) respTransactions.get(trans);
    }

    if (sip_trans == null) {
      return;
    }

    if (trans.getState().getValue() == TransactionState._TERMINATED) {
      synchronized (respTransactions) {
        respTransactions.remove(trans);
      }
    }

    // check for listener handling
    MessageListener listener = sip_trans.getClientListener();
    if (listener != null) {
      listener.processEvent(timeout);
      return;
    }

    // if no listener, use the default blocking mechanism
    synchronized (sip_trans.getBlock()) {
      sip_trans.getEvents().addLast(timeout);
      sip_trans.getBlock().notifyEvent();
    }
  }

  protected static boolean destMatch(SipURI uri1, SipURI uri2) {
    if (uri1.getScheme().equalsIgnoreCase(uri2.getScheme())) {
      if (uri1.getUser() != null) {
        if (uri2.getUser() == null) {
          return false;
        }

        if (uri1.getUser().equals(uri2.getUser()) == false) {
          return false;
        }

        if (uri1.getUserPassword() != null) {
          if (uri2.getUserPassword() == null) {
            return false;
          }

          if (uri1.getUserPassword().equals(uri2.getUserPassword()) == false) {
            return false;
          }
        } else if (uri2.getUserPassword() != null) {
          return false;
        }
      } else if (uri2.getUser() != null) {
        return false;
      }

      if (uri1.getHost().equalsIgnoreCase(uri2.getHost()) == false) {
        return false;
      }

      if (uri1.toString().indexOf(uri1.getHost() + ':') != -1) {
        if (uri2.toString().indexOf(uri2.getHost() + ':') == -1) {
          return false;
        }

        if (uri1.getPort() != uri2.getPort()) {
          return false;
        }
      } else if (uri2.toString().indexOf(uri2.getHost() + ':') != -1) {
        return false;
      }

      // FOR A FULL URI-EQUAL CHECK, add the following:
      /*
       * if (uri1.getTransportParam() != null) { if (uri2.getTransportParam() == null) { return
       * false; }
       * 
       * if (uri1.getTransportParam().equals(uri2.getTransportParam()) == false) { return false; } }
       * else if (uri2.getTransportParam() != null) { return false; }
       * 
       * if (uri1.getTTLParam() != -1) { if (uri2.getTTLParam() == -1) { return false; }
       * 
       * if (uri1.getTTLParam() != uri2.getTTLParam()) { return false; } } else if
       * (uri2.getTTLParam() != -1) { return false; }
       * 
       * if (uri1.getMethodParam() != null) { if (uri2.getMethodParam() == null) { return false; }
       * 
       * if (uri1.getMethodParam().equals(uri2.getMethodParam()) == false) { return false; } } else
       * if (uri2.getMethodParam() != null) { return false; } / next - incorporate the following
       * remaining checks:
       * 
       * URI uri-parameter components are compared as follows: - Any uri-parameter appearing in both
       * URIs must match. - A user, ttl, or method uri-parameter appearing in only one URI never
       * matches, even if it contains the default value. - A URI that includes an maddr parameter
       * will not match a URI that contains no maddr parameter. - All other uri-parameters appearing
       * in only one URI are ignored when comparing the URIs.
       * 
       * o URI header components are never ignored. Any present header component MUST be present in
       * both URIs and match for the URIs to match. The matching rules are defined for each header
       * field in Section 20.
       */

      return true;
    }

    return false;
  }

  /**
   * This sendUnidirectionalRequest() method sends out a request message with no response expected.
   * A Request object is constructed from the string parameter passed in.
   * 
   * Example: <code>
   * StringBuffer invite = new StringBuffer("INVITE sip:becky@"
   + PROXY_HOST + ':' + PROXY_PORT + ";transport="
   + PROXY_PROTO + " SIP/2.0\n");
   invite.append("Call-ID: 5ff235b07a7e4c19784d138fb26c1ec8@"
   + thisHostAddr + "\n");
   invite.append("CSeq: 1 INVITE\n");
   invite.append("From: &lt;sip:amit@nist.gov&gt;;tag=1181356482\n");
   invite.append("To: &lt;sip:becky@nist.gov&gt;\n");
   invite.append("Contact: &lt;sip:amit@" + thisHostAddr + ":5060&gt;\n");
   invite.append("Max-Forwards: 5\n");
   invite.append("Via: SIP/2.0/" + PROXY_PROTO + " " + thisHostAddr
   + ":5060;branch=322e3136382e312e3130303a3530363\n");
   invite.append("Event: presence\n");
   invite.append("Content-Length: 5\n");
   invite.append("\n");
   invite.append("12345");
  
   SipTransaction trans = ua.sendRequestWithTransaction(invite
   .toString(), true, null);
   assertNotNull(ua.format(), trans);
   *            </code>
   * 
   * @param reqMessage A request message in the form of a String with everything from the request
   *        line and headers to the body. It must be in the proper format per RFC-3261.
   * 
   * @param viaProxy If true, send the message to the proxy. In this case the request URI is
   *        modified by this method. Else send it to the user specified in the given request URI. In
   *        this case, for an INVITE request, a route header must be present for the request routing
   *        to complete. This method does NOT add a route header.
   * @return true if the message was successfully built and sent, false otherwise.
   * @throws ParseException if an error was encountered while parsing the string.
   */
  public boolean sendUnidirectionalRequest(String reqMessage, boolean viaProxy)
      throws ParseException {
    Request request = parent.getMessageFactory().createRequest(reqMessage);
    return sendUnidirectionalRequest(request, viaProxy);
  }

  /**
   * This sendUnidirectionalRequest() method sends out a request message with no response expected.
   * The Request object passed in must be a fully formed Request with all required content, ready to
   * be sent.
   * 
   * @param request The request to be sent out.
   * @param viaProxy If true, send the message to the proxy. In this case a Route header is added by
   *        this method. Else send the message as is. In this case, for an INVITE request, a route
   *        header must have been added by the caller for the request routing to complete.
   * @return true if the message was successfully sent, false otherwise.
   */
  public boolean sendUnidirectionalRequest(Request request, boolean viaProxy) {
    initErrorInfo();

    if (viaProxy == true) {
      if (addProxy(request) == false) {
        return false;
      }
    }

    try {
      parent.getSipProvider().sendRequest(request);
      return true;
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return false;
    }
  }

  /**
   * @param request
   * @return
   */
  private boolean addProxy(Request request) {
    if (proxyHost == null) {
      errorMessage =
          "Attempt to add Route header for proxy, but the proxy server was not specified for this SipPhone";
      returnCode = INVALID_ARGUMENT;
      return false;
    }

    URI request_uri = request.getRequestURI();
    if (!request_uri.isSipURI()) {
      errorMessage = "Only sip/sips routing URIs supported";
      returnCode = INVALID_ARGUMENT;
      return false;
    }

    try {
      SipURI route_uri = parent.getAddressFactory().createSipURI(null, proxyHost);
      route_uri.setLrParam();
      route_uri.setPort(proxyPort);
      route_uri.setTransportParam(proxyProto);
      route_uri.setSecure(((SipURI) request_uri).isSecure());

      Address route_address = parent.getAddressFactory().createAddress(route_uri);
      request.addHeader(parent.getHeaderFactory().createRouteHeader(route_address));

      return true;
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      ex.printStackTrace();
      return false;
    }
  }

  /**
   * This basic method sends out a request message as part of a transaction. A test program should
   * use this method when a response to a request is expected. A Request object is constructed from
   * the string passed in.
   * 
   * <p>
   * This method returns when the request message has been sent out. The calling program must
   * subsequently call the waitResponse() method to wait for the result (response, timeout, etc.).
   * 
   * @param reqMessage A request message in the form of a String with everything from the request
   *        line and headers to the body. It must be in the proper format per RFC-3261.
   * @param viaProxy If true, send the message to the proxy. In this case the request URI is
   *        modified by this method. Else send it to the user specified in the given request URI. In
   *        this case, for an INVITE request, a route header must be present for the request routing
   *        to complete. This method does NOT add a route header.
   * @param dialog If not null, send the request via the given dialog. Else send it outside of any
   *        dialog.
   * @return A SipTransaction object if the message was built and sent successfully, null otherwise.
   *         The calling program doesn't need to do anything with the returned SipTransaction other
   *         than pass it in to a subsequent call to waitResponse().
   * @throws ParseException if an error is encountered while parsing the string.
   */
  public SipTransaction sendRequestWithTransaction(String reqMessage, boolean viaProxy,
      Dialog dialog) throws ParseException {
    Request request = parent.getMessageFactory().createRequest(reqMessage);
    return sendRequestWithTransaction(request, viaProxy, dialog);
  }

  /**
   * This method is the same as the basic sendRequestWithTransaction(String,...) method except that
   * it allows the caller to specify a message body and/or additional JAIN-SIP API message headers
   * to add to or replace in the outbound message. Use of this method requires knowledge of the
   * JAIN-SIP API.
   * 
   * <p>
   * The extra parameters supported by this method are:
   * 
   * @param additionalHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add
   *        to the outbound message. These headers are added to the message after a correct message
   *        has been constructed. Note that if you try to add a header that there is only supposed
   *        to be one of in a message, and it's already there and only one single value is allowed
   *        for that header, then this header addition attempt will be ignored. Use the
   *        'replaceHeaders' parameter instead if you want to replace the existing header with your
   *        own. Use null for no additional message headers.
   * @param replaceHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add to
   *        the outbound message, replacing existing header(s) of that type if present in the
   *        message. These headers are applied to the message after a correct message has been
   *        constructed. Use null for no replacement of message headers.
   * @param body A String to be used as the body of the message. The additionalHeaders parameter
   *        must contain a ContentTypeHeader for this body to be included in the message. Use null
   *        for no body bytes.
   */
  public SipTransaction sendRequestWithTransaction(String reqMessage, boolean viaProxy,
      Dialog dialog, ArrayList<Header> additionalHeaders, ArrayList<Header> replaceHeaders,
      String body) throws ParseException {
    Request request = parent.getMessageFactory().createRequest(reqMessage);
    return sendRequestWithTransaction(request, viaProxy, dialog, additionalHeaders, replaceHeaders,
        body);
  }

  /**
   * This method is the same as the basic sendRequestWithTransaction(String,...) method except that
   * it allows the caller to specify a message body and/or additional message headers to add to or
   * replace in the outbound message without requiring knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'application' part of 'application/sdp'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
   * @param contentSubType The body content sub-type (ie, 'sdp' part of 'application/sdp'), required
   *        if there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param additionalHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message. Examples: "Priority: Urgent", "Max-Forwards: 10". These
   *        headers are added to the message after a correct message has been constructed. Note that
   *        if you try to add a header that there is only supposed to be one of in a message, and
   *        it's already there and only one single value is allowed for that header, then this
   *        header addition attempt will be ignored. Use the 'replaceHeaders' parameter instead if
   *        you want to replace the existing header with your own. Unpredictable results may occur
   *        if your headers are not syntactically correct or contain nonsensical values (the message
   *        may not pass through the local SIP stack). Use null for no additional message headers.
   * @param replaceHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message, replacing existing header(s) of that type if present in the
   *        message. Examples: "Priority: Urgent", "Max-Forwards: 10". These headers are applied to
   *        the message after a correct message has been constructed. Unpredictable results may
   *        occur if your headers are not syntactically correct or contain nonsensical values (the
   *        message may not pass through the local SIP stack). Use null for no replacement of
   *        message headers.
   * 
   */
  public SipTransaction sendRequestWithTransaction(String reqMessage, boolean viaProxy,
      Dialog dialog, String body, String contentType, String contentSubType,
      ArrayList<String> additionalHeaders, ArrayList<String> replaceHeaders) throws ParseException {
    Request request = parent.getMessageFactory().createRequest(reqMessage);

    try {
      return sendRequestWithTransaction(request, viaProxy, dialog,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * This basic method sends out a request message as part of a transaction. A test program should
   * use this method when a response to a request is expected. The Request object passed in must be
   * a fully formed Request with all required content, EXCEPT for the Via header branch parameter,
   * which cannot be filled in until a client transaction is obtained. That happens in this method.
   * If the Via branch value is set in the request parameter passed to this method, it is nulled out
   * by this method so that a new client transaction can be created by the stack.
   * 
   * This method returns when the request message has been sent out. The calling program must
   * subsequently call the waitResponse() method to wait for the result (response, timeout, etc.).
   * 
   * @param request The request to be sent out.
   * @param viaProxy If true, send the message to the proxy. In this case a Route header is added by
   *        this method. Else send the message as is. In this case, for an INVITE request, a route
   *        header must have been added by the caller for the request routing to complete.
   * @param dialog If not null, send the request via the given dialog. Else send it outside of any
   *        dialog.
   * @return A SipTransaction object if the message was sent successfully, null otherwise. The
   *         calling program doesn't need to do anything with the returned SipTransaction other than
   *         pass it in to a subsequent call to waitResponse().
   */
  public SipTransaction sendRequestWithTransaction(Request request, boolean viaProxy,
      Dialog dialog) {
    return sendRequestWithTransaction(request, viaProxy, dialog, null);
  }

  /**
   * This method is the same as the basic sendRequestWithTransaction(Request,...) method except that
   * it allows the caller to specify a message body and/or additional JAIN-SIP API message headers
   * to add to or replace in the outbound message. Use of this method requires knowledge of the
   * JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param additionalHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add
   *        to the outbound message. These headers are added to the message after a correct message
   *        has been constructed. Note that if you try to add a header that there is only supposed
   *        to be one of in a message, and it's already there and only one single value is allowed
   *        for that header, then this header addition attempt will be ignored. Use the
   *        'replaceHeaders' parameter instead if you want to replace the existing header with your
   *        own. Use null for no additional message headers.
   * @param replaceHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add to
   *        the outbound message, replacing existing header(s) of that type if present in the
   *        message. These headers are applied to the message after a correct message has been
   *        constructed. Use null for no replacement of message headers.
   * @param body A String to be used as the body of the message. The additionalHeaders parameter
   *        must contain a ContentTypeHeader for this body to be included in the message. Use null
   *        for no body bytes.
   */
  public SipTransaction sendRequestWithTransaction(Request request, boolean viaProxy, Dialog dialog,
      ArrayList<Header> additionalHeaders, ArrayList<Header> replaceHeaders, String body) {
    return sendRequestWithTransaction(request, viaProxy, dialog, null, additionalHeaders,
        replaceHeaders, body);
  }

  /**
   * This method is the same as the basic sendRequestWithTransaction(Request,...) method except that
   * it allows the caller to specify a message body and/or additional message headers to add to or
   * replace in the outbound message without requiring knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'application' part of 'application/sdp'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
   * @param contentSubType The body content sub-type (ie, 'sdp' part of 'application/sdp'), required
   *        if there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param additionalHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message. Examples: "Priority: Urgent", "Max-Forwards: 10". These
   *        headers are added to the message after a correct message has been constructed. Note that
   *        if you try to add a header that there is only supposed to be one of in a message, and
   *        it's already there and only one single value is allowed for that header, then this
   *        header addition attempt will be ignored. Use the 'replaceHeaders' parameter instead if
   *        you want to replace the existing header with your own. Unpredictable results may occur
   *        if your headers are not syntactically correct or contain nonsensical values (the message
   *        may not pass through the local SIP stack). Use null for no additional message headers.
   * @param replaceHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message, replacing existing header(s) of that type if present in the
   *        message. Examples: "Priority: Urgent", "Max-Forwards: 10". These headers are applied to
   *        the message after a correct message has been constructed. Unpredictable results may
   *        occur if your headers are not syntactically correct or contain nonsensical values (the
   *        message may not pass through the local SIP stack). Use null for no replacement of
   *        message headers.
   * 
   */
  public SipTransaction sendRequestWithTransaction(Request request, boolean viaProxy, Dialog dialog,
      String body, String contentType, String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return sendRequestWithTransaction(request, viaProxy, dialog, null,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  protected SipTransaction sendRequestWithTransaction(Request request, boolean viaProxy,
      Dialog dialog, MessageListener respListener) {
    return sendRequestWithTransaction(request, viaProxy, dialog, respListener, null, null, null);
  }

  /**
   * This method is like other sendRequestWithTransaction() except that it allows the caller to
   * specify a message body and/or additional JAIN-SIP API message headers to add to or replace in
   * the outbound message. Use of this method requires knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param additionalHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add
   *        to the outbound message. These headers are added to the message after a correct message
   *        has been constructed. Note that if you try to add a header that there is only supposed
   *        to be one of in a message, and it's already there and only one single value is allowed
   *        for that header, then this header addition attempt will be ignored. Use the
   *        'replaceHeaders' parameter instead if you want to replace the existing header with your
   *        own. Use null for no additional message headers.
   * @param replaceHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add to
   *        the outbound message, replacing existing header(s) of that type if present in the
   *        message. These headers are applied to the message after a correct message has been
   *        constructed. Use null for no replacement of message headers.
   * @param body A String to be used as the body of the message. The additionalHeaders parameter
   *        must contain a ContentTypeHeader for this body to be included in the message. Use null
   *        for no body bytes.
   */
  protected SipTransaction sendRequestWithTransaction(Request request, boolean viaProxy,
      Dialog dialog, MessageListener respListener, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    if (viaProxy == true) {
      if (addProxy(request) == false) {
        return null;
      }
    }

    try {
      // clear out branch (client transaction) value, if by some chance we
      // repeat it, the stack will fail the 'getNewClientTransaction()'
      // call below
      // Addition of a "if" condition) to check if the new Request
      // is a CANCEL. In this case, the branch-ID is the same.
      if (request.getMethod() != Request.CANCEL) {
        ViaHeader via = (ViaHeader) request.getHeader(ViaHeader.NAME);
        if (via != null) {
          via.removeParameter(ParameterNames.BRANCH);
        }
      }

      putElements(request, additionalHeaders, replaceHeaders, body);

      ClientTransaction trans = parent.getSipProvider().getNewClientTransaction(request);
      SipTransaction sip_trans = new SipTransaction();
      sip_trans.setClientTransaction(trans);
      sip_trans.setBlock(respBlock);
      sip_trans.setClientListener(respListener);

      synchronized (respTransactions) {
        respTransactions.put(trans, sip_trans);
      }

      try {
        if (dialog == null) {
          trans.sendRequest();
        } else {
          if (request.getMethod().equals(Request.ACK)) {
            dialog.sendAck(request);
          } else {
            dialog.sendRequest(trans);
          }
        }
      } catch (Exception e) {
        synchronized (respTransactions) {
          respTransactions.remove(trans);
        }
        throw e;
      }

      return sip_trans;
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * The waitResponse() method waits for a response to a previously sent transactional request
   * message. Call this method after using one of the sendRequestWithTransaction() methods.
   * 
   * This method blocks until one of the following occurs: 1) A javax.sip.ResponseEvent is received.
   * This is the object returned by this method. 2) A javax.sip.TimeoutEvent is received. This is
   * the object returned by this method. 3) The wait timeout period specified by the parameter to
   * this method expires. Null is returned in this case. 4) An error occurs. Null is returned in
   * this case.
   * 
   * Note that this method can be called repeatedly upon receipt of provisional response message(s).
   * 
   * @param trans The SipTransaction object associated with the sent request.
   * @param timeout The maximum amount of time to wait, in milliseconds. Use a value of 0 to wait
   *        indefinitely.
   * @return A javax.sip.ResponseEvent, javax.sip.TimeoutEvent, or null in the case of wait timeout
   *         or error. If null, call getReturnCode() and/or getErrorMessage() and, if applicable,
   *         getException() for further diagnostics.
   */
  public EventObject waitResponse(SipTransaction trans, long timeout) {
    // TODO later: waitResponse() for nontransactional-request case

    initErrorInfo();

    synchronized (trans.getBlock()) {
      LinkedList<EventObject> events = trans.getEvents();
      if (events.isEmpty()) {
        try {
          trans.getBlock().waitForEvent(timeout);
        } catch (Exception ex) {
          setException(ex);
          setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
          setReturnCode(EXCEPTION_ENCOUNTERED);
          return null;
        }
      }

      if (events.isEmpty()) {
        setReturnCode(TIMEOUT_OCCURRED);
        setErrorMessage("The maximum amount of time to wait for a response message has elapsed.");
        return null;
      }

      return (EventObject) events.removeFirst();
    }
  }

  /**
   * This method prepares the SipSession for reception of a request message addressed to this
   * SipSession's URI. It is non-blocking and returns immediately. A test program must subsequently
   * call the waitRequest() method which blocks until a request is received for this session's URI.
   * 
   * @return true unless an error is encountered, in which case false is returned.
   */
  public boolean listenRequestMessage() {
    synchronized (reqBlock) {
      rcvRequests = true;
    }

    return true;
  }

  /**
   * The unlistenRequestMessage() method cancels out a previous directive to listen for reception of
   * a request addressed to this SipSession's URI. That is, it undoes a previous call to
   * listenRequestMessage().
   * 
   * If there are any pending requests (received but not processed yet), those are discarded.
   * 
   * @return true unless an error is encountered, in which case false is returned.
   */
  public boolean unlistenRequestMessage() {
    synchronized (reqBlock) {
      rcvRequests = false;
      reqEvents.clear();
    }

    return true;
  }

  /**
   * The waitRequest() method waits for a request addressed to this SipSession's URI to be received
   * from the network. Call this method after calling the listenRequestMessage() method.
   * 
   * This method blocks until one of the following occurs: 1) A javax.sip.RequestEvent is received,
   * addressed to this URI. This is the object returned by this method. 2) The wait timeout period
   * specified by the parameter to this method expires. Null is returned in this case. 3) An error
   * occurs. Null is returned in this case.
   * 
   * @param timeout The maximum amount of time to wait, in milliseconds. Use a value of 0 to wait
   *        indefinitely.
   * @return A RequestEvent or null in the case of wait timeout or error. If null, call
   *         getReturnCode() and/or getErrorMessage() and, if applicable, getException() for further
   *         diagnostics.
   */
  public RequestEvent waitRequest(long timeout) {
    initErrorInfo();

    synchronized (reqBlock) {
      if (reqEvents.isEmpty()) {
        try {
          LOG.trace("about to block, waiting");
          reqBlock.waitForEvent(timeout);
          LOG.trace("we've come out of the block");
        } catch (Exception ex) {
          setException(ex);
          setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
          setReturnCode(EXCEPTION_ENCOUNTERED);
          return null;
        }
      }

      LOG.trace("either we got the request, or timed out");
      if (reqEvents.isEmpty()) {
        setReturnCode(TIMEOUT_OCCURRED);
        setErrorMessage("The maximum amount of time to wait for a request message has elapsed.");
        return null;
      }

      return (RequestEvent) reqEvents.removeFirst();
    }
  }

  /**
   * This method sends a basic, stateful response to a previously received request. Call this method
   * after calling waitRequest(). The response is constructed based on the parameters passed in. The
   * returned SipTransaction object must be used in any subsequent calls to sendReply() for the same
   * received request, if there are any.
   * 
   * @param request The RequestEvent object that was returned by a previous call to waitRequest().
   * @param statusCode The status code of the response to send (may use SipResponse constants).
   * @param reasonPhrase If not null, the reason phrase to send.
   * @param toTag If not null, it will be put into the 'To' header of the response. Required by
   *        final responses such as OK.
   * @param contact If not null, it will be used to create a 'Contact' header to be added to the
   *        response.
   * @param expires If not -1, an 'Expires' header is added to the response containing this value,
   *        which is the time the message is valid, in seconds.
   * @return A SipTransaction object that must be passed in to any subsequent call to sendReply()
   *         for the same received request, or null if an error was encountered while sending the
   *         response. The calling program doesn't need to do anything with the returned
   *         SipTransaction other than pass it in to a subsequent call to sendReply() for the same
   *         received request.
   */
  public SipTransaction sendReply(RequestEvent request, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires) {
    return sendReply(request, statusCode, reasonPhrase, toTag, contact, expires, null, null, null);
  }

  /**
   * This method is the same as the basic sendReply(RequestEvent, ...) method except that it allows
   * the caller to specify a message body and/or additional JAIN-SIP API message headers to add to
   * or replace in the outbound message. Use of this method requires knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param additionalHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add
   *        to the outbound message. These headers are added to the message after a correct message
   *        has been constructed. Note that if you try to add a header that there is only supposed
   *        to be one of in a message, and it's already there and only one single value is allowed
   *        for that header, then this header addition attempt will be ignored. Use the
   *        'replaceHeaders' parameter instead if you want to replace the existing header with your
   *        own. Use null for no additional message headers.
   * @param replaceHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add to
   *        the outbound message, replacing existing header(s) of that type if present in the
   *        message. These headers are applied to the message after a correct message has been
   *        constructed. Use null for no replacement of message headers.
   * @param body A String to be used as the body of the message. The additionalHeaders parameter
   *        must contain a ContentTypeHeader for this body to be included in the message. Use null
   *        for no body bytes.
   */
  public SipTransaction sendReply(RequestEvent request, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    if ((request == null) || (request.getRequest() == null)) {
      setErrorMessage("Cannot send reply, request information is null");
      setReturnCode(INVALID_ARGUMENT);
      return null;
    }

    Response response;

    try {
      response = parent.getMessageFactory().createResponse(statusCode, request.getRequest());

      if (reasonPhrase != null) {
        response.setReasonPhrase(reasonPhrase);
      }

      if (toTag != null) {
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
      }

      if (contact != null) {
        response.addHeader(parent.getHeaderFactory().createContactHeader(contact));
      }

      if (expires != -1) {
        response.addHeader(parent.getHeaderFactory().createExpiresHeader(expires));
      }

      putElements(response, additionalHeaders, replaceHeaders, body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return null;
    }

    return sendReply(request, response);
  }

  /**
   * This method is the same as the basic sendReply(RequestEvent, ...) method except that it allows
   * the caller to specify a message body and/or additional message headers to add to or replace in
   * the outbound message, without requiring knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'application' part of 'application/sdp'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
   * @param contentSubType The body content sub-type (ie, 'sdp' part of 'application/sdp'), required
   *        if there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param additionalHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message. Examples: "Priority: Urgent", "Max-Forwards: 10". These
   *        headers are added to the message after a correct message has been constructed. Note that
   *        if you try to add a header that there is only supposed to be one of in a message, and
   *        it's already there and only one single value is allowed for that header, then this
   *        header addition attempt will be ignored. Use the 'replaceHeaders' parameter instead if
   *        you want to replace the existing header with your own. Unpredictable results may occur
   *        if your headers are not syntactically correct or contain nonsensical values (the message
   *        may not pass through the local SIP stack). Use null for no additional message headers.
   * @param replaceHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message, replacing existing header(s) of that type if present in the
   *        message. Examples: "Priority: Urgent", "Max-Forwards: 10". These headers are applied to
   *        the message after a correct message has been constructed. Unpredictable results may
   *        occur if your headers are not syntactically correct or contain nonsensical values (the
   *        message may not pass through the local SIP stack). Use null for no replacement of
   *        message headers.
   * 
   */
  public SipTransaction sendReply(RequestEvent request, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires, String body, String contentType,
      String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return sendReply(request, statusCode, reasonPhrase, toTag, contact, expires,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * This method sends a stateful response to a previously received request. Call this method after
   * calling waitRequest(). The returned SipTransaction object must be used in any subsequent calls
   * to sendReply() for the same received request, if there are any.
   * 
   * @param request The RequestEvent object that was returned by a previous call to waitRequest().
   * @param response The response to send, as is.
   * @return A SipTransaction object that must be passed in to any subsequent call to sendReply()
   *         for the same received request, or null if an error was encountered while sending the
   *         response. The calling program doesn't need to do anything with the returned
   *         SipTransaction other than pass it in to a subsequent call to sendReply() for the same
   *         received request.
   */
  public SipTransaction sendReply(RequestEvent request, Response response) {
    initErrorInfo();

    if (request == null) {
      setReturnCode(INVALID_ARGUMENT);
      setErrorMessage("A response cannot be sent because the request event is null.");
      return null;
    }

    // The ServerTransaction needed will be in the
    // RequestEvent if the dialog already existed. Otherwise, create it
    // here.

    Request req = request.getRequest();
    if (req == null) {
      setReturnCode(INVALID_ARGUMENT);
      setErrorMessage("A response cannot be sent because the request is null.");
      return null;
    }

    ServerTransaction trans = request.getServerTransaction();
    if (trans == null) {
      try {
        trans = parent.getSipProvider().getNewServerTransaction(req);
      } catch (TransactionAlreadyExistsException ex) {
        /*
         * TransactionAlreadyExistsException - this can happen if a transaction already exists that
         * is already handling this Request. This may happen if the application gets retransmits of
         * the same request before the initial transaction is allocated.
         */

        setErrorMessage(
            "Error: Can't get transaction object. If you've already called sendReply(RequestEvent, ...) with this RequestEvent, use the SipTransaction it returned to call sendReply(SipTransaction, ...) for subsequent replies to that request.");
        setReturnCode(INTERNAL_ERROR);
        return null;
      } catch (Exception ex) {
        setException(ex);
        setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
        setReturnCode(EXCEPTION_ENCOUNTERED);
        return null;
      }
    }

    // create the SipTransaction, put the ServerTransaction in it
    SipTransaction transaction = new SipTransaction();
    transaction.setServerTransaction(trans);

    return sendReply(transaction, response);
  }

  /**
   * This method sends a basic, stateful response to a previously received request. The response is
   * constructed based on the parameters passed in. The returned SipTransaction object must be used
   * in any subsequent calls to sendReply() for the same received request, if there are any.
   * 
   * @param transaction The SipTransaction object returned from a previous call to sendReply().
   * @param statusCode The status code of the response to send (may use SipResponse constants).
   * @param reasonPhrase If not null, the reason phrase to send.
   * @param toTag If not null, it will be put into the 'To' header of the response. Required by
   *        final responses such as OK.
   * @param contact If not null, it will be used to create a 'Contact' header to be added to the
   *        response.
   * @param expires If not -1, an 'Expires' header is added to the response containing this value,
   *        which is the time the message is valid, in seconds.
   * @return A SipTransaction object that must be passed in to any subsequent call to sendReply()
   *         for the same received request, or null if an error was encountered while sending the
   *         response. The calling program doesn't need to do anything with the returned
   *         SipTransaction other than pass it in to a subsequent call to sendReply() for the same
   *         received request.
   */
  public SipTransaction sendReply(SipTransaction transaction, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires) {
    return sendReply(transaction, statusCode, reasonPhrase, toTag, contact, expires, null, null,
        null);
  }

  /**
   * This method is the same as the basic sendReply(SipTransaction, ...) method except that it
   * allows the caller to specify a message body and/or additional message headers to add to or
   * replace in the outbound message, without requiring knowledge of the JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'application' part of 'application/sdp'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
   * @param contentSubType The body content sub-type (ie, 'sdp' part of 'application/sdp'), required
   *        if there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param additionalHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message. Examples: "Priority: Urgent", "Max-Forwards: 10". These
   *        headers are added to the message after a correct message has been constructed. Note that
   *        if you try to add a header that there is only supposed to be one of in a message, and
   *        it's already there and only one single value is allowed for that header, then this
   *        header addition attempt will be ignored. Use the 'replaceHeaders' parameter instead if
   *        you want to replace the existing header with your own. Unpredictable results may occur
   *        if your headers are not syntactically correct or contain nonsensical values (the message
   *        may not pass through the local SIP stack). Use null for no additional message headers.
   * @param replaceHeaders ArrayList of String, each element representing a SIP message header to
   *        add to the outbound message, replacing existing header(s) of that type if present in the
   *        message. Examples: "Priority: Urgent", "Max-Forwards: 10". These headers are applied to
   *        the message after a correct message has been constructed. Unpredictable results may
   *        occur if your headers are not syntactically correct or contain nonsensical values (the
   *        message may not pass through the local SIP stack). Use null for no replacement of
   *        message headers.
   * 
   */
  public SipTransaction sendReply(SipTransaction transaction, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires, String body, String contentType,
      String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return sendReply(transaction, statusCode, reasonPhrase, toTag, contact, expires,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  protected ArrayList<Header> toHeader(List<String> strings) throws Exception {
    if (strings == null) {
      return null;
    }

    ArrayList<Header> headers = new ArrayList<>();

    Iterator<String> i = strings.iterator();
    while (i.hasNext()) {
      String str = (String) i.next();
      StringTokenizer tok = new StringTokenizer(str, ":");
      if (tok.countTokens() < 2) {
        throw new Exception(
            "Can't create SIP message header due to incorrect given header string (no HCOLON): "
                + str);
      }

      String header_name = tok.nextToken();
      String value = str.substring(header_name.length() + 1);

      Header hdr = parent.getHeaderFactory().createHeader(header_name.trim(), value.trim());

      headers.add(hdr);
    }

    return headers;
  }

  protected ArrayList<Header> toHeader(ArrayList<String> strings, String contentType,
      String contentSubType) throws Exception {
    ArrayList<Header> headers = toHeader(strings);
    if ((contentType == null) || (contentSubType == null)) {
      return headers;
    }

    if (headers == null) {
      headers = new ArrayList<>();
    }

    ContentTypeHeader ct_type =
        parent.getHeaderFactory().createContentTypeHeader(contentType, contentSubType);
    headers.add(0, ct_type); // override if header is already in
    // arraylist

    return headers;
  }

  /**
   * This method is the same as the basic sendReply(SipTransaction, ...) method except that it
   * allows the caller to specify a message body and/or additional JAIN-SIP API message headers to
   * add to or replace in the outbound message. Use of this method requires knowledge of the
   * JAIN-SIP API.
   * 
   * The extra parameters supported by this method are:
   * 
   * @param additionalHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add
   *        to the outbound message. These headers are added to the message after a correct message
   *        has been constructed. Note that if you try to add a header that there is only supposed
   *        to be one of in a message, and it's already there and only one single value is allowed
   *        for that header, then this header addition attempt will be ignored. Use the
   *        'replaceHeaders' parameter instead if you want to replace the existing header with your
   *        own. Use null for no additional message headers.
   * @param replaceHeaders ArrayList of javax.sip.header.Header, each element a SIP header to add to
   *        the outbound message, replacing existing header(s) of that type if present in the
   *        message. These headers are applied to the message after a correct message has been
   *        constructed. Use null for no replacement of message headers.
   * @param body A String to be used as the body of the message. The additionalHeaders parameter
   *        must contain a ContentTypeHeader for this body to be included in the message. Use null
   *        for no body bytes.
   */
  public SipTransaction sendReply(SipTransaction transaction, int statusCode, String reasonPhrase,
      String toTag, Address contact, int expires, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    if ((transaction == null) || (transaction.getRequest() == null)) {
      setErrorMessage("Cannot send reply, transaction or request information is null");
      setReturnCode(INVALID_ARGUMENT);
      return null;
    }

    Response response;

    try {
      response = parent.getMessageFactory().createResponse(statusCode, transaction.getRequest());

      if (reasonPhrase != null) {
        response.setReasonPhrase(reasonPhrase);
      }

      if (toTag != null) {
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
      }

      if (contact != null) {
        response.addHeader(parent.getHeaderFactory().createContactHeader(contact));
      }

      if (expires != -1) {
        response.addHeader(parent.getHeaderFactory().createExpiresHeader(expires));
      }

      putElements(response, additionalHeaders, replaceHeaders, body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return null;
    }

    return sendReply(transaction, response);
  }

  protected void putElements(Message message, List<Header> additionalHeaders,
                             List<Header> replaceHeaders, String body) throws Exception {
    // check for additional headers and body to add
    if (additionalHeaders != null) {
      Iterator<Header> i = additionalHeaders.iterator();
      while (i.hasNext()) {
        Header h = (Header) i.next();

        if (h.getName().equals(ContentTypeHeader.NAME)) {
          if (body == null) {
            body = "";
          }

          message.setContent(body.getBytes(), (ContentTypeHeader) h);
        } else {
          message.addHeader(h);
        }
      }
    }

    // check for headers to replace
    if (replaceHeaders != null) {
      Iterator<Header> i = replaceHeaders.iterator();
      while (i.hasNext()) {
        message.setHeader((Header) i.next());
      }
    }
  }

  /**
   * This method sends a stateful response to a previously received request. The returned
   * SipTransaction object must be used in any subsequent calls to sendReply() for the same received
   * request, if there are any.
   * 
   * @param transaction The SipTransaction object returned from a previous call to sendReply().
   * @param response The response to send, as is.
   * @return A SipTransaction object that must be passed in to any subsequent call to sendReply()
   *         for the same received request, or null if an error was encountered while sending the
   *         response. The calling program doesn't need to do anything with the returned
   *         SipTransaction other than pass it in to a subsequent call to sendReply() for the same
   *         received request.
   */
  public SipTransaction sendReply(SipTransaction transaction, Response response) {
    initErrorInfo();

    if ((transaction == null) || (transaction.getServerTransaction() == null)) {
      setErrorMessage("Cannot send reply, transaction information is null");
      setReturnCode(INVALID_ARGUMENT);
      return null;
    }

    // send the message
    try {
      SipStack.dumpMessage("Response before sending out through stack", response);
      transaction.getServerTransaction().sendResponse(response);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return null;
    }

    return transaction;
  }

  /**
   * The sendUnidirectionalResponse() method sends out a stateless response message. The response is
   * sent out as is.
   * 
   * @param response The response to be sent out.
   * @return true if the message was successfully sent, false otherwise.
   */
  public boolean sendUnidirectionalResponse(Response response) {
    initErrorInfo();
    LOG.info("Sending unidirectional response: {}", response);

    try {
      parent.getSipProvider().sendResponse(response);
      return true;
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      LOG.info(this.format());
      return false;
    }
  }

  /**
   * This method sends out a stateless response to the given request.
   * 
   * @param request The RequestEvent object that contains the request we are responding to.
   * @param statusCode The status code of the response to send (may use SipResponse constants).
   * @param reasonPhrase If not null, the reason phrase to send.
   * @param toTag If not null, it will be put into the 'To' header of the response. Required by
   *        final responses such as OK.
   * @param contact If not null, it will be used to create a 'Contact' header to be added to the
   *        response.
   * @param expires If not -1, an 'Expires' header is added to the response containing this value,
   *        which is the time the message is valid, in seconds.
   * @return True if the response was successfully sent, false otherwise.
   */
  public boolean sendUnidirectionalResponse(RequestEvent request, int statusCode,
      String reasonPhrase, String toTag, Address contact, int expires) {
    initErrorInfo();

    if ((request == null) || (request.getRequest() == null)) {
      setErrorMessage("Cannot send response, request information is null");
      setReturnCode(INVALID_ARGUMENT);
      return false;
    }

    Response response;

    try {
      response = parent.getMessageFactory().createResponse(statusCode, request.getRequest());

      if (reasonPhrase != null) {
        response.setReasonPhrase(reasonPhrase);
      }

      if (toTag != null) {
        ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
      }

      if (contact != null) {
        response.addHeader(parent.getHeaderFactory().createContactHeader(contact));
      }

      if (expires != -1) {
        response.addHeader(parent.getHeaderFactory().createExpiresHeader(expires));
      }

    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(EXCEPTION_ENCOUNTERED);
      return false;
    }

    return sendUnidirectionalResponse(response);
  }

  /**
   * The getAuthorization() method generates an authorisation header in response to an
   * authentication challenge. The WWWAuthenticateHeader parameter can represent an UAS-&gt;UAC
   * challenge (received status code = Response.UNAUTHORIZED) or a Proxy-&gt;UAC challenge (received
   * status code = Response.PROXY_AUTHENTICATION_REQUIRED).
   * 
   * This method was copied from the Sip Communicator project (package
   * net.java.sip.communicator.sip.security.SipSecurityManager, its author is Emil Ivov) and
   * slightly modified to fit here. Thanks for making it publicly available. It is licensed under
   * the Apache Software License, Version 1.1 Copyright (c) 2000.
   * 
   * @param method method of the request being authenticated.
   * @param uri digest-uri. This is the request uri of the request (ie,
   *        request.getRequestURI().toString()).
   * @param requestBody the body of the request message being authenticated. IE:
   *        request.getContent()==null?"":request.getContent().toString()
   * @param authHeader the challenge that we are responding to. The caller should pass in one of the
   *        following received response headers: WWWAuthenticateHeader (typically associated with
   *        status code Response.UNAUTHORIZED) or ProxyAuthenticateHeader (usually associated with
   *        status code Response.PROXY_AUTHENTICATION_REQUIRED).
   * @param username the name of the user to send to the challenging server
   * @param password the user's password
   * @return The AuthorizationHeader to use for this challenge.
   * @throws SecurityException
   * 
   */

  public AuthorizationHeader getAuthorization(String method, String uri, String requestBody,
      WWWAuthenticateHeader authHeader, String username, String password) throws SecurityException {
    String response = null;
    try {
      response = MessageDigestAlgorithm.calculateResponse(authHeader.getAlgorithm(), username,
          authHeader.getRealm(), new String(password), authHeader.getNonce(),
          // TODO we should one day implement those two null-s
          null, // nc-value
          null, // cnonce
          method, uri, requestBody, authHeader.getQop());
    } catch (NullPointerException exc) {
      throw new SecurityException(
          "The received authenticate header was malformatted: " + exc.getMessage());
    }

    AuthorizationHeader authorization = null;
    try {
      if (authHeader instanceof ProxyAuthenticateHeader) {
        authorization =
            parent.getHeaderFactory().createProxyAuthorizationHeader(authHeader.getScheme());
      } else {
        authorization = parent.getHeaderFactory().createAuthorizationHeader(authHeader.getScheme());
      }

      authorization.setUsername(username);
      authorization.setRealm(authHeader.getRealm());
      authorization.setNonce(authHeader.getNonce());
      authorization.setParameter("uri", uri);
      authorization.setResponse(response);
      if (authHeader.getAlgorithm() != null)
        authorization.setAlgorithm(authHeader.getAlgorithm());
      if (authHeader.getOpaque() != null)
        authorization.setOpaque(authHeader.getOpaque());

      authorization.setResponse(response);
    } catch (ParseException ex) {
      throw new SecurityException("Failed to create an authorization header: "
          + ex.getClass().getName() + ": " + ex.getMessage());
    }

    return authorization;
  }

  /**
   * This method returns the Via Header currently in effect for this user agent, needed for sending
   * requests such as INVITE. By default, it is set to the IP address and port used by this user
   * agent's sip stack. But if the setPublicAddress() has been called on this object, the returned
   * value will reflect the most recent call to setPublicAddress().
   * 
   * @return an ArrayList containing a single element: the javax.sip.header.ViaHeader currently in
   *         effect for this user agent.
   */
  public ArrayList<ViaHeader> getViaHeaders() {
    return new ArrayList<>(viaHeaders);
  }

  /**
   * This method is the same as getViaHeaders().
   * 
   * @deprecated Use getViaHeaders() instead of this method, the term 'local' in the method name is
   *             misleading if the SipUnit test is running behind a NAT.
   * 
   * @return A list of ViaHeader
   */
  public ArrayList<ViaHeader> getLocalViaHeaders() {
    return getViaHeaders();
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getErrorMessage()
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getException()
   */
  public Throwable getException() {
    return exception;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getReturnCode()
   */
  public int getReturnCode() {
    return returnCode;
  }

  /**
   * This method indicates if the given return code is an internal SipUnit return code or not.
   * 
   * @param returnCode the return code in question
   * @return true if the return code is internal to SipUnit, false if it is a SIP RFC 3261 return
   *         code.
   */
  public static boolean isInternal(int returnCode) {
    if (returnCode >= SipSession.SIPUNIT_INTERNAL_RETURNCODE_MIN) {
      return true;
    }

    return false;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#format()
   */
  public String format() {
    if (SipSession.isInternal(returnCode) == true) {
      return SipSession.statusCodeDescription.get(new Integer(returnCode))
          + (errorMessage.length() > 0 ? (": " + errorMessage) : "");
    } else {
      return "Status code received from network = " + returnCode + ", "
          + SipResponse.statusCodeDescription.get(new Integer(returnCode))
          + (errorMessage.length() > 0 ? (": " + errorMessage) : "");
    }
  }

  protected void clearTransaction(SipTransaction sip_trans) {
    synchronized (respTransactions) {
      respTransactions.remove(sip_trans.getClientTransaction());
    }
  }

  /**
   * @param errorMessage The errorMessage to set.
   */
  protected void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  protected void initErrorInfo() {
    setErrorMessage("");
    setException(null);
    setReturnCode(SipSession.NONE_YET);
  }

  /**
   * @param exception The exception to set.
   */
  protected void setException(Throwable exception) {
    this.exception = exception;
  }

  /**
   * @param returnCode The returnCode to set.
   */
  protected void setReturnCode(int returnCode) {
    this.returnCode = returnCode;
  }

  protected void addRequestListener(String requestMethod, RequestListener listener) {
    // multiple listeners per method

    synchronized (requestListeners) {
      ArrayList<RequestListener> listeners = requestListeners.get(requestMethod);
      if (listeners == null) {
        listeners = new ArrayList<>();
        requestListeners.put(requestMethod, listeners);
      }

      listeners.add(listener);
    }
  }

  protected void removeRequestListener(String requestMethod, RequestListener listener) {
    // multiple listeners per method

    synchronized (requestListeners) {
      List<RequestListener> listeners = requestListeners.get(requestMethod);
      if (listeners != null) {
        listeners.remove(listener);
        if (listeners.isEmpty()) {
          requestListeners.remove(requestMethod);
        }
      }
    }
  }

  /**
   * Call this method to get the IP address being used by this user agent (ie, the address it is
   * putting in its contact header, via header, etc. when it sends out messages).
   * 
   * @return A String containing the host IP address being used by this user agent.
   */
  public String getStackAddress() {
    return this.myhost;
  }

  /**
   * @return Returns the loopback. See setLoopback().
   */
  public boolean isLoopback() {
    return loopback;
  }

  /**
   * Under normal circumstances, the SipUnit SipPhone/SipSession shouldn't accept a request if the
   * Request URI doesn't match it's contact address, even if the 'To' header matches the SipPhone's
   * address. By calling this method with parm loopback = true, this object will accept a request if
   * the 'To' header matches even if the Request URI doesn't - so that local messaging tests without
   * proxy still work. This is for direct UA-UA testing convenience. This should not be the default,
   * however.
   * 
   * @param loopback The loopback to set.
   */
  public void setLoopback(boolean loopback) {
    this.loopback = loopback;
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
}
