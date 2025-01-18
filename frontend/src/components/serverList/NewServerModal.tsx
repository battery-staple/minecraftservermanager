import {createServer, CreateServerOptions} from "../../networking/backendAPI/Servers";
import {useEffect, useState} from "react";
import {ALL_VERSION_PHASES, Runner, VersionPhase} from "../../APIModels";
import {Modal} from "react-bootstrap";
import {Setter} from "../../util/React";
import {getAllRunners} from "../../networking/backendAPI/Runners";
import {Accessed, isError, isPresent} from "../../networking/backendAPI/AccessError";

const defaultVersionPhase = "RELEASE"

export function NewServerModal(props: { isShowing: boolean, setShowing: (showing: boolean) => void }) {
    const [error, setError] = useState<string | null>(null)
    const [name, setName] = useState<string | null>(null)
    const [versionPhase, setVersionPhase] = useState<VersionPhase>(defaultVersionPhase)
    const [version, setVersion] = useState<string | null>(null)
    const [runnerUUID, setRunnerUUID] = useState<string | null>(null)

    const reset = () => {
        setError(null)
        setName(null)
        setVersion(null)
        setVersionPhase(defaultVersionPhase);
    }

    // When props change
    useEffect(() => {
        if (!props.isShowing) { // When this becomes hidden
            reset()
        }
    }, [props.isShowing]);

    let creationOptions = null;
    if (name !== null && version !== null && versionPhase !== null && runnerUUID !== null) {
        creationOptions = {
            name: name,
            version: version,
            versionPhase: versionPhase,
            runnerUUID: runnerUUID,
        }
    }

    return (
        <Modal id="newServerModal" className={"new-server-modal"} show={props.isShowing} backdrop={"static"} centered
               onHide={() => props.setShowing(false)}
               tabIndex={-1} aria-labelledby="newServerModalLabel" aria-hidden="true">
            <Header/>
            <Body name={name} setName={setName}
                  versionPhase={versionPhase} setVersionPhase={setVersionPhase}
                  version={version} setVersion={setVersion}
                  runnerUUID={runnerUUID} setRunnerUUID={setRunnerUUID}
                  error={error}
            />
            <Footer createServerOptions={creationOptions} setShowing={props.setShowing} setError={setError}/>
        </Modal>
    )
}

function Body(props: {
    name: string | null, setName: Setter<string | null>,
    versionPhase: VersionPhase, setVersionPhase: Setter<VersionPhase>,
    version: string | null, setVersion: Setter<string | null>,
    runnerUUID: string | null, setRunnerUUID: Setter<string | null>,
    error: string | null
}) {
    const [runners, setRunners] = useState<Accessed<Runner[]>>("loading")
    useEffect(() => {
        getAllRunners()
            .then(runners => {
                setRunners(runners)

                if (runners[0] !== undefined) {
                    props.setRunnerUUID(runners[0].uuid)
                }
            })
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    return <Modal.Body>
        <form>
            {/* Name */}
            <div className="form-group">
                <label htmlFor="name">Name: </label>
                <input name="name" type="text" value={props.name ?? ""}
                       onChange={(e) => props.setName(e.target.value)}/>
            </div>
            {/* Version Phase */}
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
            {/* Version */}
            <div className="form-group"> { /* TODO: autocomplete (dynamic based on phase) */}
                <label htmlFor="version">Version: </label>
                <input name="version" type="text" value={props.version ?? ""}
                       onChange={(e) => props.setVersion(e.target.value)}/>
            </div>
            {/* Runner */}
            <div className="form-group">
                <label htmlFor="runner">Runner: </label>
                <select name="runner"
                        value={props.runnerUUID ?? ""}
                        disabled={isError(runners)}
                        onChange={(e) => props.setRunnerUUID(e.target.value)}>
                    {
                        isPresent(runners) ?
                            runners.map(({name, uuid}) =>
                                <option value={uuid} key={uuid}>
                                    {name}
                                </option>)
                            : null
                    }
                </select>
            </div>
        </form>
        {props.error !== null ? <p className="new-server-modal-error">{props.error}</p> : null}
    </Modal.Body>
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
        {name: "Classic", phase: "CLASSIC"},
        {name: "Custom", phase: "CUSTOM"},
    ]

    console.assert(phaseOptions.map(opt => opt.phase).sort().toString() === ALL_VERSION_PHASES.slice().sort().toString())

    return phaseOptions
}

function Header() {
    return <Modal.Header closeButton>
        <h1 className="modal-title fs-4" id="newServerModalLabel">New Server</h1>
    </Modal.Header>;
}

function Footer(props: {createServerOptions: CreateServerOptions | null, setShowing: (showing: boolean) => void, setError: (error: string | null) => void}) {
    const canSubmit = props.createServerOptions !== null

    return <Modal.Footer>
        <button type="button" className="btn btn-secondary" onClick={() => props.setShowing(false)}>Close</button>
        <button type="button" className="btn btn-primary" disabled={!canSubmit} onClick={() => {
            console.assert(props.createServerOptions !== null, "Button should be disabled if createServerOptions === null")
            if (props.createServerOptions !== null) {
                props.setError(null)
                
                createServer(props.createServerOptions).then(success => {
                    if (success) {
                        props.setShowing(false);
                    } else {
                        props.setError("Failed to create server."); // TODO: better message
                    }
                });
            }
        }}>
            Done
        </button>
    </Modal.Footer>
}
