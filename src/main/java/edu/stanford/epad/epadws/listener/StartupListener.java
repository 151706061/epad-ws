package edu.stanford.epad.epadws.listener;

import java.net.URL;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.WebApplicationContextUtils;

import edu.stanford.epad.epadws.Main;

/**
 * <p>StartupListener class used to initialize stuff at Startup (including Spring Context).
 */
public class StartupListener extends ContextLoaderListener
    implements ServletContextListener {
    
    private static final Log log = LogFactory.getLog(StartupListener.class);
    
    private static ApplicationContext appContext;
    private static String webAppURL;
    private static String webAppPath;
    
    public void contextInitialized(ServletContextEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("initializing context...");
        }

        // call Spring's context ContextLoaderListener to initialize
        // all the context files specified in web.xml
        super.contextInitialized(event);

        ServletContext servletContext = event.getServletContext();
       	appContext = WebApplicationContextUtils.getRequiredWebApplicationContext(servletContext);
    	webAppPath = servletContext.getRealPath("/");
    	try
    	{
        	URL url = servletContext.getResource("/");
        	webAppURL = "http:/" + url.getPath(); // Does not look correct
    		System.out.println("Context initialized , webAppUrl=" + webAppURL + " webappPath=" + webAppPath);
    	}
    	catch (Exception x) {}
		Main.initializePlugins();
		Main.startSupportThreads();
    }

	public static ApplicationContext getAppContext() {
		return appContext;
	}

	public static String getWebAppPath() {
		return webAppPath;
	}

	public static String getWebAppURL() {
		return webAppURL;
	}

}
