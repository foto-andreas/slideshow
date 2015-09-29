package de.schrell.slides;

import com.vaadin.server.UIClassSelectionEvent;
import com.vaadin.server.UIProvider;
import com.vaadin.server.VaadinSession;
import com.vaadin.ui.UI;

@SuppressWarnings("serial")
public class SlideshowUIProvider extends UIProvider {

	@Override
	public Class<? extends UI> getUIClass(final UIClassSelectionEvent event) {

		final boolean mobileUserAgent = event.getRequest().getHeader("user-agent")
			.toLowerCase().contains("mobile");
		final boolean mobileParameter = event.getRequest().getParameter("mobile") != null;

		if (overrideMobileUA() || mobileUserAgent || mobileParameter) {
			return SlideshowTouchKitUI.class;
		} else {
			//TODO            return slideshowFallbackUI.class;
			return SlideshowTouchKitUI.class;
		}
	}

	private boolean overrideMobileUA() {
		final VaadinSession session = VaadinSession.getCurrent();
		return session != null && session.getAttribute("mobile") != null;
	}
}
