package org.cafesip.sipunit.processing.matching;

import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;

import javax.sip.RequestEvent;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;

/**
 * Determines if the request is viable for processing base on the {@link ToHeader} of the received {@link Request} in
 * {@link SipSession#processRequest(RequestEvent)}
 * <p>
 * Created by TELES AG on 09/01/2018.
 */
public final class ToMatchingStrategy extends RequestProcessingStrategy<SipSession> {

	public ToMatchingStrategy(){
		super(false);
	}

	@Override
	public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipSession receiver) {
		Request request = requestEvent.getRequest();
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);

		String me = receiver.getAddress().getURI().toString();
		String expected = to.getAddress().getURI().toString();

		LOG.trace("me ('To' check) = {}", me);

		boolean isMatching = expected.equals(me);
		RequestProcessingResult result = new RequestProcessingResult(isMatching, isMatching);

		return result;
	}
}
