package net.prominic.gja_v083;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class GConfig {
	public static String get(String filePath, String name) {
		try {
			File file = new File(filePath);
			if (!file.exists()) return null;
			
			InputStream input = new FileInputStream(file);
			Properties prop = new Properties();
			prop.load(input);
			return prop.getProperty(name);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	public static void set(String filePath, String name, String value) {
		try {
			Properties prop = new Properties();

			File file = new File(filePath);
			if (file.exists()) {
				FileInputStream fis = new FileInputStream(file);
				prop.load(fis);
			}

	        // add/replace value
			prop.setProperty(name, value);
			
			OutputStream output = new FileOutputStream(filePath);
			prop.store(output, null);
			output.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
