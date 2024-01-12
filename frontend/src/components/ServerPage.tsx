import React, {useCallback, useEffect, useRef, useState} from "react";
import {CurrentRun, Server} from "../APIModels";
import {useAutoAnimate} from "@formkit/auto-animate/react";
import {
    AccessError,
    getConsoleWebsocket,
    getCurrentRun,
    getCurrentRunWebsocket, getServer,
    startServer,
    stopServer
} from "../networking/BackendAPI";
import {runAsync} from "../util/RunAsync";
export function ServerPage(props: { serverUUID: string }) {
    const [server, setServer] = useState<Server | AccessError>("loading")
    const [currentRun, setCurrentRun] = useState<CurrentRun | null | AccessError>("loading")

    const currentRunWebsocket = useRef<WebSocket | undefined>(undefined);

    useEffect(() => {
        runAsync(async () => {
            try {
                const server = await getServer(props.serverUUID);
                const currentRun = await getCurrentRun(server.uuid);

                setCurrentRun(currentRun);

                currentRunWebsocket.current =
                    await getCurrentRunWebsocket(
                        server.runnerUUID,
                        server.uuid,
                        (event: MessageEvent<string>) => {
                            const currentRuns: CurrentRun[] = JSON.parse(event.data)

                            setCurrentRun(currentRuns.length === 0 ? null : currentRuns[0])
                        }
                    );

                setServer(server);
            } catch (e) {
                setServer('unavailable');
                throw e;
            }
        })
    }, [props.serverUUID])

    useEffect(() => () => { // Cleanup
        currentRunWebsocket.current?.close()
    }, [])

    const isRunning =
        currentRun === "unavailable" ? "¯\\_(ツ)_/¯"
            : currentRun === "loading" ? ""
            : currentRun !== null

    switch (server) {
        case "unavailable":
            return (<p>Couldn't get server :(</p>);
        case "loading":
            return (<p>Loading...</p>);
        default:
            return (
                <div className="server-page">
                    <p>Server: {server.name}</p>
                    <p>Version: {server.version}</p>
                    {
                        isCurrentRun(currentRun) ?
                            <p>Address: {currentRun.address.host + ":" + currentRun.address.port}</p> : null
                    }
                    <p>running? {`${isRunning}`}</p>
                    <button onClick={() => startServer(server)}>Start</button>
                    <button onClick={() => stopServer(server)}>Stop</button>
                    <Console serverUUID={props.serverUUID} currentRun={currentRun}/>
                </div>
            );
    }
}

function Console(props: { serverUUID: string, currentRun: CurrentRun | AccessError | null }) {
    const [logHistoryState, setLogHistoryState] = useState<string[]>([])
    const [autoScroll, setAutoScroll] = useState<boolean>(true)
    const consoleBottomRef = useRef<HTMLDivElement | null>(null)
    const logHistoryRef = useRef<string[]>([]);
    const [animationParent, enableAnimation] = useAutoAnimate()

    function setLogHistory(newLogHistory: string[]) {
        logHistoryRef.current = newLogHistory // Allows immediate update of the underlying property
        setLogHistoryState(logHistoryRef.current)
    }

    const appendToLogHistory = useCallback((newLine: string) => {
        setLogHistory([...logHistoryRef.current, newLine])
    }, []);

    const consoleWebsocket = useRef<WebSocket | undefined>(undefined);

    const setConsoleWebsocket = useCallback(async (currentRun: CurrentRun | null | AccessError) => {
        if (isCurrentRun(currentRun)) {
            await setConsoleWebsocket(null) // Clear current websocket, if any

            consoleWebsocket.current = await getConsoleWebsocket(currentRun.runnerId, currentRun.uuid, (event: MessageEvent<string>) => {
                appendToLogHistory(event.data)
            })
        } else { // currentRun is an AccessError or null
            consoleWebsocket.current?.close()
            appendToLogHistory("\n")
            consoleWebsocket.current = undefined
        }
    }, [appendToLogHistory])

    useEffect(() => {
        // noinspection JSIgnoredPromiseFromCall
        setConsoleWebsocket(props.currentRun)

        return () => { // Cleanup
            consoleWebsocket.current?.close()
        }
    }, [props.currentRun, setConsoleWebsocket])

    useEffect(() => { // Autoscroll when log updates
        if (autoScroll) {
            consoleBottomRef.current?.scrollIntoView()
        }
    }, [autoScroll, logHistoryState]);




    return (<>
        <p>Console:</p>
        <div className="console" ref={animationParent}>
            {
                logHistoryState
                    .filter(line => line.trim().length > 0)
                    .map(line =>
                        <div className="console-line">{line}</div>
                    )
            }
            {/*invisible div to make scrolling to bottom easier*/}
            <div id="console-bottom" ref={consoleBottomRef}></div>
        </div>
        <label>
            Autoscroll
            <input type="checkbox" checked={autoScroll} onChange={() => setAutoScroll(!autoScroll)}/>
        </label>
    </>)
}

function isCurrentRun(possibleCurrentRun: CurrentRun | null | AccessError): possibleCurrentRun is CurrentRun {
    return possibleCurrentRun !== null && typeof possibleCurrentRun !== "string"
}