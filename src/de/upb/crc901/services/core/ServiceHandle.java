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
	
	/* default values */
	private static final String OWN_HOST = "local";
	
	private static final Object service_placeholder = new Object();
	
	private static final String not_serialized_id = "NULL";	
	
	private static final String not_specified_classpath = "NO_CLASS_SPECIFIED";


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
	 * Empty constructor to create service handle with default values.
	 */
	public ServiceHandle() {
		this(OWN_HOST, not_specified_classpath, not_serialized_id, service_placeholder);
	}
	
	/**
	 * Returns the id of the service. Throws Runtime-Exception if wasSerialized() returns false.
	 */
	public String getId() {
//		assert(isSerialized()); // needs to be serialized before accessing it's id.
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
		return !id.equals(not_serialized_id);
	}
	
	public boolean isClassPathSpecified() {
		return !classpath.equals(not_specified_classpath);
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

	public ServiceHandle withLocalHost() {
		return withExternalHost(OWN_HOST);
	}
	/**
	 * Returns a copy of this object with the given host.
	 */
	public ServiceHandle withClassPath(String classPath) {
		Objects.requireNonNull(classPath);
		if(classPath.equals(getClasspath())) {
			// otherhost changes nothing
			return this;
		}
		return new ServiceHandle(getHost(), classPath, getId(), getService());
	}
	public ServiceHandle withService(Object service) {
		Objects.requireNonNull(service);
		return new ServiceHandle(getHost(), getClasspath(), getId(), service);
	}

	public ServiceHandle withId(String id2) {
		Objects.requireNonNull(id2);
		return new ServiceHandle(getHost(), getClasspath(), id2, getService());
	}

	/**
	 * Returns a copy of this object with empty id.
	 */
	public ServiceHandle unsuccessedSerialize() {
		return new ServiceHandle(getHost(), getClasspath(), not_serialized_id, getService());
	}
	public boolean containService() {
		return service_placeholder != getService();
	}
}