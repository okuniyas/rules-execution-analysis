/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.redhat.example.rules.runtimestats.impl;

import java.io.IOException;
import java.util.Iterator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

/**
 * custom JsonSerializer to avoid long line
 * @author okuniyas
 */
public class WrapItemSerializer extends StdSerializer<Iterator<?>> {
	private static final long serialVersionUID = 1L;
	
	public WrapItemSerializer() {
		this(null);
	}
	
	public WrapItemSerializer(Class<Iterator<?>> t) {
		super(t);
	}

	@Override
	public void serialize(Iterator<?> it, JsonGenerator jgen, SerializerProvider provider) throws IOException {
		// empty array
		if (! it.hasNext()) {
			jgen.writeStartArray();
			jgen.writeEndArray();
			return;
		}
		// at least one item
		jgen.writeStartArray();
		jgen.writeRaw("\n\"");
		jgen.writeRaw(it.next().toString());
		while (it.hasNext()) {
			jgen.writeRaw("\",\n\"");
			jgen.writeRaw(it.next().toString());
		}
		jgen.writeRaw("\"\n");
		jgen.writeEndArray();
	}
}