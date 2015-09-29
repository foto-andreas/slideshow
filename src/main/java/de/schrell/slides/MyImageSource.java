package de.schrell.slides;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import com.vaadin.server.StreamResource.StreamSource;

public class MyImageSource implements StreamSource {

	private static final long serialVersionUID = -9078920569375645114L;

	private final BufferedImage bi;

	private ByteArrayOutputStream imagebuffer = null;

	private int reloads = 0;

	public MyImageSource(final BufferedImage bufferedImage) {
		bi = bufferedImage;
	}

	public int getReloads() {
		return reloads;
	}

	@Override
	public InputStream getStream () {

		reloads++;

		try {

			imagebuffer = new ByteArrayOutputStream();
			ImageIO.write(bi, "png", imagebuffer);
			return new ByteArrayInputStream(imagebuffer.toByteArray());

		} catch (final IOException e) {
			return null;
		}
	}

}