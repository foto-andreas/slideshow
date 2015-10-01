package de.schrell.slides;

import java.io.Serializable;
import java.nio.file.Path;

public class Slide implements Serializable {

	private static final long serialVersionUID = -8405069548840976122L;

	private final Path current;
	private final SlideShow show;

	public Slide(final Path current, final SlideShow show) {
		this.current = current;
		this.show = show;
	}

	@Override
	public boolean equals(final Object obj) {
		if (obj == getCurrent()) {
			return true;
		}
		if (!(obj instanceof Slide)) {
			return false;
		}
		final Slide other = (Slide) obj;
		return getCurrent().equals(other.getCurrent());
	}

	public Path getCurrent() {
		return current;
	}

	public Slide getNext() {
		return show.slideAfter(this);
	}

	public Path getPath() {
		return getCurrent();
	}

	public Slide getPrev() {
		return show.slideBefore(this);
	}

	@Override
	public int hashCode() {
		return getCurrent().hashCode();
	}

}