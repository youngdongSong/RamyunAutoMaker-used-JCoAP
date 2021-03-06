/* Copyright 2011 University of Rostock
 *
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

package org.ws4d.coap.connection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;

import org.ws4d.coap.interfaces.CoapChannel;
import org.ws4d.coap.interfaces.CoapMessage;
import org.ws4d.coap.interfaces.CoapRequest;
import org.ws4d.coap.interfaces.CoapResponse;
import org.ws4d.coap.interfaces.CoapServer;
import org.ws4d.coap.interfaces.CoapServerChannel;
import org.ws4d.coap.interfaces.CoapSocketHandler;
import org.ws4d.coap.messages.BasicCoapRequest;
import org.ws4d.coap.messages.BasicCoapResponse;
import org.ws4d.coap.messages.CoapBlockOption;
import org.ws4d.coap.messages.CoapEmptyMessage;
import org.ws4d.coap.messages.CoapMediaType;
import org.ws4d.coap.messages.CoapPacketType;
import org.ws4d.coap.messages.CoapRequestCode;
import org.ws4d.coap.messages.CoapResponseCode;
import org.ws4d.coap.messages.CoapBlockOption.CoapBlockSize;

/**
 * @author Bjoern Konieczek <bjoern.konieczek@uni-rostock.de>
 * @author Christian Lerche <christian.lerche@uni-rostock.de>
 */

public class BasicCoapServerChannel extends BasicCoapChannel implements CoapServerChannel{
	CoapServer server = null;
	CoapResponse lastResponse;
	CoapRequest lastRequest;
	ServerBlockContext blockContext = null;
	
	public BasicCoapServerChannel(CoapSocketHandler socketHandler,
			CoapServer server, InetAddress remoteAddress,
			int remotePort) {
		super(socketHandler, remoteAddress, remotePort);
		this.server = server;
	}
	
    @Override
    public void close() {
        socketHandler.removeServerChannel(this);
    }
	
	
	@Override
	public void handleMessage(CoapMessage message) 
	{
		/* message MUST be a request */
		if(message.getPacketType() == CoapPacketType.RST) 
		{
			//TODO Notify Server to handle reset messages (for example for observe cancellation)
			server.onReset( lastRequest );
		}
		if (message.isEmpty()){
			return; 
		}
		
		if (!message.isRequest()){
			return;
			//throw new IllegalStateException("Incomming server message is not a request");
		}
		
		BasicCoapRequest request = (BasicCoapRequest) message;
		lastRequest = request;
		CoapBlockOption block1 = request.getBlock1();
		
		if( blockContext == null && block1 != null ){
			blockContext = new ServerBlockContext(block1, this.maxReceiveBlocksize);
			blockContext.setFirstRequest(request);
		}
		
		if( blockContext != null ) 
		{
			if( !blockContext.isFinished() ) 
			{
				BasicCoapResponse response = null;
				if( ( blockContext.getFirstRequest().getRequestCode() == CoapRequestCode.PUT || blockContext.getFirstRequest().getRequestCode() == CoapRequestCode.POST ) && request.getRequestCode() != CoapRequestCode.GET ) {
					blockContext.addBlock( request, block1);
					if( !blockContext.isFinished() )  {
						response =  createResponse(request, CoapResponseCode.Continue_231);
						response.setBlock1(block1);
						sendMessage(response);
						return;
					}	
				} else if( blockContext.getFirstRequest().getRequestCode() == CoapRequestCode.GET  && request.getRequestCode() == CoapRequestCode.GET ) {
					CoapBlockOption newBlock = blockContext.getNextBlock();
					response = createResponse(request, CoapResponseCode.Content_205 );					
					response.setBlock2( newBlock );
					response.setPayload( blockContext.getNextPayload(newBlock) );
//					System.out.println("Sending Block Number: " + newBlock.getNumber()+"; Payload: " + new String(response.getPayload()) );
					sendMessage(response);
					if( blockContext.isFinished() ) {
						blockContext = null;
					}
					return;
				}				
			}			
		}
		
		if( blockContext == null || (blockContext.getFirstRequest().getRequestCode() != CoapRequestCode.GET && blockContext.isFinished() ) ) {
			CoapChannel channel = request.getChannel();
			if( blockContext != null ){
				request.setPayload( blockContext.getPayload() );
				blockContext = null;
			}
			/* TODO make this cast safe */
			server.onRequest((CoapServerChannel) channel, request);
		}
	}
	
	@Override
	public void handleMCResponse(CoapMessage message, InetAddress srcAddress, int srcPort) {
		System.err.println("ERROR: Received a response on a Server");
	};
	
