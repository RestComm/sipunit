/*
 * Created on November 22, 2005
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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.EventObject;
import java.util.Iterator;

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
 * The primary purpose of this class is as a test utility, to verify other UA's
 * NOTIFY reception processing.
 * 
 * When instantiated, an object of this class listens for a SUBSCRIBE message.
 * After the calling program has sent a SUBSCRIBE message to this object's uri,
 * it can call this object's processSubscribe() to receive and process the
 * SUBSCRIBE and send a response. After that, this object sends a NOTIFY message
 * on that subscription each time method sendNotify() is called, with the given
 * content body. This class can listen for and show the response to a given sent
 * NOTIFY message. It can receive and process multiple SUBSCRIBE messages for a
 * subscription.
 * 
 * @author Becky McElroy
 * 
 */
public class PresenceNotifySender implements MessageListener
{
    protected SipPhone ub;

    protected Dialog dialog;

    protected EventHeader eventHeader;

    protected Object dialogLock = new Object();

    protected String toTag;

    protected Request lastSentNotify;

    protected String errorMessage = "";

    /**
     * A constructor for this class. This object immediately starts listening
     * for a SUBSCRIBE request.
     * 
     * @param userb
     *            SipPhone object to use for messaging.
     * @throws Exception
     *             If there's a problem
     */
    public PresenceNotifySender(SipPhone userb)
    {
        ub = userb;
        ub.listenRequestMessage();
    }

    /**
     * Dispose of this object (but not the stack given to it).
     * 
     */
    public void dispose()
    {
        ub.dispose();
    }

    /**
     * This method waits for up to 10 seconds to receive a SUBSCRIBE and if
     * received, it sends an OK response.
     * 
     * @return true if SUBSCRIBE received and response sending was successful,
     *         false otherwise (call getErrorMessage() for details).
     */
    public boolean processSubscribe()
    {
        return processSubscribe(10000, SipResponse.OK, null);
    }

    /**
     * This method starts a thread that waits for up to 'timeout' milliseconds
     * to receive a SUBSCRIBE and if received, it sends a response with
     * 'statusCode' and 'reasonPhrase' (if not null). This method waits 500 ms
     * before returning to allow the thread to get started and begin waiting for
     * an incoming SUBSCRIBE. This method adds 500ms to the given timeout to
     * account for this delay.
     * 
     * @param timeout
     *            - number of milliseconds to wait for the SUBSCRIBE
     * @param statusCode
     *            - use in the response to the SUBSCRIBE
     * @param reasonPhrase
     *            - if not null, use in the SUBSCRIBE response
     * @return true if SUBSCRIBE received and response sending was successful,
     *         false otherwise (call getErrorMessage() for details).
     */
    public boolean processSubscribe(long timeout, int statusCode,
            String reasonPhrase)
    {
        setErrorMessage("");

        PhoneB b = new PhoneB(timeout + 500, statusCode, reasonPhrase);
        b.start();
        try
        {
            Thread.sleep(500);
        }
        catch (InterruptedException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return false;
        }

        return true;
    }

    class PhoneB extends Thread
    {
        long timeout;

        int statusCode;

        String reasonPhrase;

        public PhoneB(long timeout, int statusCode, String reasonPhrase)
        {
            this.timeout = timeout;
            this.statusCode = statusCode;
            this.reasonPhrase = reasonPhrase;
        }

