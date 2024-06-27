import {CurrentRun, Server} from "../../APIModels";
import React, {JSX, useCallback, useEffect, useMemo, useState} from "react";
import {getCurrentRun} from "../../networking/backendAPI/CurrentRuns";
import {deleteServer} from "../../networking/backendAPI/Servers";

export function ServerOption(props: {
    server: Server,
    editing: boolean,
    setEditing: (editing: boolean) => void,
    onClick: () => void
}): JSX.Element {
    const [currentRun, setCurrentRun] = useState<CurrentRun | null>(null);

    useEffect(() => {
        getCurrentRun(props.server.uuid)
            .then(currentRun => setCurrentRun(currentRun))
    }, [props.server.uuid])


    const isRunning = useCallback(() => currentRun !== null, [currentRun]);

    const properties = useMemo(() => {
        if (currentRun?.address) { // Server is running
            return <ul className="server-button-properties">
                <li><strong>host: </strong> {currentRun.address.host}</li>
                <li><strong>port: </strong> {currentRun.address.port}</li>
            </ul>
        } else { // Server is not running
            return <ul className="server-button-properties">
                <li><strong>version: </strong> {props.server.version}</li>
                <li><br/></li>
            </ul>
        }
    }, [currentRun, props.server.version]);

    return (
        <button className="server-button" onClick={props.onClick} disabled={props.editing}>
            {props.editing ?
                <button className="server-button-x" onClick={() => deleteServer(props.server.uuid)}>
                    <img src="/X.png" alt=""></img>
                </button> :
                null}
            <div className="server-button-title">{props.server.name}</div>
            {properties}
            <div className="server-button-status" style={{
                color: isRunning() ? "green" : "red"
            }}>{isRunning() ? "Running" : "Not Running"}</div>
        </button>
    )
}