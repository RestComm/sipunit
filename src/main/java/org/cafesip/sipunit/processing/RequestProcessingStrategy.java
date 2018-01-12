package org.cafesip.sipunit.processing;

import org.cafesip.sipunit.SipSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.SipListener;

/**
 * The request matching strategy is used within every {@link SipListener} subclass which is bound to process an incoming
 * request.
 * In order to provide multiple ways of accepting a request from various sources, we introduce this type to provide an
 * unified way to determine if a request fits a criterion for it to be accepted and processed within a session.
 * <p>
 * Created by TELES AG on 12/01/2018.
 *
 * @see RequestProcessor
 * @see SipSession#processRequest(RequestEvent)
 */
public abstract class RequestProcessingStrategy<ReceiverType extends SipListener> {

	protected static final Logger LOG = LoggerFactory.getLogger(RequestProcessingStrategy.class);

	private final boolean multipleInstanceAllowed;

	/**
	 * Initialize this strategy with multiple instances of this class allowed to be present in {@link RequestProcessor}
	 */
	public RequestProcessingStrategy() {
		this(true);
	}

	/**
	 * Initialize this strategy with option of multiple instances of this class to be present in {@link RequestProcessor}
	 *
	 * @param multipleInstanceAllowed If set to true, the processor will allow multiple instances of this class. Otherwise,
	 *                                any additional instances will not be added to the processor, and will be treated as
	 *                                a localized singleton.
	 */
	public RequestProcessingStrategy(boolean multipleInstanceAllowed) {
		this.multipleInstanceAllowed = multipleInstanceAllowed;
	}

	/**
	 * @return If true, the matcher will allow multiple instances of this class. Otherwise, any additional instances
	 * will not be added to the processor, and will be treated as a localized singleton.
	 */
	public final boolean multipleInstanceAllowed() {
		return multipleInstanceAllowed;
	}

	/**
	 * Determines if the inbound request is processed according to the criterion defined by this strategy.
	 *
	 * @param requestEvent The inbound request event
	 * @param receiver     The governing receiver handler which received the request through its {@link org.cafesip.sipunit.SipStack}
	 * @return The result of the processing. A request may be accepted, but it may not be successful. If the request is accepted,
	 * no other strategies in the governing processor will execute.
	 */
	public abstract RequestProcessingResult processRequestEvent(final RequestEvent requestEvent, final ReceiverType receiver);
}
