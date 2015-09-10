/*
 * Created on Apr 18, 2009
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

import java.text.ParseException;
import java.util.StringTokenizer;

import javax.sip.Dialog;
import javax.sip.address.SipURI;
import javax.sip.header.AcceptHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * The ReferSubscriber class represents the implicit subscription created by sending a REFER request
 * via the SipPhone.refer() method. This object is used by a test program to proceed through the
 * initial REFER-NOTIFY and any subsequent SUBSCRIBE-NOTIFY sequence(s) and to find out details at
 * any given time about the subscription such as the subscription state, amount of time left on the
 * subscription if still active, termination reason if terminated, errors encountered during
 * received REFER/SUBSCRIBE responses and incoming NOTIFY message validation, and details of any
 * received responses and requests if needed by the test program.
 * 
 * <p>
 * Please read the SipUnit User Guide, Event Subscription section for the NOTIFY-receiving side (at
 * least the operation overview part) for information on how to use the methods of this class and
 * its superclass.
 * 
 * <p>
 * As in the case of other objects like SipPhone, SipCall, etc., operation-invoking methods of this
 * class return an object or true if successful. In case of an error or caller-specified timeout, a
 * null object or a false is returned. The getErrorMessage(), getReturnCode() and getException()
 * methods may be used for further diagnostics. See SipPhone or SipActionObject javadoc for more
 * details on using these methods.
 * 
 * 
 * @author Becky McElroy
 * 
 */
public class ReferSubscriber extends EventSubscriber {
  private SipURI referToUri;

  /**
   * A constructor for this class. Used internally by SipUnit. Test programs should call one of the
   * SipPhone.refer() methods to create a refer subscription.
   */
  public ReferSubscriber(String refereeUri, SipURI referToUri, SipPhone parent)
      throws ParseException {
    this(refereeUri, referToUri, parent, null);
  }

  /**
   * A constructor for this class. Used internally by SipUnit. Test programs should call one of the
   * SipPhone.refer() methods to create a refer subscription.
   */
  public ReferSubscriber(String refereeUri, SipURI referToUri, SipPhone parent, Dialog dialog)
      throws ParseException {
    super(refereeUri, parent, dialog);
    this.referToUri = referToUri;
  }

  /**
   * Removes this object from the SipPhone referrer list. No check is done. You should unsubscribe()
   * before calling this method.
   */
  public void dispose() {
    parent.removeRefer(this);
  }

  /**
   * This method is the same as EventSubscriber.createSubscribeMessage() except there's no need for
   * the caller to supply the eventType parameter.
   * 
   * @param duration the duration in seconds to put in the SUBSCRIBE message.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later when calling refresh() or unsubscribe().
   * @return a SUBSCRIBE request
   */
  public Request createSubscribeMessage(int duration, String eventId) {
    return super.createSubscribeMessage(duration, eventId, "refer");
  }

  protected boolean expiresResponseHeaderApplicable() {
    if (lastSentRequest.getMethod().equals(SipRequest.REFER)) {
      return false;
    }

    return true;
  }

  protected void checkEventType(EventHeader receivedHdr) throws SubscriptionError {
    String event = receivedHdr.getEventType();
    if (event.equals("refer") == false) {
      throw new SubscriptionError(SipResponse.BAD_EVENT,
          "received a refer NOTIFY event header containing unknown event = " + event);
    }
  }

