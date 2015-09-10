/*
 * Created on Nov 20, 2005
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
 * This class represents a single Note element received in a NOTIFY SIP message.
 * 
 * <p>
 * Notes are optional. Zero or more notes may be received at the top level of the NOTIFY message,
 * pertaining to the presentity (buddy, watchee). Independently, zero or more notes may be received
 * at the tuple (active device) level, pertaining to that particular device.
 * 
 * @author Becky McElroy
 * 
 */
public class PresenceNote {

  private String language;

  private String value;

  protected PresenceNote(String language, String value) {
    this.language = language;
    this.value = value;
  }

  /**
   * Gets the language property of this note.
   * 
   * @return String that is the language property.
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Gets the text value of this note.
   * 
   * @return String containing the text note content.
   */
  public String getValue() {
    return value;
  }
}
