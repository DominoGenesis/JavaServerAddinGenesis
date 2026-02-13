package net.prominic.gja_v085.utils;

import lotus.domino.Base;
import lotus.domino.NotesException;

public class DominoUtils {
	public static void recycle(Base... list) {
		try {
			for (Base obj : list) {
				if (obj != null) {
					obj.recycle();
					obj = null;
				}
			}
		} catch (NotesException e) {
			e.printStackTrace();
		}
	}
}
