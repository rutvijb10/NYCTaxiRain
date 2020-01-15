Create property ZoneVertex.area embedded OMultipolygon;
Update ZoneVertex set area = St_GeomFromText(the_geom);

CREATE CLASS ZonePair EXTENDS E;

CREATE PROPERTY ZonePair.No_Of_Trips Integer;
ALTER PROPERTY ZonePair.No_Of_Trips DEFAULT 0;

CREATE PROPERTY ZonePair.TotalFare Double;
ALTER PROPERTY ZonePair.TotalFare DEFAULT 0.0;

CREATE PROPERTY ZonePair.TipperPercentage Double;
ALTER PROPERTY ZonePair.TipperPercentage DEFAULT 0.0;

CREATE EDGE ZonePair FROM (SELECT FROM ZoneVertex) TO (SELECT FROM ZoneVertex);
