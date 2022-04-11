package net.prominic.gja_v20220411;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class GLogger {
	public static void log(String message, Level level) {
		Logger logger = Logger.getLogger("GLog");
		FileHandler fh;

		try {
			// This block configure the logger with handler and formatter
			fh = new FileHandler("activity.log");
			logger.addHandler(fh);
			SimpleFormatter formatter = new SimpleFormatter();
			fh.setFormatter(formatter);

			// the following statement is used to log any messages
			if (level == Level.INFO) {
				logger.info(message); 
			}
			else if(level == Level.WARNING) {
				logger.warning(message);
			}
			else if(level == Level.SEVERE) {
				logger.severe(message);
			}
		} catch (SecurityException e) {  
			e.printStackTrace();  
		} catch (IOException e) {  
			e.printStackTrace();  
		}  
	}

	public static void logInfo(String message) {
		log(message, Level.INFO);
	}

	public static void logWarning(String message) {
		log(message, Level.WARNING);
	}
	
	public static void logSevere(String message) {
		log(message, Level.SEVERE);
	}
	
	public static void logSevere(Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);

		log(sw.toString(), Level.SEVERE);
	}
}
