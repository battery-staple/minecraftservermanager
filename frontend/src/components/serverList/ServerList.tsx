import {JSX, useEffect, useMemo, useState} from "react";
import {Server, SortStrategy} from "../../APIModels";
import {useAutoAnimate} from "@formkit/auto-animate/react";
import "./ServerList.css"
import {OrderDropdownBar} from "./OrderDropdownBar";
import {HeaderButtons, HeaderButtonsState} from "./HeaderButtons";
import {NewServerModal} from "./NewServerModal";
import {ServerOption} from "./ServerOption";
import {useServers} from "../../hooks/UseServers";
import {isError} from "../../networking/backendAPI/AccessError";
import {useSortStrategy} from "../../hooks/UseSortStrategy";

export function ServerList(props: { setHeader: (headerElement: JSX.Element) => void }): JSX.Element {
    const servers = useServers();

    const [editing, setEditing] = useState(false)
    const [creatingNew, setCreatingNew] = useState(false)
    const [sortStrategy, setSortStrategy] = useSortStrategy();

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

    const serverOptions: JSX.Element[] = useMemo(() => {
        if (servers === null || isError(servers)) return [];

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