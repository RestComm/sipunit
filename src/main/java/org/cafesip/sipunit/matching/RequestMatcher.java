package org.cafesip.sipunit.matching;

import org.cafesip.sipunit.SipSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.message.Request;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class takes care that the inbound requests received by a governing {@link SipSession} are tested against a
 * configurable list of strategies. The {@link RequestMatchingStrategy} instances which this class manages are
 * in charge of testing an inbound SIP {@link Request} and providing feedback if the receiver should accept and process
 * the received request.
 * <p>
 * The request matcher additionally manages that the request matching strategies do not produce side effects when being
 * mutated, and provides concurrent access to the strategy handling mechanism. The default strategy which is provided by
 * this class on instantiation is {@link RequestUriMatchingStrategy}.
 * <p>
 * Created by TELES AG on 11/01/2018.
 *
 * @see RequestMatchingStrategy
 */
public class RequestMatcher {

	private static final Logger LOG = LoggerFactory.getLogger(RequestMatcher.class);

	/**
	 * Request matching strategies determine if an incoming {@link Request} will be accepted for this client after receiving
	 * the request through the stack. This class is initialized with {@link org.cafesip.sipunit.matching.RequestUriMatchingStrategy}}.
	 * The user of this library may add additional matching strategies in order to accept a certain request which has
	 * been formed in different ways depending on the SIP backend configuration.
	 */
	private final List<RequestMatchingStrategy> requestMatchingStrategies = Collections.synchronizedList(new ArrayList<RequestMatchingStrategy>());

	/**
	 * Initialize this matcher with the default {@link RequestUriMatchingStrategy}
	 */
	public RequestMatcher() {
		setDefaultStrategy();
	}

	/**
	 * Initialize this matcher with the provided default strategies
	 *
	 * @param initialStrategies Initial strategies which should be added to the matcher
	 * @see RequestMatcher#add(RequestMatchingStrategy)
	 */
	public RequestMatcher(List<RequestMatchingStrategy> initialStrategies) {
		for (RequestMatchingStrategy requestMatchingStrategy : initialStrategies) {
			add(requestMatchingStrategy);
		}

		setDefaultStrategy();
	}

	/**
	 * Run all configured strategies in this matcher and determine if the request matches any configured criterion.
	 *
	 * @param request    The request being tested for a match with the available strategies
	 * @param sipSession The governing object which received the request
	 * @return If the request matches (true) or not (false)
	 */
	public boolean requestMatches(final Request request, final SipSession sipSession) {
		boolean requestMatches = false;
		synchronized (requestMatchingStrategies) {
			for (RequestMatchingStrategy strategy : requestMatchingStrategies) {
				// If we find a match, then the other strategies will not execute
				requestMatches = requestMatches || strategy.isRequestMatching(request, sipSession);
			}
		}
		return requestMatches;
	}

	/**
	 * Add the strategy to be used in request processing in this matches
	 *
	 * @param requestMatchingStrategy The added request matching strategy
	 * @return If the request matching strategy was successfully added to the list of strategies used by this matcher
	 */
	public boolean add(RequestMatchingStrategy requestMatchingStrategy) {
		// TODO: check if strategy is forced to have only one instance
		return requestMatchingStrategies.add(requestMatchingStrategy);
	}

	/**
	 * Removes the instance of the specified request matching strategy
	 *
	 * @param requestMatchingStrategy The strategy that needs to be removed by its reference in the request matcher
	 * @return True if the specified instance of the searched strategy has been removed, false otherwise
	 * @see List#remove(Object)
	 */
	public boolean remove(RequestMatchingStrategy requestMatchingStrategy) {
		return requestMatchingStrategies.remove(requestMatchingStrategy);
	}

	/**
	 * Removes any existing {@link RequestMatchingStrategy} defined by the searched class in the request matching list.
	 * If this is the only strategy in the strategy list before removal, the strategy list will be set to default,
	 * i.e. be reset with the default {@link RequestUriMatchingStrategy}.
	 *
	 * @param searchedClass The class that needs to be removed by its type in the request matcher
	 * @return True if any instance of the searched strategy has been removed, false otherwise
	 */
	public boolean remove(Class<? extends RequestMatchingStrategy> searchedClass) {
		boolean isRemoved = false;

		synchronized (requestMatchingStrategies) {
			Iterator<RequestMatchingStrategy> it = requestMatchingStrategies.iterator();

			while (it.hasNext()) {
				RequestMatchingStrategy current = it.next();

				if (current.getClass().equals(searchedClass)) {
					isRemoved = true;
					it.remove();
				}
			}

			setDefaultStrategy();
		}

		return isRemoved;
	}

	/**
	 * Check if any existing {@link RequestMatchingStrategy} defined by the searched class is present in the matching
	 * strategy list.
	 *
	 * @param searchedClass The class whose direct instances will be searched for in the request matcher
	 * @return True if any instance of the searched strategy has been found, false otherwise
	 */
	public boolean contains(Class<? extends RequestMatchingStrategy> searchedClass) {
		synchronized (requestMatchingStrategies) {
			for (RequestMatchingStrategy requestMatchingStrategy : requestMatchingStrategies) {
				if (requestMatchingStrategy.getClass().equals(searchedClass)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Check if the {@link RequestMatchingStrategy} is added to the matcher (by reference).
	 *
	 * @return True if the specified instance of the searched strategy has been found, false otherwise
	 * @see List#contains(Object)
	 */
	public boolean contains(RequestMatchingStrategy requestMatchingStrategy) {
		return requestMatchingStrategies.contains(requestMatchingStrategy);
	}

	/**
	 * @return A list of matching strategies for incoming requests which this instance uses to determine
	 * if a request will be accepted or not. This list is unmodifiable and will be updated with each change.
	 */
	public List<RequestMatchingStrategy> getRequestMatchingStrategies() {
		return Collections.unmodifiableList(requestMatchingStrategies);
	}

	/**
	 * Sets the default {@link RequestUriMatchingStrategy} if the available strategy list is empty.
	 */
	private void setDefaultStrategy() {
		synchronized (requestMatchingStrategies) {
			if (requestMatchingStrategies.isEmpty()) {
				LOG.info("Request matching strategies empty, setting default strategy to " + RequestUriMatchingStrategy.class.getName());
				add(new RequestUriMatchingStrategy());
			}
		}
	}
}
