package net.prominic.gja_v20220427;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class GLogger {
	public static int DEBUG = 0;
	public static int INFO = 1;
	public static int WARNING = 2;
	public static int SEVERE = 3;
	
	long ONE_MB = 1048576;
	int m_level = INFO;
	
	SimpleDateFormat m_formatter = new SimpleDateFormat("MM/dd/yyyy, HH:mm:ss");
	String m_directory = "";

	public GLogger() {
	}
	
	public GLogger(String directory) {
		setDirectory(directory);
	}
	
	public void setLevel(int level) {
		m_level = level;
	}

	public int getLevel() {
		return m_level;
	}

	private void setDirectory(String directory) {
		File dir = new File(directory);
		if (!dir.exists()){
			dir.mkdirs();
		}

		this.m_directory = directory;
	}

	public String getDirectory() {
		return m_directory;
	}

	public String getLevelLabel() {
		if (m_level == DEBUG) return "debug";
		if (m_level == INFO) return "info";
		if (m_level == WARNING) return "warning";
		if (m_level == SEVERE) return "severe";
		return "off";
	}

	private void writeToFile(String message, Throwable thrown, int level, String c) {
		if (level < getLevel()) return;

		try {
			SimpleDateFormat formatterFileName = new SimpleDateFormat("yyyy-MM");
			String fileName = "log-" + formatterFileName.format(new Date()) + ".log";

			File f = new File(getDirectory(), fileName);

			FileWriter fw;
			if (f.exists() && f.length() > 5 * ONE_MB) {
				fw = new FileWriter(f);
			}
			else {
				fw = new FileWriter(f, true);
			}

			PrintWriter out = new PrintWriter(new BufferedWriter(fw));

			String logLine = c + m_formatter.format(new Date()) + " " + message;
			out.println(logLine);

			if (thrown != null) {
				thrown.printStackTrace(out);
			}

			out.close();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void debug(String message) {
		writeToFile(message, null, DEBUG, "@");
	}

	public void info(String message) {
		writeToFile(message, null, INFO, " ");
	}

	public void warning(String message) {
		writeToFile(message, null, WARNING, "#");
	}
	
	public void warning(Exception e) {
		String message = e.getLocalizedMessage();
		if (message == null || message.isEmpty()) {
			message = "an undefined exception was thrown";
		}
		writeToFile(message, e, WARNING, "#");
	}
	
	public void severe(String message) {
		writeToFile(message, null, 2, "!");
	}

	public void severe(Exception e) {
		String message = e.getLocalizedMessage();
		if (message == null || message.isEmpty()) {
			message = "an undefined exception was thrown";
		}
		writeToFile(message, e, 2, "!");
	}

}
