# ## What Does This Script Do?

# This script performs trajectory segmentation, where you can find a detailed description of segmentation process in section 3.4 of the following paper:
# * [Discovery of Driving Patterns by Trajectory Segmentation](https://arxiv.org/pdf/1804.08748.pdf)
# 
# __Input__: transformed trajectory dataset, where each record of each trajectory has the following attributes:
# * `TripId`: id of trajectory (a string)
# * `TimeStep`: time step identifier for a record of a trajectory (an integer)
# * `ProbDissimilarity`: probability dissimilarity value for the current record of a trajectory (a float). This is the signal value for a record. 
# * `Lat`: latitude coordinate of GPS (a float)
# * `Lng`: longitude coordinate of GPS (a float)
# * `Speed`: ground speed in km/h (a float)
# * `Acceleration`: acceleration in m/s^2 (a float)
# * `Heading`: change of heading with respect to previous time step in degrees (a float)
# 
# Intput data must be specified in terms of a single csv file named as `ProbabilisticDissimilarities.csv`, and the input file must be placed inside `/prerequisiteFiles` directory. 
# 
# __Outputs__: this code generates a single csv file (inside the `/output` directory) that contains trajectory segmentation results. This file has the following attributes:
# * `TripId`: id of trajectory (a string)
# * `TimeStep`: time step identifier for a record of a trajectory (an integer)
# * `Speed`: ground speed in km/h (a float)
# * `Acceleration`: acceleration in m/s^2 (a float)
# * `HeadingChange`: change of heading with respect to previous time step in degrees (a float)
# * `Lat`: latitude coordinate of GPS (a float)
# * `Lng`: longitude coordinate of GPS (a float)
# * `PMD`: probabilistic movement dissimilarity (or probability dissimilarity) value for the current record of a trajectory (a float). This is the signal value for a record. 
# * `StartOfSegment`: an indicator that specifies whether a given record is the start of a new segment or not. A value of 1 indicates that a record is start of a new segment. 

import numpy as np
import math
import time

e  = math.e
pi = math.pi
float_max_value = 1000000.0

class tripTuple:
    def __init__(self, time_step, speed, acceleration, heading, latitude, longitude):
        self.speed = speed
        self.acceleration = acceleration
        self.heading = heading
        self.lat = latitude
        self.lng = longitude
        self.time_step = time_step


# ### Calculate Delta

# Calculation of Delta for all form of segments in given trajectory
# delta_i(n_(i-1) , n_i - 1): the formula before (3) in paper! where n_(i-1) and n_i - 1 can be any possible pairs in trajectory!
# Simply, here we have a window of dynamic size which scans whole trajectory! we will find the likelihood of that windows based on... 
# ...distribution of its points. Such distribution is a normal distribution and we have the assumption of independence of nodes ...
# ... in order to make simplification in problem

def estimate_mean_std(input):
    mean = 0
    for i in input:
        mean += i
    mean /= len(input)
    
    std = 0
    for i in input:
        std += (i-mean)**2
    std = math.sqrt(std/len(input))
    
    return mean, std

def ln_of_normal_distribution(x, mean, std):
    # res = ln(N(x, mean, std)) where N stands for normal distribution. 
    # N(x, mean, std) = (1/std * (2PI)^0.5) * exp(- (x - mean)^2 / (2 * std^2) )
    try:
        res = -1 * (math.log(std * math.sqrt(2 * pi)) + (math.pow(x-mean, 2)/(2 * math.pow(std, 2))))
        return res
    except: 
        return np.nan
    
def calculateDelta(points): # vectorize calculations
    delta = np.full((len(points)-1, len(points)), float_max_value)
    
    for i in range(len(points)-1):
        for j in range(len(points)):
            if (j-i+1) >= 2: # the length of a segment should be at least 2
                mean,std = estimate_mean_std(points[i:j+1])
                # get the ln value based on points in this segment and their corresponding distribution
                value = 0
                # The goal is minimizing the delta! So, when we have standard deviation = 0, this means no changes is observing in ...
                # ...probabilistic dissimilarity values in range i to j! So, no change, no std! In other words, we have a uniform distribution...
                # ...for this range! In this way, I decided to let the delta to be 0 in such situation. 
                if std!=0:
                    mean = np.full(j+1-i, mean) # to prepare input for a vectorized  calculation
                    std  = np.full(j+1-i, std) # to prepare input for a vectorized calculation
                    v = points[i:j+1]
                    # -1 * (math.log(std * math.sqrt(2 * pi)) + (math.pow(x-mean, 2)/(2 * math.pow(std, 2))))
                    value = np.log(std * np.sqrt(2*pi)) + np.power(v-mean, 2)/(2*np.power(std, 2))
                    value = np.sum(value)
                delta[i][j] = value
    return delta
                
    
