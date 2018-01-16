package org.cafesip.sipunit.processing.matching;

import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;

import javax.sip.RequestEvent;
import javax.sip.address.SipURI;
import javax.sip.message.Request;

/**
 * Determines if the request is viable for processing base on the {@link Request#getRequestURI()} of the received {@link Request}
 * <p>
 * Created by TELES AG on 09/01/2018.
 */
public final class RequestUriMatchingStrategy extends RequestProcessingStrategy<SipSession> {

	public RequestUriMatchingStrategy(){
		super(false);
	}

	@Override
	public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipSession receiver) {
		LOG.trace("my local contact info ('Request URI' check) = {}", receiver.getContactInfo().getURI());
		Request request = requestEvent.getRequest();

		boolean isRequestMatching = isSipUriEquals((SipURI) receiver.getContactInfo().getContactHeader().getAddress().getURI(),
				(SipURI) request.getRequestURI());

		return new RequestProcessingResult(isRequestMatching, isRequestMatching);
	}

	private static boolean isSipUriEquals(SipURI uri1, SipURI uri2) {
		if (uri1.getScheme().equalsIgnoreCase(uri2.getScheme())) {
			if (uri1.getUser() != null) {
				if (uri2.getUser() == null) {
					return false;
				}

				if (uri1.getUser().equals(uri2.getUser()) == false) {
					return false;
				}

				if (uri1.getUserPassword() != null) {
					if (uri2.getUserPassword() == null) {
						return false;
					}

					if (uri1.getUserPassword().equals(uri2.getUserPassword()) == false) {
						return false;
					}
				} else if (uri2.getUserPassword() != null) {
					return false;
				}
			} else if (uri2.getUser() != null) {
				return false;
			}

			if (uri1.getHost().equalsIgnoreCase(uri2.getHost()) == false) {
				return false;
			}

			if (uri1.toString().indexOf(uri1.getHost() + ':') != -1) {
				if (uri2.toString().indexOf(uri2.getHost() + ':') == -1) {
					return false;
				}

				if (uri1.getPort() != uri2.getPort()) {
					return false;
				}
			} else if (uri2.toString().indexOf(uri2.getHost() + ':') != -1) {
				return false;
			}

			// FOR A FULL URI-EQUAL CHECK, add the following:
			/*
			 * if (uri1.getTransportParam() != null) { if (uri2.getTransportParam() == null) { return
			 * false; }
			 *
			 * if (uri1.getTransportParam().equals(uri2.getTransportParam()) == false) { return false; } }
			 * else if (uri2.getTransportParam() != null) { return false; }
			 *
			 * if (uri1.getTTLParam() != -1) { if (uri2.getTTLParam() == -1) { return false; }
			 *
			 * if (uri1.getTTLParam() != uri2.getTTLParam()) { return false; } } else if
			 * (uri2.getTTLParam() != -1) { return false; }
			 *
			 * if (uri1.getMethodParam() != null) { if (uri2.getMethodParam() == null) { return false; }
			 *
			 * if (uri1.getMethodParam().equals(uri2.getMethodParam()) == false) { return false; } } else
			 * if (uri2.getMethodParam() != null) { return false; } / next - incorporate the following
			 * remaining checks:
			 *
			 * URI uri-parameter components are compared as follows: - Any uri-parameter appearing in both
			 * URIs must match. - A user, ttl, or method uri-parameter appearing in only one URI never
			 * matches, even if it contains the default value. - A URI that includes an maddr parameter
			 * will not match a URI that contains no maddr parameter. - All other uri-parameters appearing
			 * in only one URI are ignored when comparing the URIs.
			 *
			 * o URI header components are never ignored. Any present header component MUST be present in
			 * both URIs and match for the URIs to match. The matching rules are defined for each header
			 * field in Section 20.
			 */

			return true;
		}

		return false;
	}
}
