/*
 * Created on Sep 10, 2005
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
package org.cafesip.sipunit;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

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
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.Header;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.SubscriptionStateHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.xml.bind.JAXBContext;

import org.cafesip.sipunit.presenceparser.pidf.Contact;
import org.cafesip.sipunit.presenceparser.pidf.Note;
import org.cafesip.sipunit.presenceparser.pidf.Presence;
import org.cafesip.sipunit.presenceparser.pidf.Tuple;

/**
 * The Subscription class represents a buddy from a SipPhone buddy list or a
 * single-shot fetch performed by a SipPhone. The Subscription object is used by
 * a test program to proceed through the SUBSCRIBE-NOTIFY sequence(s) and to
 * find out details at any given time about the subscription such as the
 * subscription state, amount of time left on the subscription if still active,
 * termination reason if terminated, current or last known presence
 * status/information (tuples/devices, notes, etc.), errors encountered during
 * received NOTIFY message validation, and details of received responses and
 * requests if needed.
 * <p>
 * Please read the SipUnit User Guide, Presence section (at least the operation
 * overview part) for information on how to use SipUnit presence capabilities.
 * <p>
 * As in the case of other objects like SipPhone, SipCall, etc.,
 * operation-invoking methods of this class return an object or true if
 * successful. In case of an error or caller-specified timeout, a null object or
 * a false is returned. The getErrorMessage(), getReturnCode() and
 * getException() methods may be used for further diagnostics. See SipPhone or
 * SipActionObject javadoc for more details on using these methods.
 * 
 * @author Becky McElroy
 * 
 */
public class Subscription implements MessageListener, SipActionObject
{
    private String buddyUri; // The buddy's URI string (ex: sip:bob@nist.gov)

    private Address buddyAddress;

    private String subscriptionState = SubscriptionStateHeader.PENDING;

    private String terminationReason;

    /*
     * List of zero or more PresenceDeviceInfo objects (active devices) for this
     * Subscription buddy/watchee, indexed by the IDs received in the NOTIFY
     * tuples
     */
    private HashMap<String, PresenceDeviceInfo> devices = new HashMap<String, PresenceDeviceInfo>();

    /*
     * List of zero or more PresenceNote objects received in the NOTIFY body
     */
    private ArrayList<PresenceNote> presenceNotes = new ArrayList<PresenceNote>();

    /*
     * List of zero or more Object received in a NOTIFY message
     */
    private ArrayList<Object> presenceExtensions = new ArrayList<Object>();

    private long projectedExpiry = 0;

    private SipPhone parent;

    private Dialog dialog;

    private SipTransaction transaction;

    // caller-invoked status info

    private int returnCode = -1;

    private String errorMessage = "";

    private Throwable exception;

    // misc message info

    private String myTag;

    private CSeqHeader subscribeCSeq;

    private CSeqHeader notifyCSeq;

    private CallIdHeader callId;

    /*
     * list of SipResponse
     */
    private LinkedList<SipResponse> receivedResponses = new LinkedList<SipResponse>();

    /*
     * list of SipRequest
     */
    private LinkedList<SipRequest> receivedRequests = new LinkedList<SipRequest>();

    /*
     * for wait operations
     */
    private LinkedList<RequestEvent> reqEvents = new LinkedList<RequestEvent>();

    private BlockObject responseBlock = new BlockObject();

    private ResponseEvent currentSubscribeResponse;

    /*
     * misc
     */

    private Request lastSentRequest;

    private Object lastSubscribeLock = new Object(); // lock object for

    // lastSentRequest - test
    // program side writes to and reads
    // lastSentRequest, network
    // side needs to read it

    private boolean removalComplete = false;

    private LinkedList<String> eventErrors = new LinkedList<String>();

    protected Subscription(String uri, SipPhone parent) throws ParseException
    {
        this.buddyUri = uri.trim();
        this.parent = parent;
        myTag = parent.generateNewTag();

        buddyAddress = parent.getAddressFactory().createAddress(this.buddyUri);
    }

    protected boolean startSubscription(Request req, long timeout,
            boolean viaProxy)
    {
        initErrorInfo();
        SipStack.trace("Starting Subscription for URI " + buddyUri);

        if (sendSubscribe(req, viaProxy) == true)
        {
            if (waitNextPositiveResponse(timeout) == true)
            {
                return true;
            }
        }

        /*
         * Non-200 class final responses indicate that no subscription or dialog
         * has been created, and no subsequent NOTIFY message will be sent. All
         * non-200 class responses (with the exception of "489", described
         * herein) have the same meanings and handling as described in SIP [1]
         */

        SipStack.trace("Subscription startup failed : " + getErrorMessage());

        return false;
    }

    protected boolean refresh(Request req, long timeout, boolean viaProxy)
    {
        initErrorInfo();
        SipStack.trace("Refreshing Subscription for URI " + buddyUri
                + ", previous time left = " + getTimeLeft());

        if (sendSubscribe(req, viaProxy) == true)
        {
            if (waitNextPositiveResponse(timeout) == true)
            {
                return true;
            }

            if (getReturnCode() == SipResponse.CALL_OR_TRANSACTION_DOES_NOT_EXIST) // 481
            {
                /*
                 * If a SUBSCRIBE request to refresh a subscription receives a
                 * "481" response, this indicates that the subscription has been
                 * terminated and that the subscriber did not receive
                 * notification of this fact. In this case, the subscriber
                 * should consider the subscription invalid.
                 */

                subscriptionState = SubscriptionStateHeader.TERMINATED;

                SipStack.trace("Terminating Subscription for URI " + buddyUri
                        + " due to received SUBSCRIBE response code = "
                        + getReturnCode());
            }

            /*
             * If a SUBSCRIBE request to refresh a subscription fails with a
             * non-481 response, the original subscription is still considered
             * valid for the duration of the most recently known "Expires" value
             * as negotiated by SUBSCRIBE and its response, or as communicated
             * by NOTIFY in the "Subscription-State" header "expires" parameter.
             */
        }

        SipStack.trace("Subscription refresh failed : " + getErrorMessage());

        return false;
    }

    protected boolean endSubscription(Request req, long timeout,
            boolean viaProxy)
    {
        initErrorInfo();
        setRemovalComplete(true);

        if ((subscriptionState.equals(SubscriptionStateHeader.ACTIVE))
                || (subscriptionState.equals(SubscriptionStateHeader.PENDING)))
        {
            SipStack.trace("Ending subscription for URI " + buddyUri
                    + ", time left = " + getTimeLeft());

            subscriptionState = SubscriptionStateHeader.TERMINATED;
            terminationReason = "Buddy removed from contact list";

            if (sendSubscribe(req, viaProxy) == true)
            {
                if (waitNextPositiveResponse(timeout) == true)
                {
                    setRemovalComplete(false);
                    return true;
                }
            }

            SipStack.trace("Subscription termination failed : "
                    + getErrorMessage());

            return false;
        }

        return true;
    }

