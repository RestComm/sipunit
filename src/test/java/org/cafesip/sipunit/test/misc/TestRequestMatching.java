package org.cafesip.sipunit.test.misc;

import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.matching.RequestMatcher;
import org.cafesip.sipunit.matching.RequestMatchingStrategy;
import org.cafesip.sipunit.matching.RequestUriMatchingStrategy;
import org.cafesip.sipunit.matching.ToMatchingStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.*;

/**
 * Test behavior of matching depending on the configuration of {@link RequestMatchingStrategy} in {@link SipSession}.
 * <p>
 * Provides backwards compatibility testing with the removed loopback attribute which has been replaced by the tested
 * strategy approach.
 * <p>
 * Created by TELES AG on 10/01/2018.
 */
@SuppressWarnings("deprecation")
public class TestRequestMatching {

	private static final Logger LOG = LoggerFactory.getLogger(TestRequestMatching.class);

	private static final String LOCALHOST = "127.0.0.1";

	private SipStack sipStackA;
	private SipPhone ua;
	private final int uaPort = 5081;
	private final String uaProtocol = "UDP";
	private final String uaContact = "sip:clientA@127.0.0.1:" + uaPort;

	private SipStack sipStackB;
	private SipPhone ub;
	private final int ubPort = 5082;
	private final String ubProtocol = "UDP";
	private final String ubContact = "sip:clientB@127.0.0.1:" + ubPort;

	private SipStack sipStackProxy;
	private SipPhone proxy;
	private final int proxyPort = 5080;
	private final String proxyProtocol = "UDP";
	private final String proxyContact = "sip:proxy@127.0.0.1:5080";
	private final Properties uaProperties = new Properties();
	private final Properties proxyProperties = new Properties();
	private final ExecutorService executorService = Executors.newCachedThreadPool();

	private ProxyMock proxyMock;

	@Before
	public void setup() throws Exception {
		uaProperties.setProperty("javax.sip.IP_ADDRESS", LOCALHOST);
		uaProperties.setProperty("javax.sip.STACK_NAME", "testAgentA");
		uaProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		uaProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgentA_debug.txt");
		uaProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgentA_log.txt");
		uaProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
		uaProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
		uaProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

		sipStackA = new SipStack(uaProtocol, uaPort, uaProperties);
		ua = sipStackA.createSipPhone(LOCALHOST, proxyProtocol, proxyPort, uaContact);

		uaProperties.setProperty("javax.sip.IP_ADDRESS", LOCALHOST);
		uaProperties.setProperty("javax.sip.STACK_NAME", "testAgentB");
		uaProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		uaProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgentB_debug.txt");
		uaProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgentB_log.txt");
		uaProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
		uaProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
		uaProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

		sipStackB = new SipStack(ubProtocol, ubPort, uaProperties);
		ub = sipStackB.createSipPhone(LOCALHOST, proxyProtocol, proxyPort, ubContact);

		proxyProperties.setProperty("javax.sip.STACK_NAME", "testProxy");
		proxyProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		proxyProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testProxy_debug.txt");
		proxyProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testProxy_log.txt");
		proxyProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
		proxyProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
		proxyProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
		proxyProperties.setProperty("javax.sip.IP_ADDRESS", LOCALHOST);

		sipStackProxy = new SipStack(proxyProtocol, proxyPort, proxyProperties);
		proxy = sipStackProxy.createSipPhone(proxyContact);

		proxyMock = new ProxyMock();
		executorService.submit(proxyMock);
	}

	/**
	 * Release the sipStack and a user agent for the test.
	 */
	@After
	public void tearDown() {
		// Shutdown current mock
		proxyMock.setRunning(false);
		executorService.shutdown();

		ua.unregister(uaContact, 1000);
		ua.dispose();
		awaitStackDispose(sipStackA);

		ub.unregister(ubContact, 1000);
		ub.dispose();
		awaitStackDispose(sipStackB);

		proxy.dispose();
		awaitStackDispose(sipStackProxy);
	}

	/**
	 * The request matcher should not allow any mutation because of the immutable list
	 */
	@Test(expected = UnsupportedOperationException.class)
	public void testGetStrategiesMutation() {
		ua.getRequestMatcher().getRequestMatchingStrategies().clear();
	}

	@Test
	public void testStrategiesMutation() {
		final RequestMatcher requestMatcher = new RequestMatcher();

		// Attempt mutating the list obtained through the getter
		// Should have 2 default matching strategies
		List<RequestMatchingStrategy> requestMatchingStrategies = requestMatcher.getRequestMatchingStrategies();
		assertEquals(1, requestMatchingStrategies.size());

		// Create a dummy strategy
		RequestMatchingStrategy newStrategy = new RequestMatchingStrategy() {
			@Override
			public boolean isRequestMatching(Request request, SipSession sipSession) {
				return true;
			}
		};

		// Mutate strategies through the accessor
		requestMatcher.add(newStrategy);
		// Returned list should be updated
		assertEquals(2, requestMatchingStrategies.size());

		List<RequestMatchingStrategy> newMatchingStrategies = requestMatcher.getRequestMatchingStrategies();
		assertEquals(2, newMatchingStrategies.size());
		assertEquals(newStrategy, newMatchingStrategies.get(1));

		assertTrue(requestMatcher.contains(newStrategy));
		assertTrue(requestMatcher.contains(newStrategy.getClass()));

		// Default strategy
		assertTrue(requestMatcher.contains(RequestUriMatchingStrategy.class));
	}

