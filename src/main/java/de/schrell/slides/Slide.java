package de.schrell.slides;

import java.nio.file.Path;

public class Slide {

	private final Path current;
	private Slide prev = null;
	private Slide next = null;

	public Slide(final Path current) {
		this.current = current;
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
		return next;
	}

	public Path getPath() {
		return getCurrent();
	}

	public Slide getPrev() {
		return prev;
	}

	@Override
	public int hashCode() {
		return getCurrent().hashCode();
	}

	public void setNext(final Slide next) {
		this.next = next;
	}

	public void setPrev(final Slide prev) {
		this.prev = prev;
	}
}