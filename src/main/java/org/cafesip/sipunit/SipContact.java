/*
 * Created on Apr 24, 2005
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

import javax.sip.address.URI;
import javax.sip.header.ContactHeader;

/**
 * This class holds information pertaining to a contact URI and provides associated getter methods.
 * 
 * @author Becky McElroy
 * 
 */
public class SipContact {

  private ContactHeader contactHeader;

  protected SipContact() {}

  /**
   * @return Returns the javax.sip.header.ContactHeader.
   */
  public ContactHeader getContactHeader() {
    return contactHeader;
  }

  /**
   * @param contactHeader The contactHeader to set.
   */
  protected void setContactHeader(ContactHeader contactHeader) {
    this.contactHeader = contactHeader;
  }

  // // PUBLIC API methods

  /**
   * The method getExpiry() returns the expiry value for this contact URI.
   * 
   * @return Returns the expiry in seconds. If the returned value is 0, the contact doesn't expire.
   *         If the returned value is greater than 0, it is the original value of the expiry at the
   *         time the contact URI became known - elapsed time since then has NOT been accounted for.
   */
  public int getExpiry() {
    return contactHeader.getExpires();
  }

  /**
   * The method getDisplayName() returns the display name of this contact URI.
   * 
   * @return Returns the contact display name, or "" if none.
   */
  public String getDisplayName() {
    if (contactHeader.getAddress().getDisplayName() != null) {
      return contactHeader.getAddress().getDisplayName();
    }

    return "";
  }

  /**
   * The method getURI() returns the contact URI as a String. For example:
   * sips:bob@client.biloxi.example.com.
   * 
   * @return The contact URI as a String.
   */
  public String getURI() {
    return contactHeader.getAddress().getURI().toString();
  }

  /**
   * The method getURIasURI() returns the contact URI as a javax.sip.address URI.
   * 
   * @return The contact URI as a URI object.
   */
  public URI getURIasURI() {
    return contactHeader.getAddress().getURI();
  }

  /**
   * The method getURIScheme() returns the scheme of this contact URI ("sip", "sips", etc.).
   * 
   * @return Returns the value of the "scheme" of the contact URI - "sip", "sips", or "tel".
   */
  public String getURIScheme() {
    return contactHeader.getAddress().getURI().getScheme();
  }

  /**
   * This method indicates if this contact URI has a scheme of "sip" or "sips", or not.
   * 
   * @return true if the scheme is "sip" or "sips", false otherwise.
   */
  public boolean isSipURI() {
    return contactHeader.getAddress().getURI().isSipURI();
  }
}
