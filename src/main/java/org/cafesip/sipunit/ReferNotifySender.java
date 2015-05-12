/*
 * Created on May 17, 2009
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
import java.util.Calendar;

import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.header.AcceptHeader;
import javax.sip.header.AllowEventsHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.Header;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.SupportedHeader;
import javax.sip.message.Request;

/**
 * The primary purpose of this class is as a test utility, to verify other UA's NOTIFY reception
 * processing.
 * 
 * <p>
 * When instantiated, an object of this class listens for a REFER or SUBSCRIBE message. After the
 * calling program has sent a message to this object's uri, it can call this object's processRefer()
 * or processSubscribe() method to receive and process the received request and send a response.
 * After that, this object sends a NOTIFY message on that subscription each time method sendNotify()
 * is called, with the given content body. This class can listen for and show the response to a
 * given sent NOTIFY message. It can receive and process multiple request messages for a
 * subscription.
 * 
 * @author Becky McElroy
 * 
 */
public class ReferNotifySender extends PresenceNotifySender {

  private static final Logger LOG = LoggerFactory.getLogger(ReferNotifySender.class);

  /**
   * A constructor for this class. This object immediately starts listening for a REFER request.
   * 
   * @param userb SipPhone object to use for messaging.
   */
  public ReferNotifySender(SipPhone userb) {
    super(userb);
  }

  /**
   * This method waits for up to 10 seconds to receive a REFER and if received, it sends an OK
   * response.
   */
  public void processRefer() {
    processRefer(10000, SipResponse.OK, null);
  }

  /**
   * This method starts a thread that waits for up to 'timeout' milliseconds to receive a REFER and
   * if received, it sends a response with 'statusCode' and 'reasonPhrase' (if not null). This
   * method waits 500 ms before returning to allow the thread to get started and begin waiting for
   * an incoming REFER. This method adds 500ms to the given timeout to account for this delay.
   * 
   * @param timeout - number of milliseconds to wait for the request
   * @param statusCode - use in the response to the request
   * @param reasonPhrase - if not null, use in the response
   */
  public void processRefer(long timeout, int statusCode, String reasonPhrase) {
     processRefer(timeout, statusCode, reasonPhrase, -1, null);
  }

  /**
   * Same as the other processRefer() except allows for a couple of overrides for testing error
   * handling by the far end outbound REFER side: (a) takes a duration for adding ExpiresHeader to
   * the REFER response (the response shouldn't have an expires header), and (b) this method takes
   * an EventHeader for overriding what would normally/correctly be sent back in the response
   * (normally same as what was received in the request).
   */
  public void processRefer(long timeout, int statusCode, String reasonPhrase, int duration,
      EventHeader overrideEvent) {
    setErrorMessage("");

    PhoneB b = new PhoneB(timeout + 500, statusCode, reasonPhrase, duration, overrideEvent);
    b.start();
  }

  class PhoneB extends Thread {
    long timeout;

    int statusCode;

    String reasonPhrase;

    int duration;

    EventHeader overrideEvent;

