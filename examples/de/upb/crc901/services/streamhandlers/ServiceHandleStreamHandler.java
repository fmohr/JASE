package de.upb.crc901.services.streamhandlers;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import de.upb.crc901.services.core.ServiceHandle;
import de.upb.crc901.services.core.StreamHandler;

public class ServiceHandleStreamHandler implements StreamHandler<ServiceHandle> {

	@Override
	public ServiceHandle read(JsonParser jsonIn) throws IOException {
		JsonToken t = jsonIn.nextToken();
		String fieldname = jsonIn.getCurrentName();
		assert t == JsonToken.FIELD_NAME && fieldname.equals("id");
		t = jsonIn.nextToken();
		assert t == JsonToken.VALUE_STRING;
		String id = jsonIn.getValueAsString();
		return new ServiceHandle(id);
		
		
	}

	@Override
	public void write(JsonGenerator jsonOut, ServiceHandle data) throws IOException {
		jsonOut.writeStringField("id", data.getId());
	}

	@Override
	public Class<ServiceHandle> getSupportedSemanticClass() {
		return ServiceHandle.class;
	}

}
