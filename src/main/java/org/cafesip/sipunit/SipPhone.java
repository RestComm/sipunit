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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.TimeoutEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * This class provides a test program with User Agent (UA) access to the SIP protocol in the form of
 * a SIP phone. Using this class, a test program can simulate a SIP phone and perform operations
 * such as registration, making an outgoing call or receiving an incoming call, buddy list/fetch
 * (SUBSCRIBE/NOTIFY) operations, call refer, etc. In future, a SipPhone object can have more than
 * one SipCall object associated with it but currently only one is supported. Multiple subscriptions
 * (buddy/presence, refer) are supported per SipPhone object.
 * 
 * <p>
 * A SipPhone object is created by calling SipStack.createSipPhone().
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
public class SipPhone extends SipSession implements SipActionObject, RequestListener {

  private static final Logger LOG = LoggerFactory.getLogger(SipPhone.class);

  public static final int DEFAULT_SUBSCRIBE_DURATION = 3600;

  private CSeqHeader cseq;

  private Request lastRegistrationRequest;

  private Hashtable<String, Credential> credentials = new Hashtable<>();

  private Hashtable<String, LinkedHashMap<String, AuthorizationHeader>> authorizations =
      new Hashtable<>();

  private ArrayList<SipCall> callList = new ArrayList<>();

  private Hashtable<String, PresenceSubscriber> buddyList = new Hashtable<>();

  // These are the buddies that have been added to the buddy list by the test
  // program.
  // A given subscription in this list may be active or not. Buddies are
  // removed from
  // the list only by the test program. Buddies in the list may have their
  // subscriptions kept alive (re-subscribed) automatically - TODO.

  private Hashtable<String, PresenceSubscriber> buddyTerminatedList = new Hashtable<>();

  // This list contains Subscriptions that are not in the buddy list - they
  // get here
  // either because of a 'fetch' or whenever the test program removes a buddy
  // from
  // the buddy list. The main purpose is so the last known status of a
  // buddy/user can
  // be obtained. This is required for the fetch case, an added bonus for the
  // removed
  // buddy situation.

  private List<ReferSubscriber> refererList = new ArrayList<>();

  protected SipPhone(SipStack stack, String host, String proto, int port, String me)
      throws ParseException, InvalidArgumentException {
    super(stack, host, proto, port, me);
    this.addRequestListener(Request.NOTIFY, this);
  }

  // TODO, all SipPhone creation on a per-SipProvider basis, use its
  // listening point info for setting up phone

  protected SipPhone(SipStack stack, String host, String me)
      throws ParseException, InvalidArgumentException {
    super(stack, host, me);
    this.addRequestListener(Request.NOTIFY, this);
  }

  /**
   * This method is used for SIP registration. By default, the Request-URI is derived from this
   * SipPhone's proxy host, or if no proxy was specified when this SipPhone was created, the
   * Request-URI address information is taken from this SipPhone's URI (address of record). For
   * other Request-URI alternatives, see the register() method that takes parameter requestUri.
   * 
   * <p>
   * Initially, a REGISTER message is sent without any user name and password. If the server returns
   * an OK, this method returns a true value.
   * 
   * <p>
   * If any challenge is received in response to sending the REGISTER message (response code
   * UNAUTHORIZED or PROXY_AUTHENTICATION_REQUIRED), the SipPhone's credentials list is checked
   * first for the corresponding realm entry. If found, the credentials list entry username and
   * password are used to form the required authorization header for resending the REGISTER message
   * to the server, and the authorization header is saved for later re-use. You can clear out saved
   * authorization headers by calling the unregister() method.
   *
   * <p>
   * If the challenging realm is not found in the SipPhone credentials list, the user parameter
   * passed to this method is examined. If it is null, this method returns false. If it is not null,
   * the user and password values passed in to this method are used to respond to the challenge. The
   * credentials list is not modified by this scenario (no entry is automatically added with this
   * user, password). Also, the authorization created for this registration is not saved for re-use
   * on a later registration. IE, the user/password parameters are for a one-time, single-shot use
   * only.
   * 
   * <p>
   * After responding to the challenge(s) by resending the REGISTER message, this method returns a
   * true or false value depending on the outcome as indicated by the server.
   * 
   * <p>
   * If the contact parameter is null, user@hostname is used where hostname is the SipStack's IP
   * address property which defaults to InetAddress.getLocalHost().getHostAddress(), and other
   * SipStack properties also apply. Otherwise, the contact parameter given is used in the
   * Registration message sent to the server.
   * 
   * <p>
   * If the expiry parameter is 0, the registration request never expires. Otherwise, the duration,
   * given in seconds, is sent to server.
   * 
   * <p>
   * This method can be called repeatedly to update the expiry or to add new contacts.
   * 
   * <p>
   * This method determines the contact information for this user agent, whether the registration
   * was successful or not. If successful, the contact information may have been updated by the
   * server (such as the expiry time, if not specified to this method by the caller). Once this
   * method has been called, the test program can get information about the contact for this agent
   * by calling the *MyContact*() getter methods.
   * 
   * @param user Optional - user name for authenticating with the server. Required if the server
   *        issues an authentication challenge.
   * @param password Optional - used only if the server issues an authentication challenge.
   * @param contact An URI string (ex: sip:bob@192.0.2.4), or null to use the default contact for
   *        this user agent.
   * @param expiry Expiry time in seconds, or 0 if no registration expiry.
   * @param timeout The maximum amount of time to wait for a response, in milliseconds. Use a value
   *        of 0 to wait indefinitely.
   * @return false if registration fails or an error is encountered, true otherwise. In case of
   *         false, call getErrorMessage(), getReturnCode() and/or getException() methods to find
   *         out why.
   */
  public boolean register(String user, String password, String contact, int expiry, long timeout) {
    AddressFactory addrFactory = parent.getAddressFactory();
    SipURI requestUri = null;

    try {
      if (proxyHost != null) {
        requestUri = addrFactory.createSipURI(null, proxyHost);
        requestUri.setPort(proxyPort);
        requestUri.setTransportParam(proxyProto);
      }

      return register(requestUri, user, password, contact, expiry, timeout);
    } catch (Exception ex) {
      setReturnCode(EXCEPTION_ENCOUNTERED);
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      return false;
    }
  }

  /**
   * This method is the same as the register(String user, String password, String contact, int
   * expiry, long timeout) method except for the Request-URI used in the outgoing REGISTER method.
   * If the requestUri parameter passed into this method is null, the Request-URI for the REGISTER
   * is derived from this SipPhone's URI, or address of record (for example, if this SipPhone's
   * address of record is "sip:amit@cafesip.org", the REGISTER Request-URI will be sip:cafesip.org).
   * Otherwise, the requestUri passed in is used for the REGISTER Request-URI.
   * 
   */
  public boolean register(SipURI requestUri, String user, String password, String contact,
      int expiry, long timeout) {
    initErrorInfo();

    try {
      AddressFactory addr_factory = parent.getAddressFactory();
      HeaderFactory hdr_factory = parent.getHeaderFactory();

      if (requestUri == null) {
        requestUri = addr_factory.createSipURI(null, ((SipURI) (myAddress.getURI())).getHost());
        requestUri.setPort(((SipURI) (myAddress.getURI())).getPort());
        if (((SipURI) (myAddress.getURI())).getTransportParam() != null) {
          requestUri.setTransportParam(((SipURI) (myAddress.getURI())).getTransportParam());
        }
      }

      String method = Request.REGISTER;

      ToHeader to_header = hdr_factory.createToHeader(myAddress, null);
      FromHeader from_header = hdr_factory.createFromHeader(myAddress, generateNewTag());

      CallIdHeader callid_header = hdr_factory.createCallIdHeader(myRegistrationId);

      cseq = hdr_factory.createCSeqHeader(cseq == null ? 1 : (cseq.getSeqNumber() + 1), method);

      MaxForwardsHeader max_forwards = hdr_factory.createMaxForwardsHeader(MAX_FORWARDS_DEFAULT);

      if (contact != null) {
        URI uri = addr_factory.createURI(contact);
        if (uri.isSipURI() == false) {
          setReturnCode(INVALID_ARGUMENT);
          setErrorMessage("URI " + contact + " is not a Sip URI");
          return false;
        }

        Address contact_address = addr_factory.createAddress(uri);
        ContactHeader hdr = hdr_factory.createContactHeader(contact_address);
        hdr.setExpires(expiry);

        synchronized (contactLock) {
          contactInfo = new SipContact();
          contactInfo.setContactHeader(hdr);
        }
      }

      List<ViaHeader> via_headers = getViaHeaders();

      Request msg = parent.getMessageFactory().createRequest(requestUri, method, callid_header,
          cseq, from_header, to_header, via_headers, max_forwards);

      msg.addHeader(contactInfo.getContactHeader()); // use
      // setHeader()?

      if (expiry > 0) {
        ExpiresHeader expires = hdr_factory.createExpiresHeader(expiry);
        msg.setExpires(expires);
      }

      // include any auth information for this User Agent's registration
      // if any exists

      Map<String, AuthorizationHeader> auth_list =
          getAuthorizations().get(myRegistrationId);
      if (auth_list != null) {
        List<AuthorizationHeader> auth_headers =
            new ArrayList<>(auth_list.values());
        Iterator<AuthorizationHeader> i = auth_headers.iterator();
        while (i.hasNext()) {
          AuthorizationHeader auth = i.next();
          msg.addHeader(auth);
        }
      } else {
        // create the auth list entry for this phone's registrations
        enableAuthorization(myRegistrationId);
      }

      // send the REGISTRATION request and get the response
      Response response = sendRegistrationMessage(msg, user, password, timeout);
      if (response == null) {
        return false;
      }

      // update our contact info with that of the server response -
      // server may have reset our contact expiry

      ListIterator<?> contacts = response.getHeaders(ContactHeader.NAME);
      if (contacts != null) {
        while (contacts.hasNext()) {
          // TODO - at some point save ALL the contact headers and
          // provide a getter for the list of SipContact objects
          // (gobalContactList).
          // dispose() and unregister() can use the list of contact
          // headers.
          // for now just save this agent's info

          ContactHeader hdr = (ContactHeader) contacts.next();
          if (hdr.getAddress().getURI().toString().equals(contactInfo.getURI()) == true) {
            contactInfo.setContactHeader(hdr);
            break;
          }
        }
      }

      return true;
    } catch (Exception ex) {
      setReturnCode(EXCEPTION_ENCOUNTERED);
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      return false;
    }
  }

