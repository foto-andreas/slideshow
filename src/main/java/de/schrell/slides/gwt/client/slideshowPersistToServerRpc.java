package de.schrell.slides.gwt.client;

import com.vaadin.shared.communication.ServerRpc;

public interface SlideshowPersistToServerRpc extends ServerRpc {
    void persistToServer();
}
