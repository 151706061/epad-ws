package edu.stanford.isis.epadws.handlers.admin;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.isis.epad.common.util.EPADLogger;
import edu.stanford.isis.epadws.epaddb.EpadDatabase;
import edu.stanford.isis.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.isis.epadws.handlers.HandlerUtil;
import edu.stanford.isis.epadws.xnat.XNATSessionOperations;

/**
 * @author martin
 */
public class DICOMReprocessingHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String FORBIDDEN = "Forbidden method - only GET supported on reload route";
	private static final String INTERNAL_ERROR_MESSAGE = "Internal server error on reload route";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid for reload route";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		PrintWriter responseStream = null;
		int statusCode;

		httpResponse.setContentType("text/plain;charset=UTF-8");
		request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();

			if (XNATSessionOperations.hasValidXNATSessionID(httpRequest)) {
				String method = httpRequest.getMethod();
				if ("GET".equalsIgnoreCase(method)) {
					forceImageReload();
					statusCode = HttpServletResponse.SC_OK;
				} else {
					statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_FORBIDDEN, FORBIDDEN, responseStream, log);
				}
			} else {
				statusCode = HandlerUtil.invalidTokenJSONResponse(INVALID_SESSION_TOKEN_MESSAGE, responseStream, log);
			}
		} catch (Throwable t) {
			statusCode = HandlerUtil.internalErrorJSONResponse(INTERNAL_ERROR_MESSAGE, t, responseStream, log);
		}
		httpResponse.setStatus(statusCode);
	}

	private void forceImageReload()
	{
		final EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();

		log.info("Forcing reprocessing of all DICOM images");
		epadDatabaseOperations.forceDICOMReprocessing();
	}
}