    protected boolean fetchSubscription(Request req, long timeout,
            boolean viaProxy)
    {
        initErrorInfo();
        SipStack.trace("Fetching presence information for URI " + buddyUri);

        subscriptionState = SubscriptionStateHeader.TERMINATED;
        terminationReason = "Presence fetch";

        if (sendSubscribe(req, viaProxy) == true)
        {
            if (waitNextPositiveResponse(timeout) == true)
            {
                return true;
            }
        }

        /*
         * Non-200 class final responses indicate that no subscription or dialog
         * has been created, and no subsequent NOTIFY message will be sent. All
         * non-200 class responses (with the exception of "489", described
         * herein) have the same meanings and handling as described in SIP [1]
         */

        SipStack.trace("Subscription fetch failed : " + getErrorMessage());

        return false;
    }

    private boolean waitNextPositiveResponse(long timeout)
    {
        EventObject event = waitSubscribeResponse(timeout);
        if (event != null)
        {
            if (event instanceof TimeoutEvent)
            {
                setReturnCode(SipSession.TIMEOUT_OCCURRED);
                setErrorMessage("The SUBSCRIBE sending transaction timed out.");
            }
            else if (event instanceof ResponseEvent)
            {
                ResponseEvent resp_event = (ResponseEvent) event;
                int status = resp_event.getResponse().getStatusCode();
                setReturnCode(status);
                setCurrentSubscribeResponse(resp_event);

                if ((status / 100 == 1) || (status == Response.UNAUTHORIZED)
                        || (status == Response.PROXY_AUTHENTICATION_REQUIRED)
                        || (status == SipResponse.OK)
                        || (status == SipResponse.ACCEPTED))
                {
                    return true;
                }

                // if we're here, we received a final, fatal retcode

                setErrorMessage("SUBSCRIBE response status: " + status
                        + ", reason: "
                        + resp_event.getResponse().getReasonPhrase());
            }
        }

        return false;
    }

    // TODO - every x seconds, check for auto-renewal of subscriptions
    // for add/refreshBuddy, allow bool parameter auto-refresh;update
    // all subscriptions' timeLeft

    /**
     * This method creates and returns to the caller the next SUBSCRIBE message
     * that would be sent out, so that the user can modify it before it gets
     * sent out (ie, to introduce errors - insert incorrect content, remove
     * content, etc.). The idea is to call this method which will create the
     * SUBSCRIBE request correctly, then modify the returned request yourself,
     * then call one of the SipPhone buddy methods (refreshBuddy(),
     * removeBuddy()) that take Request as a parameter, which will result in the
     * request being sent out.
     * <p>
     * If you don't need to modify the next SUBSCRIBE request to introduce
     * errors, don't bother with this method and just call one of the
     * alternative SipPhone buddy methods (refreshBuddy(), removeBuddy(), etc.)
     * that doesn't take a Request parameter - it will create and send the
     * request in one step.
     * <p>
     * Effective use of this method requires knowledge of the JAIN SIP API
     * Request and Header classes. Use those to modify the request returned by
     * this method.
     * <p>
     * Note that the SipPhone methods addBuddy() and fetchPresenceInfo() do not
     * have any signatures that take Request as a parameter. The reason is
     * because a correct initial SUBSCRIBE request is needed to initialize the
     * Subscription object properly. If you want to send out a bad initial
     * SUBSCRIBE message to see what your test target does, use the SipPhone's
     * base class SipSession (low-level) methods to send the bad request and get
     * the resulting response (and that should be the end of that test method).
     * 
     * @param duration
     *            the duration in seconds to put in the SUBSCRIBE message.
     * @param eventId
     *            the event "id" to use in the SUBSCRIBE message, or null for no
     *            event "id" parameter. Whatever is indicated here will be used
     *            subsequently (for error checking SUBSCRIBE responses and
     *            NOTIFYs from the server as well as for sending subsequent
     *            SUBSCRIBEs) unless changed by the caller later on another
     *            SipPhone buddy method call (refreshBuddy(), removeBuddy(),
     *            fetch, etc.).
     * @return a SUBSCRIBE Request object if creation is successful, null
     *         otherwise.
     */
    public Request createSubscribeMessage(int duration, String eventId)
    {
        initErrorInfo();

        try
        {
            SipStack.trace("Creating SUBSCRIBE message with duration "
                    + duration + ", event id = " + eventId);
            AddressFactory addr_factory = parent.getAddressFactory();
            HeaderFactory hdr_factory = parent.getHeaderFactory();

            Request req = null;
            Request last_sent_request = getLastSentRequest();

            if (last_sent_request == null) // first time sending a request
            {
                // build the request

                URI request_uri = addr_factory.createURI(buddyUri);
                callId = parent.getNewCallIdHeader(); // get a new call Id
                String method = Request.SUBSCRIBE;

                FromHeader from_header = hdr_factory.createFromHeader(parent
                        .getAddress(), myTag);
                ToHeader to_header = hdr_factory.createToHeader(buddyAddress,
                        null);

                subscribeCSeq = hdr_factory.createCSeqHeader(
                        subscribeCSeq == null ? 1 : (subscribeCSeq
                                .getSeqNumber() + 1), method);

                MaxForwardsHeader max_forwards = hdr_factory
                        .createMaxForwardsHeader(SipPhone.MAX_FORWARDS_DEFAULT);

                ArrayList<ViaHeader> via_headers = parent.getViaHeaders();

                req = parent.getMessageFactory().createRequest(request_uri,
                        method, callId, subscribeCSeq, from_header, to_header,
                        via_headers, max_forwards);

                req.addHeader((ContactHeader) parent.getContactInfo()
                        .getContactHeader().clone());

                String proxy_host = parent.getProxyHost();
                if (proxy_host == null)
                {
                    // local: add a route header to loop the message back to our
                    // stack
                    SipURI route_uri = parent.getAddressFactory().createSipURI(
                            null, parent.getStackAddress());
                    route_uri.setLrParam();
                    route_uri.setPort(parent.getParent().getSipProvider()
                            .getListeningPoints()[0].getPort());
                    route_uri.setTransportParam(parent.getParent()
                            .getSipProvider().getListeningPoints()[0]
                            .getTransport());
                    route_uri.setSecure(((SipURI) request_uri).isSecure());

                    Address route_address = parent.getAddressFactory()
                            .createAddress(route_uri);
                    req.addHeader(parent.getHeaderFactory().createRouteHeader(
                            route_address));
                }

                SipStack.trace("We have created this first SUBSCRIBE: " + req);
            }
            else if (dialog.getState() == null) // we've sent before but not
            // heard back
            {
                req = (Request) last_sent_request.clone();

                subscribeCSeq = hdr_factory.createCSeqHeader(subscribeCSeq
                        .getSeqNumber() + 1, Request.SUBSCRIBE);
                req.setHeader(subscribeCSeq);

                SipStack.trace("We have created this first resend SUBSCRIBE: "
                        + req);
            }
            else
            // dialog is established enough to use
            {
                req = dialog.createRequest(Request.SUBSCRIBE);
                SipStack.trace("Dialog has created this subsequent SUBSCRIBE: "
                        + req);
            }

            // set other needed request info
            EventHeader hdr = hdr_factory.createEventHeader("presence");
            if (eventId != null)
            {
                hdr.setEventId(eventId);
            }
            req.setHeader(hdr);

            req.addHeader(hdr_factory.createAllowEventsHeader("presence"));
            req.setExpires(hdr_factory.createExpiresHeader(duration));
            parent.addAuthorizations(callId.getCallId(), req);

            return req;
        }
        catch (Exception e)
        {
            setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
            setException(e);
            setErrorMessage("Exception: " + e.getClass().getName() + ": "
                    + e.getMessage());
        }

        return null;
    }

