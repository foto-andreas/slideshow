package de.schrell.slides;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import javax.imageio.ImageIO;


public class SlideShow {

	public static class Slide {
		private final Path current;
		private Slide prev = null;
		private Slide next = null;
		public Slide(final Path current) {
			this.current = current;
		}
		public Slide getNext() {
			return next;
		}
		public Path getPath() {
			return current;
		}
		public Slide getPrev() {
			return prev;
		}

	}

	private final String title;

	private final MyImageSource sampleImage;

	private final ConcurrentLinkedDeque<Slide> slides = new ConcurrentLinkedDeque<>();

	private final Path path;

	public SlideShow(final String title, final Path sampleImage) throws IOException {
		this.title = title;
		final BufferedImage original = ImageIO.read(sampleImage.toFile());
		final BufferedImage scaled = Scalr.resize(original, 200);
		this.sampleImage = new MyImageSource(scaled);
		path = sampleImage.getParent();
		if (!getThumbnailPath().toFile().exists()) {
			Files.createDirectory(getThumbnailPath());
		}
	}

	public void add(final Path path) {
		final Slide s = new Slide(path);
		if (slides.isEmpty()) {
			s.prev = null;
		} else {
			s.prev = slides.getLast();
			s.prev.next = s;
		}
		slides.add(s);
	}

	public MyImageSource getSampleImage() {
		return sampleImage;
	}

	public Collection<Slide> getSlides() {
		final List<Slide> list = new ArrayList<>(slides.size());
		list.addAll(slides);
		Collections.sort(list, (a, b) -> a.current.compareTo(b.current));
		return list;
	}

	public Path getThumbnailPath() {
		return Paths.get(path.toString(), ".THUMB200");
	}

	public String getTitle() {
		return title;
	}

}
