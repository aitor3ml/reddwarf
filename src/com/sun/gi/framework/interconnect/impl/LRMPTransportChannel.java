package com.sun.gi.framework.interconnect.impl;

import java.io.*;
import java.nio.*;
import java.util.*;

import com.sun.gi.framework.interconnect.*;
import com.sun.gi.utils.*;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

public class LRMPTransportChannel implements TransportChannel {
  private String name;
  SGSUUID uuID;
  private LRMPTransportManager transportManager;
  private List<TransportChannelListener> listeners = 
	  new ArrayList<TransportChannelListener>();

  LRMPTransportChannel(String channelName,SGSUUID id,
                              LRMPTransportManager mgr) {
    name = channelName;
    uuID = id;
    transportManager = mgr;
  }

  public void sendData(ByteBuffer data) throws IOException{
    transportManager.sendData(uuID,data);
  }
  
  
  public void addListener(TransportChannelListener l) {
	  synchronized(listeners) {
		  if (!listeners.contains(l)) {
			  listeners.add(l);
		  }
	  }
  }
  
  /**
   * Removes the given listener if it exists.  After the last listener
   * is removed, the channel will be closed at the TransportManager level.
   * 
   * @param l		the listener to remove
   */
  public void removeListener(TransportChannelListener l) {
	  synchronized(listeners) {
		  if (listeners.contains(l)) {
			  listeners.remove(l);
		  }
		  if (listeners.isEmpty()) {
			  transportManager.closeChannel(uuID);
		  }
	  }
  }
  
  public void closeChannel() {
    /**@todo Implement this com.sun.gi.framework.interconnect.TransportChannel method*/
    throw new java.lang.UnsupportedOperationException("Method closeChannel() not yet implemented.");
  }

  /**
   * doCloseChannel
   */
  public void doCloseChannel() {
    for(Iterator i=listeners.iterator();i.hasNext();){
      ((TransportChannelListener)i.next()).channelClosed();
    }
  }

  /**
   * doRecieveData
   *
   * @param data ByteBuffer
   */
  public void doRecieveData(ByteBuffer data) {
    for(Iterator i=listeners.iterator();i.hasNext();){
      ((TransportChannelListener)i.next()).dataArrived(data);
    }

  }

  /**
   * getName
   *
   * @return String
   */
  public String getName() {
    return name;
  }

  /**
   * sendData
   *
   * @param byteBuffers ByteBuffer[]
   */
  public void sendData(ByteBuffer[] byteBuffers) {
     transportManager.sendData(uuID,byteBuffers);
  }

public void close() {
	// TODO Auto-generated method stub
	
}

}
