import matplotlib.pyplot as plt
import numpy.ma as ma
import numpy as np
import pyart.graph
import pyart.retrieve
import tempfile
import pyart.io
import pyart.core
import boto
import geopy
import math
import re
import csv
from geopy.distance import VincentyDistance
import pytz
from datetime import datetime
from dateutil import tz
from subprocess import call

# Generates a regular expression with which to grab the files we want from AWS
def generate_regex(year=False,month=False,day=False,station=False):
    y = str(year) if year else "\d*"
    m = str(month) if month else "\d*"
    d = str(day) if day else "\d*"
    s = station if station else ".*"
    return y+"\/"+m+"\/"+d+"\/"+s+".*"

# Creates a short cut to limit the numebr of keys we need to search to get the files we want 
def generate_short_cut_path(year,month=False,day=False,station=False):
    shortcutTemplate = str(year)
    if month:
        shortcutTemplate += "/" + str(month)
        if day:
            shortcutTemplate += "/" + str(day)
            if station:
                shortcutTemplate += "/" + station
    return shortcutTemplate

# Grab a list of files we want from the NEXRad data set
def grab_list_of_files(year,month=False,day=False,station=False):
    s3conn = boto.connect_s3(anon=True)
    bucket   = s3conn.get_bucket('noaa-nexrad-level2')
    regex    = generate_regex(year,month,day,station)
    shortcut = generate_short_cut_path(year,month,day,station)
    keys = [key.key for key in bucket.list(shortcut) if re.match(regex,key.key)  ]

    return keys

# Use Boto to grab the file from s3 and load it in to pyart
def grab_and_process_radar(key):
    s3conn = boto.connect_s3(anon=True)
    bucket = s3conn.get_bucket('noaa-nexrad-level2')
    s3key = bucket.get_key(key)
    localfile = tempfile.NamedTemporaryFile()
    s3key.get_contents_to_filename("temp")
    try:
        radar = pyart.io.read_nexrad_archive("temp")
    except:
        radar = None
        print('skipping '+str(s3key))
    return radar

def offset_by_meters(x,y,lat,lon):
    if (x.all == 0 and y.all ==0):
        return lat,lon
    dist = math.sqrt(x*x+y*y)
    bearing = math.atan2(y,x)

    origin = geopy.Point(lat, lon)
    destination = geopy.distance.geodesic(meters=dist).destination(origin, math.degrees(bearing))

    lat2, lon2 = destination.latitude, destination.longitude    
    return lat2,lon2

def get_data(radar):
    # print(pyart.retrieve.echo_class.hydroclass_semisupervised(radar))
    refl_grid = radar.get_field(0, 'reflectivity')
    rhohv_grid = radar.get_field(0, 'cross_correlation_ratio')
    zdr_grid = radar.get_field(0, 'differential_reflectivity')

    # apply rudimentary quality control
    ref_low = np.less(refl_grid, 20)
    zdrhigh = np.greater(np.abs(zdr_grid), 2.3)
    rhohvlow = np.less(rhohv_grid, 0.95)
    notweather = np.logical_or(ref_low, np.logical_or(zdrhigh, rhohvlow))

    qcrefl_grid = ma.masked_where(notweather, refl_grid)
    qced = radar.extract_sweeps([0])
    qced.add_field_like('reflectivity', 'reflectivityqc', qcrefl_grid)
    return qced

def convert_date(date_utc):
    est = pytz.timezone('US/Eastern')
    utc = pytz.utc
    fmt = '%Y-%m-%dT%H:%M:%SZ'
    to_fmt = '%Y-%m-%d %H:%M:%S'

    from_zone = tz.gettz('UTC')
    to_zone = tz.gettz('America/New_York')
    date_time_utc = datetime.strptime(date_utc, fmt)
    date_time_utc = date_time_utc.replace(tzinfo=from_zone)
    date_time_est = date_time_utc.astimezone(to_zone)
    return date_time_est.strftime(to_fmt)


def save_as_csv(filename,data,level, append, extent=300, points=100):

    grids = pyart.map.grid_from_radars(
        (data,),
        grid_shape=(11, points, points),
        grid_limits= ((0, 11000), (-extent*1000.0, extent*1000.0), (-extent*1000.0, extent*1000.0)),
        fields=['reflectivity'],
        refl_field='reflectivity',
        max_refl=100.)

    center = [grids.origin_latitude["data"][0], grids.origin_longitude["data"][0]]
    date_utc    = grids.time["units"].replace( "seconds since ","")
    est_date = convert_date(date_utc)

    ref = grids.fields["reflectivity"]["data"][level]

    x_dists = grids.x['data']
    y_dists = grids.y['data']
    
    data    = np.array(grids.fields["reflectivity"]["data"][level])
    
    if append:
        csvfile =  open(filename, 'a')
    else:
        csvfile =  open(filename, 'w')
    writer = csv.writer(csvfile, delimiter=',', quotechar='|', quoting=csv.QUOTE_MINIMAL)
    
    if not append:
        writer.writerow(["sweepTime", "latitude", "longitude", "value"])
    for (ix,iy), value in np.ndenumerate(data):
        if value != 0.0 and value != -9999.0:
            x = x_dists[ix]
            y = y_dists[iy]
            lat, lon = offset_by_meters(x,y,center[0],center[1])
            writer.writerow([est_date, lat, lon, value])
    return data

station = "KOKX"  
year = "2019"
daysPerMonth = (31,28,31,30,31,30,31,31,30,31,30,31)
firstDay = True

for m in range(5,7):
    month = str(m).zfill(2)
    for d in range(1,1 + daysPerMonth[m-1]):
        day = str(d).zfill(2)
        keys = grab_list_of_files(year, month, day, station=station)
        print("Getting " + year + "-" + month + "-" + day)
        for key in keys:
            radar  = grab_and_process_radar(key)
            if (not radar):
                continue
            reflectivity_data = get_data(radar)
            append = not (firstDay and key == keys[0])
            data = save_as_csv("all2.csv", reflectivity_data, 5 ,append)
            call(["rm", "temp"])
        firstDay = False
