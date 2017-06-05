package client;


import app_kvClient.ClientSocketListener;
import app_kvClient.ClientSocketListener.SocketStatus;
import app_kvClient.TextMessage;
import common.messages.KVMessage;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.sql.Time;
import java.util.Date;
import java.util.Set;

public class KVStore implements KVCommInterface {

	
	/**
	 * Initialize KVStore with address and port of KVServer
	 * @param address the address of the KVServer
	 * @param port the port of the KVServer
	 */
	public KVStore(String address, int port) {
		this.address = address;
		this.port = port;

	}

	private Socket clientSocket;
	private OutputStream output;
	private InputStream input;
	private String address;
	private int port;
	private Logger logger = Logger.getRootLogger();
	private Set<ClientSocketListener> listeners;
	private boolean running;
	private static final int BUFFER_SIZE = 1024;
	private static final int DROP_SIZE = 1024 * BUFFER_SIZE;

	//@Override
	/*
	Connects to a server at address:port and sets up input/output
	 */
	public void connect() throws Exception {
		this.clientSocket = new Socket(address, port);

		try {
			logger.debug("getting output at"+new Date().toString());
			this.output = clientSocket.getOutputStream();
			logger.debug("getting input at"+new Date().toString());
			this.input = clientSocket.getInputStream();

		} catch(Exception ex){
			logger.error("connection error at"+ new Date().toString(), ex);//this.logger.trace(ex.getMessage());
		}

		if(clientSocket.isConnected()){
			String message = "Connection established successffully at " + new Date().toString();
			logger.info(message);//this.logger.trace(message);
		} else {
			String message = "Could not connect to host " + this.address + ":"
					+ this.port + "at " + new Date().toString();
			logger.error(message);
			throw new Exception(message);
		}
	}

	//@Override
	/*
	disconnects from current connection
	 */
	public void disconnect() {
		String message = "try to close connection with " + this.address +":" + this.port
	    		+ "at" + new Date().toString();
	    	logger.info(message);
	    	
	    	try{
	    		tearDownConnection();
	    		for (ClientSocketListener listener : listeners){
	    			listener.handleStatus(SocketStatus.DISCONNECTED);
	    		}
	    		logger.info("connection closed with " + this.address +":" + this.port
						+ "at" + new Date().toString());
	    	}catch(Exception ioe){
	    		logger.warn("Unable to close connection!",ioe);
	    	}
	}
	/*
	disconnects from current connection
	 */
	private void tearDownConnection() throws IOException{
    	setRunning(false);
    	String message = "tearing down connection "+this.address +":"+this.port+"at"+new Date().toString();
    	logger.info(message);
    	if (clientSocket != null){
    		logger.debug("closing input at"+new Date().toString());
    		input.close();
			logger.debug("closing output at"+new Date().toString());
    		output.close();
			logger.debug("closing socket at"+new Date().toString());
    		clientSocket.close();
    		clientSocket =null;
    		message = "connection:"+this.address +":"+this.port
    				+ "closed at" + new Date().toString();
    		logger.info(message);
    	}
    }

    /*
    returns running state of client
     */
    public boolean isRunning() {
		return running;
	}

	/*sets client to running state given by run*/
    public void setRunning(boolean run) {
		logger.info("run state now"+run+"at"+new Date().toString());
    	running = run;
	}
   /*adds ClientSocketListener listener to the list*/
    public void addListener(ClientSocketListener listener){
		listeners.add(listener);
	}

	/*sends len bytes over the socket based on M0*/
    public void sendMessage(byte[] msg, int len) throws IOException {
		String message = new String(msg,0,len);
    	logger.info("Send message:\t '" + message + "'");
    	output.write(msg, 0, len);
		output.flush();
		logger.info("sent message:\t '" + message + ";");
    }

