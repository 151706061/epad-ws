package edu.stanford.epad.epadws.handlers.search;

import java.io.PrintWriter;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.dtos.EPADImageList;
import edu.stanford.epad.dtos.EPADProjectList;
import edu.stanford.epad.dtos.EPADSeriesList;
import edu.stanford.epad.dtos.EPADStudyList;
import edu.stanford.epad.dtos.EPADSubjectList;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.queries.DefaultEpadQueries;
import edu.stanford.epad.epadws.queries.EpadQueries;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;

/**
 * @author martin
 */
public class EPADSearchHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String PROJECT_LIST_TEMPLATE = "/projects/";
	private static final String PROJECT_TEMPLATE = PROJECT_LIST_TEMPLATE + "{project}";
	private static final String SUBJECT_LIST_TEMPLATE = PROJECT_TEMPLATE + "/subjects/";
	private static final String SUBJECT_TEMPLATE = SUBJECT_LIST_TEMPLATE + "{subject}";
	private static final String STUDY_LIST_TEMPLATE = SUBJECT_TEMPLATE + "/studies/";
	private static final String STUDY_TEMPLATE = STUDY_LIST_TEMPLATE + "/studies/{study}";
	private static final String SERIES_LIST_TEMPLATE = STUDY_TEMPLATE + "/series/";
	private static final String SERIES_TEMPLATE = STUDY_TEMPLATE + "/series/{series}";
	private static final String IMAGE_LIST_TEMPLATE = SERIES_TEMPLATE + "/images/";
	// private static final String IMAGE_TEMPLATE = SERIES_TEMPLATE + "/images/{image}";

	private static final String BAD_REQUEST_MESSAGE = "Bad request on search route";
	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error running query on search route";
	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid on search route";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		int statusCode;

		httpResponse.setContentType("application/json");
		request.setHandled(true);

		log.info("ePADSearch path=" + httpRequest.getPathInfo() + ", query=" + httpRequest.getQueryString());

		try {
			PrintWriter responseStream = httpResponse.getWriter();

			if (XNATSessionOperations.hasValidXNATSessionID(httpRequest)) {
				EpadQueries epadQueries = DefaultEpadQueries.getInstance();
				String jsessionID = XNATSessionOperations.getJSessionIDFromRequest(httpRequest);
				EPADSearchFilter searchFilter = EPADSearchFilterBuilder.build(httpRequest);
				String username = httpRequest.getParameter("username");
				String pathInfo = httpRequest.getPathInfo();

				if (HandlerUtil.matchesTemplate(PROJECT_LIST_TEMPLATE, pathInfo)) {
					EPADProjectList projectList = epadQueries.getAllProjectsForUser(jsessionID, username, searchFilter);
					responseStream.append(projectList.toJSON());
				} else if (HandlerUtil.matchesTemplate(SUBJECT_LIST_TEMPLATE, pathInfo)) {
					Map<String, String> templateMap = HandlerUtil.getTemplateMap(SUBJECT_LIST_TEMPLATE, pathInfo);
					String projectID = HandlerUtil.getTemplateParameter(templateMap, "project");
					EPADSubjectList subjectList = epadQueries.getAllSubjectsForProject(jsessionID, projectID, searchFilter);
					responseStream.append(subjectList.toJSON());
				} else if (HandlerUtil.matchesTemplate(STUDY_LIST_TEMPLATE, pathInfo)) {
					Map<String, String> templateMap = HandlerUtil.getTemplateMap(STUDY_LIST_TEMPLATE, pathInfo);
					String projectID = HandlerUtil.getTemplateParameter(templateMap, "project");
					String subjectID = HandlerUtil.getTemplateParameter(templateMap, "subject");
					EPADStudyList studyList = epadQueries.getAllStudiesForPatient(jsessionID, projectID, subjectID, searchFilter);
					responseStream.append(studyList.toJSON());
				} else if (HandlerUtil.matchesTemplate(SERIES_LIST_TEMPLATE, pathInfo)) {
					Map<String, String> templateMap = HandlerUtil.getTemplateMap(SERIES_LIST_TEMPLATE, pathInfo);
					String projectID = HandlerUtil.getTemplateParameter(templateMap, "project");
					String subjectID = HandlerUtil.getTemplateParameter(templateMap, "subject");
					String studyUID = HandlerUtil.getTemplateParameter(templateMap, "study");
					EPADSeriesList seriesList = epadQueries.getAllSeriesForStudy(jsessionID, projectID, subjectID, studyUID,
							searchFilter);
					responseStream.append(seriesList.toJSON());
				} else if (HandlerUtil.matchesTemplate(IMAGE_LIST_TEMPLATE, pathInfo)) {
					Map<String, String> templateMap = HandlerUtil.getTemplateMap(IMAGE_LIST_TEMPLATE, pathInfo);
					String projectID = HandlerUtil.getTemplateParameter(templateMap, "project");
					String subjectID = HandlerUtil.getTemplateParameter(templateMap, "subject");
					String studyUID = HandlerUtil.getTemplateParameter(templateMap, "study");
					String seriesUID = HandlerUtil.getTemplateParameter(templateMap, "series");
					EPADImageList imageList = epadQueries.getAllImagesForSeries(jsessionID, projectID, subjectID, studyUID,
							seriesUID, searchFilter);
					responseStream.append(imageList.toJSON());

				} else {
					statusCode = HandlerUtil.warningJSONResponse(HttpServletResponse.SC_BAD_REQUEST, BAD_REQUEST_MESSAGE, log);
				}
				responseStream.flush();
				statusCode = HttpServletResponse.SC_OK;
			} else {
				statusCode = HandlerUtil.invalidTokenJSONResponse(INVALID_SESSION_TOKEN_MESSAGE, responseStream, log);
			}
		} catch (Throwable t) {
			statusCode = HandlerUtil.internalErrorJSONResponse(INTERNAL_EXCEPTION_MESSAGE, t, log);
		}
		httpResponse.setStatus(statusCode);
	}
}