    private boolean sendSubscribe(Request req, boolean viaProxy)
    {
        if (req == null)
        {
            setReturnCode(SipSession.INVALID_ARGUMENT);
            setErrorMessage("Null request given for SUBSCRIBE message");
            return false;
        }

        synchronized (responseBlock)
        {
            // clear open transaction if any
            if (transaction != null)
            {
                parent.clearTransaction(transaction);
                transaction = null;
            }
            else if (dialog == null) // first time
            {
                parent.enableAuthorization(callId.getCallId());
            }

            try
            {
                // save and send the message

                setLastSentRequest(req);

                transaction = parent.sendRequestWithTransaction(req, viaProxy,
                        dialog, this);
                if (transaction == null)
                {
                    setReturnCode(parent.getReturnCode());
                    setErrorMessage(parent.getErrorMessage());
                    setException(parent.getException());

                    return false;
                }

                SipStack.trace("Sent REQUEST: " + req.toString());

                dialog = transaction.getClientTransaction().getDialog();

                if (req.getExpires() != null)
                {
                    int expires = req.getExpires().getExpires();
                    if ((getTimeLeft() == 0) || (getTimeLeft() > expires))
                    {
                        setTimeLeft(expires);
                    }
                }

                SipStack.trace("Sent SUBSCRIBE to "
                        + dialog.getRemoteParty().getURI().toString() + " for "
                        + buddyUri);

                return true;
            }
            catch (Exception e)
            {
                setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
                setException(e);
                setErrorMessage("Exception: " + e.getClass().getName() + ": "
                        + e.getMessage());
            }

        }

        return false;

    } /*
         * @see org.cafesip.sipunit.MessageListener#processEvent(java.util.EventObject)
         */

    public void processEvent(EventObject event)
    {
        if (event instanceof RequestEvent)
        {
            processRequest((RequestEvent) event);
        }
        else if (event instanceof ResponseEvent)
        {
            processResponse((ResponseEvent) event);
        }
        else if (event instanceof TimeoutEvent)
        {
            processTimeout((TimeoutEvent) event);
        }
        else
        {
            System.err
                    .println("Subscription.processEvent() - invalid event type received: "
                            + event.getClass().getName()
                            + ": "
                            + event.toString());
        }
    }

    // test prog may or may not yet be blocked waiting on this - watch
    // synchronization
    private void processRequest(RequestEvent requestEvent)
    {
        Request request = requestEvent.getRequest();

        // check CSEQ# - if no higher than current CSEQ, discard message

        CSeqHeader rcv_seq_hdr = (CSeqHeader) request
                .getHeader(CSeqHeader.NAME);
        if (rcv_seq_hdr == null)
        {
            Subscription.sendResponse(parent, requestEvent,
                    SipResponse.BAD_REQUEST, "no CSEQ header received");

            String err = "*** NOTIFY REQUEST ERROR ***  (" + buddyUri
                    + ") - no CSEQ header received";
            synchronized (eventErrors)
            {
                eventErrors.addLast(err);
            }
            SipStack.trace(err);
            return;
        }

        if (notifyCSeq != null) // This is not the first NOTIFY
        {
            if (rcv_seq_hdr.getSeqNumber() <= notifyCSeq.getSeqNumber())
            {
                Subscription.sendResponse(parent, requestEvent, SipResponse.OK,
                        "OK");

                SipStack.trace("Received NOTIFY CSEQ "
                        + rcv_seq_hdr.getSeqNumber()
                        + " not new, discarding message");
                return;
            }
        }

        notifyCSeq = rcv_seq_hdr;

        synchronized (this)
        {
            receivedRequests.addLast(new SipRequest(request));
            reqEvents.addLast(requestEvent);
            this.notify();
        }
    }

    private void processResponse(ResponseEvent responseEvent)
    {
        synchronized (responseBlock)
        {
            if (transaction == null)
            {
                String errstring = "*** SUBSCRIBE RESPONSE ERROR ***  ("
                        + buddyUri
                        + ") : unexpected null transaction at response reception for request: "
                        + responseEvent.getClientTransaction().getRequest()
                                .toString();
                synchronized (eventErrors)
                {
                    eventErrors.addLast(errstring);
                }
                SipStack.trace(errstring);
                return;
            }

            receivedResponses.addLast(new SipResponse(responseEvent
                    .getResponse()));
            transaction.getEvents().addLast(responseEvent);
            responseBlock.notifyEvent();
        }
    }

    private void processTimeout(TimeoutEvent timeout)
    {
        // this method is called if there was no response to the SUBSCRIBE
        // request we sent

        synchronized (responseBlock)
        {
            if (transaction == null)
            {
                String errstring = "*** SUBSCRIBE RESPONSE ERROR ***  ("
                        + buddyUri
                        + ") : unexpected null transaction at event timeout for request: "
                        + timeout.getClientTransaction().getRequest()
                                .toString();
                synchronized (eventErrors)
                {
                    eventErrors.addLast(errstring);
                }
                SipStack.trace(errstring);
                return;
            }

            transaction.getEvents().addLast(timeout);
            responseBlock.notifyEvent();
        }
    }

