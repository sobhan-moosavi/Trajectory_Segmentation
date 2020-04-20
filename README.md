# Discovery of Driving Patterns by Trajectory Segmentation
This repo contains the code and sample data for the trajectory segmentation approach proposed in [1]. Here we provide two versions of implementation, one in `java` and another in `python`. The `java` version is more performant. The objective is for a given trajectory (in terms of multivariable time series), our segmentation algorithm finds meaningful driving patterns such as *acceleration*, *harsh brake*, *turn*, etc. 

## Implementation in Java (Performant Version)
This is the original implementation of our trajectory segmentation solution that is in Java. The code includes three important modules as follows:

* __CreateGraph__: This module creates a Markov graph based on an input trajectory dataset (more details in section 3.2 of [our paper](https://arxiv.org/pdf/1804.08748v2.pdf)). As input, this module uses `graph_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be found [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/graph_trips.csv).

* __TrajectoryTransformation__: The second module transforms an input trajectory to a signal form (more details in section 3.3 of [our paper](https://arxiv.org/pdf/1804.08748v2.pdf)). As input, this module uses `segmentation_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be found [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/segmentation_trips.csv).

* __DynamicProgrammingSegmentation__: The last step perform trajectory segmentation based on the transformed version of the trajectory. Details of our segmentation solution can be found in section 3.4 of [our paper](https://arxiv.org/pdf/1804.08748v2.pdf). As input, this module uses `segmentation_trips.csv` that must be placed inside the `/data` folder. One example of such a file can be found [here](https://github.com/sobhan-moosavi/Trajectory_Segmentation/blob/master/data/segmentation_trips.csv). As output, the code generates a `CSV` file named as `segmentation_results.csv` inside the `/output` directory. 


## Implementation in Python
Our segmentation solution is implemented in terms of three major parts as follows:

* __Building_Graph__: This module is the same as the java module _CreateGraph_ (see above description). We have provided both `python` and `jupyter notebook` implementations of this module. 

* __Trajectory_Transformation__: This module is the same as the java module _TrajectoryTransformation_ (see above description). We have provided both `python` and `jupyter notebook` implementations of this module. 

* __Dynamic-programming based Segmentation__: This module is the same as the java module _DynamicProgrammingSegmentation_. We have provided both `python` and `jupyter notebook` implementations of this module. 


## Sample Data
We have provided two CSV files as sample data that you can find them inside the `/data` directory:

* `graph_trips.csv`: this file contains 1000 trajectories, and it can be used to build a Markov graph. This file has the following attributes:

| Attribute | Description |
| ------------- | ------------- |
| TripId | This is an identifier to separate different trajectories (a string). |
| TimeStep | This is an identifier for a record of a trajectory or trip (an integer). |
| Speed | This shows the ground velocity of a vehicle in km/h (a float). |
| Acceleration | This shows acceleration of a vehicle in m/s^2 (a float). |
| HeadingChange | This shows change in bearing or heading of a vehicle with respect to the previous timestamp (in degrees). |

* `segmentation_trips.csv`: this file contains 50 trajectories, and it can be used for the segmentation task. This file has the same attributes as `graph_trips.csv`, unless it has two extra columns for GPS coordinates (i.e., latitude and longitude). 

## How to Run

__Run Java Code__: You can use any IDE (e.g., [Eclipse](https://www.eclipse.org/downloads/packages/release/kepler/sr1/eclipse-ide-java-developers)) to compile and run java codes. You only need to create a new java project using the provided source codes. 

__Run Python Code__: You can run python codes using [Jupyter Notebook](https://jupyter.org/) or using a [Python Interpreter](https://www.python.org/downloads/). Please note that you need very basic python libraries such as `numpy` to run the scripts. 


## Output Format
As output, both Java and Python versions generate a CSV file inside the `/output` directory that contains the following attributes:

| Attribute | Description |
| ------------- | ------------- |
| TripId | This is an identifier to separate different trajectories (a string). |
| TimeStep | This is an identifier for a record of a trajectory or trip (an integer). |
| Speed | This shows the ground velocity of a vehicle in km/h (a float). |
| Acceleration | This shows acceleration of a vehicle in m/s^2 (a float). |
| HeadingChange | This shows change in bearing or heading of a vehicle with respect to the previous time stamp (in degrees). |
| Latitude | This is the latitude coordinate of a GPS record (a float). |
| Longitude | This is the longitude coordinate of a GPS record (a float). |
| PMD | This shows the probabilistic movement dissimilarity (pmd) value for the current record, obtained as a result of the trajectory transformation process (a float) |
| StartOfSegment | This is an indicator (0/1) that shows if the current record is the start of a new segment or not; value 1 shows the start of a new segment. |

## Evaluate Trajectory Segmentation Algorithm
To evaluate your trajectory segmentation approach, you may use our [Dataset of Annotated Car Trajectories (DACT)](https://smoosavi.org/datasets/dact) dataset, which is a collection of 50 trajectories annotated by human subjects (for more details check out [this paper](https://dl.acm.org/doi/10.1145/3152178.3152184)). 


## References
[1] Moosavi, Sobhan, Rajiv Ramnath, and Arnab Nandi. "[Discovery of driving patterns by trajectory segmentation.](https://arxiv.org/pdf/1804.08748v2.pdf)" In Proceedings of the 3rd ACM SIGSPATIAL PhD Symposium, pp. 1-4. 2016.

[2] Moosavi, Sobhan, Behrooz Omidvar-Tehrani, R. Bruce Craig, Arnab Nandi, and Rajiv Ramnath. "[Characterizing driving context from driver behavior.](https://dl.acm.org/doi/10.1145/3139958.3139992)" In Proceedings of the 25th ACM SIGSPATIAL International Conference on Advances in Geographic Information Systems, pp. 1-4. 2017.
