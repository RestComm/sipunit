/*
 * Created on Feb 20, 2005
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
import java.util.LinkedList;
import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.message.Request;

/**
 * SipTransaction is primarily used by the SipUnit API classes to manage some SIP operations. The
 * user program doesn't need to do anything with a SipTransaction if returned by the API other than
 * pass it in to a related, subsequent API call as instructed on a per-operation basis.
 * 
 * <p>
 * The user program MAY call methods on this object to get related JAIN SIP API objects. One of the
 * methods is getRequest() to get the javax.sip.message.Request object that created this
 * transaction. The others include the getClientTransaction() or getServerTransaction() method -
 * only one of these should be called for a given SipTransaction depending on the context of the
 * transaction (request sending vs. receiving). Knowledge of JAIN SIP API is required to use the
 * returned objects.
 * 
 * @author Amit Chatterjee
 * 
 */
public class SipTransaction {

  private ClientTransaction clientTransaction;

  private BlockObject block;

  private MessageListener clientListener;

  private LinkedList<EventObject> events = new LinkedList<>();

  private ServerTransaction serverTransaction;

  /**
   * A constructor for this class.
   * 
   * 
   */
  protected SipTransaction() {
    super();
    // TODO Auto-generated constructor stub
  }

  /**
   * @return Returns the JAIN SIP ClientTransaction. Use this method only within the context of a
   *         sent request. Knowledge of JAIN SIP API is required to use the returned object.
   */
  public ClientTransaction getClientTransaction() {
    return clientTransaction;
  }

  /**
   * @param clientTransaction The clientTransaction to set.
   */
  protected void setClientTransaction(ClientTransaction clientTransaction) {
    this.clientTransaction = clientTransaction;
  }

  /**
   * @return Returns the block.
   */
  protected BlockObject getBlock() {
    return block;
  }

  /**
   * @param block The block to set.
   */
  protected void setBlock(BlockObject block) {
    this.block = block;
  }

  /**
   * @return Returns the events.
   */
  protected LinkedList<EventObject> getEvents() {
    return events;
  }

  /**
   * @param events The events to set.
   */
  protected void setEvents(LinkedList<EventObject> events) {
    this.events = events;
  }

  /**
   * @return Returns the JAIN SIP ServerTransaction. Use this method only within the context of a
   *         received request. Knowledge of JAIN SIP API is required to use the returned object.
   */
  public ServerTransaction getServerTransaction() {
    return serverTransaction;
  }

  /**
   * @param serverTransaction The serverTransaction to set.
   */
  protected void setServerTransaction(ServerTransaction serverTransaction) {
    this.serverTransaction = serverTransaction;
  }

  /**
   * The user test program MAY call this method to view the javax.sip.message.Request object that
   * created this transaction. However, knowledge of JAIN SIP API is required to interpret the
   * Request object.
   * 
   * @return Returns the javax.sip.message.Request object that created this transaction.
   */
  public Request getRequest() {
    if (clientTransaction != null) {
      return clientTransaction.getRequest();
    }

    if (serverTransaction != null) {
      return serverTransaction.getRequest();
    }

    return null;
  }

  protected MessageListener getClientListener() {
    return clientListener;
  }

  protected void setClientListener(MessageListener clientListener) {
    this.clientListener = clientListener;
  }
}
