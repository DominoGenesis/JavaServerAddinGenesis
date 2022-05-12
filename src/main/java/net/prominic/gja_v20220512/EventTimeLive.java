package net.prominic.gja_v20220512;

import java.io.File;

import net.prominic.util.FileUtils;

public class EventTimeLive extends Event {

	public EventTimeLive(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		String filePath = (String) getParam("filePath");
		if (filePath == null) return;

		File f = new File(filePath);
		if (!f.exists()) return;
		
		long currentTime = System.currentTimeMillis();
		FileUtils.writeFile(f, String.valueOf(currentTime));
	}
}
