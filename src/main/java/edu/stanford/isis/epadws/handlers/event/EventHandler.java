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
import edu.stanford.isis.epadws.epaddb.EpadDatabase;
import edu.stanford.isis.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.isis.epadws.xnat.XNATSessionOperations;
import edu.stanford.isis.epadws.xnat.XNATUtil;

/**
 * Initial version of ePAD's new event notification system
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
							log.warning(MISSING_JSESSIONID_MESSAGE);
							responseStream.append(MISSING_JSESSIONID_MESSAGE);
							httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
						}
					} else {
						log.warning(MISSING_QUERY_MESSAGE);
						responseStream.append(MISSING_QUERY_MESSAGE);
						httpResponse.setStatus(HttpServletResponse.SC_BAD_REQUEST);
					}
				} else if ("POST".equalsIgnoreCase(method)) {
					if (queryString != null) {
						queryString = queryString.trim();
						String jsessionID = XNATUtil.getJSessionIDFromRequest(httpRequest);
						String event_status = httpRequest.getParameter("event_status");
						String aim_uid = httpRequest.getParameter("aim_uid");
						String aim_name = httpRequest.getParameter("aim_name");
						String patient_id = httpRequest.getParameter("patient_id");
						String patient_name = httpRequest.getParameter("patient_name");
						String template_id = httpRequest.getParameter("template_id");
						String template_name = httpRequest.getParameter("template_name");
						String plugin_name = httpRequest.getParameter("plugin_name");

						log.info("Got event for AIM ID " + aim_uid + " with JSESSIONID " + jsessionID);

						if (jsessionID != null && event_status != null && aim_uid != null && aim_uid != null && aim_name != null
								&& patient_id != null && patient_name != null && template_id != null && template_name != null
								&& plugin_name != null) {
							EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
							epadDatabaseOperations.insertEpadEvent(jsessionID, event_status, aim_uid, aim_name, patient_id,
									patient_name, template_id, template_name, plugin_name);
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
		EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		List<Map<String, String>> eventMap = epadDatabaseOperations.getEpadEventsForSessionID(sessionID);

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
}
