package de.schrell.slides;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.ImageIO;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.vaadin.addon.touchkit.annotations.CacheManifestEnabled;
import com.vaadin.addon.touchkit.annotations.OfflineModeEnabled;
import com.vaadin.addon.touchkit.ui.NavigationManager;
import com.vaadin.addon.touchkit.ui.NavigationView;
import com.vaadin.addon.touchkit.ui.Popover;
import com.vaadin.addon.touchkit.ui.SwipeView;
import com.vaadin.annotations.PreserveOnRefresh;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.Widgetset;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.StreamResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import de.schrell.slides.SlideShow.Slide;

/**
 * The UI's "main" class
 */
@SuppressWarnings("serial")
@Widgetset("de.schrell.slides.gwt.slideshowWidgetSet")
@Theme("myTouchkit")
// Cache static application files so as the application can be started
// and run even when the network is down.
@CacheManifestEnabled
// Switch to the OfflineMode client UI when the server is unreachable
@OfflineModeEnabled
// Make the server retain UI state whenever the browser reloads the app
@PreserveOnRefresh
public class slideshowTouchKitUI extends UI {

	private final static Logger LOGGER = LogManager.getLogger(slideshowFallbackUI.class);

	Properties properties = new Properties();

	private SwipeView createSwipeImage(final NavigationManager manager, final Slide slide) {
		LOGGER.debug("create SwipeView: " + slide.getPath().toString());
		final Path path = slide.getPath();
		BufferedImage original;
		try {
			original = ImageIO.read(path.toFile());
			final BufferedImage scaled = Scalr.resize(original, 1000);
			final MyImageSource imgInner = new MyImageSource(scaled);
			original.flush();
			final StreamResource.StreamSource imagesource = imgInner;
			final StreamResource resource = new StreamResource(imagesource, path.getFileName().toString());
			final Image imageInner = new Image(path.getFileName().toString(), resource);
			final SwipeView swipe = new SwipeView();
			swipe.setSizeFull();
			swipe.setData(slide);
			swipe.addStyleName("image-back");
			imageInner.addClickListener(event
				-> {
					if (event.isShiftKey()) {
						manager.navigateBack();
						return;
					}
					if (event.isCtrlKey()) {
						while (manager.getCurrentComponent() instanceof SwipeView) {
							manager.navigateBack();
						}
						return;
					}
					try {
						manager.navigateTo(createSwipeImage(manager, slide.getNext()));
					} catch (final Exception e) {
						popError(e);
					}
				});

			final VerticalLayout layout = new VerticalLayout();
			layout.setSizeFull();

			layout.addComponent(imageInner);

			layout.setComponentAlignment(imageInner,
				Alignment.MIDDLE_CENTER);

			final NavigationView navView = new NavigationView(slide.getPath().getFileName().toString(), layout);

			navView.setSizeFull();


			final Button forward = new Button(FontAwesome.FORWARD);
			forward.addClickListener(event -> manager.navigateTo(createSwipeImage(manager, slide.getNext())));

			final Button backward = new Button(FontAwesome.BACKWARD);
			backward.addClickListener(event -> manager.navigateBack());

			final Button home = new Button(FontAwesome.EJECT);
			home.addClickListener(event
				-> {
					while (manager.getCurrentComponent() instanceof SwipeView) {
						manager.navigateBack();
					}
				});
			final HorizontalLayout hl = new HorizontalLayout();
			hl.addComponent(home);
			hl.addComponent(forward);
			navView.setRightComponent(hl);

			navView.setLeftComponent(backward);
			swipe.setContent(navView);
			return swipe;
		} catch (final IOException e) {
			popError(e);
			return new SwipeView("ERROR");
		}
	}

