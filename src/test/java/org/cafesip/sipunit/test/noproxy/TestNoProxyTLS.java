/*
 * Created on April 21, 2005
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

package org.cafesip.sipunit.test.noproxy;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import java.util.LinkedHashMap;

/**
 * This class tests SipUnit API methods.
 * 
 * <p>
 * Tests in this class do not require a proxy/registrar server. Messaging between UACs is direct.
 * 
 * @author Becky McElroy
 * 
 */
public class TestNoProxyTLS extends TestNoProxy {

  private static LinkedHashMap<Object, Object> systemProperties;

  @BeforeClass
  public static void setupTestNoProxyTLS() {
    systemProperties = new LinkedHashMap<>(System.getProperties());

    System.setProperty("javax.net.ssl.keyStore",
        ClassLoader.getSystemClassLoader().getResource("testkeys").getPath());
    System.setProperty("javax.net.ssl.trustStore",
        ClassLoader.getSystemClassLoader().getResource("testkeys").getPath());
    System.setProperty("javax.net.ssl.keyStorePassword", "passphrase");
    System.setProperty("javax.net.ssl.keyStoreType", "jks");

    System.setProperty("sipunit.test.protocol", "tls");
  }

  @AfterClass
  public static void tearDownTestNoProxyTLS() {
    System.getProperties().clear();
    System.getProperties().putAll(systemProperties);
  }

  protected String getProtocol() {
    return "sips";
  }
}
