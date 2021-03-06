from __future__ import division
import csv
import math
import argparse

def mpcTree(guessFileName, data, burnin, mpcTreeName, hosts, burninInLines):

    readData = csv.reader(open(guessFileName), dialect=csv.excel_tab)

    currentLine = readData.next()

    while currentLine[0].startswith("#"):
        currentLine = readData.next()

    # no header line

    currentLine = readData.next()

    hosts.insert(0, 'state')
    stateData = list()
    totalStates = 0
    totalCountedStates = 0
    while currentLine is not None:
        totalStates += 1
        if burninInLines:
            burningIn = totalStates < burnin
        else:
            burningIn = int(currentLine[0]) < burnin

        if not burningIn:
            totalCountedStates += 1
            referenceDict = dict()
            for i in range(len(hosts)):
                referenceDict[hosts[i]] = currentLine[i]
            stateData.append(referenceDict)

        try:
            currentLine = readData.next()
        except StopIteration:
            currentLine = None
    for network in stateData:
        totalLogParentCredibility = 0
        for i in range(1, len(hosts)):
            currentHost = hosts[i]
            logParentCredibility = 0
            for guessDict in data:
                if guessDict["name"] == currentHost:
                    logParentCredibility = math.log((guessDict[network[currentHost]])/totalCountedStates)
            totalLogParentCredibility = totalLogParentCredibility + logParentCredibility
        network['TLPC']=totalLogParentCredibility
    currentHighestTLPC = float('-inf')
    currentBestNetworkMPC = None
    for network in stateData:
        if network['TLPC']>currentHighestTLPC:
            print 'State '+network['state']+": new highest credibility, exp(" + str(network['TLPC']) + ")"
            currentHighestTLPC = network['TLPC']
            currentBestNetworkMPC = network
    print
    writeData = csv.writer(open(mpcTreeName, 'w'))

    mpcBestGuessLine = list()
    mpcBestGuessLine.append('Parent')
    for i in range(1, len(hosts)):
        mpcBestGuessLine.append(currentBestNetworkMPC[hosts[i]])
    individualMultCredLine = list()
    individualMultCredLine.append('MPC credibility')


    for i in range(1, len(hosts)):
        for guessDict in data:
            if guessDict["name"]==hosts[i]:
                individualMultCredLine.append(str(guessDict[currentBestNetworkMPC[hosts[i]]] / totalCountedStates))

    hosts[0] = "Child"
    for case in range(len(hosts)):
        line = [hosts[case], mpcBestGuessLine[case], individualMultCredLine[case]]
        writeData.writerow(line)
    print


def getHostList(guessFileName):
    readData = csv.reader(open(guessFileName), dialect=csv.excel_tab)

    currentLine = readData.next()

    while currentLine[0].startswith("#"):
        currentLine = readData.next()

    headers = currentLine
    headers.remove("state")
    for index, item in enumerate(headers):
        if item.endswith('_infector'):
            headers[index] = item[:-9]
    return headers


def getGuesses(guessFileName, headers, burnin, burninInLines):
    readData = csv.reader(open(guessFileName), dialect=csv.excel_tab)

    currentLine = readData.next()

    while currentLine[0].startswith("#"):
        currentLine = readData.next()

    data = list()

    for hostName in headers:
        currentDict = dict()
        currentDict['name']=hostName
        data.append(currentDict)
    for currentDict in data:
        for hostName in headers:
            currentDict[hostName]=0
        currentDict['Start']=0

    # no header line

    currentLine = readData.next()

    totalStates = 0

    while currentLine!=None:
        totalStates += 1

        if burninInLines:
            burningIn = totalStates < burnin
        else:
            burningIn = int(currentLine[0]) < burnin

        if not burningIn:
            for i in range(1, len(currentLine)):
                for currentDict in data:
                    if currentDict['name']==headers[i-1]:
                        currentDict[currentLine[i]]=currentDict[currentLine[i]]+1
        try:
            currentLine=readData.next()
        except StopIteration:
            currentLine=None
    return data

def main():
    burninInLines = True

    parser = argparse.ArgumentParser("Processes a BEAST transmission tree output file to obtain the maximum parent"
                                     " credibility transmisstion tree")
    parser.add_argument("beastOutput", help="Name of a BEAST transmission tree output file")
    parser.add_argument("outputName", help="CSV file to which the MPC tree is to be written")
    parser.add_argument("-b", "--burnin", default=1000, help="Integer: burnin (default=1000 lines)")
    parser.add_argument("-t", "--burninInStates", dest='burninInLines', action='store_false',
                        help="If present, the burnin refers to the number of MCMC states. Otherwise, it refers to "
                             "the number of lines in the log file.")
    parser.set_defaults(burninInLines=True)

    args = parser.parse_args()

    print "Reading list of child hosts"
    childHostList = getHostList(args.beastOutput)
    print "Reading BEAST output"
    guesses = getGuesses(args.beastOutput, childHostList, int(args.burnin), args.burninInLines)
    print "Finding maximum parent credibility network (output to "+args.outputName+")"
    mpcTree(args.beastOutput, guesses, int(args.burnin), args.outputName, childHostList, args.burninInLines)

if __name__ == '__main__':
    main()