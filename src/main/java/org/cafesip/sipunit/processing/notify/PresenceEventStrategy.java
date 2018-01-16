package org.cafesip.sipunit.processing.notify;

import org.cafesip.sipunit.PresenceSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;

import javax.sip.RequestEvent;
import javax.sip.header.EventHeader;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;

/**
 * Checks that the received event in {@link EventHeader} is a {@value PresenceEventStrategy#EVENT_NAME} event. If it is,
 * and the request targets one of the active {@link PresenceSubscriber} subscribers - then this strategy routes the
 * event to the dedicated active subscriber object.
 * <p>
 * Created by TELES AG on 15/01/2018.
 */
public final class PresenceEventStrategy extends RequestProcessingStrategy<SipPhone> {

	private static final String EVENT_NAME = "presence";

	@Override
	public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipPhone receiver) {
		LOG.trace("Running " + this.getClass().getName());
		Request request = requestEvent.getRequest();

		EventHeader event = (EventHeader) request.getHeader(EventHeader.NAME);
		FromHeader from = (FromHeader) request.getHeader(FromHeader.NAME);

		if (event != null && event.getEventType().equals(EVENT_NAME)) {
			PresenceSubscriber presenceSubscriber = receiver.getBuddyInfo(from.getAddress().getURI().toString());
			if (presenceSubscriber != null && presenceSubscriber.messageForMe(request)) {
				presenceSubscriber.processEvent(requestEvent);
				return new RequestProcessingResult(true, true);
			}

			return new RequestProcessingResult(true, false);
		}

		return new RequestProcessingResult(false, true);
	}
}