    /**
     * This method processes the initial SUBSCRIBE response received after
     * sending a SUBSCRIBE request and takes the SUBSCRIBE transaction to its
     * completion by collecting any remaining responses from the far end for
     * this transaction, handling authentication challenge if needed, and
     * updating the Subscription object with the results of the SUBSCRIBE
     * sequence.
     * <p>
     * Call this method after calling any of the SipPhone buddy/fetch methods
     * that initiate a SUBSCRIBE sequence (addBuddy(), refreshBuddy(),
     * fetchPresenceInfo(), etc.) and getting back a positive indication.
     * <p>
     * If a success indication is returned by this method, you may call other
     * methods on this object to find out the result of the SUBSCRIBE sequence:
     * isSubscriptionXyz() for subscription state information, getTimeLeft()
     * which is set based on the received response.
     * <p>
     * The next step is to call waitNotify() to retrieve/wait for the NOTIFY
     * request from the server.
     * 
     * @param timeout
     *            The maximum amount of time to wait for the SUBSCRIBE
     *            transaction to complete, in milliseconds. Use a value of 0 to
     *            wait indefinitely.
     * @return true if the response(s) received were valid and no errors were
     *         encountered, false otherwise.
     */
    public boolean processSubscribeResponse(long timeout)
    {
        initErrorInfo();
        String cseq_str = "";

        try
        {
            if (currentSubscribeResponse == null)
            {
                throw new StatusReport(SipSession.INVALID_OPERATION,
                        "There is no outstanding subscribe response to process");
            }

            Response response = currentSubscribeResponse.getResponse();
            int status = response.getStatusCode();
            cseq_str = "CSEQ "
                    + ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                            .getSeqNumber();

            SipStack.trace("Processing SUBSCRIBE " + cseq_str
                    + " response with status code " + status + " from "
                    + buddyUri);

            while (status != SipResponse.OK)
            {
                if (status == SipResponse.ACCEPTED)
                {
                    break;
                }

                if (status / 100 == 1) // provisional
                {
                    if (waitNextPositiveResponse(timeout) == false)
                    {
                        SipStack.trace("*** SUBSCRIBE RESPONSE ERROR ***  ("
                                + cseq_str + ", " + buddyUri + ") - "
                                + getErrorMessage());
                        return false;
                    }

                    response = currentSubscribeResponse.getResponse();
                    status = response.getStatusCode();
                    cseq_str = "CSEQ "
                            + ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                                    .getSeqNumber();

                    SipStack.trace("Processing SUBSCRIBE " + cseq_str
                            + " response with status code " + status + " from "
                            + buddyUri);

                    continue;
                }
                else if ((status == Response.UNAUTHORIZED)
                        || (status == Response.PROXY_AUTHENTICATION_REQUIRED))
                {
                    // resend the request with the required authorization
                    authorizeSubscribe(response, getLastSentRequest());

                    // get the new response
                    if (waitNextPositiveResponse(timeout) == false)
                    {
                        SipStack.trace("*** SUBSCRIBE RESPONSE ERROR ***  ("
                                + cseq_str + ", " + buddyUri + ") - "
                                + getErrorMessage());
                        return false;
                    }

                    response = currentSubscribeResponse.getResponse();
                    status = response.getStatusCode();
                    cseq_str = "CSEQ "
                            + ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                                    .getSeqNumber();

                    SipStack.trace("Processing SUBSCRIBE " + cseq_str
                            + " response with status code " + status + " from "
                            + buddyUri);

                    continue;
                }
                else
                {
                    throw new StatusReport(SipSession.FAR_END_ERROR,
                            "Unexpected SUBSCRIBE response code encountered : "
                                    + status);
                }
            }

            // status is OK or accepted

            if (subscriptionState == SubscriptionStateHeader.TERMINATED)
            {
                return true;
            }

            // the subscription is alive - check expires header

            if (response.getExpires() == null)
            {
                throw new StatusReport(SipSession.FAR_END_ERROR,
                        "no expires header received");
            }

            int expires = response.getExpires().getExpires();
            validateExpires(expires, false);

            // use the received expiry time as the subscription duration
            SipStack.trace(buddyUri + ": received expiry = " + expires
                    + ", updating current expiry which was " + getTimeLeft());

            setTimeLeft(expires);

            // set subscription state
            if (status == SipResponse.OK)
            {
                subscriptionState = SubscriptionStateHeader.ACTIVE;
            }

            return true;
        }
        catch (StatusReport e)
        {
            String err = "*** SUBSCRIBE RESPONSE ERROR ***  (" + cseq_str
                    + ", " + buddyUri + ") - " + e.getReason();
            SipStack.trace(err);

            setReturnCode(e.getStatusCode());
            setErrorMessage(err);
        }

        return false;

    }

    private void authorizeSubscribe(Response resp, Request msg)
            throws StatusReport
    {
        // modify the request to include user authorization info and resend

        msg = parent.processAuthChallenge(resp, msg);
        if (msg == null)
        {
            throw new StatusReport(parent.getReturnCode(),
                    "error responding to authentication challenge: "
                            + parent.getErrorMessage());
        }

        try
        {
            // bump up the sequence number
            subscribeCSeq.setSeqNumber(subscribeCSeq.getSeqNumber() + 1);
            msg.setHeader(subscribeCSeq);

            synchronized (responseBlock)
            {
                // send the message
                transaction = parent.sendRequestWithTransaction(msg, false,
                        null, this);

                if (transaction == null)
                {
                    throw new StatusReport(parent.getReturnCode(),
                            "error resending SUBSCRIBE with authorization: "
                                    + parent.getErrorMessage());
                }

                dialog = transaction.getClientTransaction().getDialog();

                SipStack.trace("Resent REQUEST: " + msg.toString());
                SipStack.trace("Resent SUBSCRIBE to "
                        + dialog.getRemoteParty().getURI().toString() + " for "
                        + buddyUri);
            }
        }
        catch (Exception ex)
        {
            transaction = null;
            throw new StatusReport(SipSession.EXCEPTION_ENCOUNTERED,
                    "exception resending SUBSCRIBE with authorization: "
                            + ex.getClass().getName() + ": " + ex.getMessage());
        }

    }

    protected static void sendResponse(SipPhone parent, RequestEvent req,
            int status, String reason)
    {
        try
        {
            Response response = parent.getMessageFactory().createResponse(
                    status, req.getRequest());
            response.setReasonPhrase(reason);

            ((SipProvider) req.getSource()).sendResponse(response);
        }
        catch (Exception e)
        {
            System.err.println("Failure sending error response (" + reason
                    + ") for received " + req.getRequest().getMethod()
                    + ", Exception: " + e.getClass().getName() + ": "
                    + e.getMessage());
        }
    }

    /**
     * This method validates the given (received) NOTIFY request, updates the
     * subscription information based on the NOTIFY contents, and creates and
     * returns the correctly formed response that should be sent back in reply
     * to the NOTIFY, based on the NOTIFY content that was received. Call this
     * method after getting a NOTIFY request from method waitNotify().
     * <p>
     * If a null value is returned by this method, call getReturnCode() and/or
     * getErrorMessage() to see why.
     * <p>
     * If a non-null response object is returned by this method, it doesn't mean
     * that NOTIFY validation passed. If there was invalid content in the
     * NOTIFY, the response object returned by this method will have the
     * appropriate error code (489 Bad Event, etc.) that should be sent in reply
     * to the NOTIFY. You can call getReturnCode() to find out the status code
     * contained in the returned response (or you can examine the response in
     * detail using JAIN-SIP API). A return code of 200 OK means that the
     * received NOTIFY had correct content and the presence information stored
     * in this Subscription object has been updated with the NOTIFY message
     * contents. Call methods getPresenceDevices(), getPresenceExtensions(),
     * getPresenceNotes(), isSubscriptionXyz(), getTerminationReason() and/or
     * getTimeLeft() to see the newly updated presence information.
     * <p>
     * The next step is to invoke replyToNotify() to send a response to the
     * network. The caller may modify (corrupt) the response returned by this
     * method (using the JAIN-SIP API) before passing it to replyToNotify().
     * <p>
     * Validation performed by this method includes: event header present, event
     * type correct ("presence"), event ID matches sent SUBSCRIBE, subscription
     * state header present, received expiry not greater than that sent in
     * SUBSCRIBE, reception of NOTIFY request without having sent a SUBSCRIBE,
     * supported content type/subtype, matching (correct) presentity in NOTIFY
     * body, correctly formed xml body document, valid document content.
     * 
     * @param requestEvent
     *            the NOTIFY request event obtained from waitNotify()
     * @return a correct javax.sip.message.Response that should be sent back in
     *         reply, or null if an error was encountered.
     */
    public Response processNotify(RequestEvent requestEvent)
    {
        initErrorInfo();
        String cseq_str = "CSEQ x";

        try
        {
            if ((requestEvent == null) || (getLastSentRequest() == null))
            {
                setReturnCode(SipSession.INVALID_OPERATION);
                setErrorMessage("Request event is null and/or last sent request is null");
                return null;
            }

            Request request = requestEvent.getRequest();
            cseq_str = "CSEQ "
                    + ((CSeqHeader) request.getHeader(CSeqHeader.NAME))
                            .getSeqNumber();

            SipStack.trace("Processing NOTIFY " + cseq_str
                    + " request for Subscription to " + buddyUri);

            // validate received event header against the one sent

            validateEventHeader((EventHeader) request
                    .getHeader(EventHeader.NAME),
                    (EventHeader) getLastSentRequest().getHeader(
                            EventHeader.NAME));

            // get subscription state info from message

            SubscriptionStateHeader subs_hdr = (SubscriptionStateHeader) request
                    .getHeader(SubscriptionStateHeader.NAME);
            if (subs_hdr == null)
            {
                throw new StatusReport(SipResponse.BAD_REQUEST,
                        "no subscription state header received");
            }

            // update our subscription state information

            if (subscriptionState.equals(SubscriptionStateHeader.TERMINATED) == false)
            {
                subscriptionState = subs_hdr.getState();
            }

            if (subscriptionState.equals(SubscriptionStateHeader.TERMINATED))
            {
                terminationReason = subs_hdr.getReasonCode();

                // this is the last NOTIFY for this subscription, whether we
                // terminated
                // it from our end, or the other end just terminated it - don't
                // accept
                // any more by clearing out lastSentRequest, if one is received
                // after
                // this, SipPhone will respond with 481

                setLastSentRequest(null);

                updatePresenceInfo(request);

                Response response = createNotifyResponse(requestEvent,
                        SipResponse.OK, terminationReason);
                if (response != null)
                {
                    setReturnCode(SipResponse.OK);
                }

                return response;
            }

            // active or pending state: check expiry & update time left

            int expires = subs_hdr.getExpires();
            // SIP list TODO - it's optional - how to know if didn't get it?

            validateExpires(expires, true);

            SipStack.trace(buddyUri + ": received expiry = " + expires
                    + ", updating current expiry which was " + getTimeLeft());

            setTimeLeft(expires);
            updatePresenceInfo(request);

            Response response = createNotifyResponse(requestEvent,
                    SipResponse.OK, "OK");
            if (response != null)
            {
                setReturnCode(SipResponse.OK);
            }

            return response;
        }
        catch (StatusReport e)
        {
            String err = "*** NOTIFY REQUEST ERROR ***  (" + cseq_str + ", "
                    + buddyUri + ") - " + e.getReason();
            SipStack.trace(err);

            Response response = createNotifyResponse(requestEvent, e
                    .getStatusCode(), e.getReason());
            if (response != null)
            {
                setReturnCode(e.getStatusCode());
                setErrorMessage(e.getReason());
            }

            return response;
        }
    }

