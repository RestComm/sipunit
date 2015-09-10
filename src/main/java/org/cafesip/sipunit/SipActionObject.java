/*
 * Created on Mar 30, 2005
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

/**
 * A SipActionObject represents any SipUnit object that performs SIP operations on behalf of a user
 * program. This interface provides a uniform way of determining the result of an operation -
 * success or failure, and in the case of failure, the reason for failure. This interface is
 * primarily used by SipTestCase/SipAssert.
 * 
 * @author Becky McElroy
 * 
 */
public interface SipActionObject {

  /**
   * Gets the status code of the current or last operation performed. It returns
   * either the SIP response code received from the network (defined in SipResponse, along with the
   * corresponding textual equivalent) or a SipUnit internal status/return code (defined in
   * SipSession, along with the corresponding textual equivalent). SipUnit internal codes are in a
   * specially designated range (SipSession.SIPUNIT_INTERNAL_RETURNCODE_MIN and upward).
   * 
   * @return The status code of the last operation performed, or the status code so far of the
   *         current ongoing operation.
   */
  public int getReturnCode();

  /**
   * The getErrorMessage() method returns a descriptive, human-readable string indicating the cause
   * of the problem encountered during the last operation performed. If an exception was involved,
   * this string will contain the name of the Exception class and the exception message.
   * 
   * @return A descriptive string describing the cause of the problem encountered during the last
   *         operation performed, or an empty string if no problem was encountered.
   */
  public String getErrorMessage();

  /**
   * This method is used to get the Exception object generated during the last operation performed.
   * It applies whenever the getReturnCode() method returns internal SipUnit return code
   * EXCEPTION_ENCOUNTERED.
   * 
   * @return The Throwable object generated during the last operation performed, or null if an
   *         Exception didn't occur.
   */
  public Throwable getException();

  /**
   * The format() method can be used to obtain a human-readable string containing the result of the
   * last operation - either a successful indication or all of the error information associated with
   * the last operation performed.
   * 
   * @return A string fully describing the error information associated with the last operation
   *         performed, or a successful indication if no error occurred.
   */
  public String format();
}
