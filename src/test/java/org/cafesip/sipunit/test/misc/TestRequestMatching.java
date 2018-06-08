package org.cafesip.sipunit.test.misc;

import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipSession;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.processing.RequestProcessingResult;
import org.cafesip.sipunit.processing.RequestProcessingStrategy;
import org.cafesip.sipunit.processing.RequestProcessor;
import org.cafesip.sipunit.processing.matching.RequestUriMatchingStrategy;
import org.cafesip.sipunit.processing.matching.ToMatchingStrategy;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.SipListener;
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
 * Test behavior of matching depending on the configuration of {@link RequestProcessingStrategy} for request matching
 * in {@link SipSession}.
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
	private final int uaPort = 5081;
	private final String uaProtocol = "UDP";
	private final String uaContact = "sip:clientA@127.0.0.1:" + uaPort;
	private final int ubPort = 5082;
	private final String ubProtocol = "UDP";
	private final String ubContact = "sip:clientB@127.0.0.1:" + ubPort;
	private final int proxyPort = 5080;
	private final String proxyProtocol = "UDP";
	private final String proxyContact = "sip:proxy@127.0.0.1:5080";
	private final Properties uaProperties = new Properties();
	private final Properties proxyProperties = new Properties();
	private final ExecutorService executorService = Executors.newCachedThreadPool();
	private SipStack sipStackA;
	private SipPhone ua;
	private SipStack sipStackB;
	private SipPhone ub;
	private SipStack sipStackProxy;
	private SipPhone proxy;
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
	 * If the strategy list has only {@link ToMatchingStrategy} and this strategy is removed through
	 * {@link SipSession#setLoopback(boolean)}, the strategy list should be reset with the default
	 * {@link RequestUriMatchingStrategy}.
	 */
	@Test
	public void testRemoveOnlyToStrategy() {
		final RequestProcessor requestMatcher = ua.getRequestMatcher();
		requestMatcher.add(new ToMatchingStrategy());
		assertTrue(ua.isLoopback());

		requestMatcher.remove(RequestUriMatchingStrategy.class);
		assertEquals(1, requestMatcher.getAvailableStrategies().size());
		assertEquals(ToMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(0).getClass());

		ua.setLoopback(false);
		assertEquals(1, requestMatcher.getAvailableStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(0).getClass());
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
		final RequestProcessor requestMatcher = ua.getRequestMatcher();

		// Default is false
		assertFalse(ua.isLoopback());
		assertEquals(1, requestMatcher.getAvailableStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(0).getClass());

		ua.setLoopback(true);
		assertTrue(ua.isLoopback());
		assertEquals(2, requestMatcher.getAvailableStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(0).getClass());
		assertEquals(ToMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(1).getClass());

		// Add a new strategy and set loopback to false to check that this strategy will not be deleted
		RequestProcessingStrategy additionalStrategy = new RequestProcessingStrategy() {
			@Override
			public RequestProcessingResult processRequestEvent(RequestEvent requestEvent, SipListener receiver) {
				return new RequestProcessingResult(false, false);
			}
		};
		requestMatcher.add(additionalStrategy);

		ua.setLoopback(false);
		assertEquals(2, requestMatcher.getAvailableStrategies().size());
		assertEquals(RequestUriMatchingStrategy.class, requestMatcher.getAvailableStrategies().get(0).getClass());
		assertEquals(additionalStrategy, requestMatcher.getAvailableStrategies().get(1));
	}

	@Test
	public void testToMatching() {
		testMatchingStrategy(new ToMatchingStrategy());
	}

	@Test
	public void testRequestUriMatching() {
		testMatchingStrategy(new RequestUriMatchingStrategy());
	}

	private void testMatchingStrategy(RequestProcessingStrategy<SipSession> requestMatchingStrategy) {
		final RequestProcessor requestMatcher = ub.getRequestMatcher();

		List<RequestProcessingStrategy<SipSession>> initialStrategies = requestMatcher.getAvailableStrategies();

		requestMatcher.add(requestMatchingStrategy);
		if (initialStrategies.size() > 1) {
			for (RequestProcessingStrategy initialStrategy : initialStrategies) {
				requestMatcher.remove(initialStrategy);
			}
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
