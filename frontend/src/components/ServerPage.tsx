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
                    <button onClick={() => startServer(server)}>Start</button>
                    <button onClick={() => stopServer(server)}>Stop</button>
                    <Console serverUUID={props.serverUUID} currentRun={currentRun}/>
                </div>
            );
    }
}

function Console(props: { serverUUID: string, currentRun: CurrentRun | AccessError | null }) {
    const [logHistoryState, setLogHistoryState] = useState<LogLine[]>([])
    const [autoScroll, setAutoScroll] = useState<boolean>(true)
    const logHistoryRef = useRef<LogLine[]>([]);
    const consoleWebsocket = useRef<WebSocket | undefined>(undefined);

    function setLogHistory(newLogHistory: LogLine[]) {
        logHistoryRef.current = newLogHistory // Allows immediate update of the underlying property
        setLogHistoryState(logHistoryRef.current)
    }

    const appendToLogHistory = useCallback((newLine: LogLine) => {
        setLogHistory([...logHistoryRef.current, newLine])
    }, []);

    const setConsoleWebsocket = useCallback(async (currentRun: CurrentRun | null | AccessError) => {
        if (isCurrentRun(currentRun)) {
            await setConsoleWebsocket(null) // Clear current websocket, if any

            consoleWebsocket.current = await getConsoleWebsocket(currentRun.runnerId, currentRun.uuid, (event: MessageEvent<string>) => {
                appendToLogHistory({
                    source: "output",
                    text: event.data
                })
            })
        } else { // currentRun is an AccessError or null
            consoleWebsocket.current?.close()
            consoleWebsocket.current = undefined
        }
    }, [appendToLogHistory])

    useEffect(() => { // Setup
        // noinspection JSIgnoredPromiseFromCall
        setConsoleWebsocket(props.currentRun)

        return () => { // Cleanup
            consoleWebsocket.current?.close()
        }
    }, [props.currentRun, setConsoleWebsocket])

    return (
        <div className="console">
            <ConsoleHistory logHistory={logHistoryState} autoScroll={autoScroll}/>
            <ConsoleInput consoleWebsocket={consoleWebsocket.current} appendToLogHistory={appendToLogHistory}/>
            <AutoscrollButton autoScroll={autoScroll} setAutoScroll={setAutoScroll}/>
        </div>
    )
}

function ConsoleHistory(props: { logHistory: LogLine[], autoScroll: boolean }) {
    const consoleHistoryBottomRef = useRef<HTMLDivElement | null>(null)
    const [animationParent, enableAnimation] = useAutoAnimate()

    useEffect(() => { // Autoscroll
        if (props.autoScroll) {
            consoleHistoryBottomRef.current?.scrollIntoView()
        }
    }, [props.autoScroll, props.logHistory]); // Should scroll whenever logHistory changes

    return (
        <div className="console-history" ref={animationParent}>
            {
                props.logHistory
                    .filter(line => line.source === null || line.text.trim().length > 0)
                    .map(line => {
                        switch (line.source) {
                            case "input":
                                return <div className="console-line console-line-input">{line.text}</div>
                            case "output":
                                return <div className="console-line console-line-output">{line.text}</div>
                            case null: // Line break
                                return <div className="console-line"><br/></div>
                        }
                    })
            }
            {/*invisible div to make scrolling to bottom easier*/}
            <div id="console-history-bottom" ref={consoleHistoryBottomRef}></div>
        </div>
    )
}

function AutoscrollButton(props: { autoScroll: boolean, setAutoScroll: (value: boolean) => void }) {
    const toggleAutoScroll = () => props.setAutoScroll(!props.autoScroll)

    return (
        <label>
            Enable Autoscroll
            <input type="checkbox" checked={props.autoScroll} onChange={toggleAutoScroll}/>
        </label>
    )
}

function ConsoleInput(props: { consoleWebsocket: WebSocket | undefined, appendToLogHistory: (newLine: LogLine) => void }) {
    const [input, setInput] = useState("")

    function submit() {
        if (props.consoleWebsocket === undefined) {
            return // TODO: Report error or something
        }

        props.consoleWebsocket.send(input)
        props.appendToLogHistory({
            source: "input",
            text: input
        })
        setInput("")
    }

    return (
        <div
            className="console-input"
            onKeyDown={event => { if (event.key === "Enter") submit() }}
        >
            <input
                className="console-input-input" name="ConsoleInput"
                value={input}
                onChange={e => setInput(e.target.value)}
            />
            <button className="console-input-button" onClick={submit}>
                Send
            </button>
        </div>
    )
}

function isCurrentRun(possibleCurrentRun: CurrentRun | null | AccessError): possibleCurrentRun is CurrentRun {
    return possibleCurrentRun !== null && typeof possibleCurrentRun !== "string"
}

interface LogLine {
    source: "input" | "output"
    text: string
}