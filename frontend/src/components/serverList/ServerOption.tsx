import {CurrentRun, Server} from "../../APIModels";
import {JSX, useCallback, useEffect, useMemo, useState} from "react";
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
            return <ul className="server-option-properties">
                <li><strong>host: </strong> {currentRun.address.host}</li>
                <li><strong>port: </strong> {currentRun.address.port}</li>
            </ul>
        } else { // Server is not running
            return <ul className="server-option-properties">
                <li><strong>version: </strong> {props.server.version}</li>
                <li><br/></li>
            </ul>
        }
    }, [currentRun, props.server.version]);

    return (
        <div className="server-option">
            {props.editing ?
                <button className="server-option-x" onClick={() => deleteServer(props.server.uuid)}>
                    <img src="/X.png" alt=""></img>
                </button> : null}
            <button className="server-option-button" onClick={props.onClick} disabled={props.editing}>
                <div className="server-option-title">{props.server.name}</div>
                {properties}
                <div className="server-option-status" style={{
                    color: isRunning() ? "green" : "red"
                }}>{isRunning() ? "Running" : "Not Running"}</div>
            </button>
        </div>
    )
}