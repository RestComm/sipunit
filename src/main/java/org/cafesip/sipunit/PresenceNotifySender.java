/*
 * Created on November 22, 2005
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
import java.util.Calendar;
import java.util.Collections;
import java.util.EventObject;
import java.util.Iterator;
import java.util.List;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.address.Address;
import javax.sip.header.AcceptHeader;
import javax.sip.header.AllowEventsHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * The primary purpose of this class is as a test utility, to verify other UA's NOTIFY reception
 * processing.
 * 
 * <p>
 * When instantiated, an object of this class listens for a SUBSCRIBE message. After the calling
 * program has sent a SUBSCRIBE message to this object's uri, it can call this object's
 * processSubscribe() to receive and process the SUBSCRIBE and send a response. After that, this
 * object sends a NOTIFY message on that subscription each time method sendNotify() is called, with
 * the given content body. This class can listen for and show the response to a given sent NOTIFY
 * message. It can receive and process multiple SUBSCRIBE messages for a subscription.
 * 
 * @author Becky McElroy
 * 
 */
public class PresenceNotifySender implements MessageListener {

  private static final Logger LOG = LoggerFactory.getLogger(PresenceNotifySender.class);

  protected SipPhone phone;

  protected Dialog dialog;

  protected EventHeader eventHeader;

  protected Object dialogLock = new Object();

  protected String toTag;

  protected Request lastSentNotify;

  protected String errorMessage = "";

  protected List<SipRequest> receivedRequests;

  protected List<SipResponse> receivedResponses;

  /**
   * A constructor for this class. This object immediately starts listening for a SUBSCRIBE request.
   * 
   * @param phone SipPhone object to use for messaging.
   */
  public PresenceNotifySender(SipPhone phone) {
    this.phone = phone;
    phone.setLoopback(true);
    phone.listenRequestMessage();
    receivedRequests = Collections.synchronizedList(new ArrayList<SipRequest>());
    receivedResponses = Collections.synchronizedList(new ArrayList<SipResponse>());
  }

  /**
   * Dispose of this object (but not the stack given to it).
   * 
   */
  public void dispose() {
    phone.dispose();
  }

  /**
   * This method waits for up to 10 seconds to receive a SUBSCRIBE and if received, it sends an OK
   * response.
   * 
   */
  public void processSubscribe() {
    processSubscribe(10000, SipResponse.OK, null);
  }

  /**
   * This method starts a thread that waits for up to 'timeout' milliseconds to receive a SUBSCRIBE
   * and if received, it sends a response with 'statusCode' and 'reasonPhrase' (if not null). This
   * method waits 500 ms before returning to allow the thread to get started and begin waiting for
   * an incoming SUBSCRIBE. This method adds 500ms to the given timeout to account for this delay.
   * 
   * @param timeout - number of milliseconds to wait for the SUBSCRIBE
   * @param statusCode - use in the response to the SUBSCRIBE
   * @param reasonPhrase - if not null, use in the SUBSCRIBE response
   */
  public void processSubscribe(long timeout, int statusCode, String reasonPhrase) {
    processSubscribe(timeout, statusCode, reasonPhrase, -1, null);
  }

  /**
   * Same as the other processSubscribe() except allows for a couple of overrides for testing error
   * handling by the far end outbound SUBSCRIBE side: (a) this method takes a duration for
   * overriding what would normally/correctly be sent back in the response (which is the same as
   * what was received in the SUBSCRIBE request or default 3600 if none was received). Observed if
   * &gt;= 0. (b) this method takes an EventHeader for overriding what would normally/correctly be
   * sent back in the respone (same as what was received in the request).
   */
  public void processSubscribe(long timeout, int statusCode, String reasonPhrase,
      int overrideDuration, EventHeader overrideEvent) {
    setErrorMessage("");

    PhoneB b = new PhoneB(timeout + 500, statusCode, reasonPhrase, overrideDuration, overrideEvent);
    b.start();
  }

