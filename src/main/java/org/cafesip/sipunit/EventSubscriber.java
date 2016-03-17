/*
 * Created on Sep 10, 2005
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipProvider;
import javax.sip.TimeoutEvent;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.AcceptHeader;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ExpiresHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.ReferToHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The EventSubscriber class represents a generic subscription conforming to the event subscription
 * and asynchronous notification framework defined by RFC-3265. This class is used for the
 * Subscriber-side perspective as opposed to the NOTIFY sending side.
 * 
 * <p>
 * This object is created as the result of an initial outbound SUBSCRIBE message or REFER message.
 * This object is used indirectly by a test program to proceed through the subscribing side
 * SUBSCRIBE or REFER &lt;-&gt; NOTIFY sequence(s) and to find out details at any given time about
 * the subscription such as the subscription state, amount of time left on the subscription if still
 * active, termination reason if terminated, errors encountered during received SUBSCRIBE/REFER
 * response and incoming NOTIFY message validation, and details of any received responses and
 * requests if needed.
 * 
 * <p>
 * Event package-specific handling/information is handled by the classes extending this class. The
 * user test programs use those subclasses directly.
 * 
 * @author Becky McElroy
 * 
 */
public class EventSubscriber implements MessageListener, SipActionObject {

  private static final Logger LOG = LoggerFactory.getLogger(EventSubscriber.class);

  protected String targetUri; // The subscription target (ie,

  // sip:bob@nist.gov)

  protected Address targetAddress;

  protected String subscriptionState = SubscriptionStateHeader.PENDING;

  private String terminationReason;

  private long projectedExpiry = 0;

  protected SipPhone parent;

  private Dialog dialog;

  private SipTransaction transaction;

  // caller-invoked status info

  private int returnCode = -1;

  private String errorMessage = "";

  private Throwable exception;

  // misc message info

  private String myTag;

  private CSeqHeader requestCSeq;

  private CSeqHeader notifyCSeq;

  private CallIdHeader callId;

  /*
   * list of SipResponse
   */
  protected LinkedList<SipResponse> receivedResponses = new LinkedList<>();

  /*
   * list of SipRequest
   */
  protected LinkedList<SipRequest> receivedRequests = new LinkedList<>();

  /*
   * for wait operations
   */
  private LinkedList<RequestEvent> reqEvents = new LinkedList<>();

  private BlockObject responseBlock = new BlockObject();

  /*
   * misc
   */

  protected ResponseEvent currentResponse;

  protected Request lastSentRequest;

  private Object lastSentRequestLock = new Object(); // lock object for

  // lastSentRequest - test
  // program side writes to and reads
  // lastSentRequest, network
  // side needs to read it

  private boolean removalComplete = false;

  protected LinkedList<String> eventErrors = new LinkedList<>();

  protected EventSubscriber(String uri, SipPhone parent) throws ParseException {
    this(uri, parent, null);
  }

  protected EventSubscriber(String uri, SipPhone parent, Dialog dialog) throws ParseException {
    this.targetUri = uri.trim();
    targetAddress = parent.getAddressFactory().createAddress(this.targetUri);
    this.parent = parent;

    this.dialog = dialog;
    if (dialog == null) {
      callId = parent.getNewCallIdHeader();
      myTag = parent.generateNewTag();
    } else {
      callId = dialog.getCallId();
      myTag = dialog.getLocalTag();
    }

    if (parent.getAuthorizations().get(callId.getCallId()) == null) {
      parent.enableAuthorization(callId.getCallId());
    }
  }

  protected boolean startSubscription(Request req, long timeout, boolean viaProxy) {
    return startSubscription(req, timeout, viaProxy, null, null, null);
  }

  protected boolean startSubscription(Request req, long timeout, boolean viaProxy,
      ArrayList<Header> additionalHeaders, ArrayList<Header> replaceHeaders, String body) {
    initErrorInfo();
    LOG.trace("Starting subscription for URI {}", targetUri);

    if (sendRequest(req, viaProxy, additionalHeaders, replaceHeaders, body) == true) {
      if (waitNextPositiveResponse(timeout) == true) {
        return true;
      }
    }

    /*
     * Non-200 class final responses indicate that no subscription or dialog has been created, and
     * no subsequent NOTIFY message will be sent. All non-200 class responses (with the exception of
     * "489", described herein) have the same meanings and handling as described in SIP [1]
     */

    LOG.trace("Subscription startup failed : {}", getErrorMessage());

    return false;
  }

  protected boolean refreshSubscription(Request req, long timeout, boolean viaProxy) {
    initErrorInfo();
    LOG.trace("Refreshing subscription for URI {}, previous time left = {}", targetUri,
        getTimeLeft());

    if (sendRequest(req, viaProxy) == true) {
      if (waitNextPositiveResponse(timeout) == true) {
        return true;
      }

      if (getReturnCode() == SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST) { // 481
        /*
         * If a SUBSCRIBE request to refresh a subscription receives a "481" response, this
         * indicates that the subscription has been terminated and that the subscriber did not
         * receive notification of this fact. In this case, the subscriber should consider the
         * subscription invalid.
         */

        subscriptionState = SubscriptionStateHeader.TERMINATED;

        LOG.trace("Terminating subscription for URI {} due to received response code = {}",
            targetUri, getReturnCode());
      }

      /*
       * If a SUBSCRIBE request to refresh a subscription fails with a non-481 response, the
       * original subscription is still considered valid for the duration of the most recently known
       * "Expires" value as negotiated by SUBSCRIBE and its response, or as communicated by NOTIFY
       * in the "Subscription-State" header "expires" parameter.
       */
    }

    LOG.trace("Subscription refresh failed : {}", getErrorMessage());

    return false;
  }