  /**
   * This method is equivalent to the register(String user, String password, String contact, int
   * expiry, long timeout) method except with no authorization parameters specified. Call this
   * method if no authorization will be needed or after setting up the SipPhone's credentials list.
   * 
   * @param contact An URI string (ex: sip:bob@192.0.2.4)
   * @param expiry Expiry time in seconds, or 0 if no expiry.
   * @return false if registration fails or an error is encountered, true otherwise.
   */
  public boolean register(String contact, int expiry) {
    return register(null, null, contact, expiry, 0);
  }

  /**
   * This method performs the SIP unregistration process. It returns true if unregistration was
   * successful or no unregistration was needed, and false otherwise. Any authorization headers
   * required for the last registration are cleared out. If there was no previous registration, this
   * method does not send any messages.
   * 
   * <p>
   * If the contact parameter is null, user@hostname is unregistered where hostname is obtained by
   * calling InetAddr.getLocalHost(). Otherwise, the contact parameter value is used in the
   * unregistration message sent to the server.
   * 
   * @param contact The contact URI (ex: sip:bob@192.0.2.4) to unregister or "*".
   * @param timeout The maximum amount of time to wait for a response, in milliseconds. Use a value
   *        of 0 to wait indefinitely.
   * 
   * @return true if the unregistration succeeded or no unregistration was needed, false otherwise.
   */
  public boolean unregister(String contact, long timeout) {
    initErrorInfo();

    // TODO - need to support multiple server(s)/registrations
    // simultaneously?
    // then return registration() object to user (w/lastregrequest) and
    // receive
    // it here, get rid of lastRegistrationRequest

    if (lastRegistrationRequest == null) {
      return true;
    }

    Request msg = (Request) lastRegistrationRequest.clone();

    try {
      ExpiresHeader expires = parent.getHeaderFactory().createExpiresHeader(0);
      msg.setExpires(expires);

      cseq.setSeqNumber(cseq.getSeqNumber() + 1);
      cseq.setMethod(Request.REGISTER);
      msg.setHeader(cseq);

      // set contact header

      if (contact != null) {
        ContactHeader contact_hdr;

        if (!contact.equals("*")) {
          URI contact_uri = parent.getAddressFactory().createURI(contact);
          Address contact_address = parent.getAddressFactory().createAddress(contact_uri);
          contact_hdr = parent.getHeaderFactory().createContactHeader(contact_address);
        } else {
          contact_hdr = parent.getHeaderFactory().createContactHeader();
        }

        msg.setHeader(contact_hdr);
      }

      // send the REGISTRATION request and get the response
      Response response = sendRegistrationMessage(msg, null, null, 30000);
      if (response == null) {
        return false;
      }

      // clear out authorizations accumulated for this Call-ID
      clearAuthorizations(myRegistrationId);

      // should we drop any calls in progress?

      lastRegistrationRequest = null;

      return true;
    } catch (Exception ex) {
      setReturnCode(EXCEPTION_ENCOUNTERED);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      return false;
    }

  }

  private Response sendRegistrationMessage(Request msg, String user, String password,
      long timeout) {
    SipTransaction trans = sendRequestWithTransaction(msg, false, null);

    // get the response
    EventObject response_event = waitResponse(trans, timeout);

    if (response_event == null) // user timeout or error
    {
      return null;
    }

    if (response_event instanceof TimeoutEvent) {
      setReturnCode(TIMEOUT_OCCURRED);
      setErrorMessage("A Timeout Event was received");
      return null;
    }

    Response response = ((ResponseEvent) response_event).getResponse();
    int status_code = response.getStatusCode();

    while (status_code != Response.OK) {
      if (status_code == Response.TRYING) {
        response_event = waitResponse(trans, timeout);

        if (response_event == null) // user timeout or error
        {
          return null;
        }

        if (response_event instanceof TimeoutEvent) {
          setReturnCode(TIMEOUT_OCCURRED);
          setErrorMessage("A Timeout Event was received");
          return null;
        }

        response = ((ResponseEvent) response_event).getResponse();
        status_code = response.getStatusCode();
        continue;
      } else if ((status_code == Response.UNAUTHORIZED)
          || (status_code == Response.PROXY_AUTHENTICATION_REQUIRED)) {
        // modify the request to include user authorization info

        msg = processAuthChallenge(response, msg, user, password);
        if (msg == null) {
          return null;
        }

        try {
          // bump up the cseq number
          cseq.setSeqNumber(cseq.getSeqNumber() + 1);
          msg.setHeader(cseq);
        } catch (Exception ex) {
          setReturnCode(EXCEPTION_ENCOUNTERED);
          setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
          return null;
        }

        // clean up last transaction
        clearTransaction(trans);

        // send the request again
        trans = sendRequestWithTransaction(msg, false, null);

        // check response
        response_event = waitResponse(trans, timeout);

        if (response_event == null) {
          // user timeout or error
          return null;
        }

        if (response_event instanceof TimeoutEvent) {
          setReturnCode(TIMEOUT_OCCURRED);
          setErrorMessage("A TimeoutEvent was received");
          return null;
        }

        response = ((ResponseEvent) response_event).getResponse();
        status_code = response.getStatusCode();
        continue;
      } else {
        setReturnCode(status_code);
        setErrorMessage(
            "An unsuccessful or error status code was received from the server: " + status_code);
        return null;
      }
    }

    lastRegistrationRequest = msg;

    return response;
  }

