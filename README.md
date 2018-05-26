


[Try Restcomm Cloud NOW for FREE!](https://www.restcomm.com/sign-up/) Zero download and install required.


All Restcomm [docs](https://www.restcomm.com/docs/) and [downloads](https://www.restcomm.com/downloads/) are now available at [Restcomm.com](https://www.restcomm.com).





[![FOSSA Status](https://app.fossa.io/api/projects/git%2Bhttps%3A%2F%2Fgithub.com%2FRestComm%2Fsipunit.svg?type=shield)](https://app.fossa.io/projects/git%2Bhttps%3A%2F%2Fgithub.com%2FRestComm%2Fsipunit?ref=badge_shield)

SipUnit
-------

Overview
-------
SipUnit provides a test environment geared toward unit testing SIP applications. It extends the JUnit test framework to incorporate SIP-specific assertions, and it provides a high-level API for performing the SIP operations needed to interact with or invoke a test target. A test program using the SipUnit API is written in Java and acts as a network element that sends/receives SIP requests and responses. The SipUnit API includes SIP User Agent Client (UAC), User Agent Server (UAS), and basic UAC/UAS Core functionality – the set of processing functions that resides above the SIP transaction and transport layers – for the purpose of interacting with the test target. SipUnit uses the JAIN-SIP reference implementation as its underlying SIP stack/engine. The primary goal of SipUnit is to abstract the details of SIP messaging/call handling and facilitate free-flowing, sequential test code so that a test target can be exercised quickly and painlessly.
