package de.schrell.slides;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Collection;
import java.util.TreeMap;

import javax.imageio.ImageIO;

public class SlideShow {

	private static final String THUMB_SUB_DIR_NAME = ".THUMB200";

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
		final Slide s = new Slide(path);
		if (slides.isEmpty()) {
			s.setPrev(null);
		} else {
			s.setPrev(slides.get(slides.lastKey()));
			s.getPrev().setNext(s);
		}
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

}
