package de.upb.crc901.services.core;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ServiceManager {
	
	private final static ServiceManager singlton = new ServiceManager();
	
	private final static int maxChanceCachedService = 20; 
	
	private static List<ServiceData> cachedServices = new ArrayList<>();
	
	class ServiceData {
		final ServiceHandle handler;
		private int chance;
		public ServiceData(ServiceHandle h) {
			this.handler = h;
			resetChance();
		}
		public void resetChance() {
			chance = 0;
		}
		public void addChance() {
			chance++;
		}
		public boolean hasChanceLeft() {
			return chance < maxChanceCachedService;
		}
	}
	
	private void maintainCache() {
		Iterator<ServiceData> iterator = cachedServices.iterator();
		while(iterator.hasNext()) {
			ServiceData cachedService = iterator.next();
			if(cachedService.hasChanceLeft()) {
				cachedService.addChance();
			} else {
				iterator.remove();
			}
		}
	}
	
	public synchronized void addService(ServiceHandle handle) {
		maintainCache();
		// Delete already already cached services
		Iterator<ServiceData> iterator = cachedServices.iterator();
		while(iterator.hasNext()) {
			ServiceData cachedService = iterator.next();
			if(cachedService.handler.getId().equals(handle.getId())) {
				// same id. remove cached service:
				iterator.remove();
			}
		}
		ServiceData newCachedService = new ServiceData(handle);
		cachedServices.add(newCachedService);
	}
	
	public synchronized ServiceHandle getHandle(String id_) {
		for(ServiceData cachedService : cachedServices) {
			if(cachedService.handler.getId().equals(id_)) {
				cachedService.resetChance();
				return cachedService.handler;
			}
		}
		throw new RuntimeException("This service is not cached anymore: " + id_);
	}
	
	
	public static ServiceManager SINGLETON() {
		return singlton;
	}
	
}
