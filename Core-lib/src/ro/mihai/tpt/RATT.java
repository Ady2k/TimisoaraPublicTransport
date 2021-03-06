/*
    TimisoaraPublicTransport - display public transport information on your device
    Copyright (C) 2011-2014  Mihai Balint

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/
package ro.mihai.tpt;

import java.io.IOException;
import java.io.InputStream;
import java.net.*;
import java.util.*;

import ro.mihai.tpt.model.*;
import ro.mihai.util.*;

public class RATT {
	public static final int CITY_DB_ENTRIES = 823;
	public static final String root = "http://www.ratt.ro/txt/";
	
	private static final String stationIdParamName = "id_statie"; 
	private static final String lineIdParamName = "id_traseu";
	
	private static final String stationList = "select_statie.php";
	
	// ?stationIdParamName=...
	private static final String linesInStationList = "select_traseu.php";
	
	// ?id_traseu=...&id_statie=...
	private static final String timesOflinesInStation = "afis_msg.php";
	
	public static List<Station> downloadStations(IPrefs prefs, IMonitor mon, City c) throws IOException {
		return new StationReader(new URL(prefs.getBaseUrl()+stationList), c).readAll(mon);
	}
	
	public static String[] downloadTimes(IPrefs prefs, String pathId, String stationId) throws IOException {
		URL url = new URL(prefs.getBaseUrl()+timesOflinesInStation+"?"+lineIdParamName+"="+pathId+"&"+stationIdParamName+"="+stationId);
		FormattedTextReader rd = new FormattedTextReader(url.openStream());
		String lineName = rd.readString("Linia: </font>", "<");
		String timestamp = rd.readString("<br><br>", "<");
		String time1 = rd.readString("Sosire1: ", "<");
		String time2 = rd.readString("Sosire2: ", "<");
		rd.close();
		return new String[]{time1, time2, timestamp, lineName};
	}
	
	public static City downloadCity(IPrefs prefs, IMonitor mon) throws IOException {
		City c = new City();
		mon.setMax(1200);
		List<Station> stations = new StationReader(new URL(prefs.getBaseUrl()+stationList), c).readAll(mon);
		int cnt = 0;
		for(Station s : stations) { 
			new LineReader(c,s, new URL(prefs.getBaseUrl()+linesInStationList+"?"+stationIdParamName+"="+s.getId())).readAll(new NullMonitor());
			cnt++;
			if((cnt % 10)==0)
				System.out.println(cnt + "/" + stations.size());
			mon.workComplete();
		}
		
		return c;
	}
	
	public static void addLatLongCoords(City c, InputStream in) throws IOException {
		StationsXMLReader rd = new StationsXMLReader(new FormattedTextReader(in));
		String[] stCoords;
		
		HashMap<String, Station> stationExtIdMap = new HashMap<String, Station>();
		for(Station s:c.getStations())
			stationExtIdMap.put(s.getExtId(), s);
		
		while(null != (stCoords = rd.readStationCoords())) {
			// coords = {name, id, lat,lng}
			Station s = stationExtIdMap.get(stCoords[1]);
			if(s==null) {
				System.out.println(stCoords[1]+" - "+stCoords[0]+": "+stCoords[2]+"x"+stCoords[3]);
				continue;
			}
			assert(s!=null);
			if(!s.getName().trim().equals(stCoords[0].trim())) {
				System.out.println("Name Diff: "+stCoords[1]+" - "+stCoords[0]+": "+s.getId()+" - "+s.getName());
			}
			s.setCoords(stCoords[2], stCoords[3]);
		}
		rd.close();
	}
	
	public static class StationReader extends OptValBuilder<Station> {
		private City c; 
		public StationReader(FormattedTextReader in, City c) { super(in); this.c = c; }
		public StationReader(URL url, City c) throws IOException { super(url); this.c = c; }
		
		protected Station create(String val, String opt) {
			Station s = c.newStation(val, opt); 
			return s; 
		}
	}

	public static class LineReader extends OptValBuilder<Line> {
		private Station st;
		private City c;
		public LineReader(City c, Station st, URL url) throws IOException { 
			super(url);
			this.st = st;
			this.c = c;
		}
		public LineReader(City c, Station st, FormattedTextReader in) throws IOException { 
			super(in);
			this.st = st;
			this.c = c;
		}
		
		protected Line create(String val, String opt) {
			if (opt.startsWith(" [0]  ") || opt.startsWith(" [1]  "))
				opt = opt.substring(6);
			Line l = c.getLineByName(opt);
			if (null==l)
				l = c.newLine(opt);
			Path p;
			if (l.getPaths().isEmpty()) {
				p = c.newPath(l, val, "");
				l.addPath(p);
			} else
				p = l.getFirstPath();
			p.concatenate(st, new HourlyPlan());
			st.addPath(p);
			return l; 
		}
	}
}
