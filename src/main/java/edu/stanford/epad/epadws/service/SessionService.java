package edu.stanford.epad.epadws.service;

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

import javax.servlet.http.HttpServletRequest;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.epadws.security.EPADSessionOperations;
import edu.stanford.epad.epadws.security.EPADSessionOperations.EPADSessionResponse;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations.XNATSessionResponse;

public class SessionService {

	private static final EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();
	
	public static boolean hasValidSessionID(HttpServletRequest httpRequest) {
		if (EPADConfig.UseEPADUsersProjects) {
			return EPADSessionOperations.hasValidSessionID(httpRequest);
		} else {
			return XNATSessionOperations.hasValidXNATSessionID(httpRequest);
		}
	}
	
	public static boolean hasValidSessionID(String sessionID) {
		return hasValidSessionID(sessionID, null);
	}
	
	public static boolean hasValidSessionID(String sessionID, HttpServletRequest httpRequest) {
		if (EPADConfig.UseEPADUsersProjects) {
			return EPADSessionOperations.hasValidSessionID(sessionID, httpRequest);
		} else {
			return XNATSessionOperations.hasValidXNATSessionID(sessionID);
		}
	}
	
	public static String getJSessionIDFromRequest(HttpServletRequest httpRequest) {
		return getJSessionIDFromRequest(httpRequest, false);
	}
	
	public static String getJSessionIDFromRequest(HttpServletRequest httpRequest, boolean createNew) {
		String sessionID = null;
		if (EPADConfig.UseEPADUsersProjects) {
			sessionID = EPADSessionOperations.getJSessionIDFromRequest(httpRequest);
		} else {
			sessionID = XNATSessionOperations.getJSessionIDFromRequest(httpRequest);
		}		
		if (createNew && (sessionID == null || sessionID.length() == 0))
		{
			EPADSessionResponse response = EPADSessionOperations.authenticateUser(httpRequest);
			sessionID =  response.response;
		}
		return sessionID;
	}

	public static String extractUserNameFromAuthorizationHeader(HttpServletRequest httpRequest)
	{
		if (EPADConfig.UseEPADUsersProjects) {
			return EPADSessionOperations.extractUserNameFromAuthorizationHeader(httpRequest);
		} else {
			return XNATSessionOperations.extractUserNameFromAuthorizationHeader(httpRequest);
		}		
	}
	
	public static EPADSessionResponse authenticateUser(HttpServletRequest httpRequest)
	{
		if (EPADConfig.UseEPADUsersProjects) {
			return EPADSessionOperations.authenticateUser(httpRequest);
		} else {
			XNATSessionResponse xresponse = XNATSessionOperations.invokeXNATSessionIDService(httpRequest);
			EPADSessionResponse eresponse = new EPADSessionResponse(xresponse.statusCode, xresponse.response,"");
			return eresponse;
		}		
	}
	
	public static int invalidateSessionID(HttpServletRequest httpRequest)
	{
		if (EPADConfig.UseEPADUsersProjects) {
			return EPADSessionOperations.invalidateSessionID(httpRequest);
		} else {
			return XNATSessionOperations.invalidateXNATSessionID(httpRequest);
		}		
		
	}
	
	public static String getUsernameForSession(String sessionID)
	{
		return EPADSessionOperations.getSessionUser(sessionID);
	}
}
