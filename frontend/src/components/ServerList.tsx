import React, {JSX, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {CurrentRun, DEFAULT_SORT_STRATEGY, Server, SortStrategy} from "../APIModels";
import {useAutoAnimate} from "@formkit/auto-animate/react";
import "./ServerList.css"
import {createServer, deleteServer, getServers, getServersWebsocket} from "../networking/backendAPI/Servers";
import {getCurrentRun} from "../networking/backendAPI/CurrentRuns";
import {getPreferences, updatePreferences} from "../networking/backendAPI/Preferences";

export function ServerList(props: { setHeader: (headerElement: JSX.Element) => void }) {
    const [servers, setServers] = useState<Server[] | null | undefined>(undefined);
    const [sortStrategyState, setSortStrategyState] = useState<SortStrategy | undefined>(undefined)
    const [editing, setEditing] = useState(false)
    const [serversParent] = useAutoAnimate()

    const sortStrategy = useMemo(() => sortStrategyState ?? DEFAULT_SORT_STRATEGY, [sortStrategyState])

    const setSortStrategy = useCallback((newSortStrategy: SortStrategy) => {
        setSortStrategyState(newSortStrategy)
        updatePreferences({
            serverSortStrategy: newSortStrategy
        }).then(success => {
            if (success) {
                setSortStrategyState(newSortStrategy)
            } else {
                console.error("Failed to save preferences update to server")
            }
        })
    }, []);

    /**
     * The websocket that provides updates whenever any of the servers change (i.e., is created or deleted).
     */
    const serversWebsocket = useRef<WebSocket | null>(null);

    const setServersWebsocket = useCallback(async () => {
        getServers()
            .then(servers => setServers(servers))

        serversWebsocket.current =
            await getServersWebsocket(
                (event: MessageEvent<string>) => {
                    const servers: Server[] = JSON.parse(event.data);

                    setServers(servers);
                },
                function () {
                    if (this === serversWebsocket.current) serversWebsocket.current = null;
                }
            );
    }, []);

    useEffect(() => {
        // noinspection JSIgnoredPromiseFromCall
        setServersWebsocket()

        return () => { // Cleanup
            serversWebsocket.current?.close()
        }
    }, [setServersWebsocket])

    useEffect(() => {
        getPreferences()
            .then(preferences => setSortStrategyState(preferences?.serverSortStrategy))
    }, [])

    /**
     * This is cached here rather than being defined in useEffect in order to prevent an infinite re-render loop.
     * If this were not cached, then when useEffect is triggered,
     * setHeader would update the header state variable in the parent.
     * This would cause it to rerender this with new props,
     * which would cause useEffect to trigger, which would repeat the cycle.
     * The end effect is very high (100%+) CPU usage, which is certainly undesirable.
     */
    const headerButtons = useMemo(
        () => <HeaderButtons editing={editing} setEditing={setEditing}/>,
        [editing]);
    useEffect(() => {
        props.setHeader(headerButtons)
    }, [editing, headerButtons, props]);

    const serverOptions: JSX.Element[] = useMemo(() => {
        if (servers === null || servers === undefined) return [];

        return servers
            .sort(serverCompareFn(sortStrategy))
            .map((server) => (
                <div className="col" key={server.uuid}>
                    <ServerOption server={server} editing={editing} setEditing={setEditing} onClick={() => {
                        window.location.href = `/servers/${server.uuid}`
                    }}/>
                </div>
            ))
    }, [editing, servers, sortStrategy]);

    if (servers === undefined) {
        return LoadingServers()
    } else if (servers === null) {
        return CannotLoadServers()
    }

    return (
        <div className="server-list">
            <OrderDropdownBar sortStrategy={sortStrategy} setSortStrategy={setSortStrategy} />
            <div className="container-fluid">
                <div className="row row-cols-1 row-cols-sm-auto g-3" ref={serversParent}>
                    {serverOptions.map((serverOption) => (serverOption))}
                </div>
            </div>
        </div>
    );
}

function OrderDropdownBar(props: {sortStrategy: SortStrategy, setSortStrategy: (sortStrategy: SortStrategy) => void}): React.JSX.Element {
    return <div className="sort-dropdown-bar server-list-bar">
        <div className="dropdown">
            <a className="dropdown-toggle" href="#" role="button" id="dropdownMenuLink" data-bs-toggle="dropdown"
               aria-expanded="false">
                Sorting by: {displayName(props.sortStrategy)}
            </a>
            <ul className="dropdown-menu">
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("ALPHABETICAL")
                }}>Alphabetical</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("NEWEST")
                }}>Newest</a></li>
                <li><a className="dropdown-item" onClick={() => {
                    props.setSortStrategy("OLDEST")
                }}>Oldest</a></li>
            </ul>
        </div>
    </div>;
}

function HeaderButtons(props: { editing: boolean, setEditing: (editing: boolean) => void }) {

    if (props.editing) {
        return <>
            <button type="button" className="btn btn-primary btn-lg" onClick={() => props.setEditing(false)}>
                Done
            </button>
        </>
    } else {
        return <>
            <button type="button" className="btn btn-secondary btn-lg" onClick={() => props.setEditing(true)}>
                Edit
            </button>
            <button type="button" className="btn btn-primary btn-lg" onClick={() => createServer({
                name: "[New button]", // TODO: Customization ofc
                versionPhase: "RELEASE",
                version: "1.8.9",
                runnerUUID: "d72add0d-4746-4b46-9ecc-2dcd868062f9"
            })}>New</button>
        </>
    }
}

function ServerOption(props: { server: Server, editing: boolean, setEditing: (editing: boolean) => void, onClick: () => void }): React.JSX.Element {
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
                    <img src="/X.png" alt="" ></img>
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

function CannotLoadServers(): React.JSX.Element {
    return (
        <p>Cannot load servers :(</p>
    )
}

function LoadingServers(): React.JSX.Element {
    return (
        <p>Loading servers...</p>
    )
}

function serverCompareFn(sortStrategy: SortStrategy): (a: Server, b: Server) => number {
    switch (sortStrategy) {
        case "ALPHABETICAL": return (a, b) => a.name.localeCompare(b.name)
        case "NEWEST": return (a, b) => +b.creationTime - +a.creationTime
        case "OLDEST": return (a, b) => +a.creationTime - +b.creationTime
    }
}

function displayName(sortStrategy: SortStrategy) {
    switch (sortStrategy) {
        case "ALPHABETICAL": return "Alphabetical"
        case "NEWEST": return "Newest"
        case "OLDEST": return "Oldest"
    }
}