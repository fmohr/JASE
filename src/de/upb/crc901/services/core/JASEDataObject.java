package de.upb.crc901.services.core;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

public class JASEDataObject {
	private final String type;
	private final Object object;

	public JASEDataObject(String type, Object object) {
		super();
		this.type = type;
		this.object = object;
	}

	public String getType() {
		return type;
	}
	
	public Object getData() {
		return object;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((object == null) ? 0 : object.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		JASEDataObject other = (JASEDataObject) obj;
		if (object == null) {
			if (other.object != null)
				return false;
		} else if (!object.equals(other.object))
			return false;
		if (type == null) {
			if (other.type != null)
				return false;
		} else if (!type.equals(other.type))
			return false;
		return true;
	}

	public String toString() {
		return type + ": " + object.toString();
	}
	

//	public static final JASEDataObject FROM_JSON(JsonNode json) {
//		JsonNode object = json.get("data");
//		String type = json.get("type").asText();
//		return new JASEDataObject(type, object);
//	}

}
