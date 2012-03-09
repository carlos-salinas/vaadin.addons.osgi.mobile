package com.vaadin.terminal.gwt.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.springframework.context.ApplicationContext;

import com.vaadin.Application;
import com.vaadin.addon.touchkit.service.ApplicationIcon;
import com.vaadin.addon.touchkit.ui.TouchKitWindow;
import com.vaadin.ui.Window;

@SuppressWarnings("serial")
public class MobileSpringApplicationOSGiServlet extends
		AbstractApplicationServlet {

	// Private fields
	private Window window;

	private String beanParam;
	private String fallbackbeanParam;

	private Version version;

	@Override
	public void init(ServletConfig servletConfig) throws ServletException {

		super.init(servletConfig);

		final String version = getInitParameter("version");

		if (version == null) {
			// TODO: Write a warning, recommends to specify the bundle version
		} else {

			this.version = new Version(version);
		}

		beanParam = servletConfig.getInitParameter("bean");

		if (beanParam == null) {
			throw new ServletException(
					"Bean Name not specified in servlet parameters");
		}

		fallbackbeanParam = servletConfig.getInitParameter("fallback-bean");

	}

	@Override
	protected Application getNewApplication(HttpServletRequest request)
			throws ServletException {

		// Creates a new application instance
		try {
			// Retrieve the Spring ApplicationContext registered as a service
			ApplicationContext springContext = getApplicationContext();

			if (!isSupportedBrowser(request)) {
				Application app = getNewFallbackApplication(springContext);
				if (app != null) {
					return app;
				}
			}

			if (!springContext.containsBean(beanParam)) {

				throw new ClassNotFoundException(
						"No application bean found under name " + beanParam);
			}

			final Application application = (Application) springContext
					.getBean(beanParam);

			return application;

		} catch (ClassNotFoundException e) {
			throw new ServletException("getNewApplication failed", e);
		}

	}

	private Application getNewFallbackApplication(
			ApplicationContext springContext) throws ServletException,
			ClassNotFoundException {

		if (fallbackbeanParam == null) {
			throw new ServletException(
					"Bean Name for the fallback application not specified in servlet parameters");
		}

		if (!springContext.containsBean(fallbackbeanParam)) {

			throw new ClassNotFoundException(
					"No application bean found under name " + beanParam);
		}

		return (Application) springContext.getBean(fallbackbeanParam);

	}

	@Override
	protected Class<? extends Application> getApplicationClass()
			throws ClassNotFoundException {
		ApplicationContext springContext = getApplicationContext();

		if (!springContext.containsBean(beanParam)) {

			throw new ClassNotFoundException(
					"No application bean found under name " + beanParam);
		}

		return (Class<? extends Application>) springContext.getBean(beanParam)
				.getClass();
	}

	@Override
	protected void writeAjaxPage(HttpServletRequest request,
			HttpServletResponse response, Window window, Application application)
			throws IOException, MalformedURLException, ServletException {

		this.window = window;
		String fallbackWidgetset = getWidgetset(request, window);
		if (fallbackWidgetset != null) {
			request.setAttribute(REQUEST_WIDGETSET, fallbackWidgetset);
		}

		super.writeAjaxPage(request, response, window, application);
		this.window = null;
	}

	@Override
	protected void writeAjaxPageHtmlHeader(BufferedWriter page, String title,
			String themeUri, HttpServletRequest request) throws IOException {
		super.writeAjaxPageHtmlHeader(page, title, themeUri, request);
		if (window != null && window instanceof TouchKitWindow) {
			TouchKitWindow w = (TouchKitWindow) window;

			boolean viewportOpen = false;
			if (w.getViewPortWidth() != null) {
				viewportOpen = prepareViewPort(viewportOpen, page);
				page.write("width=" + w.getViewPortWidth());
			}
			if (w.isViewPortUserScalable() != null) {
				viewportOpen = prepareViewPort(viewportOpen, page);
				page.write("user-scalable="
						+ (w.isViewPortUserScalable() ? "yes" : "no"));
			}
			if (w.getViewPortInitialScale() != null) {
				viewportOpen = prepareViewPort(viewportOpen, page);
				page.write("initial-scale=" + w.getViewPortInitialScale());
			}
			if (w.getViewPortMaximumScale() != null) {
				viewportOpen = prepareViewPort(viewportOpen, page);
				page.write("maximum-scale=" + w.getViewPortMaximumScale());
			}

			if (w.getViewPortMinimumScale() != null) {
				viewportOpen = prepareViewPort(viewportOpen, page);
				page.write("minimum-scale=" + w.getViewPortMinimumScale());
			}
			if (viewportOpen) {
				closeSingleElementTag(page);
			}

			boolean webAppCapable = w.isWebAppCapable();
			if (webAppCapable) {
				page.write("<meta name=\"apple-mobile-web-app-capable\" "
						+ "content=\"yes\" />\n");
			}

			if (w.getStatusBarStyle() != null) {
				page.append("<meta name=\"apple-mobile-web-app-status-bar-style\" "
						+ "content=\"" + w.getStatusBarStyle() + "\" />\n");
			}
			ApplicationIcon[] icons = w.getApplicationIcons();
			for (int i = 0; i < icons.length; i++) {
				ApplicationIcon icon = icons[i];
				page.write("<link rel=\"apple-touch-icon\" ");
				if (icon.getSizes() != null) {
					page.write("sizes=\"");
					page.write(icon.getSizes());
					page.write("\"");
				}
				page.write(" href=\"");
				page.write(icon.getHref());
				closeSingleElementTag(page);
			}
			if (w.getStartupImage() != null) {
				page.append("<link rel=\"apple-touch-startup-image\" "
						+ "href=\"" + w.getStartupImage() + "\" />");
			}

		}
	}

	/**
	 * Return a possible custom widgetset for a window. The default behavior is
	 * to return fallbackWidgetset init parameter for non TouchKitWindow's.
	 * 
	 * @param request
	 * @param window
	 * @return
	 */
	protected String getWidgetset(HttpServletRequest request, Window window) {
		if (!(window instanceof TouchKitWindow)) {
			String widgetset = getApplicationProperty("fallbackWidgetset");
			return widgetset;
		}
		return null;
	}

	/**
	 * Method detects whether the main application is supported by the TouchKit
	 * application. It controls whether an optional fallback application should
	 * be served for the end user. By default the method just ensures that the
	 * browser is webkit based.
	 * 
	 * @param request
	 * @return true if the normal application should be served
	 */
	protected boolean isSupportedBrowser(HttpServletRequest request) {
		String header = request.getHeader("User-Agent");
		return !(header == null || !header.toLowerCase().contains("webkit"));
	}

	private ApplicationContext getApplicationContext()
			throws ClassNotFoundException {
		BundleContext context = FrameworkUtil.getBundle(this.getClass())
				.getBundleContext();

		ServiceReference[] refs = null;
		try {
			refs = context.getServiceReferences(
					ApplicationContext.class.getName(), null);
		} catch (InvalidSyntaxException e) {
			e.printStackTrace();
		}

		boolean checkBundleVersion = this.version != null;

		ApplicationContext springContext = null;
		if (refs != null && refs.length > 0) {

			for (ServiceReference<ApplicationContext> ref : refs) {
				springContext = (ApplicationContext) context
						.getService(ref);
				if (springContext.containsBean(beanParam)) {
					if (checkBundleVersion) {
						Version bundleVersion = (Version) ref.getProperty("Bundle-Version");
						if (this.version.equals(bundleVersion)) {
							return springContext;
						}
					} else {
						return springContext;
					}
				}
			}
		}

		throw new ClassNotFoundException(
				"Spring ApplicationContext has not been registered");

	}

	private void closeSingleElementTag(BufferedWriter page) throws IOException {
		page.write("\" />\n");
	}

	private boolean prepareViewPort(boolean viewportOpen, BufferedWriter page)
			throws IOException {
		if (viewportOpen) {
			page.write(", ");
		} else {
			page.write("\n<meta name=\"viewport\" content=\"");
		}
		return true;
	}

}
