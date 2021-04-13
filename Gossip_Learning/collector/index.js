const express = require('express');
const bodyParser = require("body-parser");
const app = express();
const port = 3000;
app.use(bodyParser.urlencoded({ extended: true }));

// Set the interval of the timeslots for data aggregation
let intervalCollectionSeconds = 5;
if (typeof process.argv[2] !== 'undefined') {
    intervalCollectionSeconds = parseInt(process.argv[2])
}

// Control variables
let results = {};
let curIndex = 0;
let curSecondResult = [];
let countNoSamples = 0;
let startDate = null;
let curMaxIteration = 0;

// The Post endpoint which receives the loss data from the gossip nodes
app.post('/collect', (req, res) => {
    console.log("Collected a result!")
    if (typeof req.body.loss !== 'undefined' && typeof req.body["current-iteration"] !== 'undefined'){
        if (startDate == null) {
            startDate = new Date()
        }
        const secondsFromStart = getTimeFromStart();
        const postIndex = Math.floor(secondsFromStart)

        // Check if we are in the next timeslot if so update
        if (postIndex > curIndex) {
            saveCurrentResults(curIndex);
            curSecondResult = [];
            curMaxIteration = 0;
            curIndex = Math.floor(postIndex);
        }

        const loss = parseFloat(req.body.loss)
        if (!isNaN(loss)) {
            curSecondResult.push(loss)
        } else {
            console.log("Whoops the loss is not a number!")
        }
        const modelIteration = parseInt(req.body["current-iteration"])
        if(!isNaN(modelIteration)) {
            if (modelIteration > curMaxIteration) {
                curMaxIteration = modelIteration;
            }
        } else {
            console.log("Whoops the model iteration is not an integer!")
        }

        countNoSamples++;
    }
    res.send('succes');
})

// The endpoint in order to reset the collector for a new experiment.
app.get('/reset-collector', (req, res) => {
    results = {};
    curIndex = 0;
    curSecondResult = [];
    countNoSamples = 0;
    startDate = null;
    curMaxIteration = 0;

    res.send('succes');
})

// The endpoint in order to reset the collector and set a new timeslot interval for a new experiment.
app.get('/reset-collector/:interval', (req, res) => {
    if (typeof req.params.interval !== 'undefined') {
        const newInterval = parseFloat(req.params.interval)
        if (!isNaN(newInterval)) {
            intervalCollectionSeconds = newInterval
        } else {
            console.log("Whoops the loss is not a number!")
        }

    }
    results = {};
    curIndex = 0;
    curSecondResult = [];
    countNoSamples = 0;
    startDate = null;
    curMaxIteration = 0;

    res.send('succes');
})

// Collect the data in JSON format
app.get('/collect', (req, res) => {
    res.json({intervalSeconds: intervalCollectionSeconds, results});
})

// Collect the data in JSON format
app.get('/', (req, res) => {
    res.json({intervalSeconds: intervalCollectionSeconds, results});
})

// Start the server and listen on the set port
app.listen(port, () => {
    console.log(`Collector server running and listening on port:${port}`)
})

// Calculate the time from start based on the timeslot interval
function getTimeFromStart() {
    const now = new Date
    let dif = now.getTime() - startDate.getTime();
    return dif / (1000 * intervalCollectionSeconds) 
}

// Calculate the average of the current timeslot
function saveCurrentResults(curIndex) {
    if (curSecondResult.length > 0) {
        const average = curSecondResult.reduce((a, b) => a + b, 0) / curSecondResult.length;
        const lengthSection = curSecondResult.length
        results[curIndex] = {average, curMaxIteration, lengthSection, countNoSamples}
    }
}