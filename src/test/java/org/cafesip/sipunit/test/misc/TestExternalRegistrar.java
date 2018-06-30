package org.cafesip.sipunit.test.misc;

import org.cafesip.sipunit.HeaderConfiguration;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sip.RequestEvent;
import javax.sip.address.Address;
import javax.sip.header.*;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.cafesip.sipunit.SipAssert.awaitStackDispose;
import static org.junit.Assert.*;

/**
 * Tests the integration of SipUnit clients in a case where the registrar is separate from the proxy. Also tests out
 * header override methods which are used in validation of registrar error-handling capabilities.
 * <p>
 * Created by TELES AG on 08/01/2018.
 */
public class TestExternalRegistrar {
	private static final Logger LOG = LoggerFactory.getLogger(TestExternalRegistrar.class);

	private static final String LOCALHOST = "127.0.0.1";

	private SipStack sipStack;
	private SipPhone ua;
	private final int uaPort = 5081;
	private final String uaProtocol = "UDP";
	private final String uaContact = "sip:client@127.0.0.1:5081";


	private SipStack sipStackRegistrar;
	private SipPhone registrar;
	private final int registrarPort = 5080;
	private final String registrarProtocol = "UDP";
	private final String registrarContact = "sip:registrar@127.0.0.1:5080";

	private final int proxyPort = 5090;
	private final String proxyProtocol = "UDP";

	private Properties uaProperties = new Properties();
	private final Properties registrarProperties = new Properties();

	private ExecutorService executorService = Executors.newCachedThreadPool();

	@Before
	public void setup() throws Exception {
		uaProperties.setProperty("javax.sip.IP_ADDRESS", LOCALHOST);
		uaProperties.setProperty("javax.sip.STACK_NAME", "testAgent");
		uaProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		uaProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testAgent_debug.txt");
		uaProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testAgent_log.txt");
		uaProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
		uaProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
		uaProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");

		sipStack = new SipStack(uaProtocol, uaPort, uaProperties);
		ua = sipStack.createSipPhone(LOCALHOST, registrarPort, LOCALHOST, proxyProtocol, proxyPort, uaContact);

		registrarProperties.setProperty("javax.sip.STACK_NAME", "testRegistrar");
		registrarProperties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "16");
		registrarProperties.setProperty("gov.nist.javax.sip.DEBUG_LOG", "testRegistrar_debug.txt");
		registrarProperties.setProperty("gov.nist.javax.sip.SERVER_LOG", "testRegistrar_log.txt");
		registrarProperties.setProperty("gov.nist.javax.sip.READ_TIMEOUT", "1000");
		registrarProperties.setProperty("gov.nist.javax.sip.CACHE_SERVER_CONNECTIONS", "false");
		registrarProperties.setProperty("gov.nist.javax.sip.PASS_INVITE_NON_2XX_ACK_TO_LISTENER", "true");
		registrarProperties.setProperty("javax.sip.IP_ADDRESS", LOCALHOST);

