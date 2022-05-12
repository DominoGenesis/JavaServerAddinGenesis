package net.prominic.gja_v20220512;

import java.util.Date;
import java.util.HashMap;

public abstract class Event {
	private String 					m_name;
	private Date					m_start;			// date time of start
	private GLogger					m_logger;
	private long 					m_intervalSeconds;	// seconds
	private boolean					m_fireOnStart;
	private HashMap<String, Object>	m_params = null;
	
	public Event(String name, long seconds, boolean fireOnStart, GLogger logger) { 
		m_name = name;
		m_intervalSeconds = seconds;
		m_fireOnStart = fireOnStart;
		m_logger = logger;
	}
	
	public abstract void run();
	
	public void addParam(String name, Object obj) {
		if (m_params == null) {
			m_params = new HashMap<String, Object>();
		}
		m_params.put(name, obj);
	}

	public Object getParam(String name) {
		if (m_params == null || !m_params.containsKey(name)) return null;
		return m_params.containsKey(name);
	}
	
	public String getName() {
		return m_name;
	}
	
	public GLogger getLogger() {
		return m_logger;
	}
	
	public void start() {
		m_start = new Date();
	}
	
	public boolean fireOnStart() {
		return m_fireOnStart;
	}
	
	public boolean fire() {
		Date now = new Date();
		long seconds = (now.getTime()-m_start.getTime())/1000;
		return seconds > m_intervalSeconds;
	}	
}
