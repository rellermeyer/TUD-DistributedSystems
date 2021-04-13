import React, {useEffect, useState} from 'react';
import logo from './logo.svg';
import './App.css';

/**
 * Top-level component which shows the cohorts and their state.
 */
function App() {
    const [network, setNetwork] = useState<Record<string, { value: string }>>();

    // Setup the network to the cohorts.
    useEffect(() => {
        if (network === undefined) {
            fetch("/api/network").then(response => {
                response.json().then(data => {
                    setNetwork(data);
                })
            })
        }
    })

    return (
        <div className="App">
            <body>
            <h3>Network</h3>
            <table>
                <thead>
                <tr>
                    <th>Address</th>
                    <th>State</th>
                </tr>
                </thead>
                <tbody>

                {Object.entries(network ?? {}).map(([cohort, {value}]) => (
                    <tr key={cohort}>
                        <td>{cohort}</td>
                        <td>{value}</td>
                    </tr>
                ))}
                </tbody>
            </table>
            <h3>Data</h3>
            {Object.entries(network ?? {}).map(([cohort, {}]) => (
                <CohortInput key={cohort} cohort={cohort}/>
            ))}
            </body>
        </div>
    );
}

/**
 * Input element to enter data to a cohort.
 * @param param0  cohort: the address of the cohort.
 */
function CohortInput({cohort}: { cohort: string }): JSX.Element {
    const [id, setId] = useState<number>();
    const [data, setData] = useState<string>();
    const [retrieved, setRetrieved] = useState<string>();

    return (
        <div>
            <h5>{cohort}</h5>
            <input type="text" onChange={evt => setData(evt.target.value)} value={data}/>
            <input type="number"  onChange={evt => setId(parseInt(evt.target.value))} value={id}/>
            <button onClick={() => {
                fetch(`/api/store/${cohort}/${id}`, { method: "POST", body: data}).then(() => {

                })
            }}>Store</button>

            <input type="number"  onChange={evt => setId(parseInt(evt.target.value))} value={id}/>
            <button onClick={() => {
                fetch(`/api/retrieve/${cohort}/${id}`).then(result => result.json().then(retrieved => {
                    setRetrieved(JSON.stringify(retrieved))
                }))
            }}>Retrieve</button>
            <p>{retrieved}</p>
        </div>
    )
}

export default App;
