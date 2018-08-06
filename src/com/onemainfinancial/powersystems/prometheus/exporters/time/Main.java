package com.onemainfinancial.powersystems.prometheus.exporters.time;




import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.yaml.snakeyaml.Yaml;

import com.hubspot.jinjava.Jinjava;

import io.prometheus.client.Collector;
import io.prometheus.client.Collector.MetricFamilySamples;
import io.prometheus.client.Collector.Type;
import io.prometheus.client.exporter.MetricsServlet;





public class Main extends Collector {	
	static Integer port=8080;
	static Yaml yaml=new Yaml();
	static Logger logger=Logger.getLogger(Main.class);
	static Jinjava jinjava=new Jinjava();
	static Server server;
	static HashMap<String,Object> bindings=new HashMap<String,Object>();
	static String prefix;
	static ArrayList<Definition> definitions=new ArrayList<Definition>();
	static DateTimeFormatter timeFormatter = DateTimeFormat.forPattern("HHmmss");
	static DateTimeFormatter dateTimeFormatter = DateTimeFormat.forPattern("YYYY-MM-dd HH:mm:ss");

	static {
		bindings.put("system", System.getProperties());
		bindings.put("environment", System.getenv());
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static HashMap<String,Object> readConfig(File file){
		
		
		HashMap<String,Object> returnData=new HashMap<String,Object>();
		if(file.isFile()) {
			Path path=Paths.get(file.getAbsolutePath());
			logger.info("Loading config file "+path.toString());
			try {
				byte[] encoded = Files.readAllBytes(path);
				String data=new String(encoded, Charset.defaultCharset());
				//evaluate any templating
				data=jinjava.render(data, bindings);
				//load to map
	        	Object map=yaml.load(data);
	    		//if an array list, convert to map
	        	if(map instanceof ArrayList) {
	        		returnData.put("metrics", map);
	        	}else {
	        		returnData=(HashMap<String, Object>) map;
	        	}
	    	}catch(Exception e) {
	    		logger.error("Could not load config",e);
	    	}
		}else {
			logger.info("No config file loaded");
		}
		//Reset definitions in case of update
		Definition.reset();
		prefix=returnData.getOrDefault("prefix","time_range_").toString();
		ArrayList<HashMap<String,Object>> metrics=(ArrayList) returnData.getOrDefault("metrics",new ArrayList<Object>());
		
		//create definitions
		for(HashMap<String,Object> metric:metrics) {
		    definitions.add(new Definition(metric));	
		}
		
		getNumberOfDaysInMonths(DateTime.now());
		LinkedHashMap<String,LinkedHashMap<String,Integer>> weekdaysInMonth=(LinkedHashMap<String, LinkedHashMap<String, Integer>>) bindings.get("weekdaysInMonth");
        LinkedHashMap<String,Integer> totalDaysInMonth=(LinkedHashMap<String, Integer>) bindings.get("daysInMonth");
        
		for(String month:totalDaysInMonth.keySet()) {
	      definitions.add(new Definition("days_in_month_total","Total number of days in month","daysInMonth[metric.labels.month]","month",month));
	      for(String day:weekdaysInMonth.get(month).keySet()) {
	        definitions.add(new Definition("weekdays_in_month_total","Total number of same weekday in month","weekdaysInMonth[metric.labels.month][metric.labels.day]",new String[] {"month","day"},new String[] {month,day}));
	      }
		}
		
		
		
		definitions.add(new Definition("is_holiday","Determines if the current day is a holiday","dayOfYear == 1","holiday","New Years Day"));
		
		//third monday of january 
		definitions.add(new Definition("is_holiday",null,"month == 1 and nameOfDay == 'Monday' and currentWeekdayNumberInMonth == 3","holiday","Martin Luther King Day"));
		
		//last monday of may
		definitions.add(new Definition("is_holiday",null,"month == 5 and nameOfDay == 'Monday' and currentWeekdayNumberInMonth == weekdaysInMonth[nameOfMonth][nameOfDay]","holiday","Memorial Day"));
		
		//fourth of july
		definitions.add(new Definition("is_holiday",null,"month == 7 and dayOfMonth == 4","holiday","Independence Day"));
		
		//first monday of september 
		definitions.add(new Definition("is_holiday",null,"month == 9 and nameOfDay == 'Monday' and currentWeekdayNumberInMonth == 1","holiday","Labor Day"));
        
		//fourth thursday of november
		definitions.add(new Definition("is_holiday",null,"month == 11 and nameOfDay == 'Thursday' and currentWeekdayNumberInMonth == 4","holiday","Thanksgiving"));

		//25th of december
		definitions.add(new Definition("is_holiday",null,"month == 12 and dayOfMonth == 25","holiday","Christmas"));
		

		
		definitions.add(new Definition("days_in_current_month","The total number of days in the current month","daysInCurrentMonth"));
		definitions.add(new Definition("current_weekday_number_in_month","The current number of this weekday in the month.  For instance 3 for the third Monday of the month","currentWeekdayNumberInMonth"));
				
		
		definitions.add(new Definition("current_time","Current time in format HHmmss","time"));
	    definitions.add(new Definition("current_hour","Current hour of day","hour"));
	    definitions.add(new Definition("current_minute","Current minute of day","minute"));
	    definitions.add(new Definition("current_second","Current second of minute","second"));
	    definitions.add(new Definition("current_week","Current week of year","week"));
	    definitions.add(new Definition("current_month","Current month of year","month"));
	    definitions.add(new Definition("current_year","Current year","year"));
	    definitions.add(new Definition("current_week_of_month","Current week of the month","weekOfMonth"));
	    definitions.add(new Definition("current_day_of_month","Current day of the month","dayOfMonth"));
	    definitions.add(new Definition("current_day_of_week","Current day of the week","dayOfWeek"));
	    definitions.add(new Definition("current_day_of_year","Current day of the year","dayOfYear"));
	    
		return returnData;
	}
	

   
	
	public static void main(String[] args) throws Exception{
	    CommandLineParser parser = new DefaultParser();
        Options options = new Options();
        
        options.addOption(Option
        		.builder("h")
                .longOpt("help")
                .desc("Print this again")
                .build());
        options.addOption(Option
        		.builder("p") 
                .longOpt("port")
                .desc("Port for embedded jetty to listen on.  May also be specified in config file.")
                .hasArg()
                .argName("port")
                .build());
        options.addOption(Option
        		.builder("c")
                .longOpt("config")
                .desc("Config file.  Overwrites the time.prometheus.exporter.config system property.")
                .hasArg()
                .argName("file")
                .build());        
        try{
	        CommandLine line = parser.parse(options, args);
	        if(line.hasOption("h")){
	        	  new HelpFormatter().printHelp("Time Range Prometheus Exporter", options);
	              System.exit(0);
	        }
	        
	        if(line.hasOption("c")){
	        	System.setProperty("time.prometheus.exporter.config",line.getOptionValue("config"));
	        }

	        
	        // set console logger
	        // may add option for log file later
	        Properties log_props=new Properties();
			log_props.put("log4j.rootLogger", "INFO, stdout");
			log_props.put("log4j.appender.stdout","org.apache.log4j.ConsoleAppender");
			log_props.put("log4j.appender.stdout.Target","System.out");
			log_props.put("log4j.appender.stdout.layout","org.apache.log4j.PatternLayout");
			log_props.put("log4j.appender.stdout.layout.ConversionPattern","[%d{yyyy-MM-dd HH:mm:ss}] [%-5p] [%-20c{1}] %m%n");
			PropertyConfigurator.configure(log_props);
			
	        //find config file
	    	String file;
	        if(System.getProperty("time.prometheus.exporter.config") !=null) {
	        	file=System.getProperty("time.prometheus.exporter.config");	
	        }else if(System.getenv().containsKey("TIME_EXPORTER_CONFIG")) {
	        	file=System.getenv("TIME_EXPORTER_CONFIG");
	        }else {
	        	file="/data/time-prometheus-exporter.yaml";
	        }
	        
	        
	        
	   
	        final File configFile=new File(file);
 	        
		    HashMap<String,Object> config = readConfig(configFile);
		    
		        
		    //start file monitor thread
		    new Thread(){
			   	long lastMod=0;
			   	public void run(){
			   		if(configFile.isFile()) {
			   		  lastMod=configFile.lastModified();
			   		}
			   	    while(true){
			   	    	//check for updates to file
			   	       try {
			   			  Thread.sleep(5000);
			   		   } catch (InterruptedException e) {
			   			  break;
			   		   }
			   	       if(configFile.isFile()&&configFile.lastModified()!=lastMod){
			   	    	   lastMod=configFile.lastModified();
			   	    	   try {
			   	    		 readConfig(configFile);
			   			   } catch (Exception e) {
			   				 e.printStackTrace();
			   		   }
			   	     }
			   	   }	    		
			   	}
			}.start();
	        
	        
	        
	        //read port argument
	        if(line.hasOption("port")) {
		        try{
		            port=Integer.parseInt(line.getOptionValue("port"));
		        }catch(Exception e){
		        	throw new Exception(line.getOptionValue("port")+" is not a valid port");
		        }
		        
	        }else if(config.containsKey("port")) {
	        	//read config file port
	        	port=Integer.valueOf(config.get("port").toString());
	        }

	        
	       	//register collector and start   
	        
	        logger.info("Starting Jetty on port "+port);
			server = new Server(port);
			
	        logger.info("Registering collector");

	        new Main().register();
	        
 		    
		    ServletContextHandler context = new ServletContextHandler();
		    context.setContextPath("/");
		    server.setHandler(context);
		    context.addServlet(new ServletHolder(new MetricsServlet()), "/metrics");
		    server.start();
		    
		    server.join();
		     
        }catch(Exception e){
        	logger.error("Exception",e);
        }
        
	}
	
    private static void getNumberOfDaysInMonths(DateTime dt) {
    	  int year=dt.getYear();
          DateTime start=dateTimeFormatter.parseDateTime(year+"-01-01 00:00:00");
          
    	  
          LinkedHashMap<String,LinkedHashMap<String,Integer>> weekdaysInMonth=new LinkedHashMap<String,LinkedHashMap<String,Integer>>();
    	  LinkedHashMap<String,Integer> totalDaysInMonth=new LinkedHashMap<String,Integer>();
    	    
    	  bindings.put("weekdaysInMonth", weekdaysInMonth);
  	      bindings.put("daysInMonth", totalDaysInMonth);
    	
    	  int currentWeekdayNumber=0;
    	  int daysInCurrentMonth=0;
    	  
    	  String currentMonth=dt.monthOfYear().getAsText();
    	  int currentDayOfMonth=dt.getDayOfMonth();

    	  while(start.getYear()==year) {
    		  String month=start.monthOfYear().getAsText();
    		  String dayOfWeek=start.dayOfWeek().getAsText();
    		  if(!totalDaysInMonth.containsKey(month)) {
    			  totalDaysInMonth.put(month,0);
    			  weekdaysInMonth.put(month, new LinkedHashMap<String,Integer>());
    		  }
    		  if(!weekdaysInMonth.get(month).containsKey(dayOfWeek)){
    			  weekdaysInMonth.get(month).put(dayOfWeek, 0);
    		  }
    		  totalDaysInMonth.put(month,totalDaysInMonth.get(month)+1);
    		  weekdaysInMonth.get(month).put(dayOfWeek,weekdaysInMonth.get(month).get(dayOfWeek)+1);
    		  if(month.equals(currentMonth)&&start.getDayOfMonth()==currentDayOfMonth) {
    			  currentWeekdayNumber=Integer.valueOf(weekdaysInMonth.get(month).get(dt.dayOfWeek().getAsText()));
    		  }
    		  start=start.plusDays(1);
    	  }
    	  daysInCurrentMonth=totalDaysInMonth.get(currentMonth);
    	  bindings.put("daysInCurrentMonth", daysInCurrentMonth);
    	  bindings.put("currentWeekdayNumberInMonth", currentWeekdayNumber);
    }
    
    
	@Override
	public List<MetricFamilySamples> collect() {
		if(!server.isStarted()) {
			return new ArrayList<MetricFamilySamples>();
		}
		//clear last samples
		Definition.clearSamples();

		DateTime dt=DateTime.now();
		
		
		//week of month is not available in JodaTime
		Calendar calendar = Calendar.getInstance();
		calendar.setTime(dt.toDate());
		int weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH);


		//get number of days in month and number of same weekday in month
		getNumberOfDaysInMonths(dt);
	    
		bindings.put("now", dt);
		bindings.put("time", Integer.valueOf(timeFormatter.print(dt)));
		bindings.put("hour", dt.getHourOfDay());
		bindings.put("minute", dt.getMinuteOfDay());
		bindings.put("second", dt.getSecondOfMinute());
		bindings.put("week", dt.getWeekOfWeekyear());
		bindings.put("year", dt.getYear());
		bindings.put("month", dt.getMonthOfYear());
		bindings.put("weekOfMonth",weekOfMonth);
		bindings.put("dayOfMonth", dt.getDayOfMonth());
		bindings.put("dayOfWeek", dt.getDayOfWeek());
		bindings.put("dayOfYear", dt.getDayOfYear());
		bindings.put("nameOfDay", dt.dayOfWeek().getAsText());
		bindings.put("nameOfMonth", dt.monthOfYear().getAsText());
		
		

		List<MetricFamilySamples> list =new ArrayList<MetricFamilySamples>();

        
		//evaluate metrics
		for(Definition definition:definitions) {
		   definition.evaluate(bindings);   
		}
		
		
		for(String s:Definition.samples.keySet()) {
			list.add(Definition.samples.get(s));
		}
		return list;
		
	}
	
}
