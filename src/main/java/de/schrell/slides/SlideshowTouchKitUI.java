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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import com.vaadin.event.MouseEvents;
import com.vaadin.server.FontAwesome;
import com.vaadin.server.Page;
import com.vaadin.server.StreamResource;
import com.vaadin.server.VaadinRequest;
import com.vaadin.shared.MouseEventDetails;
import com.vaadin.ui.AbstractComponent;
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.JavaScript;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

import de.schrell.versioninfo.VersionInfo;

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
public class SlideshowTouchKitUI extends UI {

	private static class FireImage extends Image {

		public FireImage(final String caption, final StreamResource resource) {
			super(caption, resource);
		}

		public void click() {
			super.fireEvent(new MouseEvents.ClickEvent(this, new MouseEventDetails()));
		}
	}

	private final static Logger LOGGER = LogManager.getLogger(SlideshowFallbackUI.class);

	private final Properties properties;

	private int windowWidth;
	private int windowHeight;

	private FireImage startSample = null;

	public SlideshowTouchKitUI() {
		super();
		properties = readProperties();
	}

	private void addClickListenerForSampleImage(final FireImage sample, final SlideShow show,
		final NavigationManager manager, final NavigationView showView, final CssLayout showContent) {
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
								LOGGER.trace("Mini-Ansicht " + thumbNail.toString() + " wird benutzt.");
							} else {
								scaled = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
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
	}