		sipStackRegistrar = new SipStack(registrarProtocol, registrarPort, registrarProperties);
		registrar = sipStackRegistrar.createSipPhone(registrarContact);
	}


	/**
	 * Release the sipStack and a user agent for the test.
	 */
	@After
	public void tearDown() {
		ua.unregister(uaContact, 1000);
		ua.dispose();
		awaitStackDispose(sipStack);

		registrar.dispose();
		awaitStackDispose(sipStackRegistrar);
	}

	@Test
	public void testRegisterWithRegistrar() throws Exception {
		registrar.setSupportRegisterRequests(true);
		assertTrue(registrar.listenRequestMessage());

		Future registrarResult = executorService.submit(new Runnable() {
			@Override
			public void run() {
				RequestEvent requestEvent = registrar.waitRequest(10000);
				assertNotNull(requestEvent);
				Response response = null;
				try {
					response = registrar.getParent().getMessageFactory().createResponse(200, requestEvent.getRequest());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				registrar.sendReply(requestEvent, response);
			}
		});

		assertTrue(ua.register("amit", "a1b2c3d4", uaContact, 4890, 1000000));
		assertLastOperationSuccess("user a registration - " + ua.format(), ua);

		registrarResult.get();

		assertNotNull(ua.getLastRegistrationRequest());
		assertNotNull(ua.getLastRegistrationResponse());
	}

	@Test
	public void testRegisterWithCallIdOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final CallIdHeader overrideHeader = ua.getParent().getHeaderFactory().createCallIdHeader("test-call-id");
		headerConfiguration.setCallIdHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithToOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final Address expectedAddress = ua.getParent().getAddressFactory().createAddress("sip:testaddr@test.com");
		final ToHeader overrideHeader = ua.getParent().getHeaderFactory().createToHeader(expectedAddress, "test-tag");
		headerConfiguration.setToHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithFromOverride() throws Exception {
		final HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final Address expectedAddress = ua.getParent().getAddressFactory().createAddress("sip:testaddr@test.com");
		final FromHeader overrideHeader = ua.getParent().getHeaderFactory().createFromHeader(expectedAddress, "test-tag");
		headerConfiguration.setFromHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithCSeqOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final CSeqHeader overrideHeader = ua.getParent().getHeaderFactory().createCSeqHeader(555l, Request.REGISTER);
		headerConfiguration.setCSeqHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithContactOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final Address expectedAddress = ua.getParent().getAddressFactory().createAddress("sip:testaddr@test.com");
		final ContactHeader overrideHeader = ua.getParent().getHeaderFactory().createContactHeader(expectedAddress);
		headerConfiguration.setContactHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithExpiresOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final ExpiresHeader overrideHeader = ua.getParent().getHeaderFactory().createExpiresHeader(3600);
		headerConfiguration.setExpiresHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterUserAgentOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();

		final UserAgentHeader overrideHeader = ua.getParent().getHeaderFactory()
				.createUserAgentHeader(Arrays.asList("test-agent"));
		headerConfiguration.setUserAgentHeader(overrideHeader);

		testHeaderOverride(headerConfiguration, overrideHeader);
	}

	@Test
	public void testRegisterWithOmittedContactOverride() throws Exception {
		HeaderConfiguration headerConfiguration = new HeaderConfiguration();
		headerConfiguration.setIgnoreContact(true);

		testHeaderOverride(headerConfiguration, ContactHeader.NAME, null);
	}

	private void testHeaderOverride(final HeaderConfiguration headerConfiguration, final Header overrideHeader) throws InterruptedException, java.util.concurrent.ExecutionException {
		testHeaderOverride(headerConfiguration, overrideHeader.getName(), overrideHeader);
	}

	private void testHeaderOverride(final HeaderConfiguration headerConfiguration, final String headerName, final Header expectedHeaderValue) throws InterruptedException, java.util.concurrent.ExecutionException {
		registrar.setSupportRegisterRequests(true);
		assertTrue(registrar.listenRequestMessage());

		Future registrarResult = executorService.submit(new Runnable() {
			@Override
			public void run() {
				RequestEvent requestEvent = registrar.waitRequest(10000);
				assertNotNull(requestEvent);

				Header receivedHeader = requestEvent.getRequest().getHeader(headerName);
				assertEquals(expectedHeaderValue, receivedHeader);

				Response response = null;
				try {
					response = registrar.getParent().getMessageFactory().createResponse(200, requestEvent.getRequest());
				} catch (ParseException e) {
					e.printStackTrace();
				}
				registrar.sendReply(requestEvent, response);
			}
		});

		assertTrue(ua.register("amit", "a1b2c3d4", uaContact, 4890, 1000000, headerConfiguration));
		assertLastOperationSuccess("user a registration - " + ua.format(), ua);

		registrarResult.get();
	}
}
