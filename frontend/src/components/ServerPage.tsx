import React, {useCallback, useEffect, useMemo, useRef, useState} from "react";
import {ConsoleMessage, CurrentRun, Server} from "../APIModels";
import {useAutoAnimate} from "@formkit/auto-animate/react";
import {
    AccessError
} from "../networking/backendAPI/AccessError";
import "./ServerPage.css"
import {getServer, startServer, stopServer} from "../networking/backendAPI/Servers";
import {getConsoleWebsocket, getCurrentRunWebsocket} from "../networking/backendAPI/CurrentRuns";
import {getCurrentRun} from "../networking/backendAPI/CurrentRuns";

export function ServerPage(props: { serverUUID: string }) {
    /**
     * The server on this page, or an AccessError if the server is not available or is still loading.
     */
    const [server, setServer] = useState<Server | AccessError>("loading")

    /**
     * The current run of the server on this page, or null if the server is not running.
     * If the running status of the server is currently unknown, then this is an AccessError.
     */
    const [currentRun, setCurrentRun] = useState<CurrentRun | null | AccessError>("loading")

    /**
     * The websocket that provides updates whenever the server's current run changes (i.e., when the server starts or stops).
     */
    const currentRunWebsocket = useRef<WebSocket | null>(null);

    const setCurrentRunWebsocket = useCallback(async (serverUUID: string) => {
        try {
            const server = await getServer(serverUUID);
            const currentRun = await getCurrentRun(server.uuid);

            setCurrentRun(currentRun);

            currentRunWebsocket.current =
                await getCurrentRunWebsocket(
                    server.runnerUUID,
                    server.uuid,
                    (event: MessageEvent<string>) => {
                        const currentRuns: CurrentRun[] = JSON.parse(event.data)

                        setCurrentRun(currentRuns.length === 0 ? null : currentRuns[0])
                    },
                    function () {
                        if (this === currentRunWebsocket.current) currentRunWebsocket.current = null
                    }
                );

            setServer(server);
        } catch (e) {
            setServer('unavailable');
            throw e;
        }
    }, []);

    useEffect(() => {
        // noinspection JSIgnoredPromiseFromCall
        setCurrentRunWebsocket(props.serverUUID)

        return () => { // Cleanup
            currentRunWebsocket.current?.close()
        }
    }, [props.serverUUID, setCurrentRunWebsocket])

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
    const [logHistoryState, setLogHistoryState] = useState<ConsoleMessage[]>([])
    const [autoScroll, setAutoScroll] = useState<boolean>(true)
    const logHistoryRef = useRef<ConsoleMessage[]>([]);
    const consoleWebsocket = useRef<WebSocket | null>(null);

    function setLogHistory(newLogHistory: ConsoleMessage[]) {
        logHistoryRef.current = newLogHistory // Allows immediate update of the underlying property
        setLogHistoryState(logHistoryRef.current)
    }

    const appendToLogHistory = useCallback((newLine: ConsoleMessage) => {
        setLogHistory([...logHistoryRef.current, newLine])
    }, []);

    const setConsoleWebsocket = useCallback(async (currentRun: CurrentRun | null | AccessError) => {
        if (isCurrentRun(currentRun)) {
            await setConsoleWebsocket(null) // Clear current websocket, if any

            consoleWebsocket.current = await getConsoleWebsocket(
                currentRun.runnerId,
                currentRun.uuid,
                (event: MessageEvent<string>) => {
                    const message: ConsoleMessage = JSON.parse(event.data)
                    appendToLogHistory(message)
                },
                function (event: CloseEvent) {
                    console.log(`Websocket closed with code ${event.code} and reason ${event.reason}`)
                    if (this === consoleWebsocket.current) consoleWebsocket.current = null
                }
            )

        } else { // currentRun is an AccessError or null
            consoleWebsocket.current?.close()
            consoleWebsocket.current = null
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
            <ConsoleInput consoleWebsocket={consoleWebsocket.current} logHistory={logHistoryState}/>
            <AutoscrollButton autoScroll={autoScroll} setAutoScroll={setAutoScroll}/>
        </div>
    )
}

function ConsoleHistory(props: { logHistory: ConsoleMessage[], autoScroll: boolean }) {
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
                    .filter(line => line.text.trim().length > 0)
                    .map(line => {
                        switch (line.type) {
                            case "Input":
                                return <div className="console-line console-line-input">{line.text}</div>
                            case "Log":
                                return <div className="console-line console-line-output">{line.text}</div>
                            case "Error":
                                return <div className="console-line console-line-error">{line.text}</div>
                            default:
                                console.error(`Got line of unknown type ${line.type}!`)
                                return <div className="console-line console-line-error">[Unknown Message]</div>
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

function ConsoleInput(props: { consoleWebsocket: WebSocket | null, logHistory: ConsoleMessage[] }) {
    /**
     * The current text of the input field.
     */
    const [input, setInput] = useState("");

    /**
     * If the user has currently selected a previous input to edit and send,
     * then this field holds any new data they had entered in the input field.
     * It will be restored if they decide to stop editing previous input (by pressing the down arrow)
     */
    const [cachedNewInput, setCachedNewInput] = useState("")

    /**
     * A reference to the <input> tag of the input field
     */
    const inputHTML = useRef<HTMLInputElement | null>(null);

    /**
     * The text of all previous inputs, used for allowing the user to re-execute previous inputs.
     * Most recent history is at the start of the array.
     */
    const inputHistory = useMemo(
        () => props.logHistory
            .filter((line) => line.type === "Input")
            .map(line => line.text)
            .reverse(),
        [props.logHistory]
    )

    /**
     * The index of the input history the user has currently selected, or null if they are typing a new command.
     * Invariant: this is always either null or a valid index of inputHistory.
     */
    const [inputHistoryIndex, setInputHistoryIndex] = useState<number | null>(null)

    /**
     * Sends the current text of the input field over the websocket and resets the field.
     */
    function sendInput() {
        if (props.consoleWebsocket === null) {
            return // TODO: Report error or something
        }

        props.consoleWebsocket.send(input);
        setInput("");
        setInputHistoryIndex(null)
    }

    /**
     * Moves the cursor to the end of the input field
     */
    function moveCursorToInputEnd() {
        if (inputHTML.current) {
            inputHTML.current.selectionStart = inputHTML.current?.value.length
            inputHTML.current.selectionEnd = inputHTML.current?.value.length
        }
    }

    /**
     * Changes the selected input in the history to the previous.
     */
    function previousInput() {
        if (inputHistory.length === 0) return; // There is no previous history, so do nothing

        let newInputHistoryIndex: number // inputHistoryIndex doesn't update immediately, so this property exists
                                         // so that updated values can be used immediately

        if (inputHistoryIndex === null) { // No previous input is currently selected (currently inputting a new message)
            newInputHistoryIndex = 0; // Move to most recent previous input
            setCachedNewInput(input) // Moving away from the newly entered input, but still want to save it for later
        } else {
            // Repeatedly go back in history until we find an entry that's actually different from where we were before
            const initiallySelectedPreviousInput = inputHistory[inputHistoryIndex]
            newInputHistoryIndex = inputHistoryIndex
            do {
                if (newInputHistoryIndex + 1 === inputHistory.length) return; // There is no more history, so do nothing

                newInputHistoryIndex++;
            } while (inputHistory[newInputHistoryIndex] === initiallySelectedPreviousInput)
        }

        setInputHistoryIndex(newInputHistoryIndex);
        setInput(inputHistory[newInputHistoryIndex]);
        moveCursorToInputEnd();
    }

    function nextInput() {
        let newInputHistoryIndex: number | null // inputHistoryIndex doesn't update immediately, so this property exists
                                                // so that updated values can be used immediately
        switch (inputHistoryIndex) {
            case null:
                newInputHistoryIndex = null; // Can't see into the future, sadly
                break;
            case 0:
                newInputHistoryIndex = null; // Currently looking at the most recent history entry, so moving forward
                                             // brings us to editing a new input
                break;
            default:
                // Repeatedly go forward in history until we find an entry that's actually different from where we are
                const initiallySelectedPreviousInput = inputHistory[inputHistoryIndex]
                newInputHistoryIndex = inputHistoryIndex
                do {
                    if (newInputHistoryIndex === 0) {
                        newInputHistoryIndex = null; // We've reached the new input
                        break;
                    }

                    newInputHistoryIndex--;
                } while (inputHistory[newInputHistoryIndex] === initiallySelectedPreviousInput)
                break;
        }

        if (newInputHistoryIndex == null) {
            setInput(cachedNewInput)
        } else {
            setInput(inputHistory[newInputHistoryIndex]);
        }
        setInputHistoryIndex(newInputHistoryIndex);
        moveCursorToInputEnd();
    }

    return (
        <div
            className="console-input"
            onKeyDown={event => {
                switch (event.key) {
                    case "Enter":
                        sendInput();
                        break;
                    case "ArrowUp":
                        previousInput();
                        break;
                    case "ArrowDown":
                        nextInput();
                        break;
                }
            }}
        >
            <input
                className="console-input-input" name="ConsoleInput"
                ref={inputHTML}
                value={input}
                onChange={e => setInput(e.target.value)}
            />
            <button className="console-input-button" onClick={sendInput}>
                Send
            </button>
        </div>
    )
}

function isCurrentRun(possibleCurrentRun: CurrentRun | null | AccessError): possibleCurrentRun is CurrentRun {
    return possibleCurrentRun !== null && typeof possibleCurrentRun !== "string"
}