        public void run()
        {
            try
            {
                ub.unlistenRequestMessage(); // clear out request queue
                ub.listenRequestMessage();

                RequestEvent inc_req = ub.waitRequest(timeout);
                while (inc_req != null)
                {
                    Request req = inc_req.getRequest();
                    if (req.getMethod().equals(Request.SUBSCRIBE) == false)
                    {
                        inc_req = ub.waitRequest(timeout);
                        continue;
                    }

                    try
                    {
                        synchronized (dialogLock)
                        {
                            ServerTransaction trans = inc_req
                                    .getServerTransaction();
                            if (trans == null)
                            {
                                trans = ub.getParent().getSipProvider()
                                        .getNewServerTransaction(req);
                            }

                            if (toTag == null)
                            {
                                toTag = new Long(Calendar.getInstance()
                                        .getTimeInMillis()).toString();
                            }

                            int duration = 3600;
                            ExpiresHeader exp = (ExpiresHeader) req
                                    .getHeader(ExpiresHeader.NAME);
                            if (exp != null)
                            {
                                duration = exp.getExpires();
                            }

                            // enable auth challenge handling
                            ub.enableAuthorization(((CallIdHeader) req
                                    .getHeader(CallIdHeader.NAME)).getCallId());

                            // save original event header
                            eventHeader = (EventHeader) req.getHeader(
                                    EventHeader.NAME).clone();

                            dialog = sendResponse(trans, statusCode,
                                    reasonPhrase, toTag, req, duration);

                            if (dialog == null)
                            {
                                ub.clearAuthorizations(((CallIdHeader) req
                                        .getHeader(CallIdHeader.NAME))
                                        .getCallId());
                                return;
                            }
                        }

                        SipStack.trace("Sent response to SUBSCRIBE");
                        return;
                    }
                    catch (Throwable e)
                    {
                        setErrorMessage("Throwable: " + e.getClass().getName()
                                + ": " + e.getMessage());
                        return;
                    }
                }

                setErrorMessage(ub.getErrorMessage());
                return;
            }
            catch (Exception e)
            {
                setErrorMessage("Exception: " + e.getClass().getName() + ": "
                        + e.getMessage());
            }
            catch (Throwable t)
            {
                setErrorMessage("Throwable: " + t.getClass().getName() + ": "
                        + t.getMessage());
                return;
            }
        }
    }