	private void addClickListenerForThumbnailImage(final Image image, final Slide slide, final NavigationManager manager) {
		image.addClickListener(event
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
					if (manager.getNextComponent() != null) {
						manager.navigateTo(manager.getNextComponent());
					} else {
						final Slide nextSlide = slide.getNext();
						if (nextSlide != null) {
							manager.navigateTo(createSwipeImage(manager, nextSlide));
						}
					}
				} catch (final Exception e) {
					popError(e);
				}
			});
	}

	private double calculateScaleFactor(final BufferedImage original) {
		final double sw = (double)windowWidth / original.getWidth();
		final double sh = (double)windowHeight / original.getHeight();
		final double scale = Math.min(1.0, sw < sh ? sw : sh);
		LOGGER.trace("scale = " + scale);
		return scale;
	}

	private void createLeftComponentInDetailView(final NavigationManager manager, final NavigationView navView) {
		final Button backward = new Button(FontAwesome.BACKWARD);
		backward.addClickListener(event -> manager.navigateBack());
		navView.setLeftComponent(backward);
	}

	private void createRightComponentInDetailView(final NavigationManager manager, final NavigationView navView,
		final Slide slide) {
		final Button forward = new Button(FontAwesome.FORWARD);
		forward.addClickListener(event -> {
			final Slide nextSlide = slide.getNext();
			if (nextSlide != null) {
				manager.navigateTo(createSwipeImage(manager, nextSlide));
			}
		});

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
	}

	private SwipeView createSwipeImage(final NavigationManager manager, final Slide slide) {
		LOGGER.debug("Erzeuge Detailbild-Ansicht: " + slide.getPath().toString());
		final Path path = slide.getPath();
		try {

			final Image image = loadImageFromPath(path);

			final SwipeView swipe = new SwipeView();
			swipe.setSizeFull();
			swipe.setData(slide);
			swipe.addStyleName("image-back");

			addClickListenerForThumbnailImage(image, slide, manager);

			final VerticalLayout layout = new VerticalLayout();
			layout.setSizeFull();
			layout.addComponent(image);
			layout.setComponentAlignment(image,
				Alignment.MIDDLE_CENTER);

			final NavigationView navView = new NavigationView(slide.getPath().getFileName().toString(), layout);
			navView.getNavigationBar().addStyleName("top-nav");

			navView.setSizeFull();
			createRightComponentInDetailView(manager, navView, slide);
			createLeftComponentInDetailView(manager, navView);

			swipe.setContent(navView);

			return swipe;

		} catch (final IOException e) {
			popError(e);
			return new SwipeView("Fehler - Siehe Logbuch.");
		}
	}

	@Override
	protected void init(final VaadinRequest request) {

		final String start = request.getParameter("show");

		LOGGER.info("Angefordertes Fotoalbum: " + start);

		Executors.newWorkStealingPool();
		final ExecutorService exer = Executors.newWorkStealingPool();

		final String folder = properties.getProperty("showdir");
		final String copyright = properties.getProperty("copyright");

		final Collection<SlideShow> slideshows = new ConcurrentLinkedQueue<>();

		LOGGER.debug("Lese das Basisverzeichnis " + folder);

		try (DirectoryStream<Path> showsStream = Files.newDirectoryStream(Paths.get(folder))) {
			final Iterator<Path> showsIter = showsStream.iterator();
			while(showsIter.hasNext()) {
				final Path showDir = showsIter.next();
				if (!showDir.toFile().isDirectory()) {
					continue;
				}
				LOGGER.debug("Lese Verzeichnis " + showDir.toString());
				registerSlideShow(slideshows, showDir, exer);
			}
		} catch (final IOException e) {
			popError(e);
		}

		final NavigationManager manager = new NavigationManager();

		manager.addNavigationListener(event
			-> {
				LOGGER.debug("onNavigation: " + event.getDirection());
				if (manager.getCurrentComponent() instanceof SwipeView) {
					switch(event.getDirection()) {
					case FORWARD:
						if (manager.getNextComponent() == null) {
							final Slide nextSlide = ((Slide) (((AbstractComponent) (manager.getCurrentComponent())).getData())).getNext();
							if (nextSlide != null) {
								manager.setNextComponent(createSwipeImage(manager, nextSlide));
							}
						}
						break;
					case BACK:
						break;
					}
				}
			});

		final CssLayout showsContent = new CssLayout();
		showsContent.addStyleName("thumb-layout");

		setWindowSize();

		Page.getCurrent().addBrowserWindowResizeListener(event -> setWindowSize());

		final CssLayout showContent = new CssLayout();
		showContent.addStyleName("thumb-layout");

		final NavigationView showsView = new NavigationView("Fotoalben", showsContent);
		showsView.getNavigationBar().addStyleName("top-nav");

		final VersionInfo versionInfo = new VersionInfo();
		final Label versionLabel = new Label("Version " + versionInfo.getVersion());
		versionLabel.addStyleName("label-small");
		showsView.setRightComponent(versionLabel);
		final Label copyrightLabel = new Label("© " + copyright);
		copyrightLabel.addStyleName("label-small");
		showsView.setLeftComponent(copyrightLabel);

		final NavigationView showView = new NavigationView("Album", showContent);
		showView.getNavigationBar().addStyleName("top-nav");

		slideshows.forEach(show
			-> {
				final FireImage sample = layoutImage(showsContent, show.getTitle(), show.getSampleImage());
				if (show.getTitle().equals(start)) {
					startSample = sample;
				}
				addClickListenerForSampleImage(sample, show, manager, showView, showContent);
			});
		manager.navigateTo(showsView);

		setContent(manager);

		getUI().access(()
			-> {
				if (startSample != null) {
					LOGGER.info("Starte Fotoalbum " + startSample.getCaption());
					startSample.click();
				}
			});

	}

	private FireImage layoutImage(final CssLayout showsContent, final String title, final MyImageSource source) {
		final StreamResource.StreamSource imagesource = source;
		final StreamResource resource = new StreamResource(imagesource, title);
		final FireImage image = new FireImage(title.replaceAll(".*_", ""), resource);
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

	private Image loadImageFromPath(final Path path) throws IOException {
		final BufferedImage original = ImageIO.read(path.toFile());
		final double scale = calculateScaleFactor(original);
		final BufferedImage scaled = Scalr.resize(original,
			(int)(scale * original.getWidth()), (int)(scale * original.getHeight()));
		final MyImageSource imgInner = new MyImageSource(scaled);
		original.flush();
		final StreamResource.StreamSource imagesource = imgInner;
		final StreamResource resource = new StreamResource(imagesource, path.getFileName().toString());
		final Image image = new Image(null, resource);
		image.addStyleName("thumb-image");
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
					navi.getNavigationBar().addStyleName("top-nav");
					navi.setSizeFull();
					final Popover pop = new Popover(navi);
					pop.setSizeFull();
					pop.setModal(true);
					final Button ok = new Button("Ok");
					ok.addClickListener(event -> pop.close());
					navi.setToolbar(ok);
					getUI().addWindow(pop);

				} catch (final IOException e1) {
					Notification.show(e1.getMessage());
					LOGGER.error(e1);
				}
			});
	}

	private Properties readProperties() {
		final Properties properties = new Properties();
		try {
			// Properties aus dem Tomcat-Environment lesen, falls definiert
			try {
				final Context initCtx = new InitialContext();
				final Context envCtx = (Context) initCtx.lookup("java:comp/env");
				final String propertiesFile = (String) envCtx.lookup("properties");
				try (FileInputStream stream = new FileInputStream(propertiesFile)) {
					properties.load(stream);
					LOGGER.info("Properties aus der Datei " + propertiesFile + " geladen.");
				}
			} catch(final NamingException e) {
				LOGGER.info("Properties sind im Kontext nicht verfügbar."); // e schlabbern
				// Properties aus dem Classpath holen
				try (InputStream stream = SlideshowFallbackUI.class.getClassLoader().getResourceAsStream("slideshow.properties")) {
					properties.load(stream);
					LOGGER.info("Properties vom Classpath geladen");
				}
			}
			// Properties im Log anlisten
			try (final ByteArrayOutputStream s = new ByteArrayOutputStream()) {
				properties.list(new PrintStream(s));
				s.flush();
				LOGGER.info("Properties:\n" + s.toString("UTF-8"));
			}
			return properties;
		} catch (final IOException e) {
			popError(e);
			return properties;
		}
	}

	private SlideShow registerSlideShow(final Collection<SlideShow> slideshows, final Path showDir,
		final ExecutorService exer) {
		SlideShow show = null;
		try (DirectoryStream<Path> imagesStream = Files.newDirectoryStream(showDir)) {
			final Iterator<Path> imagesIterator = imagesStream.iterator();
			while (imagesIterator.hasNext()) {
				final Path sampleImage = imagesIterator.next();
				final String name = sampleImage.toString().toLowerCase();
				if (name.endsWith("jpg")/* || name.endsWith("mov")*/ ) {
					LOGGER.trace("Bild gefunden " + sampleImage.toString());
					if (show == null) {
						show = new SlideShow(showDir.getFileName().toString(), sampleImage);
					} else {
						SlideShow.createThumbnail(exer, show, sampleImage);
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
		return show;
	}

	private void setWindowSize() {

		JavaScript.getCurrent().addFunction("de.schrell.sizeCallback",
			arguments -> {
				windowWidth = (int)arguments.getNumber(0) - 40;
				windowHeight =  (int)arguments.getNumber(1) - 100;
				LOGGER.debug("Browser-Fenstergröße (innen) = " + windowWidth + "x" + windowHeight);
			});

		JavaScript.getCurrent().execute(
			"de.schrell.sizeCallback(window.innerWidth, window.innerHeight);"
			);

	}

	private void swipeToImage(final NavigationManager manager, final Slide slide) {
		final SwipeView swipe = createSwipeImage(manager, slide);
		final Slide next = slide.getNext();

		manager.navigateTo(swipe);

		if (next != null) {
			manager.setNextComponent(createSwipeImage(manager, next));
			LOGGER.trace("NEXT: " + next.getPath().toString());
		}
	}
}

