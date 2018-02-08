package de.upb.crc901.services.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

public class Exchange {

	
	
	public final void addKeyWordArgument(String keyword, Object Value) {
//		keywordArguments.put(keyword, new Stream<Object>().value);
	}
	

	/**
	 * Returns true if the given object is primitive.
	 * 
	 */
	public static boolean isPrimitive(Object o) {
		if(o instanceof Number) {
			return true;
		}
		if(o instanceof String) {
			return true;
		}
		if(o instanceof Boolean) {
			return true;
		}
		return false;
	}
	
	/**
	 * StreamHandler for primitive objects.
	 * @author aminfaez
	 *
	 */
	final class PrimitiveStreamHandler implements StreamHandler {
		private Object primitiveObject;
		PrimitiveStreamHandler(Object primitiveObject){
			this.primitiveObject = Objects.requireNonNull(primitiveObject);
			
		}
		
		
		@Override
		public JASEDataObject read(JsonParser jsonIn) {
			try {
				while (jsonIn.nextToken() != JsonToken.END_OBJECT) {
					String fieldname = jParser.getCurrentName();
				    if ("name".equals(fieldname)) {
				    		
				    }
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public void write(JsonGenerator jsonOut, Object) {
			
		}
		
	}
	
}