  protected void updateEventInfo(Request request) throws SubscriptionError {
    // TODO future for multiple refers in a dialog:
    // NOTE FOR NOTIFY event ID validation, refresh() and unsubscribe():
    // A REFER creates an implicit subscription sharing the dialog
    // identifiers in the REFER request. If more than one REFER is issued
    // in the same dialog (a second attempt at transferring a call for
    // example), the dialog identifiers do not provide enough information to
    // associate the resulting NOTIFYs with the proper REFER.
    //
    // Thus, for the second and subsequent REFER requests a UA receives in a
    // given dialog, it MUST include an id parameter[2] in the Event header
    // field of each NOTIFY containing the sequence number (the number from
    // the CSeq header field value) of the REFER this NOTIFY is associated
    // with. This id parameter MAY be included in NOTIFYs to the first
    // REFER a UA receives in a given dialog. A SUBSCRIBE sent to refresh
    // or terminate this subscription MUST contain this id parameter.

    byte[] bodyBytes = request.getRawContent();
    if (bodyBytes == null) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST, "Refer NOTIFY has no body");
    }

    // Each NOTIFY MUST contain a body of type "message/sipfrag"
    ContentTypeHeader ct = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);
    if (ct == null) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "Refer NOTIFY body has bytes but no content type header was received");
    }

    if (ct.getContentType().equals("message") == false) {
      throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
          "received Refer NOTIFY body with unsupported content type = " + ct.getContentType());
    } else if (ct.getContentSubType().equals("sipfrag") == false) {
      throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
          "received Refer NOTIFY body with unsupported content subtype = "
              + ct.getContentSubType());
    }

    // The body of a NOTIFY MUST begin with a SIP Response Status-Line
    // IE: Status-Line = SIP-Version SP Status-Code SP Reason-Phrase
    // The Status-Code is a 3-digit integer result code, 1xx - 6xx
    // IE: SIP/2.0 200 OK

    StringTokenizer tok = new StringTokenizer(new String(bodyBytes));
    if (tok.countTokens() < 3) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "received Refer NOTIFY body with less than the number of words in a SIP Response Status-Line, received body: "
              + new String(bodyBytes));
    }

    String sipVersion = tok.nextToken();
    String statusCode = tok.nextToken();

    if (sipVersion.startsWith("SIP/") == false) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "received Refer NOTIFY body Status-Line SIP-Version is invalid, received body: "
              + new String(bodyBytes));
    }

    try {
      int status = Integer.valueOf(statusCode);
      if (status < 100 || status > 699) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "received Refer NOTIFY body Status-Line Status-Code is out of range, received body: "
                + new String(bodyBytes));
      }
    } catch (NumberFormatException e) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "received Refer NOTIFY body Status-Line Status-Code is non-numeric, received body: "
              + new String(bodyBytes));
    }

    return;
  }

  protected AcceptHeader getUnsupportedMediaAcceptHeader() throws ParseException {
    return parent.getHeaderFactory().createAcceptHeader("message", "sipfrag");
  }

  /**
   * This method initiates a SUBSCRIBE/NOTIFY sequence for the purpose of refreshing this
   * subscription. This method creates a SUBSCRIBE request message based on the parameters passed
   * in, sends out the request, and waits for a first response to be received. It saves the received
   * response and checks for a "proceedable" (positive) status code value. Positive response status
   * codes include any of the following: provisional (status / 100 == 1), UNAUTHORIZED,
   * PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED. Any other status code, or a response timeout or
   * any other error, is considered fatal to this refresh operation.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * If this method returns true, it means a positive response was received. You can find out about
   * the response by calling this object's getReturnCode() and/or getCurrentResponse() or
   * getLastReceivedResponse() methods. Your next step will be to call the processResponse() method
   * to proceed with the refresh sequence. See the processResponse() javadoc for more details.
   * 
   * <p>
   * If this method returns false, it means this refresh operation has failed. Call the usual
   * SipUnit failed-operation methods to find out what happened (ie, getErrorMessage(),
   * getReturnCode(), and/or getException() methods). The getReturnCode() method will tell you the
   * response status code that was received from the network (unless it is an internal SipUnit error
   * code, see the SipSession javadoc for more on that).
   * 
   * @param duration the duration in seconds to put in the SUBSCRIBE message and reset the
   *        subscription time left to. If it is 0, this is an unsubscribe.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later on another refresh method call or on
   *        calling unsubscribe().
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return true if the refresh operation is successful so far, false otherwise. See details above.
   */
  public boolean refresh(int duration, String eventId, long timeout) {
    Request req = createSubscribeMessage(duration, eventId);

    if (req == null) {
      return false;
    }

    req.removeHeader(ProxyAuthorizationHeader.NAME);

    return refresh(req, timeout);

  }

  /**
   * This method is the same as refresh(duration, eventId, timeout) except that the SUBSCRIBE
   * duration sent will be however much time is left on the current subscription. If time left on
   * the subscription &lt;= 0, unsubscribe occurs.
   */
  public boolean refresh(String eventId, long timeout) {
    return refresh(getTimeLeft(), eventId, timeout);
  }

  /**
   * This method is the same as refresh(duration, eventId, timeout) except that the eventId remains
   * unchanged from whatever it already was.
   */
  public boolean refresh(int duration, long timeout) {
    return refresh(duration, getEventId(), timeout);
  }

  /**
   * This method is the same as refresh(duration, eventId, timeout) except that the eventId remains
   * unchanged from whatever it already was and the SUBSCRIBE duration sent will be however much
   * time is left on the current subscription. If time left on the subscription &lt;= 0, unsubscribe
   * occurs.
   */
  public boolean refresh(long timeout) {
    return refresh(getTimeLeft(), getEventId(), timeout);
  }

  /**
   * This method is the same as refresh(duration, eventId, timeout) except that instead of creating
   * the SUBSCRIBE request from parameters passed in, the given request message parameter is used
   * for sending out the SUBSCRIBE message.
   * <p>
   * The Request parameter passed into this method should come from calling createSubscribeMessage()
   * - see that javadoc. The subscription duration is reset to the passed in Request's expiry value.
   * If it is 0, this is an unsubscribe. The event "id" in the given request will be used
   * subsequently (for error checking SUBSCRIBE responses and NOTIFYs from the server as well as for
   * sending subsequent SUBSCRIBEs).
   */
  public boolean refresh(Request req, long timeout) {
    return refreshSubscription(req, timeout, parent.getProxyHost() != null);
  }

  /**
   * This method initiates a SUBSCRIBE/NOTIFY sequence to terminate the subscription unless the
   * subscription is already terminated.
   * <p>
   * If the subscription is active when this method is called, this method creates a SUBSCRIBE
   * request message based on the parameters passed in, sends out the request, and waits for a first
   * response to be received. It saves the received response and checks for a "proceedable"
   * (positive) status code value. Positive response status codes include any of the following:
   * provisional (status / 100 == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED.
   * Any other status code, or a response timeout or any other error, is considered fatal to the
   * unsubscribe operation.
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * <p>
   * If this method returns true, it means a positive response was received or the unsubscribe
   * sequence was not required. In order for you to know which is the case (and whether or not to
   * proceed forward with the SUBSCRIBE/NOTIFY sequence processing), call isRemovalComplete(). It
   * will tell you if an unsubscribe sequence was initiated or not. If not, you are done. Otherwise
   * you can find out about the positive response by calling this object's getReturnCode() and/or
   * getCurrentResponse() or getLastReceivedResponse() methods. Your next step will be to call the
   * processResponse() method to proceed with the unsubscribe sequence. See the processResponse()
   * javadoc for more details.
   * <p>
   * If this method returns false, it means the unsubscribe operation was required and it failed.
   * Call the usual SipUnit failed-operation methods to find out what happened (ie,
   * getErrorMessage(), getReturnCode(), and/or getException() methods). The getReturnCode() method
   * will tell you the response status code that was received from the network (unless it is an
   * internal SipUnit error code, see the SipSession javadoc for more on that).
   * 
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently, for error checking the
   *        unSUBSCRIBE response and NOTIFY from the server.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return true if the unsubscribe operation is successful so far or wasn't needed, false
   *         otherwise. See more details above.
   */
  public boolean unsubscribe(String eventId, long timeout) {
    Request req = createSubscribeMessage(0, eventId);

    if (req == null) {
      return false;
    }

    req.removeHeader(ProxyAuthorizationHeader.NAME);

    return unsubscribe(req, timeout);
  }

  /**
   * This method is the same as unsubscribe(eventId, timeout) except that no event "id" parameter
   * will be included in the unSUBSCRIBE message. When error checking the SUBSCRIBE response and
   * NOTIFY from the server, no event "id" parameter will be expected.
   */
  public boolean unsubscribe(long timeout) {
    return unsubscribe((String) null, timeout);
  }

  /**
   * This method is the same as unsubscribe(eventId, timeout) except that instead of creating the
   * SUBSCRIBE request from parameters passed in, the given request message parameter is used for
   * sending out the SUBSCRIBE message if the subscription is active.
   * <p>
   * The Request parameter passed into this method should come from calling createSubscribeMessage()
   * - see that javadoc. The event "id" in the given request will be used subsequently for error
   * checking the SUBSCRIBE response and NOTIFY request from the server.
   */
  public boolean unsubscribe(Request req, long timeout) {
    initErrorInfo();

    return endSubscription(req, timeout, parent.getProxyHost() != null, "Unsubscribe");
  }

  /**
   * Get the referTo URI for this subscription.
   * 
   * @return Returns the referToUri.
   */
  public SipURI getReferToUri() {
    return referToUri;
  }

  protected void validateOkAcceptedResponse(Response response) throws SubscriptionError {
    if (!expiresResponseHeaderApplicable()) {
      if (response.getExpires() != null) {
        throw new SubscriptionError(SipSession.FAR_END_ERROR,
            "expires header was received in the REFER response");
      }
    }

    if (!eventForMe(response, lastSentRequest)) {
      throw new SubscriptionError(SipSession.FAR_END_ERROR,
          "incorrect event id received in the REFER response");
    }
  }

  protected void validateSubscriptionStateHeader(SubscriptionStateHeader subsHdr)
      throws SubscriptionError {
    if (!subsHdr.getState().equalsIgnoreCase(SubscriptionStateHeader.TERMINATED)) {
      if (subsHdr.getExpires() <= 0) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "invalid expires value in NOTIFY SubscriptionStateHeader: " + subsHdr.getExpires());
      }
    }
  }

}
