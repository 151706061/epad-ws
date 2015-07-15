package edu.stanford.epad.epadws.servlets;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import edu.stanford.epad.epadws.handlers.plugin.EPadPluginHandler;

public class EpadPluginServlet extends HttpServlet {

	@Override
	protected void doGet(HttpServletRequest httpRequest, HttpServletResponse httpResponse)
			throws ServletException, IOException {
		//super.doGet(httpRequest, httpResponse);
		new EPadPluginHandler().handle("", null, httpRequest, httpResponse);
	}

	@Override
	public void destroy() {
		super.destroy();
		new EPadPluginHandler().destroy();
	}

	@Override
	public void init() throws ServletException {
		super.init();
		new EPadPluginHandler().doStart();
	}

}
