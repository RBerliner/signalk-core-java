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
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nz.co.fortytwo.signalk.handler;

import static nz.co.fortytwo.signalk.util.ConfigConstants.MIME_TYPE;
import static nz.co.fortytwo.signalk.util.ConfigConstants.PAYLOAD;
import static nz.co.fortytwo.signalk.util.ConfigConstants.STORAGE_ROOT;
import static nz.co.fortytwo.signalk.util.ConfigConstants.STORAGE_URI;
import static nz.co.fortytwo.signalk.util.SignalKConstants.CONTEXT;
import static nz.co.fortytwo.signalk.util.SignalKConstants.PATH;
import static nz.co.fortytwo.signalk.util.SignalKConstants.PUT;
import static nz.co.fortytwo.signalk.util.SignalKConstants.UPDATES;
import static nz.co.fortytwo.signalk.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.util.SignalKConstants.value;
import static nz.co.fortytwo.signalk.util.SignalKConstants.values;
import static nz.co.fortytwo.signalk.util.SignalKConstants.vessels;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import mjson.Json;
import nz.co.fortytwo.signalk.util.SignalKConstants;
import nz.co.fortytwo.signalk.util.Util;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager; import org.apache.logging.log4j.Logger;

/**
 * Handles json messages with large blobs 
 * 
 * @author robert
 * 
 */
public class JsonStorageHandler {

	public static final String PARENT_PATH = "parentPath";
	private static Logger logger = LogManager.getLogger(JsonStorageHandler.class);
	private File storageDir = new File(Util.getConfigProperty(STORAGE_ROOT));
	private Map<String, String> mimeMap = new HashMap<String, String>();

	/**
	 * Process the message and on ingoing and extract the payload to storage, so we dont pass big blobs into the model
	 * Extracts them back into the message again on outgoing. Uses the default mimetypes mapping file
	 * @throws IOException
	 */
	public JsonStorageHandler() throws IOException {
		try (InputStream is = getClass().getResourceAsStream("/mime.types")) {
			BufferedReader reader = new BufferedReader(new InputStreamReader(is));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = line.split("=");
				mimeMap.put(parts[0], parts[1]);
			}
		}
	}

	public Json handle(Json node) throws Exception {
		// avoid full signalk syntax
		if (node.has(vessels)){
			process(vessels,node.at(vessels));
			return node;
		}
			
		// deal with diff format
		if (node.has(CONTEXT)) {
			if (logger.isDebugEnabled())
				logger.debug("processing put  " + node);

			// go to context
			String ctx = node.at(CONTEXT).asString();
			ctx=Util.fixSelfKey(ctx);

			Json puts = node.at(PUT);
			if (puts == null)
				return node;
			if (puts.isArray()) {
				for (Json put : puts.asJsonList()) {
					parseUpdate( put, ctx);
				}
			} else {
				parseUpdate( puts.at(UPDATES), ctx);
			}

			if (logger.isDebugEnabled())
				logger.debug("JsonPutHandler processed put " + node);
			return node;
		}
		return null;

	}

	protected void parseUpdate( Json update, String ctx) throws Exception {

		// DateTime timestamp = DateTime.parse(ts,fmt);

		// grab values and add
		Json array = update.at(values);
		for (Json e : array.asJsonList()) {
			String key = e.at(PATH).asString();
			// temp.put(ctx+"."+key, e.at(value).getValue());
			if(process( ctx + dot + key, e.at(value))){
				//its a delete
				e.set(value, Json.nil());
			};

		}

	}

	protected boolean process( String ctx, Json j) throws IOException {
		// capture and store any embedded content
		if (j.isObject()) {
			if (j.has(PAYLOAD)) {

				String ext = getExtension(j);
				String filePath = ctx.replace('.', '/') ;

				String payload = null;
				File content = new File(storageDir, filePath + SignalKConstants.dot+ ext);
				File data = new File(storageDir, filePath+ "db.json");
				if (j.at(PAYLOAD).isNull()){
					//we want to delete it
					if (logger.isDebugEnabled())logger.debug("Delete from "+content.getAbsolutePath());
					FileUtils.deleteQuietly(content);
					FileUtils.deleteQuietly(data);
					//now delete from model
					return true;
				}
				if (j.at(PAYLOAD).isString()) {
					// save it separately and add a storage url
					payload = j.at(PAYLOAD).asString();
				}
				if (j.at(PAYLOAD).isObject()) {
					// save it separately and add a storage url
					payload = j.at(PAYLOAD).toString().trim();
				}
				
				if (logger.isDebugEnabled())logger.debug("Save to from "+content.getAbsolutePath());
				FileUtils.writeStringToFile(content, payload);
				j.set(STORAGE_URI, filePath + SignalKConstants.dot+ ext);
				j.delAt(PAYLOAD);
				Json dataJson = j.dup();
				dataJson.set(PARENT_PATH, ctx);
				//save the data
				FileUtils.writeStringToFile(data, dataJson.toString());
			} else if (j.has(STORAGE_URI)) {
				String filePath = j.at(STORAGE_URI).asString();
				File save = new File(storageDir, filePath);
				if (logger.isDebugEnabled())logger.debug("Retrieve from "+save.getAbsolutePath());
				String payload = FileUtils.readFileToString(save);
				if (payload.startsWith("{") && payload.endsWith("}")) {
					j.set(PAYLOAD, Json.read(payload));
				} else {
					j.set(PAYLOAD, payload);
				}
				j.delAt(STORAGE_URI);
			} else {
				for (Json child : j.asJsonMap().values()) {
					if (logger.isDebugEnabled())logger.debug("Retrieve from "+ctx + dot + j.getParentKey()+", json:"+j);
					process( ctx + dot + j.getParentKey(), child);
				}
			}
		}
		return false;
	}

	private String getExtension(Json j) {
		String ext = null;
		if (j.has(MIME_TYPE)) {
			ext = mimeMap.get(j.at(MIME_TYPE).asString());
		}
		if (ext == null) {
			ext = "txt";
		}
		return ext;
	}

}
