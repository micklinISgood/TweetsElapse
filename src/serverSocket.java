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
import java.net.MalformedURLException;
import java.net.URL;
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

import com.google.gson.Gson;
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
    private String esURL = "http://awseb-e-m-awsebloa-1965qkrpsm12d-1830409115.us-east-1.elb.amazonaws.com:9200/sentiment/_search";
    private String esURL1 = "http://awseb-e-m-awsebloa-1965qkrpsm12d-1830409115.us-east-1.elb.amazonaws.com:9200/sentiment/mick/_search";
    
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
		//System.out.println(this.getClass().getResource("").getPath());

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
    public void incoming(String message) throws Exception {
    	//System.out.println(message);
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
	    				obj.add("update", element.get("data"));
	    				//System.out.println(obj.toString());
	    				broadcast(obj.toString());
				 		break;
    			case "geo_search":
    				//System.out.println(element.get("lat").toString()+element.get("lng").toString());
    				
			 		sendMsg(session,geoQuery(element.get("lat").toString(), element.get("lng").toString()));
			 		
			 		break;
			 		
    			case "key_search":
    				
    				//critical: escape string \" \"
    				String keyw = element.get("keyword").toString();
    				keyw = keyw.substring(1, keyw.length()-1);
  
    				
//    				ArrayList<String> kx = new ArrayList<String>();
//    				kx.add("key");
//    				kx.add("sentiment");
//					list to json
//                  query1.addProperty("fields", new Gson().toJson(kx));
    				
    				sendMsg(session,queryKeyHistroy(keyw));
    				break;
    		}
    		
    	}catch(NullPointerException e){
    		System.out.println(message);
    		e.printStackTrace();
    		
    	}
        // Never trust the client
    	
    	
    	
       
    }




    @OnError
    public void onError(Throwable t) throws Throwable {
           }

    public String geoQuery(String lat, String lng){
    	JsonObject query0 = new JsonObject();
		
		query0.addProperty("lat", lat);
		query0.addProperty("lon", lng);
		JsonObject query1 = new JsonObject();
		query1.add("location",query0);
		query1.addProperty("distance","50km");
		JsonObject query2 = new JsonObject();
		query2.add("geo_distance",query1);
		
	
		JsonObject query5 = new JsonObject();
	
		query5.add( "filter",query2);
		
		JsonObject query6 = new JsonObject();
		query6.add( "filtered" ,query5);
		
		JsonObject query7 = new JsonObject();
		query7.add( "query" ,query6);

		String response = HttpRequest.post(esURL).send(query7.toString()).body();
		JsonObject sr = jsonParser.parse(response).getAsJsonObject();
		JsonArray asr = sr.get("hits").getAsJsonObject().get("hits").getAsJsonArray();

		
		JsonObject rb = new JsonObject();
	    rb.add("geo_res", asr);
    	
	    return rb.toString();
    	
    }
    
    public String queryKeyHistroy(String keyw){
    	
    	JsonObject query1 = new JsonObject();
	
    	JsonObject query0 = new JsonObject();
		query0.addProperty("key", keyw);
		//query0.addProperty("sentiment", 5);
	
		JsonObject query4 = new JsonObject();
		query4.add("term", query0);
		query1.add("query", query4);
		query1.addProperty("from", 0);
		query1.addProperty("size", 10000);

		String response1 = HttpRequest.post(esURL1).send(query1.toString()).body();
		JsonObject sr1 = jsonParser.parse(response1).getAsJsonObject();
		JsonArray asr1 = sr1.get("hits").getAsJsonObject().get("hits").getAsJsonArray();
		
	
		JsonObject line = new JsonObject();
		for(JsonElement x: asr1){
			line.add(x.getAsJsonObject().get("_source").getAsJsonObject().get("tid").toString(), x.getAsJsonObject().get("_source"));
		}
		
		
		JsonObject res = new JsonObject();
		res.add("update", line);
		
		
		return res.toString();
    	
    }
    
    public void broadcast(String msg){
    	 for (serverSocket client : connections) {
    		 if(client.session==this.session) continue;
             try {
                 synchronized (client) {
                     client.session.getBasicRemote().sendText(msg);
                 }
             } catch (IOException e) {
            	 System.out.println("Chat Error: Failed to send message to client"+e);
                 connections.remove(client);
                 try {
                     client.session.close();
                 } catch (IOException e1) {
                     // Ignore
                 }
               
             }
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
