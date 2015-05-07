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

import java.util.Calendar;
import java.util.List;

/**
 * This class represents a single Tuple element received in a NOTIFY SIP message - it contains all
 * information about a single active device associated with the presentity (buddy, watchee). These
 * elements are optional in a NOTIFY message. A NOTIFY message will contain the complete information
 * known about the presence of the presentity and include all active devices for that presentity
 * (ie, partial information is not allowed in a NOTIFY message).
 * 
 * <p>
 * See RFC 3863 if you need more information.
 * 
 * @author Becky McElroy
 * 
 */
public class PresenceDeviceInfo {

  private List<Object> deviceExtensions;

  private double contactPriority = -1.0;

  private String contactValue;

  private String id;

  private List<PresenceNote> deviceNotes;

  private String basicStatus;

  private List<Object> statusExtensions;

  private Calendar timestamp;

  protected PresenceDeviceInfo() {}

  /**
   * Gets the basic status for this presence device (ie, "open" or "closed").
   * 
   * @return String indicating the basic status, or null if none received.
   */
  public String getBasicStatus() {
    return basicStatus;
  }

  protected void setBasicStatus(String basicStatus) {
    this.basicStatus = basicStatus;
  }

  /**
   * Gets the priority of this presence device, relative to others, with respect to contact
   * preference.
   * 
   * The value MUST be a decimal number between 0 and 1 inclusive with at most 3 digits after the
   * decimal point. Higher values indicate higher priority. Examples of priority values are 0,
   * 0.021, 0.5, 1.00. If the 'priority' attribute is omitted, applications MUST assign the contact
   * address the lowest priority. If the 'priority' value is out of the range, applications just
   * SHOULD ignore the value and process it as if the attribute was not present.
   * 
   * @return A decimal number between 0 and 1 inclusive with at most 3 digits after the decimal
   *         point, or -1.0 if this attribute was not received in the NOTIFY message.
   */
  public double getContactPriority() {
    return contactPriority;
  }

  protected void setContactPriority(double contactPriority) {
    this.contactPriority = contactPriority;
  }

  /**
   * Gets the contact URI for this device.
   * 
   * @return A string representing the contact URI of this presence device, or null if none
   *         received.
   */
  public String getContactURI() {
    return contactValue;
  }

  protected void setContactValue(String contactValue) {
    this.contactValue = contactValue;
  }

  /**
   * Gets the extensions received in the last NOTIFY message, if any, pertaining to this presence
   * device.
   * 
   * @return A list of zero or more Object.
   */
  public List<Object> getDeviceExtensions() {
    return deviceExtensions;
  }

  protected void setDeviceExtensions(List<Object> deviceExtensions) {
    this.deviceExtensions = deviceExtensions;
  }

  /**
   * Gets the notes received in the last NOTIFY message, if any, pertaining to this presence device.
   * 
   * @return A list of zero or more PresenceNote objects.
   */
  public List<PresenceNote> getDeviceNotes() {
    return deviceNotes;
  }

  protected void setDeviceNotes(List<PresenceNote> deviceNotes) {
    this.deviceNotes = deviceNotes;
  }

  /**
   * Gets the unique, arbitrary ID representing this particular presence device.
   * 
   * @return A String denoting the device ID.
   */
  public String getId() {
    return id;
  }

  protected void setId(String id) {
    this.id = id;
  }

  /**
   * Gets the extensions received in the last NOTIFY message, if any, pertaining to this device's
   * status.
   * 
   * @return A list of zero or more Object.
   */
  public List<Object> getStatusExtensions() {
    return statusExtensions;
  }

  protected void setStatusExtensions(List<Object> statusExtensions) {
    this.statusExtensions = statusExtensions;
  }

  /**
   * Gets the timestamp of when this device's information was set.
   * 
   * @return A Calendar object indicating the timestamp, or null if none received.
   */
  public Calendar getTimestamp() {
    return timestamp;
  }

  protected void setTimestamp(Calendar timestamp) {
    this.timestamp = timestamp;
  }
}
