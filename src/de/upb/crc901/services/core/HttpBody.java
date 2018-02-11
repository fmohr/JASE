package de.upb.crc901.services.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.reflect.MethodUtils;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;

/**
 * A data structure class, which encodes or decodes Post body data. 
 * This is used by HttpServiceClient, whose data is encoded to the post's body content, 
 * and it is also used by HttpServiceServer, who initializes an instance by the encoded post body it receives.
 * (Note: This class was created to take care of logic that was implemented in the client and server class before.)
 * 
 * @author aminfaez
 *
 */
public final class HttpBody {
	
	
	
	/**
	 * Constant strings used in communication.
	 */
	public final static String 	CHOREOGRAPGY_FIELDNAME = "choreography",
								CURRENTINDEX_FIELDNAME = "currentindex",
								MAXINDEX_FIELDNAME = "maxindex",
								INPUTS_FIELDNAME = "inputs";

	private static final String ARGLIST_FIELDNAME = "$arg_list$";
	
	public static final String PRIMITIVE_TYPE = "primitive";
	
	private String composition = null;
	private int currentIndex = 0;
	private int maxIndex = -1;
	private List<JASEDataObject> positionalArguments = new ArrayList<>();
	private Map<String, JASEDataObject> keywordArguments = new HashMap<>();
	
	
	/**
	 * @return the composition
	 */
	public final String getComposition() {
		return composition;
	}
	/**
	 * @param composition the composition to set
	 */
	public final void setComposition(String composition) {
		this.composition = composition;
	}
	/**
	 * @return the currentIndex
	 */
	public final int getCurrentIndex() {
		return currentIndex;
	}
	/**
	 * @param currentIndex the current index to set
	 */
	public final void setCurrentIndex(int currentIndex) {
		this.currentIndex = currentIndex;
	}
	/**
	 * @return the maxIndex
	 */
	public final int getMaxIndex() {
		return maxIndex;
	}
	/**
	 * @param maxIndex the max index to set
	 */
	public final void setMaxIndex(int maxIndex) {
		this.maxIndex = maxIndex;
	}
	
	public HttpBody(){
		
	}
	
	public HttpBody(Map<String, JASEDataObject> inputs, List<JASEDataObject> listInputs, String chorepgraphy, int currentIndex, int maxindex) {
		this.keywordArguments = inputs;
		this.positionalArguments = listInputs;
		this.composition = chorepgraphy;
		this.currentIndex = currentIndex;
		this.maxIndex = maxindex;
	}
	
	public void addKeyworkArgument(String name, JASEDataObject data) {
		keywordArguments.put(name, data);
	}
	
	public void addPositionalArgument(JASEDataObject data) {
		positionalArguments.add(data);
	}
	
	public void addUnparsedKeywordArgument(OntologicalTypeMarshallingSystem otms, String name, Object o) {
		addKeyworkArgument(name, parseToSemanticObject(otms, o));
	}
	
	public void addUnparsedPositionalArgument(OntologicalTypeMarshallingSystem otms, Object o) {
		
		addPositionalArgument(parseToSemanticObject(otms, o));
	}
	
	private JASEDataObject parseToSemanticObject(OntologicalTypeMarshallingSystem otms, Object o) {
		if(isPrimitive(o)) {
			// unparsed
			JASEDataObject jdo = new JASEDataObject(PRIMITIVE_TYPE, o);
			return jdo;
		}else {
			return otms.objectToSemantic(o);
		}
	}
	
	
	/**
	 * Parses the contained composition to SequentialComposition.
	 * @return a SequentialComposition object. 
	 */
	public SequentialComposition getSequentialComposition() {
		SequentialCompositionSerializer scs = new SequentialCompositionSerializer();
		// TODO workaround because bug in scs. remove the replacement after the bug is fixed:
		String composition = getComposition();
		composition = composition.replaceAll("\\(\\{\\}\\)", "({,})"); // add comma to empty inputs
		// end of workaround
		return scs.readComposition(composition);
	}
	
	/**
	 * Returns the operation in position index from parsedCompositionField.
	 * @param index position of operation in composition.
	 * @return the addressed operation
	 */
	public OperationInvocation getOperation(int index) {
		for(OperationInvocation opInv : getSequentialComposition()) {
			if(index != 0) {
				// decrease index until it hits 0.
				index--;
			}
			else {
				// index hit 0, so this one is requested.
				return opInv;
			}
		}
		throw new ArrayIndexOutOfBoundsException("index " + index + " was asked from " + getComposition());
	}
	
	/**
	 * Returns true if the index is below currentindexField.
	 */
	public boolean isBelowExecutionBound(int index) {
		return index < getCurrentIndex();
	}
	
	/**
	 * Returns true if the given index is above or equal to maxindexField. 
	 * If maxindexField is set to -1, it will be treated as infinity.
	 */
	public boolean isAboveExecutionBound(int index) {
		if(getMaxIndex() == -1) {
			return false; // always in bound if maxIndex is -1 (infinity).
		}
		else {
			return index >= getMaxIndex();
		}
	}

