/*
 * Created on Feb 19, 2005
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
 * FOR INTERNAL USE ONLY. A test class doesn't use this class.
 * 
 * @author Amit Chatterjee
 * 
 */
public class BlockObject {

  /**
   * FOR INTERNAL USE ONLY - A test class doesn't use this method.
   */
  public BlockObject() {
    super();
  }

  /**
   * FOR INTERNAL USE ONLY - A test class doesn't use this method.
   */
  public void waitForEvent(long timeout) throws Exception {
    synchronized (this) {
      this.wait(timeout);
    }
  }

  /**
   * FOR INTERNAL USE ONLY - A test class doesn't use this method.
   */
  public void notifyEvent() {
    synchronized (this) {
      this.notify();
    }
  }
}