  /**
   * This method is public for test purposes and for use when using low level SipSession methods for
   * sending/receiving messages. A test program using high level SipUnit doesn't need to call this
   * method.
   * 
   * <p>
   * This method modifies the given request to include the authorization header(s) required by the
   * given response. It may cache in SipPhone's authorizations list the AuthorizationHeader(s)
   * created here for use later. The modified Request object is returned, or null in case of error
   * or unresolved challenge.
   * 
   * <p>
   * For each received challenge present in the response message: SipPhone's credentials list is
   * checked first, for the realm entry. If it is not found there, the username parameter passed
   * into this method is checked. If null, this method returns null. If not null, the passed in
   * username and password values are used to respond to the received challenge. In this case an
   * entry is NOT added to the credentials list associating these values with the challenging realm
   * and the authorization created here is NOT saved for later re-use. If the credentials list
   * contains an entry for the challenging realm, then the authorization created here is saved in
   * the authorizations list for later re-use.
   * 
   * @param response the challenge that was received
   * @param req_msg the request originally sent (that was challenged)
   * @param username see above
   * @param password see above
   * @return the original request with the authorization header(s) added, or null if username and
   *         password weren't passed in and the SipPhone's credentials list doesn't have an entry
   *         for the realm.
   */
  public Request processAuthChallenge(Response response, Request req_msg, String username,
      String password) {
    initErrorInfo();

    ListIterator<?> challenges = null;
    if (response.getStatusCode() == Response.UNAUTHORIZED) {
      challenges = response.getHeaders(WWWAuthenticateHeader.NAME);
    } else if (response.getStatusCode() == Response.PROXY_AUTHENTICATION_REQUIRED) {
      challenges = response.getHeaders(ProxyAuthenticateHeader.NAME);
    }

    if (challenges == null) {
      setReturnCode(ERROR_OF_UNKNOWN_ORIGIN);
      setErrorMessage(
          "Improper use of processAuthChallenge() or response auth challenge header is missing");
      return null;
    }

    // find the list of cached AuthorizationHeaders for this call (Call-ID)
    String call_id = ((CallIdHeader) req_msg.getHeader(CallIdHeader.NAME)).getCallId();
    Map<String, AuthorizationHeader> authorization_list =
        getAuthorizations().get(call_id);

    if (authorization_list == null) {
      // it should have been created already when sent or received 1st
      // request for the
      // call ID
      setReturnCode(ERROR_OF_UNKNOWN_ORIGIN);
      setErrorMessage(
          "Invalid Call-ID header in request or the call's authorization list wasn't created");
      return null;
    }

    Request msg;
    try {
      msg = (Request) req_msg.clone();
      msg.setMethod(req_msg.getMethod());
    } catch (Exception ex) {
      setReturnCode(EXCEPTION_ENCOUNTERED);
      setErrorMessage(
          "Exception while cloning request: " + ex.getClass().getName() + ": " + ex.getMessage());
      setException(ex);
      return null;
    }

    int content_length = 0;
    if (req_msg.getContentLength() != null) {
      content_length = req_msg.getContentLength().getContentLength();
    }
    String req_body = null;
    if (content_length > 0) {
      req_body = new String(req_msg.getRawContent());
    }

    // loop through the challenges received and create an
    // authorization for each one
    while (challenges.hasNext()) {
      WWWAuthenticateHeader authenticate_header = (WWWAuthenticateHeader) challenges.next();

      String realm = authenticate_header.getRealm();
      String uname = username;
      String passwd = password;

      // check credentials list for this realm entry
      Credential credential = (Credential) credentials.get(realm);

      if (credential != null) {
        uname = credential.getUser();
        passwd = credential.getPassword();
      } else if (uname == null) {
        setReturnCode(MISSING_CREDENTIAL);
        setErrorMessage("Could not find credentials for realm: " + realm);
        return null;
      }

      try {
        AuthorizationHeader authorization = getAuthorization(msg.getMethod(),
            msg.getRequestURI().toString(), req_body, authenticate_header, uname, passwd);

        // what was wrong with req_body = msg.getContent() == null ? ""
        // : msg.getContent()
        // .toString() ?

        // save the auth header for use later, overwriting old one if
        // there

        if (credential != null) {
          authorization_list.put(realm, authorization);
        }

        // Add/replace this authorization header in the message

        // should we be replacing? or just add to it. Looks like you can
        // have lots
        // of auth headers for a given realm and call-ID. TODO - confirm
        // and change
        // authorizations to hold more than one auth header per realm
        // per call-ID

        msg.setHeader(authorization);

        /*
         * here's replace code
         * 
         * ListIterator msg_headers; if (authorization instanceof ProxyAuthorizationHeader) {
         * msg_headers = msg.getHeaders(ProxyAuthorizationHeader.NAME); } else { msg_headers =
         * msg.getHeaders(AuthorizationHeader.NAME); }
         * 
         * boolean replaced = false;
         * 
         * 
         * while (msg_headers.hasNext()) { AuthorizationHeader msg_hdr = (AuthorizationHeader)
         * msg_headers .next(); if (msg_hdr.getRealm().equals(realm)) {
         * msg_headers.set(authorization); replaced = true; break; } }
         * 
         * 
         * if (replaced == false) { msg.addHeader(authorization); // how to bubble auth up - //
         * check 1.2 API }
         */
      } catch (Exception ex) {
        setReturnCode(EXCEPTION_ENCOUNTERED);
        setErrorMessage("Exception while creating AuthorizationHeader(s): "
            + ex.getClass().getName() + ": " + ex.getMessage());
        setException(ex);
        return null;
      }
    }

    // do we need to compare new reqmsg auth headers to old reqmsg auth
    // headers - if
    // same, return null

    return msg;

  }

  /**
   * This method is the same as the other processAuthChallenge() without the user/password option.
   */
  public Request processAuthChallenge(Response response, Request req_msg) {
    return processAuthChallenge(response, req_msg, null, null);
  }

  /**
   * This method is used to create a SipCall object for handling one leg of a call. That is, it
   * represents an outgoing call leg or an incoming call leg. In a telephone call, there are two
   * call legs. The outgoing call leg is the connection from the phone making the call to the
   * telephone network. The incoming call leg is a connection from the telephone network to the
   * phone being called. For a SIP call, the outbound leg is the user agent originating the call and
   * the inbound leg is the user agent receiving the call. The test program can use this method to
   * create a SipCall object for handling an incoming call leg or an outgoing call leg. Currently,
   * only one SipCall object is supported per SipPhone. In future, when more than one SipCall per
   * SipPhone is supported, this method can be called multiple times to create multiple call legs on
   * the same SipPhone object.
   * 
   * @return A SipCall object unless an error is encountered.
   */
  public SipCall createSipCall() {
    initErrorInfo();

    SipCall call = new SipCall(this, myAddress);

    callList.add(call);

    return call;
  }

  protected void dropCall(SipCall call) {
    callList.remove(call);
  }

  /**
   * This blocking basic method is used to make an outgoing call. It blocks until the specified
   * INVITE response status code is received. The object returned is a SipCall object representing
   * the outgoing call leg; that is, the UAC originating a call to the network. Then you can take
   * subsequent action on the call by making method calls on the SipCall object.
   * 
   * <p>
   * Use this method when (1) you want to establish a call without worrying about the details and
   * (2) your test program doesn't need to do anything else (ie, it can be blocked) until the
   * response code parameter passed to this method is received from the network.
   * 
   * <p>
   * In case the first condition above is false: If you need to see the (intermediate/provisional)
   * response messages as they come in, then use SipPhone.createSipCall() and
   * SipCall.initiateOutgoingCall() instead of this method. If your test program can tolerate being
   * blocked until the desired response is received, you can still use this method and later look
   * back at all the received responses by calling SipCall.getAllReceivedResponses().
   * 
   * <p>
   * In case the second condition above is false: If your test code is handling both sides of the
   * call, or it has to do other things while this call establishment is in progress, then this
   * method's blocking gets in the way. In that case, use the other SipPhone.makeCall() method. It
   * returns a SipCall object after the INVITE has been successfully sent. Then, later on you can
   * check back with the SipCall object to see the call progress or block on the call establishment,
   * at a more convenient time.
   * 
   * 
   * @param to The URI string (ex: sip:bob@nist.gov) to which the call should be directed
   * @param response The SipResponse status code to look for after sending the INVITE. This method
   *        returns when that status code is received.
   * @param timeout The maximum amount of time to wait for the response, in milliseconds. Use a
   *        value of 0 to wait indefinitely.
   * @param viaNonProxyRoute Indicates whether to route the INVITE via Proxy or some other route. If
   *        null, route the call to the Proxy that was specified when the SipPhone object was
   *        created (SipStack.createSipPhone()). Else route it to the given node, which is specified
   *        as "hostaddress:port/transport" i.e. 129.1.22.333:5060/UDP.
   * @return A SipCall object representing the outgoing call leg, or null if an error was
   *         encountered.
   */
  public SipCall makeCall(String to, int response, long timeout, String viaNonProxyRoute) {
    return makeCall(to, response, timeout, viaNonProxyRoute, null, null, null);
  }