  protected boolean endSubscription(Request req, long timeout, boolean viaProxy, String reason) {
    initErrorInfo();
    setRemovalComplete(true);

    if ((subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.ACTIVE))
        || (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.PENDING))) {
      LOG.trace("Ending subscription for URI {}, time left = {}", targetUri, getTimeLeft());

      subscriptionState = SubscriptionStateHeader.TERMINATED;
      terminationReason = reason;

      if (sendRequest(req, viaProxy) == true) {
        if (waitNextPositiveResponse(timeout) == true) {
          setRemovalComplete(false);
          return true;
        }
      }

      LOG.trace("Subscription termination failed : {}", getErrorMessage());

      return false;
    }

    return true;
  }

  protected boolean fetchSubscription(Request req, long timeout, boolean viaProxy) {
    initErrorInfo();
    LOG.trace("Fetching subscription information for URI {}", targetUri);

    subscriptionState = SubscriptionStateHeader.TERMINATED;
    terminationReason = "Fetch";

    if (sendRequest(req, viaProxy) == true) {
      if (waitNextPositiveResponse(timeout) == true) {
        return true;
      }
    }

    /*
     * Non-200 class final responses indicate that no subscription or dialog has been created, and
     * no subsequent NOTIFY message will be sent. All non-200 class responses (with the exception of
     * "489", described herein) have the same meanings and handling as described in SIP [1]
     */

    LOG.trace("Subscription fetch failed : {}", getErrorMessage());

    return false;
  }

  private boolean waitNextPositiveResponse(long timeout) {
    EventObject event = waitResponse(timeout);
    if (event != null) {
      if (event instanceof TimeoutEvent) {
        setReturnCode(SipSession.TIMEOUT_OCCURRED);
        setErrorMessage("The request sending transaction timed out.");
      } else if (event instanceof ResponseEvent) {
        ResponseEvent respEvent = (ResponseEvent) event;
        int status = respEvent.getResponse().getStatusCode();
        setReturnCode(status);
        setCurrentResponse(respEvent);

        if ((status / 100 == 1) || (status == Response.UNAUTHORIZED)
            || (status == Response.PROXY_AUTHENTICATION_REQUIRED) || (status == SipResponse.OK)
            || (status == SipResponse.ACCEPTED)) {
          return true;
        }

        // if we're here, we received a final, fatal retcode

        setErrorMessage("Received response status: " + status + ", reason: "
            + respEvent.getResponse().getReasonPhrase());
      }
    }

    return false;
  }

  // TODO - every x seconds, check for auto-renewal of subscriptions
  // for add/refreshBuddy, allow bool parameter auto-refresh;update
  // all subscriptions' timeLeft

  /**
   * This method creates and returns to the caller the next SUBSCRIBE message that would be sent out
   * for this subscription, so that the user can modify it before it gets sent (to introduce errors
   * - insert incorrect content, remove content, etc.). The idea is to call this method which will
   * create the SUBSCRIBE request correctly, then modify the returned request yourself, then call
   * one of the subscription-related methods (for example: PresenceSubscriber.refreshBuddy(),
   * ReferSubscriber.unsubscribe(), etc.) that take Request as a parameter, which will result in the
   * request being sent out.
   * 
   * <p>
   * If you don't need to modify the next SUBSCRIBE request to introduce errors, don't bother with
   * this method and just call one of the alternative subscription-related methods that doesn't take
   * a Request parameter - it will create and send the request in one step.
   * 
   * <p>
   * Effective use of this method requires knowledge of the JAIN SIP API Request and Header classes.
   * Use those to modify the request returned by this method.
   * 
   * <p>
   * Note that subscription-creating methods like SipPhone.addBuddy() and SipPhone.refer() do not
   * have any signatures that take Request as a parameter. The reason is because a correct initial
   * SUBSCRIBE or REFER request is needed to initialize the Subscription object properly. If you
   * want to send out a bad initial request message to see what your test target does, use the
   * SipPhone's base class SipSession (low-level) methods to send the bad request and get the
   * resulting response.
   * 
   * @param duration the duration in seconds to put in the SUBSCRIBE message. If -1, don't include a
   *        duration (ExpiresHeader) in the message.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later on another subscription-related method
   *        call.
   * @param eventType the eventType value (for example: "presence" or "refer") to use in the
   *        EventHeader and AllowEventsHeader
   * @return a SUBSCRIBE Request object if creation is successful, null otherwise. If null, call
   *         getReturnCode(), getErrorMessage() and/or getException() for failure info.
   */
  public Request createSubscribeMessage(int duration, String eventId, String eventType) {
    return createRequestMessage(Request.SUBSCRIBE, duration, eventId, eventType,
        parent.getProxyHost());
  }

  protected Request createRequestMessage(String method, int duration, String eventId,
      String eventType, String nextHop) {
    initErrorInfo();

    try {
      LOG.trace("Creating {} message with duration {}, event id = {}, event type = {}", method,
          (duration == -1 ? "not included" : duration), eventId, eventType);
      AddressFactory addrFactory = parent.getAddressFactory();
      HeaderFactory hdrFactory = parent.getHeaderFactory();

      Request req = null;

      if (dialog == null) {
        // first time sending a request
        // build the request

        URI requestUri = addrFactory.createURI(targetUri);

        FromHeader fromHeader = hdrFactory.createFromHeader(parent.getAddress(), myTag);
        ToHeader toHeader = hdrFactory.createToHeader(targetAddress, null);

        requestCSeq =
            hdrFactory.createCSeqHeader(requestCSeq == null ? 1 : (requestCSeq.getSeqNumber() + 1),
                method);

        MaxForwardsHeader maxForwards =
            hdrFactory.createMaxForwardsHeader(SipPhone.MAX_FORWARDS_DEFAULT);

        List<ViaHeader> viaHeaders = parent.getViaHeaders();

        req =
            parent.getMessageFactory().createRequest(requestUri, method, callId, requestCSeq,
                fromHeader, toHeader, viaHeaders, maxForwards);

        req.addHeader((ContactHeader) parent.getContactInfo().getContactHeader().clone());

        if (nextHop == null) {
          // local: add a route header to loop the message back to our
          // stack
          SipURI routeUri = parent.getAddressFactory().createSipURI(null, parent.getStackAddress());
          routeUri.setLrParam();
          routeUri.setPort(parent.getParent().getSipProvider().getListeningPoints()[0].getPort());
          routeUri.setTransportParam(parent.getParent().getSipProvider().getListeningPoints()[0]
              .getTransport());
          routeUri.setSecure(((SipURI) requestUri).isSecure());

          Address routeAddress = parent.getAddressFactory().createAddress(routeUri);
          req.addHeader(parent.getHeaderFactory().createRouteHeader(routeAddress));
        }

        LOG.trace("We have created this dialog-initiating {}: {}", method, req);
      } else if (dialog.getState() == null) {
        // we've sent before but not
        // heard back
        req = (Request) getLastSentRequest().clone();

        requestCSeq = hdrFactory.createCSeqHeader(requestCSeq.getSeqNumber() + 1, method);
        req.setHeader(requestCSeq);

        LOG.trace("We have created this dialog-initiating resend {}: {}", method, req);
      } else {
        // dialog is established enough to use
        req = dialog.createRequest(method);
        req.setHeader((ContactHeader) parent.getContactInfo().getContactHeader().clone());
        LOG.trace("Dialog has created this established dialog {}: {}", method, req);
      }

      // set other needed request info
      EventHeader hdr = hdrFactory.createEventHeader(eventType);
      if (eventId != null) {
        hdr.setEventId(eventId);
      }
      req.setHeader(hdr);

      req.addHeader(hdrFactory.createAllowEventsHeader(eventType));

      if (duration != -1) {
        req.setExpires(hdrFactory.createExpiresHeader(duration));
      } else {
        req.removeHeader(ExpiresHeader.NAME);
      }

      parent.addAuthorizations(callId.getCallId(), req);

      return req;
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    return null;
  }

  /**
   * @param referToUri
   * @param eventId the event "id" to use in the REFER message, or null for no event "id" parameter.
   * @param viaNonProxyRoute if not null, it will be the next hop
   * @return Request
   * @throws ParseException
   */
  protected Request createReferMessage(SipURI referToUri, String eventId, String viaNonProxyRoute)
      throws ParseException {
    String nextHop = parent.getProxyHost();
    if (viaNonProxyRoute != null) {
      nextHop = viaNonProxyRoute;
    }

    Request req = createRequestMessage(Request.REFER, -1, eventId, "refer", nextHop);

    // add Refer-To header
    Address refAddr = parent.getAddressFactory().createAddress(referToUri);
    ReferToHeader referTo = parent.getHeaderFactory().createReferToHeader(refAddr);
    req.addHeader(referTo);

    // handle routing
    if (viaNonProxyRoute != null) {
      int xportOffset = viaNonProxyRoute.indexOf('/');
      SipURI routeUri;
      if (xportOffset == -1) {
        routeUri = parent.getAddressFactory().createSipURI(null, viaNonProxyRoute);
        routeUri.setTransportParam("udp");
      } else {
        routeUri =
            parent.getAddressFactory().createSipURI(null,
                viaNonProxyRoute.substring(0, xportOffset));
        routeUri.setTransportParam(viaNonProxyRoute.substring(xportOffset + 1));
      }

      URI requestUri = req.getRequestURI();
      if (!requestUri.isSipURI()) {
        setErrorMessage("Only sip/sips routing URIs supported");
        setReturnCode(SipSession.INVALID_ARGUMENT);
        return null;
      }
      routeUri.setSecure(((SipURI) requestUri).isSecure());
      routeUri.setLrParam();

      Address routeAddress = parent.getAddressFactory().createAddress(routeUri);
      req.addHeader(parent.getHeaderFactory().createRouteHeader(routeAddress));
    }

    return req;
  }

  private boolean sendRequest(Request req, boolean viaProxy) {
    return sendRequest(req, viaProxy, null, null, null);
  }

  private boolean sendRequest(Request req, boolean viaProxy, ArrayList<Header> additionalHeaders,
      ArrayList<Header> replaceHeaders, String body) {
    if (req == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Null subscription request message given");
      return false;
    }

    synchronized (responseBlock) {
      // clear open transaction if any
      if (transaction != null) {
        parent.clearTransaction(transaction);
        transaction = null;
      }

      try {
        // save and send the message

        setLastSentRequest(req);

        transaction =
            parent.sendRequestWithTransaction(req, viaProxy, dialog, this, additionalHeaders,
                replaceHeaders, body);
        if (transaction == null) {
          setReturnCode(parent.getReturnCode());
          setErrorMessage(parent.getErrorMessage());
          setException(parent.getException());

          return false;
        }

        LOG.trace("Sent REQUEST: {}", req);

        dialog = transaction.getClientTransaction().getDialog();

        if (req.getExpires() != null) {
          int expires = req.getExpires().getExpires();
          if ((getTimeLeft() == 0) || (getTimeLeft() > expires)) {
            setTimeLeft(expires);
          }
        }

        LOG.trace("Sent subscription request to {} for {}", dialog.getRemoteParty().getURI(),
            targetUri);

        return true;
      } catch (Exception e) {
        setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
        setException(e);
        setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
      }

    }

    return false;

  } /*
     * @see org.cafesip.sipunit.MessageListener#processEvent(java.util.EventObject)
     */

  public void processEvent(EventObject event) {
    if (event instanceof RequestEvent) {
      processRequest((RequestEvent) event);
    } else if (event instanceof ResponseEvent) {
      processResponse((ResponseEvent) event);
    } else if (event instanceof TimeoutEvent) {
      processTimeout((TimeoutEvent) event);
    } else {
      LOG.error("invalid event type received: {}: {}", event.getClass().getName(), event.toString());
    }
  }

  // test prog may or may not yet be blocked waiting on this - watch
  // synchronization
  private void processRequest(RequestEvent requestEvent) {
    Request request = requestEvent.getRequest();

    // check CSEQ# - if no higher than current CSEQ, discard message

    CSeqHeader rcvSeqHdr = (CSeqHeader) request.getHeader(CSeqHeader.NAME);
    if (rcvSeqHdr == null) {
      EventSubscriber.sendResponse(parent, requestEvent, SipResponse.BAD_REQUEST,
          "no CSEQ header received");

      String err = "*** NOTIFY REQUEST ERROR ***  (" + targetUri + ") - no CSEQ header received";
      synchronized (eventErrors) {
        eventErrors.addLast(err);
      }
      LOG.trace(err);
      return;
    }

    if (notifyCSeq != null) {
      // This is not the first NOTIFY
      if (rcvSeqHdr.getSeqNumber() <= notifyCSeq.getSeqNumber()) {
        EventSubscriber.sendResponse(parent, requestEvent, SipResponse.OK, "OK");

        LOG.trace("Received NOTIFY CSEQ {}  not new, discarding message", rcvSeqHdr.getSeqNumber());
        return;
      }
    }

    notifyCSeq = rcvSeqHdr;

    synchronized (this) {
      receivedRequests.addLast(new SipRequest(requestEvent));
      reqEvents.addLast(requestEvent);
      this.notify();
    }
  }

  private void processResponse(ResponseEvent responseEvent) {
    synchronized (responseBlock) {
      if (transaction == null) {
        String errstring =
            "*** RESPONSE ERROR ***  (" + targetUri
                + ") : unexpected null transaction at response reception for request: "
                + responseEvent.getClientTransaction().getRequest().toString();
        synchronized (eventErrors) {
          eventErrors.addLast(errstring);
        }
        LOG.trace(errstring);
        return;
      }

      receivedResponses.addLast(new SipResponse(responseEvent));
      transaction.getEvents().addLast(responseEvent);
      responseBlock.notifyEvent();
    }
  }

  private void processTimeout(TimeoutEvent timeout) {
    // this method is called if there was no response to the
    // request we sent

    synchronized (responseBlock) {
      if (transaction == null) {
        String errstring =
            "*** RESPONSE ERROR ***  (" + targetUri
                + ") : unexpected null transaction at event timeout for request: "
                + timeout.getClientTransaction().getRequest().toString();
        synchronized (eventErrors) {
          eventErrors.addLast(errstring);
        }
        LOG.trace(errstring);
        return;
      }

      transaction.getEvents().addLast(timeout);
      responseBlock.notifyEvent();
    }
  }

  /**
   * This method processes the initial response received after sending a SUBSCRIBE or REFER request
   * and takes the transaction to its completion by collecting any remaining responses from the far
   * end for this transaction, handling authentication challenges if needed, and updating this
   * object with the results of the request/response sequence. It performs any eventpackage-specific
   * validation on the received response and returns false if that validation fails.
   * 
   * <p>
   * Call this method after calling any of the subscription operation methods that send a SUBSCRIBE
   * or REFER request like SipPhone.addBuddy(), PresenceSubscriber.refreshBuddy(), SipPhone.refer(),
   * etc. and getting back a positive indication.
   * 
   * <p>
   * If a success indication is returned by this method, you can call other methods on this object
   * to find out the result of the messaging sequence: isSubscriptionActive/Pending/Terminated() for
   * subscription state information, getTimeLeft() if subscription expiry information has been
   * received.
   * 
   * <p>
   * The next step if this method returns true is to call waitNotify() to retrieve/wait for the
   * NOTIFY request from the far end which may or may not have already come in.
   * 
   * @param timeout The maximum amount of time to wait for the subscription transaction to complete,
   *        in milliseconds. Use a value of 0 to wait indefinitely.
   * @return true if the response(s) received were valid and no errors were encountered, false
   *         otherwise (call getReturnCode(), getErrorMessage()).
   */
  public boolean processResponse(long timeout) {
    initErrorInfo();
    String cseqStr = "";

    try {
      if (currentResponse == null) {
        throw new SubscriptionError(SipSession.INVALID_OPERATION,
            "There is no outstanding response to process");
      }

      Response response = currentResponse.getResponse();
      int status = response.getStatusCode();
      cseqStr = "CSEQ " + ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).toString();

      LOG.trace("Processing {} response with status code {} from {}", cseqStr, status, targetUri);

      while (status != SipResponse.OK) {
        if (status == SipResponse.ACCEPTED) {
          break;
        }

        if (status / 100 == 1) {
          // provisional
          if (waitNextPositiveResponse(timeout) == false) {
            LOG.error("*** RESPONSE ERROR ***  ({}, " + targetUri + ") - {}", cseqStr,
                getErrorMessage());
            return false;
          }

          response = currentResponse.getResponse();
          status = response.getStatusCode();
          cseqStr = "CSEQ " + ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).toString();

          LOG.trace("Processing {} response with status code {} from {}", cseqStr, status,
              targetUri);

          continue;
        } else if ((status == Response.UNAUTHORIZED)
            || (status == Response.PROXY_AUTHENTICATION_REQUIRED)) {
          // resend the request with the required authorization
          authorizeSubscribe(response, getLastSentRequest());

          // get the new response
          if (waitNextPositiveResponse(timeout) == false) {
            LOG.error("*** RESPONSE ERROR ***  ({}, {}) - {}", cseqStr, targetUri,
                getErrorMessage());
            return false;
          }

          response = currentResponse.getResponse();
          status = response.getStatusCode();
          cseqStr = "CSEQ " + ((CSeqHeader) response.getHeader(CSeqHeader.NAME)).toString();

          LOG.trace("Processing {} response with status code {} from ", cseqStr, status, targetUri);

          continue;
        } else {
          throw new SubscriptionError(SipSession.FAR_END_ERROR,
              "Unexpected response code encountered from far end : " + status);
        }
      }

      // response status is OK or accepted
      validateOkAcceptedResponse(response);

      if (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
        return true;
      }

      // the subscription is alive - check expires header if applicable

      if (expiresResponseHeaderApplicable()) {
        if (response.getExpires() == null) {
          throw new SubscriptionError(SipSession.FAR_END_ERROR, "no expires header received");
        }

        int expires = response.getExpires().getExpires();
        validateExpiresDuration(expires, false);

        // use the received expiry time as the subscription duration
        LOG.trace("{}: received expiry = {}, updating current expiry which was {}", targetUri,
            expires, getTimeLeft());

        setTimeLeft(expires);
      }

      // set subscription state
      if (status == SipResponse.OK) {
        subscriptionState = SubscriptionStateHeader.ACTIVE;
      }

      return true;
    } catch (SubscriptionError e) {
      String err =
          "*** RESPONSE ERROR ***  (" + cseqStr + ", " + targetUri + ") - " + e.getReason();
      LOG.error(err);

      setReturnCode(e.getStatusCode());
      setErrorMessage(err);
    }

    return false;
  }

  private void authorizeSubscribe(Response resp, Request msg) throws SubscriptionError {
    // modify the request to include user authorization info and resend

    msg = parent.processAuthChallenge(resp, msg);
    if (msg == null) {
      throw new SubscriptionError(parent.getReturnCode(),
          "error responding to authentication challenge: " + parent.getErrorMessage());
    }

    try {
      // bump up the sequence number
      long lastSeq = ((CSeqHeader) msg.getHeader(CSeqHeader.NAME)).getSeqNumber();
      ((CSeqHeader) msg.getHeader(CSeqHeader.NAME)).setSeqNumber(++lastSeq);

      synchronized (responseBlock) {
        // send the message
        transaction = parent.sendRequestWithTransaction(msg, false, null, this);

        if (transaction == null) {
          throw new SubscriptionError(parent.getReturnCode(),
              "error resending request with authorization: " + parent.getErrorMessage());
        }

        dialog = transaction.getClientTransaction().getDialog();

        LOG.trace("Resent request: {}", msg.toString());
        LOG.trace("Resent request to {} for {}", dialog.getRemoteParty().getURI(), targetUri);
      }
    } catch (Exception ex) {
      transaction = null;
      throw new SubscriptionError(SipSession.EXCEPTION_ENCOUNTERED,
          "exception resending request with authorization: " + ex.getClass().getName() + ": "
              + ex.getMessage());
    }

  }

  protected static void sendResponse(SipPhone parent, RequestEvent req, int status, String reason) {
    try {
      Response response = parent.getMessageFactory().createResponse(status, req.getRequest());
      response.setReasonPhrase(reason);

      if (req.getServerTransaction() != null) {
        req.getServerTransaction().sendResponse(response);
        return;
      }

      ((SipProvider) req.getSource()).sendResponse(response);
    } catch (Exception e) {
      LOG.error("Failure sending error response (" + reason + ") for received "
          + req.getRequest().getMethod() + ", Exception: " + e.toString(), e);
    }
  }

  /**
   * This method validates the given (received) NOTIFY request, updates the subscription information
   * based on the NOTIFY contents, and creates and returns the correctly formed response that should
   * be sent back in reply to the NOTIFY, based on the NOTIFY content that was received. Call this
   * method after getting a NOTIFY request from method waitNotify().
   * 
   * <p>
   * If a null value is returned by this method, call getReturnCode() and/or getErrorMessage() to
   * see why.
   * 
   * <p>
   * If a non-null response object is returned by this method, it doesn't mean that NOTIFY
   * validation passed. If there was invalid content in the NOTIFY, the response object returned by
   * this method will have the appropriate error code (489 Bad Event, etc.) that should be sent in
   * reply to the NOTIFY. You can call getReturnCode() to find out the status code contained in the
   * returned response (or you can examine the response in detail using JAIN-SIP API). A return code
   * of 200 OK means that the received NOTIFY had correct content and the event information stored
   * in this Subscription object has been updated with the NOTIFY message contents. In this case you
   * can call methods isSubscriptionActive/Pending/Terminated(), getTerminationReason() and/or
   * getTimeLeft() for updated subscription information, and for event-specific information that may
   * have been updated by the received NOTIFY, call the appropriate Subscription subclass methods.
   * 
   * <p>
   * The next step after this is to invoke replyToNotify() to send the response to the network. You
   * may modify/corrupt the response returned by this method (using the JAIN-SIP API) before passing
   * it to replyToNotify().
   * 
   * <p>
   * Validation performed by this method includes: event header existence, correct event type (done
   * by the event-specific subclass), NOTIFY event ID matches that in the sent request (SUBSCRIBE,
   * REFER), subscription state header existence, received expiry not greater than that sent in the
   * request if it was included there, catch illegal reception of NOTIFY request without having sent
   * a request, supported content type/subtype in NOTIFY (done by the event-specific subclass), and
   * other event-specific validation (such as, for presence: matching (correct) presentity in NOTIFY
   * body, correctly formed xml body document, valid document content).
   * 
   * @param requestEvent the NOTIFY request event obtained from waitNotify()
   * @return a correct javax.sip.message.Response that should be sent back in reply, or null if an
   *         error was encountered.
   */
  public Response processNotify(RequestEvent requestEvent) {
    initErrorInfo();
    String cseqStr = "CSEQ x";

    try {
      if ((requestEvent == null) || (getLastSentRequest() == null)) {
        setReturnCode(SipSession.INVALID_OPERATION);
        setErrorMessage("Request event is null and/or last sent request is null");
        return null;
      }

      Request request = requestEvent.getRequest();
      cseqStr = "CSEQ " + ((CSeqHeader) request.getHeader(CSeqHeader.NAME)).getSeqNumber();

      LOG.trace("Processing NOTIFY {} request for subscription to {}", cseqStr, targetUri);

      // validate received event header against the one sent

      validateEventHeader((EventHeader) request.getHeader(EventHeader.NAME),
          (EventHeader) getLastSentRequest().getHeader(EventHeader.NAME));

      // get subscription state info from message

      SubscriptionStateHeader subsHdr =
          (SubscriptionStateHeader) request.getHeader(SubscriptionStateHeader.NAME);
      if (subsHdr == null) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "no subscription state header received");
      }
      validateSubscriptionStateHeader(subsHdr);

      int expires = subsHdr.getExpires();
      if (!subsHdr.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
        // SIP list TODO - it's optional for presence - how to know if
        // didn't get it?

        if (getLastSentRequest().getExpires() != null) {
          validateExpiresDuration(expires, true);
        }
      }

      updateEventInfo(request);

      // all is well, update our subscription state information
      if (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED) == false) {
        subscriptionState = subsHdr.getState();
      }

      if (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
        terminationReason = subsHdr.getReasonCode();

        // this is the last NOTIFY for this subscription, whether we
        // terminated
        // it from our end, or the other end just terminated it - don't
        // accept
        // any more by clearing out lastSentRequest, if one is received
        // after
        // this, SipPhone will respond with 481
        setLastSentRequest(null);

        Response response = createNotifyResponse(requestEvent, SipResponse.OK, "OK");
        if (response != null) {
          setReturnCode(SipResponse.OK);
        }

        return response;
      }

      // subscription is active or pending - update time left
      LOG.trace("{}: received expiry = {}, updating current expiry which was {}", targetUri,
          expires, getTimeLeft());
      setTimeLeft(expires);

      Response response = createNotifyResponse(requestEvent, SipResponse.OK, "OK");
      if (response != null) {
        setReturnCode(SipResponse.OK);
      }

      return response;
    } catch (SubscriptionError e) {
      String err =
          "*** NOTIFY REQUEST ERROR ***  (" + cseqStr + ", " + targetUri + ") - " + e.getReason();
      LOG.error(err);

      Response response = createNotifyResponse(requestEvent, e.getStatusCode(), e.getReason());
      if (response != null) {
        setReturnCode(e.getStatusCode());
        setErrorMessage(e.getReason());
      }

      return response;
    }
  }

  /**
   * This method sends the given response to the network in reply to the given request that was
   * previously received. Call this method after processNotify() has handled the received request.
   * 
   * @param reqevent The object returned by waitNotify().
   * @param response The object returned by processNotify(), or a user-modified version of it.
   * @return true if the response is successfully sent out, false otherwise.
   */
  public boolean replyToNotify(RequestEvent reqevent, Response response) {
    initErrorInfo();

    if ((reqevent == null) || (reqevent.getRequest() == null) || (response == null)) {
      setErrorMessage("Cannot send reply, request or response info is null");
      setReturnCode(SipSession.INVALID_ARGUMENT);
      return false;
    }

    try {
      if (reqevent.getServerTransaction() == null) {
        // 1st NOTIFY received before 1st response
        LOG.trace("Informational : no UAS transaction available for received NOTIFY");

        // send response statelessly
        ((SipProvider) reqevent.getSource()).sendResponse(response);
      } else {
        reqevent.getServerTransaction().sendResponse(response);
      }
    } catch (Exception e) {
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      return false;
    }

    LOG.trace("Sent response message to received NOTIFY, status code : {}, reason : {}",
        response.getStatusCode(), response.getReasonPhrase());

    return true;
  }

  protected boolean messageForMe(javax.sip.message.Message msg) {
    /*
     * NOTIFY requests are matched to SUBSCRIBE/REFER requests if they contain the same "Call-ID", a
     * "To" header "tag" parameter which matches the "From" header "tag" parameter of the
     * SUBSCRIBE/REFER, and the same "Event" header field.
     */

    Request lastSentRequest = getLastSentRequest();

    if (lastSentRequest == null) {
      return false;
    }

    CallIdHeader hdr = (CallIdHeader) msg.getHeader(CallIdHeader.NAME);
    if (hdr == null) {
      return false;
    }

    CallIdHeader sentHdr = (CallIdHeader) lastSentRequest.getHeader(CallIdHeader.NAME);
    if (sentHdr == null) {
      return false;
    }

    if ((hdr.getCallId() == null) || (sentHdr.getCallId() == null)) {
      return false;
    }

    if (hdr.getCallId().equals(sentHdr.getCallId()) == false) {
      return false;
    }

    // check to-tag = from-tag, (my tag), and event header
    // fields same as in sent request

    ToHeader tohdr = (ToHeader) msg.getHeader(ToHeader.NAME);
    if (tohdr == null) {
      return false;
    }

    String toTag = tohdr.getTag();
    if (toTag == null) {
      return false;
    }

    FromHeader sentFrom = (FromHeader) lastSentRequest.getHeader(FromHeader.NAME);
    if (sentFrom == null) {
      return false;
    }

    String fromTag = sentFrom.getTag();
    if (fromTag == null) {
      return false;
    }

    if (toTag.equals(fromTag) == false) {
      return false;
    }

    return eventForMe(msg, lastSentRequest);
  }

  protected boolean eventForMe(javax.sip.message.Message msg, Request lastSentRequest) {
    EventHeader eventhdr = (EventHeader) msg.getHeader(EventHeader.NAME);
    EventHeader sentEventhdr = (EventHeader) lastSentRequest.getHeader(EventHeader.NAME);

    if ((eventhdr == null) || (sentEventhdr == null)) {
      return false;
    }

    if (eventhdr.equals(sentEventhdr) == false) {
      return false;
    }

    return true;
  }

  private void validateEventHeader(EventHeader receivedHdr, EventHeader sentHdr)
      throws SubscriptionError {
    if (receivedHdr == null) {
      throw new SubscriptionError(SipResponse.BAD_EVENT, "no event header received");
    }

    checkEventType(receivedHdr);

    // verify matching eventIds

    String lastSentId = null;
    if (sentHdr != null) {
      lastSentId = sentHdr.getEventId();
    }

    // get the one we just received
    String eventId = receivedHdr.getEventId();
    if (eventId == null) {
      if (lastSentId != null) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "event id mismatch, received null, last sent " + lastSentId);
      }
    } else {
      if (lastSentId == null) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "event id mismatch, last sent null, received " + eventId);
      } else {
        if (eventId.equals(lastSentId) == false) {
          throw new SubscriptionError(SipResponse.BAD_REQUEST, "event id mismatch, received "
              + eventId + ", last sent " + lastSentId);
        }
      }
    }
  }

  private void validateExpiresDuration(int expires, boolean isNotify) throws SubscriptionError {
    // expiry may be shorter, can't be longer than what we sent

    int sentExpires = getLastSentRequest().getExpires().getExpires();

    if (expires > sentExpires) {
      throw new SubscriptionError((isNotify == true ? SipResponse.BAD_REQUEST
          : SipSession.FAR_END_ERROR), "received expiry > expiry in sent SUBSCRIBE (" + expires
          + " > " + sentExpires + ')');
    }
  }

  /**
   * Returns the number of seconds left in the subscription, if active, or the number of seconds
   * that were remaining at the time the subscription was terminated.
   * 
   * @return Returns the timeLeft in seconds.
   */
  public int getTimeLeft() {
    if (projectedExpiry == 0) {
      return 0;
    }

    return (int) ((projectedExpiry - System.currentTimeMillis()) / 1000);
  }

  /**
   * @param timeLeft The timeLeft to set, in seconds.
   */
  protected void setTimeLeft(int timeLeft) {
    if (timeLeft <= 0) {
      projectedExpiry = 0;
      return;
    }

    projectedExpiry = System.currentTimeMillis() + (timeLeft * 1000);
  }

  protected void initErrorInfo() {
    setErrorMessage("");
    setException(null);
    setReturnCode(SipSession.NONE_YET);
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

  /**
   * Gets the last response received on this subscription.
   * 
   * @return A SipResponse object representing the last response message received, or null if none
   *         has been received.
   * 
   * @see org.cafesip.sipunit.MessageListener#getLastReceivedResponse()
   */
  public SipResponse getLastReceivedResponse() {
    synchronized (responseBlock) {
      if (receivedResponses.isEmpty()) {
        return null;
      }

      return (SipResponse) receivedResponses.getLast();
    }
  }

  /**
   * Gets the last request received on this subscription.
   * 
   * @return A SipRequest object representing the last request message received, or null if none has
   *         been received.
   * 
   * @see org.cafesip.sipunit.MessageListener#getLastReceivedRequest()
   */
  public SipRequest getLastReceivedRequest() {
    synchronized (this) {
      if (receivedRequests.isEmpty()) {
        return null;
      }

      return (SipRequest) receivedRequests.getLast();
    }
  }

  /**
   * Gets the responses received on this subscription, including any that required re-initiation of
   * the subscription (ie, authentication challenge). Not included are out-of-sequence (late)
   * responses.
   * 
   * @return ArrayList of zero or more SipResponse objects.
   * 
   * @see org.cafesip.sipunit.MessageListener#getAllReceivedResponses()
   */
  public ArrayList<SipResponse> getAllReceivedResponses() {
    synchronized (responseBlock) {
      return new ArrayList<>(receivedResponses);
    }
  }

  /**
   * Gets the NOTIFY requests received on this subscription. (Retransmissions aren't included.)
   * 
   * @return ArrayList of zero or more SipRequest objects.
   * 
   * @see org.cafesip.sipunit.MessageListener#getAllReceivedRequests()
   */
  public ArrayList<SipRequest> getAllReceivedRequests() {
    synchronized (this) {
      return new ArrayList<>(receivedRequests);
    }
  }

  /**
   * Indicates if the subscription state is TERMINATED.
   * 
   * @return true if so, false if not.
   */
  public boolean isSubscriptionTerminated() {
    return (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.TERMINATED));
  }

  /**
   * Indicates if the subscription state is ACTIVE.
   * 
   * @return true if so, false if not.
   */
  public boolean isSubscriptionActive() {
    return (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.ACTIVE));
  }

  /**
   * Indicates if the subscription state is PENDING.
   * 
   * @return true if so, false if not.
   */
  public boolean isSubscriptionPending() {
    return (subscriptionState.equalsIgnoreCase(SubscriptionStateHeader.PENDING));
  }

  /**
   * Returns the subscription termination reason for this subscription. Call this method when the
   * subscription has been terminated (method isSubscriptionTerminated() returns true).
   * 
   * @return Returns the termination reason or null if the subscription is not terminated.
   */
  public String getTerminationReason() {
    return terminationReason;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getErrorMessage()
   */
  public String getErrorMessage() {
    return errorMessage;
  }

  protected void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getException()
   */
  public Throwable getException() {
    return exception;
  }

  protected void setException(Throwable exception) {
    this.exception = exception;
  }

  /*
   * @see org.cafesip.sipunit.SipActionObject#getReturnCode()
   */
  public int getReturnCode() {
    return returnCode;
  }

  protected void setReturnCode(int returnCode) {
    this.returnCode = returnCode;
  }

  /**
   * Gets the URI of the user that this Subscription is for.
   * 
   * @return The user's URI.
   */
  public String getTargetUri() {
    return targetUri;
  }

  /**
   * Get any errors accumulated during collection of responses and NOTIFY requests. Since this
   * happens automatically, asynchronous of the test program activity, there's not a handy way like
   * a method call return code to report these errors if they happen. They are errors like: No CSEQ
   * header in received NOTIFY, error or exception resending request with authorization header,
   * unexpected null transaction object at response timeout, etc. You should at various points call
   * assertNoSubscriptionErrors() method on SipTestCase or SipAssert during a test to verify none
   * have been encountered.
   * 
   * <p>
   * The case where a NOTIFY is received by a SipPhone but there is no matching subscription results
   * in 481 response being sent back and an event error entry in each Subscription object associated
   * with that SipPhone (to ensure it will be seen by the test program).
   * 
   * <p>
   * Aside from being put in the event error list, event errors are output with the SipUnit trace if
   * you have it turned on (SipStack.setTraceEnabled(true)). You can clear this list by calling
   * clearEventErrors().
   * 
   * @return LinkedList (never null) of zero or more String
   */
  public LinkedList<String> getEventErrors() {
    synchronized (eventErrors) {
      return new LinkedList<>(eventErrors);
    }
  }

  /**
   * This method clears errors accumulated while collecting responses and NOTIFY requests. See
   * related method getEventErrors().
   */
  public void clearEventErrors() {
    synchronized (eventErrors) {
      eventErrors.clear();
    }
  }

  protected Response createNotifyResponse(RequestEvent request, int status, String reason) {
    ArrayList<Header> additionalHeaders = null;

    if (status == SipResponse.UNSUPPORTED_MEDIA_TYPE) {
      try {
        AcceptHeader ahdr = getUnsupportedMediaAcceptHeader();
        additionalHeaders = new ArrayList<>();
        additionalHeaders.add(ahdr);
      } catch (Exception e) {
        setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
        setException(e);
        setErrorMessage("Couldn't create accept header for 'Unsupported Media Type' response : Exception: "
            + e.getClass().getName() + ": " + e.getMessage());

        return null;
      }
    }

    // here we may need an overridable method with parm status, so
    // subclasses may included other additional headers

    return createNotifyResponse(request, status, reason, additionalHeaders);
  }

  // if returns null, returnCode and errorMessage already set
  protected Response createNotifyResponse(RequestEvent request, int status, String reason,
      List<Header> additionalHeaders) {
    // when used internally - WATCH OUT - retcode, errorMessage initialized here
    initErrorInfo();

    if ((request == null) || (request.getRequest() == null)) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Null request given for creating NOTIFY response");
      return null;
    }

    Request req = request.getRequest();
    String cseqStr = "CSEQ " + ((CSeqHeader) req.getHeader(CSeqHeader.NAME)).getSeqNumber();
    LOG.trace("Creating NOTIFY {} response with status code {}, reason phrase = {}", cseqStr,
        status, reason);

    try {
      Response response = parent.getMessageFactory().createResponse(status, req);

      if (reason != null) {
        response.setReasonPhrase(reason);
      }

      ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(myTag);

      response.addHeader((ContactHeader) parent.getContactInfo().getContactHeader().clone());

      if (additionalHeaders != null) {
        Iterator<Header> i = additionalHeaders.iterator();
        while (i.hasNext()) {
          response.addHeader(i.next());
        }
      }

      return response;
    } catch (Exception e) {
      setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
      setException(e);
      setErrorMessage("Exception: " + e.getClass().getName() + ": " + e.getMessage());
    }

    return null;
  }

  protected String getEventId() {
    Request lastSentRequest = getLastSentRequest();

    if (lastSentRequest == null) {
      return null;
    }

    EventHeader evt = (EventHeader) lastSentRequest.getHeader(EventHeader.NAME);
    if (evt == null) {
      return null;
    }

    return evt.getEventId();
  }

  /**
   * Get the dialog associated with this subscription.
   * 
   * @return The JAIN-SIP Dialog object.
   */
  public Dialog getDialog() {
    return dialog;
  }

  /**
   * Get the dialog ID associated with this subscription, or an empty string if the dialog isn't
   * created yet.
   * 
   * @return String which is the dialog ID associated with this subscription
   */
  public String getDialogId() {
    return dialog == null ? "" : dialog.getDialogId();
  }

  /**
   * The waitNotify() method allows received NOTIFY messages to be examined and processed by the
   * test program, one by one. Call this method whenever you are expecting a NOTIFY to be received
   * and your test program has nothing else to do until then. If there are already one or more
   * unexamined-as-yet-by-the-test-program NOTIFY messages accumulated when this method is called,
   * it returns the next in line (FIFO) immediately. Otherwise, it waits for the next NOTIFY message
   * to be received from the network for this subscription.
   * 
   * <p>
   * This method blocks until one of the following occurs: 1) A NOTIFY message is received, for this
   * subscription. The received NOTIFY javax.sip.RequestEvent object is returned in this case. The
   * calling program may examine the returned object (requires knowledge of JAIN SIP). The next step
   * for the caller is to pass the object returned by this method to processNotify() for handling.
   * 2) The wait timeout period specified by the parameter to this method expires. Null is returned
   * in this case. 3) An error occurs. Null is returned in this case.
   * 
   * <p>
   * A NOTIFY message whose CSEQ# is not greater than those previously received is discarded and not
   * returned by this method.
   * 
   * @param timeout The maximum amount of time to wait, in milliseconds. Use a value of 0 to wait
   *        indefinitely.
   * @return A RequestEvent (received NOTIFY) or null in the case of wait timeout or error. If null
   *         is returned, call getReturnCode() and/or getErrorMessage() and, if applicable,
   *         getException() for further diagnostics.
   */
  public RequestEvent waitNotify(long timeout) {
    initErrorInfo();

    synchronized (this) {
      if (reqEvents.isEmpty()) {
        try {
          LOG.trace("about to block, waiting");
          this.wait(timeout);
          LOG.trace("we've come out of the block");
        } catch (Exception ex) {
          setException(ex);
          setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
          setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
          return null;
        }
      }

      LOG.trace("either we got the request, or timed out");
      if (reqEvents.isEmpty()) {
        String err =
            "*** NOTIFY REQUEST ERROR ***  (" + targetUri
                + ") - The maximum amount of time to wait for a NOTIFY message has elapsed.";
        synchronized (eventErrors) {
          eventErrors.addLast(err);
        }
        LOG.trace(err);

        setReturnCode(SipSession.TIMEOUT_OCCURRED);
        setErrorMessage(err);
        return null;
      }

      return (RequestEvent) reqEvents.removeFirst();
    }
  }

  /**
   * The waitResponse() method waits for a response for a previously sent subscription request
   * message (SUBSCRIBE, REFER, ...).
   * 
   * <p>
   * This method blocks until one of the following occurs: 1) A javax.sip.ResponseEvent is received.
   * This is the object returned by this method. 2) A javax.sip.TimeoutEvent is received. This is
   * the object returned by this method. 3) The wait timeout period specified by the parameter to
   * this method expires. Null is returned in this case. 4) An error occurs. Null is returned in
   * this case.
   * 
   * @param timeout The maximum amount of time to wait, in milliseconds. Use a value of 0 to wait
   *        indefinitely.
   * @return A javax.sip.ResponseEvent, javax.sip.TimeoutEvent, or null in the case of wait
   *         parameter timeout or error. If null, call getReturnCode() and/or getErrorMessage() and,
   *         if applicable, getException() for further diagnostics.
   */
  protected EventObject waitResponse(long timeout) {
    synchronized (responseBlock) {
      LinkedList<EventObject> events = transaction.getEvents();
      if (events.isEmpty()) {
        try {
          LOG.trace("about to block, waiting");
          responseBlock.waitForEvent(timeout);
          LOG.trace("we've come out of the block");
        } catch (Exception ex) {
          setException(ex);
          setErrorMessage("Exception: " + ex.getClass().getName() + ": " + ex.getMessage());
          setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
          return null;
        }
      }

      LOG.trace("either we got the response, or timed out");

      if (events.isEmpty()) {
        setReturnCode(SipSession.TIMEOUT_OCCURRED);
        setErrorMessage("The maximum amount of time to wait for a response message has elapsed.");
        return null;
      }

      return (EventObject) events.removeFirst();
    }
  }

  /**
   * Get the most recent response received from the network for this subscription. Knowledge of
   * JAIN-SIP API is required to examine the object returned from this method. Alternately, call
   * getLastReceivedResponse() to see the primary values (status, reason) contained in the last
   * received response.
   * 
   * @return javax.sip.ResponseEvent - last received response to a previously sent subscription
   *         request.
   */
  public ResponseEvent getCurrentResponse() {
    return currentResponse;
  }

  protected void setCurrentResponse(ResponseEvent currentResponse) {
    this.currentResponse = currentResponse;
  }

  protected void addEventError(String err) {
    synchronized (eventErrors) {
      eventErrors.addLast(err);
    }
  }

  /**
   * Get the last request that was sent out for this subscription.
   * 
   * @return javax.sip.message.Request last sent out
   */
  public Request getLastSentRequest() {
    synchronized (lastSentRequestLock) {
      return lastSentRequest;
    }
  }

  protected void setLastSentRequest(Request lastSentRequest) {
    synchronized (lastSentRequestLock) {
      this.lastSentRequest = lastSentRequest;
    }
  }

  /**
   * This method, called after an operation that ends a subscription (such as
   * PresenceSubscriber.removeBuddy() or ReferSubscriber.unsubscribe()), indicates if an unsubscribe
   * sequence was initiated due to the operation or not. If so, you need to proceed forward with the
   * SUBSCRIBE/NOTIFY sequence processing to complete the unsubscribe sequence.
   * 
   * @return true if unsubscribe was not necessary (because the subscription was already terminated)
   *         or false if a SUBSCRIBE/NOTIFY sequence was initiated due to the subscription-ending
   *         operation.
   */
  public boolean isRemovalComplete() {
    return removalComplete;
  }

  protected void setRemovalComplete(boolean removalComplete) {
    this.removalComplete = removalComplete;
  }

  /**
   * This method must be overridden by the event-specific subclass.
   * 
   * @param receivedHdr Header from received request
   * @throws SubscriptionError
   */
  protected void checkEventType(EventHeader receivedHdr) throws SubscriptionError {
    // TODO subclass override
  }

  /**
   * This method must be overridden by the event-specific subclass.
   * 
   * @param request Received request
   * @throws SubscriptionError
   */
  protected void updateEventInfo(Request request) throws SubscriptionError {
    // TODO subclass override
  }

  /**
   * This method must be overridden by the event-specific subclass. It is called after the
   * event-specific subclass method updateEventInfo() throws an exception with
   * SipResponse.UNSUPPORTED_MEDIA_TYPE because an invalid content type/subtype was received in a
   * NOTIFY. This method must return the appropriate AcceptHeader to go into the outbound NOTIFY
   * response message.
   * 
   * @return AcceptHeader to put in the NOTIFY response after receiving an invalid type/subtype in a
   *         NOTIFY request.
   * @throws ParseException
   */
  protected AcceptHeader getUnsupportedMediaAcceptHeader() throws ParseException {
    // TODO subclass override
    return null;
  }

  protected boolean expiresResponseHeaderApplicable() {
    // TODO subclass override
    return true;
  }

  protected void validateOkAcceptedResponse(Response response) throws SubscriptionError {
    // TODO subclass override if validation needed
  }

  protected void validateSubscriptionStateHeader(SubscriptionStateHeader subsHdr)
      throws SubscriptionError {
    // TODO subclass override if validation needed
  }
}

/*
 * NEW HEADERS This table expands on tables 2 and 3 in SIP [1], as amended by the changes described
 * in section 7.1.
 * 
 * Header field where proxy ACK BYE CAN INV OPT REG PRA SUB NOT
 * ----------------------------------------------------------------- Allow-Events R o o - o o o o o
 * o Allow-Events 2xx - o - o o o o o o Allow-Events 489 - - - - - - - m m Event R - - - - - - - m m
 * Subscription-State R - - - - - - - - m
 */

