package org.cafesip.sipunit.matching;

import org.cafesip.sipunit.SipSession;

import javax.sip.address.SipURI;
import javax.sip.message.Request;

/**
 * Determines if the request is viable for processing base on the {@link Request#getRequestURI()} of the received {@link Request}
 * <p>
 * Created by TELES AG on 09/01/2018.
 */
public final class RequestUriMatchingStrategy extends RequestMatchingStrategy {
	public RequestUriMatchingStrategy(SipSession managedSession) {
		super(managedSession);
	}

	@Override
	public boolean isRequestMatching(Request request) {
		LOG.trace("my local contact info ('Request URI' check) = {}", managedSession.getContactInfo().getURI());

		return destMatch((SipURI) managedSession.getContactInfo().getContactHeader().getAddress().getURI(),
				(SipURI) request.getRequestURI());
	}
}
