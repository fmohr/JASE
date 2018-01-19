package de.upb.crc901.services.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;

/**
 * A data structure class, which encodes or decodes Post body data. 
 * This is used by HttpServiceClient, whose data is encoded to the post's body content, 
 * and it is also used by HttpServiceServer, who initializes an instance by the encoded post body it receives.
 * (Note: This class was created to take care of logic that was implemented in the client and server class before.)
 * 
 * @author aminfaez
 *
 */
@SuppressWarnings("restriction")
public final class HttpBody {
	
	
	
	/**
	 * Constant strings used in communication.
	 */
	public final static String 	coreography = "coreography",
								currentindex = "currentindex",
								inputs = "inputs";
	
	/**
	 * Encoding used in http's body.
	 */
	public final static String 	encoding = "utf-8";
	
	private final Map<String, Object> params;
	public final Map<String, Object> state;
	
	private HttpBody(Map<String, Object> params, Map<String, Object> state) {
		this.state = state;
		this.params = params;
	}
	
	/**
	 * Looks in the params map for a coreography entry.
	 * @return true, if there is a coreography entry.
	 */
	public boolean containsCoreography() {
		return params.containsKey(HttpBody.coreography);
	}
	/**
	 * Returns the coreography value from the params map.
	 * @return coreography string.
	 */
	public String getCoreographyString() {
		if(containsCoreography()) {
			return params.get(HttpBody.coreography).toString();
		}
		else {
			return "";
		}
	}
	/**
	 * Current Index. 
	 * @return the current index value from the map if it is present and is Integerparsable. Else 0 is returned.
	 */
	public int getCurrentIndex() {
		int index = 0;
		if(params.containsKey(HttpBody.currentindex)) {
			String indexString = params.get(HttpBody.currentindex).toString();
			try {
				index = Integer.parseInt(indexString);
			} catch(NumberFormatException nfe) {
				nfe.printStackTrace();
			}
		}
		return index;
	}
	
	/**
	 * Decodes Body and creates HttpBody.
	 * @param exchange The http exchange object
	 * @param otms The Marshalling System. It's only use is to look up if a type is known (see jsonDeserialiseInputs method)
	 * @return HttpBody Struct
	 * @throws IOException thrown if there are problems regarding json parsing or reading from the input stream.
	 */
	public static HttpBody decode(HttpExchange exchange, OntologicalTypeMarshallingSystem otms) throws IOException{
		// Parse Http body
		String decodedBody = readBody(exchange);
		Map<String, Object> params = parseBodyIntoMap(decodedBody);
		Map<String, Object> inputs = jsonDeserialiseInputs(params, otms);
		return new HttpBody(params, inputs);
	}
	/**
	 * Reads Post's Body from HttpExchange's input stream. 
	 * @param exchange received post request.
	 * @return string content of the body.
	 * @throws IOException thrown by opening and reading the exchange's InputStream returned by getRequestBody(). 
	 */
	private static String readBody(HttpExchange exchange) throws IOException{
		if ((!"post".equalsIgnoreCase(exchange.getRequestMethod()))) {
			throw new UnsupportedEncodingException("No post request");
		}
		BufferedReader br = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), HttpBody.encoding));
		String query = br.readLine();
		br.close();
		return query;
	}
	/**
	 * Parses the body and puts the parameters into the map.
	 * 
	 * @param decodedBody decoded string from the http body.
	 * @param consumer in this case the Map::put function.
	 */
	private static Map<String, Object> parseBodyIntoMap(String decodedBody ) {
		HashMap<String, Object> parameters = new HashMap<>(); // This map will be populated and returned.
		if(decodedBody == null) {
			// Return empty
			return parameters;
		}
		// Split by params
		// e.g.: decodedBody = "a=1&b=helloworld"
		String pairs[] = decodedBody.split("[&]");
		for (String pair : pairs) {
			String param[] = pair.split("[=]", 2); // split only returns on array of size two: "A=B=C" -> ["A", "B=C"]
			String key = null;
			String value = null;
			if(param.length == 0) {
				// Empty paramter. e.g. : decodedBody = "...&&..."
				// continue with the next pair of parameter.
				// TODO before if there was an empty pair, it would put null, null into the map. 
				continue; 
			}
			if (param.length > 0) {
				key = param[0];
			}
			if (param.length > 1) {
				value = param[1];
			}
			// e.g. : pair = "a=1" -> key = "a", value = "1"
			
			// Now if the body maps multiple entries to a parameter key, 
			// then sum them all up in a list and map to the summed list:
			if (parameters.containsKey(key)) {
				Object obj = parameters.get(key);
				if (obj instanceof String) {
					// This is the first time a duplicate key is found -> create a list of their values.
					List<String> values = new ArrayList<String>();
					values.add((String) obj);
					values.add(value);
					parameters.put(key, values);
				} else if (obj instanceof List<?>) {
					// A list was already added the last time a duplicate key was found -> add the value to the list.
					@SuppressWarnings("unchecked")
					List<String> values = (List<String>) obj;
					values.add(value);
				}
			} else {
				// This is the first time a key was found. Add it to the list.
				parameters.put(key, value);
			}
		}
		return parameters;
	}
	/**
	 * Deserialises every key of the given map to a Json Object and maps all inputs using their given index.
	 * @param params Map of body parameters. This map, maps only to String or List<String>.
	 * @return mapping from index of input to json-object-representation of the input.
	 * @throws IOException Thrown by the json parser.
	 */
	private static Map<String, Object> jsonDeserialiseInputs(Map<String, Object> params, 
			OntologicalTypeMarshallingSystem otms) throws IOException {
		Map<String, Object> inputs = new HashMap<>();
		for (String inputName : params.keySet()) {
			if (!inputName.startsWith(HttpBody.inputs)) {
				// Continue to the next parameterKey if the parameter is not a input.
				continue;
			}
			// Indexed are noted in brackets.
			// Extract index from brackets. e.g.: inputName =  "inputs[i1]" -> index = "i1"
			String index = StringUtils.substringBetween(inputName, "[", "]"); 
			String inputStringValue = params.get(inputName).toString();
			JsonNode inputObject = new ObjectMapper().readTree(inputStringValue);
			// Input needs to have type field.
			if (!inputObject.has("type"))
				throw new IllegalArgumentException("Input " + index + " has no type attribute!");
			// And the given type has to be recognized.
			if (!otms.isKnownType(inputObject.get("type").asText()))
				throw new IllegalArgumentException("Ontological type of of input " + index + " is not known to the system!");
			inputs.put(index, inputObject);
		}
		return inputs;
	}
}
