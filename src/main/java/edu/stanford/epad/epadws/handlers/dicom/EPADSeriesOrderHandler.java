package edu.stanford.epad.epadws.handlers.dicom;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.dtos.EPADDatabaseSeries;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.queries.DefaultEpadOperations;
import edu.stanford.epad.epadws.queries.EpadOperations;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;

/**
 * Create description of the images in a series and their order.
 * 
 * <p>
 * <code>
 * { "ResultSet": 
 *    [ { "contentTime": "", "fileName": "1_2_840_113619_2_131_2819278861_1343943578_325998.dcm", "instanceNumber": 1, "sliceLocation": "-0.0800" },
 *    ...
 *    ]
 * }
 * </code>
 * <p>
 * The SQL to find this information in the DCM4CHEE database is like:
 * <p>
 * <code>
 * select * from pacsdb.instance as i, pacsdb.series as s where i.series_fk=s.pk and s.series_iuid=?
 * </code>
 * <p>
 * To test:
 * <p>
 * <code>
 * curl -b JSESSIONID=<id> -X GET "http://[host]:[port]/epad/seriesorderj/?series_iuid=1.2.840.113619.2.55.3.25168424.5576.1168603848.697"
 * </code>
 */
public class EPADSeriesOrderHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String MISSING_SERIES_IUID_MESSAGE = "No series ID parameter in DICOM series request";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid on DICOM series order route";
	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error in DICOM series order handler";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		EpadOperations epadQueries = DefaultEpadOperations.getInstance();
		PrintWriter responseStream = null;
		int statusCode;

		httpResponse.setContentType("application/json");
		httpResponse.setHeader("Access-Control-Allow-Origin", "*");
		request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();
			if (XNATSessionOperations.hasValidXNATSessionID(httpRequest)) {
				String seriesUID = httpRequest.getParameter("series_iuid");
				if (seriesUID != null) {
					EPADDatabaseSeries epadDatabaseSeries = epadQueries.getSeries("TODO", seriesUID);
					responseStream.print(epadDatabaseSeries.toJSON());
					statusCode = HttpServletResponse.SC_OK;
				} else {
					statusCode = HandlerUtil.badRequestJSONResponse(MISSING_SERIES_IUID_MESSAGE, responseStream, log);
				}
			} else {
				statusCode = HandlerUtil.invalidTokenJSONResponse(INVALID_SESSION_TOKEN_MESSAGE, responseStream, log);
			}
			responseStream.flush();
		} catch (Throwable t) {
			statusCode = HandlerUtil.internalErrorJSONResponse(INTERNAL_EXCEPTION_MESSAGE, responseStream, log);
		}
		httpResponse.setStatus(statusCode);
	}
}
