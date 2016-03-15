/*
 * Created on Apr 9, 2009
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

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.sip.header.AcceptHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.EventHeader;
import javax.sip.message.Request;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.ValidationEvent;
import javax.xml.bind.ValidationEventHandler;

import org.cafesip.sipunit.presenceparser.pidf.Contact;
import org.cafesip.sipunit.presenceparser.pidf.Note;
import org.cafesip.sipunit.presenceparser.pidf.Presence;
import org.cafesip.sipunit.presenceparser.pidf.Tuple;

/**
 * The PresenceSubscriber class represents a buddy from a SipPhone buddy list or a single-shot
 * presence fetch performed by a SipPhone. This object is used by a test program to proceed through
 * the SUBSCRIBE-NOTIFY sequence(s) and to find out details at any given time about the subscription
 * such as the subscription state, amount of time left on the subscription if still active,
 * termination reason if terminated, errors encountered during received SUBSCRIBE response and
 * incoming NOTIFY message validation, details of any received responses and requests if needed by
 * the test program, and the current or last known presence status/information (tuples/devices,
 * notes, etc.) for this subscription.
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
 * @author Becky McElroy
 * 
 */
public class PresenceSubscriber extends EventSubscriber {

  private static final Logger LOG = LoggerFactory.getLogger(PresenceSubscriber.class);

  /*
   * List of zero or more PresenceDeviceInfo objects (active devices) for this Subscription
   * buddy/watchee, indexed by the IDs received in the NOTIFY tuples
   */
  private HashMap<String, PresenceDeviceInfo> devices = new HashMap<>();

  /*
   * List of zero or more PresenceNote objects received in the NOTIFY body
   */
  private ArrayList<PresenceNote> presenceNotes = new ArrayList<>();

  /*
   * List of zero or more Object received in a NOTIFY message
   */
  private ArrayList<Object> presenceExtensions = new ArrayList<>();

  /**
   * A constructor for this class. Used internally by SipUnit. Test programs should call the
   * SipPhone.addBuddy() or fetchPresenceInfo() method to create a subscription.
   * 
   * @param uri
   * @param parent
   * @throws ParseException
   */
  public PresenceSubscriber(String uri, SipPhone parent) throws ParseException {
    super(uri, parent);
  }

  /**
   * This method is the same as EventSubscriber.createSubscribeMessage() except there's no need for
   * the caller to supply the eventType parameter.
   * 
   * @param duration the duration in seconds to put in the SUBSCRIBE message.
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later on another SipPhone buddy method call
   *        (refreshBuddy(), removeBuddy(), fetch, etc.).
   * @return a SUBSCRIBE request
   */
  public Request createSubscribeMessage(int duration, String eventId) {
    return super.createSubscribeMessage(duration, eventId, "presence");
  }

  protected boolean expiresResponseHeaderApplicable() {
    return true;
  }

  protected void checkEventType(EventHeader receivedHdr) throws SubscriptionError {
    String event = receivedHdr.getEventType();
    if (event.equals("presence") == false) {
      throw new SubscriptionError(SipResponse.BAD_EVENT,
          "received a presence event header containing unknown event = " + event);
    }
  }

