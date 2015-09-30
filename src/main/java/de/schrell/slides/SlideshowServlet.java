package de.schrell.slides;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;

import com.vaadin.addon.touchkit.server.TouchKitServlet;
import com.vaadin.addon.touchkit.settings.TouchKitSettings;
import com.vaadin.addon.touchkit.settings.WebAppSettings;

@SuppressWarnings("serial")
@WebServlet("/*")
public class SlideshowServlet extends TouchKitServlet {

	private final SlideshowUIProvider uiProvider = new SlideshowUIProvider();

	@Override
	protected void servletInitialized() throws ServletException {
		super.servletInitialized();

		final TouchKitSettings tksets = getTouchKitSettings();

		final WebAppSettings webAppSettings = tksets.getWebAppSettings();
		webAppSettings.setWebAppCapable(true);
		webAppSettings.setStatusBarStyle("black");

		getService().addSessionInitListener(event -> event.getSession().addUIProvider(uiProvider));
	}

}
