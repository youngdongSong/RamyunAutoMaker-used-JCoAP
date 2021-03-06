/* Copyright 2015 University of Rostock
 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *****************************************************************************/

package org.ws4d.coap.interfaces;

import java.net.InetAddress;

/**
 * @author Christian Lerche <christian.lerche@uni-rostock.de>
 */
public interface CoapSocketHandler {
	// public void registerResponseListener(CoapResponseListener responseListener);
	// public void unregisterResponseListener(CoapResponseListener responseListener);
	// public int sendRequest(CoapMessage request);
	// public void sendResponse(CoapResponse response);
	// public void establish(DatagramSocket socket);
	// public void testConfirmation(int msgID);
	// public boolean isOpen();
	/* TODO */
	
	/**
	 * 
	 * @param client
	 * @param remoteAddress
	 * @param remotePort
	 * @return
	 */
	public CoapClientChannel connect(CoapClient client,
			InetAddress remoteAddress, int remotePort);

	/**
	 * 
	 */
	public void close();

	/**
	 * 
	 * @param msg
	 */
	public void sendMessage(CoapMessage msg);

	/**
	 * 
	 * @return
	 */
	public CoapChannelManager getChannelManager();

	/**
	 * 
	 * @return
	 */
	int getLocalPort();

	/**
	 * 
	 * @param channel
	 */
	void removeClientChannel(CoapClientChannel channel);

	/**
	 * 
	 * @param channel
	 */
	void removeServerChannel(CoapServerChannel channel);
}