	/**
	 * If the strategy list has only {@link ToMatchingStrategy} and this strategy is removed through
	 * {@link SipSession#setLoopback(boolean)}, the strategy list should be reset with the default
	 * {@link RequestUriMatchingStrategy}.
	 */
	@Test
	public void testRemoveOnlyToStrategy() {
		final RequestMatcher requestMatcher = ua.getRequestMatcher();
		requestMatcher.add(new ToMatchingStrategy());
		assertTrue(ua.isLoopback());

		requestMatcher.remove(RequestUriMatchingStrategy.class);
		assertEquals(1, requestMatcher.getRequestMatchingStrategies().size());
		assertEquals(ToMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(0).getClass());

		ua.setLoopback(false);
		assertEquals(1, requestMatcher.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(0).getClass());
	}

	@Test
	public void testMultipleInstancesAllowed() {
		RequestMatchingStrategy toStrategy = new ToMatchingStrategy();
		RequestMatchingStrategy requestUriStrategy = new RequestUriMatchingStrategy();

		assertFalse(toStrategy.multipleInstanceAllowed());
		assertFalse(requestUriStrategy.multipleInstanceAllowed());

		RequestMatchingStrategy multipleInstancesStrategy = new RequestMatchingStrategy() {
			@Override
			public boolean isRequestMatching(Request request, SipSession sipSession) {
				return false;
			}
		};

		final RequestMatcher requestMatcher = new RequestMatcher();
		assertEquals(1, requestMatcher.getRequestMatchingStrategies().size());
		assertTrue(requestMatcher.contains(requestUriStrategy.getClass()));

		assertTrue(requestMatcher.add(toStrategy));
		assertEquals(2, requestMatcher.getRequestMatchingStrategies().size());

		// Already has this strategies by now
		assertFalse(requestMatcher.add(requestUriStrategy));
		assertEquals(2, requestMatcher.getRequestMatchingStrategies().size());

		assertFalse(requestMatcher.add(toStrategy));
		assertEquals(2, requestMatcher.getRequestMatchingStrategies().size());

		// Add a multiple instance strategy
		assertTrue(requestMatcher.add(multipleInstancesStrategy));
		assertEquals(3, requestMatcher.getRequestMatchingStrategies().size());

		assertTrue(requestMatcher.add(multipleInstancesStrategy));
		assertEquals(4, requestMatcher.getRequestMatchingStrategies().size());
	}


	/**
	 * Backwards compatibility check for isLoopback after the addition of request matching strategies
	 * <p>
	 * Is loopback should be true when both the {@link RequestUriMatchingStrategy} and {@link ToMatchingStrategy}
	 * are set in the strategies of a {@link SipSession} object, and false if only the {@link RequestUriMatchingStrategy}
	 * is set in the strategies. (does not check existence of other strategies)
	 */
	@Test
	public void testIsLoopbackSetting() {
		final RequestMatcher requestMatcher = ua.getRequestMatcher();

		// Default is false
		assertFalse(ua.isLoopback());
		assertEquals(1, requestMatcher.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(0).getClass());

		ua.setLoopback(true);
		assertTrue(ua.isLoopback());
		assertEquals(2, requestMatcher.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(0).getClass());
		assertEquals(ToMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(1).getClass());

		// Add a new strategy and set loopback to false to check that this strategy will not be deleted
		RequestMatchingStrategy additionalStrategy = new RequestMatchingStrategy() {
			@Override
			public boolean isRequestMatching(Request request, SipSession sipSession) {
				return true;
			}
		};
		requestMatcher.add(additionalStrategy);

		ua.setLoopback(false);
		assertEquals(2, requestMatcher.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getRequestMatchingStrategies().get(0).getClass());
		assertEquals(additionalStrategy, requestMatcher.getRequestMatchingStrategies().get(1));
	}

	@Test
	public void testToMatching() {
		testMatchingStrategy(new ToMatchingStrategy());
	}

	@Test
	public void testRequestUriMatching() {
		testMatchingStrategy(new RequestUriMatchingStrategy());
	}

	private void testMatchingStrategy(RequestMatchingStrategy requestMatchingStrategy) {
		final RequestMatcher requestMatcher = ub.getRequestMatcher();

		List<RequestMatchingStrategy> initialStrategies = requestMatcher.getRequestMatchingStrategies();

		requestMatcher.add(requestMatchingStrategy);
		for (RequestMatchingStrategy initialStrategy : initialStrategies) {
			requestMatcher.remove(initialStrategy);
		}

		assertTrue(ua.register("userA", "test1", uaContact, 4890, 1000000));
		assertLastOperationSuccess("user a registration - " + ua.format(), ua);

		assertTrue(ub.register("userB", "test2", ubContact, 4890, 1000000));
		assertLastOperationSuccess("user b registration - " + ub.format(), ub);

		assertTrue(ub.listenRequestMessage());

		ua.makeCall(ubContact, ub.getPublicAddress() + "/" + ubProtocol);
		assertLastOperationSuccess("user a sendRequest - " + ua.format(), ua);

		ub.waitRequest(TimeUnit.SECONDS.toMillis(10));
		assertLastOperationSuccess("user b receive request - " + ub.format(), ub);
	}

	private class ProxyMock implements Runnable {

		private final AtomicBoolean isRunning = new AtomicBoolean();

		public ProxyMock() {
			proxy.setSupportRegisterRequests(true);
			assertTrue(proxy.listenRequestMessage());

			setRunning(false);
		}

		@Override
		public void run() {
			setRunning(true);

			while (isRunning.get()) {
				RequestEvent requestEvent = proxy.waitRequest(1000);
				if (requestEvent == null) {
					continue;
				}

				try {
					Response response = proxy.getParent().getMessageFactory().createResponse(200, requestEvent.getRequest());
					proxy.sendReply(requestEvent, response);
				} catch (ParseException e) {
					e.printStackTrace();
					fail("Parsing of input request failed");
				}
			}
		}

		public void setRunning(boolean runStatus) {
			isRunning.set(runStatus);
		}
	}
}
