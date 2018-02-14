package de.upb.crc901.services.core;

import java.util.Objects;

public final class ServiceHandle {
	/**
	 * 
	 */
	private final String host;
	private final String classpath;
	private final String id;
	private final Object service;
	
	private static final String OWN_HOST = "local";
	
	private static final Object service_placeholder = new Object();
	
	private static final String not_serialized_id = "NULL";


	/**
	 * Standard constructor
	 * @param id Id which the service can be accessed through
	 * @param service inner service
	 */
	public ServiceHandle(String host, String classpath, String id, Object service) {
		super();
		this.host =  Objects.requireNonNull(host);
		this.classpath = Objects.requireNonNull(classpath);
		this.id =  Objects.requireNonNull(id);
		this.service = Objects.requireNonNull(service);
	}
	/**
	 * Contructor for local host.
	 */
	public ServiceHandle(String classpath, String id, Object service) {
		this(OWN_HOST, classpath, id, service);
	}
	
	/**
	 * Constructor for external host.
	 */
	public ServiceHandle(String classpath, String id) {
		this(classpath, id, service_placeholder);
	}
	
	/**
	 * Returns the id of the service. Throws Runtime-Exception if wasSerialized() returns false.
	 */
	public String getId() {
		assert(isSerialized()); // needs to be serialized before accessing it's id.
		return id;
	}

	/**
	 * 
	 * @return The actual service object.
	 */
	public Object getService() {
		return service;
	}
	
	/**
	 * 
	 * @return Host of this service.
	 */
	public String getHost() {
		return host;
	}
	
	public String getClasspath() {
		return classpath;
	}
	
	/**
	 * @return True if the service is only remotely accessible.
	 */
	public boolean isRemote() {
		return !OWN_HOST.equals(getHost());
	}
	
	/**
	 * 
	 * @return The address under which the service is accessible.
	 */
	public String getServiceAddress() {
		assert (isRemote()); // No need to access address if you are the host.
		return getHost() + "/" + getClasspath() + "/" + getId() ;
	}
	
	public boolean isSerialized() {
		return id != not_serialized_id;
	}
	
	/**
	 * Returns a copy of this object with the given host.
	 */
	public ServiceHandle withExternalHost(String otherHost) {
		Objects.requireNonNull(otherHost);
		if(otherHost.equals(getHost())) {
			// otherhost changes nothing
			return this;
		}
		return new ServiceHandle(otherHost, getClasspath(), getId(), getService());
	}
	/**
	 * Returns a copy of this object with empty id.
	 */
	public ServiceHandle unsuccessedSerialize() {
		return new ServiceHandle(getHost(), getClasspath(), not_serialized_id, getService());
	}
}