package edu.stanford.isis.epadws.handlers.dicom;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.GetMethod;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.isis.epad.common.util.EPADConfig;
import edu.stanford.isis.epad.common.util.EPADLogger;

/**
 * WADO Handler
 */
public class WadoHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();
	private static final EPADConfig config = EPADConfig.getInstance();

	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error in WADO route";
	private static final String MISSING_QUERY_MESSAGE = "No query in WADO request";

	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid on WADO route";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		ServletOutputStream responseStream = null;
		String origin = httpRequest.getHeader("Origin"); // CORS request should have Origin header
		int statusCode;

		// Origin header indicates a possible CORS requests, which we support to allow drawing on canvas in GWT Dev Mode.
		if (origin != null) {
			httpResponse.setHeader("Access-Control-Allow-Origin", origin);
			httpResponse.setHeader("Access-Control-Allow-Credentials", "true"); // Needed to allow cookies
		} else {
			httpResponse.setHeader("Access-Control-Allow-Origin", "*");
		}

		httpResponse.setContentType("image/jpeg");
		request.setHandled(true);

		String method = httpRequest.getMethod();
		if ("GET".equalsIgnoreCase(method)) {
			try {
				responseStream = httpResponse.getOutputStream();

				// if (XNATOperations.hasValidXNATSessionID(httpRequest)) {
				if (dummy()) { // TODO Re-enable authentication
					String queryString = httpRequest.getQueryString();
					queryString = URLDecoder.decode(queryString, "UTF-8");
					if (queryString != null) {
						statusCode = performWADOQuery(queryString, responseStream);
						if (statusCode != HttpServletResponse.SC_OK)
							log.warning("WADOHandler query " + queryString + " failed; statusCode=" + statusCode);
					} else {
						log.info(MISSING_QUERY_MESSAGE);
						statusCode = HttpServletResponse.SC_BAD_REQUEST;
					}
					responseStream.flush();
				} else {
					log.info(INVALID_SESSION_TOKEN_MESSAGE);
					statusCode = HttpServletResponse.SC_UNAUTHORIZED;
				}
			} catch (Throwable t) {
				log.severe(INTERNAL_EXCEPTION_MESSAGE, t);
				statusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			}
		} else {
			log.warning("WADOHandler received unexpected method " + method);
			statusCode = HttpServletResponse.SC_BAD_REQUEST;
		}
		httpResponse.setStatus(statusCode);
	}

	private boolean dummy()
	{
		return true;
	}

	private int performWADOQuery(String queryString, ServletOutputStream outputStream) throws IOException, HttpException
	{
		String wadoHost = config.getStringPropertyValue("NameServer");
		int wadoPort = config.getIntegerPropertyValue("DicomServerWadoPort");
		String wadoBase = config.getStringPropertyValue("WadoUrlExtension");
		String wadoUrl = buildWADOURL(wadoHost, wadoPort, wadoBase, queryString);
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(wadoUrl);
		int statusCode = client.executeMethod(method);

		if (statusCode == HttpServletResponse.SC_OK) {
			InputStream res = null;
			try {
				res = method.getResponseBodyAsStream();
				int read = 0;
				byte[] bytes = new byte[4096];
				while ((read = res.read(bytes)) != -1) {
					outputStream.write(bytes, 0, read);
				}
			} finally {
				if (res != null) {
					try {
						res.close();
					} catch (IOException e) {
						log.warning("Warning: error closing WADO response stream", e);
					}
				}
			}
		} else {
			log.warning("Warning: unexpected response from WADO; statusCode=" + statusCode);
		}
		return statusCode;
	}

	private String buildWADOURL(String host, int port, String base, String queryString)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("http://").append(host);
		sb.append(":").append(port);
		sb.append(base);
		sb.append(queryString);
		return sb.toString();
	}
}
