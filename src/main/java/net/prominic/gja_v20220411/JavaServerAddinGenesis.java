package net.prominic.gja_v20220411;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;

public abstract class JavaServerAddinGenesis extends JavaServerAddin {

	// MessageQueue Constants
	// Message Queue name for this Addin (normally uppercase);
	// MSG_Q_PREFIX is defined in JavaServerAddin.class
	protected static final int 		MQ_MAX_MSGSIZE 			= 1024;
	protected MessageQueue 			mq						= null;
	protected Session 				m_session				= null;
	protected Database				m_ab					= null;
	protected String				m_javaAddinFolder		= null;
	protected String				m_javaAddinCommand		= null;
	protected String				m_javaAddinLive			= null;
	protected GLogger				m_logger				= null;
	protected String[] 				args 					= null;
	private int 					dominoTaskID			= 0;

	protected final String 			JAVA_USER_CLASSES_EXT 	= "JavaUserClassesExt";
	protected static final String 	JAVA_ADDIN_ROOT			= "JavaAddin";
	protected static final String 	COMMAND_FILE_NAME		= "command.txt";
	private static final String 	LIVE_FILE_NAME			= "live.txt";
	private static final int 		LIVE_INTERVAL_MINUTES	= 1;

	// constructor if parameters are provided
	public JavaServerAddinGenesis(String[] args) {
		this.args = args;
	}

	public JavaServerAddinGenesis() {}

	protected abstract String getJavaAddinVersion();
	protected abstract String getJavaAddinDate();
	protected void showHelpExt() {}
	protected void showInfoExt() {}
	protected void runNotesBeforeInitialize() {}
	protected void runNotesBeforeListen() {}
	protected void termBeforeAB() {}
	
	protected String getJavaAddinName() {
		return this.getClass().getName();
	}
	
	protected String getCoreVersion() {
		return "2022.04.11";
	}

	protected String getQName() {
		return MSG_Q_PREFIX + getJavaAddinName().toUpperCase();
	}

