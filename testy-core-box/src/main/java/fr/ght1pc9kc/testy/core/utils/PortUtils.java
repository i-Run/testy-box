package fr.ght1pc9kc.testy.core.utils;

import fr.ght1pc9kc.testy.core.exceptions.RandomPortException;
import lombok.experimental.UtilityClass;

import java.io.IOException;
import java.net.ServerSocket;

@UtilityClass
public class PortUtils {
    /**
     * Find a free random port on the local machine
     * Use {@link ServerSocket}.
     *
     * @return the free port as integer
     */
    public static int randomFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new RandomPortException("Unable to found a free port !", e);
        }
    }
}