    /*removes a message from the socket. Stops reading at
    0 byte based on M0
     */
    private TextMessage receiveMessage() throws IOException {
		
		int index = 0;
		byte[] msgBytes = null, tmp = null;
		byte[] bufferBytes = new byte[BUFFER_SIZE];//need to add in BUFFER_SIZE constant
		
		/* read first char from stream */
		byte read = (byte) input.read();	
		boolean reading = true;
		logger.info("client recieving message at "+ new Date().toString());
		while(read != 0 && reading) {/* carriage return */
			/* if buffer filled, copy to msg array */
			if(index == BUFFER_SIZE) {
				if(msgBytes == null){
					tmp = new byte[BUFFER_SIZE];
					System.arraycopy(bufferBytes, 0, tmp, 0, BUFFER_SIZE);
				} else {
					tmp = new byte[msgBytes.length + BUFFER_SIZE];
					System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
					System.arraycopy(bufferBytes, 0, tmp, msgBytes.length,
							BUFFER_SIZE);
				}

				msgBytes = tmp;
				bufferBytes = new byte[BUFFER_SIZE];
				index = 0;
			} 
			
			/* only read valid characters, i.e. letters and numbers */
			if((read > 31 && read < 127)) {
				bufferBytes[index] = read;
				index++;
			}
			
			/* stop reading is DROP_SIZE is reached */
			if(msgBytes != null && msgBytes.length + index >= DROP_SIZE) {
				reading = false;
				logger.warn("Drop size reached");
			}
			
			/* read next char from stream */
			read = (byte) input.read();
		}
		
		if(msgBytes == null){
			tmp = new byte[index];
			System.arraycopy(bufferBytes, 0, tmp, 0, index);
		} else {
			tmp = new byte[msgBytes.length + index];
			System.arraycopy(msgBytes, 0, tmp, 0, msgBytes.length);
			System.arraycopy(bufferBytes, 0, tmp, msgBytes.length, index);
		}
		
		msgBytes = tmp;
		
		/* build final String */
		TextMessage msg = new TextMessage(msgBytes);
		logger.info("Receive message:\t '" + msg.getMsg().trim() + "'");
		return msg;
    }

    /*
    Performs marshalling and responses in order to send a Put command to the server

     */
	//@Override
	public KVMessage put(String key, String value) throws Exception {
		try{
			if(this.clientSocket == null){
				String msg = "Put, no client socket: key = '"+key+"', value = ' "+value+"'";
	        	logger.error(msg);
	        	throw new Exception(msg);
			}
			if(!this.clientSocket.isConnected()){
				String msg = "Put, client socket not connected: key = '"+key+"', value = ' "+value+"'";
	        	logger.error(msg);
	        	throw new Exception(msg);
			}
			logger.info("client wants to put key: "+key +" and value: "+value);
			int kl = key.length();
			int vl;
			if (value != null)
				vl = value.length();
			else
				vl = 0;
			//validate that key isn't too long to store
	        if (kl>20){
	        	String msg = "key: '" + key + "' too long, ("+kl+" bytes)";
	        	logger.error(msg);
	        	throw new Exception(msg);
	        }
	        //validate that payload isn't to long to store
	        if (vl > 120*1024){
	        	String msg = "value: '"+value+"' too long, ("+vl+" bytes)";
	        	logger.error(msg);
	        	throw new Exception(msg);
	        }
	        logger.debug("lengths are both ok");
	        //get the length to send for message validation
	        String length = Integer.toString(vl);
	        int ll = length.length();
	        int ml = kl+4+ll;
	        byte[] message = new byte[ml];

	        //begin marshalling for first message exchange
			byte[] byteKey = new byte[kl];
			for(int i =0; i < kl; i++){
				byteKey[i]=(byte)key.charAt(i);
			}

			byte[] length_byte = new byte[ll];
			for(int i =0; i < ll; i++){
				length_byte[i]=(byte)length.charAt(i);
			}
	        TextMessage[] ret_vals = new TextMessage[4];
	        message[0] = (byte) 'P';
	        message[1] = (byte) 0;
	        for (int i = 0; i<kl; i++){
	        	message[2+i]=byteKey[i];
	        }
	        message[2+kl]=0;
	        for (int i = 0; i<ll;i++){
	        	message[3+kl+i]=length_byte[i];
	        }
	        message[3+kl+ll]= (byte)0;
			logger.debug("sent first message of put");
	        this.sendMessage(message,4+kl+ll);
	        for (int i =0; i<3; i++){
	        	ret_vals[i]=this.receiveMessage();
	        	if (ret_vals[0].getMsg().trim().contains("F"))
	        		break;
	        }
	        if (ret_vals[0].getMsg().trim().contains("F")){
	        	String msg = "Put, server sent F when validating key: '"+key+"'";
	        	logger.info(msg);
	        	throw new Exception(msg);
	        }
	        if (!ret_vals[1].getMsg().trim().equals(key) ||
	        		!ret_vals[2].getMsg().trim().equals(length)){
	        	byte[] failure = new byte[2];
	        	failure[0]=(byte) 'F';
	        	failure[1]=0;
	        	this.sendMessage(failure, 2);
	        	String msg = "Put, server responded with incorrect key or size: "
	        			+ ret_vals[1].getMsg().trim() +", " + ret_vals[2].getMsg().trim();
	        	logger.warn(msg);
	        	throw new Exception(msg);
	        }
	        message = null;
	        byte[] message2 = new byte[vl+3];

			byte[] payload_bytes = new byte[vl];
			for(int i =0; i < vl; i++){
				payload_bytes[i]=(byte) value.charAt(i);
			}
	        message2[0]=(byte) 'S';
	        message2[1]=0;
	        for (int i =0; i<vl; i++){
	        	message2[2+i]=payload_bytes[i];
	        }
	        message2[2+vl]=0;
			logger.info("sending payload");
	        this.sendMessage(message2, 3+vl);
	        ret_vals[3]=this.receiveMessage();
	        if (ret_vals[3].getMsg().trim().contains("F")){
	        	String msg = "Put, server sent F after inserting: "
	        			+key +" : "+value;
	        	logger.error(msg);
	        	throw new Exception(msg);
	        }
	        logger.info("put succes");
	        //determine what type of successful operation
	        if (ret_vals[0].getMsg().trim().contains("S")){
	        	return new Message(key, value, KVMessage.StatusType.PUT_SUCCESS);
	        }
	        else{
	        	if (value.equals("null")){
	        		return new Message(key, "null", KVMessage.StatusType.DELETE_SUCCESS);
	        	}
	        	else{
	        		return new Message(key, value, KVMessage.StatusType.PUT_UPDATE);
	        	}
	        }
		}
		catch(Exception ex) {
			if (ex.getMessage().contains("socket"))
				throw ex;
			logger.info(ex.getMessage());
			if (ex.getMessage().contains(", disconnecting")) {
				this.disconnect();
			}
			if (value.equals("null")) {
				return new Message(key, value, KVMessage.StatusType.DELETE_ERROR);
			} else {
				return new Message(key, value, KVMessage.StatusType.PUT_ERROR);
			}
		}

	}
	/*
	performs marshalling and responses required to get a key value pair from
	the KVStore
	 */

