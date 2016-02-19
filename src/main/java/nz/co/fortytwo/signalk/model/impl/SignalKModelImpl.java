/*
 *
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nz.co.fortytwo.signalk.model.impl;
import static nz.co.fortytwo.signalk.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.util.SignalKConstants.source;
import static nz.co.fortytwo.signalk.util.SignalKConstants.timestamp;
import static nz.co.fortytwo.signalk.util.SignalKConstants.value;
import static nz.co.fortytwo.signalk.util.SignalKConstants.values;
import static nz.co.fortytwo.signalk.util.SignalKConstants.vessels;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import mjson.Json;
import nz.co.fortytwo.signalk.model.SignalKModel;
import nz.co.fortytwo.signalk.model.event.PathEvent;
import nz.co.fortytwo.signalk.util.SignalKConstants;
import nz.co.fortytwo.signalk.util.Util;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;

import com.google.common.eventbus.EventBus;

/**
 * <p>
 * A thread-safe datamodel. Objects are stored with hierarchical keys, eg "a.b"
 * or "a.b.c", and a node in the tree can be a leaf or an intermediate, not both.
 * Nodes are always stored alphabetically.  Objects can be inserted or deleted
 * on any thread  or for the whole model or a subtree
 * of it without locking and without needing to synchronized on the returned tree.
 * </p><pre>

 * model.put("vessels.self.navigation.position.latitude", 57.9);
 * model.put("vessels.self.navigation.position.longitude", 17.2);
 * model.put("vessels.self.navigation.position.source", "gps");
 * model.put("vessels.self.navigation.position.teimstamp", model.timestamp());

 * </pre>
 * <p>
 * Changes to the model are notified on the Guava EventBus. To listen for changes 
 * obtain the event bus (getEventBus()) and register for PathEvent
 * </p>
 */
public class SignalKModelImpl implements SignalKModel {
    
	private static Logger logger = Logger.getLogger(SignalKModelImpl.class);
    private final char separator;
    private final NavigableMap<String,Object> root;
    
    private int nextrevision;

  	private EventBus eventBus = new EventBus();
    
  	 /**
     * Create a new Model
     */
    public SignalKModelImpl() {
        this.separator = '.';
        root = new ConcurrentSkipListMap<String,Object>();
    }
    
    /**
     * Create a new model from the provided sublist.
     * @param root
     */
    public SignalKModelImpl(NavigableMap<String,Object> root) {
        this.separator = '.';
        this.root = new ConcurrentSkipListMap<String,Object>(root);
    }
    
    
    /**
     * Return the hierarchy separator
     */
    public char getSeparator() {
        return separator;
    }


    private boolean doPut(String key, Object val) {
        // If val = "aa.bb.cc", fail if map contains "aa.bb" or "aa.bb.cc.dd"
        String othkey = root.lowerKey(key);
        if (othkey != null && key.startsWith(othkey) && key.charAt(othkey.length()) == separator) {
            throw new IllegalArgumentException("Can't insert key \""+key+"\" into Model containing \""+othkey+"\"");
        }
        othkey = root.higherKey(key);
        if (othkey != null && othkey.startsWith(key) && othkey.charAt(key.length()) == separator) {
            throw new IllegalArgumentException("Can't insert key \""+key+"\" into Model containing \""+othkey+"\"");
        }
        //meta.zones array
        if (!val.equals(root.put(key, val))) {
        	if(logger.isDebugEnabled())logger.debug("doPut "+key+"="+val);
        	if(!key.endsWith(dot+source)&& !key.endsWith(dot+timestamp)&&!key.contains(dot+source+dot)){
        		eventBus.post(new PathEvent(key, nextrevision, PathEvent.EventType.ADD));
        	}
            return true;
        } else {
            return false;
        }
    }

    private boolean doDelete(String key,NavigableMap<String, Object> map ) {
        NavigableMap<String,Object> subMap = map.tailMap(key, true);
        boolean found = false;
        for (Iterator<String> i = subMap.keySet().iterator();i.hasNext();) {
            String mapkey = i.next();
            if (mapkey.startsWith(key) && (mapkey.length() == key.length() || mapkey.charAt(key.length()) == separator)) {
            	eventBus.post(new PathEvent(mapkey, nextrevision ,PathEvent.EventType.DEL));
                i.remove();
                found = true;
            } else {
                break;
            }
        }
        return found;
    }

 
    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#put(java.lang.String, boolean)
	 */
    @Override
	public boolean put(String key, Object val) throws IllegalArgumentException{
    	key = fixSelfKey(key);
    	if(val == null){
    		//TODO: we delete the val, and the values equiv, then promote the next values object
    		
    		return doDelete(key, root);
		}
    	if(val instanceof Boolean 
    			|| val instanceof Number 
    			|| val instanceof String){
    		if(logger.isDebugEnabled())logger.debug("Put "+key+"="+val);
    		return doPut(key, val);
    	}
    	if(val instanceof Json && ((Json)val).isArray() ){
    		if(logger.isDebugEnabled())logger.debug("Put "+key+"="+val);
    		return doPut(key, val);
    	}
    	throw new IllegalArgumentException("Must be String, Number,Boolean or null : "+val.getClass()+":"+val);
    }

