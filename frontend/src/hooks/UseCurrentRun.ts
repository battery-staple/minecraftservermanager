import {Accessed} from "../networking/backendAPI/AccessError";
import {CurrentRun, Server} from "../APIModels";
import {useCallback, useEffect, useRef, useState} from "react";
import {getCurrentRun, getCurrentRunWebsocket} from "../networking/backendAPI/CurrentRuns";

/**
 * A hook that accesses the current run for {@link server}.
 * The run will update whenever the server's current run changes (i.e., when the server starts or stops).
 * @return the current run, or null if the server is not running,
 *         or an AccessError if the running status of the server is currently unknown.
 */
export function useCurrentRun(server: Accessed<Server>): Accessed<CurrentRun | null> {
    const [currentRun, setCurrentRun] = useState<Accessed<CurrentRun | null>>(null);

    /**
     * The websocket that provides updates whenever the server's current run changes (i.e., when the server starts or stops).
     */
    const currentRunWebsocket = useRef<WebSocket | null>(null);
    const setCurrentRunWebsocket = useCallback(async (server: Server) => {
        const currentRun = await getCurrentRun(server.uuid);

        setCurrentRun(currentRun);

        currentRunWebsocket.current =
            await getCurrentRunWebsocket(
                server.runnerUUID,
                server.uuid,
                (event: MessageEvent<string>) => {
                    const currentRuns: CurrentRun[] = JSON.parse(event.data)

                    setCurrentRun(currentRuns[0] ?? null)
                },
                function () {
                    if (this === currentRunWebsocket.current) currentRunWebsocket.current = null
                }
            );
    }, []);

    /**
     * Set up and tear down the websocket when the server changes
     */
    useEffect(() => {
        if (server !== "unavailable" && server !== "loading") {
            // noinspection JSIgnoredPromiseFromCall
            setCurrentRunWebsocket(server)
        }

        return () => { // Cleanup
            currentRunWebsocket.current?.close()
        }
    }, [server, currentRunWebsocket, setCurrentRunWebsocket]);

    return currentRun
}