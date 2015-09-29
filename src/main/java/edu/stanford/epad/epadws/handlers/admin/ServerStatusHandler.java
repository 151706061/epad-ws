//Copyright (c) 2015 The Board of Trustees of the Leland Stanford Junior University
//All rights reserved.
//
//Redistribution and use in source and binary forms, with or without modification, are permitted provided that
//the following conditions are met:
//
//Redistributions of source code must retain the above copyright notice, this list of conditions and the following
//disclaimer.
//
//Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//following disclaimer in the documentation and/or other materials provided with the distribution.
//
//Neither the name of The Board of Trustees of the Leland Stanford Junior University nor the names of its
//contributors (Daniel Rubin, et al) may be used to endorse or promote products derived from this software without
//specific prior written permission.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
//SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
//WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
//USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package edu.stanford.epad.epadws.handlers.admin;

import java.io.File;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.text.DecimalFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileSystemUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.dtos.TaskStatus;
import edu.stanford.epad.epadws.EPadWebServerVersion;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.models.Plugin;
import edu.stanford.epad.epadws.models.User;
import edu.stanford.epad.epadws.processing.pipeline.PipelineFactory;
import edu.stanford.epad.epadws.processing.pipeline.task.EpadStatisticsTask;
import edu.stanford.epad.epadws.security.EPADSession;
import edu.stanford.epad.epadws.security.EPADSessionOperations;
import edu.stanford.epad.epadws.service.DefaultEpadProjectOperations;
import edu.stanford.epad.epadws.service.PluginOperations;
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

		httpResponse.setContentType("text/html");
		if (request != null)					// In case handler is not called thru jetty
			request.setHandled(true);

		try {
			responseStream = httpResponse.getWriter();
			boolean validSession = SessionService.hasValidSessionID(httpRequest);
			if (validSession) {
				
				long upTime = System.currentTimeMillis() - startTime;
				long upTimeHr = upTime / (1000*60*60);
				long remain = upTime % (1000*60*60);
				long upTimeMin = remain / (1000*60);
				long upTimeSec = remain % (1000*60);
				upTimeSec = upTimeSec / 1000;
				responseStream.println("<body  topmargin='10px' leftmargin='10px' width='900px'>");
				responseStream.println("<a href=\"javascript:window.parent.location='" + httpRequest.getContextPath().replace("status/", "").replace("status", "") + "Web_pad.html'\"><b>Back to ePAD</b></a>");
				responseStream.println("<hr>");
				responseStream.println("<h3><center>ePAD Server Status</center></h3>");
				responseStream.println("<hr>");
				responseStream.println("<b>ePAD server uptime:</b> " + upTimeHr + " hrs " + upTimeMin + " mins " + upTimeSec + " secs<br>");
				responseStream.println("<br>");
			}
			if (!validSession)
				responseStream.println("Version: " + new EPadWebServerVersion().getVersion() + " Build Date: " + new EPadWebServerVersion().getBuildDate() + " Build Host: " + new EPadWebServerVersion().getBuildHost() + "<br>");
			if (validSession) {
				responseStream.println("<b>Version:</b> " + new EPadWebServerVersion().getVersion() + " <b>Build Date:</b> " + new EPadWebServerVersion().getBuildDate() + " <b>Build Host:</b> " + new EPadWebServerVersion().getBuildHost() + "<br>");
				List<Plugin> plugins = PluginOperations.getInstance().getPlugins();
				responseStream.println("<br>");
				responseStream.println("<b>Loaded Plugins:</b>");
				responseStream.println("<br><table border=1 cellpadding=2><tr style='font-weight: bold;'>" + 
						"<td align=center>ID</td><td align=center>Name</td><td align=center>Description</td>" +
						"<td align=center>Javaclass</td><td align=center>Enabled</td><td align=center>Status</td><td align=center>Modality</td></tr>");
				for (Plugin plugin: plugins)
				{
					responseStream.println("<tr><td>" + plugin.getPluginId() + "</td><td>" + plugin.getName() + "</td><td>" + plugin.getDescription() + "</td>");
					responseStream.println("<td>" + plugin.getJavaclass() + "</td><td>" + plugin.getEnabled() + "</td><td>" + plugin.getStatus() + "</td><td>" + plugin.getModality() + "</td></tr>");
				}
				responseStream.println("</table>");
					
				//responseStream.println("Plugin Version - interface:      " + EPadPlugin.PLUGIN_INTERFACE_VERSION + "<br>");
				//responseStream.println("Plugin Version - implementation: " + ePadPlugin.getPluginImplVersion() + "<br>");
				EpadDatabase epadDatabase = EpadDatabase.getInstance();
				responseStream.println("<br>");
				//responseStream.println("ePAD database startup time: " + epadDatabase.getStartupTime() + " ms, database version: " + epadDatabase.getEPADDatabaseOperations().getDBVersion() + "<br>");
				//Dcm4CheeDatabase dcm4CheeDatabase = Dcm4CheeDatabase.getInstance();
				//responseStream.println("<br>");
				//responseStream.println("DCM4CHEE database startup time: " + dcm4CheeDatabase.getStartupTime() + " ms" + "<br>");
				//responseStream.println("<br>");
				responseStream.println("<b>Config Server:</b> " + EPADConfig.xnatServer + "<br>");
				responseStream.println("<b>Config serverProxy:</b> " + EPADConfig.getParamValue("serverProxy") + "<br>");
				responseStream.println("<b>Config webserviceBase:</b> " + EPADConfig.getParamValue("webserviceBase") + "<br>");
				responseStream.println("<b>Hostname:</b> " + InetAddress.getLocalHost().getHostName() + "<br>");
				responseStream.println("<b>IP Address:</b> " + EpadStatisticsTask.getIPAddress() + "<br>");
				responseStream.println("<br>");
				String sessionID = SessionService.getJSessionIDFromRequest(httpRequest);
				String username = EPADSessionOperations.getSessionUser(sessionID);
				User user = DefaultEpadProjectOperations.getInstance().getUser(username);
				if (user.isAdmin()) {
					try {
						DecimalFormat df = new DecimalFormat("###,###,###");
						responseStream.println("<b>dcm4chee Free Space: </b>" + df.format(FileSystemUtils.freeSpaceKb(EPADConfig.dcm4cheeDirRoot)/1024) + " Mb<br>");
						responseStream.println("<b>ePad Free Space: </b>" + df.format(FileSystemUtils.freeSpaceKb(EPADConfig.getEPADWebServerBaseDir())/1024) + " Mb<br>");
						responseStream.println("<b>Tmp Free Space: </b>" + df.format(FileSystemUtils.freeSpaceKb(System.getProperty("java.io.tmpdir"))/1024) + " Mb (Max Upload)<br><br>");
					} catch (Exception x) {}
					responseStream.println("<b>Current Sessions: </b>" + "<br>");
					Map<String, EPADSession> sessions = EPADSessionOperations.getCurrentSessions();
					for (String id: sessions.keySet()) {
						EPADSession session = sessions.get(id);
						responseStream.println("&nbsp;&nbsp;&nbsp;<b>User:</b> " + session.getUsername() + " <b>Started:</b> " + session.getCreatedTime() + "<br>");
					}
					Collection<User> users = DefaultEpadProjectOperations.getUserCache();
					responseStream.println("<br><b>Background Tasks: </b>");
					responseStream.println("<br><table border=1 cellpadding=2><tr style='font-weight: bold;'><td align=center>User</td><td align=center>Task</td><td align=center>Target</td><td align=center>Status</td><td align=center>Start</td><td align=center>Complete</td></tr>");
					for (User u: users)
					{
						Collection<TaskStatus> tss = u.getCurrentTasks().values();
						for (TaskStatus ts: tss)
						{
							responseStream.println("<tr><td>" + u.getUsername() + "</td><td>" + ts.type + "</td><td>" + ts.target + "</td><td>" + ts.status + "</td><td>" + ts.starttime + "</td><td>" + ts.completetime + "</td></tr>");
						}
						if (tss.size() == 0) {
							responseStream.println("<tr><td colspan=100% align=center>No background processes running for " + u.getUsername() + "</td></tr>");
						}
					}
					responseStream.println("</table>");
				}  else {
					responseStream.println("<br><table border=1 cellpadding=2><tr style='font-weight: bold;'><td align=center>User</td><td align=center>Task</td><td align=center>Target</td><td align=center>Status</td><td align=center>Start</td><td align=center>Complete</td></tr>");
					Collection<TaskStatus> tss = user.getCurrentTasks().values();
					for (TaskStatus ts: tss)
					{
						responseStream.println("<tr><td>" + user.getUsername() + "</td><td>" + ts.type + "</td><td>" + ts.target + "</td><td>" + ts.status + "</td><td>" + ts.starttime + "</td><td>" + ts.completetime + "</td></tr>");
					}
					if (tss.size() == 0)
						responseStream.println("<tr><td colspan=100% align=center>No background processes running</td></tr>");
					responseStream.println("</table>");
				}
				responseStream.println("</body>");
				} 
				statusCode = HttpServletResponse.SC_OK;
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
