package net.prominic.gja_v20220502;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;

public class EventTimeLive extends Event {

	public EventTimeLive(String name, long seconds, boolean fireOnStart, HashMap<String, Object> params, GLogger logger) {
		super(name, seconds, fireOnStart, params, logger);
	}

	@Override
	public void run() {
		if (!getParams().containsKey("filePath")) return;

		String filePath = (String) getParams().get("filePath");
		File f = new File(filePath);
		long currentTime = System.currentTimeMillis();
		writeFile(f, String.valueOf(currentTime));
	}

	private void writeFile(File file, String cmd) {
		try {
			PrintWriter writer = new PrintWriter(file, "UTF-8");
			writer.println(cmd);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
}
