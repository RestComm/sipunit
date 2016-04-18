SipUnit
-------

Overview
-------
SipUnit provides a test environment geared toward unit testing SIP applications. It extends the JUnit test framework to incorporate SIP-specific assertions, and it provides a high-level API for performing the SIP operations needed to interact with or invoke a test target. A test program using the SipUnit API is written in Java and acts as a network element that sends/receives SIP requests and responses. The SipUnit API includes SIP User Agent Client (UAC), User Agent Server (UAS), and basic UAC/UAS Core functionality – the set of processing functions that resides above the SIP transaction and transport layers – for the purpose of interacting with the test target. SipUnit uses the JAIN-SIP reference implementation as its underlying SIP stack/engine. The primary goal of SipUnit is to abstract the details of SIP messaging/call handling and facilitate free-flowing, sequential test code so that a test target can be exercised quickly and painlessly.

Latest News
-------

* SipUnit 2.0.0 is out !

The following are the highlights of the release. Thanks to George Vagenas for his recent major contributions to SipUnit and also to S. Pitucha for contributing patches.

  * MESSAGE handling has been added, including support for authentication and MESSAGE with or without an existing dialog.
  * JUnit 4 support was added with static assertions in new SipAssert class.
  * SipUnit is now mavenized!
  * Convenience methods were added to get the contact URI as javax.sip.address.URI from SipContact, the call ID from SipCall, and retransmission count from SipStack. Also, support was added for deregistration using wildcard Contact Header and a new waitForAuthorisation() method for when the far end should send 401 or 407.
  * TLS support has been verified.
  * JAIN SIP stack was updated.

Releases
-------
 
Get the latest release either as a [binary](http://sourceforge.net/projects/mobicents/files/Mobicents%20SipUnit/) or [Maven dependency](https://oss.sonatype.org/content/groups/public/org/cafesip/sipunit/sipunit/2.0.0/).

For SipUnit history/archive information please visit http://cafesip.sourceforge.net/site/projects/sipunit/