	/* the runNotes method, which is the main loop of the Addin */
	@Override
	public void runNotes() {
		// Set the Java thread name to the class name (default would be "Thread-n")
		this.setName(this.getJavaAddinName());

		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = createAddinStatusLine(this.getJavaAddinName());

		try {
			runNotesBeforeInitialize();

			m_session = NotesFactory.createSession();
			m_ab = m_session.getDatabase(m_session.getServerName(), "names.nsf");
			m_javaAddinFolder = JAVA_ADDIN_ROOT + File.separator + this.getClass().getName();
			m_javaAddinCommand = m_javaAddinFolder + File.separator + COMMAND_FILE_NAME;
			m_javaAddinLive = m_javaAddinFolder + File.separator + LIVE_FILE_NAME;

			m_logger = new GLogger(m_javaAddinFolder);
			
			// cleanup old command file if exists
			File file = new File(m_javaAddinCommand);
			if (file.exists()) {
				file.delete();
			}

			ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args, m_logger);
			pc.setState(m_ab, ProgramConfig.LOAD);		// set program documents in LOAD state

			showInfo();

			runNotesBeforeListen();
			
			listen();
		} catch(Exception e) {
			logSevere(e);
		}
	}

	protected String[] getAllAddin() {
		File file = new File(JAVA_ADDIN_ROOT);
		String[] directories = file.list(new FilenameFilter() {
			@Override
			public boolean accept(File current, String name) {
				return new File(current, name).isDirectory();
			}
		});

		return directories;
	}

	/*
	 * scan JavaAddin folder for sub-folders (addins) and update command.txt with a command
	 */
	private void sendCommandAll(String command, boolean incudeThisAddin) {
		String[] directories = getAllAddin();
		for(int i=0; i<directories.length; i++) {
			if (incudeThisAddin || !directories[i].equalsIgnoreCase(getJavaAddinName())) {
				String javaAddin = JAVA_ADDIN_ROOT + File.separator + directories[i];
				if (isLive(javaAddin)) {
					File fileCommand = new File(javaAddin + File.separator + COMMAND_FILE_NAME);
					writeFile(fileCommand, command);	
				}
			}
		}		
	}
	
	/*
	 * read command from the file (command.txt)
	 */
	protected String readCommand() {
		File f = new File(this.m_javaAddinCommand);
		if (!f.exists()) return "";
		
		return this.readFile(f);
	}
	
	public void restartAll(boolean includeThisAddin) {
		sendCommandAll("reload", includeThisAddin);
	}

	protected boolean isLive(String javaAddin) {
		File f = new File(javaAddin + File.separator + LIVE_FILE_NAME);
		if (!f.exists()) return false;

		String sTimeStamp = readFile(f);
		if (sTimeStamp.length() == 0) return false;

		// last live date
		long timeStamp = Long.parseLong(sTimeStamp);
		Date date1 = new Date(timeStamp);
		Calendar c1 = Calendar.getInstance();
		c1.setTime(date1);
		c1.add(Calendar.HOUR, 1);

		// current date
		Calendar c2 = Calendar.getInstance();

		return c1.after(c2);
	}

	public void reload() {
		ProgramConfig pc = new ProgramConfig(this.getJavaAddinName(), this.args, m_logger);
		pc.setState(m_ab, ProgramConfig.UNLOAD);		// set program documents in UNLOAD state
		this.stopAddin();
	}

	private void writeFile(File file, String cmd) {
		try {
			PrintWriter writer = new PrintWriter(file, "UTF-8");
			writer.println(cmd);
			writer.close();
		} catch (FileNotFoundException e) {
			logSevere(e);
		} catch (UnsupportedEncodingException e) {
			logSevere(e);
		}
	}

	protected String readFile(File file) {
		if (!file.exists()) return "";

		StringBuilder contentBuilder = new StringBuilder();
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			String sCurrentLine;
			while ((sCurrentLine = br.readLine()) != null) {
				contentBuilder.append(sCurrentLine);
			}
		} 
		catch (IOException e) {
			logSevere(e);
		}
		return contentBuilder.toString();
	}

	@SuppressWarnings("deprecation")
	protected void listen() {
		StringBuffer qBuffer = new StringBuffer(MQ_MAX_MSGSIZE);

		try {
			mq = new MessageQueue();
			int messageQueueState = mq.create(this.getQName(), 0, 0);	// use like MQCreate in API
			if (messageQueueState == MessageQueue.ERR_DUPLICATE_MQ) {
				logWarning(this.getJavaAddinName() + " task is already running");
				return;
			}

			if (messageQueueState != MessageQueue.NOERROR) {
				logWarning("Unable to create the Domino message queue");
				return;
			}

			if (mq.open(this.getQName(), 0) != MessageQueue.NOERROR) {
				logWarning("Unable to open Domino message queue");
				return;
			}

			updateLiveDateStamp();

			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				setAddinState("Idle");

				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

				// every 10 mins we save status
				if (this.AddInHasMinutesElapsed(LIVE_INTERVAL_MINUTES)) {
					updateLiveDateStamp();
				}
				
				// check for command from console
				messageQueueState = mq.get(qBuffer, MQ_MAX_MSGSIZE, MessageQueue.MQ_WAIT_FOR_MSG, 1000);
				if (messageQueueState == MessageQueue.ERR_MQ_QUITTING) {
					return;
				}

				// check messages for Genesis
				String cmd = qBuffer.toString().trim();
				if (!cmd.isEmpty()) {
					resolveMessageQueueState(cmd);
				};

				// execute commands from file
				String line = readCommand();
				if (!line.isEmpty()) {
					logMessage(line);
					resolveMessageQueueState(line);
				}
			}
		} catch(Exception e) {
			logSevere(e);
		}
	}
	
	// file keeps getting updated while java addin works
	private void updateLiveDateStamp() {
		File f = new File(this.m_javaAddinLive);
		long currentTime = System.currentTimeMillis();
		writeFile(f, String.valueOf(currentTime));
	}

	protected boolean resolveMessageQueueState(String cmd) {
		boolean flag = true;

		if ("-h".equals(cmd) || "help".equals(cmd)) {
			showHelp();
		}
		else if ("quit".equals(cmd)) {
			quit();
		}
		else if ("info".equals(cmd)) {
			showInfo();
		}
		else if ("reload".equals(cmd)) {
			reload();
		}
		else if ("restart".equals(cmd)) {
			restartAll(true);
		}
		else {
			flag = false;
		}

		return flag;
	}

	private void showHelp() {
		logMessage("*** Usage ***");
		logMessage("load runjava " + this.getJavaAddinName());
		logMessage("tell " + this.getJavaAddinName() + " <command>");
		logMessage("   quit             Unload addin");
		logMessage("   help             Show help information (or -h)");
		logMessage("   info             Show version and more of Genesis");

		// in case if you need to extend help with other commands
		showHelpExt();

		// TODO: make it unique for each module
		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("Copyright (C) Prominic.NET, Inc. 2021" + (year > 2021 ? " - " + Integer.toString(year) : ""));
		logMessage("See https://prominic.net for more details.");
	}
	
	private void showInfo() {
		logMessage("version      " + this.getJavaAddinVersion() + " (core: " + this.getCoreVersion() + ")");
		logMessage("date         " + this.getJavaAddinDate());
		logMessage("parameters   " + Arrays.toString(this.args));

		// in case if you need to extend help with other commands
		showInfoExt();
	}

	protected void quit() {
		this.stopAddin();
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logMessage(String message) {
		m_logger.info(message);
		AddInLogMessageText(this.getJavaAddinName() + ": " + message, 0);
	}
	
	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logWarning(String message) {
		m_logger.severe(message);
		AddInLogMessageText(this.getJavaAddinName() + ": (!!!) " + message, 0);
	}
	protected final void logWarning(Exception e) {
		m_logger.warning(e);
		e.printStackTrace();
	}
	
	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logSevere(String message) {
		m_logger.severe(message);
		AddInLogMessageText(this.getJavaAddinName() + ": (###) " + message, 0);
	}
	
	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logSevere(Exception e) {
		logSevere(e);
		e.printStackTrace();
	}

	/**
	 * Set the text of the add-in which is shown in command <code>"show tasks"</code>.
	 *
	 * @param	text	Text to be set
	 */
	protected final void setAddinState(String text) {
		if (this.dominoTaskID == 0) return;

		AddInSetStatusLine(this.dominoTaskID, text);
	}

	/**
	 * Create the Domino task status line which is shown in <code>"show tasks"</code> command.
	 *
	 * Note: This method is also called by the JAddinThread and the user add-in
	 *
	 * @param	name	Name of task
	 * @return	Domino task ID
	 */
	protected final int createAddinStatusLine(String name) {
		return (AddInCreateStatusLine(name));
	}

	@Override
	public void termThread() {
		terminate();

		super.termThread();
	}

	/**
	 * Terminate all variables
	 */
	protected void terminate() {
		try {
			AddInDeleteStatusLine(dominoTaskID);

			termBeforeAB();
			
			if (this.m_ab != null) {
				this.m_ab.recycle();
				this.m_ab = null;
			}
			if (this.m_session != null) {
				this.m_session.recycle();
				this.m_session = null;
			}
			if (this.mq != null) {
				this.mq.close(0);
				this.mq = null;
			}

			logMessage("UNLOADED (OK) " + this.getJavaAddinVersion());
		} catch (NotesException e) {
			logSevere("UNLOADED (**FAILED**) " + this.getJavaAddinVersion());
		}
	}
}