	/**
	 * Returns the keywork arguments.
	 * @return Inputs
	 */
	public Map<String, JASEDataObject> getKeyworkArgs() {
		return keywordArguments;
	}
	/**
	 * Returns the positional arguments.
	 * @return Inputs
	 */
	public List<JASEDataObject> getPositionalArgs() {
		return positionalArguments;
	}
	
	/**
	 * Writes this instance as a json body to the stream using the jackson library.
	 * @param outStream the output stream 
	 * @param otms The marshaling system used to parse the arguments objects to semantic objects
	 * @throws IOException 
	 */
	private void writeBodyAsJson(OutputStream outStream) throws IOException {
		JsonFactory jfactory = new JsonFactory();
		JsonGenerator jsonOut = jfactory
		  .createGenerator(outStream, JsonEncoding.UTF8);
		jsonOut.writeStartObject(); // {
		// Write composition:
		if(containsComposition()) {
			jsonOut.writeStringField(HttpBody.CHOREOGRAPGY_FIELDNAME, this.getComposition());
		}
		// Write current and max index:
		jsonOut.writeNumberField(HttpBody.CURRENTINDEX_FIELDNAME, this.getCurrentIndex());
		jsonOut.writeNumberField(HttpBody.MAXINDEX_FIELDNAME, this.getMaxIndex());
		// Write Arguments:
		jsonOut.writeFieldName(HttpBody.INPUTS_FIELDNAME);
		jsonOut.writeStartObject();
		
		// positional arguments
		jsonOut.writeFieldName(HttpBody.ARGLIST_FIELDNAME);
		jsonOut.writeStartArray();
		for(int i = 0, size = getPositionalArgs().size(); i < size; i++) {
			writeObject(jsonOut, getPositionalArgs().get(i));
		}
		jsonOut.writeEndArray();
		
		// keyword arguments
		for(String keyword : getKeyworkArgs().keySet()) {
			JASEDataObject data = getKeyworkArgs().get(keyword);
			jsonOut.writeFieldName(keyword);
			writeObject(jsonOut, data);
		}
		
		//end of arguments
		jsonOut.writeEndObject();
		//end of body
		jsonOut.writeEndObject(); //  }
		jsonOut.flush();
	}
	
	private void writeObject(JsonGenerator jsonOut, JASEDataObject jdo) throws IOException {

		jsonOut.writeStartObject();
		jsonOut.writeStringField("type", jdo.getType());
		jsonOut.writeFieldName("data");
		if(isPrimitive(jdo)) {
			jsonOut.writeObject(jdo.getData());
		}else {
			parseObjectAndWrite(jsonOut, jdo);
		}
		jsonOut.writeEndObject();
	}
	
