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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

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
import com.vaadin.ui.Alignment;
import com.vaadin.ui.Button;
import com.vaadin.ui.CssLayout;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Image;
import com.vaadin.ui.JavaScript;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextArea;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

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

	private final Properties properties = new Properties();
	private int windowWidth;

	private int windowHeight;
	FireImage startSample = null;

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

	private SwipeView createSwipeImage(final NavigationManager manager, final Slide slide) {
		LOGGER.debug("Erzeuge Detailbild-Ansicht: " + slide.getPath().toString());
		final Path path = slide.getPath();
		try {
			final BufferedImage original = ImageIO.read(path.toFile());
			final double sw = (double)windowWidth / original.getWidth();
			final double sh = (double)windowHeight / original.getHeight();
			final double scale = sw < sh ? sw : sh;
			LOGGER.trace("scale = " + scale);
			final BufferedImage scaled = Scalr.resize(original,
				(int)(scale * original.getWidth()), (int)(scale * original.getHeight()));
			final MyImageSource imgInner = new MyImageSource(scaled);
			original.flush();
			final StreamResource.StreamSource imagesource = imgInner;
			final StreamResource resource = new StreamResource(imagesource, path.getFileName().toString());
			final Image image = new Image(null, resource);
			final SwipeView swipe = new SwipeView();
			swipe.setSizeFull();
			swipe.setData(slide);
			swipe.addStyleName("image-back");
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
							manager.navigateTo(createSwipeImage(manager, slide.getNext()));
						}
					} catch (final Exception e) {
						popError(e);
					}
				});

			final VerticalLayout layout = new VerticalLayout();
			layout.setSizeFull();

			layout.addComponent(image);

			layout.setComponentAlignment(image,
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
			return new SwipeView("Fehler - Siehe Logbuch.");
		}
	}

	private void createThumbnail(final ThreadPoolExecutor exer, final SlideShow show, final Path sampleImage) {
		final SlideShow finalShow = show;
		exer.execute(()
			-> {
				try {
					final Path thumbNail = Paths.get(finalShow.getThumbnailPath().toString(), sampleImage.getFileName().toString());
					if (!thumbNail.toFile().exists()) {
						BufferedImage original = null;
						if (sampleImage.toString().toLowerCase().endsWith(".mov")) {
							// TODO make thumb from mov
						} else {
							original = ImageIO.read(sampleImage.toFile());
						}
						if (original != null) {
							final BufferedImage scaled = Scalr.resize(original, 200);
							original.flush();
							ImageIO.write(scaled, "png", thumbNail.toFile());
							scaled.flush();
							LOGGER.debug("Mini-Ansicht generiert: " + thumbNail.toString() +
								" [" + exer.getCompletedTaskCount() + "/" + exer.getQueue().size() + "]");
						} else {
							LOGGER.error("Fehler beim Erzeugen eines Thumbnails für " + thumbNail.toString());
						}
					}
				} catch (final Exception e) {
					LOGGER.error("Fehler beim Erzeugen eines Thumbnails.", e);
				}
			});
	}

	@Override
	protected void init(final VaadinRequest request) {

		final String start = request.getParameter("show");

		LOGGER.info("Angefordertes Fotoalbum: " + start);

		final ThreadPoolExecutor exer = new ThreadPoolExecutor(
			10, 20, 60, TimeUnit.SECONDS, new ArrayBlockingQueue<>(1_000_000));

		readProperties();

		final String folder = properties.getProperty("showdir");

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

		final CssLayout showsContent = new CssLayout();
		showsContent.addStyleName("thumb-layout");

		setWindowSize();

		Page.getCurrent().addBrowserWindowResizeListener(event -> setWindowSize());

		final CssLayout showContent = new CssLayout();
		showContent.addStyleName("thumb-layout");

		final NavigationView showsView = new NavigationView("Fotoalben", showsContent);
		final NavigationView showView = new NavigationView("Album", showContent);

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

	private void readProperties() {
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
		} catch (final IOException e) {
			popError(e);
		}
	}

	private SlideShow registerSlideShow(final Collection<SlideShow> slideshows, final Path showDir,
		final ThreadPoolExecutor exer) {
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
						createThumbnail(exer, show, sampleImage);
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
				windowWidth = arguments.getInt(0) - 40;
				windowHeight =  arguments.getInt(1) - 100;
				LOGGER.debug("Browser-Fenstergröße (innen) = " + windowWidth + "x" + windowHeight);
			});

		JavaScript.getCurrent().execute(
			"de.schrell.sizeCallback(window.innerWidth, window.innerHeight);"
			);

	}

	private void swipeToImage(final NavigationManager manager, final Slide slide) {
		final SwipeView pop = createSwipeImage(manager, slide);
		final Slide next = slide.getNext();

		manager.navigateTo(pop);

		if (next != null) {
			manager.setNextComponent(createSwipeImage(manager, next));
			LOGGER.trace("NEXT: " + next.getPath().toString());
		}
	}
}

