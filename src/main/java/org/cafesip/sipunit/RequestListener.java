/*
 * Created on Jan 28, 2006
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

import java.util.EventObject;

/**
 * This class is only used internally by SipUnit. It is used for asynchronous reception of SIP
 * requests.
 * 
 * @author Becky McElroy
 * 
 */
public interface RequestListener {

  /**
   * For internal SipUnit use only.
   * 
   * @param event Event received.
   */
  public void processEvent(EventObject event);
}
