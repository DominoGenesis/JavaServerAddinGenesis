package net.prominic.gja_v20220323;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.DocumentCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class JSONRules {
	private Session m_session;

	public JSONRules(Session session) {
		m_session = session;
	}

	public void execute(String json) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(json);
			execute(jsonObject);
		} catch (ParseException e) {
			e.printStackTrace();
		}
	}

	public void execute(Reader reader) {
		JSONParser parser = new JSONParser();
		try {
			JSONObject jsonObject = (JSONObject) parser.parse(reader);
			execute(jsonObject);
		} catch (IOException | ParseException e) {
			e.printStackTrace();
		}
	}

	public void execute(JSONObject jsonObject) {

		// if error
		if (jsonObject.containsKey("error")) {
			String error = (String) jsonObject.get("error");	
			log(error);
			return;
		}
		
		JSONArray files = (JSONArray) jsonObject.get("files");
		this.parseFiles(files);

		JSONObject appConfiguration = (JSONObject) jsonObject.get("appConfiguration");
		this.parseAppConfiguration(appConfiguration);

		JSONObject success = (JSONObject) jsonObject.get("success");
		this.parseSuccess(success);
	}

	private void parseSuccess(JSONObject success) {
		if (success == null) return;
		
		JSONArray messages = (JSONArray) success.get("messages");
		if (messages == null) return;
		
		for(int i=0; i<messages.size(); i++) {
			String message = (String) messages.get(i);
			log(message);
		}
	}

	private void parseFiles(JSONArray files) {
		if (files == null) return;

		String directory;
		try {
			directory = this.getNotesINI("Directory");
			log("directory = " + directory);

			for(int i=0; i<files.size(); i++) {
				JSONObject obj = (JSONObject) files.get(i);

				String from = (String) obj.get("from");
				String to = String.valueOf(obj.get("to"));

				if (to.indexOf("${directory}")>=0) {
					to = to.replace("${directory}", directory);
				};
				
				log("file will be downloaded:");
				log("from = " + from);
				log("to = " + to);
				
				String toPath = to.substring(0, to.lastIndexOf("/"));
				Path path = Paths.get(toPath);
				if (!Files.exists(path)) {
				    Files.createDirectories(path);
					log(toPath + " - created");
				}
			    
				HTTP.saveURLTo(from, to);
				
				log("> done");
			}
		} catch (NotesException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void parseAppConfiguration(JSONObject appConfiguration) {
		if (appConfiguration == null) return;

		// notes.ini
		JSONArray notesINI = (JSONArray) appConfiguration.get("notesINI");
		parseNotesINI(notesINI);

		JSONArray databases = (JSONArray) appConfiguration.get("databases");		
		parseDatabases(databases);
	}

	private void parseDatabases(JSONArray array) {
		if (array == null) return;

		for(int i=0; i<array.size(); i++) {
			JSONObject database = (JSONObject) array.get(i);
			parseDatabase(database);
		}
	}

	private void parseDatabase(JSONObject json) {
		try {
			Database database = null;
			String action = (String) json.get("action");
			String filePath = (String) json.get("filePath");
			boolean sign = json.containsKey("sign") && (boolean) json.get("sign");
			
			log("database=" + filePath);
			
			if ("create".equalsIgnoreCase(action)) {
				String title = (String) json.get("title");
				String templatePath = (String) json.get("templatePath");
				database = createDatabaseFromTemplate(filePath, title, templatePath);
			}
			else {
				database = m_session.getDatabase(null, filePath);
			}

			if (database == null) {
				log("Database not found: " + filePath);
				return;
			};

			if (sign) {
				DominoUtils.sign(database);
			}
			
			JSONArray documents = (JSONArray) json.get("documents");
			parseDocuments(database, documents);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void parseDocuments(Database database, JSONArray array) {
		if (array == null) return;

		for(int i=0; i<array.size(); i++) {
			JSONObject doc = (JSONObject) array.get(i);

			String action = (String) doc.get("action");
			boolean computeWithForm = doc.containsKey("computeWithForm") && (boolean) doc.get("computeWithForm");
			if ("create".equalsIgnoreCase(action)) {
				createDocuments(database, doc, computeWithForm);
			}
			else {
				updateDocuments(database, doc, computeWithForm);
			}
		}
	}

	private void createDocuments(Database database, JSONObject json, boolean computeWithForm) {
		log("- create document(s)");
		JSONObject items = (JSONObject) json.get("items");
		Document doc = null;
		try {
			doc = database.createDocument();
			updateDocument(doc, items, computeWithForm);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void updateDocuments(Database database, JSONObject json, boolean computeWithForm) {
		log("- update document(s)");
		JSONObject items = (JSONObject) json.get("items");
		JSONObject findDocument = (JSONObject) json.get("findDocument");
		
		try {
			String search = "";
			@SuppressWarnings("unchecked")
			Set<Map.Entry<String, Object>> entries = findDocument.entrySet();
			for (Map.Entry<String, Object> entry : entries) {
				String name = entry.getKey();
				Object value = entry.getValue();

				if (!search.isEmpty()) {
					search += " & ";
				}
				String newCondition = name + "=\"" + value + "\"";
				search += newCondition;
			}
			log("search: " + search);

			DocumentCollection col = database.search(search, null, 1);
			if (col.getCount() == 0) return;

			Document doc = col.getFirstDocument();
			updateDocument(doc, items, computeWithForm);
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}

	private void updateDocument(Document doc, JSONObject items, boolean computeWithForm) throws NotesException {
		@SuppressWarnings("unchecked")
		Set<Map.Entry<String, Object>> entries = items.entrySet();
		for (Map.Entry<String, Object> entry : entries) {
			String name = entry.getKey();
			Object value = entry.getValue();
			doc.replaceItemValue(name, value);
			log("doc: " + name + " = " + value);
		}

		if (computeWithForm) {
			doc.computeWithForm(true, false);
			log("doc: compute with form : on");
		}
		doc.save();
		log("doc: saved");
	}

	private Database createDatabaseFromTemplate(String filePath, String title, String templatePath) {
		Database database = null;
		try {
			database = m_session.getDatabase(null, filePath, false);
			if (database != null && database.isOpen()) {
				log(database.getFilePath() + " - already exists; skip creating;");
			}
			else {
				log(filePath + " - attempt to create based on template: " + templatePath);
				Database template = m_session.getDatabase(null, templatePath);
				if (!template.isOpen()) {
					log(templatePath + " - template not found");
					return null;
				}
				database = template.createFromTemplate(null, filePath, true);
				database.setTitle(title);
				log(database.getFilePath() + " - has been created");
			}

			log(database.getFilePath() + " - exists/created");
			if (database.isOpen()) {
				log("> it is opened");
			}
			else {
				log("> it is NOT opened");	
				database.open();
			}
			log(database.getTitle());
		} catch (NotesException e) {
			e.printStackTrace();
		}

		return database;
	}

	/*
	 * notes.INI handling
	 */
	private void parseNotesINI(JSONArray array) {
		if (array == null) return;

		for(int i=0; i<array.size(); i++) {
			JSONObject obj = (JSONObject) array.get(i);

			String name = (String) obj.get("name");
			String value = String.valueOf(obj.get("value"));
			String action = (String) obj.get("action");

			try {
				// append with separator
				boolean append = (action != null && "append".equalsIgnoreCase(action));
				setNotesINI(name, value, append);
			} catch (NotesException e) {
				e.printStackTrace();
			}
		}
	}

	private String getNotesINI(String name) throws NotesException {
		return m_session.getEnvironmentString(name, true);
	}

	private void setNotesINI(String name, String value, boolean append) throws NotesException {
		if (append) {
			String currentValue = getNotesINI(name);
			if (!currentValue.contains(value)) {
				if (!currentValue.isEmpty()) {
					currentValue += ",";
				}
				currentValue += value;
			}
			value = currentValue;
		}

		m_session.setEnvironmentVar(name, value, true);	
		log("notes.ini: " + name + " = " + value);
	}

	private void log(Object o) {
		System.out.println(o.toString());
	}
}
