import {JSX, useCallback, useEffect, useMemo, useRef, useState} from "react";
import {DEFAULT_SORT_STRATEGY, Server, SortStrategy} from "../../APIModels";
import {useAutoAnimate} from "@formkit/auto-animate/react";
import "./ServerList.css"
import {getServers, getServersWebsocket} from "../../networking/backendAPI/Servers";
import {getPreferences, updatePreferences} from "../../networking/backendAPI/Preferences";
import {OrderDropdownBar} from "./OrderDropdownBar";
import {HeaderButtons, HeaderButtonsState} from "./HeaderButtons";
import {NewServerModal} from "./NewServerModal";
import {ServerOption} from "./ServerOption";

export function ServerList(props: { setHeader: (headerElement: JSX.Element) => void }): JSX.Element {
    const [servers, setServers] = useState<Server[] | null | "loading">("loading");
    const [sortStrategyState, setSortStrategyState] = useState<SortStrategy | null>(null)
    const [editing, setEditing] = useState(false)
    const [creatingNew, setCreatingNew] = useState(false)

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
     * Can't be creating and editing at the same time.
     * This can be relevant if the user deletes all the servers in edit mode and then creates a new server.
     * Once that server is created, they should not be returned to the editing view
     */
    useEffect(() => {
        if (creatingNew) {
            setEditing(false)
        }
    }, [creatingNew]);

    /**
     * The websocket that provides updates whenever any of the servers change (i.e., is created or deleted).
     */
    const serversWebsocket = useRef<WebSocket | null>(null);

    const setServersWebsocket = useCallback(async () => {
        setServers(await getServers()) // Fallback for websocket

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
        // noinspection JSIgnoredPromiseFromCall
        setServersWebsocket()

        return () => { // Cleanup
            serversWebsocket.current?.close()
        }
    }, [setServersWebsocket])

    useEffect(() => {
        getPreferences()
            .then(preferences => {
                if (preferences !== null) setSortStrategyState(preferences.serverSortStrategy)
            })
    }, [])

    const serverOptions: JSX.Element[] = useMemo(() => {
        if (servers === null || servers === "loading") return [];

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

    /**
     * This is cached here rather than being defined in useEffect in order to prevent an infinite re-render loop.
     * If this were not cached, then when useEffect is triggered,
     * setHeader would update the header state variable in the parent.
     * This would cause it to rerender this with new props,
     * which would cause useEffect to trigger, which would repeat the cycle.
     * The end effect is very high (100%+) CPU usage, which is certainly undesirable.
     */
    const headerButtons = useMemo(
        () => <HeaderButtons state={headerButtonsState(editing, serverOptions)} setEditing={setEditing} setCreating={setCreatingNew}/>,
    [serverOptions, editing]);

    useEffect(() => {
        props.setHeader(headerButtons)
    }, [editing, headerButtons, props]);

    if (servers === "loading") {
        return <LoadingServers />
    } else if (servers === null) {
        return <CannotLoadServers />
    }

    return (
        <div className="server-list">
            <OrderDropdownBar sortStrategy={sortStrategy} setSortStrategy={setSortStrategy} />
            {serverOptions.length === 0 ?
                <NoServers setCreating={setCreatingNew}/> :
                <ServerOptions options={serverOptions}></ServerOptions>}
            <NewServerModal isShowing={creatingNew} setShowing={setCreatingNew}/>
        </div>
    );
}

function NoServers(props: { setCreating: (creating: boolean) => void }): JSX.Element {
    return <button className={"server-list-no-servers-message"}
                   onClick={() => props.setCreating(true)}>
        No servers yet. Create one!
    </button>
}

function ServerOptions(props: { options: JSX.Element[] }): JSX.Element {
    const [serversParent] = useAutoAnimate()

    return <div className="container-fluid">
        <div className="server-list-grid row row-cols-1 row-cols-sm-auto g-3" ref={serversParent}>
            {props.options.map((serverOption) => (serverOption))}
        </div>
    </div>
}

function CannotLoadServers(): JSX.Element {
    return (
        <p>Cannot load servers :(</p>
    )
}

function LoadingServers(): JSX.Element {
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

function headerButtonsState(editing: boolean, serverOptions: JSX.Element[]): HeaderButtonsState {
    if (serverOptions.length === 0) {
        return "disabled"
    } else if (editing) {
        return "editing"
    } else {
        return "creating"
    }
}