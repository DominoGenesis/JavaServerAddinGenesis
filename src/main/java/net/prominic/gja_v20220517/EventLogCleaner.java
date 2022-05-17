package net.prominic.gja_v20220517;
import java.io.File;
import java.util.HashMap;

import net.prominic.util.FileUtils;

public class EventLogCleaner extends Event {
	public EventLogCleaner(String name, long seconds, boolean fireOnStart, GLogger logger) {
		super(name, seconds, fireOnStart, logger);
	}

	@Override
	public void run() {
		cleanOutdatedFiles(".log");
	}

	/*
	 * Clean old log files
	 */
	public void cleanOutdatedFiles(String ext) {
		try {
			File dir = new File(this.getLogger().getDirectory());
			if (!dir.isDirectory()) return;

			File files[] = FileUtils.endsWith(dir, ext);
			if (files.length <= 5) return;

			int count = 0;
			StringBuffer deletedFiles = new StringBuffer();
			files = FileUtils.sortFilesByModified(files, false);
			for (int i = 5; i < files.length; i++) {
				File file = files[i];
				file.delete();
				if (count > 0) deletedFiles.append(", ");
				deletedFiles.append(file.getName());
				count++;
			}

			if (count>0) {
				getLogger().info("Removed files (" + Integer.toString(count) + "): " + deletedFiles.toString());
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
