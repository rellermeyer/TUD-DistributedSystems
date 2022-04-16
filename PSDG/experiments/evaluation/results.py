from functions.readingFunctions import *
from functions.getSizeOfFiles import *
from functions.checkSubscriptions import *
from functions.checkPublications import *
import statistics
import json

def getSumOfField(dictionary, field):
    result = 0
    for nodeId in dictionary:
        if field in dictionary[nodeId]:
            if dictionary[nodeId][field] > 0:
                result += dictionary[nodeId][field]
    result = round(result, 2)

    return result


def getStandardDeviation(dictionary, field):
    results = list()
    for nodeId in dictionary:
        if field in dictionary[nodeId]:
            if isinstance(dictionary[nodeId][field], list):
                results += dictionary[nodeId][field]
            else:
                results.append(dictionary[nodeId][field])

    return round(statistics.stdev(results), 2)


def getRunResult(RUNS_DIRECTORY):
    publisherNodes = ["13", "14"]
    subscriberNodes = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12"]

    # Measure the traffic per node
    trafficGenerated = {}
    trafficGenerated["totalSent"] = 0

    # Easiest one cause we do not need to follow paths!
    sentAdvertisements = []
    sentUnadvertisements = []
    receivedAdvertisements = {}
    receivedUnadvertisements = {}

    # We have to take into account who wants which publication and if it reaches the destinations
    sentPublications = []
    receivedPublications = {}
    retransPublications = []

    # Take into account only if it reached the destination!
    sentSubscriptions = {}
    sentUnsubscriptions = {}
    receivedSubscriptions = {}
    receivedUnsubscriptions = {}

    for nodeId in os.listdir(RUNS_DIRECTORY):
        if "root" in nodeId:
            continue
        nodeDirectory = RUNS_DIRECTORY+'/'+nodeId

        if nodeId in publisherNodes:
            sentAdvertisements += readSentAdvertisements(nodeDirectory)
            receivedSubscriptions[nodeId] = readReceivedSubscriptions(nodeDirectory)
            receivedUnsubscriptions[nodeId] = readReceivedUnsubscriptions(nodeDirectory)
            sentPublications += readSentPublications(nodeDirectory)
            sentUnadvertisements += readSentUnadvertisements(nodeDirectory)
            retransPublications += readTimeoutACK(nodeDirectory)

        elif nodeId in subscriberNodes:
            receivedPublications[nodeId] = readReceivedPublications(nodeDirectory)
            sentSubscriptions[nodeId] = readSentSubscriptions(nodeDirectory)
            sentUnsubscriptions[nodeId] = readSentUnsubscriptions(nodeDirectory)

        else:
            receivedAdvertisements[nodeId] = readReceivedAdvertisements(nodeDirectory)
            receivedUnadvertisements[nodeId] = readReceivedUnadvertisements(nodeDirectory)

        trafficGenerated[nodeId] = getTrafficNode(nodeDirectory)
        trafficGenerated["totalSent"] += trafficGenerated[nodeId]['sent']
        for key in trafficGenerated[nodeId]:
            trafficGenerated[nodeId][key] = format_bytes(trafficGenerated[nodeId][key])

    # Subscriptions
    validSubscriptions, potentialSubscriptions = getValidSubscriptions(
        sentSubscriptions, sentAdvertisements, sentUnadvertisements)

    unsubscriptionsSummary = getSummaryUnsubscriptions(sentUnsubscriptions)

    # Publications
    expectedPublications = getExpectedPublications(sentPublications, validSubscriptions, unsubscriptionsSummary)
    potentialExpectedPublications = getExpectedPublications(
        sentPublications, potentialSubscriptions, unsubscriptionsSummary)

    subscribersEmpty = {}

    for node in subscriberNodes:
        subscribersEmpty[node] = {}

    # Stadistics for the subscribers of the system
    subscriberStats = checkPublications(expectedPublications, receivedPublications,
                                        retransPublications, subscribersEmpty)
    potentialPublications = checkPotentialPublications(expectedPublications, potentialExpectedPublications)

    # Creation of the summary
    numberSubs = len(subscriberNodes)

    summary = {}
    summary['n_nodes'] = len(os.listdir(RUNS_DIRECTORY))
    summary['n_publishers'] = len(publisherNodes)
    summary['n_subscribers'] = numberSubs
    summary['n_brokers'] = summary['n_nodes'] - len(publisherNodes) - len(subscriberNodes)

    summary['avg_wait_time'] = round(getSumOfField(subscriberStats, "waitTime")/numberSubs, 2)
    summary["std_wait_time"] = getStandardDeviation(subscriberStats, "waitTimes")

    summary['traffic_sent'] = format_bytes(trafficGenerated["totalSent"])
    summary['traffic_sent_bytes'] = trafficGenerated["totalSent"]
    summary['avg_traffic_sent'] = format_bytes(trafficGenerated["totalSent"]/summary['n_nodes'])

    summary['recv_pubs'] = getSumOfField(subscriberStats, "receivedPubs")
    summary['avg_recv_pubs'] = round(getSumOfField(subscriberStats, "receivedPubs")/numberSubs, 2)
    summary["std_recv_pubs"] = getStandardDeviation(subscriberStats, "receivedPubs")

    summary['miss_pubs'] = getSumOfField(subscriberStats, "missingPubs")
    summary['avg_miss_pubs'] = round(getSumOfField(subscriberStats, "missingPubs")/numberSubs, 2)
    summary["std_miss_pubs"] = getStandardDeviation(subscriberStats, "missingPubs")
    summary['avg_miss_rate'] = round(getSumOfField(subscriberStats, "missRate")/numberSubs, 2)

    # This has to be uncommented for potential publications

    # summary['avg_pot_pubs_miss'] = getSumOfField(potentialPublications, "potentialPubs")/numberSubs
    # summary['avg_pot_pubs_miss_rate'] = getSumOfField(potentialPublications, "potentialPubsRate")/numberSubs

    # avg_recv_pubs = summary['avg_recv_pubs']
    # if avg_recv_pubs < 1:
    #     avg_recv_pubs = 1

    # summary['avg_rtr_pubs_to_normal'] = round(
    #     summary['avg_success_rtr_pubs']/avg_recv_pubs, 2)

    runName = RUNS_DIRECTORY.split("/")[-2].split("_")[-1]+"_"+RUNS_DIRECTORY.split("/")[-1]

    with open(f'results/{runName}.json', 'w') as file_write:
        file_write.write(json.dumps(summary, indent=4))

    with open(f'results/{runName}_per_node.json', 'w') as file_write:
        file_write.write(json.dumps(subscriberStats, indent=4))

    return summary
