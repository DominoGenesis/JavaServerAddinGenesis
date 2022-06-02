package net.prominic.gja_v20220602;

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
			InputStream input = new FileInputStream(filePath);
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
			OutputStream output = new FileOutputStream(filePath);
			Properties prop = new Properties();
			prop.setProperty(name, value);
			prop.store(output, null);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
