package net.prominic.gja_v20220325;

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
	private String m_catalog;

	public JSONRules(Session session, String catalog) {
		m_session = session;
		m_catalog = catalog;
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

	/*
	 * Exectute JSON
	 */
	public void execute(JSONObject jsonObject) {
		// if error
		if (jsonObject.containsKey("error")) {
			String error = (String) jsonObject.get("error");	
			log(error);
			return;
		}

		JSONArray steps = (JSONArray) jsonObject.get("steps");
		if (steps.size() == 0) {
			log("there are no steps defined in json file");
			return;
		}

		for(int i=0; i<steps.size(); i++) {
			JSONObject step = (JSONObject) steps.get(i);
			parseStep(step);
		}
	}

	/*
	 * Parse a step
	 */
	private void parseStep(JSONObject step) {
		if (step.containsKey("title")) {
			log(step.get("title"));
		}

		if (step.containsKey("dependencies")) {
			doDependencies((JSONArray) step.get("dependencies"));
		}
		else if(step.containsKey("files")) {
			doFiles((JSONArray) step.get("files"));
		}
		else if(step.containsKey("notesINI")) {
			doNotesINI((JSONArray) step.get("notesINI"));
		}
		else if(step.containsKey("databases")) {
			doDatabases((JSONArray) step.get("databases"));
		}
		else if(step.containsKey("messages")) {
			doMessages((JSONArray) step.get("messages"));
		}
	}
	
	private void doDependencies(JSONArray list) {
		if (list == null || list.size() == 0) return;

		try {
			for(int i=0; i<list.size(); i++) {
				String v = (String) list.get(i);

				StringBuffer appJSON = HTTP.get(m_catalog + "/app?openagent&name=" + v);
				JSONRules dependency = new JSONRules(this.m_session, this.m_catalog);
				dependency.execute(appJSON.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/*
	 * Display messages to Domino console
	 */
	private void doMessages(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			String v = (String) list.get(i);
			log(v);
		}
	}

	/*
	 * Download files
	 */
	private void doFiles(JSONArray list) {
		if (list == null || list.size() == 0) return;

		String directory;
		try {
			directory = this.m_session.getEnvironmentString("Directory", true);

			for(int i=0; i<list.size(); i++) {
				JSONObject obj = (JSONObject) list.get(i);

				String from = (String) obj.get("from");
				String to = (String) obj.get("to");

				if (to.indexOf("${directory}")>=0) {
					to = to.replace("${directory}", directory);
				};

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

	/*
	 * notes.INI handling
	 */
	private void doNotesINI(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			JSONObject obj = (JSONObject) list.get(i);

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

	/*
	 * notes.INI variables (set, append)
	 */
	private void setNotesINI(String name, String value, boolean append) throws NotesException {
		if (append) {
			String currentValue = m_session.getEnvironmentString(name, true);
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

	private void doDatabases(JSONArray list) {
		if (list == null || list.size() == 0) return;

		for(int i=0; i<list.size(); i++) {
			JSONObject database = (JSONObject) list.get(i);
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


	private void log(Object o) {
		System.out.println(o.toString());
	}
}