	@Override
	public KVMessage get(String key) throws Exception {
		try{
			if(this.clientSocket != null){
				if(this.clientSocket.isConnected())
				{
					logger.info("client wants to get key:"+key);
					byte[] message = new byte[27];
					int kl = key.length();
					byte[] byteKey = new byte[kl];
					//create message
					for(int i =0; i < kl; i++){
						byteKey[i]=(byte)key.charAt(i);
					}

					TextMessage[] ret_vals = new TextMessage[4];

	
					// fill the message with the proper command byte
					message[0] = (byte) 'G';
	
					// pad or align
					message[1] = (byte) 0;
					// fill the get message with a  maximum 20 byte key
					for(int i = 0; i < key.length(); i++)
					{
						message[2+i] = byteKey[i];
					}
					message[2+key.length()]=0;
					logger.info("sending message");
					this.sendMessage(message,3+key.length());
					for (int i =0; i<4;i++){
						ret_vals[i]=this.receiveMessage();
						if (ret_vals[i].getMsg().trim().contains("F")) {

							break;
						}
					}
					if (ret_vals[0].getMsg().trim().contains("F")){
						String msg = "Get, server sent F when validating key: '"+key+"'";
			        	logger.info(msg);
			        	return new Message(key, null, KVMessage.StatusType.GET_ERROR);
					}
					else if(!ret_vals[1].getMsg().trim().equals(key)){
						String msg = "Get, server sent incorrect key: key="
			        			+ key + "returned key = " + ret_vals[1].getMsg().trim();
						logger.warn(msg);
						return new Message(key, null, KVMessage.StatusType.GET_ERROR);
					}
					else if(Integer.parseInt(ret_vals[2].getMsg().trim())!=ret_vals[3].getMsg().trim().length()){
						String msg = "Get, server sent either incorrect payload of incorrect size: payload="
			        			+ret_vals[3].getMsg().trim()+", size="+ret_vals[2].getMsg().trim();
						logger.warn(msg);
						return new Message(key, null, KVMessage.StatusType.GET_ERROR);
					}
					logger.info("payload = "+ret_vals[3].getMsg().trim());

					return new Message(key, ret_vals[3].getMsg().trim(), KVMessage.StatusType.GET_SUCCESS);
				}
			}
		}
		catch(Exception ex){
			if (this.clientSocket == null || (this.clientSocket!=null && !this.clientSocket.isConnected())){
				throw new Exception (ex.getMessage());
			}
        	return new Message(key, null, KVMessage.StatusType.GET_ERROR);
        }
	return null;}
}
