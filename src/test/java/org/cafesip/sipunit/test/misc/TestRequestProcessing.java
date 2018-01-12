package org.cafesip.sipunit.test.misc;

import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;
import org.cafesip.sipunit.processing.RequestProcessor;
import org.junit.Before;
import org.junit.Test;

import javax.sip.RequestEvent;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test behavior of processing processing on the configuration of {@link org.cafesip.sipunit.processing.RequestProcessingStrategy}.
 * <p>
 * Created by TELES AG on 12/01/2018.
 */
public class TestRequestProcessing {

	private RequestProcessingStrategy defaultRequestProcessingStrategy = new RequestProcessingStrategy<SipSession>(false) {
		@Override
		public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipSession receiver) {
			return new RequestProcessingResult(false, false);
		}
	};

	private RequestProcessor requestProcessor;

	@Before
	public void setUp() {
		requestProcessor = new RequestProcessor(defaultRequestProcessingStrategy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotInitializeWithNull() {
		new RequestProcessor<>((RequestProcessingStrategy<SipSession>) null);
	}

	/**
	 * The request processor should not allow any mutation because of the immutable list
	 */
	@Test(expected = UnsupportedOperationException.class)
	public void testGetStrategiesMutation() {
		requestProcessor.getAvailableStrategies().clear();
	}

	/**
	 * If the strategy list has only the default strategy and this strategy is attempted to be removed, the processor
	 * should throw an exception to prevent side effects
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testRemoveLastStrategyClass() {
		requestProcessor.remove(defaultRequestProcessingStrategy.getClass());
	}

	/**
	 * If the strategy list has only the default strategy and this strategy is attempted to be removed, the processor
	 * should throw an exception to prevent side effects
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testRemoveLastStrategyInstance() {
		requestProcessor.remove(defaultRequestProcessingStrategy);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotAddNull() {
		requestProcessor.add(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotRemoveNullInstance() {
		requestProcessor.remove((RequestProcessingStrategy) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotRemoveNullClass() {
		requestProcessor.remove((Class) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotSearchForNullInstance() {
		requestProcessor.contains((RequestProcessingStrategy) null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testShouldNotSearchForNullClass() {
		requestProcessor.contains((Class) null);
	}

	@Test
	public void testStrategiesMutation() {
		// Attempt mutating the list obtained through the getter
		// Should have 1 default processing strategies
		List<RequestProcessingStrategy> requestProcessingStrategies = requestProcessor.getAvailableStrategies();
		assertEquals(1, requestProcessingStrategies.size());

		// Create a dummy strategy
		RequestProcessingStrategy newStrategy = createMockStrategy();

		// Mutate strategies through the accessor
		requestProcessor.add(newStrategy);
		// Returned list should be updated
		assertEquals(2, requestProcessingStrategies.size());

		List<RequestProcessingStrategy> newMatchingStrategies = requestProcessor.getAvailableStrategies();
		assertEquals(2, newMatchingStrategies.size());
		assertEquals(newStrategy, newMatchingStrategies.get(1));

		assertTrue(requestProcessor.contains(newStrategy));
		assertTrue(requestProcessor.contains(newStrategy.getClass()));

		// Default strategy
		assertTrue(requestProcessor.contains(defaultRequestProcessingStrategy.getClass()));
	}

	@Test
	public void testMultipleInstancesAllowed() {
		RequestProcessingStrategy multipleInstancesStrategy = createMockStrategy();

		assertFalse(defaultRequestProcessingStrategy.multipleInstanceAllowed());
		assertTrue(multipleInstancesStrategy.multipleInstanceAllowed());

		assertEquals(1, requestProcessor.getAvailableStrategies().size());
		assertTrue(requestProcessor.contains(defaultRequestProcessingStrategy.getClass()));

		assertTrue(requestProcessor.add(multipleInstancesStrategy));
		assertEquals(2, requestProcessor.getAvailableStrategies().size());

		// Already has this strategy by now
		assertFalse(requestProcessor.add(defaultRequestProcessingStrategy));
		assertEquals(2, requestProcessor.getAvailableStrategies().size());

		// Add a multiple instance strategy
		assertTrue(requestProcessor.add(multipleInstancesStrategy));
		assertEquals(3, requestProcessor.getAvailableStrategies().size());

		assertTrue(requestProcessor.add(multipleInstancesStrategy));
		assertEquals(4, requestProcessor.getAvailableStrategies().size());
	}

	@Test
	public void testShouldConvertNullResultToFailed() {
		RequestProcessingStrategy nullStrategy = new RequestProcessingStrategy<SipSession>() {
			@Override
			public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipSession receiver) {
				return null;
			}
		};
		requestProcessor.add(nullStrategy);

		RequestProcessingResult result = requestProcessor.processRequestEvent(new RequestEvent(this, null, null, null), null);
		assertNotNull(result);
		assertFalse(result.isProcessed());
		assertFalse(result.isSuccessful());
	}

	private RequestProcessingStrategy createMockStrategy() {
		return createMockStrategy(true);
	}

	private RequestProcessingStrategy createMockStrategy(boolean multipleInstancesAllowed) {
		return new RequestProcessingStrategy<SipSession>(multipleInstancesAllowed) {
			@Override
			public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipSession receiver) {
				return new RequestProcessingResult(false, false);
			}
		};
	}
}