  class PhoneB extends Thread {
    long timeout;

    int statusCode;

    String reasonPhrase;

    int overrideDuration;

    EventHeader overrideEvent;

    public PhoneB(long timeout, int statusCode, String reasonPhrase, int overrideDuration,
        EventHeader overrideEvent) {
      this.timeout = timeout;
      this.statusCode = statusCode;
      this.reasonPhrase = reasonPhrase;
      this.overrideDuration = overrideDuration;
      this.overrideEvent = overrideEvent;
    }

    public void run() {
      try {
        phone.unlistenRequestMessage(); // clear out request queue
        phone.listenRequestMessage();

        RequestEvent inc_req = phone.waitRequest(timeout);
        while (inc_req != null) {
          receivedRequests.add(new SipRequest(inc_req));
          Request req = inc_req.getRequest();
          if (req.getMethod().equals(Request.SUBSCRIBE) == false) {
            inc_req = phone.waitRequest(timeout);
            continue;
          }

          try {
            synchronized (dialogLock) {
              ServerTransaction trans = inc_req.getServerTransaction();
              if (trans == null) {
                trans = phone.getParent().getSipProvider().getNewServerTransaction(req);
              }

              if (toTag == null) {
                toTag = new Long(Calendar.getInstance().getTimeInMillis()).toString();
              }

              int duration = 3600;
              ExpiresHeader exp = (ExpiresHeader) req.getHeader(ExpiresHeader.NAME);
              if (exp != null) {
                duration = exp.getExpires();
              }
              if (overrideDuration > -1) {
                duration = overrideDuration;
              }

              // enable auth challenge handling
              phone.enableAuthorization(((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId());

              // save event header
              if (overrideEvent != null) {
                eventHeader = overrideEvent;
              } else {
                eventHeader = (EventHeader) req.getHeader(EventHeader.NAME).clone();
              }

              dialog = sendResponse(trans, statusCode, reasonPhrase, toTag, req, duration);

              if (dialog == null) {
                phone.clearAuthorizations(
                    ((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId());
                return;
              }
            }

            LOG.trace("Sent response to SUBSCRIBE");
            return;
          } catch (Throwable e) {
            setErrorMessage("Throwable: " + e.getClass().getName() + ": " + e.getMessage());
            return;
          }
        }

        setErrorMessage(phone.getErrorMessage());
        return;
      } catch (Exception e) {
        setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
      } catch (Throwable t) {
        setErrorMessage("Throwable: " + t.getClass().getName() + ": " + t.getMessage());
        return;
      }
    }
  }

  protected Dialog sendResponse(ServerTransaction transaction, int statusCode, String reasonPhrase,
      String toTag, Request request, int duration) {
    try {
      Response response = phone.getParent().getMessageFactory().createResponse(statusCode, request);

      if (duration != -1) {
        response.setHeader(phone.getParent().getHeaderFactory().createExpiresHeader(duration));
      }

      if (reasonPhrase != null) {
        response.setReasonPhrase(reasonPhrase);
      }

      if (eventHeader != null) {
        response.setHeader((Header) eventHeader.clone());
      }

      ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
      response.addHeader((ContactHeader) phone.getContactInfo().getContactHeader().clone());

      if (statusCode / 100 == 2) { // 2xx
        AcceptHeader accept = getAcceptHeaderForResponse();
        if (accept != null)
          response.setHeader(accept);

        SupportedHeader shdr = getSupportedHeaderForResponse();
        if (shdr != null)
          response.setHeader(shdr);

        AllowEventsHeader ahdr = getAllowEventsHeaderForResponse();
        if (ahdr != null)
          response.setHeader(ahdr);
      }

      transaction.sendResponse(response);

      return transaction.getDialog();
    } catch (Exception e) {
      setErrorMessage("Error responding to request from "
          + ((FromHeader) request.getHeader(FromHeader.NAME)).getName() + ": "
          + e.getClass().getName() + ": " + e.getMessage());

      return null;
    }

  }

  /**
   * @return AllowEventsHeader
   * @throws ParseException
   */
  protected AllowEventsHeader getAllowEventsHeaderForResponse() throws ParseException {
    AllowEventsHeader ahdr = phone.getParent().getHeaderFactory().createAllowEventsHeader("presence");
    return ahdr;
  }

  /**
   * @return SupportedHeader
   * @throws ParseException
   */
  protected SupportedHeader getSupportedHeaderForResponse() throws ParseException {
    SupportedHeader shdr = phone.getParent().getHeaderFactory().createSupportedHeader("presence");
    return shdr;
  }

  /**
   * @return AcceptHeader
   * @throws ParseException
   */
  protected AcceptHeader getAcceptHeaderForResponse() throws ParseException {
    AcceptHeader accept =
        phone.getParent().getHeaderFactory().createAcceptHeader("application", "pidf+xml");
    return accept;
  }

  /**
   * This method creates a NOTIFY message using the given parameters and sends it to the subscriber.
   * The request will be resent if challenged. Use this method only if you have previously called
   * processSubscribe(). Use this method if you don't care about checking the response to the sent
   * NOTIFY, otherwise use sendStatefulNotify().
   * 
   * @param subscriptionState - String to use as the subscription state.
   * @param termReason - used only when subscriptionState = TERMINATED.
   * @param body - NOTIFY body to put in the message
   * @param timeLeft - expiry in seconds to put in the NOTIFY message (used only when
   *        subscriptionState = ACTIVE or PENDING).
   * @param viaProxy If true, send the message to the proxy. In this case a Route header will be
   *        added. Else send the message as is.
   * @return true if successful, false otherwise (call getErrorMessage() for details).
   */
  public boolean sendNotify(String subscriptionState, String termReason, String body, int timeLeft,
      boolean viaProxy) {
    return sendNotify(subscriptionState, termReason, body, timeLeft, null, null, null, null,
        viaProxy);
  }

  /**
   * This method creates a NOTIFY message using the given parameters and sends it to the subscriber.
   * Knowledge of JAIN-SIP API headers is required. The request will be resent if challenged. Use
   * this method only if you have previously called processSubscribe(). Use this method if you don't
   * care about checking the response to the sent NOTIFY, otherwise use sendStatefulNotify().
   * 
   * @param subscriptionState - String to use as the subscription state. Overridden by sshdr.
   * @param termReason - used only when subscriptionState = TERMINATED. Overridden by sshdr.
   * @param body - NOTIFY body to put in the message
   * @param timeLeft - expiry in seconds to put in the NOTIFY subscription state header (only when
   *        subscriptionState = ACTIVE or PENDING), unless the timeLeft value is -1 in which case
   *        don't include expires information in the subscription state header. Overridden by sshdr.
   * @param eventHdr - if not null, use this event header in the NOTIFY message
   * @param ssHdr - if not null, use this subscription state header in the NOTIFY message instead of
   *        building one from other parameters given.
   * @param accHdr - if not null, use this accept header. Otherwise build the default package one
   *        (pidf+xml).
   * @param ctHdr - if not null, use this content type header. Otherwise build the default package
   *        one (pidf+xml).
   * @param viaProxy If true, send the message to the proxy. In this case a Route header will be
   *        added. Else send the message as is.
   * 
   * @return true if successful, false otherwise (call getErrorMessage() for details).
   */
  public boolean sendNotify(String subscriptionState, String termReason, String body, int timeLeft,
      EventHeader eventHdr, SubscriptionStateHeader ssHdr, AcceptHeader accHdr,
      ContentTypeHeader ctHdr, boolean viaProxy) {
    setErrorMessage("");

    synchronized (dialogLock) {
      if (dialog == null) {
        setErrorMessage("Can't send notify, haven't received a request");
        return false;
      }

      try {
        Request req = dialog.createRequest(Request.NOTIFY);

        EventHeader ehdr = eventHdr;
        if (ehdr == null) {
          if (eventHeader != null) {
            ehdr = phone.getParent().getHeaderFactory().createEventHeader(eventHeader.getEventType());
            if (eventHeader.getEventId() != null) {
              ehdr.setEventId(eventHeader.getEventId());
            }
          } else {
            ehdr = phone.getParent().getHeaderFactory().createEventHeader(getEventType());
          }
        }
        req.setHeader(ehdr);

        SubscriptionStateHeader hdr = ssHdr;
        if (hdr == null) {
          hdr = phone.getParent().getHeaderFactory().createSubscriptionStateHeader(subscriptionState);

          if (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
            hdr.setReasonCode(termReason);
          } else if (timeLeft != -1) {
            hdr.setExpires(timeLeft);
          }
        }
        req.setHeader(hdr);

        AcceptHeader accept = accHdr;
        if (accept == null) {
          accept = phone.getParent().getHeaderFactory().createAcceptHeader(getPackageContentType(),
              getPackageContentSubType());
        }
        req.setHeader(accept);

        // now for the body
        ContentTypeHeader ct_hdr = ctHdr;
        if (ct_hdr == null) {
          ct_hdr = phone.getParent().getHeaderFactory()
              .createContentTypeHeader(getPackageContentType(), getPackageContentSubType());
        }

        req.setContent(body, ct_hdr);

        req.setContentLength(
            phone.getParent().getHeaderFactory().createContentLengthHeader(body.length()));

        return sendNotify(req, viaProxy);
      } catch (Exception e) {
        setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
      }

    }
    return false;

  }

  /**
   * @return content subtype
   */
  protected String getPackageContentSubType() {
    return "pidf+xml";
  }

  /**
   * @return content type
   */
  protected String getPackageContentType() {
    return "application";
  }

  /**
   * @return event type
   */
  protected String getEventType() {
    return "presence";
  }

  /**
   * This method adds each of the given parameters, if not null, to the given NOTIFY Request
   * parameter which just contains the request line (created from a string). It is for the purpose
   * of building a NOTIFY message to send out. If a dialog is associated with this object (ie, a
   * request has been previously received), this method takes the information from there to create
   * headers for the null parameters passed in, else this method makes up the header content.
   * 
   * @param req Request parameter which just contains the request line
   * @param toUser Used to create the 'To' header address - user part
   * @param toDomain Used to create the 'To' header address - host part of sip URI
   * @param subscriptionState active, pending, or terminated (SubscriptionStateHeader constant)
   * @param termReason any string
   * @param body the entire content as a string
   * @param timeLeft number of seconds to put in the NOTIFY
   * @return the modified request
   */
  public Request addNotifyHeaders(Request req, String toUser, String toDomain,
      String subscriptionState, String termReason, String body, int timeLeft) {
    setErrorMessage("");

    try {
      synchronized (dialogLock) {
        EventHeader ehdr;
        if (eventHeader != null) {
          ehdr = phone.getParent().getHeaderFactory().createEventHeader(eventHeader.getEventType());
          if (eventHeader.getEventId() != null) {
            ehdr.setEventId(eventHeader.getEventId());
          }
        } else {
          ehdr = phone.getParent().getHeaderFactory().createEventHeader(getEventType());
        }
        req.setHeader(ehdr);

        SubscriptionStateHeader hdr =
            phone.getParent().getHeaderFactory().createSubscriptionStateHeader(subscriptionState);

        if (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
          hdr.setReasonCode(termReason);
        } else if (timeLeft != -1) {
          hdr.setExpires(timeLeft);
        }
        req.setHeader(hdr);

        AcceptHeader accept = getAcceptHeaderForResponse();
        req.setHeader(accept);

        // now for the body
        if (body != null) {
          ContentTypeHeader ct_hdr = phone.getParent().getHeaderFactory()
              .createContentTypeHeader(getPackageContentType(), getPackageContentSubType());
          req.setContent(body, ct_hdr);
          req.setContentLength(
              phone.getParent().getHeaderFactory().createContentLengthHeader(body.length()));
        }

        if (dialog == null) {
          req.setHeader(phone.getParent().getHeaderFactory()
              .createCallIdHeader("somecallid-" + (System.currentTimeMillis() % 3600000)));

          toTag = new Long(Calendar.getInstance().getTimeInMillis()).toString();
          FromHeader from_header =
              phone.getParent().getHeaderFactory().createFromHeader(phone.getAddress(), toTag);
          req.setHeader(from_header);

          Address to = phone.getParent().getAddressFactory()
              .createAddress(phone.getParent().getAddressFactory().createSipURI(toUser, toDomain));
          ToHeader to_header = phone.getParent().getHeaderFactory().createToHeader(to, null);
          req.setHeader(to_header);

          CSeqHeader cseq =
              phone.getParent().getHeaderFactory().createCSeqHeader((long) 14, Request.NOTIFY);
          req.setHeader(cseq);

          MaxForwardsHeader max_forwards = phone.getParent().getHeaderFactory()
              .createMaxForwardsHeader(SipPhone.MAX_FORWARDS_DEFAULT);
          req.setHeader(max_forwards);

          List<ViaHeader> via_headers = phone.getViaHeaders();
          Iterator<ViaHeader> i = via_headers.iterator();
          while (i.hasNext()) {
            req.addHeader((Header) i.next());
          }

          req.addHeader((ContactHeader) phone.getContactInfo().getContactHeader().clone());
        }
      }
    } catch (Exception e) {
      setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
      return null;
    }

    return req;
  }

  /**
   * This method sends the given request to the subscriber. Knowledge of JAIN-SIP API headers is
   * required. The request will be resent if challenged. Use this method only if you have previously
   * called processSubscribe(). Use this method if you don't care about checking the response to the
   * sent NOTIFY, otherwise use sendStatefulNotify().
   * 
   * @param req javax.sip.message.Request to send.
   * @param viaProxy If true, send the message to the proxy. In this case a Route header will be
   *        added. Else send the message as is.
   * @return true if successful, false otherwise (call getErrorMessage() for details).
   */
  public boolean sendNotify(Request req, boolean viaProxy) {
    setErrorMessage("");

    synchronized (dialogLock) {
      if (dialog == null) {
        setErrorMessage("Can't send notify, haven't received a request");
        return false;
      }

      try {
        phone.addAuthorizations(((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId(), req);

        SipTransaction transaction = phone.sendRequestWithTransaction(req, viaProxy, dialog, this);
        if (transaction == null) {
          setErrorMessage(phone.getErrorMessage());
          return false;
        }

        setLastSentNotify(req);

        LOG.trace("Sent NOTIFY to {}:\n{}", dialog.getRemoteParty().getURI(), req);

        return true;
      } catch (Exception e) {
        setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
      }
    }

    return false;

  }

  /**
   * This method sends the given request to the subscriber. Use this method when you want to see the
   * response received in reply to the NOTIFY sent by this method. Authentication challenges
   * received in reponse to the sent request are not automatically handled by this class - the
   * caller will have to check for and handle it (TODO, provide a method that does like
   * processRespons() for the caller to use). Knowledge of JAIN-SIP API headers is required to use
   * this method. You may call this method whether or not this object has received a request (ie,
   * whether or not you have previously called processSubscribe() on this object.) You may
   * subsequently call waitResponse() to check the response returned by the far end.
   * 
   * @param req javax.sip.message.Request to send.
   * @param viaProxy If true, send the message to the proxy. In this case a Route header will be
   *        added. Else send the message as is.
   * @return A SipTransaction object if the message was sent successfully, null otherwise. The
   *         calling program doesn't need to do anything with the returned SipTransaction other than
   *         pass it in to a subsequent call to waitResponse().
   */
  public SipTransaction sendStatefulNotify(Request req, boolean viaProxy) {
    setErrorMessage("");
    SipTransaction transaction;

    synchronized (dialogLock) {
      transaction = phone.sendRequestWithTransaction(req, viaProxy, dialog);
      if (transaction == null) {
        setErrorMessage(phone.getErrorMessage());
        return null;
      }

      // enable auth challenge handling
      phone.enableAuthorization(((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId());

      dialog = transaction.getClientTransaction().getDialog();
      setLastSentNotify(req);
    }

    LOG.trace("Sent NOTIFY {}", req.getHeader(ToHeader.NAME));

    return transaction;
  }

  /**
   * The waitResponse() method waits for a response to a previously sent transactional request
   * message. Call this method after calling sendStatefulNotify().
   * 
   * <p>
   * This method blocks until one of the following occurs: 1) A javax.sip.ResponseEvent is received.
   * This is the object returned by this method. 2) A javax.sip.TimeoutEvent is received. This is
   * the object returned by this method. 3) The wait timeout period specified by the parameter to
   * this method expires. Null is returned in this case. 4) An error occurs. Null is returned in
   * this case.
   * 
   * <p>
   * Note that this method can be called repeatedly upon receipt of provisional response message(s).
   * 
   * @param trans The SipTransaction object associated with the sent request. This is the object
   *        returned by sendStatefulNotify().
   * @param timeout The maximum amount of time to wait, in milliseconds. Use a value of 0 to wait
   *        indefinitely.
   * @return A javax.sip.ResponseEvent, javax.sip.TimeoutEvent, or null in the case of wait timeout
   *         or error. If null, call getReturnCode() and/or getErrorMessage() and, if applicable,
   *         getException() for further diagnostics.
   */
  public EventObject waitResponse(SipTransaction trans, long timeout) {
    EventObject event = phone.waitResponse(trans, timeout);

    if (event instanceof ResponseEvent) {
      receivedResponses.add(new SipResponse((ResponseEvent) event));
    }

    return event;
  }

  /**
   * This method registers this notify sender as a UA with the proxy/registrar used to create the
   * SipPhone passed to this object's contructor.
   * 
   * @param credential authentication information matching that at the server
   * @return true if registration is successful, false otherwise. If false, call getErrorMessage()
   *         to find out why.
   */
  public boolean register(Credential credential) {
    phone.addUpdateCredential(credential);
    if (phone.register(null, 3600) == false) {
      setErrorMessage(phone.format());
      return false;
    }

    return true;
  }

  /**
   * Returns the error message, if any, associated with the last operation.
   * 
   * @return A String describing the error that occurred during the last operation, or an empty
   *         string ("") if there was no error.
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  protected void setErrorMessage(String errorMessage) {
    if (errorMessage.length() > 0) {
      LOG.trace("Notify sender error : {}", errorMessage);
    }
    this.errorMessage = errorMessage;
  }

  /**
   * Returns the NOTIFY request that was last sent, or null if none has ever been sent.
   * 
   * @return javax.sip.message.Request last sent.
   */
  public Request getLastSentNotify() {
    return lastSentNotify;
  }

  protected void setLastSentNotify(Request lastSentNotify) {
    this.lastSentNotify = (Request) lastSentNotify.clone();
  }

  public ArrayList<SipResponse> getAllReceivedResponses() {
    return new ArrayList<>(receivedResponses);
  }

  public ArrayList<SipRequest> getAllReceivedRequests() {
    return new ArrayList<>(receivedRequests);
  }

  public SipRequest getLastReceivedRequest() {
    synchronized (receivedRequests) {
      if (receivedRequests.isEmpty()) {
        return null;
      }

      return (SipRequest) receivedRequests.get(receivedRequests.size() - 1);
    }
  }

  public SipResponse getLastReceivedResponse() {
    synchronized (receivedResponses) {
      if (receivedResponses.isEmpty()) {
        return null;
      }

      return (SipResponse) receivedResponses.get(receivedResponses.size() - 1);
    }
  }

  /*
   * @see org.cafesip.sipunit.RequestListener#processEvent(java.util.EventObject)
   */
  public void processEvent(EventObject event) {
    // Don't need to do anything except resend if challenged
    // (we only get here for a stateless NOTIFY)

    if (event instanceof ResponseEvent) {
      receivedResponses.add(new SipResponse((ResponseEvent) event));
      resendWithAuthorization((ResponseEvent) event);
    }
  }

  /**
   * Called to find out if a sent NOTIFY was challenged. See related method
   * resendWithAuthorization().
   * 
   * @param event object returned by waitResponse().
   * @return true if response status is UNAUTHORIZED or PROXY_AUTHENTICATION_REQUIRED, false
   *         otherwise.
   */
  public boolean needAuthorization(ResponseEvent event) {
    Response response = event.getResponse();
    int status = response.getStatusCode();

    if ((status == Response.UNAUTHORIZED) || (status == Response.PROXY_AUTHENTICATION_REQUIRED)) {
      return true;
    }

    return false;
  }

  /**
   * This method resends a NOTIFY statefully and with required authorization headers. Call it after
   * you find out that authorization is required by calling method needAuthorization().
   * 
   * <p>
   * Example testcode usage (this object is "sender"): // get the response, trans is SipTransaction
   * object EventObject event = sender.waitResponse(trans, 2000);
   * assertNotNull(sender.getErrorMessage(), event);
   * 
   * <p>
   * if (event instanceof TimeoutEvent) { fail("Event Timeout received by far end while waiting for
   * NOTIFY response"); }
   * 
   * <p>
   * assertTrue("Expected auth challenge", sender .needAuthorization((ResponseEvent) event)); trans
   * = sender.resendWithAuthorization((ResponseEvent) event);
   * assertNotNull(sender.getErrorMessage(), trans); // get the next response event =
   * sender.waitResponse(trans, 2000); etc.
   * 
   * @param event object returned by waitResponse()
   * @return SipTransaction for internal use, only needs to be passed to waitResponse().
   */

  public SipTransaction resendWithAuthorization(ResponseEvent event) {
    Response response = event.getResponse();
    int status = response.getStatusCode();

    if ((status == Response.UNAUTHORIZED) || (status == Response.PROXY_AUTHENTICATION_REQUIRED)) {
      try {
        // modify the request to include user authorization info and
        // resend

        synchronized (dialogLock) {
          Request msg = getLastSentNotify();
          msg = phone.processAuthChallenge(response, msg);
          if (msg == null) {
            setErrorMessage("PresenceNotifySender: Error responding to authentication challenge: "
                + phone.getErrorMessage());
            return null;
          }

          // bump up the sequence number
          CSeqHeader hdr = (CSeqHeader) msg.getHeader(CSeqHeader.NAME);
          long cseq = hdr.getSeqNumber();
          hdr.setSeqNumber(cseq + 1);

          // send the message
          SipTransaction transaction = phone.sendRequestWithTransaction(msg, false, dialog);
          if (transaction == null) {
            setErrorMessage("Error resending NOTIFY with authorization: " + phone.getErrorMessage());
            return null;
          }

          dialog = transaction.getClientTransaction().getDialog();
          setLastSentNotify(msg);

          LOG.trace("Resent REQUEST: {}", msg);

          return transaction;
        }
      } catch (Exception ex) {
        setErrorMessage("Exception resending NOTIFY with authorization: " + ex.getClass().getName()
            + ": " + ex.getMessage());
      }
    }

    return null;
  }

  /**
   * @return Returns the dialog.
   */
  public Dialog getDialog() {
    return dialog;
  }

  /**
   * @param dialog The dialog to set.
   */
  public void setDialog(Dialog dialog) {
    this.dialog = dialog;

    if (dialog != null) {
      toTag = dialog.getLocalTag();
    }
  }

}