# ### Segmentation Process as a Dynamic Programming Algorithm

def dynamicProgramingSegmentation(points, Ns, delta):
    # Finding optimal segmentation by having N_s as number of existing segments in given trajectory
    # Here, we will find I_k(L) for all k = 1,2,...,N_s and for all L = 1,2,...,N
    # The approach for finding these values is based on dynamic programming formula...
    # ... which is provided as relation (4) in paper

    # initialization of I
    I = np.full((Ns, len(points)), float_max_value)
    
    # Initialization of Index
    # In addition to minimum values, we need minimum indexes which show the best breaking points for segments
    Index = np.full((Ns, len(points)), 0, dtype=int)
    
    # Now, we are going to find the optimal values
    for k in range(Ns):
        # Base case: this mean just having a single segment or I_1(L)
        # Note: here k is 0 but based on paper formulation is 1
        if k == 0:
            # Here, the assumption is L>=2; but I considered this once that delta is calculated
            for L in range(len(points)):
                I[k][L] = delta[k][L]  # Note, segments of length 0 or 1 have delta value as +infinity!
        

        # Rest of the cases (having more than one segment): Well, here is the idea of dynamic programming
        else:
            # why (k*2 + 1): look at the printed picture! I have to have some lower level for L! when I have a couple of segments, ... 
            # ...the minimum criteria is I have a segment from 0 to 1. And the 2nd segment would be from 2! So, when k=1, the min length...
            # ... for L should be 3 in order to have at least two separate segments. 
            for L in range(k*2+1, len(points)):
                min_value = float_max_value
                min_index = -1
                # I. Why (L-1)? since I want to let the last segment be at least from L-1 to L. That is, the minimum length is 2!
                # II. Look, here I have equality condition for L not just lower than! since L is index of upper loop and is precise. 
                # III. Why nk_1 = k*2? look at the last line of formula in left column of page 2 of paper. we have nk_1 as starting index ...
                # ... of last segment. When have k = 1 or just want two segments, such index is at least 2! this means k*2. 
                for nk_1 in range(k*2, L):
                    value = I[k-1][nk_1-1] + delta[nk_1][L]
                    if value < min_value:
                        min_value = value
                        min_index  = nk_1
                I[k][L]     = min_value
                Index[k][L] = min_index
                
    return I, Index
        


# ### Minimum Description Length to Find Optimal Number of Segments

def MinimumDescriptionLength(points, Index, Ns):
    # Here, the goal is to find the goodness of fit without any kind of over-fitting or a problem which have unseen data. 
    # We use the formula (7) in paper "Optimal segmentation of signals and its application to image denoising and boundary feature extraction (2004)" which is written based on a well-known research paper: "modeling by shortest data description, 1978"		
    MDL = 0

    # 1. Calculation of first term
    mle = 0

    segment_end = len(points) - 1
    segment_begin = -1
    
    for k in reversed(range(0, Ns)):
        thisSegmentMLE = 0
        segment_begin = Index[k][segment_end]
        mean,std = estimate_mean_std(points[segment_begin:segment_end+1])
        for i in range(segment_begin,segment_end):
             thisSegmentMLE += ln_of_normal_distribution(points[i], mean, std)
        mle += thisSegmentMLE
        segment_end = segment_begin-1
    
    # there is a negative sign behind the ln in equation (7)
    mle *= -1
    MDL = mle

    # 2. Calculation of second term
    # r_k = #parameters for estimated distributions + k - 1
    # Here, such number of parameters is 2*k since we have 2 parameters (mean and std) for every single estimated pdf
    # Also, k = Ns
    r_k = 2*Ns + Ns - 1
    MDL += (r_k/2) * math.log(len(points)); # (r_k/2)*ln(N)

    return MDL


# ### Segmentation Workflow

class trip_tuple:
    def __init__(self, time_step, speed, acceleration, heading, latitude, longitude, pmd):
        self.speed = float(speed)
        self.acceleration = float(acceleration)
        self.heading = float(heading)
        self.lat = float(latitude)
        self.lng = float(longitude)
        self.time_step = int(time_step)
        self.pmd = float(pmd) # probabilistic movement dissimilarity

