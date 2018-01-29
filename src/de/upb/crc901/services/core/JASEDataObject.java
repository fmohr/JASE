package de.upb.crc901.services.core;

import com.fasterxml.jackson.databind.JsonNode;

public class JASEDataObject {
	private final String type;
	private final JsonNode object;

	public JASEDataObject(String type, JsonNode object) {
		super();
		this.type = type;
		this.object = object;
	}

	public String getType() {
		return type;
	}

	public JsonNode getObject() {
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

}
