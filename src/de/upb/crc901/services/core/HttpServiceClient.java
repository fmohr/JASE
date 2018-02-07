/**
 * HttpServiceClient.java
 * Copyright (C) 2017 Paderborn University, Germany
 * 
 * @author: Felix Mohr (mail@felixmohr.de)
 */

/*
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.upb.crc901.services.core;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.math.NumberUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.upb.crc901.configurationsetting.compositiondomain.CompositionDomain;
import de.upb.crc901.configurationsetting.logic.LiteralParam;
import de.upb.crc901.configurationsetting.operation.OperationInvocation;
import de.upb.crc901.configurationsetting.operation.SequentialComposition;
import de.upb.crc901.configurationsetting.serialization.SequentialCompositionSerializer;

public class HttpServiceClient {

	private final OntologicalTypeMarshallingSystem otms;

	public HttpServiceClient(OntologicalTypeMarshallingSystem otms) {
		super();
		this.otms = otms;
	}

	public ServiceCompositionResult callServiceOperation(String serviceCall, Object... inputs) throws IOException {
		return callServiceOperation(ServiceUtil.getOperationInvocation(serviceCall, inputs), new SequentialComposition(new CompositionDomain()), inputs);
	}

	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography) throws IOException {
		return callServiceOperation(call, coreography, new HashMap<>());
	}

	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography, Object... additionalInputs) throws IOException {
		return callServiceOperation(call, coreography, ServiceUtil.getObjectInputMap(additionalInputs));
	}

	public ServiceCompositionResult callServiceOperation(OperationInvocation call, SequentialComposition coreography, Map<String, Object> additionalInputs) throws IOException {

		/* separate service and operation from name */
		String opFQName = call.getOperation().getName();
		
		// split the opFQName into service (classpath or objectname) and operation name (function name).
		String[] hostservice_OpTupel = opFQName.split("::", 2);
		String[] host_serviceTupel = hostservice_OpTupel[0].split("/",2);
		String host = host_serviceTupel[0]; // hostname e.g.: 'localhost:5000'
		String service = host_serviceTupel[1]; // service name e.g.: 'packagePath.Constructor'
		// If no '::' is given, assume its a '__construct' call.
		String opName = hostservice_OpTupel.length>1 ? hostservice_OpTupel[1] : "__construct";

		/* prepare data */
		// TODO coreography should have a indexof method
		int index = 0;
		for (OperationInvocation opInv : coreography) {
			if (opInv.equals(call))
				break;
			index++;
		}
		// get the coreography string
		String serializedCoreography = null;
		if (coreography.iterator().hasNext()) { // if coreography is not empty // TODO coreography::isEmpty method
			serializedCoreography = new SequentialCompositionSerializer().serializeComposition(coreography);
		}
		// create body, encode it and write it to the outputstream.
		HttpBody body = new HttpBody(additionalInputs, serializedCoreography, index, -1);
		String encoding = body.encode(otms);

		/* setup connection */
		URL url = new URL("http://" + host + "/" + service + "/" + opName);
		if(serializedCoreography != null) {
			// If it's a choreography, use choreography specific url
			url = new URL("http://" + host + "/" + "choreography");
		}
		HttpURLConnection con = (HttpURLConnection) url.openConnection();
		con.setRequestMethod("POST");
		con.setDoOutput(true);
		
		/* send data */
		DataOutputStream wr = new DataOutputStream(con.getOutputStream());
		wr.writeBytes(encoding);
		wr.flush();
		wr.close();

		/* read and return answer */
		StringBuilder content = new StringBuilder();
		try (InputStream in = con.getInputStream()){
		
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String curline;
			while ((curline = br.readLine()) != null) {
				content.append(curline + '\n');
			}
			br.close();
			con.disconnect();
		} catch(IOException serverError) {
			try (InputStream in = con.getErrorStream()){
				
				BufferedReader br = new BufferedReader(new InputStreamReader(in));
				String curline;
				while ((curline = br.readLine()) != null) {
					content.append(curline + '\n');
				}
				br.close();
				con.disconnect();
				System.out.println("");
			} catch(IOException anotherError) {
				anotherError.printStackTrace();
			}
			throw new RuntimeException("Server returned error code " + con.getResponseCode() + ". Message: \n" + content.toString());
		}
		JsonNode root = new ObjectMapper().readTree(content.toString());
		if(root == null) {
			throw new RuntimeException("Error occured parsing " + content.toString());
		}
		ServiceCompositionResult result = new ServiceCompositionResult();
		Iterator<String> it = root.fieldNames();
		while (it.hasNext()) {
			String field = it.next();
			JsonNode object = root.get(field);
			result.put(field, object);
		}
		return result;
	}

	public ServiceCompositionResult invokeServiceComposition(SequentialComposition composition, Map<String, Object> inputs) throws IOException {

		/* check first that all inputs are given */
		Collection<String> availableInputs = new HashSet<>(inputs.keySet());
		for (OperationInvocation opInv : composition) {
			for (LiteralParam l : opInv.getInputMapping().values()) {
				boolean isNumber = NumberUtils.isNumber(l.getName());
				boolean isString = !isNumber && l.getName().startsWith("\"") && l.getName().endsWith("\"");
				if (!availableInputs.contains(l.getName()) && !isNumber && !isString)
					throw new IllegalArgumentException("Parameter " + l.getName() + " required for " + opInv + " is missing in the invocation.");
			}
			availableInputs.addAll(opInv.getOutputMapping().values().stream().map(p -> p.getName()).collect(Collectors.toList()));
		}

		/* get fist service call and invoke it with the rest of the composition as a choreography */
		OperationInvocation first = composition.iterator().next();
		return callServiceOperation(first, composition, inputs);
	}

	public ServiceCompositionResult invokeServiceComposition(SequentialComposition composition, Object... additionalInputs) throws IOException {
		return invokeServiceComposition(composition, ServiceUtil.getObjectInputMap(additionalInputs));
	}
}
