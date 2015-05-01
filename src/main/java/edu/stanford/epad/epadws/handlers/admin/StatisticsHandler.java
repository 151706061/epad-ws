package edu.stanford.epad.epadws.handlers.admin;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.dicom.DICOMFileDescription;
import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabaseOperations;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.models.EpadStatistics;
import edu.stanford.epad.epadws.models.Project;
import edu.stanford.epad.epadws.models.Study;
import edu.stanford.epad.epadws.models.Subject;
import edu.stanford.epad.epadws.models.User;
import edu.stanford.epad.epadws.models.WorkList;
import edu.stanford.epad.epadws.processing.pipeline.task.DSOMaskPNGGeneratorTask;
import edu.stanford.epad.epadws.processing.pipeline.task.SingleFrameDICOMPngGeneratorTask;
import edu.stanford.epad.epadws.processing.pipeline.watcher.QueueAndWatcherManager;
import edu.stanford.epad.epadws.queries.DefaultEpadOperations;
import edu.stanford.epad.epadws.queries.EpadOperations;
import edu.stanford.epad.epadws.service.SessionService;

/**
 * @author dev
 */
public class StatisticsHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String FORBIDDEN = "Forbidden method - only PUT supported on statistics route";
	private static final String INTERNAL_ERROR_MESSAGE = "Internal server error on statistics route";
	private static final String INTERNAL_IO_ERROR_MESSAGE = "Internal server IO error on statistics route";
	private static final String INTERNAL_SQL_ERROR_MESSAGE = "Internal server SQL error on statistics route";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid for statistics route";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		PrintWriter responseStream = null;
		int statusCode;

		request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();

			String method = httpRequest.getMethod();
			if ("PUT".equalsIgnoreCase(method)) {
				try {
					String host = httpRequest.getParameter("host");
					if (host != null) {
						statusCode = HttpServletResponse.SC_OK;
						EpadStatistics es = new EpadStatistics();
						int users = getInt(httpRequest.getParameter("numOfUsers"));
						int projects = getInt(httpRequest.getParameter("numOfProjects"));
						int patients = getInt(httpRequest.getParameter("numOfPatients"));
						int studies = getInt(httpRequest.getParameter("numOfStudies"));
						int series = getInt(httpRequest.getParameter("numOfSeries"));
						int aims = getInt(httpRequest.getParameter("numOfAims"));
						int dsos = getInt(httpRequest.getParameter("numOfDSOs"));
						int wls = getInt(httpRequest.getParameter("numOfWorkLists"));
						String remoteHost = request.getRemoteHost();
						es.setHost(host + " : " + remoteHost);
						es.setNumOfUsers(users);
						es.setNumOfProjects(projects);
						es.setNumOfPatients(patients);
						es.setNumOfStudies(studies);
						es.setNumOfSeries(series);
						es.setNumOfAims(aims);
						es.setNumOfDSOs(dsos);
						es.setNumOfWorkLists(wls);
						es.setCreator("admin");
						es.save();
					}
					else
						statusCode = HttpServletResponse.SC_BAD_REQUEST;

				} catch (IOException e) {
					statusCode = HandlerUtil.internalErrorResponse(INTERNAL_IO_ERROR_MESSAGE, e, responseStream, log);
				} catch (SQLException e) {
					statusCode = HandlerUtil.internalErrorResponse(INTERNAL_SQL_ERROR_MESSAGE, e, responseStream, log);
				}
			} else {
				statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_FORBIDDEN, FORBIDDEN, responseStream, log);
			}

			responseStream.flush();
		} catch (Throwable t) {
			statusCode = HandlerUtil.internalErrorJSONResponse(INTERNAL_ERROR_MESSAGE, t, responseStream, log);
		}
		httpResponse.setStatus(statusCode);
	}
	
	private int getInt(String value)
	{
		try {
			return new Integer(value.trim()).intValue();
		} catch (Exception x) {
			return 0;
		}
	}
}
