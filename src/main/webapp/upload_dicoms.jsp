<%@ page language="java"%>
<%@ page import="java.io.*"%>
<%@ page import="java.util.*"%>
<%@ page import="edu.stanford.epad.common.util.*"%>
<%@ page import="edu.stanford.epad.epadws.handlers.*"%>
<%@ page import="edu.stanford.epad.epadws.service.*"%>
<%@ page import="edu.stanford.epad.epadws.security.*"%>
<%@ page session="false" %><%
	String sessionID = SessionService.getJSessionIDFromRequest(request);
	String username = EPADSessionOperations.getSessionUser(sessionID);
	String projectID = request.getParameter("projectID");
	if (projectID == null)
	{
%><script> alert('ProjectID is null');</script><%
		return;
	}
	String subjectID = null;
	String studyUID = null;
	String seriesUID = null;
	String requestContentType = request.getContentType();
	if (requestContentType != null && requestContentType.startsWith("multipart/form-data"))
	{
		PrintWriter responseStream = response.getWriter();
		String uploadDirPath = EPADConfig.getEPADWebServerUploadDir() + "temp" + Long.toString(System.currentTimeMillis());
		String projectName = "XNATProjectName=" + projectID;
		String sessionName = "XNATSessionID=" + sessionID;
		String userName = "XNATUserName=" + username;

		FileWriter fw = null;
		try {
			File uploadDir = new File(uploadDirPath);
			uploadDir.mkdirs();
			File xnatProps = new File(uploadDirPath, "xnat_upload.properties");
			fw = new FileWriter(xnatProps);
			fw.write(projectName + "\n");
			fw.write(sessionName + "\n");
			fw.write(userName + "\n");
		}
		catch (Exception x)
		{
			System.out.println("Error writing xnat file");
			x.printStackTrace();
		}
		finally 
		{
			if (fw != null) fw.close();
		}
		
		Map<String, Object> paramData = HandlerUtil.parsePostedData(uploadDirPath, request, responseStream);
		subjectID = (String) paramData.get("subjectID");
		studyUID = (String) paramData.get("studyUID");
		seriesUID = (String) paramData.get("seriesUID");
	}
	else
	{
		subjectID = request.getParameter("subjectID");
		studyUID = request.getParameter("studyUID");
		seriesUID = request.getParameter("seriesUID");
	}
%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">
<HTML>
<HEAD>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<meta charset="UTF-8">
<TITLE>Upload Files</TITLE>
 <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/jquery/1.9.1/jquery.min.js"></script>
</HEAD>

<BODY bgcolor=white>
<div id=imagelist></div>
<br>
<% if (subjectID == null) { %>
 <form name="uploadform" id="uploadform" method="post" enctype="multipart/form-data" accept-charset=utf-8>
 Project: <input type=text readonly name="projectID" value="<%=projectID%>" size=10><br>
<% } else if (studyUID == null) { %>
 <form name="uploadform" id="uploadform"  method="post" enctype="multipart/form-data" accept-charset=utf-8>
 Project: <input type=text readonly name="projectID" value="<%=projectID%>"> Patient: <input type=text readonly name="subjectID" value="<%=subjectID%>"><br>
<% } else if (seriesUID == null) { %>
 <form name="uploadform" id="uploadform" method="post" enctype="multipart/form-data" accept-charset=utf-8>
 Project: <input type=text readonly name="projectID" value="<%=projectID%>"> Patient: <input type=text readonly name="subjectID" value="<%=subjectID%>"> Study: <input type=text readonly name="studyUID" value="<%=studyUID%>"><br>
<% } else { %>
 <form name="uploadform" id="uploadform" method="post" enctype="multipart/form-data" accept-charset=utf-8>
  Project: <input type=text readonly name="projectID" value="<%=projectID%>"> Patient: <input type=text readonly name="subjectID" value="<%=subjectID%>"> Study: <input type=text readonly name="studyUID" value="<%=studyUID%>"> Series: <input type=text readonly name="seriesUID" value="<%=seriesUID%>"><br>
<% } %>
 <br>
 <b>Zip Files:</b> <input type=file name=file multiple="multiple"><br>
  <br>
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<input type="button" value="Submit" onclick="uploadFiles()">
&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
  <input type="button" value="Status" onclick="status()">
</form>
<script>
var filedata;
function uploadFiles()
{
	document.getElementById("uploadform").submit();
}
function status()
{
	window.location = "<%=request.getContextPath()%>/status?Date=" + new Date();
}
</script>
</BODY>
</HTML>