    private void updatePresenceInfo(Request request) throws StatusReport
    {
        // parse the presence info contained in the message, if any

        byte[] body_bytes = request.getRawContent();
        if (body_bytes == null)
        {
            return;
        }

        // check for supported content type, currently supporting only the
        // package default

        ContentTypeHeader ct = (ContentTypeHeader) request
                .getHeader(ContentTypeHeader.NAME);

        if (ct == null)
        {
            throw new StatusReport(SipResponse.BAD_REQUEST,
                    "NOTIFY body has bytes but no content type header was received");
        }

        if (ct.getContentType().equals("application") == false)
        {
            throw new StatusReport(SipResponse.UNSUPPORTED_MEDIA_TYPE,
                    "received NOTIFY body with unsupported content type = "
                            + ct.getContentType());
        }
        else if (ct.getContentSubType().equals("pidf+xml") == false)
        {
            throw new StatusReport(SipResponse.UNSUPPORTED_MEDIA_TYPE,
                    "received NOTIFY body with unsupported content subtype = "
                            + ct.getContentSubType());
        }

        // parse and get info from the xml body

        try
        {
            Presence doc = (Presence) JAXBContext.newInstance(
                    "org.cafesip.sipunit.presenceparser.pidf")
                    .createUnmarshaller().unmarshal(
                            new ByteArrayInputStream(body_bytes));

            // is it the correct presentity?

            if (!buddyUri.equals(doc.getEntity()))
            {
                throw new StatusReport(SipResponse.BAD_REQUEST,
                        "received NOTIFY body with wrong presentity = "
                                + doc.getEntity());
            }

            // finally, update our presence information

            devices.clear();
            if (doc.getTuple() != null)
            {
                Iterator i = doc.getTuple().iterator();
                while (i.hasNext())
                {
                    Tuple t = (Tuple) i.next();

                    PresenceDeviceInfo dev = new PresenceDeviceInfo();
                    dev.setBasicStatus(t.getStatus().getBasic());

                    Contact contact = t.getContact();
                    if (contact != null)
                    {
                        if (contact.getPriority() != null)
                        {
                            dev.setContactPriority(contact.getPriority()
                                    .doubleValue());
                        }
                        dev.setContactValue(contact.getValue());
                    }

                    dev.setDeviceExtensions(t.getAny());
                    dev.setId(t.getId());
                    dev.setStatusExtensions(t.getStatus().getAny());
                    dev.setTimestamp(t.getTimestamp());

                    ArrayList<PresenceNote> notes = new ArrayList<PresenceNote>();
                    if (t.getNote() != null)
                    {
                        Iterator<Note> j = t.getNote().iterator();
                        while (j.hasNext())
                        {
                            Note n = j.next();
                            notes.add(new PresenceNote(n.getLang(), n
                                    .getValue()));
                        }
                    }
                    dev.setDeviceNotes(notes);

                    devices.put(t.getId(), dev);
                }
            }

            presenceNotes.clear();
            if (doc.getNote() != null)
            {
                Iterator i = doc.getNote().iterator();
                while (i.hasNext())
                {
                    Note n = (Note) i.next();
                    presenceNotes.add(new PresenceNote(n.getLang(), n
                            .getValue()));
                }
            }

            presenceExtensions.clear();
            if (doc.getAny() != null)
            {
                presenceExtensions.addAll(doc.getAny());
            }

            SipStack
                    .trace("Successfully processed NOTIFY message body for Subscription to "
                            + buddyUri);
        }
        catch (Exception e)
        {
            throw new StatusReport(SipResponse.BAD_REQUEST,
                    "NOTIFY body parsing error : " + e.getMessage());
        }

        return;
    }

    /**
     * This method sends the given response to the network in reply to the given
     * request that was previously received. Call this method after
     * processNotify() has handled the received request.
     * 
     * @param reqevent
     *            The object returned by waitNotify().
     * @param response
     *            The object returned by processNotify(), or a user-modified
     *            version of it.
     * @return true if the response is successfully sent out, false otherwise.
     */
    public boolean replyToNotify(RequestEvent reqevent, Response response)
    {
        initErrorInfo();

        if ((reqevent == null) || (reqevent.getRequest() == null)
                || (response == null))
        {
            setErrorMessage("Cannot send reply, request or response info is null");
            setReturnCode(SipSession.INVALID_ARGUMENT);
            return false;
        }

        try
        {
            if (reqevent.getServerTransaction() == null)
            {
                // 1st NOTIFY received before 1st subscribe response
                SipStack
                        .trace("Informational : no UAS transaction available for received NOTIFY");

                // send response statelessly
                ((SipProvider) reqevent.getSource()).sendResponse(response);
            }
            else
            {
                reqevent.getServerTransaction().sendResponse(response);
            }
        }
        catch (Exception e)
        {
            setException(e);
            setErrorMessage("Exception: " + e.getClass().getName() + ": "
                    + e.getMessage());
            setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
            return false;
        }

        SipStack
                .trace("Sent response message to received NOTIFY, status code : "
                        + response.getStatusCode()
                        + ", reason : "
                        + response.getReasonPhrase());

        return true;
    }

