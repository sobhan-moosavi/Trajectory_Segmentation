# ## What Does This Script Do?

# This script uses an input trajectory dataset to create a __Markov Graph__ as described in section 3.2 of the following paper:
# * [Discovery of Driving Patterns by Trajectory Segmentation](https://arxiv.org/pdf/1804.08748.pdf)
# 
# __Input__: a trajectory dataset, where each trajectory is a sequence of record, and each record has the following attributes:
# * `trip_id` (a string)
# * `time_step` (an integer)
# * `speed` (an integer based on km/h)
# * `acceleration` (a float based on m/s^2)
# * `heading change` (an integer based on degree)
# 
# Intput data must be specified in terms of a single csv file named as `graph_trips.csv`, and the input file must be placed inside `/data` directory. 
# 
# __Outputs__: this code generates several csv files as output inside `/prerequisiteFiles` directory :
# * `transitionsAdv.csv`: this file contains existing state transitions extracted from `graph_trips.csv`
# * `probsAdv.csv`: this file contains probability of transitions or the Markov graph
# * `probsRegularized.csv`: this file contains Markov graph after regularization process

import numpy as np


# #### First step: explore state transitions in an input set of trajectories

def explore_all_transitions():
    reader = open('data/graph_trips.csv', 'r')
    writer = open('prerequisiteFiles/transitionsAdv.csv', 'w')

    transitions = {}
    print ('Started to discover transitions...')

    prevLine = ''
    crntLine  = ''
    header = True
    n_trajectories = set()
    
    # obtain and count all transitions
    for line in reader:
        line = line.replace('\r','').replace('\n','')

        if header: # skip the header line
            header = False
            continue

        if crntLine == '':
            crntLine = line
            continue

        prevLine = crntLine
        crntLine = line

        cParts = prevLine.split(',')
        nParts = crntLine.split(',')
        
        n_trajectories.add(cParts[0])

        try:
            if cParts[0] == nParts[0]:
                cTime = int(cParts[1])
                nTime = int(nParts[1])

                cState = cParts[2] + "&" + cParts[3] + "&" + cParts[4]  # Speed&Acc&Angle
                nState = nParts[2] + "&" + nParts[3] + "&" + nParts[4]  # Speed&Acc&Angle

                if nTime - cTime == 1:

                    destin = {}

                    if cState in transitions:
                        destin = transitions[cState]

                    if nState in destin:
                        destin[nState] = destin[nState] + 1
                    else:
                        destin[nState] = 1

                    transitions[cState] = destin

        except:
            pass

    # write transitions into output file
    print ('Started to write transitions...')
    n_trans = 0
    for f_state in transitions:
        trans = transitions[f_state]
        for s_state in trans:
            writer.write(str(f_state) + ',' + str(s_state) + ',' + str(trans[s_state]) + '\n')
            n_trans += 1

    writer.close()
    print ('Discovered {} transitions in {} trajectories!'.format(n_trans, len(n_trajectories)))
    

# #### Second step: obtain state transition probabilities

