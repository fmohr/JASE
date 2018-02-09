package de.upb.crc901.services.core;

public final class ServiceHandle {
	/**
	 * 
	 */
	private final String id;
	private final Object service;

	/**
	 * Standard constructor
	 * @param id Id which the service can be accessed through
	 * @param service inner service
	 */
	ServiceHandle( String id, Object service) {
		super();
		this.id = id;
		this.service = service;
	}
	
	/**
	 * Constructor which is used to indicate that the serialization has failed.
	 * @param service inner service
	 * @param httpServiceServer TODO
	 */
	public ServiceHandle(Object service) {
		super();
		this.id = null;
		this.service = service; 
	}
	
	ServiceHandle(String id) {
		this.id = id;
		this.service = null;
	}
	
	/**
	 * If id is set to null, the service couldn't be serialized (see the invokeOperation method)
	 * This the server shouldn't return this servicehandle to the client. (see 
	 * 
	 * @return True if the inner service was serialized to disk.
	 */
	public boolean wasSerialized() {
		return id!=null;
	}

	/**
	 * Returns the id of the service. Throws Runtime-Exception if wasSerialized() returns false.
	 */
	public String getId() {
		if(wasSerialized()) {
			return id;
		}
		else {
			// this service wasn't serialized. so the id shouldn't be accessed.
			throw new RuntimeException("The service wasn't serialized. Can't access the id.");
		}
	}

	public Object getService() {
		return service;
	}
}