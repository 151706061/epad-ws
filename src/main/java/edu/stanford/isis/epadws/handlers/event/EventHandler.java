package edu.stanford.isis.epadws.handlers.event;

import java.io.PrintWriter;
import java.net.URLDecoder;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.isis.epad.common.util.EPADConfig;
import edu.stanford.isis.epad.common.util.EPADLogger;
import edu.stanford.isis.epad.common.util.SearchResultUtils;
import edu.stanford.isis.epadws.persistence.Database;
import edu.stanford.isis.epadws.persistence.DatabaseOperations;
import edu.stanford.isis.epadws.xnat.XNATSessionOperations;
import edu.stanford.isis.epadws.xnat.XNATUtil;

/**
 * 
 * 
 * 
 * @author martin
 */
public class EventHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();
	private static final EPADConfig config = EPADConfig.getInstance();

	private static final String INVALID_METHOD_MESSAGE = "Only POST and GET methods valid for the events route";
	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error on event search";
	private static final String MISSING_JSESSIONID_MESSAGE = "No session identifier in event query";
	private static final String BAD_PARAMETERS_MESSAGE = "Missing parameters in event query";
	private static final String MISSING_QUERY_MESSAGE = "No query in event request";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid on event route";

	@Override
	public void handle(String base, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		PrintWriter responseStream;

		httpResponse.setContentType("text/plain");
		request.setHandled(true);

		if (XNATSessionOperations.hasValidXNATSessionID(httpRequest)) {
			try {
				responseStream = httpResponse.getWriter();

				String method = httpRequest.getMethod();
				String queryString = httpRequest.getQueryString();
				queryString = URLDecoder.decode(queryString, "UTF-8");

				if ("GET".equalsIgnoreCase(method)) {
					if (queryString != null) {
						queryString = queryString.trim();
						String jsessionID = XNATUtil.getJSessionIDFromRequest(httpRequest);
						if (jsessionID != null) {
							findEventsForSessionID(responseStream, jsessionID);
							httpResponse.setStatus(HttpServletResponse.SC_OK);
						} else {
							log.info(MISSING_JSESSIONID_MESSAGE);
							responseStream.append(MISSING_JSESSIONID_MESSAGE);
							httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
						}
					} else {
						log.info(MISSING_QUERY_MESSAGE);
						responseStream.append(MISSING_QUERY_MESSAGE);
						httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					}
				} else if ("POST".equalsIgnoreCase(method)) {
					if (queryString != null) {
						queryString = queryString.trim();
						String jsessionID = XNATUtil.getJSessionIDFromRequest(httpRequest);
						String event_status = getEventStatusFromRequest(queryString);
						String aim_uid = getAimUidFromRequest(queryString);
						String aim_name = getAimNameFromRequest(queryString);
						String patient_id = getPatientIdFromRequest(queryString);
						String patient_name = getPatientNameFromRequest(queryString);
						String template_id = getTemplateIdFromRequest(queryString);
						String template_name = getTemplateNameFromRequest(queryString);
						String plugin_name = getPluginNameFromRequest(queryString);

						log.info("Got event for AIM ID " + aim_uid + " with JSESSIONID " + jsessionID);

						if (jsessionID != null && event_status != null && aim_uid != null && aim_uid != null && aim_name != null
								&& patient_id != null && patient_name != null && template_id != null && template_name != null
								&& plugin_name != null) {
							DatabaseOperations databaseOperations = Database.getInstance().getDatabaseOperations();
							databaseOperations.insertEpadEvent(jsessionID, event_status, aim_uid, aim_name, patient_id, patient_name,
									template_id, template_name, plugin_name);
							responseStream.flush();
							httpResponse.setStatus(HttpServletResponse.SC_OK);
						} else {
							log.info(BAD_PARAMETERS_MESSAGE);
							responseStream.append(BAD_PARAMETERS_MESSAGE);
							httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
						}
					} else {
						log.info(MISSING_QUERY_MESSAGE);
						responseStream.append(MISSING_QUERY_MESSAGE);
						httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					}
				} else {
					log.info(INVALID_METHOD_MESSAGE);
					responseStream.append(INVALID_METHOD_MESSAGE);
					httpResponse.setHeader("Access-Control-Allow-Methods", "POST, GET");
					httpResponse.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
				}
			} catch (Throwable t) {
				log.severe(INTERNAL_EXCEPTION_MESSAGE, t);
				httpResponse.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
			}
		} else {
			log.info(INVALID_SESSION_TOKEN_MESSAGE);
			httpResponse.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		}
	}

	private void findEventsForSessionID(PrintWriter responseStrean, String sessionID)
	{
		DatabaseOperations databaseOperations = Database.getInstance().getDatabaseOperations();
		List<Map<String, String>> eventMap = databaseOperations.getEpadEventsForSessionID(sessionID);

		responseStrean.print(new SearchResultUtils().get_EVENT_SEARCH_HEADER());

		String separator = config.getStringPropertyValue("fieldSeparator");
		for (Map<String, String> row : eventMap) {
			StringBuilder sb = new StringBuilder();
			sb.append(row.get("pk")).append(separator);
			sb.append(row.get("event_status")).append(separator);
			sb.append(row.get("created_time")).append(separator);
			sb.append(row.get("aim_uid")).append(separator);
			sb.append(row.get("aim_name")).append(separator);
			sb.append(row.get("patient_id")).append(separator);
			sb.append(row.get("patient_name")).append(separator);
			sb.append(row.get("template_id")).append(separator);
			sb.append(row.get("template_name")).append(separator);
			sb.append(row.get("plugin_name"));
			sb.append("\n");
			responseStrean.print(sb.toString());
			log.info(sb.toString());
		}
	}

	private static String getEventStatusFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[1].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getAimUidFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[2].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getAimNameFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[3].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getPatientIdFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[4].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getPatientNameFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[5].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getTemplateIdFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[6].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getTemplateNameFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[7].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}

	private static String getPluginNameFromRequest(String queryString)
	{
		String[] parts = queryString.split("&");
		String value = parts[8].trim();
		parts = value.split("=");
		value = parts[1].trim();
		return value;
	}
}
