from collections import defaultdict
from functions.readingFunctions import getIdMessage

def getExpectedPublications(sentPublications, validSubscriptions, unsubscriptionsSummary):
    expectedPublications = defaultdict(list)
    for nodeId, subscriptions in validSubscriptions.items():
        copiedPublications = sentPublications.copy()

        for subscription in subscriptions:
            for publication in copiedPublications:
                subscriptionID = getIdMessage(subscription["ID"])

                if subscriptionID in unsubscriptionsSummary[nodeId]:
                    timestampUnsub = unsubscriptionsSummary[nodeId][subscriptionID]
                    if timestampUnsub < publication["timestamp"]:
                        continue

                if subscription["timestamp"] < publication["timestamp"]:
                    if subscription["pClass"] == publication["pClass"]:
                        operation = publication["pAttributes"]["_1"]
                        if operation == subscription["pAttributes"]["_1"] or operation == "ne":
                            valid = False
                            adValue = publication["pAttributes"]["_2"]
                            subValue = subscription["pAttributes"]["_2"]

                            if operation == "gt":
                                if(adValue >= subValue):
                                    valid = True
                            elif operation == "lt":
                                if(adValue <= subValue):
                                    valid = True
                            elif operation == "e" or operation == "ne":
                                if(adValue == subValue):
                                    valid = True

                            if valid and publication not in expectedPublications[nodeId]:
                                expectedPublications[nodeId].append(publication)
                                copiedPublications.remove(publication)
                                # break
    return expectedPublications


def checkPublications(expectedPublications, receivedPublications, retransPublications, subscriberStats):

    def getTimestampsPublications(publications):
        timestampsPubs = {}
        for publication in publications:
            pubId = getIdMessage(publication['ID'])
            if pubId not in timestampsPubs:
                timestampsPubs[pubId] = publication['timestamp']
        return timestampsPubs

    for nodeId in expectedPublications:
        if nodeId in receivedPublications:
            expectedTimestamps = getTimestampsPublications(expectedPublications[nodeId])
            receivedTimestamps = getTimestampsPublications(receivedPublications[nodeId])
            subscriberStats[nodeId]['waitTimes'] = list()

            averageWait = 0
            receivedCounter = 0
            missingCounter = 0
            succesRetrans = 0
            failRetrans = 0
            for pubId in expectedTimestamps:
                if pubId in receivedTimestamps:
                    waitingTime = receivedTimestamps[pubId]-expectedTimestamps[pubId]
                    averageWait += waitingTime
                    receivedCounter += 1
                    if pubId in retransPublications:
                        succesRetrans += 1
                    subscriberStats[nodeId]['waitTimes'].append(waitingTime)
                else:
                    missingCounter += 1
                    if pubId in retransPublications:
                        failRetrans += 1
            subscriberStats[nodeId]['missingPubs'] = missingCounter
            subscriberStats[nodeId]['receivedPubs'] = receivedCounter
            subscriberStats[nodeId]['missRate'] = round(missingCounter/(receivedCounter+missingCounter), 2)

            if receivedCounter == 0:
                receivedCounter = 1

            subscriberStats[nodeId]["totalRetrans"] = succesRetrans+failRetrans
            subscriberStats[nodeId]["succesRetrans"] = succesRetrans
            subscriberStats[nodeId]["failRetrans"] = failRetrans
            subscriberStats[nodeId]["successRetrans"] = succesRetrans / \
                (succesRetrans+failRetrans) if succesRetrans > 0 else 0
            subscriberStats[nodeId]["retransRatio"] = receivedCounter/succesRetrans if succesRetrans > 0 else 0

            subscriberStats[nodeId]['waitTime'] = round(averageWait/receivedCounter, 2)

    return subscriberStats


def checkPotentialPublications(expectedPublications, potentialExpectedPublications):
    potentialPublications = defaultdict(lambda: {})

    def getIdsPublications(publications):
        ids = []
        for publication in publications:
            if publication['ID'] not in ids:
                ids.append(getIdMessage(publication['ID']))
        return ids

    for nodeId in expectedPublications:
        if nodeId in potentialExpectedPublications:

            expectedIds = set(getIdsPublications(expectedPublications[nodeId]))
            potentialIds = set(getIdsPublications(potentialExpectedPublications[nodeId]))
            potentialPubs = len(potentialIds.symmetric_difference(expectedIds))

            potentialPublications[nodeId]["potentialPubs"] = potentialPubs
            potentialPublications[nodeId]["potentialPubsRate"] = round(
                potentialPubs/(potentialPubs+len(expectedIds)), 2)

    return potentialPublications