    protected boolean messageForMe(javax.sip.message.Message msg)
    {
        /*
         * NOTIFY requests are matched to SUBSCRIBE requests if they contain the
         * same "Call-ID", a "To" header "tag" parameter which matches the
         * "From" header "tag" parameter of the SUBSCRIBE, and the same "Event"
         * header field.
         */

        Request last_subscribe = getLastSentRequest();

        if (last_subscribe == null)
        {
            return false;
        }

        CallIdHeader hdr = (CallIdHeader) msg.getHeader(CallIdHeader.NAME);
        if (hdr == null)
        {
            return false;
        }

        CallIdHeader sent_hdr = (CallIdHeader) last_subscribe
                .getHeader(CallIdHeader.NAME);
        if (sent_hdr == null)
        {
            return false;
        }

        if ((hdr.getCallId() == null) || (sent_hdr.getCallId() == null))
        {
            return false;
        }

        if (hdr.getCallId().equals(sent_hdr.getCallId()) == false)
        {
            return false;
        }

        // check to-tag = from-tag, (my tag), and event header
        // fields same as in
        // sent SUBSCRIBE

        ToHeader tohdr = (ToHeader) msg.getHeader(ToHeader.NAME);
        if (tohdr == null)
        {
            return false;
        }

        String to_tag = tohdr.getTag();
        if (to_tag == null)
        {
            return false;
        }

        FromHeader sent_from = (FromHeader) last_subscribe
                .getHeader(FromHeader.NAME);
        if (sent_from == null)
        {
            return false;
        }

        String from_tag = sent_from.getTag();
        if (from_tag == null)
        {
            return false;
        }

        if (to_tag.equals(from_tag) == false)
        {
            return false;
        }

        EventHeader eventhdr = (EventHeader) msg.getHeader(EventHeader.NAME);
        EventHeader sent_eventhdr = (EventHeader) last_subscribe
                .getHeader(EventHeader.NAME);

        if ((eventhdr == null) || (sent_eventhdr == null))
        {
            return false;
        }

        if (eventhdr.equals(sent_eventhdr) == false)
        {
            return false;
        }

        return true;
    }

    private void validateEventHeader(EventHeader received_hdr,
            EventHeader sent_hdr) throws StatusReport
    {
        if (received_hdr == null)
        {
            throw new StatusReport(SipResponse.BAD_REQUEST,
                    "no event header received");
        }

        // check event type

        String event = received_hdr.getEventType();
        if (event.equals("presence") == false)
        {
            throw new StatusReport(SipResponse.BAD_EVENT,
                    "received an event header containing unknown event = "
                            + event);
        }

        // verify matching eventIds

        String last_sent_id = null;
        if (sent_hdr != null)
        {
            last_sent_id = sent_hdr.getEventId();
        }

        // get the one we just received
        String event_id = received_hdr.getEventId();
        if (event_id == null)
        {
            if (last_sent_id != null)
            {
                throw new StatusReport(SipResponse.BAD_REQUEST,
                        "event id mismatch, received null, last sent "
                                + last_sent_id);
            }
        }
        else
        {
            if (last_sent_id == null)
            {
                throw new StatusReport(SipResponse.BAD_REQUEST,
                        "event id mismatch, last sent null, received "
                                + event_id);
            }
            else
            {
                if (event_id.equals(last_sent_id) == false)
                {
                    throw new StatusReport(SipResponse.BAD_REQUEST,
                            "event id mismatch, received " + event_id
                                    + ", last sent " + last_sent_id);
                }
            }
        }
    }

    private void validateExpires(int expires, boolean isNotify)
            throws StatusReport
    {
        // expiry may be shorter, can't be longer than what we sent

        int sent_expires = getLastSentRequest().getExpires().getExpires();

        if (expires > sent_expires)
        {
            throw new StatusReport((isNotify == true ? SipResponse.BAD_REQUEST
                    : SipSession.FAR_END_ERROR),
                    "received expiry > expiry in sent SUBSCRIBE (" + expires
                            + " > " + sent_expires + ')');
        }

    }

    /**
     * Gets the list of known devices for this Subscription (buddy, watchee).
     * This list represents the list of 'tuple's received in the last NOTIFY
     * message.
     * 
     * @return A HashMap containing zero or more PresenceDeviceInfo objects,
     *         indexed/keyed by the unique IDs received for each in the NOTIFY
     *         messages (tuple elements).
     */
    public HashMap<String, PresenceDeviceInfo> getPresenceDevices()
    {
        return new HashMap<String, PresenceDeviceInfo>(devices);
    }

    /**
     * Gets the list of notes pertaining to this Subscription as received in the
     * last NOTIFY message (at the top 'presence' element level).
     * 
     * @return An ArrayList containing zero or more PresenceNote objects.
     */
    public ArrayList<PresenceNote> getPresenceNotes()
    {
        return new ArrayList<PresenceNote>(presenceNotes);
    }

    /**
     * Gets the list of extensions pertaining to this Subscription as received
     * in the last NOTIFY message (at the top 'presence' element level).
     * 
     * @return An ArrayList containing zero or more Object.
     */
    public ArrayList<Object> getPresenceExtensions()
    {
        return new ArrayList<Object>(presenceExtensions);
    }

    /**
     * Returns the number of seconds left in the subscription, if active, or the
     * number of seconds that were remaining at the time the subscription was
     * terminated.
     * 
     * @return Returns the timeLeft in seconds.
     */
    public int getTimeLeft()
    {
        if (projectedExpiry == 0)
        {
            return 0;
        }

        return (int) ((projectedExpiry - System.currentTimeMillis()) / 1000);
    }

    /**
     * @param timeLeft
     *            The timeLeft to set, in seconds.
     */
    protected void setTimeLeft(int timeLeft)
    {
        if (timeLeft <= 0)
        {
            projectedExpiry = 0;
            return;
        }

        projectedExpiry = System.currentTimeMillis() + (timeLeft * 1000);
    }

    protected void initErrorInfo()
    {
        setErrorMessage("");
        setException(null);
        setReturnCode(SipSession.NONE_YET);
    }

    /*
     * @see org.cafesip.sipunit.SipActionObject#format()
     */
    public String format()
    {
        if (SipSession.isInternal(returnCode) == true)
        {
            return SipSession.statusCodeDescription
                    .get(new Integer(returnCode))
                    + (errorMessage.length() > 0 ? (": " + errorMessage) : "");
        }
        else
        {
            return "Status code received from network = "
                    + returnCode
                    + ", "
                    + SipResponse.statusCodeDescription.get(new Integer(
                            returnCode))
                    + (errorMessage.length() > 0 ? (": " + errorMessage) : "");
        }
    }

    /**
     * This method returns the last response received on this subscription.
     * 
     * @return A SipResponse object representing the last response message
     *         received, or null if none has been received.
     * 
     * @see org.cafesip.sipunit.MessageListener#getLastReceivedResponse()
     */
    public SipResponse getLastReceivedResponse()
    {
        synchronized (responseBlock)
        {
            if (receivedResponses.isEmpty())
            {
                return null;
            }

            return (SipResponse) receivedResponses.getLast();
        }
    }