    private String fixSelfKey(String key) {
    
		return Util.fixSelfKey(key);
	}

	@Override
	public boolean put(String key, Object val, String source) throws IllegalArgumentException {
		return put(key,val,source,Util.getIsoTimeString());
    	//key = fixSelfKey(key);
    	//if(source==null)return (doPut(key, val));
		//return (doPut(key+dot+value, val)&& doPut(key+dot+source, source));
	}

	@Override
	public boolean put(String key, Object val, String src, String ts) throws IllegalArgumentException {
		key = fixSelfKey(key);
		if(StringUtils.isBlank(src)) src="default";
		boolean success = putValues(key, val, src, ts);
		String curSource = (String) root.get(key+dot+SignalKConstants.source);
		if(success && (StringUtils.isBlank(curSource)||StringUtils.equals(curSource, src))){
			if(ts!=null)return(doPut(key+dot+value, val)&& doPut(key+dot+SignalKConstants.source, src)&& doPut(key+dot+timestamp, ts));
			if(ts==null)return(doPut(key+dot+value, val)&& doPut(key+dot+SignalKConstants.source, src));
		}
		return success;
	}
    

	/**
	 * Adds the val to the values arr
	 * @param string
	 * @param val
	 * @param timestamp 
	 * @param src 
	 * @return
	 */
	private boolean putValues(String key, Object val, String src, String ts) {
				
		String vKey = key+".values."+source;
		if(ts!=null){
			return (doPut(vKey+dot+value, val)&& doPut(vKey+dot+source, src)&& doPut(vKey+dot+timestamp, ts));
		}else{
			return (doPut(vKey+dot+value, val)&& doPut(vKey+dot+source, src));
		}
		

	}

	/* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#get(java.lang.String)
	 */
    @Override
	public Object get(String key) {
    	key = fixSelfKey(key);
    	return nullFix(root.get(key));
    }
    
    /**
     * ConcurrentSkipList cant store nulls so we store "null". Fix that here
     * @param object
     * @return
     */
    private Object nullFix(Object object) {
		if("null".equals(object))return null;
		return object;
	}

	/* (non-Javadoc)
   	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#get(java.lang.String)
   	 */
       @Override
   	public Object getValue(String key) {
    	   key = fixSelfKey(key);
               return nullFix(root.get(key+dot+value));
       }

    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getTree(java.lang.String)
	 */
    @Override
	public NavigableSet<String> getTree(String key) {
    	key = fixSelfKey(key);
         return getKeys().subSet(key, true, key+".\uFFFD", true);
    }
    
    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getTree(java.lang.String)
	 */
    @Override
	public NavigableMap<String, Object> getSubMap(String key) {
    	key = fixSelfKey(key);
            return root.subMap(key, true, key+".\uFFFD", true);
    }


    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getEventBus()
	 */
    @Override
	public EventBus getEventBus() {
		return eventBus;
	}
    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getKeys()
	 */
    @Override
	public NavigableSet<String> getKeys() {
        //return Collections.unmodifiableNavigableSet(root.navigableKeySet());      // Java 8 method
    	return root.navigableKeySet();
    }

    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getData()
	 */
    @Override
	public SortedMap<String,Object> getData() {
        return getSubMap(vessels);
    }
    
    /* (non-Javadoc)
	 * @see nz.co.fortytwo.signalk.model.impl.SignalKModel#getData()
	 */
    @Override
	public SortedMap<String,Object> getFullData() {
        return root;
    }

    public String toString() {
            return root.toString();
    }

	@Override
	public boolean putAll(SortedMap<String, Object> map) {
		boolean success = true;
		for(Entry<String, Object> entry: map.entrySet()){
			if(logger.isDebugEnabled())logger.debug("Adding "+entry.getKey()+"="+entry.getValue());
			boolean s = put(entry.getKey(),entry.getValue());
			success = success && s;
		}
		if(logger.isDebugEnabled())logger.debug("putAll done: "+this);
		return success;
	}

	@Override
	public boolean putValue(String key, Object val) {
		key = fixSelfKey(key);
		return put(key+dot+value, val);
	}

	@Override
	public NavigableMap<String, Object> getValues(String key) {
		key = fixSelfKey(key);
        return getSubMap(key+dot+values);
	}

	

}

