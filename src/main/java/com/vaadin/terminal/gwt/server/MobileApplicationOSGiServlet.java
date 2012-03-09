package com.vaadin.terminal.gwt.server;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.MalformedURLException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.service.packageadmin.ExportedPackage;
import org.osgi.service.packageadmin.PackageAdmin;

import com.vaadin.Application;
import com.vaadin.addon.touchkit.service.ApplicationIcon;
import com.vaadin.addon.touchkit.ui.TouchKitApplication;
import com.vaadin.addon.touchkit.ui.TouchKitWindow;
import com.vaadin.ui.Window;

@SuppressWarnings({ "deprecation", "serial" })
public class MobileApplicationOSGiServlet extends AbstractApplicationServlet {

	// Private fields
	private Window window;

	private Class<? extends TouchKitApplication> touchkitApplicationClass;
	private Class<? extends Application> fallbackApplicationClass;

	@SuppressWarnings("unchecked")
	@Override
	public void init(ServletConfig servletConfig) throws ServletException {

		super.init(servletConfig);

		// Loads the application class using the same class loader
		// as the servlet itself

		final String touchkitApplicationClassName = servletConfig
				.getInitParameter("application");

		if (touchkitApplicationClassName == null) {
			throw new ServletException(
					"Application not specified in servlet parameters");
		}

		final String version = getInitParameter("version");

		if (version == null) {
			throw new ServletException(
					"Bundle Version not specified in servlet parameters");
		}
		
		try {
			touchkitApplicationClass = (Class<? extends TouchKitApplication>) getApplication(
					touchkitApplicationClassName, version);

		} catch (final ClassNotFoundException e) {
			throw new ServletException("Failed to load application class: "
					+ touchkitApplicationClassName);
		}

		// Gets the application class name
		final String applicationClassName = servletConfig
				.getInitParameter("fallbackApplication");

		if (applicationClassName == null) {
			return;
		}

		try {
			fallbackApplicationClass = (Class<? extends Application>) getApplication(
					applicationClassName, version);

		} catch (final ClassNotFoundException e) {
			throw new ServletException("Failed to load application class: "
					+ applicationClassName);
		}
	}

	@Override
	protected Class<? extends Application> getApplicationClass()
			throws ClassNotFoundException {

		return this.touchkitApplicationClass;
	}
	
	
	@Override
	protected Application getNewApplication(HttpServletRequest request)
			throws ServletException {

		if (!isSupportedBrowser(request)) {
			Application app = getNewFallbackApplication(request);
			if (app != null) {
				return app;
			}
		}
		try {
			
			return getApplicationClass().newInstance();
			
		} catch (InstantiationException e) {
			throw new ServletException("getNewApplication failed", e);
		} catch (IllegalAccessException e) {
			throw new ServletException("getNewApplication failed", e);
		} catch (ClassNotFoundException e) {
			throw new ServletException("getNewApplication failed", e);
		}

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
	 * @param request
	 * @return an application instance to be used for non touchkit compatible
	 *         browsers
	 * @throws ServletException
	 */
	protected Application getNewFallbackApplication(HttpServletRequest request)
			throws ServletException {
		if (this.fallbackApplicationClass != null) {
			try {
				final Application application = getFallbackApplicationClass()
						.newInstance();

				return application;
			} catch (final IllegalAccessException e) {
				throw new ServletException("getNewFallbackApplication failed", e);
			} catch (final InstantiationException e) {
				throw new ServletException("getNewFallbackApplication failed", e);
			}
		}
		return null;
	}

	protected Class<? extends Application> getFallbackApplicationClass() {
		return this.fallbackApplicationClass;
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

	@SuppressWarnings("unchecked")
	private Class<? extends Application> getApplication(
			String className, String version) throws ClassNotFoundException {
		Version versionParam = version != null ? Version.parseVersion(version)
				: null;

		String packageName = className;
		String[] splittedString = packageName.split("\\.");

		if (splittedString.length > 0)
			packageName = packageName.substring(0, packageName.length()
					- splittedString[splittedString.length - 1].length() - 1);

		BundleContext context = FrameworkUtil.getBundle(this.getClass())
				.getBundleContext();
		ServiceReference ref = context.getServiceReference(PackageAdmin.class
				.getName());
		PackageAdmin packageAdmin = (PackageAdmin) context.getService(ref);

		ExportedPackage[] packages = packageAdmin
				.getExportedPackages(packageName);
		if (packages == null) {
			return null;
		}
		for (ExportedPackage packageImported : packages) {
			Bundle bundle = packageImported.getExportingBundle();
			if (bundle == null) {
				return null;
			}
			if (versionParam == null
					|| versionParam.equals(bundle.getVersion())) {

				return (Class<? extends Application>) bundle
						.loadClass(className);
			}

		}

		return null;

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
