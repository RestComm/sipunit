package org.cafesip.sipunit.matching;

import org.cafesip.sipunit.SipSession;

import javax.sip.RequestEvent;
import javax.sip.header.ToHeader;
import javax.sip.message.Request;

/**
 * Determines if the request is viable for processing base on the {@link ToHeader} of the received {@link Request} in
 * {@link SipSession#processRequest(RequestEvent)}
 * <p>
 * Created by TELES AG on 09/01/2018.
 */
public final class ToMatchingStrategy extends RequestMatchingStrategy {
	public ToMatchingStrategy(SipSession managedSession) {
		super(managedSession);
	}

	@Override
	public boolean isRequestMatching(Request request) {
		ToHeader to = (ToHeader) request.getHeader(ToHeader.NAME);

		String me = managedSession.getAddress().getURI().toString();
		String expected = to.getAddress().getURI().toString();

		LOG.trace("me ('To' check) = {}", me);

		return expected.equals(me);
	}
}