    protected Dialog sendResponse(ServerTransaction transaction,
            int statusCode, String reasonPhrase, String toTag, Request request,
            int duration)
    {
        try
        {
            Response response = ub.getParent().getMessageFactory()
                    .createResponse(statusCode, request);

            if (duration != -1)
            {
                response.setHeader(ub.getParent().getHeaderFactory()
                        .createExpiresHeader(duration));
            }

            if (reasonPhrase != null)
            {
                response.setReasonPhrase(reasonPhrase);
            }

            EventHeader event_hdr = (EventHeader) request
                    .getHeader(EventHeader.NAME);
            if (event_hdr != null)
            {
                response.setHeader((Header) event_hdr.clone());
            }

            ((ToHeader) response.getHeader(ToHeader.NAME)).setTag(toTag);
            response.addHeader((ContactHeader) ub.getContactInfo()
                    .getContactHeader().clone());

            if (statusCode / 100 == 2) // 2xx
            {
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
        }
        catch (Exception e)
        {
            setErrorMessage("Error responding to request from "
                    + ((FromHeader) request.getHeader(FromHeader.NAME))
                            .getName() + ": " + e.getClass().getName() + ": "
                    + e.getMessage());

            return null;
        }

    }

    /**
     * @return
     * @throws ParseException
     */
    protected AllowEventsHeader getAllowEventsHeaderForResponse()
            throws ParseException
    {
        AllowEventsHeader ahdr = ub.getParent().getHeaderFactory()
                .createAllowEventsHeader("presence");
        return ahdr;
    }

    /**
     * @return
     * @throws ParseException
     */
    protected SupportedHeader getSupportedHeaderForResponse()
            throws ParseException
    {
        SupportedHeader shdr = ub.getParent().getHeaderFactory()
                .createSupportedHeader("presence");
        return shdr;
    }

    /**
     * @return
     * @throws ParseException
     */
    protected AcceptHeader getAcceptHeaderForResponse() throws ParseException
    {
        AcceptHeader accept = ub.getParent().getHeaderFactory()
                .createAcceptHeader("application", "pidf+xml");
        return accept;
    }

    /**
     * This method creates a NOTIFY message using the given parameters and sends
     * it to the subscriber. The request will be resent if challenged. Use this
     * method only if you have previously called processSubscribe(). Use this
     * method if you don't care about checking the response to the sent NOTIFY,
     * otherwise use sendStatefulNotify().
     * 
     * @param subscriptionState
     *            - String to use as the subscription state.
     * @param termReason
     *            - used only when subscriptionState = TERMINATED.
     * @param body
     *            - NOTIFY body to put in the message
     * @param timeLeft
     *            - expiry in seconds to put in the NOTIFY message (used only
     *            when subscriptionState = ACTIVE or PENDING).
     * @param viaProxy
     *            If true, send the message to the proxy. In this case a Route
     *            header will be added. Else send the message as is.
     * @return true if successful, false otherwise (call getErrorMessage() for
     *         details).
     */
    public boolean sendNotify(String subscriptionState, String termReason,
            String body, int timeLeft, boolean viaProxy)
    {
        return sendNotify(subscriptionState, termReason, body, timeLeft, null,
                null, null, null, viaProxy);
    }

    /**
     * This method creates a NOTIFY message using the given parameters and sends
     * it to the subscriber. Knowledge of JAIN-SIP API headers is required. The
     * request will be resent if challenged. Use this method only if you have
     * previously called processSubscribe(). Use this method if you don't care
     * about checking the response to the sent NOTIFY, otherwise use
     * sendStatefulNotify().
     * 
     * @param subscriptionState
     *            - String to use as the subscription state. Overridden by
     *            sshdr.
     * @param termReason
     *            - used only when subscriptionState = TERMINATED. Overridden by
     *            sshdr.
     * @param body
     *            - NOTIFY body to put in the message
     * @param timeLeft
     *            - expiry in seconds to put in the NOTIFY subscription state
     *            header (only when subscriptionState = ACTIVE or PENDING),
     *            unless the timeLeft value is -1 in which case don't include
     *            expires information in the subscription state header.
     *            Overridden by sshdr.
     * @param eventHdr
     *            - if not null, use this event header in the NOTIFY message
     * @param ssHdr
     *            - if not null, use this subscription state header in the
     *            NOTIFY message instead of building one from other parameters
     *            given.
     * @param accHdr
     *            - if not null, use this accept header. Otherwise build the
     *            default package one (pidf+xml).
     * @param ctHdr
     *            - if not null, use this content type header. Otherwise build
     *            the default package one (pidf+xml).
     * @param viaProxy
     *            If true, send the message to the proxy. In this case a Route
     *            header will be added. Else send the message as is.
     * 
     * @return true if successful, false otherwise (call getErrorMessage() for
     *         details).
     */
    public boolean sendNotify(String subscriptionState, String termReason,
            String body, int timeLeft, EventHeader eventHdr,
            SubscriptionStateHeader ssHdr, AcceptHeader accHdr,
            ContentTypeHeader ctHdr, boolean viaProxy)
    {
        setErrorMessage("");

        synchronized (dialogLock)
        {
            if (dialog == null)
            {
                setErrorMessage("Can't send notify, haven't received a request");
                return false;
            }

            try
            {
                Request req = dialog.createRequest(Request.NOTIFY);

                EventHeader ehdr = eventHdr;
                if (ehdr == null)
                {
                    if (eventHeader != null)
                    {
                        ehdr = ub.getParent().getHeaderFactory()
                                .createEventHeader(eventHeader.getEventType());
                        if (eventHeader.getEventId() != null)
                        {
                            ehdr.setEventId(eventHeader.getEventId());
                        }
                    }
                    else
                    {
                        ehdr = ub.getParent().getHeaderFactory()
                                .createEventHeader(getEventType());
                    }
                }
                req.setHeader(ehdr);

                SubscriptionStateHeader hdr = ssHdr;
                if (hdr == null)
                {
                    hdr = ub.getParent().getHeaderFactory()
                            .createSubscriptionStateHeader(subscriptionState);

                    if (subscriptionState
                            .equals(SubscriptionStateHeader.TERMINATED))
                    {
                        hdr.setReasonCode(termReason);
                    }
                    else if (timeLeft != -1)
                    {
                        hdr.setExpires(timeLeft);
                    }
                }
                req.setHeader(hdr);

                AcceptHeader accept = accHdr;
                if (accept == null)
                {
                    accept = ub.getParent().getHeaderFactory()
                            .createAcceptHeader(getPackageContentType(),
                                    getPackageContentSubType());
                }
                req.setHeader(accept);

                // now for the body
                ContentTypeHeader ct_hdr = ctHdr;
                if (ct_hdr == null)
                {
                    ct_hdr = ub.getParent().getHeaderFactory()
                            .createContentTypeHeader(getPackageContentType(),
                                    getPackageContentSubType());
                }

                req.setContent(body, ct_hdr);

                req.setContentLength(ub.getParent().getHeaderFactory()
                        .createContentLengthHeader(body.length()));

                return sendNotify(req, viaProxy);
            }
            catch (Exception e)
            {
                setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
            }

        }
        return false;

    }

    /**
     * @return
     */
    protected String getPackageContentSubType()
    {
        return "pidf+xml";
    }

    /**
     * @return
     */
    protected String getPackageContentType()
    {
        return "application";
    }

    /**
     * @return
     */
    protected String getEventType()
    {
        return "presence";
    }

    /**
     * This method adds each of the given parameters, if not null, to the given
     * NOTIFY Request parameter which just contains the request line (created
     * from a string). It is for the purpose of building a NOTIFY message to
     * send out. If a dialog is associated with this object (ie, a request has
     * been previously received), this method takes the information from there
     * to create headers for the null parameters passed in, else this method
     * makes up the header content.
     * 
     * @param req
     *            Request parameter which just contains the request line
     * @param toUser
     *            Used to create the 'To' header address - user part
     * @param toDomain
     *            Used to create the 'To' header address - host part of sip URI
     * @param subscriptionState
     *            active, pending, or terminated (SubscriptionStateHeader
     *            constant)
     * @param termReason
     *            any string
     * @param body
     *            the entire content as a string
     * @param timeLeft
     *            number of seconds to put in the NOTIFY
     * @return the modified request
     */
    public Request addNotifyHeaders(Request req, String toUser,
            String toDomain, String subscriptionState, String termReason,
            String body, int timeLeft)
    {
        setErrorMessage("");

        try
        {
            synchronized (dialogLock)
            {
                EventHeader ehdr;
                if (eventHeader != null)
                {
                    ehdr = ub.getParent().getHeaderFactory().createEventHeader(
                            eventHeader.getEventType());
                    if (eventHeader.getEventId() != null)
                    {
                        ehdr.setEventId(eventHeader.getEventId());
                    }
                }
                else
                {
                    ehdr = ub.getParent().getHeaderFactory().createEventHeader(
                            getEventType());
                }
                req.setHeader(ehdr);

                SubscriptionStateHeader hdr = ub.getParent().getHeaderFactory()
                        .createSubscriptionStateHeader(subscriptionState);

                if (subscriptionState
                        .equals(SubscriptionStateHeader.TERMINATED))
                {
                    hdr.setReasonCode(termReason);
                }
                else if (timeLeft != -1)
                {
                    hdr.setExpires(timeLeft);
                }
                req.setHeader(hdr);

                AcceptHeader accept = getAcceptHeaderForResponse();
                req.setHeader(accept);

                // now for the body
                ContentTypeHeader ct_hdr = ub.getParent().getHeaderFactory()
                        .createContentTypeHeader(getPackageContentType(),
                                getPackageContentSubType());
                req.setContent(body, ct_hdr);
                req.setContentLength(ub.getParent().getHeaderFactory()
                        .createContentLengthHeader(body.length()));

                if (dialog == null)
                {
                    req
                            .setHeader(ub
                                    .getParent()
                                    .getHeaderFactory()
                                    .createCallIdHeader(
                                            "somecallid-"
                                                    + (System
                                                            .currentTimeMillis() % 3600000)));

                    toTag = new Long(Calendar.getInstance().getTimeInMillis())
                            .toString();
                    FromHeader from_header = ub.getParent().getHeaderFactory()
                            .createFromHeader(ub.getAddress(), toTag);
                    req.setHeader(from_header);

                    Address to = ub.getParent().getAddressFactory()
                            .createAddress(
                                    ub.getParent().getAddressFactory()
                                            .createSipURI(toUser, toDomain));
                    ToHeader to_header = ub.getParent().getHeaderFactory()
                            .createToHeader(to, null);
                    req.setHeader(to_header);

                    CSeqHeader cseq = ub.getParent().getHeaderFactory()
                            .createCSeqHeader((long) 14, Request.NOTIFY);
                    req.setHeader(cseq);

                    MaxForwardsHeader max_forwards = ub.getParent()
                            .getHeaderFactory().createMaxForwardsHeader(
                                    SipPhone.MAX_FORWARDS_DEFAULT);
                    req.setHeader(max_forwards);

                    ArrayList<ViaHeader> via_headers = ub.getViaHeaders();
                    Iterator<ViaHeader> i = via_headers.iterator();
                    while (i.hasNext())
                    {
                        req.addHeader((Header) i.next());
                    }

                    req.addHeader((ContactHeader) ub.getContactInfo()
                            .getContactHeader().clone());
                }
            }
        }
        catch (Exception e)
        {
            setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
        }

        return null;
    }

    /**
     * This method sends the given request to the subscriber. Knowledge of
     * JAIN-SIP API headers is required. The request will be resent if
     * challenged. Use this method only if you have previously called
     * processSubscribe(). Use this method if you don't care about checking the
     * response to the sent NOTIFY, otherwise use sendStatefulNotify().
     * 
     * @param req
     *            javax.sip.message.Request to send.
     * @param viaProxy
     *            If true, send the message to the proxy. In this case a Route
     *            header will be added. Else send the message as is.
     * @return true if successful, false otherwise (call getErrorMessage() for
     *         details).
     */
    public boolean sendNotify(Request req, boolean viaProxy)
    {
        setErrorMessage("");

        synchronized (dialogLock)
        {
            if (dialog == null)
            {
                setErrorMessage("Can't send notify, haven't received a request");
                return false;
            }

            try
            {
                ub.addAuthorizations(((CallIdHeader) req
                        .getHeader(CallIdHeader.NAME)).getCallId(), req);

                SipTransaction transaction = ub.sendRequestWithTransaction(req,
                        viaProxy, dialog, this);
                if (transaction == null)
                {
                    setErrorMessage(ub.getErrorMessage());
                    return false;
                }

                setLastSentNotify(req);

                SipStack.trace("Sent NOTIFY to "
                        + dialog.getRemoteParty().getURI().toString() + ": \n"
                        + req.toString());

                return true;
            }
            catch (Exception e)
            {
                setErrorMessage(e.getClass().getName() + ": " + e.getMessage());
            }
        }

        return false;

    }

    /**
     * This method sends the given request to the subscriber. Use this method
     * when you want to see the response received in reply to the NOTIFY sent by
     * this method. Authentication challenges received in reponse to the sent
     * request are not automatically handled by this class - the caller will
     * have to check for and handle it (TODO, provide a method that does like
     * processRespons() for the caller to use). Knowledge of JAIN-SIP API
     * headers is required to use this method. You may call this method whether
     * or not this object has received a request (ie, whether or not you have
     * previously called processSubscribe() on this object.) You may
     * subsequently call waitResponse() to check the response returned by the
     * far end.
     * 
     * @param req
     *            javax.sip.message.Request to send.
     * @param viaProxy
     *            If true, send the message to the proxy. In this case a Route
     *            header will be added. Else send the message as is.
     * @return A SipTransaction object if the message was sent successfully,
     *         null otherwise. The calling program doesn't need to do anything
     *         with the returned SipTransaction other than pass it in to a
     *         subsequent call to waitResponse().
     */
    public SipTransaction sendStatefulNotify(Request req, boolean viaProxy)
    {
        setErrorMessage("");
        SipTransaction transaction;

        synchronized (dialogLock)
        {
            transaction = ub.sendRequestWithTransaction(req, viaProxy, dialog);
            if (transaction == null)
            {
                setErrorMessage(ub.getErrorMessage());
                return null;
            }

            // enable auth challenge handling
            ub.enableAuthorization(((CallIdHeader) req
                    .getHeader(CallIdHeader.NAME)).getCallId());

            dialog = transaction.getClientTransaction().getDialog();
            setLastSentNotify(req);
        }

        SipStack.trace("Sent NOTIFY " + req.getHeader(ToHeader.NAME));

        return transaction;
    }

    /**
     * The waitResponse() method waits for a response to a previously sent
     * transactional request message. Call this method after calling
     * sendStatefulNotify().
     * 
     * This method blocks until one of the following occurs: 1) A
     * javax.sip.ResponseEvent is received. This is the object returned by this
     * method. 2) A javax.sip.TimeoutEvent is received. This is the object
     * returned by this method. 3) The wait timeout period specified by the
     * parameter to this method expires. Null is returned in this case. 4) An
     * error occurs. Null is returned in this case.
     * 
     * Note that this method can be called repeatedly upon receipt of
     * provisional response message(s).
     * 
     * @param trans
     *            The SipTransaction object associated with the sent request.
     *            This is the object returned by sendStatefulNotify().
     * @param timeout
     *            The maximum amount of time to wait, in milliseconds. Use a
     *            value of 0 to wait indefinitely.
     * @return A javax.sip.ResponseEvent, javax.sip.TimeoutEvent, or null in the
     *         case of wait timeout or error. If null, call getReturnCode()
     *         and/or getErrorMessage() and, if applicable, getException() for
     *         further diagnostics.
     */
    public EventObject waitResponse(SipTransaction trans, long timeout)
    {
        return ub.waitResponse(trans, timeout);
    }

    /**
     * This method registers this notify sender as a UA with the proxy/registrar
     * used to create the SipPhone passed to this object's contructor.
     * 
     * @param credential
     *            authentication information matching that at the server
     * @return true if registration is successful, false otherwise. If false,
     *         call getErrorMessage() to find out why.
     */
    public boolean register(Credential credential)
    {
        ub.addUpdateCredential(credential);
        if (ub.register(null, 3600) == false)
        {
            setErrorMessage(ub.format());
            return false;
        }

        return true;
    }

    /**
     * Returns the error message, if any, associated with the last operation.
     * 
     * @return A String describing the error that occurred during the last
     *         operation, or an empty string ("") if there was no error.
     */
    public String getErrorMessage()
    {
        return errorMessage;
    }

    protected void setErrorMessage(String errorMessage)
    {
        if (errorMessage.length() > 0)
        {
            SipStack.trace("PresenceNotifySender error : " + errorMessage);
        }
        this.errorMessage = errorMessage;
    }

    /**
     * Returns the NOTIFY request that was last sent, or null if none has ever
     * been sent.
     * 
     * @return javax.sip.message.Request last sent.
     */
    public Request getLastSentNotify()
    {
        return lastSentNotify;
    }

    protected void setLastSentNotify(Request lastSentNotify)
    {
        this.lastSentNotify = (Request) lastSentNotify.clone();
    }

    /*
     * Not implemented for this class. Returns empty ArrayList.
     */
    public ArrayList<SipResponse> getAllReceivedResponses()
    {
        return new ArrayList<SipResponse>();
    }

    /*
     * Not implemented for this class. Returns empty ArrayList.
     */
    public ArrayList<SipRequest> getAllReceivedRequests()
    {
        return new ArrayList<SipRequest>();
    }

    /*
     * Not implemented for this class. Returns null.
     */
    public SipRequest getLastReceivedRequest()
    {
        return null;
    }

    /*
     * Not implemented for this class. Returns null.)
     */
    public SipResponse getLastReceivedResponse()
    {
        return null;
    }

    /*
     * @see
     * org.cafesip.sipunit.RequestListener#processEvent(java.util.EventObject)
     */
    public void processEvent(EventObject event)
    {
        // Don't need to do anything except resend if challenged
        // (we only get here for a stateless NOTIFY)

        if (event instanceof ResponseEvent)
        {
            resendWithAuthorization((ResponseEvent) event);
        }
    }

    /**
     * Called to find out if a sent NOTIFY was challenged. See related method
     * resendWithAuthorization().
     * 
     * @param event
     *            object returned by waitResponse().
     * @return true if response status is UNAUTHORIZED or
     *         PROXY_AUTHENTICATION_REQUIRED, false otherwise.
     */
    public boolean needAuthorization(ResponseEvent event)
    {
        Response response = event.getResponse();
        int status = response.getStatusCode();

        if ((status == Response.UNAUTHORIZED)
                || (status == Response.PROXY_AUTHENTICATION_REQUIRED))
        {
            return true;
        }

        return false;
    }

    /**
     * This method resends a NOTIFY statefully and with required authorization
     * headers. Call it after you find out that authorization is required by
     * calling method needAuthorization().
     * 
     * Example testcode usage (this object is "sender"): // get the response,
     * trans is SipTransaction object EventObject event =
     * sender.waitResponse(trans, 2000); assertNotNull(sender.getErrorMessage(),
     * event);
     * 
     * if (event instanceof TimeoutEvent) { fail("Event Timeout received by far
     * end while waiting for NOTIFY response"); }
     * 
     * assertTrue("Expected auth challenge", sender
     * .needAuthorization((ResponseEvent) event)); trans =
     * sender.resendWithAuthorization((ResponseEvent) event);
     * assertNotNull(sender.getErrorMessage(), trans); // get the next response
     * event = sender.waitResponse(trans, 2000); etc.
     * 
     * @param event
     *            object returned by waitResponse()
     * @return SipTransaction for internal use, only needs to be passed to
     *         waitResponse().
     */

    public SipTransaction resendWithAuthorization(ResponseEvent event)
    {
        Response response = event.getResponse();
        int status = response.getStatusCode();

        if ((status == Response.UNAUTHORIZED)
                || (status == Response.PROXY_AUTHENTICATION_REQUIRED))
        {
            try
            {
                // modify the request to include user authorization info and
                // resend

                synchronized (dialogLock)
                {
                    Request msg = getLastSentNotify();
                    msg = ub.processAuthChallenge(response, msg);
                    if (msg == null)
                    {
                        setErrorMessage("PresenceNotifySender: Error responding to authentication challenge: "
                                + ub.getErrorMessage());
                        return null;
                    }

                    // bump up the sequence number
                    CSeqHeader hdr = (CSeqHeader) msg
                            .getHeader(CSeqHeader.NAME);
                    long cseq = hdr.getSeqNumber();
                    hdr.setSeqNumber(cseq + 1);

                    // send the message
                    SipTransaction transaction = ub.sendRequestWithTransaction(
                            msg, false, dialog);
                    if (transaction == null)
                    {
                        setErrorMessage("Error resending NOTIFY with authorization: "
                                + ub.getErrorMessage());
                        return null;
                    }

                    dialog = transaction.getClientTransaction().getDialog();
                    setLastSentNotify(msg);

                    SipStack.trace("Resent REQUEST: " + msg.toString());

                    return transaction;
                }
            }
            catch (Exception ex)
            {
                setErrorMessage("Exception resending NOTIFY with authorization: "
                        + ex.getClass().getName() + ": " + ex.getMessage());
            }
        }

        return null;
    }

}