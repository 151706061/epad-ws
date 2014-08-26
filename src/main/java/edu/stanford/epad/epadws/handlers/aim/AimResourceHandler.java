package edu.stanford.epad.epadws.handlers.aim;

import java.io.PrintWriter;
import java.net.URLDecoder;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.aim.AIMSearchType;
import edu.stanford.epad.epadws.aim.AIMUtil;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;

public class AimResourceHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error in AIM handler";
	private static final String INVALID_METHOD_MESSAGE = "Only POST and GET methods valid for the AIM route";
	private static final String FILE_UPLOAD_ERROR_MESSAGE = "AIM file upload failures; see response for details";
	private static final String MISSING_QUERY_MESSAGE = "No query in AIM request";
	private static final String MISSING_AIM_ID_MESSAGE = "No AIM identifier in delete request";
	private static final String BAD_QUERY_MESSAGE = "Bad query in AIM request; should have search type and user";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid for AIM request";

	/**
	 * To test the post try:
	 * 
	 * <pre>
	 * curl --form upload=@/tmp/AIM_83ga0zjofj3y8ncm8wb1k3mlitis1glyugamx0zl.xml http://[host]:[port]/epad/aimresource/
	 * </pre>
	 */
	@Override
	public void handle(String base, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		PrintWriter responseStream = null;
		int statusCode;

		httpResponse.setContentType("text/xml");
		httpResponse.setHeader("Cache-Control", "no-cache");
		request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();

			if (XNATSessionOperations.hasValidXNATSessionID(httpRequest)) {
				String method = httpRequest.getMethod();
				if ("GET".equalsIgnoreCase(method)) {
					String queryString = httpRequest.getQueryString();
					queryString = URLDecoder.decode(queryString, "UTF-8");
					if (queryString != null) { // TODO httpRequest.getParameter with "patientID", "user"
						AIMSearchType aimSearchType = AIMUtil.getAIMSearchType(httpRequest);
						String searchValue = aimSearchType != null ? httpRequest.getParameter(aimSearchType.getName()) : null;
						String user = httpRequest.getParameter("user");
						log.info("GET request for AIM resource from user " + user + "; query type is " + aimSearchType + ", value "
								+ searchValue);

						if (validParameters(aimSearchType, searchValue, user)) {
							String format = httpRequest.getParameter("format");
							if ("V4".equal(format))
								AIMUtil.queryAIMImageAnnotationsV4(responseStream, aimSearchType, searchValue, user);
							else
								AIMUtil.queryAIMImageAnnotations(responseStream, aimSearchType, searchValue, user);
							statusCode = HttpServletResponse.SC_OK;
						} else
							statusCode = HandlerUtil.badRequestResponse(BAD_QUERY_MESSAGE, log);
					} else
						statusCode = HandlerUtil.badRequestResponse(MISSING_QUERY_MESSAGE, log);
				} else if ("POST".equalsIgnoreCase(method)) {
					String annotationsUploadDirPath = EPADConfig.getEPADWebServerAnnotationsUploadDir();
					log.info("Uploading AIM annotation(s) to directory " + annotationsUploadDirPath);
					try {
						boolean saveError = AIMUtil.uploadAIMAnnotations(httpRequest, responseStream, annotationsUploadDirPath);
						if (saveError) {
							statusCode = HandlerUtil.internalErrorResponse(FILE_UPLOAD_ERROR_MESSAGE + "<br>", log);
						} else {
							statusCode = HttpServletResponse.SC_OK;
						}
					} catch (Throwable t) {
						String errorMessage = "Failed to upload AIM files to directory " + annotationsUploadDirPath;
						statusCode = HandlerUtil.internalErrorResponse(errorMessage, t, log);
					}
				} else if ("DELETE".equalsIgnoreCase(method)) {
					String queryString = httpRequest.getQueryString();
					queryString = URLDecoder.decode(queryString, "UTF-8");
					if (queryString != null) {
						String aimID = httpRequest.getParameter("aimID");
						if (aimID != null) {
							try {
								log.info("Deleting AIM annotation " + aimID);
								if (AIMUtil.deleteAIM(aimID))
									statusCode = HttpServletResponse.SC_OK;
								else
									statusCode = HttpServletResponse.SC_NOT_FOUND;
							} catch (Throwable t) {
								String errorMessage = INTERNAL_EXCEPTION_MESSAGE;
								statusCode = HandlerUtil.internalErrorResponse(errorMessage, t, log);
							}
						} else
							statusCode = HandlerUtil.badRequestResponse(MISSING_AIM_ID_MESSAGE, log);
					} else
						statusCode = HandlerUtil.badRequestResponse(MISSING_AIM_ID_MESSAGE, log);

				} else {
					httpResponse.setHeader("Access-Control-Allow-Methods", "POST, GET, DELETE");
					statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_METHOD_NOT_ALLOWED, INVALID_METHOD_MESSAGE,
							log);
				}
			} else {
				statusCode = HandlerUtil.invalidTokenResponse(INVALID_SESSION_TOKEN_MESSAGE, log);
			}
		} catch (Throwable t) {
			statusCode = HandlerUtil.internalErrorResponse(INTERNAL_EXCEPTION_MESSAGE + t.getMessage() + "<br>", t,
					responseStream, log);
		}
		httpResponse.setStatus(statusCode);
	}

	private boolean validParameters(AIMSearchType aimSearchType, String searchValue, String user)
	{
		return (aimSearchType != null && searchValue != null && user != null);
	}
}
