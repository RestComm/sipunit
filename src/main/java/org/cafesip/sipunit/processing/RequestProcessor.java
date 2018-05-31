package org.cafesip.sipunit.processing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.SipListener;
import javax.sip.message.Request;
import java.util.*;

/**
 * This class takes care that the inbound requests received by a governing {@link SipListener} subclass are tested
 * against a configurable list of available strategies. The {@link RequestProcessingStrategy} instances which this class
 * manages are in charge of testing an inbound SIP {@link Request} and providing feedback if the strategy was able to
 * process the request successfully. The semantics of the (result of) processing is at the discretion of the implementing
 * strategy.
 * <p>
 * The request processor additionally manages that the request processing strategies do not produce side effects when being
 * mutated, and provides concurrent access to the strategy handling mechanism. The default strategies which are provided by
 * this class on instantiation are specified with the dedicated constructor.
 * <p>
 * Created by TELES AG on 12/01/2018.
 *
 * @see RequestProcessingStrategy
 */
public class RequestProcessor<ReceiverType extends SipListener> {

	protected static final Logger LOG = LoggerFactory.getLogger(RequestProcessor.class);

	/**
	 * Request processing strategies process an incoming {@link Request} for the governing client after receiving
	 * the request through the stack. This class is initialized with a subset of initial strategies.
	 * The user of this library may add additional processing strategies in order to add additional processing options.
	 */
	private final List<RequestProcessingStrategy<ReceiverType>> availableStrategies =
			Collections.synchronizedList(new ArrayList<RequestProcessingStrategy<ReceiverType>>());

	/**
	 * Initialize this instance with the provided initial strategies
	 *
	 * @param initialStrategies Initial strategies which should be added to the instance
	 * @see RequestProcessor#add(RequestProcessingStrategy)
	 */
	public RequestProcessor(final RequestProcessingStrategy<ReceiverType>... initialStrategies) {
		this(Arrays.asList(initialStrategies));
	}

	/**
	 * Initialize this instance with the provided initial strategies
	 *
	 * @param initialStrategies Initial strategies which should be added to the instance
	 * @see RequestProcessor#add(RequestProcessingStrategy)
	 */
	public RequestProcessor(final List<RequestProcessingStrategy<ReceiverType>> initialStrategies) {
		synchronized (availableStrategies) {
			for (RequestProcessingStrategy<ReceiverType> strategy : initialStrategies) {
				add(strategy);
			}
		}
	}

	/**
	 * Run all configured strategies in this matcher and determine if the request matches any configured criterion. The
	 * processor will execute every strategy in the available strategy list until the first strategy reports that the
	 * processing was successful.
	 *
	 * @param requestEvent The request being tested for a processing success with the available strategies
	 * @param receiver     The governing object which received the request
	 * @return Result denoting if the request has been processed by any configured strategy, and if it was processed
	 * successfully
	 */
	public RequestProcessingResult processRequestEvent(final RequestEvent requestEvent, final ReceiverType receiver) {
		RequestProcessingResult requestProcessingResult = new RequestProcessingResult(false, false);

		synchronized (availableStrategies) {
			Iterator<RequestProcessingStrategy<ReceiverType>> iterator = availableStrategies.iterator();

			// If we find a match, then the other strategies will not execute
			while (!requestProcessingResult.isProcessed() && iterator.hasNext()) {
				RequestProcessingStrategy strategy = iterator.next();
				requestProcessingResult = strategy.processRequestEvent(requestEvent, receiver);

				if(requestProcessingResult == null){
					LOG.warn("Request processing strategy " + strategy.getClass().getName() + " returned null");

					requestProcessingResult = new RequestProcessingResult(false, false);
				}
			}
		}
		return requestProcessingResult;
	}

	/**
	 * Add the strategy to be used in request processing in this processor. If the strategy is not permitted to add multiple
	 * instances and an existing instance of the same strategy class is present in the processor, it will not be added.
	 * Otherwise, the strategy will be added per the collection add behavior contract.
	 *
	 * @param requestProcessingStrategy The added request processing strategy
	 * @return If the request processing strategy was successfully added to the list of strategies used by this class
	 * @see List#add(Object)
	 */
	public boolean add(RequestProcessingStrategy<ReceiverType> requestProcessingStrategy) {
		assertNotNull(requestProcessingStrategy);

		synchronized (availableStrategies) {
			boolean permittedToAddAdditionalInstances = requestProcessingStrategy.multipleInstanceAllowed() ||
					!contains(requestProcessingStrategy.getClass());
			return permittedToAddAdditionalInstances && availableStrategies.add(requestProcessingStrategy);
		}
	}