def segmentation_process(trip_id, # trip id
                         points,  # contains probabilistic dissimilarity values
                         trip_points, # contains trip points
                         max_number_of_segments, # the maximum number of segments that we allow
                         writer # writer to print output 
                        ):
    # 1. Calculation of Delta for all form of segments in given trajectory
    print ('Building delta for {} ... '.format(trip_id), end='')
    start = time.time()
    delta = calculateDelta(points)
    print ('completed in {:.1f} sec!'.format(time.time()-start))
    
    # 2. Optimization to find the best value of Ns
    MDL = float_max_value
    bestNs = -1
    optimizedIndex = None
    print ('Segmenting trajectory {} '.format(trip_id), end=''),
    
    start = time.time()
    for Ns in range(1, max_number_of_segments+1):
        # 3. Finding optimal segmentation by having N_s as number of existing segments in given trajectory
        # In addition to minimum values, we need minimum indexes which show the best breaking points for segments
        I,Index = dynamicProgramingSegmentation(points, Ns, delta)
        mdl = MinimumDescriptionLength(points, Index, Ns)
        if mdl < MDL:
            MDL = mdl
            bestNs = Ns
            optimizedIndex = Index
        print ('.', end='')
    print (' completed in {:.1f} sec!'.format(time.time() - start))
    
    # 3. Using optimize I and Index to get the optimal segment boundaries
    segmentPoints = set()
    lastEndPoint = len(points)-1
    for k in reversed(range(bestNs)):
        segmentPoints.add(optimizedIndex[k][lastEndPoint])
        lastEndPoint = optimizedIndex[k][lastEndPoint] - 1
        
    for i in range(0, len(trip_points)):
        if i in segmentPoints:
            writer.write('{},{},{},{},{},{},{},{},{}\n'.format(trip_id, 
                                                       trip_points[i].time_step,
                                                      trip_points[i].speed,
                                                      trip_points[i].acceleration,
                                                      trip_points[i].heading,
                                                      trip_points[i].lat,
                                                      trip_points[i].lng,
                                                      trip_points[i].pmd,
                                                      1))
        else:
            writer.write('{},{},{},{},{},{},{},{},{}\n'.format(trip_id, 
                                                       trip_points[i].time_step,
                                                      trip_points[i].speed,
                                                      trip_points[i].acceleration,
                                                      trip_points[i].heading,
                                                      trip_points[i].lat,
                                                      trip_points[i].lng,
                                                      trip_points[i].pmd,
                                                      0))
            


writer = open('output/segmentation_results.csv', 'w')
writer.write('TripId,TimeStep,Speed,Acceleration,HeadingChange,Latitude,Longitude,PMD,StartOfSegment\n')
max_number_of_segments = 50
n_trajectories = 0

path = r'C:\Users\sobhan\workspace\TrajectorySegmentation'
with open('prerequisiteFiles/ProbabilisticDissimilarities.csv', 'r') as reader:
    header = True
    trip_id = ''
    points = [] # for probability dissimilarity values
    trip_points = [] # for all trip points
    
    for line in reader:
        if header:
            header = False
            continue
        parts = line.replace('\r','').replace('\n','').split(',') # TripId,TimeStep,ProbDissimilarity,Lat,Lng,Speed,Acceleration,Heading
        if parts[0] == trip_id:
            points.append(float(parts[2]))
            trip_points.append(trip_tuple(parts[1], # time step
                                         parts[5], # speed
                                         parts[6], # acceleration
                                         parts[7], # heading change
                                         parts[3], #latitude
                                         parts[4], #longitude
                                         parts[2] # pmd
                                ))
        else:
            if trip_id != '':
                # do segmentation
                segmentation_process(trip_id, points, trip_points, max_number_of_segments, writer)
                n_trajectories += 1
            trip_id = parts[0]
            points = [float(parts[2])]
            trip_points = [trip_tuple(parts[1], # time step
                                         parts[5], # speed
                                         parts[6], # acceleration
                                         parts[7], # heading change
                                         parts[3], #latitude
                                         parts[4], #longitude
                                         parts[2] # pmd
                                )
                ]
        
    
# do segmentation for the last trajectory
segmentation_process(trip_id, points, trip_points, max_number_of_segments, writer)
n_trajectories += 1
writer.close()   
print ('\nDone with segmentation of {} trajectories!'.format(n_trajectories))


# ### Notes
# 
# * Building delta is a preprocessing step prior to dynamic programming segmentation, which takes surprisingly longer than its Java implementation to run. 
# 
# * The python implmentation of segmentation takes way longer than its Java implmentation, altough both perform the same algorithm. 
