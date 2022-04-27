package net.prominic.gja_v20220427;

import java.util.Date;

public class Event {
	private String 	m_name;
	private Date	m_start;			// date time of start
	private long 	m_intervalSeconds;	// seconds
	private boolean	m_fireOnStart;
	
	public Event(String name, long seconds, boolean fireOnStart) { 
		m_name = name;
		m_intervalSeconds = seconds;
		m_fireOnStart = fireOnStart;
	}
	
	public String getName() {
		return m_name;
	}
	
	public void start() {
		m_start = new Date();
	}
	
	public boolean fireOnStart() {
		return m_fireOnStart;
	}
	
	public boolean fire() {
		Date now = new Date();
		
		System.out.println(now);
		System.out.println(m_start);
		
		long seconds = (now.getTime()-m_start.getTime())/1000;
		return seconds > m_intervalSeconds;
	}
	
}
