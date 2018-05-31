package org.cafesip.sipunit.processing.notify;

import org.cafesip.sipunit.ReferSubscriber;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;

import javax.sip.Dialog;
import javax.sip.RequestEvent;
import javax.sip.header.EventHeader;
import javax.sip.message.Request;
import java.util.List;

/**
 * Checks that the received event in {@link EventHeader} is a {@value ReferEventStrategy#EVENT_NAME} event. If it is,
 * and the request targets one of the active {@link ReferSubscriber} subscribers - then this strategy routes the
 * event to the dedicated active subscriber object.
 * <p>
 * Created by TELES AG on 15/01/2018.
 */
public final class ReferEventStrategy extends RequestProcessingStrategy<SipPhone> {

	private static final String EVENT_NAME = "refer";

	@Override
	public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipPhone receiver) {
		LOG.trace("Running " + this.getClass().getName());
		Request request = requestEvent.getRequest();
		Dialog dialog = requestEvent.getDialog();

		EventHeader event = (EventHeader) request.getHeader(EventHeader.NAME);

		if (event != null && dialog != null && event.getEventType().equals(EVENT_NAME)) {
			List<ReferSubscriber> refers = receiver.getRefererInfoByDialog(dialog.getDialogId());
			for (ReferSubscriber referSubscriber : refers) {
				if (referSubscriber.messageForMe(request)) {
					referSubscriber.processEvent(requestEvent);
					return new RequestProcessingResult(true, true);
				}
			}

			return new RequestProcessingResult(true, false);
		}

		return new RequestProcessingResult(false, false);
	}
}