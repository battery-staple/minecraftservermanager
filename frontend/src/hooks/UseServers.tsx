import {useCallback, useEffect, useRef, useState} from "react";
import {Server} from "../APIModels";
import {getServers, getServersWebsocket} from "../networking/backendAPI/Servers";
import {Accessed} from "../networking/backendAPI/AccessError";

/**
 * A hook that accesses all servers.
 * The list will update whenever a new server is added or removed.
 * @return an array of all servers, or an AccessError if they are not known.
 */
export function useServers(): Accessed<Server[]> {
    const [servers, setServers] = useState<Accessed<Server[]>>("loading");

    /**
     * The websocket that provides updates whenever any of the servers change (i.e., is created or deleted).
     */
    const serversWebsocket = useRef<WebSocket | null>(null);

    const setServersWebsocket = useCallback(async () => {
        serversWebsocket.current =
            await getServersWebsocket(
                (servers) => {
                    setServers(servers);
                },
                function () {
                    if (this === serversWebsocket.current) serversWebsocket.current = null;
                }
            );
    }, []);

    useEffect(() => {
        // Use REST API as fallback for websocket
        getServers()
            .then(servers => {
                if (servers !== null) {
                    setServers(servers)
                }
            });

        // noinspection JSIgnoredPromiseFromCall
        setServersWebsocket()

        return () => { // Cleanup
            serversWebsocket.current?.close()
        }
    }, [setServersWebsocket])

    return servers;
}