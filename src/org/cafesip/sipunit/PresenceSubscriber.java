/*
 * Created on Apr 9, 2009
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
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

import javax.sip.header.AcceptHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.message.Request;
import javax.xml.bind.JAXBContext;

import org.cafesip.sipunit.presenceparser.pidf.Contact;
import org.cafesip.sipunit.presenceparser.pidf.Note;
import org.cafesip.sipunit.presenceparser.pidf.Presence;
import org.cafesip.sipunit.presenceparser.pidf.Tuple;

/**
 * The PresenceSubscriber class represents a buddy from a SipPhone buddy list or
 * a single-shot presence fetch performed by a SipPhone. This object is used by
 * a test program to proceed through the SUBSCRIBE-NOTIFY sequence(s) and to
 * find out details at any given time about the subscription such as the
 * subscription state, amount of time left on the subscription if still active,
 * termination reason if terminated, errors encountered during received
 * SUBSCRIBE response and incoming NOTIFY message validation, details of any
 * received responses and requests if needed by the test program, and the
 * current or last known presence status/information (tuples/devices, notes,
 * etc.) for this subscription.
 * <p>
 * Please read the SipUnit User Guide, Event Subscription section for the
 * NOTIFY-receiving side (at least the operation overview part) for information
 * on how to use the methods of this class and its superclass.
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
public class PresenceSubscriber extends SubscriptionSubscriber
{
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

    /**
     * A constructor for this class.
     * 
     * @param uri
     * @param parent
     * @throws ParseException
     */
    public PresenceSubscriber(String uri, SipPhone parent)
            throws ParseException
    {
        super(uri, parent);
    }

    /**
     * This method is the same as
     * SubscriptionSubscriber.createSubscribeMessage() except there's no need
     * for the caller to supply the eventType parameter.
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
     * @return
     */
    public Request createSubscribeMessage(int duration, String eventId)
    {
        return super.createSubscribeMessage(duration, eventId, "presence");
    }

    protected void checkEventType(EventHeader receivedHdr)
            throws SubscriptionError
    {
        String event = receivedHdr.getEventType();
        if (event.equals("presence") == false)
        {
            throw new SubscriptionError(SipResponse.BAD_EVENT,
                    "received an event header containing unknown event = "
                            + event);
        }
    }

    protected void updateEventInfo(Request request) throws SubscriptionError
    {
        byte[] bodyBytes = request.getRawContent();
        if (bodyBytes == null)
        {
            return;
        }

        // check for supported content type, currently supporting only the
        // package default

        ContentTypeHeader ct = (ContentTypeHeader) request
                .getHeader(ContentTypeHeader.NAME);

        if (ct == null)
        {
            throw new SubscriptionError(SipResponse.BAD_REQUEST,
                    "NOTIFY body has bytes but no content type header was received");
        }

        if (ct.getContentType().equals("application") == false)
        {
            throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
                    "received NOTIFY body with unsupported content type = "
                            + ct.getContentType());
        }
        else if (ct.getContentSubType().equals("pidf+xml") == false)
        {
            throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
                    "received NOTIFY body with unsupported content subtype = "
                            + ct.getContentSubType());
        }

        // parse and get info from the xml body

        try
        {
            Presence doc = (Presence) JAXBContext.newInstance(
                    "org.cafesip.sipunit.presenceparser.pidf")
                    .createUnmarshaller().unmarshal(
                            new ByteArrayInputStream(bodyBytes));

            // is it the correct presentity?

            if (!targetUri.equals(doc.getEntity()))
            {
                throw new SubscriptionError(SipResponse.BAD_REQUEST,
                        "received NOTIFY body with wrong presentity = "
                                + doc.getEntity());
            }

            // finally, update our presence information

            devices.clear();
            if (doc.getTuple() != null)
            {
                Iterator<?> i = doc.getTuple().iterator();
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
                        Iterator<?> j = t.getNote().iterator();
                        while (j.hasNext())
                        {
                            Note n = (Note) j.next();
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
                Iterator<?> i = doc.getNote().iterator();
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
                presenceExtensions.addAll((Collection<?>) doc.getAny());
            }

            SipStack
                    .trace("Successfully processed NOTIFY message body for Subscription to "
                            + targetUri);
        }
        catch (Exception e)
        {
            throw new SubscriptionError(SipResponse.BAD_REQUEST,
                    "NOTIFY body parsing error : " + e.getMessage());
        }

        return;
    }

    protected AcceptHeader getUnsupportedMediaAcceptHeader()
            throws ParseException
    {
        return parent.getHeaderFactory().createAcceptHeader("application",
                "pidf+xml");
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
}
