package net.prominic.gja_v20220322;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;
import lotus.domino.Session;

public class DominoUtils {
	public static void signDb(Session session, String filePath) {
		try {
			Database database = session.getDatabase(null, filePath);
			if (database == null || !database.isOpen()) {
				log("database not found: " + filePath);
				return;
			}
			log(database.getTitle().concat(" - initialized"));

			NoteCollection nc = database.createNoteCollection(false);
			nc.selectAllDesignElements(true);
			nc.buildCollection();

			log(database.getTitle().concat(" - design elements to sign: " + String.valueOf(nc.getCount())));

			String noteid = nc.getFirstNoteID();
			while (noteid.length() > 0) {
				Document doc = database.getDocumentByID(noteid);

				doc.sign();
				doc.save();
				doc.recycle();

				noteid = nc.getNextNoteID(noteid);
			}

			log(database.getTitle().concat(" - has been signed (").concat(String.valueOf(nc.getCount())) + " design elements)");
			
			nc.recycle();
			database.recycle();
		} catch (NotesException e) {
			log("signDb command failed: " + e.getMessage());
		}
	}

	private static void log(String string) {
		System.out.println();
	}


}