    public PhoneB(long timeout, int statusCode, String reasonPhrase, int duration,
        EventHeader overrideEvent) {
      this.timeout = timeout;
      this.statusCode = statusCode;
      this.reasonPhrase = reasonPhrase;
      this.duration = duration;
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
          if (req.getMethod().equals(Request.REFER) == false) {
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

            LOG.trace("Sent response to REFER");
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

  /**
   * This method starts a thread that waits for up to 'timeout' milliseconds to receive a REFER and
   * if received, it sends NOTIFY with the given notify parms and 500ms later, it sends a response
   * with 'statusCode' and 'reasonPhrase' (if not null). This method waits 500 ms before returning
   * to allow the thread to get started and begin waiting for an incoming REFER. This method adds
   * 1000ms to the given timeout to account for these delays.
   * 
   * @param timeout - number of milliseconds to wait for the request
   * @param statusCode - use in the response to the request
   * @param reasonPhrase - if not null, use in the response
   * @param notifySubscriptionState
   * @param notifyTermReason
   * @param notifyBody
   * @param notifyTimeLeft
   */
  public void processReferSendNotifyBeforeResponse(long timeout, int statusCode,
      String reasonPhrase, String notifySubscriptionState, String notifyTermReason,
      String notifyBody, int notifyTimeLeft) {
    setErrorMessage("");

    PhoneB2 b = new PhoneB2(timeout + 1000, statusCode, reasonPhrase, notifySubscriptionState,
        notifyTermReason, notifyBody, notifyTimeLeft);
    b.start();
  }

  class PhoneB2 extends Thread {
    long timeout;

    int statusCode;

    String reasonPhrase;

    String notifySubscriptionState;

    String notifyTermReason;

    String notifyBody;

    int notifyTimeLeft;

    public PhoneB2(long timeout, int statusCode, String reasonPhrase,
        String notifySubscriptionState, String notifyTermReason, String notifyBody,
        int notifyTimeLeft) {
      this.timeout = timeout;
      this.statusCode = statusCode;
      this.reasonPhrase = reasonPhrase;
      this.notifySubscriptionState = notifySubscriptionState;
      this.notifyTermReason = notifyTermReason;
      this.notifyBody = notifyBody;
      this.notifyTimeLeft = notifyTimeLeft;
    }

    public void run() {
      try {
        phone.unlistenRequestMessage(); // clear out request queue
        phone.listenRequestMessage();

        RequestEvent inc_req = phone.waitRequest(timeout);
        while (inc_req != null) {
          receivedRequests.add(new SipRequest(inc_req));
          Request req = inc_req.getRequest();
          if (req.getMethod().equals(Request.REFER) == false) {
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

              // enable auth challenge handling
              phone.enableAuthorization(((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId());

              // save original event header
              eventHeader = (EventHeader) req.getHeader(EventHeader.NAME).clone();

              // send the NOTIFY before sending the REFER response
              Request notifyRequest = inc_req.getDialog().createRequest(SipRequest.NOTIFY);
              notifyRequest = addNotifyHeaders(notifyRequest, null, null, notifySubscriptionState,
                  notifyTermReason, notifyBody, notifyTimeLeft);
              notifyRequest.addHeader((Header) eventHeader.clone());
              if (sendNotify(notifyRequest, false) == false) {
                return;
              }

              // now send the REFER response
              dialog = sendResponse(trans, statusCode, reasonPhrase, toTag, req, -1);

              if (dialog == null) {
                phone.clearAuthorizations(
                    ((CallIdHeader) req.getHeader(CallIdHeader.NAME)).getCallId());
                return;
              }
            }

            LOG.trace("Sent response to REFER");
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

  /**
   * @return AllowEventsHeader
   * @throws ParseException
   */
  protected AllowEventsHeader getAllowEventsHeaderForResponse() throws ParseException {
    AllowEventsHeader ahdr = phone.getParent().getHeaderFactory().createAllowEventsHeader("refer");
    return ahdr;
  }

  /**
   * @return SupportedHeader
   * @throws ParseException
   */
  protected SupportedHeader getSupportedHeaderForResponse() throws ParseException {
    SupportedHeader shdr = phone.getParent().getHeaderFactory().createSupportedHeader("refer");
    return shdr;
  }

  /**
   * @return AcceptHeader
   * @throws ParseException
   */
  protected AcceptHeader getAcceptHeaderForResponse() throws ParseException {
    AcceptHeader accept =
        phone.getParent().getHeaderFactory().createAcceptHeader("message", "sipfrag");
    return accept;
  }

  /**
   * This method creates a NOTIFY message using the given parameters and sends it to the subscriber.
   * The request will be resent if challenged. Use this method only if you have previously called
   * processRefer() or processSubscribe(). Use this method if you don't care about checking the
   * response to the sent NOTIFY, otherwise use sendStatefulNotify().
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
   * this method only if you have previously called processRefer() or processSubscribe(). Use this
   * method if you don't care about checking the response to the sent NOTIFY, otherwise use
   * sendStatefulNotify().
   * 
   * @param subscriptionState - String to use as the subscription state. Overridden by sshdr.
   * @param termReason - used only when subscriptionState = TERMINATED. Overridden by sshdr.
   * @param body - NOTIFY body to put in the message
   * @param timeLeft - expiry in seconds to put in the NOTIFY message (used only when
   *        subscriptionState = ACTIVE or PENDING). Overridden by sshdr.
   * @param eventHdr - if not null, use this event header in the NOTIFY message
   * @param ssHdr - if not null, use this subscription state header in the NOTIFY message instead of
   *        building one from other parameters given.
   * @param accHdr - if not null, use this accept header. Otherwise build the default package one.
   * @param ctHdr - if not null, use this content type header. Otherwise build the default package
   *        one.
   * @param viaProxy If true, send the message to the proxy. In this case a Route header will be
   *        added. Else send the message as is.
   * 
   * @return true if successful, false otherwise (call getErrorMessage() for details).
   */
  public boolean sendNotify(String subscriptionState, String termReason, String body, int timeLeft,
      EventHeader eventHdr, SubscriptionStateHeader ssHdr, AcceptHeader accHdr,
      ContentTypeHeader ctHdr, boolean viaProxy) {
    return super.sendNotify(subscriptionState, termReason, body, timeLeft, eventHdr, ssHdr, accHdr,
        ctHdr, viaProxy);
  }

  /**
   * @return event type
   */
  protected String getEventType() {
    return "refer";
  }

  /**
   * @return content subtype
   */
  protected String getPackageContentSubType() {
    return "sipfrag";
  }

  /**
   * @return content type
   */
  protected String getPackageContentType() {
    return "message";
  }

}