    /**
     * This method returns the last request received on this subscription.
     * 
     * @return A SipRequest object representing the last request message
     *         received, or null if none has been received.
     * 
     * @see org.cafesip.sipunit.MessageListener#getLastReceivedRequest()
     */
    public SipRequest getLastReceivedRequest()
    {
        synchronized (this)
        {
            if (receivedRequests.isEmpty())
            {
                return null;
            }

            return (SipRequest) receivedRequests.getLast();
        }
    }

    /**
     * This method returns all the responses received on this subscription,
     * including any that required re-initiation of the subscription (ie,
     * authentication challenge). Not included are out-of-sequence (late)
     * SUBSCRIBE responses.
     * 
     * @return ArrayList of zero or more SipResponse objects.
     * 
     * @see org.cafesip.sipunit.MessageListener#getAllReceivedResponses()
     */
    public ArrayList<SipResponse> getAllReceivedResponses()
    {
        synchronized (responseBlock)
        {
            return new ArrayList<SipResponse>(receivedResponses);
        }
    }

    /**
     * This method returns all the NOTIFY requests received on this
     * subscription. (Retransmissions aren't included.)
     * 
     * @return ArrayList of zero or more SipRequest objects.
     * 
     * @see org.cafesip.sipunit.MessageListener#getAllReceivedRequests()
     */
    public ArrayList<SipRequest> getAllReceivedRequests()
    {
        synchronized (this)
        {
            return new ArrayList<SipRequest>(receivedRequests);
        }
    }

    /**
     * Indicates if the subscription state is TERMINATED.
     * 
     * @return true if so, false if not.
     */
    public boolean isSubscriptionTerminated()
    {
        return (subscriptionState.equals(SubscriptionStateHeader.TERMINATED));
    }

    /**
     * Indicates if the subscription state is ACTIVE.
     * 
     * @return true if so, false if not.
     */
    public boolean isSubscriptionActive()
    {
        return (subscriptionState.equals(SubscriptionStateHeader.ACTIVE));
    }

    /**
     * Indicates if the subscription state is PENDING.
     * 
     * @return true if so, false if not.
     */
    public boolean isSubscriptionPending()
    {
        return (subscriptionState.equals(SubscriptionStateHeader.PENDING));
    }

    /**
     * Returns the subscription termination reason for this subscription. Call
     * this method when the subscription has been terminated (method
     * isSubscriptionTerminated() returns true).
     * 
     * @return Returns the termination reason or null if the subscription is not
     *         terminated.
     */
    public String getTerminationReason()
    {
        return terminationReason;
    }

    /*
     * @see org.cafesip.sipunit.SipActionObject#getErrorMessage()
     */
    public String getErrorMessage()
    {
        return errorMessage;
    }

    private void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

    /*
     * @see org.cafesip.sipunit.SipActionObject#getException()
     */
    public Throwable getException()
    {
        return exception;
    }

    private void setException(Throwable exception)
    {
        this.exception = exception;
    }

    /*
     * @see org.cafesip.sipunit.SipActionObject#getReturnCode()
     */
    public int getReturnCode()
    {
        return returnCode;
    }

    private void setReturnCode(int returnCode)
    {
        this.returnCode = returnCode;
    }

    /**
     * This method returns the URI of the user that this Subscription is for.
     * 
     * @return The user's URI.
     */
    public String getBuddyUri()
    {
        return buddyUri;
    }

    /**
     * This method returns any errors accumulated during collection of SUBSCRIBE
     * responses and NOTIFY requests. Since this happens automatically,
     * asynchronous of the test program activity, there's not a handy way like a
     * method call return code to report these errors if they happen. They are
     * errors like: No CSEQ header in received NOTIFY, error or exception
     * resending SUBSCRIBE with authorization header, unexpected null
     * transaction object at response timeout, etc. You should at various points
     * call SipTestCase.assertNoEventErrors() during a test to verify none have
     * been encountered.
     * <p>
     * The case where a NOTIFY is received by a SipPhone but there is no
     * matching subscription results in 481 response being sent back and an
     * event error entry in each Subscription object associated with that
     * SipPhone (to ensure it will be seen by the test program).
     * <p>
     * Aside from being put in the event error list, event errors are output
     * with the SipUnit trace if you have it turned on
     * (SipStack.setTraceEnabled(true)). You can clear this list by calling
     * clearEventErrors().
     * 
     * @return LinkedList (never null) of zero or more String
     */
    public LinkedList<String> getEventErrors()
    {
        synchronized (eventErrors)
        {
            return new LinkedList<String>(eventErrors);
        }
    }

    /**
     * This method clears errors accumulated while collecting SUBSCRIBE
     * responses and NOTIFY requests. See related method getEventErrors().
     */
    public void clearEventErrors()
    {
        synchronized (eventErrors)
        {
            eventErrors.clear();
        }
    }

    protected Response createNotifyResponse(RequestEvent request, int status,
            String reason)
    {
        ArrayList<Header> additional_headers = null;

        if (status == SipResponse.UNSUPPORTED_MEDIA_TYPE)
        {
            try
            {
                AcceptHeader ahdr = parent.getHeaderFactory()
                        .createAcceptHeader("application", "pidf+xml");
                additional_headers = new ArrayList<Header>();
                additional_headers.add(ahdr);
            }
            catch (Exception e)
            {
                setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
                setException(e);
                setErrorMessage("Couldn't create accept header for 'Unsupported Media Type' response : Exception: "
                        + e.getClass().getName() + ": " + e.getMessage());

                return null;
            }
        }

        return createNotifyResponse(request, status, reason, additional_headers);
    }

    // if returns null, returnCode and errorMessage already set
    protected Response createNotifyResponse(RequestEvent request, int status,
            String reason, ArrayList<Header> additionalHeaders)
    // when used internally - WATCH OUT - retcode, errorMessage initialized here
    {
        initErrorInfo();

        if ((request == null) || (request.getRequest() == null))
        {
            setReturnCode(SipSession.INVALID_ARGUMENT);
            setErrorMessage("Null request given for creating NOTIFY response");
            return null;
        }

        Request req = request.getRequest();
        String cseq_str = "CSEQ "
                + ((CSeqHeader) req.getHeader(CSeqHeader.NAME)).getSeqNumber();
        SipStack.trace("Creating NOTIFY " + cseq_str
                + " response with status code " + status + ", reason phrase = "
                + reason);

        try
        {
            Response response = parent.getMessageFactory().createResponse(
                    status, req);

            if (reason != null)
            {
                response.setReasonPhrase(reason);
            }

            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(myTag);

            response.addHeader((ContactHeader) parent.getContactInfo()
                    .getContactHeader().clone());

            if (additionalHeaders != null)
            {
                Iterator<Header> i = additionalHeaders.iterator();
                while (i.hasNext())
                {
                    response.addHeader(i.next());
                }
            }

            return response;
        }
        catch (Exception e)
        {
            setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
            setException(e);
            setErrorMessage("Exception: " + e.getClass().getName() + ": "
                    + e.getMessage());
        }

        return null;
    }

    protected String getEventId()
    {
        Request last_subscribe = getLastSentRequest();

        if (last_subscribe == null)
        {
            return null;
        }

        EventHeader evt = (EventHeader) last_subscribe
                .getHeader(EventHeader.NAME);
        if (evt == null)
        {
            return null;
        }

        return evt.getEventId();
    }