	/**
	 * Removes the instance of the specified request processing strategy
	 *
	 * @param requestProcessingStrategy The strategy that needs to be removed by its reference in the request processor
	 * @return True if the specified instance of the searched strategy has been removed, false otherwise
	 * @see List#remove(Object)
	 */
	public boolean remove(RequestProcessingStrategy<ReceiverType> requestProcessingStrategy) {
		assertNotNull(requestProcessingStrategy);

		synchronized (availableStrategies) {
			if (availableStrategies.contains(requestProcessingStrategy) && availableStrategies.size() == 1) {
				throw new IllegalArgumentException("Cannot remove only remaining strategy");
			}

			return availableStrategies.remove(requestProcessingStrategy);
		}
	}

	/**
	 * Removes any existing {@link RequestProcessingStrategy} defined by the searched class in the strategy list.
	 * If this is the only strategy in the strategy list before removal, the strategy list will be set to default,
	 * i.e. be reset with the default configured strategies.
	 *
	 * @param searchedClass The class that needs to be removed by its type in the request processor
	 * @return True if any instance of the searched strategy has been removed, false otherwise
	 */
	public boolean remove(Class<? extends RequestProcessingStrategy<ReceiverType>> searchedClass) {
		assertNotNull(searchedClass);
		boolean isRemoved = false;

		synchronized (availableStrategies) {
			Iterator<RequestProcessingStrategy<ReceiverType>> it = availableStrategies.iterator();

			while (it.hasNext()) {
				RequestProcessingStrategy current = it.next();

				if (current.getClass().equals(searchedClass)) {
					if (availableStrategies.size() == 1) {
						throw new IllegalArgumentException("Cannot remove only remaining strategy");
					}

					isRemoved = true;
					it.remove();
				}
			}
		}

		return isRemoved;
	}

	/**
	 * Check if any existing {@link RequestProcessingStrategy<ReceiverType>} defined by the searched class is present in the configured
	 * strategy list.
	 *
	 * @param searchedClass The class whose direct instances will be searched for in the request class
	 * @return True if any instance of the searched strategy has been found, false otherwise
	 */
	public boolean contains(Class<? extends RequestProcessingStrategy> searchedClass) {
		assertNotNull(searchedClass);

		synchronized (availableStrategies) {
			for (RequestProcessingStrategy requestProcessingStrategy : availableStrategies) {
				if (requestProcessingStrategy.getClass().equals(searchedClass)) {
					return true;
				}
			}
		}

		return false;
	}

	/**
	 * Check if the {@link RequestProcessingStrategy<ReceiverType>} is added to this instance (by reference).
	 *
	 * @return True if the specified instance of the searched strategy has been found, false otherwise
	 * @see List#contains(Object)
	 */
	public boolean contains(RequestProcessingStrategy<ReceiverType> requestProcessingStrategy) {
		assertNotNull(requestProcessingStrategy);

		return availableStrategies.contains(requestProcessingStrategy);
	}

	/**
	 * @return A list of available strategies for incoming requests which this instance uses to process the inbound
	 * request. This list is unmodifiable and synchronized and will be updated with each change.
	 * @see Collections#synchronizedCollection(Collection)
	 */
	public List<RequestProcessingStrategy<ReceiverType>> getAvailableStrategies() {
		return Collections.unmodifiableList(availableStrategies);
	}

	private void assertNotNull(RequestProcessingStrategy<ReceiverType> requestProcessingStrategy) {
		if(requestProcessingStrategy == null){
			throw new IllegalArgumentException("Request processing strategy may not be null");
		}
	}

	private void assertNotNull(Class searchedClass) {
		if(searchedClass == null){
			throw new IllegalArgumentException("Request processing strategy class may not be null");
		}
	}
}
