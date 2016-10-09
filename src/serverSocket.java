/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

import java.io.BufferedReader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;



import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;



@ServerEndpoint("/conn")
public class serverSocket {

   
    private static String uuid;
    private static final AtomicInteger connectionIds = new AtomicInteger(0);
    private static final Set<serverSocket> connections =
            new CopyOnWriteArraySet<>();

    
    private Session session;
    private Map<String,Session> connPool = new ConcurrentHashMap<>();
    private JsonObject list;
    
    private JsonParser jsonParser;
    
    public serverSocket() throws IOException {
    	MessageDigest md;
		try {
			md = MessageDigest.getInstance("md5");
			byte[] bytesOfMessage = String.valueOf(connectionIds.getAndIncrement()).getBytes("UTF-8");
			uuid = md.digest(bytesOfMessage).toString();

		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
    public JsonObject sortJson(JsonArray arr) {
    	
    	List<JsonObject> tmp = new ArrayList<JsonObject>();
    	for(JsonElement obj: arr){
    		tmp.add(obj.getAsJsonObject());
    	}
    	Collections.sort(tmp, new Comparator<JsonObject>(){
    		@Override
            public int compare(JsonObject a, JsonObject b){
    			return (int)(a.get("epoch").getAsLong()-b.get("epoch").getAsLong());
    		}
    	});
    	
    	//classify by time slots
    	long start = tmp.get(0).get("epoch").getAsLong();
    	//15 mins
    	long slot = 15L*60L;
  
    	start += slot;
    	JsonArray collection = new JsonArray();
    	JsonObject done = new JsonObject();
    	for(JsonObject obj: tmp){
    		if(obj.get("epoch").getAsLong() > start){
    			done.add(String.valueOf(start), collection);
    			start = obj.get("epoch").getAsLong();
    			start += slot;
    			collection = new JsonArray();
    			collection.add(obj);
    		}else{
    			collection.add(obj);
    	
    		}
    		
    	}
    	
    	return  done;
    }

    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);
    	try {
            synchronized (this) {
            	JsonObject ret = new JsonObject();
            	ret.addProperty("access_key", uuid);
            	this.session.getBasicRemote().sendText(ret.toString());
            	connPool.put(uuid,session);
            }
        } catch (IOException e) {
        	   e.printStackTrace();
              
            try {
                this.session.close();
            } catch (IOException e1) {
                // Ignore
            }
      
        }
    	
		jsonParser= new JsonParser();
		
		//tricky, put tweets_data in this path
		System.out.println(this.getClass().getResource("").getPath());

		JsonArray rawlist = new JsonArray();
        try {
        	BufferedReader br = new BufferedReader(new InputStreamReader(serverSocket.class.getResourceAsStream("/tweets_data.txt")));
        	String line = br.readLine();
        	rawlist = jsonParser.parse(line).getAsJsonArray();
        	
        	br.close();
            //System.out.println(list);
        	
        } catch (IOException e) {
        	 System.out.println(this.getClass().getResource("").getPath());
        	 e.printStackTrace();
        }
        
        list = sortJson(rawlist);
        
        

    }


    @OnClose
    public void end() {
        connections.remove(this);
        connPool.remove(uuid);
      
    }


    @OnMessage
    public void incoming(String message) {
    	System.out.println(message);
    	JsonObject element = jsonParser.parse(message).getAsJsonObject();
    	//System.out.println(element);
    	try{
    		String act = element.get("action").toString();
    		//critical: escape ", "a" -> a
    		act = act.substring(1, act.length()-1);
    	
    		switch(act){
    			case "ELAPSE":
    				    JsonObject obj = new JsonObject();
    				    obj.add("elapse", list);
    			 		sendMsg(session,obj.toString());
    			 		break;
    			case "UpdateKeyWords":
	    				obj = new JsonObject();
	    				obj.add("Update", element.get("data"));
	    				broadcast(obj.toString());
				 		break;
    		
    		}
    	}catch(NullPointerException e){
    		System.out.println(message);
    		//e.printStackTrace();
    		
    	}
        // Never trust the client
    	
    	
    	
       
    }




    @OnError
    public void onError(Throwable t) throws Throwable {
           }

    
    public void broadcast(String content){
    	for (Session peer : session.getOpenSessions()){
    		 sendMsg(peer,content);
    	}
    }
    
    public void sendMsg(Session session,String content){
    	
    	try {
            synchronized (this) {
            	
            	session.getBasicRemote().sendText(content);

            }
        } catch (IOException e) {
               
            try {
                session.close();
            } catch (IOException e1) {
                // Ignore
            }
      
        }
    }
    
}
