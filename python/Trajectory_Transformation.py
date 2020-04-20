# ## What Does This Script Do?
# 
# This script transform each trajectory to a signal form using a Markov Graph as described in Section 3.3 of the following paper:
# * [Discovery of Driving Patterns by Trajectory Segmentation](https://arxiv.org/pdf/1804.08748.pdf)
# 
# __Input__: a trajectory dataset, where each trajectory is a sequence of data points, and each data point includes the following attributes:
# * `TripId`: id of trajectory/trip (a string)
# * `Time_Step`: time step identifier for a record of a trajectory (an integer)
# * `Speed(km/h)`: ground speed (a float)	
# * `Acceleration(m/s^2)`: acceleration of vehicle (a float)
# * `Heading_Change(degrees)`: change of heading with respect to previous time step (a float)
# * `Latitude`: latitude coordinate of GPS (a float)
# * `Longitude`: longitude coordinate of GPS (a float)
# 
# Intput data must be specified in terms of a single csv file named as `segmentation_trips.csv`, and the input file must be placed inside `/data` directory. 
# 
# __Output__: this notebook generates a single `csv` file named as `ProbabilisticDissimilarities.csv` which will be written inside the `/prerequisiteFiles` folder. This file includes the following attributes:
# * `TripId`: id of trajectory (a string)
# * `TimeStep`: time step identifier for a record of a trajectory (an integer)
# * `ProbDissimilarity`: probability dissimilarity value for the current record of a trajectory (a float). This is the signal value for a record. 
# * `Lat`: latitude coordinate of GPS (a float)
# * `Lng`: longitude coordinate of GPS (a float)
# * `Speed`: ground speed in km/h (a float)
# * `Acceleration`: acceleration in m/s^2 (a float)
# * `Heading`: change of heading with respect to previous time step in degrees (a float)


import numpy as np


# ### Load Trajectory Data

class tripTuple:
    def __init__(self, time_step, speed, acceleration, heading, latitude, longitude):
        self.speed = speed
        self.acceleration = acceleration
        self.heading = heading
        self.lat = latitude
        self.lng = longitude
        self.time_step = time_step

def load_trajectory_data():

    reader = open('data/segmentation_trips.csv', 'r')
    tripData = {}


    header = True
    for line in reader:
        if header:
            header = False
            continue

        try:
            # TripId,Time_Step,Speed(km/h),Acceleration(m/s^2),Heading_Change(degrees),Latitude,Longitude
            parts = line.replace('\r','').replace('\n','').split(',')

            tr = tripTuple(int(parts[1]), float(parts[2]), float(parts[3]), float(parts[4]),
                          float(parts[5]), float(parts[6]))

            lst = []
            if parts[0] in tripData:
                lst = tripData[parts[0]]
            lst.append(tr)
            tripData[parts[0]] = lst

        except:
            pass
    
    return tripData


# ### Calculate Probabilistic Distance

def get_euclidean_distance(first, second):
    distance = 0

    maxSpeed = 180 # it was 178 previously
    minSpeed = 0
    maxAccel = 19
    minAccel = -16
    maxAngle = 180
    minAngle = 0

    firstParts  = first.split('&')
    secondParts = second.split('&')

    f_angle = float(firstParts[2])
    s_angle = float(secondParts[2])
    f_modified = 360 - f_angle
    s_modified = 360 - s_angle

    headingDistance = 0;

    if (np.abs(f_angle-s_angle) > np.abs(f_angle + s_modified)):
        headingDistance = f_angle + s_modified
    elif (np.abs(f_angle-s_angle) > np.abs(f_modified + s_angle)):
        headingDistance = f_modified + s_angle
    else:
        headingDistance = np.abs(f_angle - s_angle)
    headingDistance = (headingDistance - minAngle)/(maxAngle - minAngle)

    firstSpeed = (float(firstParts[0]) - minSpeed)/(maxSpeed - minSpeed)
    firstAccel = (float(firstParts[1]) - minAccel)/(maxAccel - minAccel)

    secondSpeed = (float(secondParts[0]) - minSpeed)/(maxSpeed - minSpeed)
    secondAccel = (float(secondParts[1]) - minAccel)/(maxAccel - minAccel)

    distance = np.sqrt(np.power(firstSpeed - secondSpeed, 2) + np.power(firstAccel - secondAccel, 2) + np.power(headingDistance, 2))

    return distance