def obtain_transition_probabilities():

    print ('Started to compute probability values ...')

    # set Acceleration bin size
    accelNormalizationFactor = 0.25

    # 0: Reading transition raw values and simplify states by normalizing them using normalization factors (if any)
    reader = open('prerequisiteFiles/transitionsAdv.csv', 'r')
    stateTransitionFreq = {}
    for line in reader:
        try:
            parts = line.replace('\r','').replace('\n','').split(',')

            spdAccAngle = parts[0].split('&')
            crntSpeed = int(float(spdAccAngle[0]))
            crntAccel = np.round(float(spdAccAngle[1])/accelNormalizationFactor) * accelNormalizationFactor
            crntAngle = int(spdAccAngle[2])

            spdAccAngle = parts[1].split('&')
            nxtSpeed = int(float(spdAccAngle[0]))
            nxtAccel = np.round(float(spdAccAngle[1])/accelNormalizationFactor) * accelNormalizationFactor
            nxtAngle = int(spdAccAngle[2])

            trans = '{}&{}&{},{}&{}&{}'.format(crntSpeed,crntAccel, crntAngle,nxtSpeed,nxtAccel,nxtAngle)

            if trans in stateTransitionFreq:
                stateTransitionFreq[trans] = stateTransitionFreq[trans] + int(parts[2])
            else:
                stateTransitionFreq[trans] = int(parts[2])
        except:
            pass


    # 1: Obtain frequency of each state
    count = {}
    for states in stateTransitionFreq:
        parts = states.split(',')

        if parts[0] != parts[1]:
            if parts[0] in count:
                count[parts[0]] = count[parts[0]] + stateTransitionFreq[states]
            else:
                count[parts[0]] = stateTransitionFreq[states]


    # 2: calculate probability for state transitions
    probs = {}
    for states in stateTransitionFreq:
        parts = states.split(',')

        probsForThisState = {}
        if parts[0] in probs:
            probsForThisState = probs[parts[0]]

        if parts[0] != parts[1]:
            probsForThisState[parts[1]] = float(stateTransitionFreq[states])/count[parts[0]]
        elif float(parts[0].split('&')[1]) == 0:
            probsForThisState[parts[1]] = 1.0 # transfer of a state to itself is 1, if acceleration is 0. Otherwise, we should not have no increase/decrease in speed when acceleration is positive/negative

        probs[parts[0]] = probsForThisState



    # 3: print out probability values!
    writer = open('prerequisiteFiles/probsAdv.csv', 'w')
    for f_state in probs:
        for s_state in probs[f_state]:
            writer.write('{},{},{},{}\n'.format(f_state, s_state, probs[f_state][s_state], count[f_state]))

    writer.close()
    
    print ('All transition probabilities are calculated!')


# #### Third step: Regularization of probability graph

class State:
    def __init__(self, input):
        parts = input.split('&')
        self.speed = int(parts[0])
        self.accel = float(parts[1])
        self.heading = int(parts[2])

