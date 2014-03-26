package edu.stanford.epad.epadws.processing.events;

import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;

public class ServerEventUtil
{
	public static void postEvent(String username, String event_status, String aimUid, String aimName, String patientID,
			String patientName, String templateID, String templateName, String pluginName)
	{
		EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		databaseOperations.insertEpadEvent(username, event_status, aimUid, aimName, patientID, patientName, templateID,
				templateName, pluginName);
	}
}