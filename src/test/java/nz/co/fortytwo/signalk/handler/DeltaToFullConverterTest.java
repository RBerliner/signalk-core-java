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
package nz.co.fortytwo.signalk.handler;

import static nz.co.fortytwo.signalk.util.JsonConstants.SELF;
import static nz.co.fortytwo.signalk.util.JsonConstants.SOURCE;
import static nz.co.fortytwo.signalk.util.JsonConstants.TIMESTAMP;
import static nz.co.fortytwo.signalk.util.JsonConstants.VALUE;
import static nz.co.fortytwo.signalk.util.JsonConstants.VESSELS;
import static nz.co.fortytwo.signalk.util.SignalKConstants.nav;
import static org.junit.Assert.assertEquals;
import mjson.Json;
import nz.co.fortytwo.signalk.model.impl.SignalKModelFactory;
import nz.co.fortytwo.signalk.model.impl.SignalKModelImpl;

import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DeltaToFullConverterTest {

	String jsonDiff = "{\"context\": \"vessels."+SELF+".navigation\",\"updates\":[{\"source\": {\"device\" : \"/dev/actisense\",\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"src\":\"115\",\"pgn\":\"128267\"},\"values\": [{ \"path\": \"courseOverGroundTrue\",\"value\": 172.9 },{ \"path\": \"speedOverGround\",\"value\": 3.85 }]}]}";
	String jsonDiff1 = "{\"context\": \"vessels."+SELF+"\",\"updates\":[{\"source\": {\"device\" : \"/dev/actisense\", \"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"src\":\"115\",\"pgn\":\"128267\"},\"values\": [{ \"path\": \"navigation.courseOverGroundTrue\",\"value\": 172.9 },{ \"path\": \"navigation.speedOverGround\",\"value\": 3.85 }]}]}";
	String jsonDiff2 = "{\"context\": \"vessels."+SELF+".navigation\",\"updates\":[{\"source\": {\"device\" : \"/dev/actisense\",\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"src\":\"115\",\"pgn\":\"128267\"},\"values\": [{ \"path\": \"courseOverGroundTrue\",\"value\": 172.9 },{ \"path\": \"speedOverGround\",\"value\": 3.85 }]},{\"source\": {\"device\" : \"/dev/ttyUSB0\",\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"src\":\"115\",\"pgn\":\"128267\"},\"values\": [{ \"path\": \"courseOverGroundMagnetic\",\"value\": 152.9 },{ \"path\": \"speedOverWater\",\"value\": 2.85 }]}]}";
	String jsonDiff3 = "{\"updates\":[{\"values\":[{\"value\":172.9,\"path\":\"courseOverGroundTrue\"},{\"value\":3.85,\"path\":\"speedOverGround\"}],\"source\":{\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"device\":\"/dev/actisense\",\"pgn\":\"128267\",\"src\":\"115\"}}],\"context\":\"vessels."+SELF+".navigation\"}";
	private static Logger logger = Logger.getLogger(SignalKModelImpl.class);
	@Before
	public void setUp() throws Exception {
		
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void shouldProcessDiff() {
		Json diff = Json.read(jsonDiff);
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(172.9, output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(SOURCE).asString());
		
		assertEquals(3.85, output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(SOURCE).asString());
	}
	@Test
	public void shouldProcessDiff3() {
		Json diff = Json.read(jsonDiff3);
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(172.9, output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(SOURCE).asString());
		
		assertEquals(3.85, output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(SOURCE).asString());
	}

	@Test
	public void shouldIgnoreSignalKJson() {
		Json diff = (Json) SignalKModelFactory.getCleanInstance();
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(diff.toString(), output.toString());
	}
	@Test
	public void shouldIgnoreRandomJson() {
		Json diff = Json.read("{\"headingTrue\": {\"value\": 23,\"source\": \""+SELF+"\",\"timestamp\": \"2014-03-24T00: 15: 41Z\" }}");
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(diff.toString(), output.toString());
	}
	@Test
	public void shouldProcessComplexDiff() {
		Json diff = Json.read(jsonDiff1);
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(172.9, output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(SOURCE).asString());
		
		assertEquals(3.85, output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(SOURCE).asString());
	}
	
	@Test
	public void shouldProcessDiffArray() {
		Json diff = Json.read(jsonDiff2);
		DeltaToFullConverter processor = new DeltaToFullConverter();
		Json output = processor.handle(diff);
		logger.debug(output);
		assertEquals(172.9, output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundTrue").at(SOURCE).asString());
		
		assertEquals(3.85, output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(TIMESTAMP).asString());
		assertEquals("/dev/actisense-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("speedOverGround").at(SOURCE).asString());
		
		assertEquals(152.9, output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundMagnetic").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundMagnetic").at(TIMESTAMP).asString());
		assertEquals("/dev/ttyUSB0-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("courseOverGroundMagnetic").at(SOURCE).asString());
		
		assertEquals(2.85, output.at(VESSELS).at(SELF).at(nav).at("speedOverWater").at(VALUE).asDouble(),001);
		assertEquals("2014-08-15T16:00:00.081Z", output.at(VESSELS).at(SELF).at(nav).at("speedOverWater").at(TIMESTAMP).asString());
		assertEquals("/dev/ttyUSB0-N2K-115-128267", output.at(VESSELS).at(SELF).at(nav).at("speedOverWater").at(SOURCE).asString());
	}
}
