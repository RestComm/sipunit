/*
 * Created on Jun 24, 2005
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
 * This class holds authentication information required for accessing a realm. This information is
 * used to form an authorization header when a sent request is challenged by the network element
 * owning the realm.
 * 
 * @author Becky McElroy
 * 
 */
public class Credential {

  private String realm = "";

  private String user = "";

  private String password = "";

  /**
   * A constructor for this class.
   * 
   * @param realm the realm to which the user, password apply
   * @param user the user name to use when authenticating
   * @param password the password to use when authenticating
   */
  public Credential(String realm, String user, String password) {
    this.realm = realm;
    this.user = user;
    this.password = password;
  }

  /**
   * A no-arg constructor for this class.
   */
  public Credential() {}

  /**
   * This method returns the password.
   * 
   * @return the password string.
   */
  public String getPassword() {
    return password;
  }

  /**
   * This method sets the password to use when authentication is required.
   * 
   * @param password The password value to set.
   */
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * This method returns the realm.
   * 
   * @return the realm string.
   */
  public String getRealm() {
    return realm;
  }

  /**
   * This method sets the realm associated with the user name and password.
   * 
   * @param realm The realm value to set.
   */
  public void setRealm(String realm) {
    this.realm = realm;
  }

  /**
   * This method returns the user.
   * 
   * @return the user string.
   */
  public String getUser() {
    return user;
  }

  /**
   * This method sets the user name to use when authentication is required.
   * 
   * @param user The user value to set.
   */
  public void setUser(String user) {
    this.user = user;
  }
}
