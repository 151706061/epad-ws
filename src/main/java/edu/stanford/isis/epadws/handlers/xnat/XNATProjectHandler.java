package edu.stanford.isis.epadws.handlers.xnat;

import java.io.IOException;
import java.io.InputStream;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.isis.epad.common.ProxyConfig;
import edu.stanford.isis.epad.common.ProxyLogger;
import edu.stanford.isis.epad.common.util.JsonHelper;
import edu.stanford.isis.epad.common.xnat.XNATProjectDescription;
import edu.stanford.isis.epadws.xnat.XNATUtil;

/**
 * XNAT-based project retrieval handler
 * <p>
 * <code>
 * curl -b JSESSIONID=<session_key> -X GET "http://[host:port]/projects/"
 * </code>
 * 
 * @author martin
 * @see XNATProjectDescription
 */
public class XNATProjectHandler extends AbstractHandler
{
	private static final ProxyLogger log = ProxyLogger.getInstance();
	private static final ProxyConfig config = ProxyConfig.getInstance();

	private static final String INVALID_SESSION_TOKEN_MESSAGE = "No valid session token";
	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error getting server status";
	private static final String XNAT_INVOCATION_ERROR_MESSAGE = "Error invoking XNAT project call";

	@Override
	public void handle(String base, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
			throws IOException
	{
		ServletOutputStream responseStream = null;
		int statusCode;

		httpResponse.setContentType("application/json");
		httpResponse.setHeader("Access-Control-Allow-Origin", "*");
		request.setHandled(true);

		try {
			responseStream = httpResponse.getOutputStream();

			if (XNATUtil.hasValidXNATSessionID(httpRequest)) {
				statusCode = invokeXNATProjectService(base, httpRequest, httpResponse, responseStream);
			} else {
				log.info(INVALID_SESSION_TOKEN_MESSAGE);
				responseStream.print(JsonHelper.createJSONErrorResponse(INVALID_SESSION_TOKEN_MESSAGE));
				statusCode = HttpServletResponse.SC_UNAUTHORIZED;
			}
		} catch (Throwable t) {
			log.warning(INTERNAL_EXCEPTION_MESSAGE, t);
			responseStream.print(JsonHelper.createJSONErrorResponse(INTERNAL_EXCEPTION_MESSAGE, t));
			statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		} finally {
			if (responseStream != null) {
				try {
					responseStream.flush();
					responseStream.close();
				} catch (IOException e) {
					log.warning("Error closing project output stream", e);
				}
			}
		}
		httpResponse.setStatus(statusCode);
	}

	private int invokeXNATProjectService(String base, HttpServletRequest httpRequest, HttpServletResponse httpResponse,
			ServletOutputStream outputStream) throws IOException, HttpException
	{
		String xnatHost = config.getStringConfigurationParameter("XNATServer");
		int xnatPort = config.getIntegerConfigurationParameter("XNATPort");
		String xnatProjectURL = XNATUtil.buildURLString(xnatHost, xnatPort, XNATUtil.XNAT_PROJECT_BASE, base);
		HttpClient client = new HttpClient();
		GetMethod getMethod = new GetMethod(xnatProjectURL);
		String jsessionID = XNATUtil.getJSessionIDFromRequest(httpRequest);

		// TODO Cleaner cookie pass through?
		getMethod.setRequestHeader("Cookie", "JSESSIONID=" + jsessionID);

		log.info("Invoking XNAT project service at " + xnatProjectURL);

		int statusCode = client.executeMethod(getMethod);

		if (statusCode == HttpServletResponse.SC_OK) {
			log.info("Successfully invoked XNAT project service");
			InputStream xnatResponse = null;
			try {
				xnatResponse = getMethod.getResponseBodyAsStream();
				int read = 0;
				byte[] bytes = new byte[4096];
				while ((read = xnatResponse.read(bytes)) != -1) {
					outputStream.write(bytes, 0, read);
				}
			} finally {
				if (xnatResponse != null) {
					try {
						xnatResponse.close();
					} catch (IOException e) {
						log.warning("Error closing XNAT project response stream", e);
					}
				}
			}
		} else {
			log.info(XNAT_INVOCATION_ERROR_MESSAGE + ";status code=" + statusCode);
			JsonHelper.createJSONErrorResponse(XNAT_INVOCATION_ERROR_MESSAGE + ";status code=" + statusCode);
		}
		return statusCode;
	}
}