  /**
   * This method is the same as the basic blocking makeCall() method except that it allows the
   * caller to specify a message body and/or additional message headers to add to or replace in the
   * outbound message without requiring knowledge of the JAIN-SIP API.
   * 
   * <p>
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
  public SipCall makeCall(String to, int response, long timeout, String viaNonProxyRoute,
      String body, String contentType, String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return makeCall(to, response, timeout, viaNonProxyRoute,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * This method is the same as the basic blocking makeCall() method except that it allows the
   * caller to specify a message body and/or additional JAIN-SIP API message headers to add to or
   * replace in the outbound INVITE message. Use of this method requires knowledge of the JAIN-SIP
   * API.
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
  public SipCall makeCall(String to, int response, long timeout, String viaNonProxyRoute,
      ArrayList<Header> additionalHeaders, ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    SipCall call = this.createSipCall();

    if (call.initiateOutgoingCall(null, to, viaNonProxyRoute, additionalHeaders, replaceHeaders,
        body) == false) {
      setReturnCode(call.getReturnCode());
      setErrorMessage(call.getErrorMessage());
      setException(call.getException());
      return null;
    }

    if (call.waitOutgoingCallResponse(timeout) == false) {
      setReturnCode(call.getReturnCode());
      setErrorMessage(call.getErrorMessage());
      setException(call.getException());
      return null;
    }

    int status_code = call.getReturnCode();

    while (status_code != response) {
      if (status_code / 100 == 1) {
        if (call.waitOutgoingCallResponse(timeout) == false) {
          setReturnCode(call.getReturnCode());
          setErrorMessage(call.getErrorMessage());
          setException(call.getException());
          return null;
        }

        status_code = call.getReturnCode();
        continue;
      } else if ((status_code == Response.UNAUTHORIZED)
          || (status_code == Response.PROXY_AUTHENTICATION_REQUIRED)) {
        Request msg = call.getSentRequest();

        // modify the request to include user authorization info

        msg = processAuthChallenge((Response) call.getLastReceivedResponse().getMessage(), msg);
        if (msg == null) {
          return null;
        }

        if (call.reInitiateOutgoingCall(msg) == false) {
          setReturnCode(call.getReturnCode());
          setErrorMessage(call.getErrorMessage());
          setException(call.getException());
          return null;
        }

        if (call.waitOutgoingCallResponse(timeout) == false) {
          setReturnCode(call.getReturnCode());
          setErrorMessage(call.getErrorMessage());
          setException(call.getException());
          return null;
        }

        status_code = call.getReturnCode();
        continue;
      } else {
        setReturnCode(status_code);
        setErrorMessage(
            "Desired make-call response was not received, got this instead: " + status_code);
        return null;
      }
    }

    return call;
  }

  /**
   * This nonblocking basic method is used to make an outgoing call. It returns when the INVITE
   * request message has been sent out, and after that all received responses (TRYING, RINGING,
   * etc.) are automatically collected and any received authentication challenges are automatically
   * handled as well. The object returned by this method is a SipCall object representing the
   * outgoing call leg; that is, the UAC originating a call to the network.
   * 
   * <p>
   * After calling this method, you can later call one or more of the following methods on the
   * returned SipCall object to see what happened (each is nonblocking unless otherwise noted):
   * isCallAnswered() - to see if the call has been answered, callTimeoutOrError() - to see if an
   * error/timeout has occured, getReturnCode() - to see the last response code received so far,
   * getLastReceivedResponse() - to see the details of the last response received so far,
   * getAllReceivedResponses() - to see the details of all the responses received so far,
   * waitForAnswer() - BLOCKING - when your test program is done with its tasks and can be blocked
   * until OK is received - it returns immediately if OK already received,
   * waitOutgoingCallResponse() - BLOCKING - when your test program is done with its tasks and can
   * be blocked until the next response is received (if you are interested in something other than
   * OK) - use this only if you know that the INVITE transaction is still up.
   * 
   * <p>
   * Call this method when (1) you want to establish a call without worrying about the details and
   * (2) your test program needs to do other tasks after the INVITE is sent but before a
   * final/expected response is received (ie, the calling program cannot be blocked during call
   * establishment).
   * 
   * <p>
   * Otherwise: If you need to see or act on any of the (intermediate/provisional) response messages
   * as they come in, use SipPhone.createSipCall() and SipCall.initiateOutgoingCall() instead of
   * this method. If your test program doesn't need to do anything else until the call is
   * established: use the other SipPhone.makeCall() method which conveniently blocks until the
   * response code you specify is received from the network.
   * 
   * @param to The URI string (ex: sip:bob@nist.gov) to which the call should be directed
   * @param viaNonProxyRoute Indicates whether to route the INVITE via Proxy or some other route. If
   *        null, route the call to the Proxy that was specified when the SipPhone object was
   *        created (SipStack.createSipPhone()). Else route it to the given node, which is specified
   *        as "hostaddress:port/transport" i.e. 129.1.22.333:5060/UDP.
   * @return A SipCall object representing the outgoing call leg, or null if an error was
   *         encountered.
   * 
   */
  public SipCall makeCall(String to, String viaNonProxyRoute) {
    return makeCall(to, viaNonProxyRoute, null, null, null);
  }

  /**
   * This method is the same as the basic nonblocking makeCall() method except that it allows the
   * caller to specify a message body and/or additional JAIN-SIP API message headers to add to or
   * replace in the outbound INVITE message. Use of this method requires knowledge of the JAIN-SIP
   * API.
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
  public SipCall makeCall(String to, String viaNonProxyRoute, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    SipCall call = this.createSipCall();

    if (call.initiateOutgoingCall(null, to, viaNonProxyRoute, call, additionalHeaders,
        replaceHeaders, body) == false) {
      setReturnCode(call.getReturnCode());
      setErrorMessage(call.getErrorMessage());
      setException(call.getException());
      return null;
    }

    return call;
  }

  /**
   * This method is the same as the basic nonblocking makeCall() method except that it allows the
   * caller to specify a message body and/or additional message headers to add to or replace in the
   * outbound message without requiring knowledge of the JAIN-SIP API.
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
  public SipCall makeCall(String to, String viaNonProxyRoute, String body, String contentType,
      String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return makeCall(to, viaNonProxyRoute,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * This method releases all resources associated with this SipPhone. Neither this SipPhone object
   * nor its SipSession base class should be used again after calling the dispose() method.
   * Server/proxy unregistration occurs and SipCall(s) associated with this SipPhone are dropped. No
   * un-SUBSCRIBE is done for active Subscriptions in the buddy list.
   * 
   * @see org.cafesip.sipunit.SipCall#dispose()
   */
  public void dispose() {
    this.removeRequestListener(Request.NOTIFY, this);

    // drop calls
    while (!callList.isEmpty()) {
      ((SipCall) callList.get(0)).dispose();
    }

    unregister(contactInfo.getContactHeader().getAddress().getURI().clone().toString(), 15000);

    super.dispose();
  }

  protected AddressFactory getAddressFactory() {
    return parent.getAddressFactory();
  }

  protected HeaderFactory getHeaderFactory() {
    return parent.getHeaderFactory();
  }

  protected MessageFactory getMessageFactory() {
    return parent.getMessageFactory();
  }

  protected CallIdHeader getNewCallIdHeader()
  /*
   * The Call-ID of the request MUST be set to the Call-ID of the dialog. The Call-ID header field
   * uniquely identifies a particular invitation or all registrations of a particular client. A
   * single multimedia conference can give rise to several calls with different Call-IDs, for
   * example, if a user invites a single individual several times to the same (long-running)
   * conference.
   */

  {
    CallIdHeader id = parent.getSipProvider().getNewCallId();

    return id;
  }

  /**
   * The method getContactInfo() returns the contact information currently in effect for this user
   * agent. This may be the value associated with the last registration attempt or as defaulted to
   * user@host if no registration has occurred. Or, if the setPublicAddress() has been called on
   * this object, the returned value will reflect the most recent call to setPublicAddress().
   * 
   * @return The SipContact object currently in effect for this user agent
   */
  public SipContact getContactInfo() {
    return contactInfo;
  }

  /**
   * This method is the same as getContactInfo().
   * 
   * @deprecated Use getContactInfo() instead of this method, the term 'local' in the method name is
   *             misleading if the SipUnit test is running behind a NAT.
   * 
   * @return The SipContact object currently in effect for this user agent
   * 
   */
  public SipContact getLocalContactInfo() {
    return getContactInfo();
  }

  protected void updateContactInfo(ContactHeader hdr) {
    synchronized (contactLock) {
      contactInfo = new SipContact();
      contactInfo.setContactHeader(hdr);
    }
  }

  /**
   * Gets the user Address for this SipPhone. This is the same address used in the
   * "from" header field.
   * 
   * @return Returns the javax.sip.address.Address for this SipPhone (UA).
   */
  public Address getAddress() {
    return myAddress;
  }

  /**
   * Gets the request sent at the last successful registration.
   * 
   * @return Returns the lastRegistrationRequest.
   */
  protected Request getLastRegistrationRequest() {
    return lastRegistrationRequest;
  }

  /**
   * @return Returns the authorizations.
   */
  protected Map<String, LinkedHashMap<String, AuthorizationHeader>> getAuthorizations() {
    return authorizations;
  }