    /**
     * The waitNotify() method allows received NOTIFY messages to be examined
     * and processed by the test program, one by one. Call this method whenever
     * you are expecting a NOTIFY to be received and your test program has
     * nothing else to do until then. If there are already one or more
     * unexamined-as-yet-by-the-test-program NOTIFY messages accumulated when
     * this method is called, it returns the next in line (FIFO) immediately.
     * Otherwise, it waits for the next NOTIFY message to be received from the
     * network for this subscription.
     * <p>
     * This method blocks until one of the following occurs: 1) A NOTIFY message
     * is received, for this subscription. The received NOTIFY
     * javax.sip.RequestEvent object is returned in this case. The calling
     * program may examine the returned object (requires knowledge of JAIN SIP).
     * The next step for the caller is to pass the object returned by this
     * method to processNotify() for handling. 2) The wait timeout period
     * specified by the parameter to this method expires. Null is returned in
     * this case. 3) An error occurs. Null is returned in this case.
     * <p>
     * A NOTIFY message whose CSEQ# is not greater than those previously
     * received is discarded and not returned by this method.
     * 
     * @param timeout
     *            The maximum amount of time to wait, in milliseconds. Use a
     *            value of 0 to wait indefinitely.
     * @return A RequestEvent (received NOTIFY) or null in the case of wait
     *         timeout or error. If null is returned, call getReturnCode()
     *         and/or getErrorMessage() and, if applicable, getException() for
     *         further diagnostics.
     */
    public RequestEvent waitNotify(long timeout)
    {
        initErrorInfo();

        synchronized (this)
        {
            if (reqEvents.size() == 0)
            {
                try
                {
                    SipStack
                            .trace("Subscription.waitNotify() - about to block, waiting");
                    this.wait(timeout);
                    SipStack
                            .trace("Subscription.waitNotify() - we've come out of the block");
                }
                catch (Exception ex)
                {
                    setException(ex);
                    setErrorMessage("Exception: " + ex.getClass().getName()
                            + ": " + ex.getMessage());
                    setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
                    return null;
                }
            }

            SipStack
                    .trace("Subscription.waitNotify() - either we got the request, or timed out");
            if (reqEvents.size() == 0)
            {
                setReturnCode(SipSession.TIMEOUT_OCCURRED);
                setErrorMessage("The maximum amount of time to wait for a NOTIFY message has elapsed.");
                return null;
            }

            return (RequestEvent) reqEvents.removeFirst();
        }
    }

    /**
     * The waitSubscribeResponse() method waits for a response for a previously
     * sent SUBSCRIBE request message.
     * <p>
     * This method blocks until one of the following occurs: 1) A
     * javax.sip.ResponseEvent is received. This is the object returned by this
     * method. 2) A javax.sip.TimeoutEvent is received. This is the object
     * returned by this method. 3) The wait timeout period specified by the
     * parameter to this method expires. Null is returned in this case. 4) An
     * error occurs. Null is returned in this case.
     * 
     * @param timeout
     *            The maximum amount of time to wait, in milliseconds. Use a
     *            value of 0 to wait indefinitely.
     * @return A javax.sip.ResponseEvent, javax.sip.TimeoutEvent, or null in the
     *         case of wait parameter timeout or error. If null, call
     *         getReturnCode() and/or getErrorMessage() and, if applicable,
     *         getException() for further diagnostics.
     */
    protected EventObject waitSubscribeResponse(long timeout)
    {
        synchronized (responseBlock)
        {
            LinkedList<EventObject> events = transaction.getEvents();
            if (events.size() == 0)
            {
                try
                {
                    SipStack
                            .trace("Subscription.waitSubscribeResponse() - about to block, waiting");
                    responseBlock.waitForEvent(timeout);
                    SipStack
                            .trace("Subscription.waitSubscribeResponse() - we've come out of the block");
                }
                catch (Exception ex)
                {
                    setException(ex);
                    setErrorMessage("Exception: " + ex.getClass().getName()
                            + ": " + ex.getMessage());
                    setReturnCode(SipSession.EXCEPTION_ENCOUNTERED);
                    return null;
                }
            }

            SipStack
                    .trace("Subscription.waitSubscribeResponse() - either we got the response, or timed out");

            if (events.size() == 0)
            {
                setReturnCode(SipSession.TIMEOUT_OCCURRED);
                setErrorMessage("The maximum amount of time to wait for a SUBSCRIBE response message has elapsed.");
                return null;
            }

            return (EventObject) events.removeFirst();
        }
    }

    private class StatusReport extends Exception
    {
        /**
         * Comment for <code>serialVersionUID</code>
         */
        private static final long serialVersionUID = 1L;

        private int statusCode = -1;

        private String reason;

        public StatusReport(int statusCode, String reason)
        {
            this.statusCode = statusCode;
            this.reason = reason;
        }

        public String getReason()
        {
            return reason;
        }

        public void setReason(String reason)
        {
            this.reason = reason;
        }

        public int getStatusCode()
        {
            return statusCode;
        }

        public void setStatusCode(int statusCode)
        {
            this.statusCode = statusCode;
        }
    }

    /**
     * This method returns the most recent SUBSCRIBE response received from the
     * network for this subscription. Knowledge of JAIN-SIP API is required to
     * examine the object returned from this method. Alternately, call
     * getLastReceivedResponse() to see the primary values (status, reason)
     * contained in the last received response.
     * 
     * @return javax.sip.ResponseEvent - last received SUBSCRIBE response.
     */
    public ResponseEvent getCurrentSubscribeResponse()
    {
        return currentSubscribeResponse;
    }

    protected void setCurrentSubscribeResponse(
            ResponseEvent currentSubscribeResponse)
    {
        this.currentSubscribeResponse = currentSubscribeResponse;
    }

    protected void addEventError(String err)
    {
        synchronized (eventErrors)
        {
            eventErrors.addLast(err);
        }
    }

    /**
     * This method returns the last SUBSCRIBE request that was sent out for this
     * Subscription.
     * 
     * @return javax.sip.message.Request last sent out
     */
    public Request getLastSentRequest()
    {
        synchronized (lastSubscribeLock)
        {
            return lastSentRequest;
        }
    }

    protected void setLastSentRequest(Request lastSentRequest)
    {
        synchronized (lastSubscribeLock)
        {
            this.lastSentRequest = lastSentRequest;
        }
    }

    /**
     * This method, called after removing a buddy from the buddy list, indicates
     * if an unsubscribe sequence was initiated due to the removal or not.
     * 
     * @return true if unsubscribe was not necessary (because the subscription
     *         was already terminated) and false if a SUBSCRIBE/NOTIFY sequence
     *         was initiated due to the removal of the buddy from the list.
     */
    public boolean isRemovalComplete()
    {
        return removalComplete;
    }

    protected void setRemovalComplete(boolean removalComplete)
    {
        this.removalComplete = removalComplete;
    }
}

/*
 * NEW HEADERS This table expands on tables 2 and 3 in SIP [1], as amended by
 * the changes described in section 7.1.
 * 
 * Header field where proxy ACK BYE CAN INV OPT REG PRA SUB NOT
 * -----------------------------------------------------------------
 * Allow-Events R o o - o o o o o o Allow-Events 2xx - o - o o o o o o
 * Allow-Events 489 - - - - - - - m m Event R - - - - - - - m m
 * Subscription-State R - - - - - - - - m
 */