  protected void updateEventInfo(Request request) throws SubscriptionError {
    byte[] bodyBytes = request.getRawContent();
    if (bodyBytes == null) {
      return;
    }

    // check for supported content type, currently supporting only the
    // package default

    ContentTypeHeader ct = (ContentTypeHeader) request.getHeader(ContentTypeHeader.NAME);

    if (ct == null) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "NOTIFY body has bytes but no content type header was received");
    }

    if (ct.getContentType().equals("application") == false) {
      throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
          "received NOTIFY body with unsupported content type = " + ct.getContentType());
    } else if (ct.getContentSubType().equals("pidf+xml") == false) {
      throw new SubscriptionError(SipResponse.UNSUPPORTED_MEDIA_TYPE,
          "received NOTIFY body with unsupported content subtype = " + ct.getContentSubType());
    }

    // parse and get info from the xml body

    try {
      Unmarshaller parser =
          JAXBContext.newInstance("org.cafesip.sipunit.presenceparser.pidf").createUnmarshaller();
      parser.setEventHandler(new ValidationEventHandler() {
        public boolean handleEvent(ValidationEvent arg0) {
          if (arg0.getMessage().startsWith("Unexpected element")) {
            return true;
          }

          return false;
        }
      });
      Presence doc = (Presence) parser.unmarshal(new ByteArrayInputStream(bodyBytes));

      // is it the correct presentity?

      if (!targetUri.equals(doc.getEntity())) {
        throw new SubscriptionError(SipResponse.BAD_REQUEST,
            "received NOTIFY body with wrong presentity = " + doc.getEntity());
      }

      // finally, update our presence information

      devices.clear();
      if (doc.getTuple() != null) {
        Iterator<?> i = doc.getTuple().iterator();
        while (i.hasNext()) {
          Tuple t = (Tuple) i.next();

          PresenceDeviceInfo dev = new PresenceDeviceInfo();
          dev.setBasicStatus(t.getStatus().getBasic());

          Contact contact = t.getContact();
          if (contact != null) {
            if (contact.getPriority() != null) {
              dev.setContactPriority(contact.getPriority().doubleValue());
            }
            dev.setContactValue(contact.getValue());
          }

          dev.setDeviceExtensions(t.getAny());
          dev.setId(t.getId());
          dev.setStatusExtensions(t.getStatus().getAny());
          dev.setTimestamp(t.getTimestamp());

          List<PresenceNote> notes = new ArrayList<>();
          if (t.getNote() != null) {
            Iterator<?> j = t.getNote().iterator();
            while (j.hasNext()) {
              Note n = (Note) j.next();
              notes.add(new PresenceNote(n.getLang(), n.getValue()));
            }
          }
          dev.setDeviceNotes(notes);

          devices.put(t.getId(), dev);
        }
      }

      presenceNotes.clear();
      if (doc.getNote() != null) {
        Iterator<?> i = doc.getNote().iterator();
        while (i.hasNext()) {
          Note n = (Note) i.next();
          presenceNotes.add(new PresenceNote(n.getLang(), n.getValue()));
        }
      }

      presenceExtensions.clear();
      if (doc.getAny() != null) {
        presenceExtensions.addAll((Collection<?>) doc.getAny());
      }

      LOG.trace("Successfully processed NOTIFY message body for Subscription to " + targetUri);
    } catch (Exception e) {
      throw new SubscriptionError(SipResponse.BAD_REQUEST,
          "NOTIFY body parsing error : " + e.getMessage());
    }

    return;
  }

  protected AcceptHeader getUnsupportedMediaAcceptHeader() throws ParseException {
    return parent.getHeaderFactory().createAcceptHeader("application", "pidf+xml");
  }

  /**
   * Gets the list of known devices for this Subscription (buddy, watchee). This list represents the
   * list of 'tuple's received in the last NOTIFY message.
   * 
   * @return A HashMap containing zero or more PresenceDeviceInfo objects, indexed/keyed by the
   *         unique IDs received for each in the NOTIFY messages (tuple elements).
   */
  public HashMap<String, PresenceDeviceInfo> getPresenceDevices() {
    return new HashMap<>(devices);
  }

  /**
   * Gets the list of notes pertaining to this Subscription as received in the last NOTIFY message
   * (at the top 'presence' element level).
   * 
   * @return An ArrayList containing zero or more PresenceNote objects.
   */
  public ArrayList<PresenceNote> getPresenceNotes() {
    return new ArrayList<>(presenceNotes);
  }

  /**
   * Gets the list of extensions pertaining to this Subscription as received in the last NOTIFY
   * message (at the top 'presence' element level).
   * 
   * @return An ArrayList containing zero or more Object.
   */
  public List<Object> getPresenceExtensions() {
    return new ArrayList<>(presenceExtensions);
  }

  /**
   * This method initiates a SUBSCRIBE/NOTIFY sequence for the purpose of updating the presence
   * information for this buddy. This method creates a SUBSCRIBE request message based on the
   * parameters passed in, sends out the request, and waits for a response to be received. It saves
   * the received response and checks for a "proceedable" (positive) status code value. Positive
   * response status codes include any of the following: provisional (status / 100 == 1),
   * UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED. Any other status code, or a
   * response timeout or any other error, is considered fatal to this refresh operation.
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
   *        subscription time left to. If it is 0, this is an unsubscribe (note, the buddy stays in
   *        the SipPhone's buddy list even though the subscription won't be active).
   * @param eventId the event "id" to use in the SUBSCRIBE message, or null for no event "id"
   *        parameter. Whatever is indicated here will be used subsequently (for error checking
   *        SUBSCRIBE responses and NOTIFYs from the server as well as for sending subsequent
   *        SUBSCRIBEs) unless changed by the caller later on another buddy method call.
   * @param timeout The maximum amount of time to wait for a SUBSCRIBE response, in milliseconds.
   *        Use a value of 0 to wait indefinitely.
   * @return true if the refresh operation is successful so far, false otherwise. False just means
   *         this SUBSCRIBE sequence failed - this buddy remains in the SipPhone's buddy list. See
   *         more details above.
   */
  public boolean refreshBuddy(int duration, String eventId, long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy refresh for URI " + targetUri
          + " failed, URI was not found in the active buddy list. Use SipPhone.fetchPresenceInfo() for users not in the buddy list");

      return false;
    }

    Request req = createSubscribeMessage(duration, eventId);

    if (req == null) {
      return false;
    }

    return refreshBuddy(req, timeout);

  }

  /**
   * This method is the same as refreshBuddy(duration, eventId, timeout) except that the SUBSCRIBE
   * duration sent will be however much time is left on the current subscription. If time left on
   * the subscription &lt;= 0, unsubscribe occurs (note, the buddy stays in the list).
   */
  public boolean refreshBuddy(String eventId, long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy refresh for URI " + targetUri + " failed, not found in buddy list.");
      return false;
    }

    return refreshBuddy(getTimeLeft(), eventId, timeout);
  }

  /**
   * This method is the same as refreshBuddy(duration, eventId, timeout) except that the eventId
   * remains unchanged from whatever it already was.
   */
  public boolean refreshBuddy(int duration, long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy refresh for URI " + targetUri + " failed, not found in buddy list.");
      return false;
    }

    return refreshBuddy(duration, getEventId(), timeout);
  }

  /**
   * This method is the same as refreshBuddy(duration, eventId, timeout) except that the eventId
   * remains unchanged from whatever it already was and the SUBSCRIBE duration sent will be however
   * much time is left on the current subscription. If time left on the subscription &lt;= 0,
   * unsubscribe occurs (note, the buddy stays in the list).
   */
  public boolean refreshBuddy(long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy refresh for URI " + targetUri + " failed, not found in buddy list.");
      return false;
    }

    return refreshBuddy(getTimeLeft(), getEventId(), timeout);
  }

  /**
   * This method is the same as refreshBuddy(duration, eventId, timeout) except that instead of
   * creating the SUBSCRIBE request from parameters passed in, the given request message parameter
   * is used for sending out the SUBSCRIBE message.
   * 
   * <p>
   * The Request parameter passed into this method should come from calling createSubscribeMessage()
   * - see that javadoc. The subscription duration is reset to the passed in Request's expiry value.
   * If it is 0, this is an unsubscribe. Note, the buddy stays in the buddy list even though the
   * subscription won't be active. The event "id" in the given request will be used subsequently
   * (for error checking SUBSCRIBE responses and NOTIFYs from the server as well as for sending
   * subsequent SUBSCRIBEs).
   */
  public boolean refreshBuddy(Request req, long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy refresh for URI " + targetUri
          + " failed, uri was not found in the buddy list. Use fetchPresenceInfo() for users not in the buddy list");

      return false;
    }

    return refreshSubscription(req, timeout, parent.getProxyHost() != null);
  }

  /**
   * This method removes this buddy from the SipPhone buddy list and initiates a SUBSCRIBE/NOTIFY
   * sequence to terminate the subscription unless the subscription is already terminated.
   * Regardless, this buddy is taken out of the active buddy list and put into the retired buddy
   * list (which is a list of PresenceSubscriber objects of buddies that have been removed from the
   * buddy list and PresenceSubscriber objects for individual fetch operations that have been done).
   * A retired buddy's PresenceSubscriber object continues to be valid and accessible via
   * SipPhone.getBuddyInfo().
   * 
   * <p>
   * If the subscription is active when this method is called, this method creates a SUBSCRIBE
   * request message based on the parameters passed in, sends out the request, and waits for a
   * response to be received. It saves the received response and checks for a "proceedable"
   * (positive) status code value. Positive response status codes include any of the following:
   * provisional (status / 100 == 1), UNAUTHORIZED, PROXY_AUTHENTICATION_REQUIRED, OK and ACCEPTED.
   * Any other status code, or a response timeout or any other error, is considered fatal to the
   * unsubscribe operation.
   * 
   * <p>
   * This method blocks until one of the above outcomes is reached.
   * 
   * <p>
   * If this method returns true, it means a positive response was received or the unsubscribe
   * sequence was not required. In order for you to know which is the case (and whether or not to
   * proceed forward with the SUBSCRIBE/NOTIFY sequence processing), call isRemovalComplete(). It
   * will tell you if an unsubscribe sequence was initiated or not. If not, you are done. Otherwise
   * you can find out about the positive response by calling this object's getReturnCode() and/or
   * getCurrentResponse() or getLastReceivedResponse() methods. Your next step will be to call the
   * processResponse() method to proceed with the unsubscribe sequence. See the processResponse()
   * javadoc for more details.
   * 
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
   *         otherwise. See more details above. In either case, the buddy is removed from the buddy
   *         list.
   */
  public boolean removeBuddy(String eventId, long timeout) {
    if (parent.getBuddyList().get(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy removal for URI " + targetUri + " failed, not found in buddy list.");
      return false;
    }

    Request req = createSubscribeMessage(0, eventId);

    if (req == null) {
      return false;
    }

    return removeBuddy(req, timeout);
  }

  /**
   * This method is the same as removeBuddy(eventId, timeout) except that no event "id" parameter
   * will be included in the unSUBSCRIBE message. When error checking the SUBSCRIBE response and
   * NOTIFY from the server, no event "id" parameter will be expected.
   */
  public boolean removeBuddy(long timeout) {
    return removeBuddy((String) null, timeout);
  }

  /**
   * This method is the same as removeBuddy(eventId, timeout) except that instead of creating the
   * SUBSCRIBE request from parameters passed in, the given request message parameter is used for
   * sending out the SUBSCRIBE message if the subscription is active.
   * 
   * <p>
   * The Request parameter passed into this method should come from calling createSubscribeMessage()
   * - see that javadoc. The event "id" in the given request will be used subsequently for error
   * checking the SUBSCRIBE response and NOTIFY request from the server.
   */
  public boolean removeBuddy(Request req, long timeout) {
    initErrorInfo();

    if (parent.retireBuddy(targetUri) == null) {
      setReturnCode(SipSession.INVALID_ARGUMENT);
      setErrorMessage("Buddy removal for URI " + targetUri + " failed, not found in buddy list.");
      return false;
    }

    if (endSubscription(req, timeout, parent.getProxyHost() != null,
        "Buddy removed from contact list") == true) {
      return true;
    }

    // unsubscribe failed
    return false;
  }
}