def getProbabilisticDistance(crntState, prevState, transProb, totalCounter):
    distance = 0
    totalCounter += 1
    
    for state in transProb:
        if state == prevState:
            continue
        distance += (get_euclidean_distance(crntState, state) * transProb[state])
    
    return distance, totalCounter

def compute_probabilistic_dissimilarities():
    
    zeroCounter  = 0
    totalCounter = 0
    avgTransProb = 0   # this value will be used for missing transition probability; i.e. for those with 0 prob.
    
    # load trip data
    tripData = load_trajectory_data()

    # Load State Transition Probability
    transProb = {}
    count = 0
    with open('prerequisiteFiles/probsRegularized.csv', 'r') as reader:
        for line in reader:
            parts = line.replace('\r','').replace('\n','').split(',')
            prob = float(parts[2])
            avgTransProb += prob
            count += 1

            trans = {}
            if parts[0] in transProb:
                trans = transProb[parts[0]]
            trans[parts[1]] = prob
            transProb[parts[0]] = trans

    avgTransProb /= count
    print ('Probability values are loaded!')


    # specify output file
    writer = open('prerequisiteFiles/ProbabilisticDissimilarities.csv', 'w')

    # set transition threshold 
    minLength = 1;
    # set Angle bin size
    angleBinSize = 1;
    # set top candidates for comparison
    numberOfTrips = 0;

    writer.write('TripId,TimeStep,ProbDissimilarity,Lat,Lng,Speed,Acceleration,Heading\n')
    for trip in tripData:
        crntTripLength = len(tripData[trip])
        down = 0
        up = crntTripLength

        if crntTripLength < minLength:
            continue

        ## get the most recent available heading values
        ## Why getting last heading? Currently, we use heading as values between 0 to 359. So, if have no GPS coordinates for some time ...
        ##  during trip, then we no longer can use 180. So, last heading gives the closest (time base closeness) available heading value to be used as an estimation

        numberOfTrips += 1
        print ('Transforming {} of size {}'.format(trip, len(tripData[trip])))
         
        writer.write('{},{},{},{},{},{},{},{}\n'.format(
            trip, 
            tripData[trip][down].time_step, 
            0.0,
            tripData[trip][down].lat,
            tripData[trip][down].lng,
            tripData[trip][down].speed,
            tripData[trip][down].acceleration,
            tripData[trip][down].heading
        ))
        
        prevState = '{}&{}&{}'.format(int(tripData[trip][down].speed), 
                                        int(np.round(tripData[trip][down].acceleration*.25))/.25, 
                                        int(tripData[trip][down].heading))

        for i in range(down+1, up):
            crntState = '{}&{}&{}'.format(int(tripData[trip][i].speed), 
                                        int(np.round(tripData[trip][i].acceleration*.25))/.25, 
                                        int(tripData[trip][i].heading))
            distance = 0

            if crntState != prevState:
                if prevState in transProb:
                    distance,totalCounter = getProbabilisticDistance(crntState, prevState, transProb[prevState], totalCounter)
                else:
                    zeroCounter += 1
                    distance = avgTransProb

            writer.write('{},{},{},{},{},{},{},{}\n'.format(
                trip, 
                tripData[trip][i].time_step, 
                distance,
                tripData[trip][i].lat,
                tripData[trip][i].lng,
                tripData[trip][i].speed,
                tripData[trip][i].acceleration,
                tripData[trip][i].heading
            ))

            prevState = crntState

    writer.close()
    print ('\nNumber of processed trips: ', numberOfTrips)
    
    print ('\n% time that PMD was zero due to non existing states: {:.2f}'.format(float(zeroCounter*100.0/totalCounter)))
    print ('zeroCounter: {} \ntotalCounter: {}'.format(zeroCounter, totalCounter))


# ### Transforming Trajectories to Probabilistic Dissimilarity Space (aka Generating Signals)

compute_probabilistic_dissimilarities()





