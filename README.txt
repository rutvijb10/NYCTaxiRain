Setup

	- Tested on OrientDB version 3.0.24
	- This 'taxiproject' folder should be in main orientdb 3.0 installation folder

	- Run the server with server.sh in the orientdb bin folder. The server must be running during ingestion.
		- Etl configeration files convigured with user: root, password: csci5751password
		- to use a different user all json files prefexid with 'etl-' need to be updated

Loading Taxi Zone Data

	- /taxiproject/taxidata/taxi_zones.csv must be the first thing loaded into a new database. This should only be done once.
		- Do this by running ingest_taxi_zone_data.sh
		- This should also setup the taxiproject database should it not already exist

 	- After ingesting the zone data, execute the commands in prepare_graph.sql to settup the ZoneVertex graph.

Loading Taxi Trip Data

	- Data Source: https://www1.nyc.gov/site/tlc/about/tlc-trip-record-data.page
		- Download monthly yellow and green taxi csv files. 
		- Due to data format changes, only months from 2016 and beyond are supported.

	- Ingest green and yellow taxi CSV data with ingest_green.sh and ingest_yellow.sh shell scripts respectively
		- Modify the csv paths in these files to select the right file

Generating and loading Radar Data

	- To download and process radar data into a csv file run prepare_radar_data.py with Python 3
	- Configuration for the needed date range can be changed within the Python file

Analysis

	- At this point queries can be run on the server via its web interface at localhost:2480 or by running console.sh in the OrientDB/bin/ folder
	- The taxiproject/java folder contains a number of more advanced analysis queries that use the java api


