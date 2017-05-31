package act.ws;

/*-
 * #%L
 * ACT Framework
 * %%
 * Copyright (C) 2014 - 2017 ActFramework
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import act.Destroyable;
import act.util.DestroyableBase;
import act.xio.WebSocketConnection;
import org.osgl.$;
import org.osgl.util.C;

import javax.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Organize websocket connection by string typed keys. Multiple connections
 * can be attached to the same key
 */
public class WebSocketConnectionRegistry extends DestroyableBase {
    private ConcurrentMap<String, List<WebSocketConnection>> registry = new ConcurrentHashMap<>();

    /**
     * Return a list of websocket connection by key
     * @param key the key to find the websocket connection list
     * @return a list of websocket connection or an empty list if no websocket connection found by key
     */
    public List<WebSocketConnection> get(String key) {
        final List<WebSocketConnection> retList = new ArrayList<>();
        accept(key, C.F.addTo(retList));
        return retList;
    }

    /**
     * Accept a visitor to iterate through the connections attached to the key specified
     *
     * @param key the key
     * @param visitor the visitor
     */
    public void accept(String key, $.Function<WebSocketConnection, ?> visitor) {
        List<WebSocketConnection> connections = registry.get(key);
        if (null == connections) {
            return;
        }
        if (!connections.isEmpty()) {
            List<WebSocketConnection> toBeCleared = null;
            for (WebSocketConnection conn : connections) {
                if (conn.closed()) {
                    if (null == toBeCleared) {
                        toBeCleared = new ArrayList<>();
                    }
                    toBeCleared.add(conn);
                    continue;
                }
                visitor.apply(conn);
            }
            if (null != toBeCleared) {
                List<WebSocketConnection> originalCopy = registry.get(key);
                originalCopy.removeAll(toBeCleared);
            }
        }
    }

    /**
     * Register a connection to the registry by key.
     *
     * Note multiple connections can be attached to the same key
     *
     * @param key the key
     * @param connection the websocket connection
     */
    public void register(String key, WebSocketConnection connection) {
        List<WebSocketConnection> connections = registry.get(key);
        if (null == connections) {
            // TODO find a better strategy to keep track of the connections
            // see http://stackoverflow.com/questions/44040637/best-practice-to-track-websocket-connections-in-java/
            List<WebSocketConnection> newConnections = new CopyOnWriteArrayList<>();
            connections = registry.putIfAbsent(key, newConnections);
            if (null == connections) {
                connections = newConnections;
            }
        }
        connections.add(connection);
    }

    /**
     * Returns the connection count in this registry.
     *
     * Note it might count connections that are closed but not removed from registry yet
     *
     * @return the connection count
     */
    public int count() {
        int n = 0;
        for (List<?> list : registry.values()) {
            n += list.size();
        }
        return n;
    }

    /**
     * Returns the connection count by key specified in this registry
     *
     * Note it might count connections that are closed but not removed from registry yet
     *
     * @param key the key
     * @return connection count by key
     */
    public int count(String key) {
        List<?> list = registry.get(key);
        return null == list ? 0 : list.size();
    }

    @Override
    protected void releaseResources() {
        for (List<WebSocketConnection> connections : registry.values()) {
            Destroyable.Util.tryDestroyAll(connections, ApplicationScoped.class);
        }
        registry.clear();
    }
}