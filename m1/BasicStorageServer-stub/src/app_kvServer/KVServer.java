package app_kvServer;

import cache.KVCache;
import cache.FIFOCache;
import cache.LFUCache;
import cache.LRUCache;

import logger.LogSetup;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;


public class KVServer  {
	
	/**
	* Start KV Server at given port
	* @param port given port for storage server to operate
	* @param cacheSize specifies how many key-value pairs the server is allowed
	*           to keep in-memory
	* @param strategy specifies the cache replacement strategy in case the cache
	*           is full and there is a GET- or PUT-request on a key that is
	*           currently not contained in the cache. Options are "FIFO", "LRU",
	*           and "LFU".
	*/

	private int port;
	private int cacheSize;
	private String strategy;
	private boolean isRunning;
	private ServerSocket socket;
    private static Logger logger = Logger.getRootLogger();
    private boolean log;

    private String KVFileName;

    private KVCache cache;

    /**
    * KVServer contructor
    * @param port the port on which the server is to run
    * @param cSize the size of the cache in terms of the number of key-value pairs
    * @param strat cache policy to run
    * @param KVFName the file in which to store KVP on disk
    * @param log a variable to toggle logging
    * */
	public KVServer(int port, int cSize, String strat, String KVFName, boolean log) {
		this.port = port;
		this.cacheSize = cSize;
		this.strategy = strat;

		if(cSize <= 0) {//user wants no caching
            this.cache = null;
        }
        else {
            if (this.strategy.equals("FIFO")) {
                this.cache = new FIFOCache(this.cacheSize);
            }
            else if (this.strategy.equals("LFU")) {
                this.cache = new LFUCache(this.cacheSize);
            }
            else if (this.strategy.equals("LRU")) {
                this.cache = new LRUCache(this.cacheSize);
            }
            else {
                this.cache = null;
            }
        }
		this.KVFileName = KVFName;
        this.log = log;
	}

    public KVCache getKvcache(){return this.cache;}


    /**
    * Repeatedly listen for incoming connections, once a connection is recieved,
    * pass it to a worker thread. The work done by a worker thread is defined in
    * ClientConnection
    * */
	public void Run(){

	    isRunning = InitializeServer();
        if(socket != null) {
            while(isRunning){
                try {
                    Socket client = socket.accept();
                    ClientConnection connection =
                            new ClientConnection(client,this, this.KVFileName, this.log);
                    new Thread(connection).start();

                    if(log) {
                        logger.info("Connected to "
                                + client.getInetAddress().getHostName()
                                + " on port " + client.getPort());
                    }
                } catch (IOException e) {
                    if(log) {
                        logger.error("Error! " +
                                "Unable to establish connection. \n", e);
                    }
                }
            }
        }
        if(log) {
            logger.info("Server stopped.");
        }
	}

	/**
	* Stops the server
	* */
	public void Stop(){
        isRunning = false;
        try {
            socket.close();
        } catch (IOException e) {
            logger.error("Error! " +
                    "Unable to close socket on port: " + port, e);

        }
    }

    /**
    * Initializes the server by trying to obtain a socket on the port
    * */
	private boolean InitializeServer(){
        logger.info("Initialize server ...");

        try {
            socket = new ServerSocket(port);
            logger.info("Server listening on port: "
                    + socket.getLocalPort());

            return true;

        } catch (IOException e) {
            logger.error("Error! Cannot open server socket:");
            if(e instanceof BindException){
                logger.error("Port " + port + " is already bound!");
            }
            return false;
        }
    }

    /**
    * Entry point for KVServer
    * Parses command line arguments and starts a KVServer
    * @param args an array of string command line arguments
    * */
    public static void main(String[] args){
	    // todo validation
	    int port = Integer.parseInt(args[0]);
	    int cacheSize = Integer.parseInt(args[1]);
        String strategy = args[2];
        String KVFileName = args[3];
        boolean shouldLog = Boolean.parseBoolean(args[4]);

        try {
            new LogSetup(System.getProperty("user.dir")+"/logs/server.log", Level.ALL);
        } catch (Exception ex)
        {
            System.out.println(ex.getMessage());
        }

        KVServer server = new KVServer(port, cacheSize, strategy, KVFileName, shouldLog);
        server.Run();
    }


    /**
    * KVServer contains a global cache and provides an interface to threads
    * for adding to a cache
    * @param k the key
    * @param v the value
    * */
    public void addToCache(String k, String v)
    {

        if (this.cacheSize >0) {
            logger.debug("adding "+k+" : "+ v+"to cache");
            this.cache.insertInCache(k, v);
        }
    }

    /**
    * KVServer contains a global cache and provides an interface to threads
    * for finding something in the cache
    * @param k the key
    * @param log variable to toggle logging
    * */
    public String findInCache(String k, boolean log)
    {
        if (this.cacheSize == 0)
            return null;

        String val = this.cache.checkCache(k,log);
        logger.debug("found " + val +"in cache");
        return val;
    }
}