  protected void enableAuthorization(String call_id) {
    getAuthorizations().put(call_id, new LinkedHashMap<String, AuthorizationHeader>());
  }

  protected void clearAuthorizations(String call_id) {
    getAuthorizations().remove(call_id);
  }

  protected void addAuthorizations(String call_id, Request msg) {
    Map<String, AuthorizationHeader> auth_list = getAuthorizations().get(call_id);
    if (auth_list != null) {
      List<AuthorizationHeader> auth_headers =
          new ArrayList<>(auth_list.values());
      Iterator<AuthorizationHeader> i = auth_headers.iterator();
      while (i.hasNext()) {
        AuthorizationHeader auth = i.next();
        msg.addHeader(auth);
      }
    }
  }

  /**
   * This method adds a new credential to the credentials list or updates an existing credential in
   * the list.
   * 
   * @param c the credential to be added/updated.
   */
  public void addUpdateCredential(Credential c) {
    credentials.put(c.getRealm(), c);
  }

  /**
   * This method removes a credential from the credentials list.
   * 
   * @param c the credential to be removed.
   */
  public void removeCredential(Credential c) {
    credentials.remove(c.getRealm());
  }

  /**
   * This method removes a credential from the credentials list.
   * 
   * @param realm the realm associated with the credential to be removed.
   */
  public void removeCredential(String realm) {
    credentials.remove(realm);
  }

  /**
   * This method empties out the credentials list - completely - for this SipPhone.
   */
  public void clearCredentials() {
    credentials.clear();
  }

  /*
   * @see org.cafesip.sipunit.RequestListener#processEvent(java.util.EventObject)
   */
  public void processEvent(EventObject event) {
    if (event instanceof RequestEvent) {
      processRequestEvent((RequestEvent) event);
    } else {
      LOG.error("invalid event type received: " + event.getClass().getName() + ": "
          + event.toString());
    }
  }

  private void processRequestEvent(RequestEvent requestEvent) {
    Request request = requestEvent.getRequest();
    Dialog d = requestEvent.getDialog();

    if (request.getMethod().equals(Request.NOTIFY) == false) {
      EventSubscriber.sendResponse(this, requestEvent, SipResponse.SERVER_INTERNAL_ERROR,
          "Expected to receive a NOTIFY request, but instead got: " + request.getMethod());

      String err = "*** NOTIFY REQUEST ERROR ***  (SipPhone " + me
          + ") - SipPhone.processRequestEvent() - incoming request was misrouted, expected NOTIFY but got "
          + request.getMethod() + " : " + request;
      distributeEventError(err);
      LOG.error(err);
      return;
    }

    // find the EventSubscriber that this message is for - get the
    // subscription target uri from the message

    FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);

    EventHeader event = (EventHeader) request.getHeader(EventHeader.NAME);
    if (event == null) {
      EventSubscriber.sendResponse(this, requestEvent, SipResponse.BAD_REQUEST,
          "Received a NOTIFY request with no event header");

      String err = "*** NOTIFY REQUEST ERROR ***  (SipPhone " + me
          + ") - SipPhone.processRequestEvent() - Received a NOTIFY request with no event header : \n"
          + request;
      distributeEventError(err);
      LOG.error(err);
      return;
    }

    if (event.getEventType().equals("presence")) {
      PresenceSubscriber subs = getBuddyInfo(from.getAddress().getURI().toString());
      if (subs != null) {
        if (subs.messageForMe(request) == true) {
          subs.processEvent(requestEvent);
          return;
        }
      }
    } else if (event.getEventType().equals("refer")) {
      if (d != null) {
        List<ReferSubscriber> refers = getRefererInfoByDialog(d.getDialogId());
        for (ReferSubscriber s : refers) {
          if (s.messageForMe(request) == true) {
            s.processEvent(requestEvent);
            return;
          }
        }
      }
    } else if (event.getEventType().equals("conference")) {
        // just return so that the test can use waitRequest()
        return;
    } else {
      String error =
          "Received a NOTIFY request with unrecognized event header : " + event.getEventType();
      EventSubscriber.sendResponse(this, requestEvent, SipResponse.BAD_EVENT, error);

      String err = "*** NOTIFY REQUEST ERROR ***  (SipPhone " + me
          + ") - SipPhone.processRequestEvent() - " + error + " : \n" + request;
      distributeEventError(err);
      LOG.error(err);
      return;
    }

    // no Subscription match for this NOTIFY - 481 status
    String err = "Received orphan NOTIFY message (no matching subscription) from "
        + from.getAddress().getURI().toString();

    EventSubscriber.sendResponse(this, requestEvent, SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST,
        err);

    String error = "*** NOTIFY REQUEST ERROR ***  (" + from.getAddress().getURI().toString()
        + ") : " + err + " : " + request.toString();
    distributeEventError(error);
    LOG.error(error);

