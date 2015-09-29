package de.schrell.slides.gwt.client;

import com.vaadin.shared.communication.ServerRpc;

public interface slideshowPersistToServerRpc extends ServerRpc {
    void persistToServer();
}
