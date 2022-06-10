package net.prominic.gja_v081;

import java.util.Date;

public abstract class Event {
	private String 					m_name;
	private Date					m_start;			// date time of start
	private GLogger					m_logger;
	private long 					m_intervalSeconds;	// seconds
	private boolean					m_fireOnStart;
	
	public Event(String name, long seconds, boolean fireOnStart, GLogger logger) { 
		m_name = name;
		m_intervalSeconds = seconds;
		m_fireOnStart = fireOnStart;
		m_logger = logger;
	}
	
	public abstract void run();
	
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
