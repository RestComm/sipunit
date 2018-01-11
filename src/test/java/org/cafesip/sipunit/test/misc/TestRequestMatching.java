package org.cafesip.sipunit.test.misc;

import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
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
import java.util.Arrays;
import java.util.Collections;
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
 *
 * Provides backwards compatibility testing with the removed loopback attribute which has been replaced by the tested
 * strategy approach.
 *
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

	@Test
	public void testStrategiesMutation() {
		// Attempt mutating the list obtained through the getter
		// Should have 2 default matching strategies
		List<RequestMatchingStrategy> requestMatchingStrategies = ua.getRequestMatchingStrategies();
		assertEquals(1, requestMatchingStrategies.size());

		requestMatchingStrategies.clear();
		assertEquals(1, ua.getRequestMatchingStrategies().size());

		// Create a dummy strategy
		RequestMatchingStrategy newStrategy = new RequestMatchingStrategy(ua) {
			@Override
			public boolean isRequestMatching(Request request) {
				return true;
			}
		};

		// Mutate strategies through the setter
		ua.setRequestMatchingStrategies(Arrays.asList(newStrategy));
		List<RequestMatchingStrategy> newMatchingStrategies = ua.getRequestMatchingStrategies();

		assertEquals(1, newMatchingStrategies.size());
		assertEquals(newStrategy, newMatchingStrategies.get(0));
	}

	/**
	 * If the strategy list has only {@link ToMatchingStrategy} and this strategy is removed through
	 * {@link SipSession#setLoopback(boolean)}, the strategy list should be reset with the default
	 * {@link RequestUriMatchingStrategy}.
	 */
	@Test
	public void testRemoveOnlyToStrategy() {
		ua.setRequestMatchingStrategies(Arrays.asList(new ToMatchingStrategy(ua)));
		assertTrue(ua.isLoopback());

		ua.setLoopback(false);
		assertEquals(1, ua.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, ua.getRequestMatchingStrategies().get(0).getClass());
	}

	/**
	 * The session object should not allow for no request strategies to be present for request processing
	 */
	@Test(expected = IllegalArgumentException.class)
	public void testStrategiesMutationEmpty() {
		ua.setRequestMatchingStrategies(Collections.<RequestMatchingStrategy>emptyList());
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
		// Default is false
		assertFalse(ua.isLoopback());
		assertEquals(1, ua.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, ua.getRequestMatchingStrategies().get(0).getClass());

		ua.setLoopback(true);
		assertTrue(ua.isLoopback());
		assertEquals(2, ua.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, ua.getRequestMatchingStrategies().get(0).getClass());
		assertEquals(ToMatchingStrategy.class, ua.getRequestMatchingStrategies().get(1).getClass());

		// Add a new strategy and set loopback to false to check that this strategy will not be deleted
		List<RequestMatchingStrategy> strategies = ua.getRequestMatchingStrategies();
		RequestMatchingStrategy additionalStrategy = new RequestMatchingStrategy(ua) {
			@Override
			public boolean isRequestMatching(Request request) {
				return false;
			}
		};
		strategies.add(additionalStrategy);

		ua.setRequestMatchingStrategies(strategies);

		ua.setLoopback(false);
		assertEquals(2, ua.getRequestMatchingStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, ua.getRequestMatchingStrategies().get(0).getClass());
		assertEquals(additionalStrategy, ua.getRequestMatchingStrategies().get(1));
	}

	@Test
	public void testToMatching() {
		testMatchingStrategy(new ToMatchingStrategy(ub));
	}

	@Test
	public void testRequestUriMatching() {
		testMatchingStrategy(new RequestUriMatchingStrategy(ub));
	}

	private void testMatchingStrategy(RequestMatchingStrategy requestMatchingStrategy) {
		ub.setRequestMatchingStrategies(Arrays.asList(requestMatchingStrategy));

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
