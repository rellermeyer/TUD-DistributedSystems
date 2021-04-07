# The Collector
Simple express server which receives the loss results by POST requests.
Results are aggregated in timeslots and the average is computed.
By default they will be aggregated in timeslots of 5 seconds.

#How to run
yarn install

yarn start <optional amount of seconds for the timeslots>

e.g.

yarn start 20

#Endpoints
In the default case the host will be localhost:3000.

Results can be posted to "/collect" with a POST request where the loss value 
is in the body of the POST request. 

Results can be collected by a GET request on the endpoints: "/" and "/collect"

The collector can be reset by a GET request to "/reset-collector"

Additionally one can reset the collector and set a new timeslot interval
with the endpoint "/reset-collector/{new timeslot interval}", where the param is a number representing seconds.
e.g.

"/reset-collector/5"

"/reset-collector/0.2"