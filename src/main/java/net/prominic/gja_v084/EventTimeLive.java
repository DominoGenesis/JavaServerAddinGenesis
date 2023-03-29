package net.prominic.gja_v084;

import java.io.File;

import net.prominic.util.FileUtils;

public class EventTimeLive extends Event {
	public String FilePath = null;
	
	public EventTimeLive(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		if (FilePath == null) return;

		File f = new File(FilePath);
		long currentTime = System.currentTimeMillis();
		FileUtils.writeFile(f, String.valueOf(currentTime));
	}
}