	private void  parseObjectAndWrite(JsonGenerator jsonOut, JASEDataObject jdo) throws IOException {
		String streamHandlerClassName = "de.upb.crc901.services.streamhandlers." + jdo.getType() + "StreamHandler";
		Class<?> streamHandlerClass;
		try {
			streamHandlerClass = Class.forName(streamHandlerClassName);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Can't find streamhandler for semantic type " + jdo.getType());
		}
		StreamHandler<?> handler;
		try {
			handler = (StreamHandler<?>) streamHandlerClass.getConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			e.printStackTrace();
			throw new RuntimeException(e); // mask exception
		}
		
		Method write = MethodUtils.getMatchingAccessibleMethod(streamHandlerClass, "write", JsonGenerator.class, handler.getSupportedSemanticClass());
		assert write != null : "Could not find method \"write(" + JsonGenerator.class + ", " + handler.getSupportedSemanticClass() + ")\" in streamhandler class " + streamHandlerClassName;
		
		try {
			write.invoke(handler, jsonOut, jdo.getData());
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	/**
	 * Returns true if the given object is primitive.
	 * 
	 */
	public static boolean isPrimitive(Object o) {
		if(o instanceof JASEDataObject) {
			return ((JASEDataObject)o).getType().equals(PRIMITIVE_TYPE);
		}
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
	 * Encodes this instance and writes it chunk wise through the outStream.
	 * @param otms The marshaling system used to parse the arguments objects to semantic objects
	 * @throws IOException 
	 * @throws SecurityException 
	 * @throws NoSuchMethodException 
	 * @throws InstantiationException 
	 * @throws ClassNotFoundException 
	 * @throws InvocationTargetException 
	 * @throws IllegalArgumentException 
	 * @throws IllegalAccessException 
	 */
	public void writeBody(OutputStream outStream) throws IOException {
		writeBodyAsJson(outStream);
	}
	
	
	public void readfromJsonBody(InputStream input) throws IOException {
		JsonFactory jfactory = new JsonFactory();
//		jfactory.setCodec(new ObjectMapper());
		JsonParser jsonIn = jfactory.createParser(input);
		while (jsonIn.nextToken() != JsonToken.END_OBJECT) {
			if(jsonIn.currentToken() == null) {
				// end stream:
				break;
			}
			 String fieldname = jsonIn.getCurrentName();
			 if(HttpBody.CHOREOGRAPGY_FIELDNAME.equals(fieldname)) {
				 jsonIn.nextToken();
				 String composition = jsonIn.getValueAsString();
//				 composition.replaceAll("\\\"", "\"");
				 setComposition(composition);
			 }
			 else if(HttpBody.CURRENTINDEX_FIELDNAME.equals(fieldname)) {
				 jsonIn.nextToken();
				 setCurrentIndex(jsonIn.getIntValue());
			 }
			 else if(HttpBody.MAXINDEX_FIELDNAME.equals(fieldname)) {
				 jsonIn.nextToken();
				 setMaxIndex(jsonIn.getIntValue());
			 }
			 else if(HttpBody.INPUTS_FIELDNAME.equals(fieldname)) {
				 jsonIn.nextToken();
				 // inputs are wrapped in a object:
				 while(jsonIn.nextToken() != JsonToken.END_OBJECT) {
					 fieldname = jsonIn.getCurrentName();
					 if(HttpBody.ARGLIST_FIELDNAME.equals(fieldname)) {
						 jsonIn.nextToken();
						 while(jsonIn.nextToken() != JsonToken.END_ARRAY) {
							 JASEDataObject jdo = readObject(jsonIn);
							 addPositionalArgument(jdo);
						 }
					 } else {
						 String keywrod = fieldname;
						 jsonIn.nextToken();
						 JASEDataObject jdo = readObject(jsonIn);
						 addKeyworkArgument(keywrod, jdo);
					 }
				 }
			 }
			 
		}
	}
	
	private JASEDataObject readObject(JsonParser jsonIn) throws IOException {
		String type = null;
		Object data = null;
		while(jsonIn.nextToken() != JsonToken.END_OBJECT) {
			String fieldname = jsonIn.getCurrentName();
			if("type".equals(fieldname)) {
				jsonIn.nextToken();
				type = jsonIn.getText();
			}
			if("data".equals(fieldname)) {
				if(type!=null) {
					jsonIn.nextToken();
					data = parseData(jsonIn, type); 
				}
				else {
					throw new RuntimeException("The incoming json string doesn't specify type before the data.");
				}
			} 
		}
		JASEDataObject jdo = new JASEDataObject(type, data);
		return jdo;
	}
	
	private Object parseData(JsonParser jsonIn, String type) throws IOException {
		if(HttpBody.PRIMITIVE_TYPE.equals(type)) {
			if(jsonIn.getCurrentToken() == JsonToken.VALUE_NUMBER_INT) {
				return jsonIn.getIntValue();
			}
			else if (jsonIn.getCurrentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
				return jsonIn.getDoubleValue();
			}
			else if (jsonIn.getCurrentToken() == JsonToken.VALUE_STRING) {
				return jsonIn.getValueAsString();
			}
			else {
				throw new RuntimeException("Can't parse this token to primitive type");
			}
		}else {
			try {
				String streamHandlerClassName = "de.upb.crc901.services.streamhandlers." + type + "StreamHandler";
				Class<?> streamHandlerClass = Class.forName(streamHandlerClassName);
				StreamHandler<?> handler = (StreamHandler<?>) streamHandlerClass.getConstructor().newInstance();
				
				Method read = MethodUtils.getMatchingAccessibleMethod(streamHandlerClass, "read", JsonParser.class);
				assert read != null : "Could not find method \"write(" + JsonParser.class + ")\" in streamhandler class " + streamHandlerClassName;
			
				return read.invoke(handler, jsonIn);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				e.printStackTrace();
			} catch (ClassNotFoundException e) {
				e.printStackTrace();
			} catch (InstantiationException | NoSuchMethodException | SecurityException e) {
				e.printStackTrace();
			}
			return null;
		}
	}
	
	public void readfromBody(InputStream input) throws IOException {
		readfromJsonBody(input);
	}
	public boolean containsComposition() {
		return getComposition() != null;
	}
	
	public boolean equals(Object object) {
		if ( object instanceof HttpBody) {
			return equals((HttpBody) object);
		}
		return false;
	}
	
	public boolean equals(HttpBody otherBody) {
		if(!getComposition().equals(otherBody.getComposition())) {
			return false;
		}
		if(getCurrentIndex() != otherBody.getCurrentIndex()) {
			return false;
		}
		if(getMaxIndex() != otherBody.getMaxIndex()) {
			return false;
		}
		if(!getPositionalArgs().equals(otherBody.getPositionalArgs())){
			return false;
		}
		if(!getKeyworkArgs().equals(otherBody.getKeyworkArgs())){
			return false;
		}
		return true;
	}
	
	
	
	
}
