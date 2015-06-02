/*
 * Copyright 2012 The Board of Trustees of the Leland Stanford Junior University.
 * Author: Daniel Rubin, Alan Snyder, Debra Willrett. All rights reserved. Possession
 * or use of this program is subject to the terms and conditions of the Academic
 * Software License Agreement available at:
 *   http://epad.stanford.edu/license/
 */
package edu.stanford.epad.epadws.handlers.admin;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.plugins.EPadPlugin;
import edu.stanford.epad.common.plugins.impl.EPadPluginImpl;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.EPadWebServerVersion;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.processing.pipeline.PipelineFactory;
import edu.stanford.epad.epadws.service.SessionService;

/**
 * <code>
 * curl -v -b JSESSIOND=<id> -X GET "http://<ip>:<port>/epad/status/"
 * </code>
 * 
 * @author martin
 */
public class ServerStatusHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String INVALID_SESSION_TOKEN_MESSAGE = "Session token is invalid on status route";
	private static final String INTERNAL_EXCEPTION_MESSAGE = "Internal error getting server status";

	private static Long startTime = null;

	public ServerStatusHandler()
	{
		if (startTime == null) startTime = System.currentTimeMillis();
	}

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		PrintWriter responseStream = null;
		int statusCode;

		httpResponse.setContentType("text/plain");
		if (request != null)					// In case handler is not called thru jetty
			request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();

			if (SessionService.hasValidSessionID(httpRequest)) {
				
				EPadPlugin ePadPlugin = new EPadPluginImpl();
				long upTime = System.currentTimeMillis() - startTime;
				long upTimeSec = upTime / 1000;

				responseStream.println();
				responseStream.println("--------------  ePAD Server Status  --------------");
				responseStream.println();
				responseStream.println("ePAD server uptime: " + upTimeSec + " second(s)");
				responseStream.println();
				responseStream.println("Version: " + new EPadWebServerVersion().getVersion() + " Build Date: " + new EPadWebServerVersion().getBuildDate() + " Build Host: " + new EPadWebServerVersion().getBuildHost());
				responseStream.println();
				responseStream.println("Plugin Version - interface:      " + EPadPlugin.PLUGIN_INTERFACE_VERSION);
				responseStream.println("Plugin Version - implementation: " + ePadPlugin.getPluginImplVersion());
				EpadDatabase epadDatabase = EpadDatabase.getInstance();
				responseStream.println();
				responseStream.println("ePAD database startup time: " + epadDatabase.getStartupTime() + " ms, database version: " + epadDatabase.getEPADDatabaseOperations().getDBVersion());
				Dcm4CheeDatabase dcm4CheeDatabase = Dcm4CheeDatabase.getInstance();
				responseStream.println();
				responseStream.println("DCM4CHEE database startup time: " + dcm4CheeDatabase.getStartupTime() + " ms");
				responseStream.println();
				responseStream.println("Pipeline activity level: " + getPipelineActivityLevel());

				statusCode = HttpServletResponse.SC_OK;
			} else {
				log.warning(INVALID_SESSION_TOKEN_MESSAGE);
				statusCode = HandlerUtil.invalidTokenResponse(INVALID_SESSION_TOKEN_MESSAGE, responseStream, log);
			}
		} catch (Throwable t) {
			log.warning(INTERNAL_EXCEPTION_MESSAGE, t);
			statusCode = HandlerUtil.internalErrorResponse(INTERNAL_EXCEPTION_MESSAGE, responseStream, log);
		}
		httpResponse.setStatus(statusCode);
	}

	private String getPipelineActivityLevel()
	{
		PipelineFactory pipelineFactory = PipelineFactory.getInstance();
		StringBuilder sb = new StringBuilder();
		int activityLevel = pipelineFactory.getActivityLevel();
		int errCount = pipelineFactory.getErrorFileCount();
		if (activityLevel == 0) {
			sb.append("idle.");
		} else {
			sb.append("active-" + activityLevel);
			if (errCount > 0) {
				sb.append(" errors-" + errCount);
			}
		}
		return sb.toString();
	}
}
