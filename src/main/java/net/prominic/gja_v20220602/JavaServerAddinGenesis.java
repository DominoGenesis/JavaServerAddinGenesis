package net.prominic.gja_v20220602;

import java.io.File;
import java.io.FilenameFilter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

import lotus.domino.Database;
import lotus.domino.NotesException;
import lotus.domino.NotesFactory;
import lotus.domino.Session;
import lotus.notes.addins.JavaServerAddin;
import lotus.notes.internal.MessageQueue;
import net.prominic.util.FileUtils;

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
	protected String				m_javaAddinConfig		= null;
	protected GLogger				m_logger				= null;
	protected String[] 				args 					= null;
	private int 					dominoTaskID			= 0;
	private ArrayList<Event>		m_events				= null;
	private String 					m_startDateTime			= "";

	protected final String 			JAVA_USER_CLASSES_EXT 	= "JavaUserClassesExt";
	protected static final String 	JAVA_ADDIN_ROOT			= "JavaAddin";
	protected static final String 	COMMAND_FILE_NAME		= "command.txt";
	private static final String 	LIVE_FILE_NAME			= "live.txt";
	protected static final String 	CONFIG_FILE_NAME		= "config.txt";

	// constructor if parameters are provided
	public JavaServerAddinGenesis(String[] args) {
		this.args = args;
	}

	public JavaServerAddinGenesis() {}

	protected abstract String getJavaAddinVersion();
	protected abstract String getJavaAddinDate();
	protected void showHelpExt() {}
	protected void showInfoExt() {}
	protected void runNotesBeforeListen() {}
	protected void termBeforeAB() {}

	protected String getJavaAddinName() {
		return this.getClass().getName();
	}

	protected String getCoreVersion() {
		return "2022.06.02";
	}

	protected String getQName() {
		return MSG_Q_PREFIX + getJavaAddinName().toUpperCase();
	}

	/*
	 * Used for initialization
	 */
	private boolean runNotesInitialize() {
		return true;
	}

	/*
	 * Used for validation & initialization
	 */
	protected boolean runNotesAfterInitialize() {
		return true;
	}

	/* the runNotes method, which is the main loop of the Addin */
	@Override
	public void runNotes() {
		boolean initialize = runNotesInitialize();
		if(!initialize) return;

		// Set the Java thread name to the class name (default would be "Thread-n")
		this.setName(this.getJavaAddinName());
		// Create the status line showed in 'Show Task' console command
		this.dominoTaskID = createAddinStatusLine(this.getJavaAddinName());
		try {
			m_javaAddinFolder = JAVA_ADDIN_ROOT + File.separator + this.getClass().getName();
			m_logger = new GLogger(m_javaAddinFolder);
			m_session = NotesFactory.createSession();
			m_ab = m_session.getDatabase(m_session.getServerName(), "names.nsf");
			m_javaAddinCommand = m_javaAddinFolder + File.separator + COMMAND_FILE_NAME;
			m_javaAddinLive = m_javaAddinFolder + File.separator + LIVE_FILE_NAME;
			m_javaAddinConfig = m_javaAddinFolder + File.separator + CONFIG_FILE_NAME;
			m_startDateTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());

			boolean next = runNotesAfterInitialize();
			if (!next) return;

			// add main event
			EventTimeLive event = new EventTimeLive("LiveDateStamp", 60, true, this.m_logger);
			event.FilePath = this.m_javaAddinLive;
			eventsAdd(event);

			// Clean logs
			long monthInSeconds = 30 * 86400;
			Event eventCleaner = new EventLogCleaner("LogCleaner", monthInSeconds, true, this.m_logger);
			eventsAdd(eventCleaner);

			// cleanup old command file if exists
			File file = new File(m_javaAddinCommand);
			if (file.exists()) {
				file.delete();
			}

			showInfo();

			runNotesBeforeListen();
			listen();
		} catch(Exception e) {
			logSevere(e);
		}
	}

	protected String getJavaAddinFolder() {
		return this.m_javaAddinFolder;
	}

	public String[] getAllAddin() {
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
					FileUtils.writeFile(fileCommand, command);	
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

		String cmd = FileUtils.readFile(f);
		return cmd;
	}

	public void reload() {
		this.stopAddin();
	}

	public void restartAll(boolean includeThisAddin) {
		sendCommandAll("reload", includeThisAddin);
	}

	protected boolean isLive(String javaAddin) {
		File f = new File(javaAddin + File.separator + LIVE_FILE_NAME);
		if (!f.exists()) return false;

		String sTimeStamp = FileUtils.readFile(f);
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

			this.eventsFireOnStart();	// start events before loop
			this.eventsStart();			// enable timer
			while (this.addInRunning() && (messageQueueState != MessageQueue.ERR_MQ_QUITTING)) {
				listenAfterWhile();

				/* gives control to other task in non preemptive os*/
				OSPreemptOccasionally();

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

				// check if we need to run events
				eventsFire();
			}
		} catch(Exception e) {
			logSevere(e);
		}
	}
	
	protected String getConfigValue(String name) {
		return GConfig.get(this.m_javaAddinConfig, name);
	}

	protected void setConfigValue(String name, String value) {
		GConfig.set(this.m_javaAddinConfig, name, value);
	}

	protected void listenAfterWhile() {
		setAddinState("Idle");		
	}

	protected void eventsAdd(Event event) {
		if (this.m_events == null) {
			this.m_events = new ArrayList<Event>();
		}
		m_events.add(event);
	}

	private void eventsStart() {
		if (this.m_events == null) return;

		for (Event event: m_events) {
			event.start();
		}
	}

	private void eventsFireOnStart() {
		for (int i = 0; i < m_events.size(); i++) {
			Event event = m_events.get(i);
			if (event.fireOnStart()) {
				event.run();
			}
		}
	}

	private void eventsFire() {
		eventsFire(true);
	}

	private void eventsFire(boolean resetTimer) {
		for (int i = 0; i < m_events.size(); i++) {
			Event event = m_events.get(i);
			if (event.fire()) {
				event.run();
				if (resetTimer) {
					event.start();
				}
			}
		}
	}

	private void eventsFireForce() {
		for (Event event: m_events) {
			event.run();
		}
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
		else if ("fire".equals(cmd)) {
			eventsFireForce();
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

	protected void showHelp() {
		logMessage("*** Usage ***");
		logMessage("load runjava " + this.getJavaAddinName());
		logMessage("tell " + this.getJavaAddinName() + " <command>");
		logMessage("   quit             Unload addin");
		logMessage("   help             Show help information (or -h)");
		logMessage("   info             Show version and more of Genesis");

		// in case if you need to extend help with other commands
		showHelpExt();

		int year = Calendar.getInstance().get(Calendar.YEAR);
		logMessage("Copyright (C) Prominic.NET, Inc. 2021" + (year > 2021 ? " - " + Integer.toString(year) : ""));
		logMessage("See https://prominic.net for more details.");
	}

	private void showInfo() {
		logMessage("version      " + getJavaAddinVersion() + " (core: " + this.getCoreVersion() + ")");
		logMessage("date         " + getJavaAddinDate());
		logMessage("parameters   " + Arrays.toString(this.args));
		logMessage("log folder   " + m_logger.getDirectory());
		logMessage("logging      " + m_logger.getLevelLabel());
		logMessage("started      " + m_startDateTime);


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
		if (m_logger != null) m_logger.info(message);
		AddInLogMessageText(this.getJavaAddinName() + ": " + message, 0);
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logWarning(String message) {
		if (m_logger != null) m_logger.severe(message);
		AddInLogErrorText(this.getJavaAddinName() + ": (!!!) " + message, 0);
	}
	protected final void logWarning(Exception e) {
		if (m_logger != null) m_logger.warning(e);
		e.printStackTrace();
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logSevere(String message) {
		if (m_logger != null) m_logger.severe(message);

		AddInLogErrorText(this.getJavaAddinName() + ": (###) " + message, 0);
	}

	/**
	 * Write a log message to the Domino console. The message string will be prefixed with the add-in name
	 * followed by a column, e.g. <code>"AddinName: xxxxxxxx"</code>
	 *
	 * @param	message		Message to be displayed
	 */
	protected final void logSevere(Exception e) {
		if (m_logger != null) m_logger.severe(e);	
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
		logMessage("MainThread: termThread");

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

	/*
	 * Clean old jar and log files
	 * We keep last 5 jar files and last 5 log files
	 */
	/*
	public void cleanOutdatedFiles(String ext) {
		try {
			File dir = new File("DominoMeterAddin");
			if (!dir.isDirectory()) return;

			File files[] = FileUtils.endsWith(dir, ext);
			if (files.length <= 5) return;

			int count = 0;
			StringBuffer deletedFiles = new StringBuffer();
			files = FileUtils.sortFilesByModified(files, false);
			for (int i = 5; i < files.length; i++) {
				File file = files[i];
				if (!file.getName().equalsIgnoreCase(version)) {
					file.delete();
					if (count > 0) deletedFiles.append(", ");
					deletedFiles.append(file.getName());
					count++;
				}
			}

			if (count>0) {
				fileLogger.info("Removed files (" + Integer.toString(count) + "): " + deletedFiles.toString());
				Log.sendLog(server, endpoint, "Removed files (" + Integer.toString(count) + ")", deletedFiles.toString());
			}

		} catch (Exception e) {
			incrementExceptionTotal();
			fileLogger.severe(e);
			Log.sendError(server, endpoint, new ParsedError(e));
			e.printStackTrace();
		}
	}
	 */

}