	@Override
	protected void init(final VaadinRequest request) {

		readProperties();

		final String folder = properties.getProperty("showdir");

		final Collection<SlideShow> slideshows = new ConcurrentLinkedQueue<>();

		LOGGER.debug("reading from base directory " + folder);

		try (DirectoryStream<Path> showsStream = Files.newDirectoryStream(Paths.get(folder))) {
			final Iterator<Path> showsIter = showsStream.iterator();
			while(showsIter.hasNext()) {
				final Path showDir = showsIter.next();
				if (!showDir.toFile().isDirectory()) {
					continue;
				}
				LOGGER.debug("entering directory " + showDir.toString());
				SlideShow show = null;
				try (DirectoryStream<Path> imagesStream = Files.newDirectoryStream(showDir)) {
					final Iterator<Path> imagesIterator = imagesStream.iterator();
					while (imagesIterator.hasNext()) {
						final Path sampleImage = imagesIterator.next();
						if (sampleImage.toString().toLowerCase().endsWith("jpg")) {
							LOGGER.debug("seeing image " + sampleImage.toString());
							if (show == null) {
								show = new SlideShow(showDir.getFileName().toString(), sampleImage);
							}
							show.add(sampleImage);
						}
					}
					if (show != null) {
						slideshows.add(show);
					}
				} catch (final IOException e) {
					popError(e);
				}
			}
		} catch (final IOException e) {
			popError(e);
		}

		final NavigationManager manager = new NavigationManager();

		manager.addNavigationListener(event
			-> {
				LOGGER.debug("addNavigationListener: " + event.getDirection());
				if (manager.getCurrentComponent() instanceof SwipeView) {
					switch(event.getDirection()) {
					case FORWARD:
						try {
							manager.setNextComponent(createSwipeImage(manager, ((Slide) (((AbstractComponent) (manager.getCurrentComponent())).getData())).getNext()));
						} catch (final Exception e) {
							popError(e);
						}
						break;
					case BACK:
						break;
					}
				}
			});

		final CssLayout showsContent = new CssLayout();
		showsContent.addStyleName("thumb-layout");
		final CssLayout showContent = new CssLayout();
		showContent.addStyleName("thumb-layout");

		final NavigationView showsView = new NavigationView("Shows", showsContent);
		final NavigationView showView = new NavigationView("Show", showContent);

		slideshows.forEach(show
			-> {
				final Image sample = layoutImage(showsContent, show.getTitle(), show.getSampleImage());
				sample.addClickListener(event
					-> {
						showContent.removeAllComponents();
						showView.setCaption(show.getTitle());
						show.getSlides().forEach(slide
							-> {
								final Path path = slide.getPath();
								final Path thumbNail = Paths.get(show.getThumbnailPath().toString(), path.getFileName().toString());
								try {
									final BufferedImage scaled;
									if (thumbNail.toFile().exists()) {
										scaled = ImageIO.read(thumbNail.toFile());
										LOGGER.debug("using " + thumbNail.toString());
									} else {
										final BufferedImage original = ImageIO.read(path.toFile());
										scaled = Scalr.resize(original, 200);
										original.flush();
										ImageIO.write(scaled, "png", thumbNail.toFile());
										LOGGER.debug("created " + thumbNail.toString());
									}
									final MyImageSource img = new MyImageSource(scaled);
									final Image image = layoutImage(showContent, path.getFileName().toString(), img);
									image.addClickListener(click
										-> {
											swipeToImage(manager, slide);
										});

								} catch (final Exception e) {
									popError(e);
								}
							});
						manager.navigateTo(showView);
					});
			});
		manager.navigateTo(showsView);

		setContent(manager);

	}

	private Image layoutImage(final CssLayout showsContent, final String title, final MyImageSource source) {
		final StreamResource.StreamSource imagesource = source;
		final StreamResource resource = new StreamResource(imagesource, title);
		final Image image = new Image(title.replaceAll(".*_", ""), resource);
		image.addStyleName("thumb-caption");
		final HorizontalLayout box = new HorizontalLayout();
		box.setStyleName("thumb-box");
		box.setWidth(200, Unit.PIXELS);
		box.setHeight(200, Unit.PIXELS);
		box.setMargin(true);

		showsContent.addComponent(box);
		box.addComponent(image);
		box.setComponentAlignment(image, Alignment.MIDDLE_CENTER);
		return image;
	}

	private void popError(final Throwable e) {
		LOGGER.error("Fehlermeldung", e);
		getUI().access(()
			-> {
				try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
					try (PrintStream ps = new PrintStream(os)) {
						e.printStackTrace(ps);
						ps.flush();
					}
					final TextArea detailed = new TextArea("Details:");
					detailed.setSizeFull();
					detailed.setValue(os.toString("UTF-8"));
					detailed.setReadOnly(true);
					final VerticalLayout layout = new VerticalLayout(detailed);
					layout.setSizeFull();
					layout.setComponentAlignment(detailed, Alignment.MIDDLE_CENTER);
					final NavigationView navi = new NavigationView(e.getMessage(), layout);
					navi.setSizeFull();
					final Popover pop = new Popover(navi);
					pop.setSizeFull();
					pop.setModal(true);
					final Button ok = new Button("ok");
					ok.addClickListener(event -> pop.close());
					navi.setToolbar(ok);
					getUI().addWindow(pop);

				} catch (final IOException e1) {
					Notification.show(e1.getMessage());
					LOGGER.error(e1);
				}
			});
	}

	private void readProperties() {
		try {
			// Properties aus dem Tomcat-Environment lesen, falls definiert
			try {
				final Context initCtx = new InitialContext();
				final Context envCtx = (Context) initCtx.lookup("java:comp/env");
				final String propertiesFile = (String) envCtx.lookup("properties");
				try (FileInputStream stream = new FileInputStream(propertiesFile)) {
					properties.load(stream);
					LOGGER.info("loaded properties from properties file: " + propertiesFile);
				}
			} catch(final NamingException e) {
				LOGGER.info("Properties im Kontext nicht verfügbar.", e);
				// Properties aus dem Classpath holen
				try (InputStream stream = slideshowFallbackUI.class.getClassLoader().getResourceAsStream("slideshow.properties")) {
					properties.load(stream);
					LOGGER.info("loaded properties from classpath");
				}
			}
			// Properties im Log anlisten
			try (final ByteArrayOutputStream s = new ByteArrayOutputStream()) {
				properties.list(new PrintStream(s));
				s.flush();
				LOGGER.info("Properties:\n" + s.toString("UTF-8"));
			}
		} catch (final IOException e) {
			popError(e);
		}
	}

	private void swipeToImage(final NavigationManager manager, final Slide slide) {
		final SwipeView pop = createSwipeImage(manager, slide);
		final Slide next = slide.getNext();
		manager.navigateTo(pop);

		if (next != null) {
			manager.setNextComponent(createSwipeImage(manager, next));
			LOGGER.debug("NEXT: " + next.getPath().toString());
		}
	}
}

