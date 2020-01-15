package com.orientdb;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import com.orientechnologies.orient.core.sql.executor.OResult;
import com.orientechnologies.orient.core.sql.executor.OResultSet;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.Scanner;
import java.util.HashMap;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Query {
    public static void main(String args []){
        OrientDB orient = new OrientDB("remote:localhost", OrientDBConfig.defaultConfig());
        ODatabaseSession db = orient.open("taxiproject", "root", "csci5751password");

        Scanner myObj = new Scanner(System.in);
        System.out.println("Enter query name");
        String query = myObj.nextLine();
        if(query.contains("tip")){
            String nonTippersBorough = getMaximumNonTippersBorough(db);
            System.out.println(nonTippersBorough);
        }
        else if(query.contains("rain")){
            System.out.println("Enter trip number");
            String rid = "#" + myObj.nextLine();
            String raining = checkRain(db, rid);
            System.out.println(raining);
        }
        else if (query.contains("trip")){
            System.out.println("Enter start date");
            String start = myObj.nextLine();
            System.out.println("Enter end date");
            String end = myObj.nextLine();
            String answer = getMaximumZonePair(db,start, end);
            System.out.println(answer);
        }
        db.close();
        orient.close();
    }

    public static String getMaximumNonTippersBorough(ODatabaseSession db){
        long startTime = System.currentTimeMillis();
        HashMap<String, String > boroughId = new HashMap();
        HashMap<String, Float> boroughNonTippersCount = new HashMap();
        HashMap<String, Float> boroughTotalCount = new HashMap();
        String statement1 = "SELECT borough, OBJECTID FROM ZoneVertex GROUP BY OBJECTID";
        OResultSet rs1 = db.query(statement1);
        while(rs1.hasNext()){
            OResult row = rs1.next();
            JSONObject result = new JSONObject(row.toJSON());
            boroughId.put(result.get("OBJECTID").toString(), result.get("borough").toString());
            boroughNonTippersCount.put(result.get("borough").toString(), (float) 0);
            boroughTotalCount.put(result.get("borough").toString(), (float) 0);
        }

        String statement2 = "SELECT PULocationID, COUNT(*) As NonTipCount FROM Trip where tip_amount==0 and payment_type!=2 GROUP BY PULocationID";
        OResultSet rs2 = db.query(statement2);
        while(rs2.hasNext()){
            OResult row = rs2.next();
            JSONObject result = new JSONObject(row.toJSON());
            String borough = boroughId.get(result.getString("PULocationID"));
            String nonTipCount = result.get("NonTipCount").toString();
            float updatedBoroughCount = boroughNonTippersCount.get(borough) + Integer.parseInt(nonTipCount);
            boroughNonTippersCount.put(borough, updatedBoroughCount);
        }

        String statement3 = "SELECT PULocationID, COUNT(*) As TotalCount FROM Trip where payment_type!=2 GROUP BY PULocationID";
        OResultSet rs3 = db.query(statement3);
        while(rs3.hasNext()){
            OResult row = rs3.next();
            JSONObject result = new JSONObject(row.toJSON());
            String borough = boroughId.get(result.getString("PULocationID"));
            float updateTotalCount = boroughTotalCount.get(borough) + Integer.parseInt(result.get("TotalCount").toString());
            boroughTotalCount.put(borough, updateTotalCount);
        }

        float max = 0;
        String maxBorough = "";
        for (String borough: boroughNonTippersCount.keySet()){
            if (boroughTotalCount.get(borough) == 0) {
                continue;
            }
            float percentNonTip = (boroughNonTippersCount.get(borough) / boroughTotalCount.get(borough)) * 100;
            if ( percentNonTip> max){
                max = percentNonTip;
                maxBorough = borough + " ";
            }
            else if (percentNonTip == max){
                maxBorough = maxBorough + borough + " ";
            }
        }
        if (maxBorough.isEmpty()){
            maxBorough = "No non tippers are present in any borough";
        }
        maxBorough = "The maximum percentage of non tippers are present in " + maxBorough;
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        maxBorough += "\n Time taken to execute query: " +  TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS) +" seconds";
        return maxBorough;
    }

    public static String checkRain(ODatabaseSession db, String rid){
        long startTime = System.currentTimeMillis();
        String statement1 = "select PULocationID, pickup_datetime from Trip where @rid=?";
        OResultSet rs1 = db.query(statement1, rid);
        String locationId ="";
        String pickup_datetime = "";
        while(rs1.hasNext()) {
            OResult row = rs1.next();
            JSONObject result = new JSONObject(row.toJSON());
            locationId = result.getString("PULocationID");
            pickup_datetime = result.getString("pickup_datetime");
        }

        String statement2 = "select the_geom from ZoneVertex where OBJECTID= ?";
        OResultSet rs2 = db.query(statement2, locationId);
        String zone_geom ="";
        while(rs2.hasNext()){
            OResult row = rs2.next();
            JSONObject result = new JSONObject(row.toJSON());
            zone_geom = result.get("the_geom").toString();
        }
        String statement3 = "Select ST_Contains(ST_geomFromText(\'"+zone_geom+"\'), rainpoint),sweepTime from (select coordinates as rainpoint, sweepTime from Climate)";
        OResultSet rs3 = db.query(statement3);

        String raining = "Not raining during trip";
        while(rs3.hasNext()){
            OResult row = rs3.next();
            String result = row.toString();
            if (result.contains("true")){
                JSONObject result1 = new JSONObject(row.toJSON());
                String sweepString = result1.getString("sweepTime");
                Date sweepDate = new Date();
                Date pickupDate = new Date();
                try {
                    sweepDate=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(sweepString);
                    pickupDate=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(pickup_datetime);
                } catch (ParseException e) {
                    e.printStackTrace();
                }

                long diffInMillies = pickupDate.getTime() - sweepDate.getTime();
                long diffInMins = TimeUnit.MINUTES.convert(diffInMillies, TimeUnit.MILLISECONDS);
                if (diffInMins > 30 &&  diffInMins>0){
                    raining = "Raining during trip";
                    break;
                }
            }
        }
        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        raining += "\nTime taken to execute query: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS) +" seconds";
        return raining;
    }
    public static String getMaximumZonePair(ODatabaseSession db, String start, String end){
        long startTime = System.currentTimeMillis();
        String statement1 = "SELECT zone, OBJECTID FROM ZoneVertex GROUP BY OBJECTID";
        OResultSet rs1 = db.query(statement1);
        HashMap<String, String > zoneId = new HashMap();
        while(rs1.hasNext()){
            OResult row = rs1.next();
            JSONObject result = new JSONObject(row.toJSON());
            zoneId.put(result.get("OBJECTID").toString(), result.get("zone").toString());
        }

        String statement2 = "select PULocationID, DOLocationID, count(*) as NoOfTrips from Trip WHERE pickup_datetime.format('YYYY-MM-dd') BETWEEN \'"+start+"\'  AND \'"+end+"\' group by PULocationID, DOLOCATIONID ORDER BY NoOfTrips DESC";
        OResultSet rs2 = db.query(statement2);
        String result1 = "";
        while(rs2.hasNext()){
            OResult row = rs2.next();
            JSONObject result = new JSONObject(row.toJSON());
            String source = result.get("PULocationID").toString();
            String sourceZone = zoneId.get(source);
            String dest = result.get("DOLocationID").toString();
            String destZone = zoneId.get(dest);
            result1 = "The maximum number of trips are from "+ sourceZone + " to " + destZone +".";
        }

        long stopTime = System.currentTimeMillis();
        long elapsedTime = stopTime - startTime;
        result1 += "\nTime taken to execute query: " + TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS) +" seconds";
        return result1;
    }
}
