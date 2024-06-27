import {createServer, CreateServerOptions} from "../../networking/backendAPI/Servers";
import React, {Dispatch, useState} from "react";
import {ALL_VERSION_PHASES, VersionPhase} from "../../APIModels";

const defaultVersionPhase = "RELEASE"

export function NewServerModal() {
    const [name, setName] = useState<string>()
    const [versionPhase, setVersionPhase] = useState<VersionPhase>(defaultVersionPhase)
    const [version, setVersion] = useState<string>()
    const runnerUUID = "d72add0d-4746-4b46-9ecc-2dcd868062f9" // TODO: Support other runners

    let creationOptions = undefined;
    if (name !== undefined && version !== undefined && versionPhase !== undefined) {
        creationOptions = {
            name: name,
            version: version,
            versionPhase: versionPhase,
            runnerUUID: runnerUUID,
        }
    }

    return <div className="new-server-modal modal fade" id="newServerModal" data-bs-backdrop="static" tabIndex={-1}
                aria-labelledby="newServerModalLabel" aria-hidden="true">
        <div className="modal-dialog modal-dialog-centered">
            <div className="modal-content">
                <Header/>
                <Body name={name} setName={setName}
                      versionPhase={versionPhase} setVersionPhase={setVersionPhase}
                      version={version} setVersion={setVersion} />
                <Footer createServerOptions={creationOptions} onSubmit={(success) => {
                    if (success) { // Reset all dropdowns
                        setVersionPhase(defaultVersionPhase);
                    }
                }}/>
            </div>
        </div>
    </div>
}

function Body(props: {
    name: string | undefined, setName: Dispatch<React.SetStateAction<string | undefined>>
    versionPhase: VersionPhase, setVersionPhase: Dispatch<React.SetStateAction<VersionPhase>>
    version: string | undefined, setVersion: Dispatch<React.SetStateAction<string | undefined>>
}) {
    return <div className="modal-body">
        <form>
            <div className="form-group">
                <label htmlFor="name">Name: </label>
                <input name="name" type="text" value={props.name} onChange={(e) => props.setName(e.target.value)} />
            </div>
            <div className="form-group">
                <label htmlFor="name">Version Phase: </label>
                <select name="name"
                        value={props.versionPhase}
                        onChange={(e) => props.setVersionPhase(e.target.value as VersionPhase)}>
                    {
                        versionPhaseOptions().map(({name, phase}) => (
                            <option value={phase} key={phase}>
                                {name}
                            </option>
                        ))
                    }
                </select>
            </div>
            <div className="form-group"> { /* TODO: autocomplete (dynamic based on phase) */ }
                <label htmlFor="version">Version: </label>
                <input name="version" type="text" value={props.version} onChange={(e) => props.setVersion(e.target.value)} />
            </div>
        </form>
    </div>
}

interface VersionPhaseOption {
    name: string
    phase: VersionPhase
}

/**
 * All of the possible version phases, in displayable order.
 */
function versionPhaseOptions(): VersionPhaseOption[] {
    const phaseOptions: VersionPhaseOption[] = [
        {name: "Release", phase: "RELEASE"},
        {name: "Snapshot", phase: "SNAPSHOT"},
        {name: "Beta", phase: "BETA"},
        {name: "Alpha", phase: "ALPHA"},
        {name: "Infdev", phase: "INFDEV"},
        {name: "Indev", phase: "INDEV"},
        {name: "Post-Survival Test", phase: "POST_SURVIVAL_TEST"},
        {name: "Survival Test", phase: "SURVIVAL_TEST"},
        {name: "Pre-Survival Test", phase: "PRE_SURVIVAL_TEST"},
        {name: "Pre-Classic", phase: "PRE_CLASSIC"},
        {name: "Custom", phase: "CUSTOM"},
    ]

    console.assert(phaseOptions.map(opt => opt.phase).sort().toString() === ALL_VERSION_PHASES.slice().sort().toString())

    return phaseOptions
}

function Header() {
    return <div className="modal-header">
        <h1 className="modal-title fs-4" id="newServerModalLabel">New Server</h1>
        <button type="button" className="btn-close" data-bs-dismiss="modal" aria-label="Close"></button>
    </div>;
}

function Footer(props: {createServerOptions: CreateServerOptions | undefined, onSubmit: (success: boolean) => void}) {
    return <div className="modal-footer">
        <button type="button" className="btn btn-secondary" data-bs-dismiss="modal">Close</button>
        <button type="button" className="btn btn-primary" data-bs-dismiss="modal" disabled={props.createServerOptions === undefined} onClick={() => {
            if (props.createServerOptions !== undefined) {
                createServer(props.createServerOptions).then(props.onSubmit);
            } else {
                props.onSubmit(false);
            }
        }}>
            Done
        </button>
    </div>;
}
