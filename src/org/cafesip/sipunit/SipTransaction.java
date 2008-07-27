/*
 * Created on Feb 20, 2005
 * 
 * Copyright 2005 CafeSip.org 
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *	http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 *
 */
package org.cafesip.sipunit;

import java.util.EventObject;
import java.util.LinkedList;

import javax.sip.ClientTransaction;
import javax.sip.ServerTransaction;
import javax.sip.message.Request;

/**
 * SipTransaction is used internally by the SipUnit API to manage some SIP
 * operations. The user program doesn't need to do anything with a
 * SipTransaction if returned by the API other than pass it in to a related,
 * subsequent API call as instructed on a per-operation basis. The user program
 * MAY call getRequest() to get the javax.sip.message.Request object that
 * created this transaction. However, knowledge of JAIN SIP API is required to
 * use the Request object.
 * 
 * @author Amit Chatterjee
 * 
 */
public class SipTransaction
{
    private ClientTransaction clientTransaction;

    private BlockObject block;

    private MessageListener clientListener;

    private LinkedList<EventObject> events = new LinkedList<EventObject>();

    private ServerTransaction serverTransaction;

    /**
     * A constructor for this class.
     * 
     * 
     */
    protected SipTransaction()
    {
        super();
        // TODO Auto-generated constructor stub
    }

    /**
     * @return Returns the clientTransaction.
     */
    protected ClientTransaction getClientTransaction()
    {
        return clientTransaction;
    }

    /**
     * @param clientTransaction
     *            The clientTransaction to set.
     */
    protected void setClientTransaction(ClientTransaction clientTransaction)
    {
        this.clientTransaction = clientTransaction;
    }

    /**
     * @return Returns the block.
     */
    protected BlockObject getBlock()
    {
        return block;
    }

    /**
     * @param block
     *            The block to set.
     */
    protected void setBlock(BlockObject block)
    {
        this.block = block;
    }

    /**
     * @return Returns the events.
     */
    protected LinkedList<EventObject> getEvents()
    {
        return events;
    }

    /**
     * @param events
     *            The events to set.
     */
    protected void setEvents(LinkedList<EventObject> events)
    {
        this.events = events;
    }

    /**
     * @return Returns the serverTransaction.
     */
    protected ServerTransaction getServerTransaction()
    {
        return serverTransaction;
    }

    /**
     * @param serverTransaction
     *            The serverTransaction to set.
     */
    protected void setServerTransaction(ServerTransaction serverTransaction)
    {
        this.serverTransaction = serverTransaction;
    }

    /**
     * The user test program MAY call this method to view the
     * javax.sip.message.Request object that created this transaction. However,
     * knowledge of JAIN SIP API is required to interpret the Request object.
     * 
     * @return Returns the javax.sip.message.Request object that created this
     *         transaction.
     */
    public Request getRequest()
    {
        if (clientTransaction != null)
        {
            return clientTransaction.getRequest();
        }

        if (serverTransaction != null)
        {
            return serverTransaction.getRequest();
        }

        return null;
    }

    protected MessageListener getClientListener()
    {
        return clientListener;
    }

    protected void setClientListener(MessageListener clientListener)
    {
        this.clientListener = clientListener;
    }
}