def wedding_cake_probability_regularization():

    maxSpeedThreshold = 3    # increase/decrease by steps of size 1.0
    maxAccelThreshold = 0.25 # increase/decrease by steps of size 0.25
    maxHeadingThreshold = 6  # Some updates on heading by steps of size 6.0 ==> this is based on Change of Heading instead of abslute heading

    influenceFactorForAccel = 2.0 # This is a relative factor regarding the influence of accel v.s speed to calculate distance between original and updated states

    state2id = {}
    id2state = {}

    # 1: load probability values
    print ('Started to load probability values...')

    probs = {}
    regularizedProbs = {}

    with open('prerequisiteFiles/probsAdv.csv', 'r') as probatility_file:
        for line in probatility_file:
            parts = line.split(',')

            if not parts[0] in state2id:
                s = State(parts[0])
                state2id[parts[0]] = len(state2id) + 1
                id2state[state2id[parts[0]]] = s
            s1 = state2id[parts[0]]

            if not parts[1] in state2id:
                s = State(parts[1])
                state2id[parts[1]] = len(state2id) + 1
                id2state[state2id[parts[1]]] = s
            s2 = state2id[parts[1]]

            trans1 = {}
            trans2 = {}

            if s1 in probs:
                trans1 = probs[s1]
                trans2 = regularizedProbs[s1]

            trans1[s2] = float(parts[2])
            trans2[s2] = float(parts[2])

            probs[s1] = trans1
            regularizedProbs[s1] = trans2


    # 2: Normalize probability values
    print ('Started to normalize/regularize probability values...')
    n_lines = 0;

    for f_state in probs:
        n_lines += 1.0
        if len(regularizedProbs)%100 == 0:
            print ('{0: <12}  #RegularizedStatesWithTransitionsFrom:{1: <10}  #RegularizedStates:{2: <10}'
                   .format(str(np.round(n_lines*100.0/len(probs), 2)) + '%', len(regularizedProbs), len(state2id)))

        s1 = id2state[f_state]
        srcSpeed = s1.speed
        srcAccel = s1.accel
        srcHead  = s1.heading

        for s_state in probs[f_state]:
            s2 = id2state[s_state]
            dstSpeed = s2.speed
            dstAccel = s2.accel
            dstHead  = s2.heading

            # Regularizing by updating the Source   
            for s in range(-maxSpeedThreshold, maxSpeedThreshold+1):
                for a in np.arange(-maxAccelThreshold, maxAccelThreshold+0.25, 0.25):
                    for h in range(-maxHeadingThreshold, maxHeadingThreshold+6, 6):
                        state = '{}&{}&{}'.format(srcSpeed+s, srcAccel+a, srcHead+h)

                        ## s*a < 0: change in Speed and Acceleration is not in the same direction
                        ## Negative speed doesn't make any sense
                        ## Negative change of heading does'nt sound.
                        if s*a<0 or srcSpeed+s<0 or srcHead+h<0:
                            continue

                        if not state in state2id:
                            _s = State(state)
                            state2id[state] = len(state2id) + 1
                            id2state[state2id[state]] = _s
                        s3 = state2id[state]

                        if s3==f_state or s3==s_state:
                            continue

                        absoluteDistanceBetweenStates = 1.0 / (np.sqrt(s*s + influenceFactorForAccel*a*a + h*h) + 1) # Adding 1 to further regularize the improvement on probability value
                        probAug = probs[f_state][s_state] * absoluteDistanceBetweenStates

                        toTheseStates = {}
                        if s3 in regularizedProbs:
                            toTheseStates = regularizedProbs[s3]

                        if s_state in toTheseStates:
                            toTheseStates[s_state] = toTheseStates[s_state] + probAug
                        else:
                            toTheseStates[s_state] = probAug

                        # Heuristic: if updated acceleration is zero, let's have self transition with probability as 1
                        if srcAccel+a == 0:
                            toTheseStates[s3] = 1.0

                        regularizedProbs[s3] = toTheseStates


            # Regularizing by updating the Destination
            for s in range(-maxSpeedThreshold, maxSpeedThreshold+1):
                for a in np.arange(-maxAccelThreshold, maxAccelThreshold+0.25, 0.25):
                    for h in range(-maxHeadingThreshold, maxHeadingThreshold+6, 6):
                        state = '{}&{}&{}'.format(dstSpeed+s, dstAccel+a, dstHead+h)

                        ## s*a < 0: change in Speed and Acceleration is not in the same direction
                        ## Negative speed doesn't make any sense
                        ## Negative change of heading does'nt sound.
                        if s*a<0 or dstSpeed+s<0 or dstHead+h<0:
                            continue

                        if not state in state2id:
                            _s = State(state)
                            state2id[state] = len(state2id) + 1
                            id2state[state2id[state]] = _s
                        s3 = state2id[state]

                        if s3==f_state or s3==s_state:
                            continue

                        absoluteDistanceBetweenStates = 1.0/(np.sqrt(s*s + influenceFactorForAccel*a*a + h*h) + 1) # Adding 1 to further regularize the improvement on probability value
                        probAug = probs[f_state][s_state] * absoluteDistanceBetweenStates

                        toTheseStates = regularizedProbs[f_state]

                        if s3 in toTheseStates:
                            toTheseStates[s3] = toTheseStates[s3] + probAug
                        else:
                            toTheseStates[s3] = probAug

                        regularizedProbs[f_state] = toTheseStates


    # 3: Print out probability values! for analysis purpose 
    writer = open('prerequisiteFiles/probsRegularized.csv', 'w')
    totalRegularizedTransitions = 0
    totalRegularizedStates = set()

    for f_state in regularizedProbs:
        
        totalRegularizedStates.add(f_state)
        
        sum = 0
        for s_state in regularizedProbs[f_state]:
            sum += regularizedProbs[f_state][s_state]
            totalRegularizedStates.add(s_state)

        if id2state[f_state].accel == 0:
            sum -= 1 # We have self transition for this case. Then, need to subtract 1 from that

        s1 = '{}&{}&{}'.format(id2state[f_state].speed, id2state[f_state].accel, id2state[f_state].heading)

        for s_state in regularizedProbs[f_state]:
            s2 = '{}&{}&{}'.format(id2state[s_state].speed, id2state[s_state].accel, id2state[s_state].heading)

            if f_state != s_state:
                writer.write('{},{},{}\n'.format(s1, s2, regularizedProbs[f_state][s_state]/sum))
            else:
                writer.write('{},{},{}\n'.format(s1, s2, 1.0))

            totalRegularizedTransitions += 1

    writer.close()
    print ('\nNumber of States (or nodes) in Final Markov Graph: ', len(totalRegularizedStates))
    print ('Number of Transitions (or edges) in Final Markov Graph: ', totalRegularizedTransitions)


# ## The Main Process of Building Markov Graph

explore_all_transitions()  # to find all existing state transitions in input trajectory set
print ('\n')
obtain_transition_probabilities()  # to find probability of transitions and create transition graph
print ('\n')
wedding_cake_probability_regularization() # to regularize transition graph 



