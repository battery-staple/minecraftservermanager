import React, {useEffect, useRef, useState} from "react";
import {CurrentRun, Server} from "../APIModels";
import {
    AccessError,
    fetchCurrentRun,
    fetchServer,
    getConsoleWebsocket,
    getCurrentRunWebsocket, startServer, stopServer
} from "../Networking";
import {useAutoAnimate} from "@formkit/auto-animate/react";
export function ServerPage(props: { serverUUID: string }) {
    const [server, setServer] = useState<Server | AccessError>("loading");
    const [currentRun, setCurrentRun] = useState<CurrentRun | null | AccessError>("loading");
    const [logHistory, _setLogHistory] = useState<string[]>([]);

    const [parent, enableAnimation] = useAutoAnimate()

    const _logHistory = useRef<string[]>([]);
    function setLogHistory(newLogHistory: string[]) {
        _logHistory.current = newLogHistory // Allows immediate update of the underlying property
        _setLogHistory(_logHistory.current)
    }

    function appendToLogHistory(newLine: string) { setLogHistory([..._logHistory.current, newLine]) }

    const consoleWebsocket = useRef<WebSocket | undefined>(undefined);
    const currentRunWebsocket = useRef<WebSocket | undefined>(undefined);

    useEffect(() => {
        fetchServer(props.serverUUID)
            .catch(e => { setServer("unavailable"); throw e })
            .then((server) => {
                fetchCurrentRun(server.uuid)
                    .then(currentRun => currentRun)
                    .then(currentRun => {
                        setConsoleWebsocket(currentRun)
                        setCurrentRun(currentRun)
                    })
                    .catch(e => { setCurrentRun("unavailable"); throw e })

                currentRunWebsocket.current =
                    getCurrentRunWebsocket(
                        server.runnerUUID,
                        server.uuid,
                        (event: MessageEvent<string>) => {
                            const currentRuns: CurrentRun[] = JSON.parse(event.data)

                            let currentRun: CurrentRun | null
                            if (currentRuns.length === 0) {
                                currentRun = null
                            } else {
                                currentRun = currentRuns[0]
                            }

                            setCurrentRun(currentRun)
                        }
                    )

                setServer(server)
            })
    }, [props.serverUUID])

    useEffect(() => {
        setConsoleWebsocket(currentRun)
    }, [currentRun])

    useEffect(() => { return () => {
        currentRunWebsocket.current?.close()
        consoleWebsocket.current?.close()
    }}, [])

    function setConsoleWebsocket(currentRun: CurrentRun | null | AccessError) {
        if (currentRun === null || typeof currentRun === "string") {
            consoleWebsocket.current?.close()
            appendToLogHistory("\n")
            consoleWebsocket.current = undefined
        } else {
            console.log("setting console websocket!")
            consoleWebsocket.current = getConsoleWebsocket(currentRun.runnerId, currentRun.uuid, (event: MessageEvent<string>) => {
                appendToLogHistory(event.data)
            })
        }
    }

    const isRunning = () =>
        currentRun === "unavailable" ? "¯\\_(ツ)_/¯"
            : currentRun === "loading" ? ""
            : currentRun !== null

    if (server === "unavailable") {
        return (
            <p>Couldn't get server :(</p>
        );
    } else if (server === "loading") {
        return (
            <p>Loading...</p>
        );
    } else {
        return <>
            <p>Server: {server.name}</p>
            <p>Version: {server.version}</p>
            <p>running? {`${isRunning()}`}</p>
            <button onClick={() => startServer(server)}>Start</button>
            <button onClick={() => stopServer(server)}>Stop</button>
            <p>Log:</p>
            <div className="console" ref={parent}>
                {
                    logHistory.map(line => <p>{line}</p>)
                }
            </div>
        </>;
    }
}