    /*TODO: implement */
	public void lostConnection(boolean notReachable, boolean resetByServer){
		server.onSeparateResponseFailed(this);
	}
	
    @Override
    public BasicCoapResponse createResponse(CoapMessage request, CoapResponseCode responseCode) {
    	return createResponse(request, responseCode, null);
    }  
    
    @Override
    public BasicCoapResponse createResponse(CoapMessage request, CoapResponseCode responseCode, CoapMediaType contentType){
    	BasicCoapResponse response;
    	if (request.getPacketType() == CoapPacketType.CON) {
    		response = new BasicCoapResponse(CoapPacketType.ACK, responseCode, request.getMessageID(), request.getToken());
    	} else if (request.getPacketType() == CoapPacketType.NON) {
    		response = new BasicCoapResponse(CoapPacketType.NON, responseCode, request.getMessageID(), request.getToken());
    	} else {
    		throw new IllegalStateException("Create Response failed, Request is neither a CON nor a NON packet");
    	}
    	if (contentType != null && contentType != CoapMediaType.UNKNOWN){
    		response.setContentType(contentType);
    	}
    	
    	response.setChannel(this);
    	return response;
    }


	@Override
	public CoapResponse createSeparateResponse(CoapRequest request,	CoapResponseCode responseCode) {
		
		BasicCoapResponse response = null;
		if (request.getPacketType() == CoapPacketType.CON) {
			/* The separate Response is CON (normally a Response is ACK or NON) */
    		response = new BasicCoapResponse(CoapPacketType.CON, responseCode, channelManager.getNewMessageID(), request.getToken());
    		/*send ack immediately */
    		sendMessage(new CoapEmptyMessage(CoapPacketType.ACK, request.getMessageID()));
		} else if (request.getPacketType() == CoapPacketType.NON){
			/* Just a normal response*/
			response = new BasicCoapResponse(CoapPacketType.NON, responseCode, request.getMessageID(), request.getToken());
		} else {
    		throw new IllegalStateException("Create Response failed, Request is neither a CON nor a NON packet");
		}

		response.setChannel(this);
		return response;
	}


	@Override
	public void sendSeparateResponse(CoapResponse response) {
		this.sendMessage(response);
	}

	@Override
	public CoapResponse createNotification(CoapRequest request, CoapResponseCode responseCode, int sequenceNumber){
		/*use the packet type of the request: if con than con otherwise non*/
		if (request.getPacketType() == CoapPacketType.CON){
			return createNotification(request, responseCode, sequenceNumber, true);
		} else {
			return createNotification(request, responseCode, sequenceNumber, false);
		}
	}
	
	@Override
	public CoapResponse createNotification(CoapRequest request, CoapResponseCode responseCode, int sequenceNumber, boolean reliable){
		BasicCoapResponse response = null;
		CoapPacketType packetType;
		if (reliable){
			packetType = CoapPacketType.CON;
		} else {
			packetType = CoapPacketType.NON;
		}
		
		response = new BasicCoapResponse(packetType, responseCode, channelManager.getNewMessageID(), request.getToken());
		response.setChannel(this);
		response.setObserveOption(sequenceNumber);
		return response;
	}


	@Override
	public void sendNotification(CoapResponse response) {
		this.sendMessage(response);
	}
	
	@Override
	public void sendMessage(CoapMessage msg) 
	{
		super.sendMessage( msg );
		this.lastResponse = (CoapResponse) msg;
	}
	
	public CoapResponse addBlockContext( CoapRequest request, byte[] payload ){
		CoapBlockSize bSize = request.getBlock2().getBlockSize();
		BasicCoapResponse response = this.createResponse(request, CoapResponseCode.Content_205 );
		if( maxSendBlocksize != null && bSize.compareTo( maxSendBlocksize ) > 0 ) {
			bSize = maxSendBlocksize;
		}
		
		if( bSize.getSize() >= payload.length ) {
			response.setPayload(payload);
		} else {
			this.blockContext = new ServerBlockContext( bSize, payload);
			this.blockContext.setFirstRequest(request);
			CoapBlockOption block2 = new CoapBlockOption(0, true, bSize );
			response.copyHeaderOptions( (BasicCoapRequest)request );
			response.setBlock2( block2 );
			response.setBlock2( block2 );
			response.setPayload( blockContext.getNextPayload( block2 ));			
		}
		return response;
	}
	
	private class ServerBlockContext{

    	private ByteArrayOutputStream incomingStream;
    	private ByteArrayInputStream outgoingStream;
    	boolean finished = false;
    	boolean sending = false; //false=receiving; true=sending
    	CoapBlockSize blockSize; //null means no block option
    	int blockNumber;
    	int maxBlockNumber;
    	CoapRequest request;
    	CoapResponse response;
    	
