package org.cafesip.sipunit.processing.notify;

import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;

import javax.sip.RequestEvent;
import javax.sip.header.EventHeader;
import javax.sip.message.Request;

/**
 * Checks that the received event in {@link EventHeader} is a {@value ConferenceEventStrategy#EVENT_NAME} event. If that
 * is the case, then the receiver object will continue the event handling (i.e. this handler does not do any other
 * processing for this event).
 * <p>
 * Created by TELES AG on 15/01/2018.
 */
public final class ConferenceEventStrategy extends RequestProcessingStrategy<SipPhone> {

	private static final String EVENT_NAME = "conference";

	@Override
	public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipPhone receiver) {
		LOG.trace("Running " + this.getClass().getName());
		Request request = requestEvent.getRequest();
		EventHeader event = (EventHeader) request.getHeader(EventHeader.NAME);

		// Just return so that the test can use waitRequest()
		boolean isValid = event != null && event.getEventType().equals(EVENT_NAME);
		return new RequestProcessingResult(isValid, isValid);
	}
}
