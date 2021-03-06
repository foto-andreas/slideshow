package de.schrell.slides;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;

import javax.imageio.ImageIO;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SlideShow implements Serializable {

	private static final long serialVersionUID = 5385893771074569889L;

	private final static Logger LOGGER = LogManager.getLogger(SlideshowFallbackUI.class);

	private static final String THUMB_SUB_DIR_NAME = ".THUMB200";

	public static void createThumbnail(final ExecutorService exer, final SlideShow show, final Path sampleImage) {
		final SlideShow finalShow = show;
		exer.submit(()
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
							LOGGER.debug("Mini-Ansicht generiert: " + thumbNail.toString());
						} else {
							LOGGER.error("Fehler beim Erzeugen eines Thumbnails für " + thumbNail.toString());
						}
					}
				} catch (final Exception e) {
					LOGGER.error("Fehler beim Erzeugen eines Thumbnails.", e);
				}
			});
	}

	private final String title;

	private final MyImageSource sampleImage;

	private final TreeMap<Path, Slide> slides = new TreeMap<>();

	private final Path path;

	public SlideShow(final String title, final Path sampleImage) throws IOException {
		this.title = title;
		final BufferedImage original = ImageIO.read(sampleImage.toFile());
		final BufferedImage scaled = Scalr.resize(original, 200);
		this.sampleImage = new MyImageSource(scaled);
		path = sampleImage.getParent();
		if (!getThumbnailPath().toFile().exists()) {
			Files.createDirectory(getThumbnailPath(),
				PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx")));
		}
	}

	public synchronized void add(final Path path) {
		final Slide s = new Slide(path, this);
		slides.put(s.getCurrent(), s);
	}

	public MyImageSource getSampleImage() {
		return sampleImage;
	}

	public synchronized Collection<Slide> getSlides() {
		return slides.values();
	}

	public Path getThumbnailPath() {
		return Paths.get(path.toString(), THUMB_SUB_DIR_NAME);
	}

	public String getTitle() {
		return title;
	}

	public Slide slideAfter(final Slide slide) {
		final Entry<Path, Slide> higherEntry = slides.higherEntry(slide.getCurrent());
		return higherEntry == null ? null : higherEntry.getValue();
	}

	public Slide slideBefore(final Slide slide) {
		final Entry<Path, Slide> lowerEntry = slides.lowerEntry(slide.getCurrent());
		return lowerEntry == null ? null : lowerEntry.getValue();
	}

}
