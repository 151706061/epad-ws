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
package edu.stanford.epad.epadws.handlers.xnat;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.handlers.HandlerUtil;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations.XNATSessionResponse;

/**
 * Handler for XNAT-based session management.
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
 * @author martin
 */
public class XNATSessionHandler extends AbstractHandler
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String MISSING_USER = "Missing user name";
	private static final String INVALID_METHOD_MESSAGE = "Only POST and DELETE methods valid for this route";
	private static final String LOGIN_EXCEPTION_MESSAGE = "Warning: internal login error";
	private static final String LOGOUT_EXCEPTION_MESSAGE = "Warning: internal logout error";
	private static final String UNEXPECTED_XNAT_RESPONSE_MESSAGE = "Warning: unexpected response code from XNAT";
	private static final String UNAUTHORIZED_USER_XNAT_RESPONSE_MESSAGE = "Invalid username or password";

	@Override
	public void handle(String s, Request request, HttpServletRequest httpRequest, HttpServletResponse httpResponse)
	{
		String origin = httpRequest.getHeader("Origin");
		int statusCode;

		httpResponse.setContentType("text/plain");
		request.setHandled(true);

		String method = httpRequest.getMethod();
		log.info("Request from client " + method + " s:" + s);
		if ("POST".equalsIgnoreCase(method)) {
			String username = XNATSessionOperations.extractUserNameFromAuthorizationHeader(httpRequest);
			if (username.length() != 0) {
				log.info("Login request from user " + username);
				try {
					PrintWriter responseStream = httpResponse.getWriter();
					XNATSessionResponse xnatSessionResponse = XNATSessionOperations.invokeXNATSessionIDService(httpRequest);
					if (xnatSessionResponse.statusCode == HttpServletResponse.SC_OK) {
						String jsessionID = xnatSessionResponse.response;
						responseStream.append(jsessionID);
						httpResponse.addHeader("Set-Cookie", "JSESSIONID=" + jsessionID);
						httpResponse.addHeader("Set-Cookie", "ePADLoggedinUser=" + username);
						httpResponse.addHeader("Access-Control-Allow-Origin", origin);
						httpResponse.addHeader("Access-Control-Allow-Credentials", "true");
						log.info("Successful login to XNAT; JSESSIONID=" + jsessionID);
						statusCode = HttpServletResponse.SC_OK;
					} else if (xnatSessionResponse.statusCode == HttpServletResponse.SC_UNAUTHORIZED) {
						statusCode = HandlerUtil.invalidTokenResponse(UNAUTHORIZED_USER_XNAT_RESPONSE_MESSAGE, responseStream, log);
					} else {
						statusCode = HandlerUtil.warningResponse(xnatSessionResponse.statusCode, UNEXPECTED_XNAT_RESPONSE_MESSAGE
								+ ";statusCode = " + xnatSessionResponse.statusCode, responseStream, log);
					}
				} catch (Throwable t) {
					statusCode = HandlerUtil.internalErrorResponse(LOGIN_EXCEPTION_MESSAGE, t, log);
				}
			} else {
				statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_BAD_REQUEST, MISSING_USER, log);
			}
		} else if ("DELETE".equalsIgnoreCase(method)) {
			log.info("Logout request, sessionId:" + XNATSessionOperations.getJSessionIDFromRequest(httpRequest));
			try {
				int xnatStatusCode = XNATSessionOperations.invalidateXNATSessionID(httpRequest);
				log.info("XNAT delete session returns status code " + xnatStatusCode);
				statusCode = xnatStatusCode;
			} catch (Throwable t) {
				statusCode = HandlerUtil.internalErrorResponse(LOGOUT_EXCEPTION_MESSAGE, t, log);
			}
		} else if ("GET".equalsIgnoreCase(method)) {
			log.info("GET request, sessionId:" + XNATSessionOperations.getJSessionIDFromRequest(httpRequest));
			try {
				statusCode = HttpServletResponse.SC_OK;
			} catch (Throwable t) {
				statusCode = HandlerUtil.internalErrorResponse(LOGOUT_EXCEPTION_MESSAGE, t, log);
			}
		} else if ("OPTIONS".equalsIgnoreCase(method)) {
			log.info("CORS preflight OPTIONS request to session route");
			httpResponse.setHeader("Access-Control-Allow-Origin", origin);
			httpResponse.setHeader("Access-Control-Allow-Credentials", "true");
			httpResponse.setHeader("Access-Control-Allow-Headers", "Authorization");
			statusCode = HttpServletResponse.SC_OK;
		} else {
			httpResponse.setHeader("Access-Control-Allow-Methods", "POST, DELETE, OPTIONS");
			statusCode = HandlerUtil.warningResponse(HttpServletResponse.SC_METHOD_NOT_ALLOWED, INVALID_METHOD_MESSAGE
					+ "; got " + method, log);
		}
		httpResponse.setStatus(statusCode);
	}
}