    return;
  }

  private void distributeEventError(String err)
  // to all the Subscriptions - test program will need to see the error
  {
    List<EventSubscriber> subscriptions =
        new ArrayList<EventSubscriber>(getBuddyList().values());
    subscriptions.addAll(new ArrayList<>(getRetiredBuddies().values()));
    subscriptions.addAll(getRefererList());

    for (EventSubscriber s : subscriptions) {
      s.addEventError(err);
    }
  }

  /**
   * This method adds a buddy to the buddy list and starts an ongoing subscription for purposes of
   * tracking the buddy's presence information. Please read the SipUnit User Guide webpage Event
   * Subscription (at least the operation overview part) for information on how to use SipUnit
   * presence capabilities.
   * 
   * <p>
   * This method creates a SUBSCRIBE request message, sends it out, and waits for a response to be
   * received. It saves the received response and checks for a "proceedable" (positive) status code
   * value. Positive response status codes include any of the following: provisional (status / 100
   * == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED. Any other status code, or
   * a response timeout or any other error, is considered fatal to the subscription.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * In the case of a positive response status code, this method returns a PresenceSubscriber object
   * that will represent the buddy for the life of the subscription and puts the PresenceSubscriber
   * object in this SipPhone's buddy list. You can save the returned PresenceSubscriber object
   * yourself or retrieve it anytime later by calling this SipPhone's getBuddyInfo(buddy-uri). Use
   * the returned PresenceSubscriber object to proceed through the remainder of this
   * SUBSCRIBE-NOTIFY sequence as well as for future SUBSCRIBE-NOTIFY sequences and to find out
   * details at any given time such as the subscription state, amount of time left on the
   * subscription, termination reason, presence information, details of received responses and
   * requests, etc.
   * 
   * <p>
   * In the case of a positive response status code (a non-null object is returned), you may find
   * out more about the response that was just received by calling the PresenceSubscriber methods
   * getReturnCode() and getCurrentResponse()/getLastReceivedResponse(). Your next step at this
   * point will be to call the PresenceSubscriber's processResponse() method to proceed with the
   * SUBSCRIBE processing.
   * 
   * <p>
   * In the case of a fatal outcome, no subscription object is created and null is returned. In this
   * case, call the usual SipUnit failed-operation methods to find out what happened (ie, call this
   * SipPhone's getErrorMessage(), getReturnCode(), and/or getException() methods). The
   * getReturnCode() method will tell you the response status code that was received from the
   * network (unless it is an internal SipUnit error code, see the SipSession javadoc for more on
   * that).
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the buddy to be added to the list.
   * @param duration the duration in seconds to put in the SUBSCRIBE message. If 0, this is
   *        equivalent to a fetch except that the buddy stays in the buddy list even though the
   *        subscription won't be active.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later on another buddy method call
   *        (refreshBuddy(), removeBuddy(), fetch, etc.).
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the buddy if the operation is successful so far,
   *         null otherwise.
   */
  public PresenceSubscriber addBuddy(String uri, int duration, String eventId, long timeout) {
    initErrorInfo();

    if (buddyList.get(uri) != null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("addBuddy() called but buddy is already in the list");
      return null;
    }

    try {
      PresenceSubscriber sub = new PresenceSubscriber(uri, this);
      Request req = sub.createSubscribeMessage(duration, eventId);

      if (req != null) {
        synchronized (buddyList) {
          buddyList.put(uri, sub);
        }

        if (sub.startSubscription(req, timeout, proxyHost != null) == true) {
          synchronized (buddyList) {
            buddyTerminatedList.remove(uri); // in case it was
            // there
            // from before
          }
          return sub;
        }
      }

      setReturnCode(sub.getReturnCode());
      setErrorMessage(sub.getErrorMessage());
      setException(sub.getException());
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    synchronized (buddyList) {
      buddyList.remove(uri);
    }
    return null;
  }

  /**
   * This method is the same as addBuddy(uri, duration, eventId, timeout) except that the duration
   * is defaulted to the default period defined in the event package RFC (3600 seconds) and no event
   * "id" parameter will be included.
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the buddy to be added to the list.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the buddy if the operation is successful so far,
   *         null otherwise.
   */
  public PresenceSubscriber addBuddy(String uri, long timeout) {
    return addBuddy(uri, DEFAULT_SUBSCRIBE_DURATION, null, timeout);
  }

  /**
   * This method is the same as addBuddy(uri, duration, eventId, timeout) except that no event "id"
   * parameter will be included.
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the buddy to be added to the list.
   * @param duration the duration in seconds to put in the SUBSCRIBE message. If 0, this is
   *        equivalent to a fetch except that the buddy stays in the buddy list even though the
   *        subscription won't be active.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the buddy if the operation is successful so far,
   *         null otherwise.
   */
  public PresenceSubscriber addBuddy(String uri, int duration, long timeout) {
    return addBuddy(uri, duration, null, timeout);
  }

  /**
   * This method is the same as addBuddy(uri, duration, eventId, timeout) except that the duration
   * is defaulted to the default period defined in the event package RFC (3600 seconds).
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the buddy to be added to the list.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. See addBuddy(uri, duration, eventId, timeout) javadoc for details on event
   *        "id" treatment.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the buddy if the operation is successful so far,
   *         null otherwise.
   */
  public PresenceSubscriber addBuddy(String uri, String eventId, long timeout) {
    return addBuddy(uri, DEFAULT_SUBSCRIBE_DURATION, eventId, timeout);
  }

  /**
   * This method performs a presence 'fetch' on the given user, to get a one-time report on the
   * user's presence status and information. Please read the SipUnit User Guide webpage Event
   * Subscription (at least the operation overview part) for information on how to use SipUnit
   * presence capabilities.
   * 
   * <p>
   * This method creates a SUBSCRIBE request message with expiry time of 0, sends it out, and waits
   * for a response to be received. It saves the received response and checks for a "proceedable"
   * (positive) status code value. Positive response status codes include any of the following:
   * provisional (status / 100 == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED.
   * Any other status code, or a response timeout or any other error, is considered fatal to the
   * operation.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * In the case of a positive response status code, this method returns a PresenceSubscriber object
   * representing the user and puts the object in this SipPhone's retired buddy list. The retired
   * buddy list consists of subscriptions resulting from a fetch or from removing a buddy from the
   * buddy list. You can save the returned PresenceSubscriber object yourself or retrieve it anytime
   * later by calling this SipPhone's getBuddyInfo(buddy-uri). You will use the returned
   * PresenceSubscriber object to proceed through the remainder of the SUBSCRIBE-NOTIFY sequence and
   * to find out details such as the subscription state, termination reason, presence information,
   * details of received responses and requests, etc.
   * 
   * <p>
   * In the case of a positive response status code (a non-null object is returned), you may find
   * out more about the response that was just received by calling the PresenceSubscriber methods
   * getReturnCode() and getCurrentResponse()/getLastReceivedResponse(). Your next step at this
   * point will be to call the PresenceSubscriber's processResponse() method to proceed with the
   * SUBSCRIBE processing.
   * 
   * <p>
   * In the case of a fatal outcome, no Subscription object is created and null is returned. In this
   * case, call the usual SipUnit failed-operation methods to find out what happened (ie, call this
   * SipPhone's getErrorMessage(), getReturnCode(), and/or getException() methods). The
   * getReturnCode() method will tell you the response status code that was received from the
   * network (unless it is an internal SipUnit error code, see the SipSession javadoc for more on
   * that).
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the user whose presence info is to be fetched.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently, for error checking the
   *        SUBSCRIBE response and NOTIFY from the server.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the user fetch if the operation is successful so
   *         far, null otherwise.
   */
  public PresenceSubscriber fetchPresenceInfo(String uri, String eventId, long timeout) {
    initErrorInfo();

    try {
      if (buddyList.get(uri) != null) {
        setReturnCode(SipSession.INVALID_ARGUMENT);
        setErrorMessage(
            "fetchPresenceInfo() called but the uri is in the buddy list. Use refreshBuddy() for buddies in the list.");
        return null;
      }

      PresenceSubscriber sub = new PresenceSubscriber(uri, this);
      synchronized (buddyList) {
        buddyTerminatedList.put(uri, sub);
      }

      Request req = sub.createSubscribeMessage(0, eventId);

      if (req == null) {
        setReturnCode(sub.getReturnCode());
        setErrorMessage(sub.getErrorMessage());
        setException(sub.getException());

        synchronized (buddyList) {
          buddyTerminatedList.remove(uri);
        }
        return null;
      }

      if (sub.fetchSubscription(req, timeout, proxyHost != null) == true) {
        return sub;
      }

      setReturnCode(sub.getReturnCode());
      setErrorMessage(sub.getErrorMessage());
      setException(sub.getException());
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    synchronized (buddyList) {
      buddyTerminatedList.remove(uri);
    }
    return null;
  }

  /**
   * This method is the same as fetchPresenceInfo(uri, eventId, timeout) except that no event "id"
   * parameter will be included in the SUBSCRIBE message. When error checking the SUBSCRIBE response
   * and NOTIFY from the server, no event "id" parameter will be expected.
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the user whose presence info is to be fetched.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return PresenceSubscriber object representing the user fetch if the operation is successful so
   *         far, null otherwise.
   */
  public PresenceSubscriber fetchPresenceInfo(String uri, long timeout) {
    return fetchPresenceInfo(uri, null, timeout);
  }

  /**
   * Gets the PresenceSubscriber object representing a buddy or user whose presence
   * information was fetched at some previous point. The returned object contains the most recently
   * obtained presence information for the given user. Status, presence device info, received
   * requests (NOTIFY's) and SUBSCRIBE responses, etc. for this user's subscription can be obtained
   * from the returned object. The user may have been a buddy in the buddy list (but was removed
   * from the list by the test program), or fetchPresenceInfo() was previously called for the user
   * to get a one-time status report, or the user may still be in the buddy list.
   * 
   * @param uri the URI (ie, sip:bob@nist.gov) of the user whose subscription object is to be
   *        returned.
   * @return A PresenceSubscriber object that contains information about the user's last obtained
   *         presence status and other info, or null if there was never any status fetch done for
   *         this user and this user was never in the buddy list.
   * 
   */
  public PresenceSubscriber getBuddyInfo(String uri) {
    synchronized (buddyList) {
      PresenceSubscriber s = buddyList.get(uri);

      if (s == null) {
        s = buddyTerminatedList.get(uri);
      }

      return s;
    }
  }

  /**
   * Returns a copy of the current buddy list on this SipPhone. These are the buddies that have been
   * added to the buddy list by the test program during the lifetime of this SipPhone object, that
   * are still in the buddy list. A given buddy, or subscription, in this list may be active or not
   * - ie, subscription termination by the far end does not remove a buddy from this list. Buddies
   * are removed from the list only by the test program (by calling removeBuddy()).
   * 
   * <p>
   * See related methods getBuddyInfo(), getRetiredBuddies().
   * 
   * @return a Hashtable of zero or more entries, where the key = URI of the buddy, value =
   *         PresenceSubscriber object.
   */
  public Map<String, PresenceSubscriber> getBuddyList() {
    return new Hashtable<>(buddyList);
  }

  /**
   * Returns a copy of the list of subscriptions associated with this SipPhone which are not in the
   * current buddy list. Subscriptions get in this list either because of a 'fetch' or whenever the
   * test program removes a buddy from the buddy list. The main purpose of this list is so the last
   * known presence status of a user can be obtained anytime. This is required to make the fetch
   * case useful.
   * 
   * <p>
   * See related methods getBuddyInfo(), getBuddyList().
   * 
   * @return a Hashtable of zero or more entries, where the key = URI of the user, value =
   *         PresenceSubscriber object.
   */
  public Map<String, PresenceSubscriber> getRetiredBuddies() {
    return new Hashtable<>(buddyTerminatedList);
  }

  protected PresenceSubscriber retireBuddy(String uri) {
    PresenceSubscriber sub;

    synchronized (buddyList) {
      sub = buddyList.remove(uri);
    }

    if (sub != null) {
      synchronized (buddyList) {
        buddyTerminatedList.put(uri, sub);
      }
    }

    return sub;
  }

  protected boolean removeRefer(ReferSubscriber ref) {
    synchronized (refererList) {
      return refererList.remove(ref);
    }
  }

  /**
   * Gets the subscription object(s) associated with the given referToUri. The returned object(s)
   * contains subscription state, received requests (NOTIFY's) and REFER/SUBSCRIBE responses, etc.
   * for outbound REFER subscription(s) associated with the referToUri.
   * 
   * @param referToUri the referToUri that was previously passed in to SipPhone.refer(). See javadoc
   *        there.
   * @return ReferSubscriber object(s) associated with the referToUri, or an empty list if there was
   *         never a call to SipPhone.refer() with that referToUri.
   * 
   */
  public List<ReferSubscriber> getRefererInfo(SipURI referToUri) {
    List<ReferSubscriber> list = new ArrayList<>();

    synchronized (refererList) {
      for (ReferSubscriber s : refererList) {
        if (s.getReferToUri().equals(referToUri)) {
          list.add(s);
        }
      }
    }

    return list;
  }

  /**
   * Gets the subscription object(s) associated with the given dialog ID. The
   * returned object(s) contains subscription state, received requests (NOTIFY's) and
   * REFER/SUBSCRIBE responses, etc. for outbound REFER subscription(s) associated with the dialog.
   * 
   * @param dialogId the dialog ID of interest
   * @return ReferSubscriber object(s) associated with the dialog, or an empty list if there was
   *         never a refer subscription associated with that dialog.
   * 
   */
  public List<ReferSubscriber> getRefererInfoByDialog(String dialogId) {
    List<ReferSubscriber> list = new ArrayList<>();

    synchronized (refererList) {
      for (ReferSubscriber s : refererList) {
        if (s.getDialogId().equals(dialogId)) {
          list.add(s);
        }
      }
    }

    return list;
  }

  /**
   * Returns a copy of the list of subscriptions associated with outbound REFER requests from this
   * SipPhone. These are the subscriptions resulting from calling SipPhone.refer() during the
   * lifetime of this SipPhone object. A given subscription in the list may be active or not -
   * subscription termination does not automatically remove a subscription from this list (calling
   * ReferSubscriber.dispose() does that).
   * 
   * <p>
   * See related method getRefererInfo().
   * 
   * @return a list of ReferSubscriber objects or an empty list if there are none.
   */
  public List<ReferSubscriber> getRefererList() {
    return new ArrayList<>(refererList);
  }

  /**
   * Creates an URI object useful for passing into methods such as SipPhone.refer().
   * 
   * @param scheme "sip:" or "sips:" or null if the scheme is already included in the userHostPort
   *        parameter.
   * @param userHostPort Addressing information in the form of: user@host:port. Port is not
   *        required, and the scheme may optionally be included here instead of in the "scheme"
   *        parameter. Examples: sipmaster@192.168.1.11:5060 and sip:alice@atlanta.sip.com.
   * @param transportUriParameter Specifies which transport protocol to use for sending requests and
   *        responses to this URI, or null if not needed.
   * @param methodUriParameter Specifies which SIP method to use in requests directed at this URI
   *        (ie, INVITE), or null if not applicable.
   * @param otherUriParameters Remaining URI parameters affecting a request constructed from this
   *        URI (Lr, MAddr, TTL, etc), or null if not applicable. URI parameters are key-value
   *        pairs. Any given parameter name (key) MUST NOT appear more than once.
   * @param joinUriHeader This value is the dialog ID for a Join header to be included in a request
   *        constructed from this URI, or null if not applicable.
   * @param replacesUriHeader This value is the dialog ID for a Replaces header to be included in a
   *        request constructed from this URI, or null if not applicable.
   * @param bodyUriHeader This value is the message body of a SIP request constructed from this URI,
   *        or null if not applicable.
   * @param otherUriHeaders Remaining header fields to be included in a request constructed from
   *        this URI, or null if not applicable. Header fields are key-value pairs.
   * @return A SipURI for passing into methods such as SipPhone.refer().
   * @throws ParseException On parse error due to invalid scheme or userHostPort parameters.
   */
  public SipURI getUri(String scheme, String userHostPort, String transportUriParameter,
      String methodUriParameter, Map<String, String> otherUriParameters, String joinUriHeader,
      String replacesUriHeader, String bodyUriHeader, Map<String, String> otherUriHeaders)
          throws ParseException {
    String address = scheme == null ? userHostPort : scheme + userHostPort;

    Address addr = getAddressFactory().createAddress(address);
    URI addrUri = addr.getURI();
    if (!addrUri.isSipURI()) {
      throw new ParseException("Non-SIP URI not supported: " + address, 0);
    }
    SipURI uri = (SipURI) addrUri;

    // handle URI parameters
    if (otherUriParameters == null) {
      otherUriParameters = new HashMap<>();
    }
    if (transportUriParameter != null) {
      otherUriParameters.put("transport", transportUriParameter);
    }
    if (methodUriParameter != null) {
      otherUriParameters.put("method", methodUriParameter);
    }
    for (Map.Entry<String, String> entry : otherUriParameters.entrySet()) {
      uri.setParameter(entry.getKey(), entry.getValue());
    }

    // handle URI headers
    if (otherUriHeaders == null) {
      otherUriHeaders = new HashMap<>();
    }
    if (joinUriHeader != null) {
      otherUriHeaders.put("Join", joinUriHeader);
    }
    if (replacesUriHeader != null) {
      otherUriHeaders.put("Replaces", replacesUriHeader);
    }
    if (bodyUriHeader != null) {
      otherUriHeaders.put("body", bodyUriHeader);
    }
    for (Map.Entry<String, String> entry : otherUriHeaders.entrySet()) {
      uri.setHeader(entry.getKey(), entry.getValue());
    }

    return uri;
  }

  /**
   * This basic refer method creates an out-of-dialog REFER request message, sends it out, and waits
   * for a first response to be received. It saves the received response and checks for a
   * "proceedable" (positive) status code value. Positive response status codes include any of the
   * following: provisional (status / 100 == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and
   * ACCEPTED. Any other status code, or a response timeout or any other error, is considered fatal
   * to the subscription.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * In the case of a positive response status code, this method returns a ReferSubscriber object
   * that represents the implicit subscription. You can save the returned object yourself or
   * retrieve it anytime later by calling one of this SipPhone's getReferrerInfo() methods or
   * getRefererList(). Use the returned subscription object to proceed through the remainder of this
   * REFER-NOTIFY sequence as well as for future SUBSCRIBE-NOTIFY sequences on this subscription and
   * also to find out details at any given time such as the subscription state, amount of time left
   * on the subscription, termination reason, details of received responses and requests, etc.
   * 
   * <p>
   * In the case of a positive response status code (a non-null object is returned), you may find
   * out more about the response that was just received by calling the ReferSubscriber methods
   * getReturnCode() and getCurrentResponse(). Your next step will then be to call the
   * ReferSubscriber's processResponse() method to proceed with the REFER processing.
   * 
   * <p>
   * In the case of a fatal outcome, no subscription object is created and null is returned. In this
   * case, call the usual SipUnit failed-operation methods to find out what happened (ie, call this
   * SipPhone's getErrorMessage(), getReturnCode(), and/or getException() methods). The
   * getReturnCode() method will tell you the response status code that was received from the
   * network (unless it is an internal SipUnit error code, see the SipSession javadoc for more on
   * that).
   * 
   * @param refereeUri The URI (ie, sip:bob@nist.gov) of the far end of the subscription. This is
   *        the party the REFER request is sent to.
   * @param referToUri The URI that the refereeUri is to refer to. You can use SipPhone.getUri() to
   *        create this object for you to pass in, or you can create your own using the JAIN SIP
   *        API.
   * @param eventId The event "id" to use in the REFER message, or null for no event "id" parameter.
   *        If an eventId is specified here, it will be used subsequently for error checking the
   *        REFER responses and the NOTIFYs from the far end as well as for sending subsequent
   *        SUBSCRIBEs unless changed by the caller later when invoking ReferSubscriber methods
   *        refresh() or unsubscribe().
   * @param timeout The maximum amount of time to wait for a response, in milliseconds. Use a value
   *        of 0 to wait indefinitely.
   * @param viaNonProxyRoute Affects the routing of the REFER message. By default, the message is
   *        routed to the Proxy that was specified when this SipPhone object was created
   *        (SipStack.createSipPhone()) or if no proxy was specified then, the REFER is looped back
   *        to this local sip stack. If the viaNonProxyRoute parameter is not null, it overrides the
   *        default behavior and causes the message to be routed differently, to the
   *        viaNonProxyRoute node which is specified as "hostaddress:port/transport" i.e.
   *        129.1.22.333:5060/UDP. A route header will be added to the REFER for this, before the
   *        request is sent.
   * 
   * @return ReferSubscriber object representing the implicit subscription if the operation is
   *         successful so far, null otherwise.
   */
  public ReferSubscriber refer(String refereeUri, SipURI referToUri, String eventId, long timeout,
      String viaNonProxyRoute) {
    return refer(refereeUri, referToUri, eventId, timeout, viaNonProxyRoute, null, null, null);
  }

  /**
   * This method is the same as the basic out-of-dialog refer() method except that it allows the
   * caller to specify a message body and/or additional JAIN-SIP API message headers to add to or
   * replace in the outbound message. Use of this method requires knowledge of the JAIN-SIP API.
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
  public ReferSubscriber refer(String refereeUri, SipURI referToUri, String eventId, long timeout,
      String viaNonProxyRoute, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    if (referToUri == null) {
      setReturnCode(INVALID_ARGUMENT);
      setErrorMessage("Null referToUri");
      return null;
    }

    ReferSubscriber sub = null;
    try {
      sub = new ReferSubscriber(refereeUri, referToUri, this);
      Request req = sub.createReferMessage(referToUri, eventId, viaNonProxyRoute);

      if (req != null) {
        synchronized (refererList) {
          refererList.add(sub);
        }

        boolean viaProxy = (proxyHost != null) && (viaNonProxyRoute == null);

        if (sub.startSubscription(req, timeout, viaProxy, additionalHeaders, replaceHeaders,
            body) == true) {
          return sub;
        }
      }

      setReturnCode(sub.getReturnCode());
      setErrorMessage(sub.getErrorMessage());
      setException(sub.getException());
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    if (sub != null) {
      synchronized (refererList) {
        refererList.remove(sub);
      }
    }

    return null;
  }

  /**
   * This method is the same as the basic out-of-dialog refer() method except that it allows the
   * caller to specify a message body and/or additional message headers to add to or replace in the
   * outbound message without requiring knowledge of the JAIN-SIP API.
   * 
   * <p>
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'message' part of 'message/sipfrag'), required if
   *        there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param contentSubType The body content sub-type (ie, 'sipfrag' part of 'message/sipfrag'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
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
  public ReferSubscriber refer(String refereeUri, SipURI referToUri, String eventId, long timeout,
      String viaNonProxyRoute, String body, String contentType, String contentSubType,
      ArrayList<String> additionalHeaders, ArrayList<String> replaceHeaders) {
    try {
      return refer(refereeUri, referToUri, eventId, timeout, viaNonProxyRoute,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  /**
   * This basic refer method creates an in-dialog REFER request message, sends it out, and waits for
   * a first response to be received. It saves the received response and checks for a "proceedable"
   * (positive) status code value. Positive response status codes include any of the following:
   * provisional (status / 100 == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED.
   * Any other status code, or a response timeout or any other error, is considered fatal to the
   * subscription.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * In the case of a positive response status code, this method returns a ReferSubscriber object
   * that represents the implicit subscription. You can save the returned object yourself or
   * retrieve it anytime later by calling one of this SipPhone's getReferrerInfo() methods or
   * getRefererList(). Use the returned subscription object to proceed through the remainder of this
   * REFER-NOTIFY sequence as well as for future SUBSCRIBE-NOTIFY sequences on this subscription and
   * also to find out details at any given time such as the subscription state, amount of time left
   * on the subscription, termination reason, details of received responses and requests, etc.
   * 
   * <p>
   * In the case of a positive response status code (a non-null object is returned), you may find
   * out more about the response that was just received by calling the ReferSubscriber methods
   * getReturnCode() and getCurrentResponse(). Your next step will then be to call the
   * ReferSubscriber's processResponse() method to proceed with the REFER processing.
   * 
   * <p>
   * In the case of a fatal outcome, no subscription object is created and null is returned. In this
   * case, call the usual SipUnit failed-operation methods to find out what happened (ie, call this
   * SipPhone's getErrorMessage(), getReturnCode(), and/or getException() methods). The
   * getReturnCode() method will tell you the response status code that was received from the
   * network (unless it is an internal SipUnit error code, see the SipSession javadoc for more on
   * that).
   * 
   * @param dialog The existing dialog that this REFER should be associated with. You can get it
   *        from SipCall.getDialog() or ReferSubscriber.getDialog().
   * @param referToUri The URI that the far end of the dialog is to refer to. You can use
   *        SipPhone.getUri() to create this object for you to pass in, or you can create your own
   *        using the JAIN SIP API.
   * @param eventId The event "id" to use in the REFER message, or null for no event "id" parameter.
   *        If an eventId is specified here, it will be used subsequently for error checking the
   *        REFER responses and the NOTIFYs from the far end as well as for sending subsequent
   *        SUBSCRIBEs unless changed by the caller later when invoking ReferSubscriber methods
   *        refresh() or unsubscribe().
   * @param timeout The maximum amount of time to wait for a response, in milliseconds. Use a value
   *        of 0 to wait indefinitely.
   * 
   * @return ReferSubscriber object representing the implicit subscription if the operation is
   *         successful so far, null otherwise.
   */
  public ReferSubscriber refer(Dialog dialog, SipURI referToUri, String eventId, long timeout) {
    return refer(dialog, referToUri, eventId, timeout, null, null, null);
  }

  /**
   * This method is the same as the basic in-dialog refer() method except that it allows the caller
   * to specify a message body and/or additional JAIN-SIP API message headers to add to or replace
   * in the outbound message. Use of this method requires knowledge of the JAIN-SIP API.
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
  public ReferSubscriber refer(Dialog dialog, SipURI referToUri, String eventId, long timeout,
      ArrayList<Header> additionalHeaders, ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();

    if ((referToUri == null) || (dialog == null)) {
      setReturnCode(INVALID_ARGUMENT);
      setErrorMessage("Null referToUri or dialog");
      return null;
    }

    ReferSubscriber sub = null;
    try {
      sub = new ReferSubscriber(dialog.getRemoteParty().getURI().toString(), referToUri, this,
          dialog);
      Request req = sub.createReferMessage(referToUri, eventId, null);

      if (req != null) {
        synchronized (refererList) {
          refererList.add(sub);
        }

        if (sub.startSubscription(req, timeout, false, additionalHeaders, replaceHeaders,
            body) == true) {
          return sub;
        }
      }

      setReturnCode(sub.getReturnCode());
      setErrorMessage(sub.getErrorMessage());
      setException(sub.getException());
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    if (sub != null) {
      synchronized (refererList) {
        refererList.remove(sub);
      }
    }

    return null;
  }

  /**
   * This method is the same as the basic in-dialog refer() method except that it allows the caller
   * to specify a message body and/or additional message headers to add to or replace in the
   * outbound message without requiring knowledge of the JAIN-SIP API.
   * 
   * <p>
   * The extra parameters supported by this method are:
   * 
   * @param body A String to be used as the body of the message. Parameters contentType,
   *        contentSubType must both be non-null to get the body included in the message. Use null
   *        for no body bytes.
   * @param contentType The body content type (ie, 'message' part of 'message/sipfrag'), required if
   *        there is to be any content (even if body bytes length 0). Use null for no message
   *        content.
   * @param contentSubType The body content sub-type (ie, 'sipfrag' part of 'message/sipfrag'),
   *        required if there is to be any content (even if body bytes length 0). Use null for no
   *        message content.
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
  public ReferSubscriber refer(Dialog dialog, SipURI referToUri, String eventId, long timeout,
      String body, String contentType, String contentSubType, ArrayList<String> additionalHeaders,
      ArrayList<String> replaceHeaders) {
    try {
      return refer(dialog, referToUri, eventId, timeout,
          toHeader(additionalHeaders, contentType, contentSubType), toHeader(replaceHeaders), body);
    } catch (Exception ex) {
      setException(ex);
      setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return null;
    }
  }

  protected String getProxyHost() {
    return proxyHost;
  }

  protected ContactHeader updateContactInfo(String contact, String displayName) throws Exception {
    if ((contact != null) && (contact.trim().length() > 0)) {
      URI uri = getAddressFactory().createURI(contact.trim());
      if (uri.isSipURI() == false) {
        throw new Exception("Contact URI " + contact + " is not a Sip URI");
      }

      Address contact_address = getAddressFactory().createAddress(uri);
      if (displayName != null) {
        contact_address.setDisplayName(displayName);
      }

      ContactHeader hdr = getHeaderFactory().createContactHeader(contact_address);
      updateContactInfo((ContactHeader) hdr.clone());

      return hdr;
    }

    throw new Exception("Update contact info was null or blank");
  }

}
