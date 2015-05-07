package org.cafesip.sipunit.test.util;

import org.cafesip.sipunit.SipRequest;

import javax.sip.header.HeaderFactory;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;

public class AuthUtil {

  /**
   * Utility to create an auth header for sending a 401 or 407 message, for purposes of testing out
   * SipUnit handling of a received 401/407 message. Meant for miscellaneous tests - full auth
   * testing should be done with a real proxy and using the proxywithauth package test classes.
   * 
   * @param message received request message to be challenged
   * @param headerFactory header factory
   * @param realm authentication realm
   * @return the header to include when sending the 401 or 407 response
   * @throws Exception on error
   */
  public static WWWAuthenticateHeader getAuthenticationHeader(SipRequest message,
      HeaderFactory headerFactory, String realm) throws Exception {
    WWWAuthenticateHeader header = null;
    String htype =
        message.getRequestEvent().getRequest().getMethod().equals(Request.REGISTER) == true ? WWWAuthenticateHeader.NAME
            : ProxyAuthenticateHeader.NAME;

    if (htype.equals(WWWAuthenticateHeader.NAME) == true) {
      header = headerFactory.createWWWAuthenticateHeader("Digest");
    } else {
      header = headerFactory.createProxyAuthenticateHeader("Digest");
    }
    header.setNonce("someNonce");
    header.setAlgorithm("MD5");
    header.setRealm(realm);
    header.setDomain(realm);
    header.setOpaque("someOpaque");
    header.setStale(false);
    return header;
  }
}
