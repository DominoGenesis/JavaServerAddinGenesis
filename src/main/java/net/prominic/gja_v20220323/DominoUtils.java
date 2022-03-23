package net.prominic.gja_v20220323;

import lotus.domino.Database;
import lotus.domino.Document;
import lotus.domino.NoteCollection;
import lotus.domino.NotesException;

public class DominoUtils {
	public static void sign(Database database) {
		try {
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
		} catch (NotesException e) {
			log("signDb command failed: " + e.getMessage());
		}
	}

	private static void log(String string) {
		System.out.println();
	}


}
