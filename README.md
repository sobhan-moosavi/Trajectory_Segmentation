# Discovery of Driving Patterns by Trajectory Segmentation
This repo contains the code and sample data for trajectory segmentation approach proposed in [1]. Here we provide two versions of implementaiton, one in `java` and another in `python`. The `java` version is more performant. The objective is, given a trajectory (in terms of several attributes such as latitude, longitude, speed, etc.), our segmentation algorithm finds significant driving patterns in the input trajectory.  

## Implementation in Java (Performant Version)
This is a the original implementation of our trajectory segmentation solution that is in Java. The code incldues three important modules as follows:

* __CreateGraph__: This module creates a Markov graph based on an input trajectory datase (details are in described in section 3.2 of [our paper](https://dl.acm.org/doi/pdf/10.1145/3003819.3003824)). As input, this module uses `graph_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be find [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/graph_trips.csv).

* __TrajectoryTransformation__: The second module transforms an input trajectory to a signal form (as described in section 3.3 of [our paper](https://dl.acm.org/doi/pdf/10.1145/3003819.3003824)). As input, this module uses `segmentation_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be find [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/segmentation_trips.csv).

* __DynamicProgramingSegmentation__: The last step perform trajectory segmentation based on the transformed version of trajectory. Details of our segmentation solution can be find in section 3.4 of [our paper](https://dl.acm.org/doi/pdf/10.1145/3003819.3003824). As input, this module uses `segmentation_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be find [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/segmentation_trips.csv). As output, the code generates a `csv` file named as `segmentation_results.csv` inside the `/output` directory. 


## Implementation in Python
Our segmentation solution is implemented in terms of three major parts as follows. 

* __Building the Graph__

* __Trajectory Transformation__

* __Dynamic-programming based Segmentation__


## Sample Data
We have provided two csv files as sample data that you can find them inside the `/data` directory:
* `graph_trips.csv`: this file contians 500 trajectories, and it can be used to build a Markov graph. 
* `segmentation_trips.csv`: this file contains 50 trajectories, and it can be used for the segmentation task. 

## How to Run


## References
[1] Moosavi, Sobhan, Rajiv Ramnath, and Arnab Nandi. "[Discovery of driving patterns by trajectory segmentation.](https://arxiv.org/pdf/1804.08748.pdf)" In Proceedings of the 3rd ACM SIGSPATIAL PhD Symposium, pp. 1-4. 2016.

[2] Moosavi, Sobhan, Behrooz Omidvar-Tehrani, R. Bruce Craig, Arnab Nandi, and Rajiv Ramnath. "[Characterizing driving context from driver behavior.](https://arxiv.org/pdf/1710.05733.pdf)" In Proceedings of the 25th ACM SIGSPATIAL International Conference on Advances in Geographic Information Systems, pp. 1-4. 2017.
