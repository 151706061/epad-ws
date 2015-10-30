package edu.stanford.epad.epadws.handlers.session;

//Copyright (c) 2014 The Board of Trustees of the Leland Stanford Junior University
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

import java.io.PrintWriter;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.security.EPADSessionOperations;
import edu.stanford.epad.epadws.security.EPADSessionOperations.EPADSessionResponse;
import edu.stanford.epad.epadws.service.DefaultEpadProjectOperations;
import edu.stanford.epad.epadws.service.EpadProjectOperations;
import edu.stanford.epad.epadws.service.SessionService;

/**
 * Handler for EPAD-based session management.
 * <p>
 * To create a session key:
 * <p>
 * <code>curl -v -u [username:password] -X POST http://[host:port]/epad/session/ </code>
 * <p>
 * Returns a session key.
 * <p>
 * To deactivate that key:
 * <p>
 * <code>curl -v -b JSESSIONID=[session_key] -X DELETE http://[host:port]/epad/session/</code>
 * 
 * @author dev
 */
public class EPADSessionHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();
	private final EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();

	private static final String MISSING_USER = "Missing user name";
	private static final String INVALID_METHOD_MESSAGE = "Only POST and DELETE methods valid for this route";
	private static final String LOGIN_EXCEPTION_MESSAGE = "Warning: internal login error";
	private static final String LOGOUT_EXCEPTION_MESSAGE = "Warning: internal logout error";
	private static final String UNEXPECTED_XNAT_RESPONSE_MESSAGE = "Warning: unexpected response code from XNAT";
	private static final String UNAUTHORIZED_USER_XNAT_RESPONSE_MESSAGE = "Invalid username or password";
	private static final String DISABLED_USER = "User has been disabled";
	private static final String JSESSIONID_COOKIE = "JSESSIONID";
	private static final String LOGGEDINUSER_COOKIE = "ePADLoggedinUser";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		String origin = httpRequest.getHeader("Origin");
		int statusCode;

		if (request != null)
			request.setHandled(true);
		httpResponse.setContentType("text/plain");

		String method = httpRequest.getMethod();
		log.info("Session request from client " + method + ", s:" + s + ", origin:" + origin + ", params:" + httpRequest.getQueryString());
		if ("POST".equalsIgnoreCase(method)) {
			String username = SessionService.extractUserNameFromAuthorizationHeader(httpRequest);
			boolean formpost = false;
			if (username == null || username.length() == 0)
			{
				username = httpRequest.getParameter("username");
				formpost = true;
			}
			String host = httpRequest.getParameter("hostname");
			String ip = httpRequest.getParameter("hostip");
			if (ip == null && host == null)
			{
				ip = httpRequest.getRemoteAddr();
				host = httpRequest.getRemoteHost();
			}
			if (username.length() != 0) {
				log.info("Login Request, User:" + username  + " hostname:" + host +" ip:" + ip + " origin:" + origin);
				log.info("From httpRequest hostname:" + httpRequest.getRemoteHost() +" ip:" + httpRequest.getRemoteAddr());
				try {
					EPADSessionResponse sessionResponse = SessionService.authenticateUser(httpRequest);
					if (sessionResponse.statusCode == HttpServletResponse.SC_OK) {
						projectOperations.createEventLog(username, null, null, null, null, null, null, null, "User Logged In",  ip + " " + host, false);
						String jsessionID = sessionResponse.response;
						log.info("Successful login to EPAD; SESSIONID=" + jsessionID + " host:" + host + " ip:" + ip + " host from request:" + httpRequest.getRemoteHost() + "-" + httpRequest.getRemoteAddr());
						EPADSessionOperations.setSessionHost(jsessionID, host, ip);
				    	if (formpost)
				    	{
				            Cookie userName = new Cookie(LOGGEDINUSER_COOKIE, username);
				            userName.setMaxAge(8*3600);
				            //userName.setPath("/epad/; Secure; HttpOnly");
				            userName.setPath(httpRequest.getContextPath().replace("session/", "").replace("session", ""));
				            httpResponse.addCookie(userName);
							//log.info("Setting HttpOnly, Secure cookie =" + jsessionID);
				            Cookie sessionCookie = new Cookie(JSESSIONID_COOKIE, jsessionID);
				            sessionCookie.setMaxAge(8*3600);
				            //sessionCookie.setPath("/epad/; Secure; HttpOnly");
				            String contextPath = httpRequest.getContextPath().replace("session/", "").replace("session", "");
				            sessionCookie.setPath(contextPath);
				            httpResponse.addCookie(sessionCookie);
				            String home = contextPath;
				            if (!home.endsWith("/")) home = home + "/";
				            home =  home  + EPADConfig.getParamValue("HomePage", "Web_pad.html");
				            String redirectUrl = httpRequest.getParameter("redirectUrl");
				            if (redirectUrl != null)
					    		httpResponse.sendRedirect(redirectUrl);
				            else
				            	httpResponse.sendRedirect(home);
				    		return;
				    	}

						httpResponse.setContentType("text/plain");
						PrintWriter responseStream = httpResponse.getWriter();
						responseStream.append(jsessionID);
						if ("true".equalsIgnoreCase(EPADConfig.getParamValue("SeparateWebServicesApp")) 
								|| "true".equals(httpRequest.getParameter("setCookies")))
						{
				            Cookie userName = new Cookie(LOGGEDINUSER_COOKIE, username);
				            userName.setMaxAge(8*3600);
				            userName.setPath(httpRequest.getContextPath().replace("session/", "").replace("session", ""));
				            httpResponse.addCookie(userName);
							log.info("Setting JSESSIONID Cookie");
				            Cookie sessionCookie = new Cookie(JSESSIONID_COOKIE, jsessionID);
				            sessionCookie.setMaxAge(8*3600);
				            sessionCookie.setPath(httpRequest.getContextPath().replace("session/", "").replace("session", ""));
				            httpResponse.addCookie(sessionCookie);
//							httpResponse.addHeader("Set-Cookie", "JSESSIONID=" + jsessionID);
						}
						httpResponse.setHeader("Access-Control-Allow-Origin", "*");
						//httpResponse.addHeader("Access-Control-Allow-Origin", origin);
						httpResponse.addHeader("Access-Control-Allow-Credentials", "true");
						log.info("Successful login to EPAD; JSESSIONID=" + jsessionID);
						statusCode = HttpServletResponse.SC_OK;
				    	
					} else if (sessionResponse.statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
						projectOperations.createEventLog(username, null, null, null, null, null, null, null, "User Logged Failed",  ip + " " + host, true);
						PrintWriter responseStream = httpResponse.getWriter();
						if (sessionResponse.message != null && sessionResponse.message.contains("disabled"))
							statusCode = HandlerUtil.invalidTokenResponse(DISABLED_USER, responseStream, log);
						else
						statusCode = HandlerUtil.invalidTokenResponse(UNAUTHORIZED_USER_XNAT_RESPONSE_MESSAGE, responseStream, log);
					} else {
						PrintWriter responseStream = httpResponse.getWriter();
						statusCode = HandlerUtil.warningResponse(sessionResponse.statusCode, UNEXPECTED_XNAT_RESPONSE_MESSAGE
								+ ";statusCode = " + sessionResponse.statusCode, responseStream, log);
					}
				} catch (Throwable t) {
					statusCode = HandlerUtil.internalErrorResponse(LOGIN_EXCEPTION_MESSAGE, t, log);
				}
			} else {
				statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_BAD_REQUEST, MISSING_USER, log);
			}
		} else if ("DELETE".equalsIgnoreCase(method)) {
			log.info("Logout request, sessionId:" + SessionService.getJSessionIDFromRequest(httpRequest)  + " origin:" + origin);
			try {
				String jsessionID = SessionService.getJSessionIDFromRequest(httpRequest);
				String username = null;
				if (jsessionID != null)
					username = EPADSessionOperations.getSessionUser(jsessionID);
				statusCode = SessionService.invalidateSessionID(httpRequest);
				httpResponse.setHeader("Access-Control-Allow-Origin", "*");
				httpResponse.setHeader("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
				//httpResponse.addHeader("Access-Control-Allow-Origin", "*");
				if ("true".equalsIgnoreCase(EPADConfig.getParamValue("SeparateWebServicesApp")))
				{
		            Cookie sessionCookie = new Cookie(JSESSIONID_COOKIE, "");
		            sessionCookie.setMaxAge(0);
		            sessionCookie.setPath(httpRequest.getContextPath() + "/");
		            httpResponse.addCookie(sessionCookie);
		            sessionCookie = new Cookie(JSESSIONID_COOKIE, "");
		            sessionCookie.setMaxAge(0);
		            sessionCookie.setPath(httpRequest.getContextPath());
		            httpResponse.addCookie(sessionCookie);
				}
				if (username != null)
					projectOperations.createEventLog(username, null, null, null, null, null, null, null, "User Logged Out", null, false);
				log.info("Delete session returns status code " + statusCode);
				statusCode = HttpServletResponse.SC_OK;
			} catch (Throwable t) {
				statusCode = HandlerUtil.internalErrorResponse(LOGOUT_EXCEPTION_MESSAGE, t, log);
			}
		} else if ("GET".equalsIgnoreCase(method)) {
			log.info("GET request, sessionId:" + SessionService.getJSessionIDFromRequest(httpRequest));
			try {
				statusCode = HttpServletResponse.SC_OK;
			} catch (Throwable t) {
				statusCode = HandlerUtil.internalErrorResponse(LOGOUT_EXCEPTION_MESSAGE, t, log);
			}
		} else if ("OPTIONS".equalsIgnoreCase(method)) {
			log.info("CORS preflight OPTIONS request to session route" + " origin:" + origin);
			//httpResponse.setHeader("Access-Control-Allow-Origin", origin);
			httpResponse.setHeader("Access-Control-Allow-Origin", "*");
			//httpResponse.addHeader("Access-Control-Allow-Origin", "*");
			httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
			httpResponse.setHeader("Access-Control-Allow-Headers", "Authorization");
			httpResponse.setHeader("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
			statusCode = HttpServletResponse.SC_OK;
		} else {
			log.info("Request, Method:" + method  + " origin:" + origin);
			httpResponse.setHeader("Access-Control-Allow-Origin", "*");
			httpResponse.setHeader("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
			statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_METHOD_NOT_ALLOWED, INVALID_METHOD_MESSAGE
					+ "; got " + method, log);
		}
		log.info("Status returned to client:" + statusCode);
		httpResponse.setStatus(statusCode);
	}
}