    	/** Create BlockContext for GET requests. This is done automatically, if the sent GET request or the obtained response
    	 *  contain a Block2-Option.
    	 * 
    	 * @param blockOption	The CoapBlockOption object, that contains the block size indicated by the server
    	 * @param maxBlocksize	Indicates the maximum block size supported by the client
    	 */
    	public ServerBlockContext(CoapBlockOption blockOption, CoapBlockSize maxBlocksize) {
    		this.incomingStream = new ByteArrayOutputStream();
    		this.outgoingStream = null;
    		
    		/* determine the right blocksize (min of remote and max)*/
    		if (maxBlocksize == null){
    			blockSize = blockOption.getBlockSize();
    		} else {
    			int max = maxBlocksize.getSize();
    			int remote = blockOption.getBlockSize().getSize();
    			if (remote < max){
    				blockSize = blockOption.getBlockSize();
    			} else {
    				blockSize = maxBlocksize;
    			}
    		}
    		this.blockNumber = blockOption.getNumber();
    		this.sending=false;
    	}
    	
    	/** Create BlockContext for POST or PUT requests. Is only called by addBlockContext().
    	 * 
    	 * @param maxBlocksize	Indicates the block size for the transaction
    	 * @param payload		The whole payload, that should be transferred
    	 */
    	public ServerBlockContext(CoapBlockSize maxBlocksize, byte[] payload ){
    		this.outgoingStream = new ByteArrayInputStream( payload );
    		this.incomingStream = null;
    		this.blockSize = maxBlocksize;
    		
    		this.blockNumber = 0;
    		this.maxBlockNumber = payload.length / this.blockSize.getSize() - 1;
    		if( payload.length%this.blockSize.getSize() > 0 )
    			this.maxBlockNumber++;

    		this.sending = true;
    	}

		public byte[] getPayload() {
			if (!this.sending ) {
				try{
					this.incomingStream.close();
				} catch(IOException e){
					e.printStackTrace();
				}
				return this.incomingStream.toByteArray();
			} else if( this.outgoingStream != null ) {
				byte[] payload = new byte[ this.outgoingStream.available() ];
				outgoingStream.read(payload, 0, this.outgoingStream.available());
				return payload;
			} else
				return null;
		}

		/** Adds the new obtained data block to the complete payload, in the case of blockwise GET requests.
		 * 
		 * @param msg		The received CoAP message
		 * @param block		The block option of the CoAP message, indicating which block of data was received 
		 * 					and whether there are more to follow.
		 * @return			Indicates whether the operation was successful.
		 */
		public boolean addBlock(CoapMessage msg, CoapBlockOption block){
			int number = block.getNumber();
			if( number > this.blockNumber )
				return false;
			
			this.blockNumber++;
			try{
				this.incomingStream.write( msg.getPayload() );
			} catch( IOException e) {
				System.err.println("ERROR: Cannot write data block to input buffer!");
			}
			if( block.isLast() ) {
				finished = true;
			}

    		return true;
    	}
    	
		/** Retrieve the next block option, indicating the requested (GET) or send (POST or PUT) block
		 * 
		 * @return	BlockOption to indicate the next block, that should be send (POST or PUT) or received (GET)
		 */
		public CoapBlockOption getNextBlock() {
			if( !sending ) {
				blockNumber++; //ignore the rest (no rest should be there)
				return new CoapBlockOption( blockNumber, false, blockSize);
			}
			else { 
				blockNumber++;
				if( blockNumber == maxBlockNumber) 
					return new CoapBlockOption(blockNumber, false, blockSize);
				else
					return new CoapBlockOption(blockNumber, true, blockSize);
			}
		}
		
		/** Get the next block of payload, that should be send in a POST or PUT request
		 * 
		 * @param block		Indicates which block of data should be send next.
		 * @return			The next part of the payload
		 */
		public byte[] getNextPayload(CoapBlockOption block){
			
			int number = block.getNumber();
			byte[] payloadBlock;
					
			if( number == maxBlockNumber ) {
				payloadBlock = new byte[ block.getBlockSize().getSize() ];
				this.outgoingStream.read(payloadBlock, 0, this.outgoingStream.available() );
				finished = true;
			} else {
				payloadBlock = new byte[ block.getBlockSize().getSize() ];
				this.outgoingStream.read(payloadBlock, 0, block.getBlockSize().getSize());
			}		
			
			return payloadBlock;
		}
    	
		public boolean isFinished() {
			return finished;
		}

		public CoapRequest getFirstRequest() {
			return request;
		}

		public void setFirstRequest(CoapRequest request) {
			this.request = request;
		}
    }

}

