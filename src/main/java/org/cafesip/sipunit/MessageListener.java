/*
 * Created on Aug 23, 2005
 * 
 * Copyright 2005 CafeSip.org
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.cafesip.sipunit;

import java.util.ArrayList;

/**
 * MessageListener represents certain SipUnit objects (such as SipCall) that perform SIP messaging
 * on behalf of a user program. This interface provides a uniform way of retrieving messages
 * received by such SipUnit objects.
 * 
 * <p>
 * Internally, this interface is used for asynchronous reception of SIP requests, responses, and
 * response timeouts.
 * 
 * @author Becky McElroy
 * 
 */
public interface MessageListener extends RequestListener {

  /**
   * Gets all the responses received by this object and can be called directly by a
   * test program.
   * 
   * @return ArrayList of zero or more SipResponse objects.
   * 
   * @see #getLastReceivedResponse()
   */
  public ArrayList<SipResponse> getAllReceivedResponses();

  /**
   * Gets all the requests received by this object and can be called directly by a
   * test program.
   * 
   * @return ArrayList of zero or more SipRequest objects.
   * 
   * @see #getLastReceivedRequest()
   */
  public ArrayList<SipRequest> getAllReceivedRequests();

  /**
   * Gets the last request received by this object and can be called directly by a
   * test program.
   * 
   * @return A SipRequest object representing the last request message received, or null if none has
   *         been received.
   * 
   * @see #getAllReceivedRequests()
   */
  public SipRequest getLastReceivedRequest();

  /**
   * Gets the last response received by this object and can be called directly by a
   * test program.
   * 
   * @return A SipResponse object representing the last response message received, or null if none
   *         has been received.
   * 
   * @see #getAllReceivedResponses()
   */
  public SipResponse getLastReceivedResponse();
}
